import React from 'react'
import { Link } from 'react-router-dom'
import { useApp } from '../context/AppContext'

const STEPS = [
  { to: '/register',       label: '1. Register',    key: 'clientId' },
  { to: '/verify',         label: '2. Verify OTP',  key: 'verified' },
  { to: '/login',          label: '3. Login',        key: 'challengeToken' },
  { to: '/mfa',            label: '4. MFA',          key: 'isAuthenticated' },
  { to: '/accounts/new',   label: '5. Open Account', key: 'currentAccountNumber' },
  { to: '/transactions',   label: '6. Transact',     key: 'lastTransactionId' },
]

export default function FlowStepper({ current }) {
  const { clientId, challengeToken, isAuthenticated, currentAccountNumber, lastTransactionId } = useApp()

  const isDone = (key) => {
    if (key === 'clientId') return !!clientId
    if (key === 'verified') return !!clientId  // simplified
    if (key === 'challengeToken') return !!challengeToken || isAuthenticated
    if (key === 'isAuthenticated') return isAuthenticated
    if (key === 'currentAccountNumber') return !!currentAccountNumber
    if (key === 'lastTransactionId') return !!lastTransactionId
    return false
  }

  return (
    <div className="flow-stepper mb-4">
      {STEPS.map((step, i) => {
        const done = isDone(step.key)
        const active = step.to === current
        return (
          <div key={step.to} className="step-item">
            {i > 0 && <div className="step-connector" />}
            <Link
              to={step.to}
              className={`step-bubble ${active ? 'active' : done ? 'done' : ''}`}
            >
              {done && !active && '✓ '}
              {step.label}
            </Link>
          </div>
        )
      })}
    </div>
  )
}
