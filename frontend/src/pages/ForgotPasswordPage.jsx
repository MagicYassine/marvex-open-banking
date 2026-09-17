import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useShared, useAppTheme, Logo } from '../theme'
import PageShell from '../components/PageShell'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const shared = useShared()
  const t = useAppTheme()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await api.post('/auth/forgot-password', { email }, true)
      setDone(true)
    } catch (err) {
      setError(err?.error || 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  if (done) return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={{ ...shared.card, textAlign: 'center' }}>
          <Logo />
          <div style={{ fontSize: 48, marginBottom: 12 }}>📧</div>
          <p style={{ color: t.muted }}>If that email is registered, you'll receive a password reset link shortly.</p>
          <Link to="/login" style={{ ...shared.link, display: 'block', marginTop: 16 }}>Back to Login</Link>
        </div>
      </div>
    </PageShell>
  )

  return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={shared.card}>
          <Logo />
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <input placeholder="Email" type="email" aria-label="Email" autoComplete="email" value={email}
              onChange={e => setEmail(e.target.value)} required style={shared.input} />
            {error && <p style={shared.error}>{error}</p>}
            <button type="submit" disabled={loading} style={shared.btn}>
              {loading ? 'Sending…' : 'Send Reset Link'}
            </button>
          </form>
          <p style={{ textAlign: 'center', marginTop: 16, fontSize: 14, color: t.muted }}>
            <Link to="/login" style={shared.link}>Back to Login</Link>
          </p>
        </div>
      </div>
    </PageShell>
  )
}
