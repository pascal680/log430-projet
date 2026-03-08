import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppProvider } from './context/AppContext'
import { ToastProvider } from './context/ToastContext'
import Navbar from './components/Navbar'
import RegisterPage from './pages/RegisterPage'
import VerifyOtpPage from './pages/VerifyOtpPage'
import LoginPage from './pages/LoginPage'
import MfaPage from './pages/MfaPage'
import DashboardPage from './pages/DashboardPage'
import CreateAccountPage from './pages/CreateAccountPage'
import AccountSummaryPage from './pages/AccountSummaryPage'
import TransactionsPage from './pages/TransactionsPage'

export default function App() {
  return (
    <BrowserRouter>
      <AppProvider>
        <ToastProvider>
          <div className="app-layout">
            <Navbar />
            <main className="main-content">
              <Routes>
                <Route path="/" element={<Navigate to="/register" replace />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/verify" element={<VerifyOtpPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/mfa" element={<MfaPage />} />
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/accounts/new" element={<CreateAccountPage />} />
                <Route path="/accounts/summary" element={<AccountSummaryPage />} />
                <Route path="/transactions" element={<TransactionsPage />} />
                <Route path="*" element={<Navigate to="/register" replace />} />
              </Routes>
            </main>
          </div>
        </ToastProvider>
      </AppProvider>
    </BrowserRouter>
  )
}
