# Arc42 — CanBankX Architecture Document

> Template Arc42 v8 adapté pour le projet LOG430 — CanBankX Banking Platform

---

## 1. Introduction et Objectifs

### 1.1 Description du système

CanBankX est une plateforme bancaire numérique construite en microservices. Elle couvre cinq cas d'utilisation métier :

| UC | Description |
|---|---|
| UC-01 | Inscription & KYC — enregistrement client avec vérification OTP par email |
| UC-02 | Authentification & MFA — login par mot de passe + code OTP sur téléphone |
| UC-03 | Ouverture de compte — création de comptes CHECKING ou SAVINGS |
| UC-04 | Consultation soldes & historique — lecture des balances et transactions |
| UC-05 | Virement bancaire — DEBIT, CREDIT, TRANSFER avec garantie exactly-once |

### 1.2 Objectifs de qualité

| Priorité | Objectif | Scénario de mesure |
|---|---|---|
| 1 | **Exactitude** | Aucun doublon de paiement même en cas de retry réseau (idempotency key) |
| 2 | **Performance** | p95 < 400 ms pour les lectures, < 1 500 ms pour les transactions (charge normale, 50 VUs) |
| 3 | **Disponibilité** | Les 3 services répondent `/actuator/health` 200 à tout moment (smoke test) |
| 4 | **Observabilité** | 4 Golden Signals visibles en temps réel dans Grafana |
| 5 | **Sécurité** | Mots de passe BCrypt, sessions stateless, communications internes sur réseau Docker privé |

### 1.3 Parties prenantes

| Partie | Attente principale |
|---|---|
| Client bancaire | Effectuer des virements fiables depuis n'importe quel client HTTP |
| Développeur | Swagger UI par service, Postman collection complète |
| Ops / Monitoring | Dashboard Grafana avec 4 Golden Signals + JVM + HikariCP |
| Correcteur LOG430 | Architecture documentée, charge testée, décisions justifiées |

---

## 2. Contraintes architecturales

| Contrainte | Impact |
|---|---|
| **Runtime Docker** | Tous les composants s'exécutent en conteneurs Docker, orchestrés par Docker Compose |
| **Spring Boot 3 / Java 21** | Framework imposé ; JPA/Hibernate pour la persistance |
| **MySQL 8.4** | Base de données relationnelle imposée |
| **Pas de JWT externe** | L'authentification est gérée en interne ; pas d'Identity Provider tiers |
| **Stateless services** | Aucune session HTTP côté services ; état stocké dans Redis (TTL) ou MySQL |
| **Budget réseau** | Déploiement mono-machine (localhost) ; pas de cluster Kubernetes |

---

## 3. Contexte du système (périmètre)

```
┌─────────────────────────────────────────────────────────────────┐
│                        CanBankX System                         │
│                                                                 │
│  ┌──────────┐    ┌──────────────────────────────────────────┐   │
│  │  Client  │───▶│         KrakenD API Gateway :8080        │   │
│  │ (Browser │    │  /api/clients, /api/auth, /api/accounts  │   │
│  │  Postman │    │  /api/transactions, /api/accounts/summary│   │
│  │   k6)    │    └──────┬──────────┬──────────┬─────────────┘   │
│  └──────────┘           │          │          │                 │
│                         ▼          ▼          ▼                 │
│                  identity  account-  payment-service            │
│                  -service  service   (×2 instances LB)         │
│                  :8081     :8082     :8083 / :8083              │
│                         │          │          │                 │
│                         └──────────┴────┬─────┘                 │
│                                         ▼                       │
│                                      MySQL 8.4                  │
│                               db_identity | db_account          │
│                               db_payment                        │
│                                                                 │
│  identity-service ◀──▶ Redis :6379 ◀──▶ payment-service        │
│  (MFA tokens)                           (idempotency cache)     │
└─────────────────────────────────────────────────────────────────┘

Systèmes externes:
  MailHog :1025/:8025   — capture des emails OTP (dev uniquement)
  Prometheus :9090      — scraping des métriques Actuator
  Grafana :3000         — dashboards 4 Golden Signals + JVM + HikariCP
```

---

## 4. Stratégie de solution

| Décision | Choix | Justification |
|---|---|---|
| Architecture | Microservices (3 services) | Isolation des domaines DDD, scalabilité ciblée |
| Gateway | KrakenD 2.7 | Agrégation native, zéro-code, LB round-robin |
| Cache | Redis 7 | TTL natif, partagé entre 2 usages (MFA + idempotency) |
| Idempotence | Redis + fallback DB | Exactly-once sur les paiements |
| Observabilité | Prometheus + Grafana | 4 Golden Signals + JVM + HikariCP |
| Tests de charge | k6 (smoke/load/stress) | Scénarios réalistes, métriques custom |
| Sécurité | BCrypt(8) + stateless | Hachage fort sans overhead excessif |

