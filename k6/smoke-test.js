/**
 * CanBankX – k6 Smoke Test
 *
 * Runs the full happy-path flow with a single VU (1 iteration) to verify
 * every critical endpoint is reachable and returns the expected status code.
 * Run this BEFORE a load test to confirm the stack is healthy.
 *
 * Usage:
 *   k6 run k6/smoke-test.js
 *   k6 run --env BASE_URL=http://localhost:8080 k6/smoke-test.js
 */

import http  from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL        = __ENV.BASE_URL     || 'http://localhost:8080';
const DIRECT_IDENTITY = __ENV.IDENTITY_URL || 'http://localhost:8081';
const DIRECT_ACCOUNT  = __ENV.ACCOUNT_URL  || 'http://localhost:8082';
const DIRECT_PAYMENT  = __ENV.PAYMENT_URL  || 'http://localhost:8083';
const MAILHOG_URL     = __ENV.MAILHOG_URL  || 'http://localhost:8025';
const HEADERS = { 'Content-Type': 'application/json' };

/**
 * Reads the real MFA OTP from MailHog.
 * Searches by the unique challengeToken embedded in the email body,
 * so concurrent VUs using the same account don't pick up each other's codes.
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
  vus:        1,
  iterations: 1,
  thresholds: {
    // Every single check must pass — zero tolerance in smoke test
    checks:            ['rate==1.0'],
    http_req_failed:   ['rate==0'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  let clientId, accountNumber, accountId, savingsNumber, challengeToken, txId;

  const ts       = Date.now();
  const email    = `smoke_${ts}@canbankx.ca`;
  const password = 'SmokeTest123!';
  const nas      = String(ts).slice(-9);

  // identity-service

  group('UC-01 / Register Client', () => {
    const res = http.post(
      `${BASE_URL}/api/clients`,
      JSON.stringify({
        firstName: 'Smoke', lastName: 'Test',
        email, password,
        phoneNumber: '5140000001',
        address: '1 Smoke Ave, Montréal, QC H1A 1A1',
        nas,
      }),
      { headers: HEADERS },
    );
    check(res, {
      'register → 201':  r => r.status === 201,
      'register → id':   r => { try { return !!r.json('id'); } catch { return false; } },
    });
    clientId = res.json('id');
  });

  if (!clientId) { console.error('Aborting: register failed'); return; }

  group('UC-01 / Activate Client (admin bypass)', () => {
    const res = http.post(
      `${BASE_URL}/api/clients/${clientId}/activate`,
      null,
      { headers: HEADERS },
    );
    check(res, {
      'activate → 200':       r => r.status === 200,
      'activate → ACTIVE':    r => { try { return r.json('status') === 'ACTIVE'; } catch { return false; } },
    });
  });

  group('UC-02 / Get Client by ID', () => {
    const res = http.get(`${BASE_URL}/api/clients/${clientId}`, { headers: HEADERS });
    check(res, {
      'get client → 200':   r => r.status === 200,
      'get client → email': r => { try { return r.json('email') === email; } catch { return false; } },
    });
  });

  group('UC-02 / List All Clients', () => {
    const res = http.get(`${BASE_URL}/api/clients/list`, { headers: HEADERS });
    check(res, { 'list clients → 200': r => r.status === 200 });
  });

  // auth

  group('UC-02 / Login (MFA Step 1)', () => {
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password }),
      { headers: HEADERS },
    );
    check(res, {
      'login → 200':              r => r.status === 200,
      'login → challengeToken':   r => { try { return !!r.json('challengeToken'); } catch { return false; } },
    });
    challengeToken = res.json('challengeToken');
  });

  group('UC-02 / Verify MFA (MFA Step 2)', () => {
    const otpCode = getMfaOtp(challengeToken);
    const res = http.post(
      `${BASE_URL}/api/auth/mfa`,
      JSON.stringify({ challengeToken, otpCode }),
      { headers: HEADERS },
    );
    check(res, {
      'MFA → 200':     r => r.status === 200,
      'MFA → SUCCESS': r => { try { return r.json('status') === 'SUCCESS'; } catch { return false; } },
    });
  });

  // account-service

  group('UC-03 / Create CHECKING Account', () => {
    const res = http.post(
      `${BASE_URL}/api/accounts`,
      JSON.stringify({ clientId, accountType: 'CHECKING', initialDeposit: 5000.00 }),
      { headers: HEADERS },
    );
    check(res, {
      'create checking → 201':       r => r.status === 201,
      'create checking → accountNumber': r => { try { return !!r.json('accountNumber'); } catch { return false; } },
      'create checking → balance 5000':  r => { try { return parseFloat(r.json('balance')) === 5000; } catch { return false; } },
    });
    accountNumber = res.json('accountNumber');
    accountId     = res.json('id');
  });

  group('UC-03 / Create SAVINGS Account', () => {
    const res = http.post(
      `${BASE_URL}/api/accounts`,
      JSON.stringify({ clientId, accountType: 'SAVINGS', initialDeposit: 1000.00 }),
      { headers: HEADERS },
    );
    check(res, {
      'create savings → 201': r => r.status === 201,
    });
    savingsNumber = res.json('accountNumber');
  });

  group('UC-04 / Get Account by UUID', () => {
    const res = http.get(`${BASE_URL}/api/accounts/${accountId}`, { headers: HEADERS });
    check(res, {
      'get account → 200':          r => r.status === 200,
      'get account → accountNumber': r => { try { return r.json('accountNumber') === accountNumber; } catch { return false; } },
    });
  });

  group('UC-04 / List Accounts by Client', () => {
    const res = http.get(
      `${BASE_URL}/api/accounts/list?clientId=${clientId}`,
      { headers: HEADERS },
    );
    check(res, { 'list accounts → 200': r => r.status === 200 });
  });

  // payment-service

  group('UC-05 / Submit DEBIT Transaction', () => {
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify({ sourceAccountNumber: accountNumber, amount: 100.00, type: 'DEBIT' }),
      { headers: { ...HEADERS, 'Idempotency-Key': `smoke-debit-${ts}` } },
    );
    check(res, {
      'debit → 201':       r => r.status === 201,
      'debit → COMPLETED': r => { try { return r.json('status') === 'COMPLETED'; } catch { return false; } },
    });
    txId = res.json('id');
  });

  group('UC-05 / Submit CREDIT Transaction', () => {
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify({ sourceAccountNumber: accountNumber, amount: 200.00, type: 'CREDIT' }),
      { headers: { ...HEADERS, 'Idempotency-Key': `smoke-credit-${ts}` } },
    );
    check(res, {
      'credit → 201':       r => r.status === 201,
      'credit → COMPLETED': r => { try { return r.json('status') === 'COMPLETED'; } catch { return false; } },
    });
  });

  group('UC-05 / Submit TRANSFER Transaction', () => {
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify({
        sourceAccountNumber: accountNumber,
        targetAccountNumber: savingsNumber,
        amount: 50.00,
        type: 'TRANSFER',
      }),
      { headers: { ...HEADERS, 'Idempotency-Key': `smoke-transfer-${ts}` } },
    );
    check(res, {
      'transfer → 201':       r => r.status === 201,
      'transfer → COMPLETED': r => { try { return r.json('status') === 'COMPLETED'; } catch { return false; } },
    });
  });

  group('UC-05 / Idempotency – Replay DEBIT Key', () => {
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify({ sourceAccountNumber: accountNumber, amount: 100.00, type: 'DEBIT' }),
      { headers: { ...HEADERS, 'Idempotency-Key': `smoke-debit-${ts}` } },
    );
    check(res, {
      'idempotency → 201':        r => r.status === 201,
      'idempotency → same tx id': r => { try { return r.json('id') === txId; } catch { return false; } },
    });
  });

  group('UC-04 / Get Transaction by ID', () => {
    const res = http.get(`${BASE_URL}/api/transactions/${txId}`, { headers: HEADERS });
    check(res, {
      'get tx → 200': r => r.status === 200,
      'get tx → id matches': r => { try { return r.json('id') === txId; } catch { return false; } },
    });
  });

  group('UC-04 / List Transactions by Account', () => {
    const res = http.get(
      `${BASE_URL}/api/transactions/list?accountNumber=${accountNumber}`,
      { headers: HEADERS },
    );
    check(res, { 'list tx → 200': r => r.status === 200 });
  });

  group('UC-04 / Account Summary (KrakenD aggregation)', () => {
    const res = http.get(
      `${BASE_URL}/api/accounts/${accountNumber}/summary`,
      { headers: HEADERS },
    );
    check(res, {
      'summary → 200':                    r => r.status === 200,
      'summary → has account key':        r => { try { return !!r.json('account'); } catch { return false; } },
      'summary → has recentTransactions': r => { try { return r.json('recentTransactions') !== undefined; } catch { return false; } },
    });
  });

  // error cases — expected non-2xx responses

  group('Error / 404 Client Not Found', () => {
    const res = http.get(
      `${BASE_URL}/api/clients/00000000-0000-0000-0000-000000000000`,
      { headers: HEADERS, responseCallback: http.expectedStatuses(404) },
    );
    check(res, { '404 client → 404': r => r.status === 404 });
  });

  group('Error / 401 Wrong Password', () => {
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password: 'wrongpassword' }),
      { headers: HEADERS, responseCallback: http.expectedStatuses(401) },
    );
    check(res, { '401 wrong pwd → 401': r => r.status === 401 });
  });

  group('Error / 409 Duplicate Email', () => {
    const res = http.post(
      `${BASE_URL}/api/clients`,
      JSON.stringify({
        firstName: 'Dupe', lastName: 'Test',
        email, password,
        phoneNumber: '5140000002',
        address: '2 Dupe Ave, Montréal, QC H1A 1A2',
        nas: String(ts + 1).slice(-9),
      }),
      { headers: HEADERS, responseCallback: http.expectedStatuses(409) },
    );
    check(res, { '409 duplicate email → 409': r => r.status === 409 });
  });

  group('Error / 422 Insufficient Funds', () => {
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify({ sourceAccountNumber: accountNumber, amount: 9999999.00, type: 'DEBIT' }),
      { headers: { ...HEADERS, 'Idempotency-Key': `smoke-overdraft-${ts}` }, responseCallback: http.expectedStatuses(422) },
    );
    check(res, { '422 overdraft → 422': r => r.status === 422 });
  });

  group('Error / 400 Invalid Account Type', () => {
    const res = http.post(
      `${BASE_URL}/api/accounts`,
      JSON.stringify({ clientId, accountType: 'MORTGAGE', initialDeposit: 0 }),
      { headers: HEADERS, responseCallback: http.expectedStatuses(400) },
    );
    check(res, { '400 bad account type → 400': r => r.status === 400 });
  });

  group('Error / 400 Transfer without Target', () => {
    const res = http.post(
      `${BASE_URL}/api/transactions`,
      JSON.stringify({ sourceAccountNumber: accountNumber, targetAccountNumber: '', amount: 50.00, type: 'TRANSFER' }),
      { headers: { ...HEADERS, 'Idempotency-Key': `smoke-notarget-${ts}` }, responseCallback: http.expectedStatuses(400) },
    );
    check(res, { '400 no target → 400': r => r.status === 400 });
  });

  // direct health checks (bypasses KrakenD)

  group('Health / Identity', () => {
    const res = http.get(`${DIRECT_IDENTITY}/actuator/health`);
    check(res, { 'identity health → 200': r => r.status === 200 });
  });

  group('Health / Account', () => {
    const res = http.get(`${DIRECT_ACCOUNT}/actuator/health`);
    check(res, { 'account health → 200': r => r.status === 200 });
  });

  group('Health / Payment', () => {
    const res = http.get(`${DIRECT_PAYMENT}/actuator/health`);
    check(res, { 'payment health → 200': r => r.status === 200 });
  });
}
