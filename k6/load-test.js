/**
 * CanBankX — k6 Load Test
 *
 * Three concurrent scenarios modelling real banking traffic:
 *   auth_traffic        (20% of VUs) — Login + MFA flow
 *   transaction_traffic (50% of VUs) — DEBIT / CREDIT / TRANSFER + idempotency check
 *   read_traffic        (30% of VUs) — Account summary, transaction list, account lookup
 *
 * Usage:
 *   k6 run k6/load-test.js
 *   k6 run --env BASE_URL=http://localhost:8080 k6/load-test.js
 */

import http  from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// custom metrics
const txSuccessRate  = new Rate('transaction_success_rate');
const txDuration     = new Trend('transaction_duration_ms',  true);
const authDuration   = new Trend('auth_duration_ms',         true);
const readDuration   = new Trend('read_duration_ms',         true);
const idemHits       = new Counter('idempotency_cache_hits');

// Config
const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const MAILHOG_URL = __ENV.MAILHOG_URL || 'http://localhost:8025';
const HEADERS     = { 'Content-Type': 'application/json' };

/**
 * Fetches the MFA OTP from MailHog by searching for the unique challengeToken
 * embedded in the email body. Each challengeToken is unique per login call so
 * concurrent VUs sharing the same account don't pick up each other's codes.
 */
function getMfaOtp(challengeToken) {
  const res = http.get(
    `${MAILHOG_URL}/api/v2/search?kind=containing&query=${encodeURIComponent(challengeToken)}&limit=1`,
    { tags: { name: 'mailhog' } },
  );
  if (res.status !== 200) return null;
  try {
    const data  = JSON.parse(res.body);
    if (!data.items || data.items.length === 0) return null;
    const body  = data.items[0].Content.Body;
    const match = body.match(/Your login code is:\s*(\d{6})/);
    return match ? match[1] : null;
  } catch (_) {
    return null;
  }
}

// Scenarios + thresholds
export const options = {
  scenarios: {
    // 20 % — authentication traffic (login + MFA)
    auth_traffic: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages: [
        { duration: '30s', target: 10 },   // ramp up
        { duration: '3m',  target: 10 },   // steady
        { duration: '30s', target: 0  },   // ramp down
      ],
      gracefulRampDown: '15s',
      exec: 'authScenario',
    },
    // 50 % — transaction processing (the most critical path)
    transaction_traffic: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages: [
        { duration: '30s', target: 25 },
        { duration: '3m',  target: 25 },
        { duration: '30s', target: 0  },
      ],
      gracefulRampDown: '15s',
      exec: 'transactionScenario',
    },
    // 30 % — read traffic (balances, transaction history)
    read_traffic: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages: [
        { duration: '30s', target: 15 },
        { duration: '3m',  target: 15 },
        { duration: '30s', target: 0  },
      ],
      gracefulRampDown: '15s',
      exec: 'readScenario',
    },
  },

  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],

    'http_req_duration{scenario:auth_traffic}':        ['p(95)<800'],
    'http_req_duration{scenario:transaction_traffic}': ['p(95)<1500'],
    'http_req_duration{scenario:read_traffic}':        ['p(95)<400'],

    transaction_success_rate: ['rate>0.99'],
    transaction_duration_ms:  ['p(95)<1500'],
    auth_duration_ms:         ['p(95)<800'],
    read_duration_ms:         ['p(95)<400'],
  },
};

// Runs once before VUs start — creates a shared client + 2 accounts for all scenarios
export function setup() {
  const ts       = Date.now();
  const email    = `loadtest_${ts}@canbankx.ca`;
  const password = 'LoadTest123!';
  const nas      = String(ts).slice(-9);

  const regRes = http.post(
    `${BASE_URL}/api/clients`,
    JSON.stringify({
      firstName: 'Load', lastName: 'Test',
      email, password,
      phoneNumber: '5140000000',
      address: '1 Rue Test, Montréal, QC H1A 1A1',
      nas,
    }),
    { headers: HEADERS },
  );
  if (regRes.status !== 201 && regRes.status !== 200) {
    console.error(`[setup] register failed: ${regRes.status} — ${regRes.body}`);
    return null;
  }
  const clientId = regRes.json('id');

  const actRes = http.post(`${BASE_URL}/api/clients/${clientId}/activate`, null, { headers: HEADERS });
  if (actRes.status !== 200) {
    console.error(`[setup] activate failed: ${actRes.status} — ${actRes.body}`);
    return null;
  }

  const checkRes = http.post(
    `${BASE_URL}/api/accounts`,
    JSON.stringify({ clientId, accountType: 'CHECKING', initialDeposit: 10000000.00 }),
    { headers: HEADERS },
  );
  if (checkRes.status !== 201 && checkRes.status !== 200) {
    console.error(`[setup] create CHECKING failed: ${checkRes.status} — ${checkRes.body}`);
    return null;
  }
  const accountNumber = checkRes.json('accountNumber');
  const accountId     = checkRes.json('id');

  const savRes = http.post(
    `${BASE_URL}/api/accounts`,
    JSON.stringify({ clientId, accountType: 'SAVINGS', initialDeposit: 10000000.00 }),
    { headers: HEADERS },
  );
  if (savRes.status !== 201 && savRes.status !== 200) {
    console.error(`[setup] create SAVINGS failed: ${savRes.status} — ${savRes.body}`);
    return null;
  }
  const savingsNumber = savRes.json('accountNumber');

  // Seed one transaction so the read scenario has a stable ID to query (avoids
  // fetching the unbounded list which grows to GB over a long test run)
  const seedRes = http.post(
    `${BASE_URL}/api/transactions`,
    JSON.stringify({ sourceAccountNumber: accountNumber, amount: 1.00, type: 'CREDIT' }),
    { headers: { ...HEADERS, 'Idempotency-Key': `setup-seed-${ts}` } },
  );
  const transactionId = seedRes.status === 201 ? seedRes.json('id') : null;

  console.log(`[setup] ✅  client=${clientId}  checking=${accountNumber}  savings=${savingsNumber}  seedTx=${transactionId}`);
  return { email, password, clientId, accountNumber, accountId, savingsNumber, transactionId };
}