---

## 5. Vue des blocs de construction

### 5.1 Niveau 1 — Vue d'ensemble

| Bloc | Responsabilité |
|---|---|
| `identity-service` | Gestion des clients (UC-01, UC-02) |
| `account-service` | Gestion des comptes (UC-03, UC-04) |
| `payment-service` | Traitement des paiements (UC-05) |
| `krakend` | Routage, agrégation, load balancing |
| `mysql` | Persistance relationnelle (3 schémas isolés) |
| `redis` | Cache TTL (tokens MFA + clés idempotence) |
| `prometheus` | Collecte des métriques `/actuator/prometheus` |
| `grafana` | Visualisation, dashboards auto-provisionnés |
| `mailhog` | Capture emails OTP en développement |

### 5.2 Niveau 2 — identity-service

```
ClientController      → /identityservice/clients/**
AuthController        → /identityservice/auth/login, /mfa
ClientService         → logique métier (inscription, OTP, MFA)
StringRedisTemplate   → stockage token MFA challenge (TTL 5 min)
ClientRepository      → JPA → db_identity.clients
EmailService          → envoi OTP via MailHog
SecurityConfig        → Spring Security stateless
```

### 5.3 Niveau 2 — account-service

```
AccountController     → /accountservice/accounts/**
AccountService        → CRUD + atomicDebit/atomicCredit (SQL UPDATE atomique)
AccountRepository     → JPA → db_account.accounts
SecurityConfig        → Spring Security stateless
```

### 5.4 Niveau 2 — payment-service

```
PaymentController     → /paymentservice/transactions/**
PaymentService        → orchestration exactly-once avec compensation
  ├─ Redis check      → payment:idem:{key} (TTL 24h)
  ├─ AccountClient    → appel REST → account-service (debit/credit)
  ├─ IdentityClient   → appel REST → identity-service (info client)
  ├─ AuditLog         → trace append-only de chaque étape
  └─ EmailService     → confirmation par email (async, fire-and-forget)
BankTransactionRepository → JPA → db_payment.bank_transactions
AuditLogRepository    → JPA → db_payment.audit_log
```

---

## 6. Vue d'exécution (Runtime)

### 6.1 Scénario — Virement bancaire (UC-05) happy path

```
Client → KrakenD POST /api/transactions (Authorization, Idempotency-Key)
  → payment-service /paymentservice/transactions
     1. Redis GET payment:idem:{key}      [< 1 ms]
        → miss → continuer
     2. DB SELECT findByIdempotencyKey    [fallback]
        → miss → continuer
     3. DB INSERT bank_transaction PENDING
     4. REST PATCH account-service /debit  [atomicDebit SQL UPDATE]
        → DB UPDATE accounts SET balance=balance-amount WHERE accountNumber=? AND balance>=amount
        → DB INSERT audit_log BALANCE_DEBITED
     5. REST PATCH account-service /credit (si TRANSFER)
        → DB INSERT audit_log BALANCE_CREDITED
     6. DB UPDATE bank_transaction → COMPLETED
     7. Redis SET payment:idem:{key} = txId TTL 24h
     8. async → EmailService confirmation
  ← 201 Created {id, status: COMPLETED, ...}
```

### 6.2 Scénario — Retry idempotent

```
Client → KrakenD POST /api/transactions (même Idempotency-Key)
  → payment-service
     1. Redis GET payment:idem:{key} → HIT → txId
     2. DB SELECT tx by id
  ← 201 Created {même id, status: COMPLETED}
  (aucune écriture DB, aucun appel inter-service)
```

### 6.3 Résultats des tests de charge

| Test | VUs | Résultat | p95 latence tx |
|---|---|---|---|
| Smoke (1 VU) | 1 | 100% checks ✅ | 118 ms |
| Load (50 VUs) | 50 | 100% checks ✅, 0% erreur | 179 ms |
| Stress (200 VUs) | 200 | 72% tx success ⚠️ | 5 s (timeout) |

L'auth (identity-service) reste à p95 = 25 ms même à 200 VUs. Le goulot d'étranglement est la contention sur les verrous InnoDB dans `db_payment` (confirmé par les métriques HikariCP Grafana).

---

## 7. Vue de déploiement

```yaml
# Réseau Docker : log430_projet-network (bridge externe)
# Tous les services communiquent par nom de conteneur DNS

Services exposés sur l'hôte :
  krakend           :8080  ← point d'entrée unique pour les clients
  identity-service  :8081  ← accès direct (Swagger, health)
  account-service   :8082  ← accès direct (Swagger, health)
  payment-service   :8083  ← accès direct (Swagger, health)
  mysql             :3306
  redis             :6379
  mailhog           :8025 (UI) / :1025 (SMTP)
  prometheus        :9090
  grafana           :3000

Services internes uniquement (pas de port hôte) :
  payment-service-2        ← deuxième instance LB (KrakenD only)
  db-init                  ← job one-shot, sort après init

Volumes persistants :
  mysql_data, redis_data, prometheus_data, grafana_data
```

