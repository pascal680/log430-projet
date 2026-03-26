# Architecture Phase 2 — Virement Interbancaire (MVP Épuré)

> **Date :** Mars 2026  
> **Projet :** CanBankX — LOG430  
> **Périmètre :** Éléments essentiels UNIQUEMENT pour compléter un virement interbancaire  
> **Status :** Conception — Prêt pour implémentation

---

## 📌 Contexte et Contraintes

### Cahier de Charge Phase 2 (Rappel des Contraintes)

| Contrainte | Cible |
|---|---|
| **Latence P95** | ≤ 500 ms (lookup), ≤ 250 ms bonus |
| **Débit** | ≥ 500 ops/sec (1000 bonus) |
| **Observabilité** | 4 Golden Signals temps-réel (logs structurés, métriques, traces, dashboards) |
| **Idempotence** | Exactement-une-fois (exactly-once) pour paiements |
| **Finalité** | Ledger append-only, immuable après settlement |
| **Architecture** | Microservices + saga chorégraphiée + Kafka |
| **Sécurité** | REST/JSON + JWT/OAuth + CORS |
| **Déploiement** | Docker + Compose/Kubernetes |
| **Tests** | Unit + Intégration + Contract + Load (k6) |

---

## 🏗️ Architecture Simplifiée (Essentials Only)

### 3 Bounded Contexts (au lieu de 7)

```
┌────────────────────────────────────────────────────────┐
│            BC PLATFORM — API Gateway (KrakenD)         │
│ Port :8080 (Single Entry Point)                        │
└────────────────────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Alias      │  │ Orchestration│  │    Clearing  │
│  (Directory) │  │  (Payment)   │  │ (Settlement) │
│  :8092       │  │  :8093       │  │  :8094       │
└──────────────┘  └──────────────┘  └──────────────┘
     │                  │                  │
     └──────────────────┼──────────────────┘
                        │
                   ┌────▼────┐
                   │  Kafka   │
                   │(Events)  │
                   └────┬────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
   [Prometheus]   [Grafana]       [Redis]
      (Metrics)   (Dashboards)  (Cache + Outbox)
```

---

## 🎯 Les 3 Bounded Contexts Essentiels

### **(A) Alias Registry** — `alias-service` :8092

**Responsabilité :** Enregistrement et résolution de clés (US2, US4)

```yaml
Service: alias-service
Port: 8092
Language: Java 21 (Spring Boot 3)
DB: db_alias (MySQL 8.4)
Cache: Redis (TTL 5 min per resolution)

Entities:
  - Alias: id (UUID), key (email|phone|alphaparam), 
           participant_id, account_code, status

APIs:
  POST   /alias                          # US2: Register alias
         { key, participant_id, account_code }
         → Verify uniqueness, emit alias.registered
  
  GET    /alias/{key}/resolve            # US4: Lookup
         → Return { participant_id, account_code, masked_name }
         → Cache hit: < 50 ms
  
  PATCH  /alias/{id}/deactivate          # Disable alias

Events Produced:
  - alias.registered { aliasId, key, participantId }
  - alias.resolved { aliasId, participantId, accountCode, maskedName }
  - alias.deactivated { aliasId }

Events Consumed:
  - (none — leaf service)

Database:
  CREATE TABLE alias (
    id VARCHAR(36) PRIMARY KEY,
    key VARCHAR(255) UNIQUE NOT NULL,
    participant_id VARCHAR(36) NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    status ENUM('ACTIVE', 'DEACTIVATED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_key (key),
    INDEX idx_participant (participant_id)
  );
  
  -- Outbox table for event publishing
  CREATE TABLE alias_outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_root_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    published BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    INDEX idx_published (published, created_at)
  );
```

---

### **(B) Payment Orchestration** — `payment-orchestration-service` :8093

**Responsabilité :** Orchestration saga du virement (US1, US5, US6)