export function authScenario(data) {
  if (!data) { sleep(1); return; }

  group('Auth / Login', () => {
    const t0 = Date.now();
    const loginRes = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: data.email, password: data.password }),
      { headers: HEADERS },
    );
    authDuration.add(Date.now() - t0);

    const ok = check(loginRes, {
      'login → 200':                r => r.status === 200,
      'login → has challengeToken': r => { try { return !!r.json('challengeToken'); } catch { return false; } },
    });
    if (!ok) { sleep(1); return; }

    const challengeToken = loginRes.json('challengeToken');
    const otpCode        = getMfaOtp(challengeToken);

    const mfaRes = http.post(
      `${BASE_URL}/api/auth/mfa`,
      JSON.stringify({ challengeToken, otpCode }),
      { headers: HEADERS },
    );
    check(mfaRes, {
      'MFA → 200':     r => r.status === 200,
      'MFA → SUCCESS': r => { try { return r.json('status') === 'SUCCESS'; } catch { return false; } },
    });
  });

  sleep(1);
}

export function transactionScenario(data) {
  if (!data) { sleep(1); return; }

  const types   = ['DEBIT', 'CREDIT', 'TRANSFER'];
  const type    = types[__ITER % types.length];
  const idemKey = `load-${__VU}-${__ITER}`;

  const body = {
    sourceAccountNumber: data.accountNumber,
    amount: 1.00,
    type,
    ...(type === 'TRANSFER' ? { targetAccountNumber: data.savingsNumber } : {}),
  };

  group(`Transaction / ${type}`, () => {
    const t0  = Date.now();
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify(body),
      { headers: { ...HEADERS, 'Idempotency-Key': idemKey } },
    );
    txDuration.add(Date.now() - t0);

    const ok = check(res, {
      'transaction → 201':       r => r.status === 201,
      'transaction → has id':    r => { try { return !!r.json('id'); } catch { return false; } },
      'transaction → COMPLETED': r => { try { return r.json('status') === 'COMPLETED'; } catch { return false; } },
    });
    txSuccessRate.add(ok ? 1 : 0);

    // Replay the same key — must return the identical transaction (exactly-once)
    const idemRes = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify(body),
      { headers: { ...HEADERS, 'Idempotency-Key': idemKey } },
    );
    const firstId = (() => { try { return res.json('id'); } catch { return null; } })();
    const idemOk  = check(idemRes, {
      'idempotency → 201':        r => r.status === 201,
      'idempotency → same tx id': r => { try { return r.json('id') === firstId; } catch { return false; } },
    });
    if (idemOk) idemHits.add(1);
  });

  sleep(0.5);
}

export function readScenario(data) {
  if (!data) { sleep(1); return; }

  group('Read / Account Summary', () => {
    // KrakenD aggregates account-service + payment-service in a single upstream call
    const t0  = Date.now();
    const res = http.get(`${BASE_URL}/api/accounts/${data.accountNumber}/summary`, { headers: HEADERS });
    readDuration.add(Date.now() - t0);
    check(res, {
      'summary → 200':         r => r.status === 200,
      'summary → has account': r => { try { return !!r.json('account'); } catch { return false; } },
    });
  });

  group('Read / Transaction by ID', () => {
    if (!data.transactionId) { return; }
    const t0  = Date.now();
    const res = http.get(`${BASE_URL}/api/transactions/${data.transactionId}`, { headers: HEADERS });
    readDuration.add(Date.now() - t0);
    check(res, {
      'tx by id → 200':    r => r.status === 200,
      'tx by id → has id': r => { try { return !!r.json('id'); } catch { return false; } },
    });
  });

  group('Read / Get Account by ID', () => {
    const t0  = Date.now();
    const res = http.get(`${BASE_URL}/api/accounts/${data.accountId}`, { headers: HEADERS });
    readDuration.add(Date.now() - t0);
    check(res, {
      'account → 200':               r => r.status === 200,
      'account → has accountNumber': r => { try { return !!r.json('accountNumber'); } catch { return false; } },
    });
  });

  group('Read / List Accounts by Client', () => {
    const t0  = Date.now();
    const res = http.get(`${BASE_URL}/api/accounts/list?clientId=${data.clientId}`, { headers: HEADERS });
    readDuration.add(Date.now() - t0);
    check(res, { 'account list → 200': r => r.status === 200 });
  });

  sleep(1);
}

export function teardown(data) {
  if (data) {
    console.log(`[teardown] checking: ${data.accountNumber}  savings: ${data.savingsNumber}`);
  }
}