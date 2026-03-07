# ──────────────────────────────────────────────────────────────────────────────
# This project is a Maven multi-module monorepo.
# Each microservice has its own Dockerfile:
#
#   identity-service/Dockerfile  → port 8081
#   account-service/Dockerfile   → port 8082
#   payment-service/Dockerfile   → port 8083
#
# Use docker-compose.yaml to build and start all services together:
#   docker compose up --build
#
# Or build a single service:
#   docker build -f identity-service/Dockerfile -t canbankx/identity-service .
#   docker build -f account-service/Dockerfile  -t canbankx/account-service  .
#   docker build -f payment-service/Dockerfile  -t canbankx/payment-service  .
# ──────────────────────────────────────────────────────────────────────────────