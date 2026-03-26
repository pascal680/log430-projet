# Dossier Décisionnel Phase 2 — ADRs MVP

> **Date :** Mars 2026  
> **Projet :** CanBankX — LOG430  
> **Périmètre :** Decisions MVP UNIQUEMENT (virement interbancaire)  
> **Préparé par :** Pascal Bourgoin

---

## 📋 ADRs MVP (4 décisions essentielles)

### ADR-007: Event-Driven Architecture with Kafka

**Status:** PROPOSED

**Context:**
- Phase 1 uses synchronous REST calls between microservices
- Payment orchestration is blocking (orchestrator waits for debit response before credit)
- Tight coupling makes it difficult to scale individual services independently
- Need to handle 1000+ ops/sec with ~2 sec full settlement latency

**Decision:**
Adopt **Apache Kafka** as the event broker for:
1. **Decoupling** services by topic subscriptions (publisher doesn't know subscribers)
2. **Asynchronous pipelining** (fraud check runs in parallel, not sequentially)
3. **Event sourcing** (full audit trail via Kafka log)
4. **Horizontal scaling** (each service scales independently based on load)

**Implementation Details:**

```yaml
Kafka Topic Mapping:
  
  payment.initiated
    └─ Producers: payment-orchestration-service
    └─ Consumers: directory-service (lookup alias), control-service (fraud), 
                  payment-orchestration (internal state machine)

  alias.resolved
    └─ Producers: directory-service
    └─ Consumers: payment-orchestration (routing decision), control-service (risk check)

  control.payment_approved / .rejected
    └─ Producers: control-service
    └─ Consumers: payment-orchestration (proceed or reject)

  payment.debit_requested / .credit_requested
    └─ Producers: payment-orchestration-service
    └─ Consumers: None (webhook callbacks to Bank A/B instead)

  bank_a.debit_accepted / .rejected
    └─ Producers: Bank A (via webhook handler → internal event bridge)
    └─ Consumers: payment-orchestration-service

  payment.settlement_triggered
    └─ Producers: payment-orchestration-service
    └─ Consumers: clearing-settlement-service, audit-service

  clearing.settlement_confirmed
    └─ Producers: clearing-settlement-service
    └─ Consumers: payment-orchestration-service, audit-service

  audit.*
    └─ Producers: All services (via outbox polling)
    └─ Consumers: audit-service (append to immutable log)

---

Partitioning Strategy:
  
  Key: paymentId (ensures all events for same payment go to same partition)
  Partitions: 10 (allows parallel processing of different payments)
  Replication: 2 (HA)
  Retention: 7 days (sufficient for replay, older events off-cloud)
  Compression: Snappy (balance speed vs. size)

---

Consumer Groups:
  
  group="payment-orchestration-svc"
    ├─ topic=alias.resolved
    ├─ topic=control.payment_approved
    ├─ topic=bank_a.debit_accepted
    └─ topic=bank_b.credit_accepted
    
  group="clearing-settlement-svc"
    └─ topic=payment.settlement_triggered
    
  group="audit-svc"
    └─ topic=* (all events via wildcard or explicit list)

---

Consumer Implementation (Spring Kafka):

@Service
public class PaymentSagaOrchestrator {

  @KafkaListener(
    topics = "alias.resolved",
    groupId = "payment-orchestration-svc",
    concurrency = "5"
  )
  public void handleAliasResolved(@Payload AliasResolvedEvent event) {
    Payment payment = paymentRepo.findById(event.getPaymentId());
    payment.setState(PaymentState.BENEFICIARY_IDENTIFIED);
    payment.setReceiverParticipantId(event.getReceiverParticipantId());
    paymentRepo.save(payment);
    
    // Emit next event
    eventPublisher.publishEvent(new PaymentDebitRequestedEvent(...));
  }

  @KafkaListener(topics = "bank_a.debit_accepted", groupId = "payment-orchestration-svc")
  public void handleDebitAccepted(@Payload DebitAcceptedEvent event) {
    // Proceed to credit phase
  }

  @KafkaListener(topics = "bank_a.debit_rejected", groupId = "payment-orchestration-svc")
  public void handleDebitRejected(@Payload DebitRejectedEvent event) {
    // Trigger compensation (refund)
  }
}
```

**Consequences:**
- ✅ Loose coupling: adding new consumer (e.g., analytics service) requires no changes to producers
- ✅ Async processing: fraud check & alias resolution happen in parallel (500 ms total vs 1000 ms sequential)
- ✅ Durability: Kafka log acts as audit trail; messages can be replayed if consumer crashes
- ❌ Complexity: Distributed tracing becomes essential (must track via correlationId)
- ❌ Eventual consistency: settled payment state may lag behind event ingestion by milliseconds
- ❌ Storage: Kafka cluster needs disk space for 7-day retention

**Risks Mitigated:**
- Consumer failure → restart from last committed offset (automatic)
- Producer failure → messages stay in Kafka until processed
- Topic imbalance → partition strategy by paymentId ensures even distribution

---

### ADR-008: Saga Pattern for Payment Orchestration

**Status:** PROPOSED

**Context:**
- Payment involves **coordinating two independent organizations** (Bank A, Bank B)
- No distributed transaction (2PC) available across bank boundaries
- Must handle partial failures (Bank A debits but Bank B rejects)
- Must guarantee compensation (rollback) if credit fails

**Decision:**
Implement **Choreography-based Saga** (not Orchestration-based):

| Aspect | Choreography | Orchestration |
|---|---|---|
| Controller | **Distributed events** | Central orchestrator service |
| Coupling | Loose (services react to events) | Tight (all logic in orchestrator) |
| Complexity | Implicit (flow via events) | Explicit (state machine in code) |
| Debugging | Harder (distributed) | Easier (one service) |

**We choose Choreography because:**
1. Banks (Bank A, Bank B) are **external systems** — cannot force them to use orchestrator
2. Banks emit events asynchronously (webhook callbacks) — orchestrator must **react**, not control
3. Loose coupling matters for operational independence

**Saga Flow (Choreography):**

```
[payment.initiated]
    │
    ├─→ [directory-service] resolve alias
    │   └─→ [alias.resolved]
    │
    ├─→ [control-service] check fraud
    │   └─→ [control.payment_approved]
    │
    ├─→ [payment-orchestration] accumulate state
    │
    ├─→ [orchestration] emit [payment.debit_requested]
    │   └─→ Bank A webhook
    │       └─→ Bank A processes locally
    │           └─→ Bank A emits [bank_a.debit_accepted/rejected]
    │
    ├─→ [payment-orchestration] on debit_accepted, emit [payment.credit_requested]
    │   └─→ Bank B webhook
    │       └─→ Bank B processes locally
    │           └─→ Bank B emits [bank_b.credit_accepted/rejected]
    │
    ├─→ [payment-orchestration] on both accepted, emit [payment.settlement_triggered]
    │   └─→ [clearing-settlement] create ledger entries
    │       └─→ [clearing.settlement_confirmed]
    │
    └─→ [SETTLED] ✓ (or REJECTED if any step failed)
```

**Compensation (Rollback):**

```
If bank_b.credit_rejected → [payment.debit_compensation_required]
    └─→ Bank A webhook: reverse debit (credit Alice back 100 CAD)
        └─→ Bank A emits [bank_a.credit_for_reversal_accepted]
            └─→ [payment.failed] FINAL STATE

Timeline: Compensation completes in ~500 ms (single bank action)
```

**State Machine (payment-orchestration-service):**

```
         [INITIATED]
              │
              ▼
      (lookup + fraud check: parallel)
              │
         ┌────┴────┐
         ▼         ▼
   [LOOKUP_OK] [FRAUD_REJECTED] → [REJECTED] ✓
         │
         ▼
      [DEBIT_REQUESTED]
              │
         ┌────┴─────────┐
         ▼              ▼
   [DEBIT_ACCEPTED]  [DEBIT_REJECTED]
         │              │
         ▼              ▼
 [CREDIT_REQUESTED] [COMPENSATION_PENDING]
         │              │
    ┌────┴─────────┐    └─────→ [COMPENSATION_DONE] → [REJECTED] ✓
    ▼              ▼
[CREDIT_ACCEPTED] [CREDIT_REJECTED]
    │              │
    ▼              └─────→ [COMPENSATION_PENDING] → [COMPENSATION_DONE] → [REJECTED] ✓
[SETTLEMENT_TRIGGERED]
    │
    ▼
[SETTLED] ✓
```

---

### ADR-009: Outbox Pattern for Exactly-Once Guarantee

**Status:** PROPOSED

**Problem:**
```
Naïve approach (FAILS):
  1. paymentRepo.save(payment)         // DB
  2. kafkaTemplate.send(event)         // Kafka

❌ Server crashes between 1 & 2
   → DB has payment, but Kafka never gets event
   → Database and event log are out of sync
```

**Solution: Transactional Outbox**

```
ATOMIC transaction (both or nothing):
  BEGIN
    1. paymentRepo.save(payment)       // DB
    2. outboxRepo.save(outboxEvent)    // Same DB, same transaction
  COMMIT
    → Both succeed or both fail

SEPARATE polling job:
  SELECT * FROM outbox WHERE published = false
    → For each: publishToKafka(event)
               UPDATE outbox SET published = true
```

**Implementation (Spring Data JPA + Kafka):**

```java
// 1. Outbox entity
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  @Id @GeneratedValue
  private Long id;
  
  private String aggregateRootId;        // paymentId
  private String eventType;              // "payment.settled"
  
  @Column(columnDefinition = "JSON")
  private String payload;                // Serialized event
  
  private Boolean published = false;
  private LocalDateTime createdAt = LocalDateTime.now();
  private LocalDateTime publishedAt;
}

// 2. Save payment + outbox atomically
@Service
@Transactional
public class PaymentService {
  
  public void settlePayment(String paymentId) {
    Payment payment = paymentRepo.findById(paymentId);
    payment.setStatus(PaymentStatus.SETTLED);
    paymentRepo.save(payment);
    
    // Same transaction
    OutboxEvent outboxEvent = new OutboxEvent();
    outboxEvent.setAggregateRootId(paymentId);
    outboxEvent.setEventType("payment.settled");
    outboxEvent.setPayload(objectMapper.writeValueAsString(
      new PaymentSettledEvent(paymentId, payment.getAmount(), ...)
    ));
    outboxRepo.save(outboxEvent);
    // Both committed together
  }
}

// 3. Background polling job
@Component
public class OutboxPollingService {
  
  @Scheduled(fixedDelay = 500)  // Poll every 500 ms
  public void publishPendingEvents() {
    List<OutboxEvent> unpublished = outboxRepo.findByPublishedFalse();
    
    for (OutboxEvent event : unpublished) {
      try {
        // Publish to Kafka
        SendResult<String, String> result = kafkaTemplate.send(
          event.getEventType(),          // Topic
          event.getAggregateRootId(),    // Key (partitioning)
          event.getPayload()             // Value
        ).get();
        
        // Mark published only if Kafka succeeds
        event.setPublished(true);
        event.setPublishedAt(LocalDateTime.now());
        outboxRepo.save(event);
      } catch (Exception e) {
        log.warn("Failed to publish event {}, will retry: {}", 
          event.getAggregateRootId(), e.getMessage());
        // Leave published=false → will retry
      }
    }
  }
}
```

**Idempotent Receiver (Consumer):**

```java
// Consumers must be idempotent too (Kafka no-guarantee)
@KafkaListener(topics = "payment.settled")
public void handlePaymentSettled(@Payload PaymentSettledEvent event) {
  
  // Guard: already processed?
  if (settlementRepo.existsByPaymentId(event.getPaymentId())) {
    log.info("Settlement already recorded for {}, skipping", event.getPaymentId());
    return;  // Idempotent ✓
  }
  
  // Process
  LedgerEntry debit = new LedgerEntry(DEBIT, event.getDebitParticipantId(), ...);
  LedgerEntry credit = new LedgerEntry(CREDIT, event.getCreditParticipantId(), ...);
  
  settlementRepo.saveAll(Arrays.asList(debit, credit));
}
```

**Data Consistency Guarantee:**

```
Timeline:
  T0: Client → POST /payment/initiate
  T1: payment-orchestration saves Payment + OutboxEvent (atomic)
  T2: outbox polling job finds unpublished event
  T3: outbox polling publishes to Kafka
  T4: clearing-settlement consumer receives event
  T5: clearing-settlement saves LedgerEntry (idempotent)
  
Crash scenarios:
  ✓ Crash at T1: neither DB nor Kafka gets anything
  ✓ Crash at T2: outbox still unpublished, polling retries
  ✓ Crash at T4: event still in Kafka (by partitionKey = paymentId)
  ✓ Crash at T5: consumer re-reads same event, idempotent save skips
  
Result: **Exactly-once delivery** (per payment)
```

---

### ADR-011: Ledger Immutability & Append-Only Storage

**Status:** PROPOSED

**Principle:** Financial records must be **immutable** once committed.

```sql
-- Clearing-Settlement DB

CREATE TABLE ledger_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entry_type ENUM('DEBIT', 'CREDIT') NOT NULL,
  participant_id VARCHAR(36) NOT NULL,
  payment_id VARCHAR(36) NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  -- Immutability signatures
  sha256_hash VARCHAR(64) NOT NULL,
  signature VARCHAR(512) NOT NULL,  -- BC signs with private key
  
  -- Constraints: prevent mutations
  PRIMARY KEY (id),
  UNIQUE KEY (payment_id, entry_type),
  INDEX idx_participant_timestamp (participant_id, timestamp)
);

-- Integrity triggers (MySQL)
DELIMITER //
CREATE TRIGGER prevent_ledger_update
BEFORE UPDATE ON ledger_entries
FOR EACH ROW
BEGIN
  RAISE EXCEPTION 'Ledger entries are immutable';
END //
DELIMITER ;

CREATE TRIGGER prevent_ledger_delete
BEFORE DELETE ON ledger_entries
FOR EACH ROW
BEGIN
  RAISE EXCEPTION 'Ledger entries cannot be deleted';
END //
DELIMITER ;
```

**Reversal (not mutation):**

```
If payment must be reversed (e.g., fraud detected after settlement):

Option A: REVERSAL via new entries (APPEND-ONLY)
  Original:
    DEBIT  bank-a (+100)
    CREDIT bank-b (-100)
  
  Reversal (creates 2 new entries):
    CREDIT bank-a (-100)  ← reverse debit
    DEBIT  bank-b (+100)  ← reverse credit
  
  Final ledger: balanced ✓
  Audit trail: complete (original + reversal visible)

Option B: Fail (abort reversal if already settled)
  Once status = SETTLED, immutable → no reversal allowed
  Requires court order / regulatory override
```

**Audit Trail & Signature:**

```java
public class LedgerEntryService {
  
  @Transactional
  public void recordSettlement(String paymentId, BigDecimal amount,
      String debitParticipantId, String creditParticipantId) {
    
    LedgerEntry debit = new LedgerEntry();
    debit.setEntryType(EntryType.DEBIT);
    debit.setParticipantId(debitParticipantId);
    debit.setPaymentId(paymentId);
    debit.setAmount(amount);
    debit.setTimestamp(Instant.now());
    debit.setHash(sha256(debit.toString()));
    debit.setSignature(signWithBCPrivateKey(debit.getHash()));
    
    ledgerRepo.save(debit);  // INSERT only
    
    LedgerEntry credit = new LedgerEntry();
    credit.setEntryType(EntryType.CREDIT);
    credit.setParticipantId(creditParticipantId);
    credit.setPaymentId(paymentId);
    credit.setAmount(amount);
    credit.setTimestamp(Instant.now());
    credit.setHash(sha256(credit.toString()));
    credit.setSignature(signWithBCPrivateKey(credit.getHash()));
    
    ledgerRepo.save(credit);  // INSERT only
  }
  
  public void verifyIntegrity() {
    // Auditeur: verify all entries
    List<LedgerEntry> entries = ledgerRepo.findAll();
    
    BigDecimal sumDebits = entries.stream()
      .filter(e -> e.getEntryType() == EntryType.DEBIT)
      .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    
    BigDecimal sumCredits = entries.stream()
      .filter(e -> e.getEntryType() == EntryType.CREDIT)
      .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    
    if (!sumDebits.equals(sumCredits)) {
      throw new LedgerIntegrityException("Debits != Credits!");
    }
  }
}
```

---

## 🎯 Récapitulatif des Décisions MVP

| Décision | Choix | Impact |
|---|---|---|
| **Broker Événements** | Apache Kafka | Découplage loose, cohérence finale, capacité de replay |
| **Pattern Saga** | Choreography | Banques externes; couplage faible; flux implicite |
| **Cohérence des données** | Outbox + consumers idempotents | Livraison exactement-une-fois par virement |
| **Ledger** | Append-only + signatures | Conformité réglementaire, audit trail, recovery |

---

## ℹ️ ADRs POST-MVP (pour Phase 3+)

- **ADR-010:** Alias Portability & History (US3) — documenté mais implémentation déférée
- **ADR-012:** Service Mesh & Circuit Breakers — pour observabilité avancée (futur)

---

## ✅ Prochaines étapes

**Implémentation basée sur ces 4 ADRs essentiels :**
1. Kafka cluster (partitions par paymentId)
2. Saga choreography (6 services, 3 BCs MVP)
3. Outbox pattern (3-layer guarantee)
4. Ledger append-only (immutable financial records)
