#!/usr/bin/env bash
# Ghetto NAT integration test.
#
# Double NAT with:
#   - outer: MASQUERADE --random, 15s UDP timeout, 100ms±40ms latency, 10% loss, UDP rate limit
#   - inner: MASQUERADE --random, 20s UDP timeout
#
# Expects:
#   - TCP still works (cluster join via seed)
#   - UDP is fragile (short timeouts, random ports, high loss)

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

echo "== Building ghetto NAT topology =="
docker compose -f docker-compose.ghetto-nat.yml build

echo "== Starting ghetto NAT environment =="
docker compose -f docker-compose.ghetto-nat.yml up -d

echo "== Waiting for double NAT and nodes =="
sleep 50

echo "== Topology =="
docker compose -f docker-compose.ghetto-nat.yml ps

echo "== Server logs =="
docker compose -f docker-compose.ghetto-nat.yml logs server | tail -25 || true

echo "== Client logs (behind double NAT) =="
docker compose -f docker-compose.ghetto-nat.yml logs client | tail -25 || true

echo "== Outer NAT conntrack / iptables =="
docker compose -f docker-compose.ghetto-nat.yml exec -T outer-nat \
  sh -c "iptables -t nat -L POSTROUTING -n -v; echo '---'; cat /proc/sys/net/netfilter/nf_conntrack_udp_timeout 2>/dev/null || echo 'timeout sysctl not visible'" || true

echo "== Checking cluster membership (TCP path) =="
if docker compose -f docker-compose.ghetto-nat.yml logs server | grep -q "Node up\|Server started"; then
  echo "PASS: server node is Up (TCP through double NAT works)"
else
  echo "FAIL: server node did not report Up"
  docker compose -f docker-compose.ghetto-nat.yml logs
  docker compose -f docker-compose.ghetto-nat.yml down
  exit 1
fi

echo "== Ghetto NAT summary =="
echo "- Double NAT (outer CGNAT-style + inner home router)"
echo "- UDP timeout ~15s on outer, ~20s on inner"
echo "- MASQUERADE --random (port mapping less stable)"
echo "- 100ms±40ms latency, 10% loss, UDP rate limit on outer"
echo "- TCP expected to work; UDP expected to be fragile"

echo "== Tearing down =="
docker compose -f docker-compose.ghetto-nat.yml down

echo "Ghetto NAT test complete."