```yaml
Service: payment-orchestration-service
Port: 8093
Language: Java 21 (Spring Boot 3)
DB: db_payment (MySQL 8.4)
Cache: Redis (idempotency keys TTL 24h)

Entities:
  - Payment: id (UUID), idempotency_key (UNIQUE), 
             initiator_participant_id, alias_key, amount,
             status, created_at, settled_at

APIs:
  POST   /payment/initiate               # US5: Start payment
         { idempotencyKey, participantId, alias, amount }
         → Create Payment + OutboxEvent
         → Return { paymentId, status: INITIATED }
         → IDEMPOTENT: same idempotencyKey → same paymentId
  
  GET    /payment/{id}                   # Query payment status
         → Return { paymentId, status, beneficiary }
  
  PATCH  /payment/{id}/respond           # US6: Bank responds
         { response: "accepted|rejected", reason? }
         → Internal: processes debit/credit responses

Events Produced:
  - payment.initiated { paymentId, alias, amount, initiatorId }
  - payment.debit_requested { paymentId, participantId, amount }
  - payment.credit_requested { paymentId, participantId, amount }
  - payment.settled { paymentId, status: "SETTLED" }
  - payment.failed { paymentId, reason }

Events Consumed:
  - alias.resolved (→ know receiver participant + account)
  - bank_a.debit_accepted / .rejected (→ debit confirmed)
  - bank_b.credit_accepted / .rejected (→ credit confirmed)
  - clearing.settlement_confirmed (→ update to SETTLED)

State Machine:
  INITIATED 
    → (alias.resolved + fraud_check) 
    → DEBIT_REQUESTED 
    → (debit_accepted) 
    → CREDIT_REQUESTED 
    → (credit_accepted) 
    → SETTLEMENT_TRIGGERED 
    → (settlement_confirmed) 
    → SETTLED ✓
  
  OR on rejection:
    → COMPENSATION_PENDING → FAILED

Database:
  CREATE TABLE payment (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    initiator_participant_id VARCHAR(36) NOT NULL,
    alias_key VARCHAR(255) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status ENUM('INITIATED', 'DEBIT_REQUESTED', 'CREDIT_REQUESTED',
                'SETTLEMENT_TRIGGERED', 'SETTLED', 'FAILED') DEFAULT 'INITIATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP NULL,
    INDEX idx_initiator (initiator_participant_id),
    INDEX idx_idempotency (idempotency_key)
  );
  
  -- Outbox table
  CREATE TABLE payment_outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_root_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    published BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    INDEX idx_published (published, created_at)
  );
```

---

### **(C) Clearing & Settlement** — `clearing-settlement-service` :8094

**Responsabilité :** Ledger immuable et finalité (US7)

```yaml
Service: clearing-settlement-service
Port: 8094
Language: Java 21 (Spring Boot 3)
DB: db_clearing (MySQL 8.4, append-only ledger)
Cache: None (ledger = source of truth)

Entities:
  - LedgerEntry: id (BIGINT), entry_type (DEBIT|CREDIT),
                 participant_id, payment_id, amount,
                 timestamp, signature, immutable=true

APIs:
  POST   /settlement/record_entry        # US7: Record ledger entry
         { paymentId, debitParticipantId, creditParticipantId, amount }
         → INSERT 2 immutable entries (debit + credit)
         → Verify invariant: sum(DEBIT) == sum(CREDIT)
         → Emit settlement.confirmed
  
  GET    /settlement/ledger/{paymentId}  # Audit trail per payment

Events Produced:
  - clearing.settlement_confirmed { paymentId, ledgerEntries }

Events Consumed:
  - payment.settlement_triggered (→ record ledger entries)

Database (APPEND-ONLY):
  CREATE TABLE ledger_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_type ENUM('DEBIT', 'CREDIT') NOT NULL,
    participant_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    signature VARCHAR(512) NOT NULL,  -- BC signs each entry
    
    UNIQUE KEY (payment_id, entry_type),  -- one debit + one credit per payment
    INDEX idx_participant (participant_id),
    INDEX idx_payment (payment_id)
  );
  
  -- Prevent mutations (database-level immutability)
  CREATE TRIGGER prevent_ledger_update
  BEFORE UPDATE ON ledger_entry
  FOR EACH ROW
  BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable';
  END;
  
  -- Outbox table (for audit events)
  CREATE TABLE clearing_outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_root_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    published BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    INDEX idx_published (published, created_at)
  );
```

---

## 🔄 Saga Chorégraphiée — Virement Complet (Outbox Intégré)

### Timeline: Alice (Bank A) paie Bob (Bank B) 100 CAD

