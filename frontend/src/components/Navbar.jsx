import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import { useToast } from '../context/ToastContext'

const NAV_ITEMS = [
  { to: '/register',  icon: '📝', label: 'Register' },
  { to: '/verify',    icon: '✉️',  label: 'Verify OTP' },
  { to: '/login',     icon: '🔑', label: 'Login' },
  { to: '/mfa',       icon: '🔐', label: 'MFA' },
  { to: '/dashboard', icon: '🏠', label: 'Dashboard' },
  { to: '/accounts/new',    icon: '💳', label: 'New Account' },
  { to: '/accounts/summary',icon: '📊', label: 'Summary' },
  { to: '/transactions',    icon: '💸', label: 'Transactions' },
]

export default function Navbar() {
  const { clientId, clientFirstName, isAuthenticated, currentAccountNumber, reset } = useApp()
  const { addToast } = useToast()
  const navigate = useNavigate()

  const handleReset = () => {
    reset()
    addToast('Session cleared.', 'info')
    navigate('/register')
  }

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <a href="/" className="navbar-brand">
          <span className="logo-icon">🏦</span>
          <div>
            <div>CanBankX</div>
            <div className="logo-sub">Developer Portal</div>
          </div>
        </a>

        <div className="navbar-divider" />

        <div className="navbar-nav">
          {NAV_ITEMS.map(({ to, icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
            >
              <span className="nav-icon">{icon}</span>
              {label}
            </NavLink>
          ))}
        </div>

        <div className="navbar-session">
          {isAuthenticated ? (
            <span className="session-pill active">
              <span className="session-dot" />
              {clientFirstName || 'Authenticated'}
            </span>
          ) : clientId ? (
            <span className="session-pill" title={`Client: ${clientId}`}>
              🪪 {clientFirstName || clientId.slice(0, 8) + '…'}
            </span>
          ) : null}

          {currentAccountNumber && (
            <span className="session-pill" title={`Account: ${currentAccountNumber}`}>
              💳 {currentAccountNumber}
            </span>
          )}

          {(clientId || isAuthenticated) && (
            <button className="btn-reset" onClick={handleReset} title="Clear session">
              ✕ Reset
            </button>
          )}
        </div>
      </div>
    </nav>
  )
}
