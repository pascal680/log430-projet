import React, { createContext, useContext, useState, useCallback } from 'react'

const STORAGE_KEY = 'canbankx_session'

const defaultState = {
  clientId: '',
  clientEmail: '',
  clientFirstName: '',
  clientLastName: '',
  challengeToken: '',
  isAuthenticated: false,
  accounts: [],
  currentAccountNumber: '',
  currentAccountId: '',
  lastTransactionId: '',
  lastIdempotencyKey: '',
}

const AppContext = createContext(null)

export function AppProvider({ children }) {
  const [state, setState] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      return saved ? { ...defaultState, ...JSON.parse(saved) } : defaultState
    } catch {
      return defaultState
    }
  })

  const update = useCallback((updates) => {
    setState(prev => {
      const next = { ...prev, ...updates }
      try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)) } catch {}
      return next
    })
  }, [])

  const reset = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setState(defaultState)
  }, [])

  return (
    <AppContext.Provider value={{ ...state, update, reset }}>
      {children}
    </AppContext.Provider>
  )
}

export const useApp = () => useContext(AppContext)
