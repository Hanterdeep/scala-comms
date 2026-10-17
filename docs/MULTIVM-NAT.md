# Multi-VM NAT Test

This directory contains a realistic multi-VM integration test that simulates
clients behind NAT with network impairments that commonly break UDP.

## Topology

```
[server] ---- public (172.20.0.0/16) ---- [nat-gw-a] ---- private-a (10.1.0.0/24) ---- [client-a]
                                  \---- [nat-gw-b] ---- private-b (10.2.0.0/24) ---- [client-b]
                                  \---- [nat-gw-c] ---- private-c (10.3.0.0/24) ---- [client-c]
```

## Obstacles

| Client | Latency | Jitter | Loss | NAT |
|--------|---------|--------|------|-----|
| A      | 50ms    | 20ms   | 2%   | MASQUERADE |
| B      | 80ms    | 30ms   | 5%   | MASQUERADE |
| C      | 120ms   | 40ms   | 8%   | MASQUERADE |

## What this tests

1. **TCP through NAT** — clients can join the cluster via the server seed
2. **Outbound UDP** — client → server UDP works (connection tracked)
3. **Inbound UDP without hole punching** — server → client UDP fails
4. **Hole punching** — after client sends first, server can reply
5. **Loss/latency tolerance** — cluster remains usable under impairment

## Running

```bash
# Build jars first
APP_VERSION=0.1.0-SNAPSHOT sbt assembly
sbt clientAssembly
cp target/scala-2.13/scala-communicator-server-*.jar target/

# Run the test
./scripts/multivm-nat-test.sh
```

Or via CI: the `Multi-VM NAT integration` workflow runs this on every PR.
