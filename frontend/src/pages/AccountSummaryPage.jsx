import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'

function TxBadge({ type }) {
  const t = type?.toLowerCase() || 'default'
  return <span className={`badge badge-${t}`}>{type}</span>
}

function StatusBadge({ status }) {
  const s = status?.toLowerCase() || 'default'
  return <span className={`badge badge-${s}`}>{status}</span>
}

function fmtDate(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('en-CA', { dateStyle: 'short', timeStyle: 'medium' })
}

function fmtAmt(amount, type) {
  const n = parseFloat(amount || 0).toLocaleString('en-CA', { minimumFractionDigits: 2 })
  const cls = type === 'CREDIT' ? 'credit' : type === 'DEBIT' ? 'debit' : ''
  const sign = type === 'CREDIT' ? '+' : type === 'DEBIT' ? '-' : ''
  return <span className={`td-amount ${cls}`}>{sign}${n}</span>
}

export default function AccountSummaryPage() {
  const { currentAccountNumber } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [accountNum, setAccountNum] = useState(currentAccountNumber || '')
  const [loading, setLoading] = useState(false)
  const [summary, setSummary] = useState(null)

  const handleFetch = async (e) => {
    e?.preventDefault()
    if (!accountNum) { addToast('Enter an account number', 'error'); return }
    setLoading(true)
    setSummary(null)
    try {
      const data = await api.getAccountSummary(accountNum)
      setSummary(data)
    } catch (err) {
      addToast(err.message || 'Could not fetch account summary', 'error')
    } finally {
      setLoading(false)
    }
  }

  const account = summary?.account
  const transactions = summary?.recentTransactions || []

  return (
    <div className="page-wide">
      <div className="page-header">
        <h1>📊 Account Summary <span className="uc-badge">UC-04</span></h1>
        <p className="page-subtitle">
          Aggregated view: account details + last 10 transactions (KrakenD multi-backend).
        </p>
      </div>

      <div className="card mb-4" style={{ maxWidth: 600 }}>
        <form onSubmit={handleFetch} className="form-grid">
          <div className="form-group">
            <label>Account Number</label>
            <div className="input-with-btn">
              <input
                value={accountNum}
                onChange={e => setAccountNum(e.target.value)}
                placeholder="e.g. 2536624609"
                className="mono"
                required
              />
              <button type="submit" className="btn btn-primary" disabled={loading}>
                {loading ? <span className="spinner-sm" /> : '🔍 Fetch'}
              </button>
            </div>
          </div>
        </form>
      </div>

      {loading && (
        <div className="loading-block"><span className="spinner" /> Loading summary…</div>
      )}

      {account && (
        <div className="card card-info mb-4">
          <div className="card-title">💳 Account Details</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Account Number</span>
              <code className="info-value">{account.accountNumber}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Type</span>
              <span className={`badge badge-${account.accountType?.toLowerCase()}`}>{account.accountType}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Balance</span>
              <span className="info-value" style={{ fontWeight: 700, fontSize: '1.15rem' }}>
                ${parseFloat(account.balance || 0).toLocaleString('en-CA', { minimumFractionDigits: 2 })}
              </span>
            </div>
            <div className="info-item">
              <span className="info-label">Status</span>
              <StatusBadge status={account.status} />
            </div>
            <div className="info-item">
              <span className="info-label">Client ID</span>
              <code className="info-value">{account.clientId}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Created</span>
              <span className="info-value">{fmtDate(account.createdAt)}</span>
            </div>
          </div>
          <div className="card-actions">
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => { navigate('/transactions') }}
            >
              💸 Make Transaction
            </button>
            <button className="btn btn-ghost btn-sm" onClick={handleFetch}>↻ Refresh</button>
          </div>
        </div>
      )}

      {summary && (
        <div className="card">
          <div className="flex-between mb-4">
            <div className="card-title" style={{ borderBottom: 'none', paddingBottom: 0, marginBottom: 0 }}>
              🕐 Recent Transactions
            </div>
            <span className="text-xs text-muted">{transactions.length} record(s)</span>
          </div>

          {transactions.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">📭</div>
              <p>No transactions yet for this account.</p>
            </div>
          ) : (
            <div className="table-container">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Type</th>
                    <th>Amount</th>
                    <th>Target</th>
                    <th>Status</th>
                    <th>Idempotency Key</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map(tx => (
                    <tr key={tx.id}>
                      <td className="text-sm">{fmtDate(tx.createdAt)}</td>
                      <td><TxBadge type={tx.type} /></td>
                      <td>{fmtAmt(tx.amount, tx.type)}</td>
                      <td className="td-mono text-sm">{tx.targetAccountNumber || '—'}</td>
                      <td><StatusBadge status={tx.status} /></td>
                      <td className="td-mono text-xs text-muted">{tx.idempotencyKey || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
