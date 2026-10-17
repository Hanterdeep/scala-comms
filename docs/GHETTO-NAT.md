# Ghetto NAT Test

Simulates a bad ISP / CGNAT environment that commonly breaks UDP.

## Topology

```
[server] ---- public (172.40.0.0/16)
                 |
           [outer-nat]   CGNAT-style: random ports, 15s UDP timeout, 10% loss
                 |
              mid (10.50.0.0/24)
                 |
           [inner-nat]   home router: also MASQUERADE
                 |
            private (10.60.0.0/24)
                 |
            [client]
```

## Obstacles

| Layer | UDP timeout | Port mapping | Extra |
|-------|-------------|--------------|-------|
| Outer | 15s | MASQUERADE --random | 100ms±40ms latency, 10% loss, UDP rate limit |
| Inner | 20s | MASQUERADE --random | — |

## What this tests

1. **TCP through double NAT** — client can still join the cluster via the seed
2. **UDP fragility** — short timeouts and random ports make long-lived UDP sessions unreliable
3. **Loss under CGNAT-style conditions** — high loss + rate limit

## Expected behavior

- TCP path should succeed (cluster membership forms)
- UDP is expected to be unreliable; the app should fall back to TCP or use frequent keepalives

## Running

```bash
./scripts/ghetto-nat-test.sh
```

Or via CI: the `Ghetto NAT integration` workflow runs this on every PR.
