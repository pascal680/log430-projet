const BASE = '/api'
const MAILHOG = '/mailhog'

async function req(method, path, body = null, extraHeaders = {}) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json', ...extraHeaders },
  }
  if (body !== null) opts.body = JSON.stringify(body)

  const res = await fetch(BASE + path, opts)
  const text = await res.text()
  let data
  try { data = JSON.parse(text) } catch { data = text }
  if (!res.ok) {
    const msg = (typeof data === 'object' && data !== null)
      ? (data.message || data.error || JSON.stringify(data))
      : (data || `HTTP ${res.status}`)
    const err = new Error(msg)
    err.status = res.status
    err.data = data
    throw err
  }
  return data
}

export const api = {
  /* ── Identity / Clients ── */
  registerClient: (dto)               => req('POST', '/clients', dto),
  verifyOtp:      (clientId, otpCode) => req('POST', `/clients/${clientId}/verify`, { otpCode }),
  activateClient: (clientId)          => req('POST', `/clients/${clientId}/activate`),
  getClient:      (clientId)          => req('GET',  `/clients/${clientId}`),
  listClients:    ()                  => req('GET',  '/clients/list'),

  /* ── Auth ── */
  login:     (email, password)                  => req('POST', '/auth/login', { email, password }),
  verifyMfa: (challengeToken, otpCode)          => req('POST', '/auth/mfa',   { challengeToken, otpCode }),

  /* ── Accounts ── */
  createAccount:     (dto)           => req('POST', '/accounts', dto),
  getAccount:        (accountId)     => req('GET',  `/accounts/${accountId}`),
  getAccountSummary: (accountNumber) => req('GET',  `/accounts/${accountNumber}/summary`),
  listAccounts:      (clientId)      => req('GET',  `/accounts/list${clientId ? `?clientId=${encodeURIComponent(clientId)}` : ''}`),

  /* ── Transactions ── */
  createTransaction: (dto, idempotencyKey) => req('POST', '/transactions', dto, { 'Idempotency-Key': idempotencyKey }),
  getTransaction:    (txId)               => req('GET',  `/transactions/${txId}`),
  listTransactions:  (accountNumber)      => req('GET',  `/transactions/list${accountNumber ? `?accountNumber=${encodeURIComponent(accountNumber)}` : ''}`),

  /* ── MailHog helpers ── */
  fetchMfaOtp: async (challengeToken) => {
    try {
      const res = await fetch(`${MAILHOG}/api/v2/messages?limit=20`)
      if (!res.ok) return null
      const data = await res.json()
      if (!data?.items?.length) return null
      const email = data.items.find(m => {
        const body = m?.Content?.Body || ''
        return challengeToken ? body.includes(challengeToken) : true
      })
      if (!email) return null
      const match = (email.Content.Body || '').match(/Your login code is:\s*(\d{6})/)
      return match ? match[1] : null
    } catch { return null }
  },

  fetchRegistrationOtp: async () => {
    try {
      const res = await fetch(`${MAILHOG}/api/v2/messages?limit=20`)
      if (!res.ok) return null
      const data = await res.json()
      if (!data?.items?.length) return null
      // Look for verification / OTP email — find the most recent one with a 6-digit code
      const emails = [...data.items].sort((a, b) => {
        const ta = a?.Created ? new Date(a.Created).getTime() : 0
        const tb = b?.Created ? new Date(b.Created).getTime() : 0
        return tb - ta
      })
      for (const m of emails) {
        const body = m?.Content?.Body || ''
        const subject = (m?.Content?.Headers?.Subject || [])[0] || ''
        if (subject.toLowerCase().includes('verif') || subject.toLowerCase().includes('otp') || body.match(/\d{6}/)) {
          const match = body.match(/\b(\d{6})\b/)
          if (match) return match[1]
        }
      }
      return null
    } catch { return null }
  },
}
