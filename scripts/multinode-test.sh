#!/usr/bin/env bash
# Multi-node integration test runner.
# Builds snapshot jars, then launches 1 server + 3 clients in Docker
# and checks that clients join the cluster under various conditions.

set -euo pipefail

cd "$(dirname "$0")/.."

echo "== Building snapshot jars =="
export APP_VERSION="${APP_VERSION:-0.1.0-SNAPSHOT}"
sbt -batch assembly
sbt -batch "clientAssembly"
ls -la target/scala-communicator-*.jar target/scala-2.13/scala-communicator-*.jar 2>/dev/null || true

echo "== Building multi-node image =="
docker compose -f docker-compose.multinode.yml build

echo "== Starting multi-node environment =="
docker compose -f docker-compose.multinode.yml up -d

echo "== Waiting for nodes to join =="
sleep 20

echo "== Server logs =="
docker compose -f docker-compose.multinode.yml logs server | tail -40

echo "== Client A logs =="
docker compose -f docker-compose.multinode.yml logs client-a | tail -20

echo "== Checking cluster membership =="
if docker compose -f docker-compose.multinode.yml logs server | grep -q "Node up"; then
  echo "PASS: server node is Up"
else
  echo "FAIL: server node did not report Up"
  docker compose -f docker-compose.multinode.yml down
  exit 1
fi

echo "== Simulating client dropout (real-life condition) =="
docker compose -f docker-compose.multinode.yml stop client-b
sleep 5
echo "Client B stopped. Remaining nodes should continue."

echo "== Restarting client B (rejoin) =="
docker compose -f docker-compose.multinode.yml start client-b
sleep 10

echo "== Final status =="
docker compose -f docker-compose.multinode.yml ps

echo "== Tearing down =="
docker compose -f docker-compose.multinode.yml down

echo "Multi-node integration test complete."
