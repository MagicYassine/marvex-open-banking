import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './hooks/useAuth'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import PaymentQRPage from './pages/PaymentQRPage'
import DashboardPage from './pages/DashboardPage'
import PayPage from './pages/PayPage'
import PaySuccessPage from './pages/PaySuccessPage'
import PayCallbackPage from './pages/PayCallbackPage'
import PayFailedPage from './pages/PayFailedPage'
import MockBankPage from './pages/MockBankPage'
import Homepage from './pages/Homepage'
import PrivacyPage from './pages/PrivacyPage'
import TermsPage from './pages/TermsPage'
import MyAccountPage from './pages/MyAccountPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import CompleteRegistrationPage from './pages/CompleteRegistrationPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/payment-qr" element={
            <ProtectedRoute><PaymentQRPage /></ProtectedRoute>
          } />
          <Route path="/dashboard" element={
            <ProtectedRoute><DashboardPage /></ProtectedRoute>
          } />
          <Route path="/complete-registration" element={<CompleteRegistrationPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/my-account" element={<ProtectedRoute><MyAccountPage /></ProtectedRoute>} />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route path="/pay/:id" element={<PayPage />} />
          <Route path="/pay/:id/callback" element={<PayCallbackPage />} />
          <Route path="/pay/:id/success" element={<PaySuccessPage />} />
          <Route path="/pay/:id/failed" element={<PayFailedPage />} />
          <Route path="/mock-bank/:id" element={<MockBankPage />} />
          <Route path="/" element={<Homepage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
