import { useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../api'
import { useShared, useAppTheme, Logo } from '../theme'
import PageShell from '../components/PageShell'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

function validateEmail(email) {
  if (!email) return 'Email is required'
  if (!EMAIL_RE.test(email)) return 'Enter a valid email address'
  return null
}

export default function RegisterPage() {
  const [form, setForm] = useState({ email: '' })
  const [fieldError, setFieldError] = useState(null)
  const [error, setError] = useState(null)
  const [done, setDone] = useState(false)
  const shared = useShared()
  const t = useAppTheme()

  const handleSubmit = async (e) => {
    e.preventDefault()
    const err = validateEmail(form.email)
    if (err) { setFieldError(err); return }
    setFieldError(null)
    setError(null)
    try {
      await authApi.register(form.email)
      setDone(true)
    } catch (err) {
      setError(err?.error || 'Registration failed')
    }
  }

  if (done) return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={{ ...shared.card, textAlign: 'center' }}>
          <Logo />
          <div style={{ fontSize: 56, marginBottom: 12 }}>✅</div>
          <h2 style={{ color: t.primary, margin: '0 0 8px' }}>Nearly there!</h2>
          <p style={{ color: t.muted }}>Check your email at <strong>{form.email}</strong> and click the link to complete your registration.</p>
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
            <input
              placeholder="Email"
              type="email"
              aria-label="Email"
              autoComplete="email"
              value={form.email}
              onChange={e => { setForm({ ...form, email: e.target.value }); setFieldError(null) }}
              required
              style={{ ...shared.input, borderColor: fieldError ? '#DC2626' : undefined }}
            />
            {fieldError && <p style={shared.error}>{fieldError}</p>}
            {error && (
              <p style={shared.error}>
                {error.includes('already registered')
                  ? <>Email already registered. <Link to="/forgot-password" style={shared.link}>Forgot password?</Link></>
                  : error}
              </p>
            )}
            <button type="submit" style={shared.btn}>Create Account</button>
          </form>
          <p style={{ textAlign: 'center', marginTop: 16, fontSize: 14, color: t.muted }}>
            Already have an account? <Link to="/login" style={shared.link}>Login</Link>
          </p>
          <p style={{ textAlign: 'center', marginTop: 12, fontSize: 12, color: t.muted }}>
            <Link to="/terms" style={shared.link}>Terms</Link>
            {' · '}
            <Link to="/privacy" style={shared.link}>Privacy</Link>
          </p>
        </div>
      </div>
    </PageShell>
  )
}
