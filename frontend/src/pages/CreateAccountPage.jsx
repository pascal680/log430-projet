import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'
import FlowStepper from '../components/FlowStepper'

export default function CreateAccountPage() {
  const { clientId, update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState({
    clientId: clientId || '',
    accountType: 'CHECKING',
    initialDeposit: '1000.00',
  })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const set = (e) => setForm(p => ({ ...p, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    try {
      const dto = {
        clientId: form.clientId,
        accountType: form.accountType,
        initialDeposit: parseFloat(form.initialDeposit) || 0,
      }
      const data = await api.createAccount(dto)
      setResult(data)
      update({
        currentAccountNumber: data.accountNumber,
        currentAccountId: data.id,
        clientId: form.clientId,
      })
      addToast(`Account ${data.accountNumber} created successfully!`, 'success')
    } catch (err) {
      addToast(err.message || 'Account creation failed', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <FlowStepper current="/accounts/new" />

      <div className="page-header">
        <h1>💳 Open New Account <span className="uc-badge">UC-03</span></h1>
        <p className="page-subtitle">Create a CHECKING or SAVINGS account for a client.</p>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Client ID</label>
            <input
              name="clientId"
              value={form.clientId}
              onChange={set}
              placeholder="UUID of the client"
              className="mono"
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Account Type</label>
              <select name="accountType" value={form.accountType} onChange={set}>
                <option value="CHECKING">CHECKING</option>
                <option value="SAVINGS">SAVINGS</option>
              </select>
            </div>
            <div className="form-group">
              <label>Initial Deposit ($)</label>
              <input
                type="number"
                name="initialDeposit"
                value={form.initialDeposit}
                onChange={set}
                min="0"
                step="0.01"
                placeholder="1000.00"
              />
            </div>
          </div>

          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
              {loading ? <><span className="spinner-sm" /> Creating…</> : '💳 Open Account'}
            </button>
          </div>
        </form>
      </div>

      {result && (
        <div className="card card-success mt-4">
          <div className="card-title">✅ Account Created</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Account Number</span>
              <code className="info-value" style={{ fontSize: '1.1rem' }}>{result.accountNumber}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Type</span>
              <span className={`badge badge-${result.accountType?.toLowerCase()}`}>{result.accountType}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Balance</span>
              <span className="info-value" style={{ fontWeight: 700 }}>
                ${parseFloat(result.balance || 0).toLocaleString('en-CA', { minimumFractionDigits: 2 })}
              </span>
            </div>
            <div className="info-item">
              <span className="info-label">Status</span>
              <span className={`badge badge-${result.status?.toLowerCase()}`}>{result.status}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Account ID</span>
              <code className="info-value">{result.id}</code>
            </div>
          </div>
          <div className="alert alert-info mt-4">
            <span className="alert-icon">💡</span>
            <span>Account number <strong>{result.accountNumber}</strong> saved to session — use it for transactions.</span>
          </div>
          <div className="card-actions">
            <button className="btn btn-secondary" onClick={() => navigate('/accounts/summary')}>
              📊 View Summary
            </button>
            <button className="btn btn-primary" onClick={() => navigate('/transactions')}>
              💸 Make a Transaction →
            </button>
          </div>
        </div>
      )}

      <div className="card card-info mt-4">
        <div className="card-title">ℹ️ Account Types</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <span className="badge badge-checking">CHECKING</span>
            <p className="text-sm text-muted mt-2">Day-to-day banking with debit, credit and transfer support.</p>
          </div>
          <div>
            <span className="badge badge-savings">SAVINGS</span>
            <p className="text-sm text-muted mt-2">Savings account for long-term deposits.</p>
          </div>
        </div>
      </div>
    </div>
  )
}
