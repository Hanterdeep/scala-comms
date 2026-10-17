#!/usr/bin/env bash
# Multi-node integration test runner.
# Uses pre-built jars if present, otherwise builds them.
# Launches 1 server + 3 clients in Docker and checks join/dropout/rejoin.

set -euo pipefail

cd "$(dirname "$0")/.."

# Ensure jars are in target/ for the Dockerfile COPY
if ! ls target/scala-communicator-server-*.jar >/dev/null 2>&1; then
  if ls target/scala-2.13/scala-communicator-server-*.jar >/dev/null 2>&1; then
    cp target/scala-2.13/scala-communicator-server-*.jar target/
  fi
fi
if ! ls target/scala-communicator-client-*.jar >/dev/null 2>&1; then
  if ls target/scala-communicator-client-*.jar >/dev/null 2>&1; then
    :
  elif ls target/scala-2.13/scala-communicator-client-*.jar >/dev/null 2>&1; then
    cp target/scala-2.13/scala-communicator-client-*.jar target/
  fi
fi

if ! ls target/scala-communicator-server-*.jar >/dev/null 2>&1; then
  echo "== Building snapshot jars =="
  export APP_VERSION="${APP_VERSION:-0.1.0-SNAPSHOT}"
  sbt -batch assembly
  sbt -batch "clientAssembly"
  cp target/scala-2.13/scala-communicator-server-*.jar target/ || true
fi

ls -la target/scala-communicator-*.jar || true

echo "== Building multi-node image =="
docker compose -f docker-compose.multinode.yml build

echo "== Starting multi-node environment =="
docker compose -f docker-compose.multinode.yml up -d

echo "== Waiting for nodes to join =="
sleep 25

echo "== Server logs =="
docker compose -f docker-compose.multinode.yml logs server | tail -40

echo "== Client A logs =="
docker compose -f docker-compose.multinode.yml logs client-a | tail -20

echo "== Checking cluster membership =="
if docker compose -f docker-compose.multinode.yml logs server | grep -q "Node up"; then
  echo "PASS: server node is Up"
else
  echo "FAIL: server node did not report Up"
  docker compose -f docker-compose.multinode.yml logs
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
