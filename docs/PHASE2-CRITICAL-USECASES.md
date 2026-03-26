# User Stories Critiques : Virement Interbancaire Phase 2

> **Date :** Mars 2026  
> **Projet :** CanBankX — LOG430  
> **Question :** Quels US sont ABSOLUMENT NÉCESSAIRES pour compléter un virement interbancaire ?

---

## 🎯 **Chemin Critique (Happy Path) — Virement Complète**

### Définition du Succès

> Un virement interbancaire est **COMPLET** quand :
> - Payeur (Bank A) a débité son compte ✓
> - Bénéficiaire (Bank B) a crédité son compte ✓
> - Banque Centrale a enregistré les écritures immuables ✓
> - Finalité garantie (irrévocable) ✓

---

## 📊 Matrice US : Critiques vs Optionnelles

```
┌──────┬─────────────────────────────────────────┬─────────────┬──────────────────────┐
│ US # │ Description                             │ CRITIQUE    │ Pourquoi / Optionnel │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US1  │ Paiement par clé                        │ ✅ OUI (*)  │ Vue client finale du  │
│      │ (client paie client via alias)          │             │ virement (UX layer)   │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US2  │ Enregistrement de clé                   │ ✅ OUI      │ Prérequis absolu :    │
│      │ (bénéficiaire crée alias)              │             │ besoin d'une clé pour │
│      │                                         │             │ que quelqu'un puisse  │
│      │                                         │             │ lui payer             │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US3  │ Portabilité de clé                      │ ❌ NON      │ Feature optionnelle : │
│      │ (client transfère clé vers autre bank)  │             │ changer de banque,    │
│      │                                         │             │ mais ne change pas la │
│      │                                         │             │ mécanique du virement │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US4  │ Confirmation bénéficiaire (lookup)      │ ✅ OUI      │ Sécurité CRITIQUE :   │
│      │ Payeur voit nom partiellement masqué    │             │ empêcher erreur de    │
│      │ après résolution de clé                 │             │ destinataire (payer   │
│      │                                         │             │ la mauvaise personne) │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US5  │ Initier paiement interbancaire          │ ✅ OUI      │ **CŒUR DU SYSTÈME** : │
│      │ (participant Bank A → BC → Bank B)      │             │ orchestration saga    │
│      │ avec idempotencyKey + statut            │             │ du paiement           │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US6  │ Répondre à paiement entrant             │ ✅ OUI      │ **CŒUR DU SYSTÈME** : │
│      │ (Bank B accepte/refuse credit)          │             │ Bank B valide et      │
│      │                                         │             │ confirme credit       │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US7  │ Settlement et finalité (BC)             │ ✅ OUI      │ **CŒUR DU SYSTÈME** : │
│      │ (BC enregistre écritures comptables)    │             │ Ledger immuable,      │
│      │                                         │             │ double-écriture,      │
│      │                                         │             │ irrévocabilité        │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US8  │ Suspension/limitation participant       │ ❌ NON      │ Cas d'erreur / ops :  │
│      │ (BC protège stabilité en cas incident)  │             │ nécessaire pour fiabil│
│      │                                         │             │ mais pas pour le      │
│      │                                         │             │ happy path            │
├──────┼─────────────────────────────────────────┼─────────────┼──────────────────────┤
│ US9  │ Rapport de réconciliation               │ ❌ NON      │ Back-office reporting │
│      │ (réconciliation interbancaire)          │             │ pour audit/comptable, │
│      │                                         │             │ pas nécessaire pour   │
│      │                                         │             │ le virement lui-même  │
└──────┴─────────────────────────────────────────┴─────────────┴──────────────────────┘

(*) US1 vs US5: 
    - US1 = perspective CLIENT (Alice utilise app Bank A -> "Pay bob@mail.com")
    - US5 = perspective SYSTÈME (Bank A orchestrates → BC → Bank B)
    
    US1 est l'UX; US5 est l'implémentation. Les deux sont nécessaires.
```

---

