import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'
import FlowStepper from '../components/FlowStepper'

export default function VerifyOtpPage() {
  const { clientId, update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [cid, setCid] = useState(clientId || '')
  const [otpCode, setOtpCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [fetching, setFetching] = useState(false)
  const [result, setResult] = useState(null)

  const autoFetchOtp = async () => {
    setFetching(true)
    const otp = await api.fetchRegistrationOtp()
    setFetching(false)
    if (otp) {
      setOtpCode(otp)
      addToast(`OTP fetched from MailHog: ${otp}`, 'success')
    } else {
      addToast('Could not fetch OTP from MailHog. Open it manually.', 'warning')
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!cid) { addToast('Client ID is required', 'error'); return }
    setLoading(true)
    setResult(null)
    try {
      const data = await api.verifyOtp(cid, otpCode)
      setResult(data)
      update({ clientId: cid })
      addToast('Email verified! Account is now active. Proceed to Login.', 'success')
    } catch (err) {
      addToast(err.message || 'OTP verification failed', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <FlowStepper current="/verify" />

      <div className="page-header">
        <h1>✉️ Verify Email OTP <span className="uc-badge">UC-01</span></h1>
        <p className="page-subtitle">Enter the 6-digit code sent to your email to activate your account.</p>
      </div>

      <div className="alert alert-info mb-4">
        <span className="alert-icon">💡</span>
        <div>
          OTP emails are captured by <strong>MailHog</strong>. You can{' '}
          <a href="http://localhost:8025" target="_blank" rel="noopener noreferrer">open it here</a>
          {' '}or click <strong>"Auto-fetch OTP"</strong> below.
        </div>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Client ID</label>
            <input
              value={cid}
              onChange={e => setCid(e.target.value)}
              placeholder="UUID from registration"
              className="mono"
              required
            />
          </div>

          <div className="form-group">
            <label>6-Digit OTP Code</label>
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
              <a href="http://localhost:8025" target="_blank" rel="noopener noreferrer">Open MailHog</a> to get the code manually.
            </span>
          </div>

          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
              {loading ? <><span className="spinner-sm" /> Verifying…</> : '✅ Verify OTP'}
            </button>
          </div>
        </form>
      </div>

      {result && (
        <div className="card card-success mt-4">
          <div className="card-title">✅ Account Verified</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Status</span>
              <span className={`badge badge-${result.status?.toLowerCase()}`}>{result.status}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Name</span>
              <span className="info-value">{result.firstName} {result.lastName}</span>
            </div>
          </div>
          <div className="card-actions">
            <button className="btn btn-primary" onClick={() => navigate('/login')}>
              Next: Login →
            </button>
          </div>
        </div>
      )}

      <div className="card card-warning mt-4">
        <div className="card-title">⚡ Admin Bypass (skip OTP)</div>
        <p className="text-sm text-muted mb-4">
          If you just want to test without email verification, activate the client directly.
        </p>
        <AdminActivate clientId={cid} addToast={addToast} navigate={navigate} />
      </div>
    </div>
  )
}

function AdminActivate({ clientId, addToast, navigate }) {
  const [loading, setLoading] = useState(false)
  const handleActivate = async () => {
    if (!clientId) { addToast('Enter a Client ID first', 'error'); return }
    setLoading(true)
    try {
      await api.activateClient(clientId)
      addToast('Client activated via admin bypass!', 'success')
      navigate('/login')
    } catch (err) {
      addToast(err.message || 'Activation failed', 'error')
    } finally {
      setLoading(false)
    }
  }
  return (
    <button className="btn btn-secondary" onClick={handleActivate} disabled={loading}>
      {loading ? <><span className="spinner-sm spinner-dark" /> Activating…</> : '⚡ Activate (Admin Bypass)'}
    </button>
  )
}