---

## 8. Concepts transversaux

### 8.1 Gestion des erreurs normalisée

Un `GlobalExceptionHandler` (`@RestControllerAdvice`) dans le module `common` intercepte toutes les exceptions et retourne un objet `ErrorResponse` homogène :

```json
{ "status": 404, "error": "Not Found", "message": "Account 1234 not found", "timestamp": "..." }
```

Codes HTTP systématiquement mappés : 400 (validation), 401 (auth), 403 (accès), 404 (ressource), 409 (conflit/doublon), 422 (fonds insuffisants).

### 8.2 Observabilité (4 Golden Signals)

Chaque service Spring Boot expose `/actuator/prometheus`. Prometheus scrape toutes les 15 secondes. Le dashboard Grafana affiche :

| Signal | Métrique Prometheus |
|---|---|
| **Traffic** | `rate(http_server_requests_seconds_count[1m])` par service |
| **Latence** | `histogram_quantile(0.95, ...)` — P50/P95/P99 |
| **Erreurs** | `rate(http_server_requests_seconds_count{status=~"5.."}[1m])` |
| **Saturation** | `jvm_memory_used_bytes`, `system_cpu_usage`, `hikaricp_connections_active` |

### 8.3 Sécurité

- Mots de passe hachés avec **BCrypt force 8** (~25 ms/hash, 4× plus rapide que BCrypt 10 sous charge)
- Sessions **stateless** (pas de HttpSession), `SessionCreationPolicy.STATELESS`
- CSRF désactivé (API REST, pas de formulaires HTML)
- CORS configuré sur tous les services
- Communication inter-services sur réseau Docker privé (non accessible depuis l'hôte)

### 8.4 Idempotence et exactly-once

La garantie exactly-once sur les paiements repose sur :
1. `idempotencyKey` avec contrainte `UNIQUE` en base de données
2. Index unique `idx_idempotency_key` pour les requêtes rapides
3. Cache Redis `payment:idem:{key}` pour éviter les lectures DB systématiques
4. Compensation manuelle : si le crédit échoue après le débit, un CREDIT de compensation est automatiquement soumis

---

## 9. Décisions architecturales (ADR)

| ADR | Titre | Statut |
|---|---|---|
| [ADR-001](adr/ADR-001-microservices-bounded-contexts.md) | Décomposition en microservices avec Bounded Contexts DDD | Accepté |
| [ADR-002](adr/ADR-002-redis-idempotence-mfa.md) | Redis pour double usage : idempotence et tokens MFA | Accepté |
| [ADR-003](adr/ADR-003-krakend-api-gateway.md) | KrakenD comme API Gateway | Accepté |
| [ADR-004](adr/ADR-004-schemas-mysql-isoles.md) | Schémas MySQL isolés par service | Accepté |

---

## 10. Exigences de qualité

### 10.1 Scénarios de qualité (4+1 view)

| ID | Stimulus | Réponse attendue | Mesure |
|---|---|---|---|
| Q1 | 50 VUs soumettent des transactions concurrentes | 0% d'erreur, p95 < 1 500 ms | ✅ Load test k6 : 100% success, p95 = 179 ms |
| Q2 | Même `Idempotency-Key` envoyée deux fois | Retour identique, aucune double-écriture | ✅ Smoke test vérifie `same tx id` |
| Q3 | Solde insuffisant | 422 Unprocessable Entity avec message clair | ✅ Smoke test `overdraft → 422` |
| Q4 | Service `account-service` retourne une erreur pendant un transfer | Transaction marquée FAILED, débit compensé | ✅ Logique de compensation dans `PaymentService` |
| Q5 | 200 VUs simultanés | Dégradation gracieuse, auth reste < 2 s | ✅ Stress test : auth p95 = 25 ms, tx dégradent (attendu) |

---

## 11. Risques et dette technique

| Risque | Sévérité | Mitigation actuelle | Recommandation future |
|---|---|---|---|
| SPOF MySQL | Haute | Volumes Docker persistants | MySQL Group Replication ou RDS Multi-AZ |
| `ddl-auto: update` | Moyenne | Acceptable en dev/test | Migrer vers Flyway pour la prod |
| Pas de JWT réel | Moyenne | Réseau Docker privé + Spring Security | Intégrer Keycloak ou Auth0 |
| Pas de circuit breaker | Moyenne | Timeout KrakenD (5 s) | Ajouter Resilience4j sur les appels inter-services |
| Contention DB à 200 VUs | Haute (observée) | HikariCP 50 connexions + 2 instances payment-service | Read replica MySQL + cache de lecture |
| SPOF Redis | Faible | Fallback DB sur les clés d'idempotence | Redis Sentinel ou Redis Cluster |
