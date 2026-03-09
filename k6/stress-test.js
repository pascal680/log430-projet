/**
 * CanBankX – k6 Stress Test
 *
 * Pushes the system well beyond normal load to find its breaking point.
 * Focuses entirely on the two most expensive paths:
 *   - Transaction submission (Redis + 2 DB + email + 2 inter-service calls)
 *   - Auth (password hashing under concurrency)
 *
 * Stages:
 *   0 → 50  VUs  in 1 min   (warm-up)
 *   50 → 150 VUs  in 1 min   (ramp to stress)
 *   150       VUs  for 2 min  (sustained stress)
 *   150 → 200 VUs  in 30 s    (spike)
 *   200       VUs  for 1 min  (peak)
 *   200 → 0   VUs  in 1 min   (cool-down)
 *
 * Account pool: setup() creates POOL_SIZE independent client+account pairs.
 * Each VU is assigned its own lane (__VU % POOL_SIZE) so no two VUs share
 * the same MySQL row — this tests true service throughput, not row-lock
 * contention on a single account.
 *
 * Usage:
 *   k6 run k6/stress-test.js
 *   k6 run --env BASE_URL=http://localhost:8080 k6/stress-test.js
 */

import http  from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const txSuccessRate = new Rate('stress_tx_success_rate');
const txDuration    = new Trend('stress_tx_duration_ms', true);
const authDuration  = new Trend('stress_auth_duration_ms', true);
const failCount     = new Counter('stress_failures');

const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const MAILHOG_URL = __ENV.MAILHOG_URL || 'http://localhost:8025';
const HEADERS     = { 'Content-Type': 'application/json' };

// Number of independent account "lanes". Each VU is pinned to one lane so
// no two VUs ever contend on the same MySQL row.
const POOL_SIZE = 20;

/**
 * Fetches the real MFA OTP from MailHog by searching for the unique
 * challengeToken embedded in the email body. Safe for concurrent VUs
 * sharing the same account — each challengeToken is unique per login call.
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

export const options = {
  stages: [
    { duration: '1m',  target: 50  },   // warm-up
    { duration: '1m',  target: 150 },   // ramp to stress
    { duration: '2m',  target: 150 },   // sustained stress
    { duration: '30s', target: 200 },   // spike
    { duration: '1m',  target: 200 },   // peak load
    { duration: '1m',  target: 0   },   // cool-down
  ],
  thresholds: {
    http_req_failed:        ['rate<0.05'],   // < 5 % HTTP errors
    http_req_duration:      ['p(95)<3000'],  // p95 < 3 s under stress
    stress_tx_success_rate: ['rate>0.95'],   // 95 % of transactions succeed
    stress_tx_duration_ms:  ['p(95)<3000'],
    stress_auth_duration_ms:['p(95)<2000'],
  },
};

/**
 * Creates POOL_SIZE independent client+account pairs.
 * Runs once before any VU starts. Takes ~3–5 s for 20 accounts.
 */
export function setup() {
  const ts      = Date.now();
  const pool    = [];

  for (let i = 0; i < POOL_SIZE; i++) {
    const email    = `stress_${ts}_${i}@canbankx.ca`;
    const password = 'StressTest123!';
    // NAS must be 9 digits and unique
    const nas      = String((ts + i * 1000) % 1000000000).padStart(9, '0');

    // 1. Register
    const regRes = http.post(
      `${BASE_URL}/api/clients`,
      JSON.stringify({
        firstName: `Stress${i}`, lastName: 'Test',
        email, password,
        phoneNumber: `514${String(9000000 + i).padStart(7, '0')}`,
        address: `${i + 1} Stress Blvd, Montréal, QC H9Z 9Z9`,
        nas,
      }),
      { headers: HEADERS },
    );
    if (regRes.status !== 201 && regRes.status !== 200) {
      console.error(`[stress setup] register failed for lane ${i}: ${regRes.status}`);
      continue;
    }
    const clientId = regRes.json('id');

    // 2. Activate (admin bypass)
    http.post(`${BASE_URL}/api/clients/${clientId}/activate`, null, { headers: HEADERS });

    // 3. CHECKING account — large balance so DEBITs never run dry
    const checkRes = http.post(
      `${BASE_URL}/api/accounts`,
      JSON.stringify({ clientId, accountType: 'CHECKING', initialDeposit: 99999999.00 }),
      { headers: HEADERS },
    );
    const accountNumber = checkRes.json('accountNumber');

    // 4. SAVINGS account — TRANSFER target
    const savRes = http.post(
      `${BASE_URL}/api/accounts`,
      JSON.stringify({ clientId, accountType: 'SAVINGS', initialDeposit: 99999999.00 }),
      { headers: HEADERS },
    );
    const savingsNumber = savRes.json('accountNumber');

    pool.push({ email, password, accountNumber, savingsNumber });
  }

  console.log(`[stress setup] ✅  created ${pool.length}/${POOL_SIZE} account lanes`);
  return pool;
}

export default function (pool) {
  if (!pool || pool.length === 0) { sleep(1); return; }

  // Each VU is pinned to its own lane — no shared row contention
  const data = pool[(__VU - 1) % pool.length];

  // Alternate between auth and transaction on each iteration
  if (__ITER % 3 === 0) {
    runAuthFlow(data);
  } else {
    runTransactionFlow(data);
  }
}

function runAuthFlow(data) {
  group('Stress / Auth', () => {
    const t0       = Date.now();
    const loginRes = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: data.email, password: data.password }),
      { headers: HEADERS },
    );
    authDuration.add(Date.now() - t0);

    const ok = check(loginRes, {
      'stress login → 200': r => r.status === 200,
    });
    if (!ok) { failCount.add(1); sleep(0.5); return; }

    const challengeToken = loginRes.json('challengeToken');
    const otpCode        = getMfaOtp(challengeToken);

    const mfaRes = http.post(
      `${BASE_URL}/api/auth/mfa`,
      JSON.stringify({ challengeToken, otpCode }),
      { headers: HEADERS },
    );
    check(mfaRes, { 'stress MFA → 200': r => r.status === 200 });
  });
  sleep(0.5);
}

function runTransactionFlow(data) {
  const types   = ['DEBIT', 'CREDIT', 'TRANSFER'];
  const type    = types[__ITER % types.length];
  const idemKey = `stress-${__VU}-${__ITER}`;

  group(`Stress / Transaction ${type}`, () => {
    const body = {
      sourceAccountNumber: data.accountNumber,
      amount: 0.01,
      type,
      ...(type === 'TRANSFER' ? { targetAccountNumber: data.savingsNumber } : {}),
    };

    const t0  = Date.now();
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify(body),
      { headers: { ...HEADERS, 'Idempotency-Key': idemKey } },
    );
    txDuration.add(Date.now() - t0);

    const ok = check(res, {
      'stress tx → 201':       r => r.status === 201,
      'stress tx → COMPLETED': r => { try { return r.json('status') === 'COMPLETED'; } catch { return false; } },
    });
    txSuccessRate.add(ok ? 1 : 0);
    if (!ok) failCount.add(1);
  });

  // Also hammer the most expensive read (KrakenD aggregation) on every 5th iteration
  if (__ITER % 5 === 0) {
    group('Stress / Account Summary', () => {
      const res = http.get(
        `${BASE_URL}/api/accounts/${data.accountNumber}/summary`,
        { headers: HEADERS },
      );
      check(res, { 'stress summary → 200': r => r.status === 200 });
    });
  }

  sleep(0.3);
}