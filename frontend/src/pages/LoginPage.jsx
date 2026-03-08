import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'
import FlowStepper from '../components/FlowStepper'

export default function LoginPage() {
  const { clientEmail, update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: clientEmail || '', password: 'SecurePass123!' })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const set = (e) => setForm(p => ({ ...p, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    try {
      const data = await api.login(form.email, form.password)
      setResult(data)
      update({ challengeToken: data.challengeToken, clientEmail: form.email })
      addToast('Login successful! Check MailHog for your MFA code.', 'success')
      setTimeout(() => navigate('/mfa'), 1200)
    } catch (err) {
      addToast(err.message || 'Login failed. Check credentials and account status.', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <FlowStepper current="/login" />

      <div className="page-header">
        <h1>🔑 Login <span className="uc-badge">UC-02</span></h1>
        <p className="page-subtitle">Validates credentials and sends a 6-digit MFA code to your email.</p>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Email</label>
            <input type="email" name="email" value={form.email} onChange={set} required placeholder="jean.tremblay@canbankx.ca" />
          </div>

          <div className="form-group">
            <label>Password</label>
            <input type="password" name="password" value={form.password} onChange={set} required />
          </div>

          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
              {loading ? <><span className="spinner-sm" /> Signing in…</> : '🔑 Sign In'}
            </button>
          </div>
        </form>
      </div>

      {result && (
        <div className="card card-success mt-4">
          <div className="card-title">✅ Credentials Accepted — MFA Required</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Challenge Token</span>
              <code className="info-value">{result.challengeToken}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Message</span>
              <span className="info-value">{result.message}</span>
            </div>
          </div>
          <div className="alert alert-info mt-4">
            <span className="alert-icon">📧</span>
            <span>A 6-digit code has been emailed. Redirecting to MFA verification…</span>
          </div>
        </div>
      )}
    </div>
  )
}
