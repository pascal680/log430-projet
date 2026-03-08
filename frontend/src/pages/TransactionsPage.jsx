import React, { useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'
import FlowStepper from '../components/FlowStepper'

function genUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

function TxBadge({ type }) {
  return <span className={`badge badge-${type?.toLowerCase() || 'default'}`}>{type}</span>
}
function StatusBadge({ status }) {
  return <span className={`badge badge-${status?.toLowerCase() || 'default'}`}>{status}</span>
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

export default function TransactionsPage() {
  const { currentAccountNumber, lastIdempotencyKey, update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState('new')   // 'new' | 'history'
  const [txType, setTxType] = useState('DEBIT')        // DEBIT | CREDIT | TRANSFER

  // New transaction form
  const [form, setForm] = useState({
    sourceAccountNumber: currentAccountNumber || '',
    targetAccountNumber: '',
    amount: '50.00',
  })
  const [idempotencyKey, setIdempotencyKey] = useState(genUUID())
  const [loading, setLoading] = useState(false)
  const [txResult, setTxResult] = useState(null)
  const [idempotencyWarning, setIdempotencyWarning] = useState(false)

  // History
  const [histAccountNum, setHistAccountNum] = useState(currentAccountNumber || '')
  const [histLoading, setHistLoading] = useState(false)
  const [transactions, setTransactions] = useState([])
  const [histFetched, setHistFetched] = useState(false)

  // Single transaction lookup
  const [lookupId, setLookupId] = useState('')
  const [lookupLoading, setLookupLoading] = useState(false)
  const [lookupResult, setLookupResult] = useState(null)

  const set = (e) => setForm(p => ({ ...p, [e.target.name]: e.target.value }))

  const handleGenKey = () => {
    const key = genUUID()
    setIdempotencyKey(key)
    setIdempotencyWarning(false)
  }

  const handleReuseKey = () => {
    if (lastIdempotencyKey) {
      setIdempotencyKey(lastIdempotencyKey)
      setIdempotencyWarning(true)
      addToast('Reusing previous Idempotency-Key — this tests idempotency (should return original transaction).', 'warning')
    } else {
      addToast('No previous key in session. Submit a transaction first.', 'warning')
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!idempotencyKey) { addToast('Idempotency-Key is required', 'error'); return }

    setLoading(true)
    setTxResult(null)
    try {
      const dto = {
        sourceAccountNumber: form.sourceAccountNumber,
        amount: parseFloat(form.amount),
        type: txType,
        ...(txType === 'TRANSFER' && { targetAccountNumber: form.targetAccountNumber }),
      }
      const data = await api.createTransaction(dto, idempotencyKey)
      setTxResult(data)
      update({ lastTransactionId: data.id, lastIdempotencyKey: idempotencyKey })
      if (idempotencyWarning) {
        addToast('Idempotency test: server returned the original transaction.', 'success')
      } else {
        addToast(`${txType} transaction submitted successfully!`, 'success')
      }
    } catch (err) {
      addToast(err.message || 'Transaction failed', 'error')
    } finally {
      setLoading(false)
    }
  }

  const fetchHistory = async (e) => {
    e?.preventDefault()
    setHistLoading(true)
    setHistFetched(false)
    try {
      const data = await api.listTransactions(histAccountNum)
      const list = Array.isArray(data) ? data : (data?.collection || [])
      setTransactions(list)
      setHistFetched(true)
    } catch (err) {
      addToast(err.message || 'Could not fetch transactions', 'error')
    } finally { setHistLoading(false) }
  }

  const fetchSingleTx = async (e) => {
    e?.preventDefault()
    if (!lookupId) return
    setLookupLoading(true)
    setLookupResult(null)
    try {
      const data = await api.getTransaction(lookupId)
      setLookupResult(data)
    } catch (err) {
      addToast(err.message || 'Transaction not found', 'error')
    } finally { setLookupLoading(false) }
  }

  return (
    <div className="page-wide">
      <FlowStepper current="/transactions" />

      <div className="page-header">
        <h1>💸 Transactions <span className="uc-badge">UC-05</span></h1>
        <p className="page-subtitle">Submit DEBIT, CREDIT and TRANSFER transactions. Test idempotency with duplicate keys.</p>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'new' ? 'active' : ''}`} onClick={() => setActiveTab('new')}>
          ➕ New Transaction
        </button>
        <button className={`tab ${activeTab === 'history' ? 'active' : ''}`} onClick={() => setActiveTab('history')}>
          📋 Transaction History
        </button>
        <button className={`tab ${activeTab === 'lookup' ? 'active' : ''}`} onClick={() => setActiveTab('lookup')}>
          🔍 Lookup by ID
        </button>
      </div>

      {/* ── NEW TRANSACTION ── */}
      {activeTab === 'new' && (
        <div>
          {/* Transaction type selector */}
          <div style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
            {['DEBIT', 'CREDIT', 'TRANSFER'].map(t => (
              <button
                key={t}
                className={`btn ${txType === t ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setTxType(t)}
              >
                {t === 'DEBIT' ? '↑ DEBIT' : t === 'CREDIT' ? '↓ CREDIT' : '⇄ TRANSFER'}
              </button>
            ))}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 20, alignItems: 'start' }}>
            <div className="card">
              <div className="card-title">
                {txType === 'DEBIT' ? '↑ DEBIT' : txType === 'CREDIT' ? '↓ CREDIT' : '⇄ TRANSFER'} Transaction
              </div>

              {idempotencyWarning && (
                <div className="alert alert-warning mb-4">
                  <span className="alert-icon">⚠️</span>
                  <div>
                    <strong>Idempotency Test Active</strong> — you are reusing the previous key.
                    The server should return the original transaction without creating a duplicate.
                  </div>
                </div>
              )}

              <form onSubmit={handleSubmit} className="form-grid">
                <div className="form-group">
                  <label>Source Account Number</label>
                  <input
                    name="sourceAccountNumber"
                    value={form.sourceAccountNumber}
                    onChange={set}
                    placeholder="e.g. 2536624609"
                    className="mono"
                    required
                  />
                </div>

                {txType === 'TRANSFER' && (
                  <div className="form-group">
                    <label>Target Account Number</label>
                    <input
                      name="targetAccountNumber"
                      value={form.targetAccountNumber}
                      onChange={set}
                      placeholder="e.g. 5626038191"
                      className="mono"
                      required
                    />
                  </div>
                )}

                <div className="form-group">
                  <label>Amount ($)</label>
                  <input
                    type="number"
                    name="amount"
                    value={form.amount}
                    onChange={set}
                    min="0.01"
                    step="0.01"
                    placeholder="50.00"
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Idempotency-Key</label>
                  <div className="input-with-btn">
                    <input
                      value={idempotencyKey}
                      onChange={e => { setIdempotencyKey(e.target.value); setIdempotencyWarning(false) }}
                      className="mono"
                      required
                    />
                    <button type="button" className="btn btn-secondary btn-sm" onClick={handleGenKey} title="Generate new UUID">
                      🔄
                    </button>
                  </div>
                  <span className="form-hint">
                    Unique per transaction. Reuse the same key to test idempotency.
                  </span>
                </div>

                <div className="form-actions">
                  <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
                    {loading ? <><span className="spinner-sm" /> Submitting…</> : `Submit ${txType}`}
                  </button>
                  <button type="button" className="btn btn-secondary" onClick={handleReuseKey}>
                    🔁 Reuse Last Key (Idempotency Test)
                  </button>
                </div>
              </form>
            </div>

            {/* Info panel */}
            <div>
              <div className="card card-info">
                <div className="card-title">💡 Transaction Types</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div>
                    <span className="badge badge-debit">DEBIT</span>
                    <p className="text-sm text-muted mt-2">Withdraws money from the source account.</p>
                  </div>
                  <div>
                    <span className="badge badge-credit">CREDIT</span>
                    <p className="text-sm text-muted mt-2">Deposits money into the source account.</p>
                  </div>
                  <div>
                    <span className="badge badge-transfer">TRANSFER</span>
                    <p className="text-sm text-muted mt-2">Moves funds between two accounts. Requires a target account number.</p>
                  </div>
                </div>
              </div>

              <div className="card card-warning mt-3">
                <div className="card-title">🔑 Idempotency</div>
                <p className="text-sm text-muted">
                  Submitting the same <code>Idempotency-Key</code> twice returns the original
                  transaction without creating a duplicate.
                </p>
                <p className="text-sm text-muted mt-2">
                  Click <strong>"Reuse Last Key"</strong> to test this behavior.
                </p>
              </div>
            </div>
          </div>

          {txResult && (
            <div className={`card mt-4 ${idempotencyWarning ? 'card-warning' : 'card-success'}`}>
              <div className="card-title">
                {idempotencyWarning ? '🔁 Idempotency Response (original returned)' : '✅ Transaction Submitted'}
              </div>
              <div className="info-grid">
                <div className="info-item">
                  <span className="info-label">Transaction ID</span>
                  <code className="info-value">{txResult.id}</code>
                </div>
                <div className="info-item">
                  <span className="info-label">Type</span>
                  <TxBadge type={txResult.type} />
                </div>
                <div className="info-item">
                  <span className="info-label">Amount</span>
                  <span className="info-value" style={{ fontWeight: 700 }}>
                    ${parseFloat(txResult.amount || 0).toLocaleString('en-CA', { minimumFractionDigits: 2 })}
                  </span>
                </div>
                <div className="info-item">
                  <span className="info-label">Status</span>
                  <StatusBadge status={txResult.status} />
                </div>
                <div className="info-item">
                  <span className="info-label">Source</span>
                  <code className="info-value">{txResult.sourceAccountNumber}</code>
                </div>
                {txResult.targetAccountNumber && (
                  <div className="info-item">
                    <span className="info-label">Target</span>
                    <code className="info-value">{txResult.targetAccountNumber}</code>
                  </div>
                )}
                <div className="info-item">
                  <span className="info-label">Idempotency Key</span>
                  <code className="info-value">{txResult.idempotencyKey}</code>
                </div>
                <div className="info-item">
                  <span className="info-label">Created</span>
                  <span className="info-value">{fmtDate(txResult.createdAt)}</span>
                </div>
              </div>
              {txResult.auditNote && (
                <div className="alert alert-info mt-4">
                  <span className="alert-icon">📋</span>
                  <span>{txResult.auditNote}</span>
                </div>
              )}
              <div className="card-actions">
                <button className="btn btn-secondary btn-sm" onClick={() => navigate('/accounts/summary')}>
                  📊 View Account Summary
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── HISTORY ── */}
      {activeTab === 'history' && (
        <div>
          <div className="card mb-4" style={{ maxWidth: 580 }}>
            <form onSubmit={fetchHistory} className="form-grid">
              <div className="form-group">
                <label>Account Number <span className="text-muted">(leave empty for all)</span></label>
                <div className="input-with-btn">
                  <input
                    value={histAccountNum}
                    onChange={e => setHistAccountNum(e.target.value)}
                    placeholder="e.g. 2536624609"
                    className="mono"
                  />
                  <button type="submit" className="btn btn-primary" disabled={histLoading}>
                    {histLoading ? <span className="spinner-sm" /> : '🔍 Load'}
                  </button>
                </div>
              </div>
            </form>
          </div>

          {histLoading && <div className="loading-block"><span className="spinner" /> Loading transactions…</div>}

          {histFetched && (
            <div className="card">
              <div className="flex-between mb-4">
                <div className="card-title" style={{ borderBottom: 'none', paddingBottom: 0, marginBottom: 0 }}>
                  Transaction History
                </div>
                <span className="text-xs text-muted">{transactions.length} record(s)</span>
              </div>

              {transactions.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-icon">📭</div>
                  <p>No transactions found.</p>
                </div>
              ) : (
                <div className="table-container">
                  <table>
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Type</th>
                        <th>Amount</th>
                        <th>Source</th>
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
                          <td className="td-mono text-sm">{tx.sourceAccountNumber}</td>
                          <td className="td-mono text-sm">{tx.targetAccountNumber || '—'}</td>
                          <td><StatusBadge status={tx.status} /></td>
                          <td className="td-mono text-xs text-muted" style={{ maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {tx.idempotencyKey || '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── LOOKUP ── */}
      {activeTab === 'lookup' && (
        <div>
          <div className="card mb-4" style={{ maxWidth: 580 }}>
            <form onSubmit={fetchSingleTx} className="form-grid">
              <div className="form-group">
                <label>Transaction ID (UUID)</label>
                <div className="input-with-btn">
                  <input
                    value={lookupId}
                    onChange={e => setLookupId(e.target.value)}
                    placeholder="UUID"
                    className="mono"
                    required
                  />
                  <button type="submit" className="btn btn-primary" disabled={lookupLoading}>
                    {lookupLoading ? <span className="spinner-sm" /> : '🔍 Lookup'}
                  </button>
                </div>
              </div>
            </form>
          </div>

          {lookupResult && (
            <div className="card card-info">
              <div className="card-title">Transaction Details</div>
              <div className="info-grid">
                <div className="info-item"><span className="info-label">ID</span><code className="info-value">{lookupResult.id}</code></div>
                <div className="info-item"><span className="info-label">Type</span><TxBadge type={lookupResult.type} /></div>
                <div className="info-item">
                  <span className="info-label">Amount</span>
                  <span className="info-value" style={{ fontWeight: 700 }}>
                    ${parseFloat(lookupResult.amount || 0).toLocaleString('en-CA', { minimumFractionDigits: 2 })}
                  </span>
                </div>
                <div className="info-item"><span className="info-label">Status</span><StatusBadge status={lookupResult.status} /></div>
                <div className="info-item"><span className="info-label">Source</span><code className="info-value">{lookupResult.sourceAccountNumber}</code></div>
                {lookupResult.targetAccountNumber && (
                  <div className="info-item"><span className="info-label">Target</span><code className="info-value">{lookupResult.targetAccountNumber}</code></div>
                )}
                <div className="info-item"><span className="info-label">Idempotency Key</span><code className="info-value">{lookupResult.idempotencyKey}</code></div>
                <div className="info-item"><span className="info-label">Created</span><span className="info-value">{fmtDate(lookupResult.createdAt)}</span></div>
                <div className="info-item"><span className="info-label">Updated</span><span className="info-value">{fmtDate(lookupResult.updatedAt)}</span></div>
              </div>
              {lookupResult.auditNote && (
                <div className="alert alert-info mt-4">
                  <span className="alert-icon">📋</span>
                  <span>{lookupResult.auditNote}</span>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
