# CanBankX – k6 Load Tests

Three test files targeting the most critical paths through the KrakenD API Gateway.

| File | Purpose | VUs | Duration |
|------|---------|-----|----------|
| `smoke-test.js` | Full happy-path + all error cases, 1 VU, zero-tolerance | 1 | ~1 min |
| `load-test.js` | Realistic concurrent load across 3 scenarios | 50 | ~4 min |
| `stress-test.js` | Find the breaking point with aggressive ramp | 200 | ~7 min |

---

## ⚠️ Critical: LB mode vs no-LB mode

**You MUST start the stack in the same mode you plan to test.**

### Mode A — No load balancer (default, single instance per service)

```zsh
docker compose up -d
docker compose --profile testing run --rm k6 run /tests/smoke-test.js
docker compose --profile testing run --rm k6 run /tests/load-test.js
```

KrakenD uses `krakend-nolb.json` → routes directly to each service container.

### Mode B — With Nginx load balancer (2× replicas per service)

```zsh
KRAKEND_CONFIG=krakend.json docker compose --profile lb up -d
docker compose --profile lb --profile testing run --rm k6 run /tests/smoke-test.js
docker compose --profile lb --profile testing run --rm k6 run /tests/load-test.js
```

KrakenD uses `krakend.json` → routes through `nginx-lb` on ports 8081/8082/8083
→ load-balanced across `identity-service` + `identity-service-2`, etc.

> **If transactions fail with P95 < 2 ms and 0 % success rate**, you are in the wrong mode:
> `krakend.json` is active but `nginx-lb` is not running (or vice versa).
> Run `docker compose ps` to check, then restart in the correct mode.

---

## Prerequisites

Your stack must be running and healthy before you run any test:

```zsh
docker compose ps
```

All services should show `healthy`. If any show `starting`, wait and check again.

---

## Running Tests — Docker (recommended, no install needed)

k6 is wired into `docker-compose.yaml` under the `testing` profile.
It joins the same Docker network as the services, so it reaches them by container name.

### Step 1 — Smoke test (always run this first)

```zsh
# No-LB mode
docker compose --profile testing run --rm k6 run /tests/smoke-test.js

# LB mode
docker compose --profile lb --profile testing run --rm k6 run /tests/smoke-test.js
```

Every single check must be green (100 %) before you run a load test.
If anything fails here, your stack has a bug — fix it before proceeding.

### Step 2 — Load test (normal traffic)

```zsh
docker compose --profile testing run --rm k6 run /tests/load-test.js
```

50 VUs spread across 3 concurrent scenarios for ~4 minutes.

### Step 3 — Stress test (find the ceiling)

```zsh
docker compose --profile testing run --rm k6 run /tests/stress-test.js
```

Ramps up to 200 VUs to find where the system starts degrading.

---

## Running Tests — Native k6 (alternative)

If you prefer to run k6 directly on your machine against `localhost:8080`:

### Install k6 on macOS

```zsh
brew install k6
```

### Run against localhost

```zsh
k6 run k6/smoke-test.js
k6 run k6/load-test.js
k6 run k6/stress-test.js
```

---

## What Each Test Does

### `smoke-test.js`
Runs the complete flow exactly once with 1 VU:

1. Register a new client → activate → login → MFA
2. Create CHECKING + SAVINGS accounts
3. Submit DEBIT, CREDIT, and TRANSFER transactions
4. Replay the DEBIT idempotency key — asserts same `id` is returned
5. Fetch account summary (KrakenD aggregation)
6. List transactions
7. Verify all error cases return the correct status: `404`, `401`, `409`, `422`, `400`
8. Health-check all three services directly

**Pass condition:** every check = 100 %, zero HTTP errors.

---

### `load-test.js`
Three concurrent scenarios:

| Scenario | VUs | What it does |
|----------|-----|--------------|
| `auth_traffic` | 10 | Login → MFA (BCrypt hashing under concurrency) |
| `transaction_traffic` | 25 | DEBIT / CREDIT / TRANSFER round-robin + idempotency replay |
| `read_traffic` | 15 | Account summary, transaction list, account lookup, account list |

**Thresholds (test fails if exceeded):**

| Metric | Limit |
|--------|-------|
| HTTP error rate | < 1 % |
| Auth p95 latency | < 800 ms |
| Transaction p95 latency | < 1 500 ms |
| Read p95 latency | < 400 ms |
| Transaction success rate | > 99 % |

---

### `stress-test.js`
Stages:

```
0 VUs ──► 50 VUs  (1 min ramp)
50    ──► 150 VUs (1 min ramp)
150        VUs    (2 min sustained)
150   ──► 200 VUs (30 s spike)
200        VUs    (1 min peak)
200   ──► 0 VUs   (1 min cool-down)
```

Alternates 2:1 transactions vs auth. Every 5th iteration also calls the aggregated summary endpoint.

Thresholds are relaxed (p95 < 3 s, errors < 5 %) — the goal is to observe degradation curves, not enforce SLAs.

---

## Reading the Output

```
✓ transaction → 201
✓ transaction → COMPLETED
✓ idempotency → same tx id

checks.........................: 99.87% ✓ 14832  ✗ 19
data_received..................: 18 MB  74 kB/s
data_sent......................: 4.2 MB 17 kB/s
http_req_duration..............: avg=234ms min=12ms  med=198ms  max=2.1s  p(90)=410ms p(95)=612ms
http_req_failed................: 0.05%  ✓ 0      ✗ 7
transaction_duration_ms........: avg=310ms p(95)=890ms
auth_duration_ms...............: avg=145ms p(95)=310ms
read_duration_ms...............: avg=52ms  p(95)=120ms
idempotency_cache_hits.........: 4488
transaction_success_rate.......: 99.89% ✓ 4491   ✗ 5
```

**What to look at:**

| Field | Good sign | Bad sign |
|-------|-----------|----------|
| `checks` | ≥ 99 % | < 99 % → a specific assertion is failing |
| `http_req_failed` | ~0 % | > 1 % → errors in the service layer |
| `p(95)` duration | within threshold | above threshold → service is slow under load |
| `idempotency_cache_hits` | growing counter | 0 → Redis is not caching |
| `transaction_success_rate` | > 99 % | drop → insufficient funds or service errors |

A ✗ on a threshold line means that threshold was **breached** — the test is considered a failure.

---

## Viewing Confirmation Emails During Tests

Every completed transaction triggers a confirmation email.
Open **MailHog** to see them pile up in real time:

```
http://localhost:8025
```

Under load, you will see hundreds of emails arrive.
This confirms the `payment-service → account-service → identity-service → MailHog` email chain works end-to-end.

---

## Saving Results to a File

```cmd
docker compose run --rm k6 run --out json=/tests/results/load-result.json /tests/load-test.js
```

The JSON file will appear in `k6/results/` on your host machine (the `/tests` volume is mounted from `./k6`).

Create the folder first if it doesn't exist:
```cmd
mkdir k6\results
```