```
[T0] Client → Bank A: "Pay bob@mail.com 100 CAD"
     └─→ Bank A calls: POST /payment/initiate

[T1] payment-orchestration ─ ATOMIC TRANSACTION ─
     ├─ INSERT payment { id='pay-123', idempotencyKey='tx-...', status='INITIATED' }
     ├─ INSERT outbox_event { aggregateId='pay-123', eventType='payment.initiated', published=FALSE }
     └─ COMMIT ✓ (both or nothing)
     └─ Return { paymentId='pay-123', status='INITIATED' }

[T2] Outbox polling job (every 500ms) ─
     ├─ SELECT * FROM payment_outbox WHERE published=FALSE
     ├─ Found: payment.initiated
     ├─ kafkaTemplate.send('payment.initiated', 'pay-123', JSON)
     └─ UPDATE outbox SET published=TRUE ✓

[T3] alias-service @KafkaListener(topics='payment.initiated')
     ├─ Lookup: bob@mail.com
     ├─ Find: { participantId='bank-b', account='ACC-456', maskedName='B*b J*n' }
     ├─ ─ ATOMIC TRANSACTION ─
     │  ├─ SELECT / INSERT audit if needed
     │  ├─ INSERT outbox_event { eventType='alias.resolved', published=FALSE }
     │  └─ COMMIT ✓
     └─ Polling publishes alias.resolved to Kafka

[T4] payment-orchestration @KafkaListener(topics='alias.resolved')
     ├─ ─ ATOMIC TRANSACTION ─
     │  ├─ UPDATE payment SET status='DEBIT_REQUESTED'
     │  ├─ INSERT outbox_event { eventType='payment.debit_requested', published=FALSE }
     │  └─ COMMIT ✓
     └─ Polling publishes payment.debit_requested

[T5] Bank A receives webhook: debit_requested
     ├─ Process locally (Bank A's own transaction):
     │  ├─ UPDATE accounts SET balance = balance - 100 (Alice's account)
     │  ├─ INSERT audit_log (Alice debited 100 CAD)
     │  ├─ INSERT bank_a.outbox_event { eventType='bank_a.debit_accepted' }
     │  └─ COMMIT ✓
     └─ Bank A's polling publishes bank_a.debit_accepted

[T6] payment-orchestration @KafkaListener(topics='bank_a.debit_accepted')
     ├─ ─ ATOMIC TRANSACTION ─
     │  ├─ UPDATE payment SET status='CREDIT_REQUESTED'
     │  ├─ INSERT outbox_event { eventType='payment.credit_requested', published=FALSE }
     │  └─ COMMIT ✓
     └─ Polling publishes payment.credit_requested

[T7] Bank B receives webhook: credit_requested
     ├─ Process locally (Bank B's own transaction):
     │  ├─ Validate account exists ✓
     │  ├─ UPDATE accounts SET balance = balance + 100 (Bob's account)
     │  ├─ INSERT audit_log (Bob credited 100 CAD)
     │  ├─ INSERT bank_b.outbox_event { eventType='bank_b.credit_accepted' }
     │  └─ COMMIT ✓
     └─ Bank B's polling publishes bank_b.credit_accepted

[T8] payment-orchestration @KafkaListener(topics='bank_b.credit_accepted')
     ├─ ─ ATOMIC TRANSACTION ─
     │  ├─ UPDATE payment SET status='SETTLEMENT_TRIGGERED'
     │  ├─ INSERT outbox_event { eventType='payment.settlement_triggered', published=FALSE }
     │  └─ COMMIT ✓
     └─ Polling publishes payment.settlement_triggered

[T9] clearing-settlement @KafkaListener(topics='payment.settlement_triggered')
     ├─ ─ ATOMIC TRANSACTION (IMMUTABLE LEDGER) ─
     │  ├─ INSERT ledger_entry { id=auto, DEBIT, bank-a, -100, paymentId, sig }
     │  ├─ INSERT ledger_entry { id=auto, CREDIT, bank-b, +100, paymentId, sig }
     │  ├─ VERIFY: SUM(DEBIT) == SUM(CREDIT) ✓
     │  ├─ INSERT outbox_event { eventType='clearing.settlement_confirmed', published=FALSE }
     │  └─ COMMIT ✓
     └─ Polling publishes clearing.settlement_confirmed

[T10] payment-orchestration @KafkaListener(topics='clearing.settlement_confirmed')
      ├─ ─ ATOMIC TRANSACTION ─
      │  ├─ UPDATE payment SET status='SETTLED', settled_at=NOW()
      │  ├─ INSERT outbox_event { eventType='payment.settled', published=FALSE }
      │  └─ COMMIT ✓
      └─ Polling publishes payment.settled

[T11] VIREMENT COMPLÈTE ✓
      ├─ Alice's account: -100 CAD (final)
      ├─ Bob's account: +100 CAD (final)
      ├─ BC Ledger: balanced (immutable)
      ├─ payment.status: SETTLED (irrévocable)
      └─ Audit trail: recorded

TOTAL TIME: ~1500–2500 ms
  (accounting for outbox polling delays max ~500ms per event)
```

