import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'
import { api } from '../api/api'

function StatusBadge({ status }) {
  const s = status?.toLowerCase() || 'default'
  return <span className={`badge badge-${s}`}>{status || '—'}</span>
}

export default function DashboardPage() {
  const { clientId, clientFirstName, clientLastName, isAuthenticated, update } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const [cid, setCid] = useState(clientId || '')
  const [client, setClient] = useState(null)
  const [accounts, setAccounts] = useState([])
  const [loadingClient, setLoadingClient] = useState(false)
  const [loadingAccounts, setLoadingAccounts] = useState(false)

  const fetchClient = async (id) => {
    if (!id) return
    setLoadingClient(true)
    try {
      const data = await api.getClient(id)
      setClient(data)
      update({ clientId: id, clientFirstName: data.firstName, clientLastName: data.lastName })
    } catch (err) {
      addToast(err.message || 'Could not fetch client', 'error')
    } finally { setLoadingClient(false) }
  }

  const fetchAccounts = async (id) => {
    if (!id) return
    setLoadingAccounts(true)
    try {
      const data = await api.listAccounts(id)
      const list = Array.isArray(data) ? data : (data?.collection || [])
      setAccounts(list)
    } catch (err) {
      addToast(err.message || 'Could not fetch accounts', 'error')
    } finally { setLoadingAccounts(false) }
  }

  useEffect(() => {
    if (clientId) {
      setCid(clientId)
      fetchClient(clientId)
      fetchAccounts(clientId)
    }
  }, [clientId])

  const handleLookup = (e) => {
    e.preventDefault()
    fetchClient(cid)
    fetchAccounts(cid)
  }

  const handleSelectAccount = (acc) => {
    update({ currentAccountNumber: acc.accountNumber, currentAccountId: acc.id })
    addToast(`Account ${acc.accountNumber} selected as active.`, 'info')
  }

  return (
    <div className="page-wide">
      <div className="page-header">
        <h1>
          🏠 Dashboard
          {isAuthenticated && <span className="badge badge-active" style={{ marginLeft: 10 }}>Authenticated</span>}
        </h1>
        <p className="page-subtitle">
          {(clientFirstName || clientLastName)
            ? `Welcome, ${clientFirstName} ${clientLastName}`
            : 'View client info and manage accounts.'}
        </p>
      </div>

      {/* Client Lookup */}
      <div className="card mb-4" style={{ maxWidth: 680 }}>
        <div className="card-title">🪪 Client Lookup</div>
        <form onSubmit={handleLookup} className="form-grid">
          <div className="form-group">
            <label>Client ID</label>
            <div className="input-with-btn">
              <input value={cid} onChange={e => setCid(e.target.value)} placeholder="Enter UUID" className="mono" />
              <button type="submit" className="btn btn-primary" disabled={loadingClient}>
                {loadingClient ? <span className="spinner-sm" /> : '🔍 Load'}
              </button>
            </div>
          </div>
        </form>
      </div>

      {client && (
        <div className="card card-info mb-4" style={{ maxWidth: 680 }}>
          <div className="card-title">👤 Client Profile</div>
          <div className="info-grid">
            <div className="info-item">
              <span className="info-label">Name</span>
              <span className="info-value">{client.firstName} {client.lastName}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Email</span>
              <span className="info-value">{client.email}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Phone</span>
              <span className="info-value">{client.phoneNumber}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Status</span>
              <StatusBadge status={client.status} />
            </div>
            <div className="info-item">
              <span className="info-label">Client ID</span>
              <code className="info-value">{client.id}</code>
            </div>
            <div className="info-item">
              <span className="info-label">Created</span>
              <span className="info-value">{client.createdAt ? new Date(client.createdAt).toLocaleString() : '—'}</span>
            </div>
          </div>
        </div>
      )}

      {/* Accounts */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <h2 style={{ fontSize: '1.1rem', fontWeight: 700 }}>💳 Accounts</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-secondary btn-sm" onClick={() => fetchAccounts(cid)} disabled={loadingAccounts}>
            {loadingAccounts ? <span className="spinner-sm spinner-dark" /> : '↻ Refresh'}
          </button>
          <button className="btn btn-primary btn-sm" onClick={() => navigate('/accounts/new')}>
            + New Account
          </button>
        </div>
      </div>

      {loadingAccounts ? (
        <div className="loading-block"><span className="spinner" /> Loading accounts…</div>
      ) : accounts.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon">💳</div>
            <p>No accounts found for this client.</p>
            <button className="btn btn-primary mt-4" onClick={() => navigate('/accounts/new')}>
              Open First Account
            </button>
          </div>
        </div>
      ) : (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Account Number</th>
                <th>Type</th>
                <th>Balance</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map(acc => (
                <tr key={acc.id}>
                  <td className="td-mono">{acc.accountNumber}</td>
                  <td><span className={`badge badge-${acc.accountType?.toLowerCase()}`}>{acc.accountType}</span></td>
                  <td className="td-amount">
                    ${parseFloat(acc.balance || 0).toLocaleString('en-CA', { minimumFractionDigits: 2 })}
                  </td>
                  <td><StatusBadge status={acc.status} /></td>
                  <td>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => { handleSelectAccount(acc); navigate('/accounts/summary') }}
                      >
                        📊 Summary
                      </button>
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => { handleSelectAccount(acc); navigate('/transactions') }}
                      >
                        💸 Transact
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Quick Actions */}
      <div style={{ marginTop: 28, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <button className="btn btn-secondary" onClick={() => navigate('/accounts/new')}>💳 Open New Account</button>
        <button className="btn btn-secondary" onClick={() => navigate('/accounts/summary')}>📊 Account Summary</button>
        <button className="btn btn-secondary" onClick={() => navigate('/transactions')}>💸 Transactions</button>
      </div>
    </div>
  )
}
