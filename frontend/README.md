# CanBankX Frontend — Developer Portal

A clean React testing UI for all CanBankX use cases.

## Prerequisites

- Node.js 18+
- CanBankX backend stack running (`docker compose up`)

## Setup & Run

```bash
cd frontend
npm install
npm run dev
```

The app starts at **http://localhost:5173**

All `/api/*` calls are proxied to the KrakenD gateway at `http://localhost:8080`.  
MailHog API calls (`/mailhog/*`) are proxied to `http://localhost:8025`.

---

## Happy-Path Flow

| Step | Page | Use Case |
|------|------|----------|
| 1 | **Register** `/register` | UC-01 — Create client, OTP sent to MailHog |
| 2 | **Verify OTP** `/verify` | UC-01 — Enter 6-digit OTP (or use Admin Bypass) |
| 3 | **Login** `/login` | UC-02 — Credentials → challenge token |
| 4 | **MFA** `/mfa` | UC-02 — 6-digit MFA code → authenticated |
| 5 | **Open Account** `/accounts/new` | UC-03 — CHECKING or SAVINGS |
| 6 | **Transactions** `/transactions` | UC-05 — DEBIT / CREDIT / TRANSFER |
| 7 | **Summary** `/accounts/summary` | UC-04 — Aggregated account + recent transactions |

---

## Features

- 🔍 **Auto-fetch OTP** from MailHog for both registration and MFA steps
- 🔁 **Idempotency test** — reuse the last Idempotency-Key to verify duplicate prevention
- 💾 **Session persistence** — client ID, account number, auth state saved to `localStorage`
- ✕ **Reset button** in navbar clears the entire session
- 📊 **Dashboard** shows all accounts and lets you jump directly to Summary or Transactions
- 📋 **Transaction History** tab — filter by account number
- 🔍 **Transaction Lookup** by UUID

---

## Services

| Service | URL |
|---------|-----|
| This app | http://localhost:5173 |
| KrakenD Gateway | http://localhost:8080 |
| MailHog UI | http://localhost:8025 |
| Grafana | http://localhost:3001 |
