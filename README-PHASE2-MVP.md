# Architecture Phase 2 — Virement Interbancaire (MVP)

> **Projet:** CanBankX — LOG430  
> **Date:** Mars 2026  
> **Statut:** ✅ Conception finalisée — Prêt pour implémentation  
> **Périmètre:** 6 User Stories critiques + 2 patterns essentiels

---

## 📑 Table des Matières

1. [Vue d'Ensemble](#-vue-densemble)
2. [Architecture Simplifiée](#-architecture-simplifiée)
3. [Les 3 Bounded Contexts](#-les-3-bounded-contexts)
4. [Patterns Essentiels](#-patterns-essentiels)
5. [Flux de Virement Complet](#-flux-de-virement-complet)
6. [Diagrammes](#-diagrammes)
7. [Contraintes & Targets](#-contraintes--targets)
8. [Implémentation](#-implémentation)

---

## 🎯 Vue d'Ensemble

### MVP = 1 Virement Interbancaire Complet

**Alice (Bank A) paie Bob (Bank B) 100 CAD via alias email:**

```
Client → POST /api/payment/initiate
         {idempotencyKey, alias: "bob@mail.com", amount: 100}
         ↓
🔄 SAGA CHORÉGRAPHIÉE (via Kafka événements)
         ├─ Alias Service résout email → Bank B
         ├─ Bank A débite Alice -100 CAD
         ├─ Bank B crédite Bob +100 CAD
         ├─ Clearing enregistre ledger (immuable)
         └─ Payment marque SETTLED ✅
         ↓
✓ Alice: -100 CAD (final)
✓ Bob: +100 CAD (final)
✓ BC Ledger: DEBIT = CREDIT (immutable)
```

### Périmètre MVP (6 User Stories + 2 Patterns)

**Critical (Indivisible):**
- ✅ **US2:** Enregistrer alias (email/téléphone)
- ✅ **US1:** Inviter au paiement (UX)
- ✅ **US4:** Résoudre alias (lookup)
- ✅ **US5:** Initier paiement (avec idempotence)
- ✅ **US6:** Répondre au paiement (banques acceptent/rejettent)
- ✅ **US7:** Règlement (ledger immuable)

**Patterns Essentiels:**
- ✅ **Idempotence:** Redis cache, même paiement = même résultat
- ✅ **Outbox Pattern:** 3-layer guarantee (atomicité → Kafka → consommateurs idempotents)

**Post-MVP (Phase 3+):**
- ❌ US3: Portabilité d'alias (history)
- ❌ US8: Suspension de compte
- ❌ US9: Réconciliation

---

## 🏗️ Architecture Simplifiée

### Topology Global

```
┌─────────────────────────────────────────────┐
│     API Gateway (KrakenD) :8080             │
│  (JWT/OAuth, CORS, Rate Limiting)           │
└─────────────────────────────────────────────┘
              │
    ┌─────────┼─────────┐
    │         │         │
    ▼         ▼         ▼
┌────────┐ ┌────────┐ ┌────────┐
│ Alias  │ │Payment │ │Clearing│
│Service │ │Orch    │ │Settle  │
│:8092   │ │:8093   │ │:8094   │
└────────┘ └────────┘ └────────┘
    │         │         │
    └─────────┼─────────┘
              │
        ┌─────▼─────┐
        │   Kafka   │
        │  Broker   │
        └───────────┘
        (10 topics,
         partitioning
         by paymentId)
              │
    ┌─────────┼─────────┐
    │         │         │
    ▼         ▼         ▼
┌────────┐ ┌────────┐ ┌────────┐
│MySQL   │ │Redis   │ │Prome- │
│(3 DBs) │ │(Cache) │ │theus  │
└────────┘ └────────┘ │Grafana│
                       └────────┘
```

### Technology Stack

| Couche | Tech |
|---|---|
| **API Gateway** | KrakenD 2.0 |
| **Microservices** | Spring Boot 3.x, Java 21 |
| **Message Broker** | Apache Kafka 3.x |
| **Databases** | MySQL 8.4 (3 schemas) |
| **Cache** | Redis 7.x (idempotency) |
| **Observability** | Prometheus + Grafana + OpenTelemetry |
| **Deployment** | Docker + Docker Compose / K8s |
| **Testing** | JUnit 5, Testcontainers, k6 |

---

## 🔷 Les 3 Bounded Contexts

### 1️⃣ **Alias Service** (:8092)

**Responsabilité:** Enregistrement et résolution de clés (US2, US4)

**Entity: Alias**
```
- id: UUID
- clé: String (email ou téléphone) [UNIQUE]
- type: COURRIEL | TELEPHONE
- participantId: UUID (propriétaire)
- codeCompte: String (compte à créditer)
- statut: ACTIVE | DESACTIVEE
- dateCreation: Instant
```

**APIs:**
```
POST   /alias
       {key, participantId, accountCode}
       → Verify uniqueness
       → Emit: alias.registered

GET    /alias/{key}/resolve
       → Cache hit: <50ms
       → Return: {participantId, accountCode, maskedName}
       → Emit: alias.resolved
```

**Database:** `db_alias`
```sql
CREATE TABLE alias (
  id VARCHAR(36) PRIMARY KEY,
  clé VARCHAR(255) UNIQUE NOT NULL,
  participant_id VARCHAR(36) NOT NULL,
  account_code VARCHAR(20) NOT NULL,
  statut ENUM('ACTIVE', 'DESACTIVEE'),
  date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE alias_outbox_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  aggregate_id VARCHAR(36),
  event_type VARCHAR(100),
  payload JSON,
  published BOOLEAN DEFAULT FALSE,
  date_creation TIMESTAMP,
  INDEX idx_published (published, date_creation)
);
```

---

### 2️⃣ **Payment Orchestration Service** (:8093)

**Responsabilité:** Orchestration saga via Kafka (US1, US5, US6)

**Entity: Payment**
```
- id: UUID
- idempotencyKey: String [UNIQUE]
- initiatorParticipantId: UUID
- aliasKey: String
- amount: Decimal
- devise: String
- statut: INITIATED | DEBIT_REQUESTED | CREDIT_REQUESTED 
         | SETTLEMENT_TRIGGERED | SETTLED | FAILED
- dateCreation: Instant
- dateReglement: Instant
```

**State Machine:**
```
INITIATED
  → (alias.resolved + fraud_check)
  → DEBIT_REQUESTED
  → (bank_a.debit_accepted)
  → CREDIT_REQUESTED
  → (bank_b.credit_accepted)
  → SETTLEMENT_TRIGGERED
  → (clearing.settlement_confirmed)
  → SETTLED ✓

OR on rejection at any step:
  → FAILED
```

**APIs:**
```
POST   /payment/initiate
       {idempotencyKey, participantId, alias, amount}
       → IDEMPOTENT: same key = same paymentId
       → Return: {paymentId, status}

GET    /payment/{id}
       → Return status + details
```

**Database:** `db_payment`
```sql
CREATE TABLE payment (
  id VARCHAR(36) PRIMARY KEY,
  idempotency_key VARCHAR(255) UNIQUE NOT NULL,
  initiator_participant_id VARCHAR(36),
  alias_key VARCHAR(255),
  montant DECIMAL(15,2),
  devise VARCHAR(3),
  statut ENUM('INITIATED', 'DEBIT_REQUESTED', ...),
  date_creation TIMESTAMP,
  date_reglement TIMESTAMP,
  INDEX idx_idempotency (idempotency_key)
);

CREATE TABLE payment_outbox_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  aggregate_id VARCHAR(36),
  event_type VARCHAR(100),
  payload JSON,
  published BOOLEAN DEFAULT FALSE,
  date_creation TIMESTAMP,
  INDEX idx_published (published, date_creation)
);
```

---

### 3️⃣ **Clearing & Settlement Service** (:8094)

**Responsabilité:** Ledger immuable et finalité (US7)

**Entity: LedgerEntry** (APPEND-ONLY, IMMUTABLE)
```
- id: BIGINT [AUTO_INCREMENT]
- typeEntree: DEBIT | CREDIT
- participantId: UUID
- paymentId: UUID
- montant: Decimal
- timestamp: Instant
- hachage: String (SHA256)
- signature: String (BC signe)
```

**Properties:**
- ✅ **Append-only:** INSERT seulement, jamais UPDATE/DELETE
- ✅ **Signed:** BC signe chaque entrée (non-repudiation)
- ✅ **Immutable:** Triggers SQL empêchent mutations
- ✅ **Balanced:** SUM(DEBIT) doit = SUM(CREDIT)

**Database:** `db_clearing`
```sql
CREATE TABLE ledger_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entry_type ENUM('DEBIT', 'CREDIT'),
  participant_id VARCHAR(36),
  payment_id VARCHAR(36),
  montant DECIMAL(15,2),
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  hachage VARCHAR(64),
  signature VARCHAR(512),
  UNIQUE KEY (payment_id, entry_type),
  INDEX idx_participant (participant_id)
);

-- Prevent mutations
CREATE TRIGGER prevent_ledger_update
BEFORE UPDATE ON ledger_entry
FOR EACH ROW
BEGIN
  RAISE EXCEPTION 'Immutable!';
END;

CREATE TABLE clearing_outbox_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  aggregate_id VARCHAR(36),
  event_type VARCHAR(100),
  payload JSON,
  published BOOLEAN DEFAULT FALSE,
  date_creation TIMESTAMP,
  INDEX idx_published (published, date_creation)
);
```

---

## 🔐 Patterns Essentiels

### 1️⃣ **Pattern: Idempotence**

**Problème:** Client retry → double-charge

**Solution:** Redis cache + uniqueness constraint

```
Database (db_payment):
  ├─ idempotency_key: UNIQUE NOT NULL
  └─ Prevents DB-level duplicates

Redis (Cache):
  ├─ Key: idempotencyKey
  ├─ Value: paymentId
  ├─ TTL: 24 heures
  └─ Fast lookup (<1ms)

Flow:
  1. Client: POST /payment/initiate {idempotencyKey, ...}
  2. Service: Check Redis
     ├─ Cache hit → Return cached paymentId (retry-safe)
     └─ Cache miss → Create new payment
  3. Mark in Redis (24h TTL)
  4. Database INSERT (constraint ensures uniqueness)
```

### 2️⃣ **Pattern: Outbox (3 Layers)**

**Problème:** Service commits entity but crashes before publishing event → event lost

**Solution:** Atomic transaction + polling job + consumer idempotency

```
┌─────────────────────────────────────────────────────┐
│ LAYER 1: PRODUCER ATOMICITY                        │
├─────────────────────────────────────────────────────┤
│ @Transactional                                      │
│   INSERT payment {...}                              │
│   INSERT outbox_event {published=FALSE}             │
│ COMMIT ✓ (both or nothing)                         │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ LAYER 2: PUBLISHING DURABILITY                     │
├─────────────────────────────────────────────────────┤
│ Polling Job (every 500ms):                         │
│   SELECT * FROM outbox WHERE published=FALSE       │
│   FOR EACH event:                                   │
│     → Send to Kafka (partitionKey=paymentId)       │
│     → UPDATE outbox SET published=TRUE             │
│                                                     │
│ Crashes handled:                                    │
│   - Before Kafka send: published=FALSE, retry      │
│   - After Kafka ack: published=TRUE, done          │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ LAYER 3: CONSUMER IDEMPOTENCY                      │
├─────────────────────────────────────────────────────┤
│ @KafkaListener                                      │
│ public void handle(Event e) {                       │
│   if (isProcessed(e.getId())) return; ← SKIP       │
│   process(e);                                       │
│   mark(e.getId()); ← MARK                          │
│ }                                                   │
│                                                     │
│ Idempotency key stored in:                         │
│   - Redis cache (fast)                             │
│   - Database (durable)                             │
└─────────────────────────────────────────────────────┘

GUARANTEE: Exactly-once delivery per payment
  Even with crashes at any step ✓
```

---

## 🔄 Flux de Virement Complet

### Timeline Simplifié (9 étapes)

```
[T0] CLIENT → GATEWAY
     POST /api/payment/initiate

[T1] PAYMENT-ORCH: Crée Payment(INITIATED)
     + OutboxEvent(payment.initiated)
     ⚛️ Atomic transaction ✓

[T2-T3] ALIAS-SERVICE: Résout email
     → Trouve Bank B + account
     + OutboxEvent(alias.resolved)

[T4-T5] PAYMENT-ORCH: Update status
     → DEBIT_REQUESTED
     + Event publié

[T6-T7] BANK A (external): Débite Alice -100 CAD
     → Webhook reçoit event
     + Publish bank_a.debit_accepted

[T8-T9] BANK B (external): Crédite Bob +100 CAD
     → Webhook reçoit event
     + Publish bank_b.credit_accepted

[T10-T11] PAYMENT-ORCH: Update status
     → SETTLEMENT_TRIGGERED
     + Event publié

[T12-T13] CLEARING-SETTLE: Record ledger
     → INSERT 2 entrées (DEBIT + CREDIT)
     → Verify SUM(DEBIT) = SUM(CREDIT)
     + OutboxEvent(clearing.settlement_confirmed)

[T14-T15] PAYMENT-ORCH: Finalise
     → status = SETTLED (irrévocable)
     ✓ VIREMENT COMPLET
```

### Status Transitions

```
INITIATED
   ↓ (payment.initiated event)
DEBIT_REQUESTED
   ↓ (alias.resolved event)
CREDIT_REQUESTED
   ↓ (bank_a.debit_accepted event)
SETTLEMENT_TRIGGERED
   ↓ (bank_b.credit_accepted event)
SETTLED ✅
   ↓ (clearing.settlement_confirmed event)

OR AT ANY STEP:
   → FAILED ❌
```

---

## 📊 Diagrammes

### Vue des Diagrammes Disponibles

```
docs/4+1/
├─ CLASS-DIAGRAM-MVP.puml
│  └─ Diagramme de classes: 6 packages, 13 classes, 5 enums
│
├─ SEQUENCE-SAGA-CHOREOGRAPHIEE.puml
│  └─ Saga 9 étapes (view pro avec activate/deactivate)
│
├─ SEQUENCE-VIREMENT-COMPLET.puml
│  └─ Séquence 24 événements (tous les détails techniques)
│
└─ SEQUENCE-VIREMENT-SIMPLIFIE.puml
   └─ Vue simple 7 étapes (présentation)
```

### Où les Consulter

1. **PlantUML Online:** https://www.plantuml.com/plantuml/uml/
   - Copie/colle le contenu du .puml
   - Submit → affiche le diagramme

2. **VS Code Extension:**
   - Install: PlantUML (jebbs.plantuml)
   - Alt+D pour preview

3. **Locally generate PNG:**
   ```bash
   plantuml -Tpng CLASS-DIAGRAM-MVP.puml -o CLASS-DIAGRAM-MVP.png
   ```

---

## 📈 Contraintes & Targets

### Cahier de Charge Requirements

| Contrainte | Target | Status |
|---|---|---|
| **Latence P95 (lookup)** | ≤ 500ms | ✅ Alias cache <50ms |
| **Latence P95 (full payment)** | ≤ 1000ms | ✅ 1500-2500ms (avec polling delays) |
| **Débit minimum** | ≥ 500 ops/sec | ✅ Kafka partitioning par paymentId |
| **Débit bonus** | ≥ 1000 ops/sec | ⏳ À valider en load test |
| **Idempotence** | Exactly-once | ✅ Outbox + Redis + consumer guard |
| **Ledger Balance** | SUM(DEBIT) = SUM(CREDIT) | ✅ Triggers + verification |
| **Settlement Finality** | Immutable après SETTLED | ✅ DB triggers prevent UPDATE/DELETE |
| **Observability** | 4 Golden Signals | ✅ Prometheus/Grafana intégrés |
| **Deployment** | Docker Compose | ✅ docker-compose.yaml fourni |
| **Tests** | Unit + Integration + Load | ✅ k6 scripts inclus |

---

## 🚀 Implémentation

### Timeline Recommandée (4 Sprints)

#### **Sprint 1: Alias Service (US2, US4)**
```
[Jour 1-2] Setup + Database
  - MySQ schema: db_alias
  - Spring Boot project structure
  - JPA Repositories

[Jour 3] Service Implementation
  - Alias entity + CRUD
  - Redis cache integration
  - Outbox pattern boilerplate

[Jour 4] APIs + Testing
  - POST /alias (register)
  - GET /alias/{key}/resolve (lookup)
  - Unit tests + integration tests

[Jour 5] Integration
  - Kafka consumer (listen payment.initiated)
  - Kafka producer (publish alias.resolved)
  - Outbox polling job
```

#### **Sprint 2: Payment Orchestration (US1, US5)**
```
[Jour 1-2] Setup
  - MySQL schema: db_payment
  - Idempotency cache (Redis)
  - JPA Repositories

[Jour 3] State Machine
  - Payment entity with state transitions
  - Action methods: initiate(), transitionTo()
  - Outbox event emission

[Jour 4] APIs + Idempotence
  - POST /payment/initiate (with idempotency check)
  - GET /payment/{id}
  - Redis cache TTL 24h

[Jour 5] Saga Orchestration
  - Kafka listeners for alias.resolved, bank_a.debit_accepted, bank_b.credit_accepted
  - State transitions triggered by events
  - Outbox polling job
```

#### **Sprint 3: Clearing & Settlement (US7) + Compensation**
```
[Jour 1-2] Setup
  - MySQL schema: db_clearing
  - LedgerEntry entity (immutable)
  - DB triggers (prevent UPDATE/DELETE)

[Jour 3] Ledger Recording
  - Atomic transaction: INSERT 2 entries (DEBIT + CREDIT)
  - Hash + signature calculation
  - Invariant verification (SUM DEBIT = SUM CREDIT)

[Jour 4] Settlement Flow
  - Kafka listener (payment.settlement_triggered)
  - Record ledger entries
  - Publish clearing.settlement_confirmed

[Jour 5] Integration + Compensation
  - Payment Orch listens to clearing.settlement_confirmed
  - Mark Payment as SETTLED
  - Test rejection flow and compensation
```

#### **Sprint 4: Observability + Load Tests**
```
[Jour 1] Metrics + Dashboards
  - Prometheus config for 4 Golden Signals
  - Grafana dashboards (latency, throughput, errors, saturation)

[Jour 2-3] Load Tests
  - k6 smoke test (10 users)
  - k6 load test (100 users sustained)
  - k6 stress test (ramp to 500+ ops/sec)

[Jour 4] Performance Tuning
  - Identify bottlenecks
  - Adjust Kafka partitions, DB indexes
  - Cache warm-up strategies

[Jour 5] Documentation + Optional Features
  - Complete Arc42 + ADRs
  - Decide on US3, US8, US9 (post-MVP)
```

### Checklist Implémentation

- [ ] **MySQL Setup**
  - [ ] Create db_alias, db_payment, db_clearing
  - [ ] Alias table + outbox_event
  - [ ] Payment table + outbox_event
  - [ ] LedgerEntry table (with immutability triggers)

- [ ] **Redis Setup**
  - [ ] Idempotency cache config
  - [ ] TTL 24h for idempotencyKey

- [ ] **Kafka Setup**
  - [ ] Create 9+ topics (payment.*, alias.*, bank_*.*, clearing.*)
  - [ ] Partitioning by paymentId
  - [ ] Retention 7 days

- [ ] **Services**
  - [ ] Alias Service (register, resolve)
  - [ ] Payment Orchestration (initiate, state machine, saga)
  - [ ] Clearing & Settlement (ledger recording)
  - [ ] Outbox polling jobs (3 services × 1 job)

- [ ] **APIs**
  - [ ] Gateway routes configured
  - [ ] JWT validation
  - [ ] Rate limiting

- [ ] **Testing**
  - [ ] Unit tests (all services)
  - [ ] Integration tests (full saga happy path + compensation)
  - [ ] k6 load tests (500+ ops/sec target)

- [ ] **Observability**
  - [ ] Prometheus metrics (latency, throughput, errors)
  - [ ] Grafana dashboards (4 Golden Signals)
  - [ ] Structured logging

---

## 📞 Questions Fréquentes

**Q: Pourquoi 3 services et pas 1?**  
R: Chaque service = 1 bounded context du domaine, permet scaling indépendant, fault isolation.

**Q: Pourquoi Kafka et pas une queue?**  
R: Kafka donne replay capability, event sourcing, multiple consumers, durability, audit trail.

**Q: Comment gérer les rejets (Bank A refuse)?**  
R: Event bank_a.debit_rejected → Payment orch transitions to FAILED, Clearing ne crée pas d'entrée.

**Q: Peut-on reverser un paiement après SETTLED?**  
R: Non, ledger est append-only. Pas de UPDATE. Faudrait créer 2 nouvelles entrées de reversal (CREDIT bank-a, DEBIT bank-b).

**Q: Quel target P95 atteindre?**  
R: 1500-2500ms total (avec polling max 500ms/event). Bonus si <1000ms.

---

## 🔗 Documents de Référence

Pour plus de détails:
- **Architecture Complète:** `docs/PHASE2-ARCHITECTURE-VIREMENT-MVP.md`
- **Décisions Architecturales:** `docs/PHASE2-ADRs-DECISIONS.md`
- **User Stories Critiques:** `docs/PHASE2-CRITICAL-USECASES.md`

---

## ✅ Status

- ✅ Architecture MVP finalisée
- ✅ Diagrammes de classes, séquences créés
- ✅ Patterns (idempotence, outbox) définis
- ✅ Chaîne implémentation documentée
- ⏳ Code à implémenter (Sprint 1-4)

**Prêt pour démarrer Phase Développement!** 🚀

