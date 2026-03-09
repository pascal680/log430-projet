# Documentation — CanBankX

**Auteur :** Pascal Bourgoin
**Cours :** LOG430 — Architecture logicielle
**Session :** Hiver 2026

---

## Index des documents

### Architecture

| Fichier | Contenu |
|---|---|
| [`arc42.md`](arc42.md) | Document Arc42 complet — sections 1 à 11 |
| [`4+1-views.md`](4+1-views.md) | Vues 4+1 détaillées avec diagrammes PlantUML (Scénarios, Logique, Processus, Développement, Déploiement) |
| [`analyse-metier-ddd.md`](analyse-metier-ddd.md) | Analyse métier, DDD, langage ubiquitaire, bounded contexts, règles métier, UC détaillés |

### Décisions architecturales (ADR)

| ADR | Titre |
|---|---|
| [`adr/ADR-001`](adr/ADR-001-microservices-bounded-contexts.md) | Décomposition en microservices avec Bounded Contexts DDD |
| [`adr/ADR-002`](adr/ADR-002-redis-idempotence-mfa.md) | Redis pour double usage : idempotence et tokens MFA |
| [`adr/ADR-003`](adr/ADR-003-krakend-api-gateway.md) | KrakenD comme API Gateway |
| [`adr/ADR-004`](adr/ADR-004-schemas-mysql-isoles.md) | Schémas MySQL isolés par service |
| [`adr/ADR-005`](adr/ADR-005-error-handling-versioning.md) | Gestion des erreurs normalisée et versionnage |
| [`adr/ADR-006`](adr/ADR-006-cicd-github-actions.md) | Pipeline CI/CD GitHub Actions |

### Diagrammes PlantUML

| Fichier | Vue |
|---|---|
| [`UseCaseDiagram.puml`](4+1/UseCaseDiagram.puml) | Vue Scénarios — UC-01 à UC-05 |
| [`ClassDiagram.puml`](4+1/ClassDiagram.puml) | Vue Logique — modèle de domaine DDD |
| [`SequenceDiagram.puml`](4+1/SequenceDiagram.puml) | Vue Processus — UC-05 virement exactly-once |
| [`ComponentDiagram.puml`](4+1/ComponentDiagram.puml) | Vue Processus — architecture interne des services |
| [`DeploymentDiagram.puml`](4+1/DeploymentDiagram.puml) | Vue Déploiement — infrastructure Docker Compose |
| [`ObservabilityDiagram.puml`](4+1/ObservabilityDiagram.puml) | Vue Observabilité — Prometheus + Grafana |

### Rapport de projet

| Fichier | Contenu |
|---|---|
| [`Rapport_CanBankX_LOG430.md`](Rapport_CanBankX_LOG430.md) | Rapport complet toutes sections (8 sections, annexes) |

### Collection Postman

| Fichier | Contenu |
|---|---|
| [`collections/CanBankX.postman_collection.json`](collections/CanBankX.postman_collection.json) | Tous les appels UC-01 à UC-05 |

---

## Démarrage rapide

```bash
# Cloner et démarrer
git clone <repo> && cd log430-projet
cp .env.example .env
docker network create log430_projet-network
docker compose up -d --build

# Vérifier la santé
curl http://localhost:8080/__health       # KrakenD
open http://localhost:3000               # Grafana (admin/admin)
open http://localhost:8025               # MailHog (emails OTP)

# Lancer le smoke test
docker compose --profile testing run --rm k6 run /tests/smoke-test.js
```

## Structure des dossiers

```
docs/
├── README.md                    ← cet index
├── arc42.md                     ← Arc42 sections 1-11
├── 4+1-views.md                 ← vues 4+1 complètes
├── analyse-metier-ddd.md        ← DDD, UC détaillés, règles métier
├── Rapport_CanBankX_LOG430.md   ← rapport complet
├── adr/
│   ├── ADR-001 à ADR-006
├── collections/
│   └── CanBankX.postman_collection.json
└── *.puml                       ← diagrammes PlantUML
```