---

## ✅ Patterns Critiques Intégrés

### 1️⃣ **Idempotence (Exactly-Once)**

```yaml
Mechanism:
  - Client sends: POST /payment/initiate { idempotencyKey: "tx-..." }
  - Service checks: is idempotencyKey in Redis cache?
    └─ YES → return cached paymentId (same payment, no retry)
    └─ NO → create new payment, store { idempotencyKey → paymentId } in Redis (TTL 24h)

Guarantees:
  - Alice retries payment 3x with same idempotencyKey
  - Only 1 debit from Alice's account ✓
  - Only 1 credit to Bob's account ✓
```

### 2️⃣ **Outbox Pattern (Exactly-Once Events)**

```yaml
Architecture:
  ┌─ Business Entity Update + Outbox Event ─ ATOMIC ─┐
  │ @Transactional                                   │
  │   paymentRepo.save(payment)                      │
  │   outboxRepo.save(outboxEvent with published=F) │
  │ COMMIT (both or nothing)                         │
  └──────────────────────────────────────────────────┘
                        │
         ┌─────────────┴──────────────┐
         │                            │
         │ Polling Job                │
         │ (every 500ms)              │
         │                            │
         ├─ SELECT unpublished        │
         ├─ kafkaTemplate.send()      │
         ├─ UPDATE published=TRUE     │
         └─ (retry on Kafka fail)     │
         
Consumer Idempotency (Layer 3):
  @KafkaListener
  public void handle(Event e) {
    if (idempotencyService.isProcessed(e.getId()))
      return;  ← IDEMPOTENT skip
    process(e);
    idempotencyService.mark(e.getId());
  }

Guarantee: Exactly-once delivery even with crashes/retries
```

### 3️⃣ **Ledger Immutability (Append-Only)**

```sql
-- No UPDATE allowed
CREATE TRIGGER prevent_ledger_update
BEFORE UPDATE ON ledger_entry
FOR EACH ROW
BEGIN
  RAISE EXCEPTION 'Immutable';
END;

-- Only INSERT (append)
INSERT INTO ledger_entry (DEBIT, bank-a, -100, paymentId, sig);
INSERT INTO ledger_entry (CREDIT, bank-b, +100, paymentId, sig);

-- Invariant checked
SELECT SUM(amount) FROM ledger_entry WHERE entry_type='DEBIT' 
  = SELECT SUM(amount) FROM ledger_entry WHERE entry_type='CREDIT';

-- Result: Ledger forever balanced, no reversals, full audit trail
```

---

## 📊 Kafka Topic Map (Essentials Only)

| Topic | Producer | Consumer(s) | Key | Use |
|---|---|---|---|---|
| `payment.initiated` | payment-orch | alias-service, control-svc | paymentId | Start saga |
| `alias.resolved` | alias-service | payment-orch | paymentId | Know receiver |
| `control.payment_approved` | control-svc | payment-orch | paymentId | Fraud passed |
| `payment.debit_requested` | payment-orch | Bank A webhook | paymentId | Debit Alice |
| `bank_a.debit_accepted` | Bank A (via event bridge) | payment-orch | paymentId | Debit confirmed |
| `payment.credit_requested` | payment-orch | Bank B webhook | paymentId | Credit Bob |
| `bank_b.credit_accepted` | Bank B (via event bridge) | payment-orch | paymentId | Credit confirmed |
| `payment.settlement_triggered` | payment-orch | clearing-settlement | paymentId | Settle (ledger) |
| `clearing.settlement_confirmed` | clearing-settlement | payment-orch | paymentId | Finality |
| `payment.settled` | payment-orch | audit-service | paymentId | Log complete |

