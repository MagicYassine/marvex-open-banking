import { useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useShared, useAppTheme, Logo } from '../theme'
import PageShell from '../components/PageShell'
import { PasswordStrength, validatePassword } from '../components/PasswordStrength'
import PasswordInput from '../components/PasswordInput'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [pwError, setPwError] = useState(null)
  const [confirmError, setConfirmError] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const shared = useShared()
  const t = useAppTheme()

  if (!token) return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={{ ...shared.card, textAlign: 'center' }}>
          <Logo />
          <p style={{ color: t.errorColor }}>Invalid or missing reset link.</p>
        </div>
      </div>
    </PageShell>
  )

  const handleSubmit = async (e) => {
    e.preventDefault()
    const pwErr = validatePassword(password)
    if (pwErr) { setPwError(pwErr); return }
    if (password !== confirm) { setConfirmError('Passwords do not match'); return }
    setPwError(null)
    setConfirmError(null)
    setError(null)
    setLoading(true)
    try {
      await api.post('/auth/reset-password', { token, password }, true)
      navigate('/login')
    } catch (err) {
      setError(err?.error || 'Failed to reset password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={shared.card}>
          <Logo />
          <h2 style={{ color: t.primary, margin: '0 0 16px', fontSize: 18 }}>Reset Password</h2>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>New password</label>
              <PasswordInput placeholder="Choose a strong password" autoComplete="new-password" value={password}
                onChange={e => { setPassword(e.target.value); setPwError(null) }} required
                style={{ ...shared.input, borderColor: pwError ? '#DC2626' : undefined }} />
              <PasswordStrength value={password} />
              {pwError && <p style={{ ...shared.error, marginTop: 4 }}>{pwError}</p>}
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Confirm new password</label>
              <PasswordInput placeholder="Repeat your password" autoComplete="new-password" value={confirm}
                onChange={e => { setConfirm(e.target.value); setConfirmError(null) }} required
                style={{ ...shared.input, borderColor: confirmError ? '#DC2626' : undefined }} />
              {confirmError && <p style={{ ...shared.error, marginTop: 4 }}>{confirmError}</p>}
            </div>
            {error && <p style={shared.error}>{error}</p>}
            <button type="submit" disabled={loading} style={shared.btn}>
              {loading ? 'Saving…' : 'Reset Password'}
            </button>
          </form>
        </div>
      </div>
    </PageShell>
  )
}