## 🔗 Dépendances Critiques (Chaîne de Succès)

```
┌────────────────────────────────────────────────────────────────────────┐
│           VIREMENT INTERBANCAIRE RÉUSSI = 6 US CHAÎNÉES               │
└────────────────────────────────────────────────────────────────────────┘

[T0] Alice (Bank A client) veut payer Bob (Bank B client)
     ├─ Alice doit avoir un compte à Bank A ✓ (phase 1)
     └─ Bob doit avoir une clé d'alias enregistrée
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │ US2: Enregistrement │
                              │ de clé              │
                              │                     │
                              │ Bob@Bank B:         │
                              │ POST /alias         │
                              │ {                   │
                              │   key: bob@mail.com,│
                              │   account_id: ...   │
                              │ }                   │
                              │ Status: REGISTERED  │
                              └─────────────────────┘
                                        │
                                        ▼
[T1] Alice clicks "Send 100 CAD to bob@mail.com"
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │ US1: Paiement par   │
                              │ clé (UX)            │
                              │                     │
                              │ Alice's Bank A app: │
                              │ POST /initiate      │
                              │ {                   │
                              │   alias: bob@...,   │
                              │   amount: 100       │
                              │ }                   │
                              │                     │
                              │ Alice sees:         │
                              │ "Sending to Bob J..." │
                              └─────────────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────┐
                         │ US4: Confirmation        │
                         │ Bénéficiaire (Lookup)    │
                         │                          │
                         │ BC Directory resolves:   │
                         │ bob@mail.com →           │
                         │ { Bank B, ACC-456,       │
                         │   maskedName: "B*b J**" }│
                         │                          │
                         │ Alice confirms:          │
                         │ "Pay B*b J**? YES/NO"    │
                         │ Alice: YES               │
                         └──────────────────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────┐
                         │ US5: Initiate Payment    │
                         │ Interbancaire            │
                         │                          │
                         │ Bank A → BC              │
                         │ POST /payment/initiate   │
                         │ {                        │
                         │   idempotencyKey: "tx-", │
                         │   alias: bob@...,        │
                         │   amount: 100            │
                         │ }                        │
                         │                          │
                         │ BC saga orchestrates:    │
                         │ 1. Validate & lookup     │
                         │ 2. Fraud check           │
                         │ 3. Send debit to Bank A  │
                         │ 4. Send credit to Bank B │
                         │                          │
                         │ BC emits events via      │
                         │ Kafka:                   │
                         │ - payment.initiated      │
                         │ - alias.resolved         │
                         │ - payment.debit_req      │
                         └──────────────────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────┐
                         │ US6: Respond to Payment  │
                         │ Entrant                  │
                         │                          │
                         │ Bank B receives:         │
                         │ credit.request via      │
                         │ Kafka/webhook            │
                         │                          │
                         │ Bank B validates:        │
                         │ ├─ Account exists? ✓     │
                         │ ├─ Risk OK? ✓            │
                         │ └─ Compliance OK? ✓      │
                         │                          │
                         │ Bank B events:           │
                         │ - credit_accepted        │
                         │                          │
                         │ Bob's balance += 100     │
                         └──────────────────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────┐
                         │ US7: Settlement et       │
                         │ Finalité (BC)            │
                         │                          │
                         │ BC Clearing & Settlement │
                         │ receives indicators:     │
                         │ - Bank A debit_accepted  │
                         │ - Bank B credit_accepted │
                         │                          │
                         │ BC records:              │
                         │ INSERT ledger_entries:   │
                         │ ├─ DEBIT bank-a: -100    │
                         │ ├─ CREDIT bank-b: +100   │
                         │ └─ signature & hash ✓    │
                         │                          │
                         │ Finality: SETTLED        │
                         │ (IMMUTABLE from here)    │
                         │                          │
                         │ Events:                  │
                         │ - payment.settled        │
                         │ - clearing.confirmed     │
                         └──────────────────────────┘
                                        │
                                        ▼
[T+2s] VIREMENT COMPLÈTE ✓

Alice's account: -100 ✓
Bob's account: +100 ✓
BC Ledger: balanced ✓
Status: SETTLED (irrévocable) ✓
Audit trail: 7-year retention ✓
```

