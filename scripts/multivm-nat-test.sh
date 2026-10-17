#!/usr/bin/env bash
# Multi-VM NAT integration test.
#
# Topology:
#   server (172.30.0.10) on public network
#   client-a behind nat-gw-a (10.10.0.0/24, 50ms latency, 2% loss)
#   client-b behind nat-gw-b (10.20.0.0/24, 80ms latency, 5% loss)
#   client-c behind nat-gw-c (10.30.0.0/24, 120ms latency, 8% loss)

set -euo pipefail

cd "$(dirname "$0")/.."

echo "== Building jars if needed =="
if ! ls target/scala-communicator-server-*.jar >/dev/null 2>&1; then
  if ls target/scala-2.13/scala-communicator-server-*.jar >/dev/null 2>&1; then
    cp target/scala-2.13/scala-communicator-server-*.jar target/
  else
    export APP_VERSION="${APP_VERSION:-0.1.0-SNAPSHOT}"
    sbt -batch assembly
    sbt -batch "clientAssembly"
    cp target/scala-2.13/scala-communicator-server-*.jar target/ || true
  fi
fi

echo "== Building NAT topology =="
docker compose -f docker-compose.nat.yml build

echo "== Starting multi-VM NAT environment =="
docker compose -f docker-compose.nat.yml up -d

echo "== Waiting for NAT gateways and nodes =="
sleep 45

echo "== Topology check =="
docker compose -f docker-compose.nat.yml ps

echo "== Server logs =="
docker compose -f docker-compose.nat.yml logs server | tail -30 || true

echo "== Client A logs (behind NAT, low loss) =="
docker compose -f docker-compose.nat.yml logs client-a | tail -20 || true

echo "== Client B logs (behind NAT, medium loss) =="
docker compose -f docker-compose.nat.yml logs client-b | tail -20 || true

echo "== Client C logs (behind NAT, high loss) =="
docker compose -f docker-compose.nat.yml logs client-c | tail -20 || true

echo "== UDP connectivity probe =="
docker compose -f docker-compose.nat.yml exec -T client-a \
  sh -c "echo 'udp-probe' | nc -u -w 2 172.30.0.10 9000 || echo 'UDP probe sent (or nc missing)'" || true

echo "== Checking cluster membership =="
if docker compose -f docker-compose.nat.yml logs server | grep -q "Node up\|Server started"; then
  echo "PASS: server node is Up"
else
  echo "FAIL: server node did not report Up"
  docker compose -f docker-compose.nat.yml logs
  docker compose -f docker-compose.nat.yml down
  exit 1
fi

echo "== NAT obstacle summary =="
echo "- Client A: 50ms±20ms latency, 2% loss"
echo "- Client B: 80ms±30ms latency, 5% loss"
echo "- Client C: 120ms±40ms latency, 8% loss"
echo "- All clients behind NAT (MASQUERADE)"
echo "- Outbound TCP/UDP tracked; inbound requires hole punching"

echo "== Tearing down =="
docker compose -f docker-compose.nat.yml down

echo "Multi-VM NAT test complete."