**Kafka Config:**
- Partitions: 10 (by paymentId key → same partition per payment)
- Replication: 2 (HA)
- Retention: 7 days
- Compression: snappy

---

## 🔒 Sécurité & API

### Gateway (KrakenD) :8080

```yaml
Routes:
  POST /api/payment/initiate    → payment-orch :8093
  GET  /api/payment/{id}        → payment-orch :8093
  POST /api/alias               → alias-service :8092
  GET  /api/alias/{key}/resolve → alias-service :8092

Security:
  - CORS enabled (whitelist Bank A/B domains)
  - JWT Bearer token validation (Spring Security)
  - Rate limiting: 1000 req/sec per participant
  - HTTPS/TLS 1.3 (enforced)
  - HSTS headers (Strict-Transport-Security)

Headers forwarded:
  - Authorization (JWT)
  - Content-Type
  - Idempotency-Key (for POST /payment/initiate)
  
Headers blocked:
  - Cookies
  - X-Forwarded-*
  - Any custom headers not whitelisted
```

---

## 📈 Observabilité (4 Golden Signals)

### Prometheus Metrics

```yaml
# 1. LATENCY
payment_request_duration_seconds{endpoint,status}
  - P50, P95, P99 quantiles
  - Target: P95 < 500ms (lookup), < 1000ms (full saga)

# 2. TRAFFIC
http_requests_total{service,endpoint,method}
  - Count of requests per endpoint
  - Target: ≥500 req/sec sustained (1000+ bonus)

# 3. ERRORS
http_requests_failed_total{service,endpoint,reason}
  - Failed requests by reason (timeout, invalid, rejected)
  - Target: < 0.1% error rate

# 4. SATURATION
kafka_consumer_lag_sum{service,topic}
  - Queue backlog (how many events pending)
  - Target: < 1000 events (no bottleneck)

database_pool_active_connections{service}
  - Active DB connections
  - Target: < 80% utilization
```

### Grafana Dashboards

1. **Payment Flow Overview**
   - Payment initiate → settlement latency (histogram)
   - Success rate by status (SETTLED vs FAILED)
   - Throughput (ops/sec)

2. **Service Health**
   - Per-service availability (%)
   - P95 latency per service
   - Error rate per service

3. **Ledger Integrity**
   - Total DEBIT entries vs CREDIT entries (should match)
   - Last settlement timestamp
   - Any orphaned payments (no ledger entries)

---

## 🧪 Tester (Essentials)

### Unit Tests
- [ ] Idempotency check: same idempotencyKey returns cached paymentId
- [ ] Payment state transitions (valid sequences)
- [ ] Ledger invariants (sum(DEBIT) == sum(CREDIT))
- [ ] Alias masking logic (correct masking of names)

### Integration Tests
- [ ] Full saga happy path (Alice → Bob, 100 CAD, SETTLED)
- [ ] Saga with debit rejection → compensation triggered
- [ ] Outbox reliability: mark published=TRUE only after Kafka ack
- [ ] Consumer idempotency: same event processed once

### Load Tests (k6)

```javascript
// smoke-test.js
export const options = { stages: [{ duration: '1m', target: 10 }] };
export default function() {
  const res = http.post('http://localhost:8080/api/payment/initiate', {
    idempotencyKey: `payment-${Date.now()}`,
    participantId: "bank-a",
    alias: 'bob@mail.com',
    amount: 100.00,
  });
  check(res, {
    'status is 201': (r) => r.status === 201,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}

// load-test.js
export const options = {
  stages: [
    { duration: '5m', target: 100 },   // ramp
    { duration: '10m', target: 100 },  // sustain
    { duration: '5m', target: 0 },     // ramp down
  ],
  thresholds: {
    'http_req_duration': ['p(95) < 500'],
    'http_req_failed': ['rate < 0.01'],
  },
};
```

---

## 🚀 Déploiement (Docker + Compose)