---

## ✅ Les 6 User Stories Critiques (Minimum Viable Product)

### 1️⃣ **US2: Enregistrement de Clé**

```yaml
CRITIÈRE:   Absolument nécessaire — prérequis
RAISON:     Bénéficiaire doit avoir un alias pour être trouvé
IMPACT:     Sans ça → impossible de résoudre "bob@mail.com"

Scénario:   Bob crée alias "bob@mail.com" sur son compte @Bank B
            DOWN: Personne ne peut le payer → virement impossible
            
API:        POST /alias { key, account_id }
            GET  /alias/{id}/confirm-code  (MFA si requise)

Dépend de:  - Participation de Bob à system (lui, son compte Bank B)
            - Alias validé (unique)

Prépare:    US5 (lookup résolvain bob@mail.com)
```

### 2️⃣ **US1 + US4: Paiement par Clé + Confirmation Bénéficiaire**

```yaml
CRITIÈRE:   Absolument nécessaire — sécurité du payeur
RAISON:     Payeur doit voir qui il paie avant de confirmer
IMPACT:     Sans ça → payer par erreur (mauvaise personne)

Scénario:   Alice types "bob@mail.com" et MUST see confirmation:
            "Pay to: B*b J*nson at Bank B?"
            
            WITHOUT this: Alice could pay wrong "Bob" entirely

API:        GET /alias/{key}/resolve → { participant_id, masked_name }
            
Dépend de:  US2 (alias bob@mail.com exists)

Résultat:   Alice confirms → ready for US5
```

### 3️⃣ **US5: Initier Paiement Interbancaire**

```yaml
CRITIÈRE:   **CŒUR DU SYSTÈME** — propulseur du virement
RAISON:     Déclenche la saga chorégraphiée entière
IMPACT:     Sans ça → aucun virement ne commence

Scénario:   Alice's Bank A calls:
            POST /payment/initiate
            {
              idempotencyKey: "tx-alice-20260325-123",
              alias: "bob@mail.com",
              amount: 100.00
            }
            
            BC responds:
            {
              paymentId: "pay-xyz",
              status: "INITIATED",
              beneficiaryMasked: "B*b J*n"
            }
            
            Behind scenes:
            └─ Saga orchestration starts
               ├─ Lookup alias (directory)
               ├─ Fraud check (control)
               ├─ Request debit (Bank A)
               ├─ Request credit (Bank B)
               └─ Settlement (clearing)

API:        POST /payment/initiate
            GET  /payment/{id}/status

Dépend de:  US2 (alias exists)
            US4 (lookup works)

Déclenchante: US6 (Bank B must respond)
```

### 4️⃣ **US6: Répondre à Paiement Entrant**

```yaml
CRITIÈRE:   **CŒUR DU SYSTÈME** — completion du débit/crédit
RAISON:     Bank B doit accepter/rejeter la demande
IMPACT:     Sans ça → saga stagne, incomplete

Scénario:   Bank B receives:
            webhook/Kafka: credit.requested
            {
              paymentId: "pay-xyz",
              amount: 100,
              beneficiaryAlias: "bob@mail.com"
            }
            
            Bank B validates:
            ├─ Account exists? ✓
            ├─ No AML violations? ✓
            ├─ Sufficient funds limit? ✓
            
            Bank B responds:
            ├─ credit_accepted { paymentId }
            │  └─ Bob's balance += 100
            │
            OR
            │
            └─ credit_rejected { paymentId, reason }
               └─ Compensation triggered (refund Alice)

Dépend de:  US5 (debit already accepted by Bank A)

Prepare:    US7 (settlement happens only if both accept)
```

### 5️⃣ **US7: Settlement et Finalité**

