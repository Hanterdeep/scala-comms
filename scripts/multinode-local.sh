#!/usr/bin/env bash
# Process-based multi-node test (no Docker required).
# Launches 1 server + 3 clients as separate JVMs using the snapshot jars,
# then exercises real-life conditions: join, dropout, rejoin.

set -euo pipefail

cd "$(dirname "$0")/.."

SERVER_JAR=$(ls -1 target/scala-communicator-server-*.jar target/scala-2.13/scala-communicator-server-*.jar 2>/dev/null | head -1)
CLIENT_JAR=$(ls -1 target/scala-communicator-client-*.jar 2>/dev/null | head -1)

if [[ -z "$SERVER_JAR" || -z "$CLIENT_JAR" ]]; then
  echo "Jars not found. Run: APP_VERSION=0.1.0-SNAPSHOT sbt assembly && sbt clientAssembly"
  exit 1
fi

echo "== Using =="
echo "server: $SERVER_JAR"
echo "client: $CLIENT_JAR"

mkdir -p /tmp/multinode-logs
rm -f /tmp/multinode-logs/*.log

echo "== Starting server =="
java -jar "$SERVER_JAR" > /tmp/multinode-logs/server.log 2>&1 &
SERVER_PID=$!
echo "server pid $SERVER_PID"

for i in $(seq 1 30); do
  if grep -q "Server started on port" /tmp/multinode-logs/server.log 2>/dev/null; then
    echo "server ready"
    break
  fi
  sleep 2
done

echo "== Starting 3 clients =="
java -jar "$CLIENT_JAR" 127.0.0.1 2551 2552 > /tmp/multinode-logs/client-a.log 2>&1 &
PID_A=$!
sleep 3
java -jar "$CLIENT_JAR" 127.0.0.1 2551 2553 > /tmp/multinode-logs/client-b.log 2>&1 &
PID_B=$!
sleep 3
java -jar "$CLIENT_JAR" 127.0.0.1 2551 2554 > /tmp/multinode-logs/client-c.log 2>&1 &
PID_C=$!

echo "clients: $PID_A $PID_B $PID_C"
sleep 15

echo "== Checking join =="
grep -E "Node up|Client started|Selected|Peer visible" /tmp/multinode-logs/*.log || true

echo "== Real-life: kill client B (dropout) =="
kill $PID_B || true
sleep 5
echo "client B dropped"

echo "== Real-life: restart client B (rejoin) =="
java -jar "$CLIENT_JAR" 127.0.0.1 2551 2553 > /tmp/multinode-logs/client-b-rejoin.log 2>&1 &
PID_B2=$!
sleep 10

echo "== Final server view =="
grep -E "Node up|Node left|Peer" /tmp/multinode-logs/server.log | tail -20 || true

echo "== Cleanup =="
kill $SERVER_PID $PID_A $PID_C $PID_B2 2>/dev/null || true
sleep 2
echo "logs in /tmp/multinode-logs/"
ls -la /tmp/multinode-logs/
echo "done"
