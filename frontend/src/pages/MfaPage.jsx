import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'
import FlowStepper from '../components/FlowStepper'

export default function MfaPage() {
  const { challengeToken, update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [token, setToken] = useState(challengeToken || '')
  const [otpCode, setOtpCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [fetching, setFetching] = useState(false)
  const [result, setResult] = useState(null)

  const autoFetchOtp = async () => {
    if (!token) { addToast('Enter the challenge token first', 'warning'); return }
    setFetching(true)
    const otp = await api.fetchMfaOtp(token)
    setFetching(false)
    if (otp) {
      setOtpCode(otp)
      addToast(`MFA OTP fetched from MailHog: ${otp}`, 'success')
    } else {
      addToast('Could not fetch OTP automatically. Check MailHog or container logs.', 'warning')
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    try {
      const data = await api.verifyMfa(token, otpCode)
      setResult(data)
      update({
        isAuthenticated: true,
        challengeToken: '',
        clientId: data.clientId || undefined,
      })
      addToast('MFA verified! You are now authenticated.', 'success')
      setTimeout(() => navigate('/dashboard'), 1200)
    } catch (err) {
      addToast(err.message || 'MFA verification failed. The token is single-use — re-login for a new OTP.', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <FlowStepper current="/mfa" />

      <div className="page-header">
        <h1>🔐 Two-Factor Authentication <span className="uc-badge">UC-02</span></h1>
        <p className="page-subtitle">Enter the 6-digit MFA code emailed after login.</p>
      </div>

      <div className="alert alert-warning mb-4">
        <span className="alert-icon">⚠️</span>
        <div>
          The challenge token is <strong>single-use</strong>. If you submit a wrong OTP, you must re-run Login to get a new one.
          You can also read the OTP from container logs:{' '}
          <code>docker compose logs identity-service | grep MFA-OTP</code>
        </div>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Challenge Token</label>
            <input
              value={token}
              onChange={e => setToken(e.target.value)}
              placeholder="UUID from login response"
              className="mono"
              required
            />
          </div>

          <div className="form-group">
            <label>6-Digit MFA Code</label>
            <div className="input-with-btn">
              <input
                value={otpCode}
                onChange={e => setOtpCode(e.target.value)}
                placeholder="123456"
                maxLength={6}
                className="mono"
                required
              />
              <button type="button" className="btn btn-secondary" onClick={autoFetchOtp} disabled={fetching}>
                {fetching ? <><span className="spinner-sm spinner-dark" /> Fetching…</> : '🔍 Auto-fetch'}
              </button>
            </div>
            <span className="form-hint">
              <a href="http://localhost:8025" target="_blank" rel="noopener noreferrer">Open MailHog</a>
              {' '}— look for an email with "Your login code is: XXXXXX"
            </span>
          </div>

          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
              {loading ? <><span className="spinner-sm" /> Verifying…</> : '🔐 Verify MFA'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => navigate('/login')}>
              ← Re-run Login
            </button>
          </div>
        </form>
      </div>

      {result && (
        <div className="card card-success mt-4">
          <div className="card-title">✅ Authentication Successful</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Status</span>
              <span className="badge badge-active">{result.status}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Client ID</span>
              <code className="info-value">{result.clientId}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Message</span>
              <span className="info-value">{result.message}</span>
            </div>
          </div>
          <div className="alert alert-success mt-4">
            <span className="alert-icon">🚀</span>
            <span>Redirecting to Dashboard…</span>
          </div>
        </div>
      )}
    </div>
  )
}
