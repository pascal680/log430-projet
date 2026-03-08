import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'
import FlowStepper from '../components/FlowStepper'

export default function RegisterPage() {
  const { update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState({
    firstName: 'Jean',
    lastName: 'Tremblay',
    email: 'jean.tremblay@canbankx.ca',
    password: 'SecurePass123!',
    phoneNumber: '5141234567',
    address: '123 Rue Sainte-Catherine, Montréal, QC H3G 1M8',
    nas: '123456789',
  })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const set = (e) => setForm(p => ({ ...p, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    try {
      const data = await api.registerClient(form)
      setResult(data)
      update({
        clientId: data.id,
        clientEmail: form.email,
        clientFirstName: form.firstName,
        clientLastName: form.lastName,
      })
      addToast(`Client registered! Check MailHog for your verification OTP.`, 'success')
    } catch (err) {
      addToast(err.message || 'Registration failed', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <FlowStepper current="/register" />

      <div className="page-header">
        <h1>📝 Register Client <span className="uc-badge">UC-01</span></h1>
        <p className="page-subtitle">Creates a PENDING account and sends a 6-digit OTP to the client's email.</p>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-row">
            <div className="form-group">
              <label>First Name</label>
              <input name="firstName" value={form.firstName} onChange={set} required />
            </div>
            <div className="form-group">
              <label>Last Name</label>
              <input name="lastName" value={form.lastName} onChange={set} required />
            </div>
          </div>

          <div className="form-group">
            <label>Email</label>
            <input type="email" name="email" value={form.email} onChange={set} required />
          </div>

          <div className="form-group">
            <label>Password</label>
            <input type="password" name="password" value={form.password} onChange={set} required />
            <span className="form-hint">Min 8 chars, uppercase, digit, special character</span>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Phone Number</label>
              <input name="phoneNumber" value={form.phoneNumber} onChange={set} required placeholder="5141234567" />
            </div>
            <div className="form-group">
              <label>NAS (Social Insurance Number)</label>
              <input name="nas" value={form.nas} onChange={set} required placeholder="123456789" className="mono" />
            </div>
          </div>

          <div className="form-group">
            <label>Address</label>
            <input name="address" value={form.address} onChange={set} required />
          </div>

          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
              {loading ? <><span className="spinner-sm" /> Registering…</> : '📝 Register Client'}
            </button>
          </div>
        </form>
      </div>

      {result && (
        <div className="card card-success mt-4">
          <div className="card-title">✅ Client Registered Successfully</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Client ID</span>
              <code className="info-value">{result.id}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Name</span>
              <span className="info-value">{result.firstName} {result.lastName}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Email</span>
              <span className="info-value">{result.email}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Status</span>
              <span className={`badge badge-${result.status?.toLowerCase()}`}>{result.status}</span>
            </div>
          </div>

          <hr className="divider" />

          <div className="alert alert-info">
            <span className="alert-icon">📧</span>
            <div>
              A 6-digit OTP has been sent to <strong>{result.email}</strong>.
              Open MailHog to read it, then proceed to Verify OTP.
            </div>
          </div>

          <div className="card-actions">
            <a href="http://localhost:8025" target="_blank" rel="noopener noreferrer" className="btn btn-secondary">
              📬 Open MailHog
            </a>
            <button className="btn btn-primary" onClick={() => navigate('/verify')}>
              Next: Verify OTP →
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