```yaml
# docker-compose.yaml

version: '3.8'

services:
  # Database
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: canbankx
    volumes:
      - ./db-init/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "3306:3306"

  # Cache + Idempotency/Outbox tracking
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  # Event Broker
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  # Observability
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    ports:
      - "3000:3000"
    depends_on:
      - prometheus

  # API Gateway
  krakend:
    image: devopsfaith/krakend:latest
    volumes:
      - ./krakend/krakend.json:/etc/krakend/krakend.json
    ports:
      - "8080:8080"
    depends_on:
      - alias-service
      - payment-orchestration
      - clearing-settlement

  # Microservices
  alias-service:
    build: ./alias-service
    ports:
      - "8092:8092"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/db_alias
      KAFKA_BROKERS: kafka:9092
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis
      - kafka

  payment-orchestration:
    build: ./payment-orchestration
    ports:
      - "8093:8093"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/db_payment
      KAFKA_BROKERS: kafka:9092
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis
      - kafka

  clearing-settlement:
    build: ./clearing-settlement
    ports:
      - "8094:8094"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/db_clearing
      KAFKA_BROKERS: kafka:9092
    depends_on:
      - mysql
      - kafka

volumes:
  mysql_data:
  kafka_data:
```

---

## 📋 Checklist d'Implémentation

### Sprint 1 (US2, US4 — Directory)
- [ ] `alias-service` schema + entities
- [ ] Outbox polling job
- [ ] POST /alias (register)
- [ ] GET /alias/{key}/resolve (with Redis cache)
- [ ] Unit tests: uniqueness, masking
- [ ] Kafka topic: alias.resolved

### Sprint 2 (US5 — Orchestration Setup)
- [ ] `payment-orchestration` schema + Payment entity
- [ ] Idempotency check (idempotencyKey → paymentId cache)
- [ ] POST /payment/initiate (atomic: entity + outbox)
- [ ] Outbox polling job
- [ ] State machine (INITIATED → DEBIT_REQUESTED → ...)
- [ ] Unit tests: idempotence, state transitions
- [ ] Kafka topics: payment.*, control.payment_*

### Sprint 3 (US6, US7 — Completion)
- [ ] `clearing-settlement` schema + LedgerEntry
- [ ] Ledger immutability (triggers)
- [ ] Webhook handlers for Bank A/B responses
- [ ] Settlement flow (POST → ledger inserts)
- [ ] Ledger invariant checks (SUM DEBIT == SUM CREDIT)
- [ ] Integration tests: full saga happy path
- [ ] Kafka topics: bank_*.*, clearing.*

### Sprint 4 (Observability + Load)
- [ ] Prometheus metrics + Grafana dashboards
- [ ] k6 smoke test + load test
- [ ] Chaos testing (kill services mid-saga)
- [ ] Performance baselines (Phase 1 vs Phase 2)
- [ ] Documentation: Arc42, ADRs
- [ ] Contract tests (Bank A/B API agreements)

---

## 🎯 Success Criteria

| Criterion | Target | Verified By |
|---|---|---|
| **All 6 US implemented** | US1, US2, US4, US5, US6, US7 | Integration tests pass |
| **Idempotence** | Same idempotencyKey → same paymentId, no double-charge | Idempotency test |
| **Exactly-once settlement** | No duplicate ledger entries on retry | Load test + chaos test |
| **Latency P95** | ≤ 500 ms (lookup), ≤ 1000 ms (full saga) | k6 load test |
| **Throughput** | ≥ 500 ops/sec | k6 load test, sustained |
| **Ledger balance** | SUM(DEBIT) == SUM(CREDIT) always | Grafana alert |
| **Outbox reliability** | 100% event delivery (0 lost events) | Chaos test |
| **Observability** | 4 Golden Signals on Grafana | Dashboard visible |

---

## 📊 Taille du Code Attendu

| Component | Lines | Complexity |
|---|---|---|
| alias-service | ~500 | Low (CRUD + cache) |
| payment-orchestration | ~1000 | Medium (state machine, saga) |
| clearing-settlement | ~400 | Low (append-only ledger) |
| Outbox polling jobs | ~300 | Low (polling + retry) |
| Tests (unit + integration) | ~2000 | Medium |
| Configuration (Kafka, DB, K8s) | ~500 | Low |
| **Total est.** | **~5000–6000** | **Implementable in 4 sprints** |

---

**Prochaines Étapes:**
1. Créer les schémas MySQL (3 services)
2. Implémenter alias-service (US2, US4)
3. Implémenter payment-orchestration (US1, US5)
4. Implémenter clearing-settlement (US7)
5. Tests + load test
6. Documentation