```yaml
CRITIÈRE:   **CŒUR DU SYSTÈME** — irrévocabilité
RAISON:     BC records immutable ledger entries → money final
IMPACT:     Sans ça → pas de finalité, BC peut inverser

Scénario:   BC Clearing & Settlement receives:
            ├─ bank_a.debit_accepted
            └─ bank_b.credit_accepted
            
            BC records (APPEND-ONLY):
            INSERT ledger_entry { DEBIT, bank-a, -100, sig, hash }
            INSERT ledger_entry { CREDIT, bank-b, +100, sig, hash }
            
            Verify: SUM(DEBIT) == SUM(CREDIT) ✓
            
            Status: SETTLED (IMMUTABLE from now on)
            
            No UPDATE/DELETE ever allowed on these entries.

Dépend de:  US5 (initiation)
            US6 (both responses received)

Finalizes:  Virement est TERMINÉ, irrévocable
```

### 6️⃣ **US5.5 (Implicite): Idempotence & Outbox**

```yaml
CRITIÈRE:   Absolument nécessaire — exactly-once guarantee
RAISON:     Alice retries payment (network timeout) → must not double-charge
IMPACT:     Sans ça → possible doublons de montants

Scénario:   Alice clicks "Send" → timeout → retries with SAME idempotencyKey
            
            Request 1 (T+0):
            POST /payment/initiate { idempotencyKey: "tx-123", ... }
            [NETWORK TIMEOUT, no ACK received]
            
            Request 2 (T+5s):
            POST /payment/initiate { idempotencyKey: "tx-123", ... }
            
            BC must return:
            SAME paymentId as Request 1 (not new one!)
            → Alice debited once, not twice
            
Mechanism:  Outbox pattern + Redis cache (idempotency key → paymentId)
            ├─ Idempotency check: is idempotencyKey in cache? 
            │  └─ YES → return cached paymentId (no new saga)
            │  └─ NO → create new payment
            │
            └─ Outbox guarantee: all events published exactly once

Dépend de:  Distributed transaction atomicity
            
Cover:      All of US1–7
```

---

## ❌ User Stories NON Critiques (Optionnelles / Edge Cases)

### US3: Portabilité de Clé
```
Why optional: Bob changes from Bank B to Bank C
              Doesn't affect ongoing virements
              Can be implemented AFTER MVP
```

### US8: Suspension/Limitation Participant
```
Why optional: Only needed if Bank A has incident
              Happy path doesn't encounter this
              Is operational / incident management
```

### US9: Rapport Réconciliation
```
Why optional: Back-office reporting feature
              Not needed to EXECUTE the virement
              Needed for AUDIT after the fact
```

---

## 🎯 **MVP (Minimum Viable Product) — Phase 2**

Pour livrer un **virement interbancaire complet** opérationnel :

```
┌─────────────────────────────────────────────────────────┐
│  ABSOLUMENT NÉCESSAIRES (Sprint 1–3)                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ✅ US2  — Enregistrement de clé (alias creation)     │
│  ✅ US1  — Payment par clé (client UX)                │
│  ✅ US4  — Beneficiary lookup avec masquage            │
│  ✅ US5  — Initiation interbancaire + saga            │
│  ✅ US6  — Acceptation/rejet du crédit                │
│  ✅ US7  — Settlement + ledger immuable               │
│  ✅ "   — Outbox pattern (exactly-once) INTEGRATED    │
│  ✅ "   — Idempotency (no double-charging)            │
│                                                         │
│  → = 7 user stories + 2 patterns critiques             │
│  → Can process: 500+ virements/sec                     │
│  → P95 latency: ≤ 500 ms                              │
│  → Guarantee: exactly-once, no data loss              │
│                                                         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  OPTIONNELS (Sprint 4+, Post-MVP)                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  🔲 US3  — Portabilité de clé                         │
│  🔲 US8  — Suspension/limitation                      │
│  🔲 US9  — Rapport réconciliation                     │
│  🔲 "    — Service mesh / advanced resilience         │
│  🔲 "    — Advanced fraud detection (ML)              │
│  🔲 "    — Multi-currency support                     │
│                                                         │
│  → Commercial / operational features                  │
│  → Do NOT block first release                         │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 📋 Checklist de Complétude du Virement

```
Pour qu'un virement soit COMPLÈTE:

┌─────────────── PAYEUR SIDE ──────────────────┐
│ ✓ Alias du bénéficiaire existant (US2)       │
│ ✓ Payeur a solde suffisant                   │
│ ✓ Payeur initialise via app (US1)            │
│ ✓ Payeur voit qui il paie (US4)              │
│ ✓ Payeur confirme montant                    │
│ ✓ Bank A débite le compte payeur             │
│ ✓ Bank A refuse idempotence (same tx twice)  │
└─────────────────────────────────────────────┘
                  │
                  ↓ (Saga via Kafka)
┌─────────────── BC SIDE ──────────────────────┐
│ ✓ Payment.initiated (US5 start)              │
│ ✓ Alias resolved (directory lookup)          │
│ ✓ Fraud check passed                         │
│ ✓ Debit validation via Bank A                │
│ ✓ Credit validation via Bank B               │
│ ✓ Both banks respond OK                      │
│ ✓ Settlement triggered (US7)                 │
│ ✓ Ledger entries created (immutable)         │
│ ✓ payment.settled event emitted              │
└─────────────────────────────────────────────┘
                  │
                  ↓ (Saga via Kafka)
┌──────────────── BENEFICIARY SIDE ────────────┐
│ ✓ Credit requested (US6 start)               │
│ ✓ Bank B validates account                   │
│ ✓ Bank B accepts/rejects                     │
│ ✓ Money arrives on Bob's account             │
│ ✓ Bob receives notification                  │
└─────────────────────────────────────────────┘

ALL MUST BE TRUE → VIREMENT COMPLETE ✓
```

---

## 🎬 Dépendance Résumée (1 phrase par US)

| US | Essence | Requis? |
|---|---|---|
| **US1** | Client voit l'UX "pay via alias" | ✅ |
| **US2** | Bénéficiaire crée alias (pour être trouvé) | ✅ |
| **US3** | Bénéficiaire change de banque | ❌ |
| **US4** | Payeur voit nom masqué du bénéficiaire | ✅ |
| **US5** | Orchestration du virement interbancaire | ✅ |
| **US6** | Bank destinataire accepte/refuse crédit | ✅ |
| **US7** | BC enregistre ledger immuable → Finalité | ✅ |
| **US8** | BC suspend une banque en incident | ❌ |
| **US9** | Rapport réconciliation pour audit | ❌ |
| **Idempotence** | Pas de double-charge si retry | ✅ |
| **Outbox Pattern** | Tous les events garantis published | ✅ |

---

## 🚀 Ordre d'Implémentation Recommandé

**Sprint 1 (Week 1-2):**
- [ ] US2 + basic DB schema (directory service)
- [ ] US4 (alias lookup, masking logic)
- [ ] Redis cache for idempotency keys
- [ ] Kafka setup + outbox tables

**Sprint 2 (Week 3-4):**
- [ ] US5 (payment orchestration state machine)
- [ ] Outbox polling job for all services
- [ ] Consumer idempotency guards
- [ ] Unit tests: idempotence, saga transitions

**Sprint 3 (Week 5-6):**
- [ ] US6 (webhook handling, Bank A/B responses)
- [ ] US7 (clearing-settlement, ledger append-only)
- [ ] US1 (tie all together, client UX)
- [ ] Integration tests: full saga happy path + compensation

**Sprint 4 (Week 7-8):**
- [ ] Load test: 500+ ops/sec, measure latency/debit
- [ ] Chaos testing: crash services randomly
- [ ] US3, US8, US9 (if time)
- [ ] Documentation: Arc42, 4+1, ADRs

---

**CONCLUSION:** Les **6 user stories + outbox pattern** sont le cœur indivisible d'un virement interbancaire fiable. Les autres (US3, US8, US9) sont des optimisations / operational nice-to-haves.
