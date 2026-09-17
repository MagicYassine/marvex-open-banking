import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api'
import { useShared, useAppTheme, Logo } from '../theme'
import PageShell from '../components/PageShell'
import PasswordInput from '../components/PasswordInput'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const { login } = useAuth()
  const navigate = useNavigate()
  const shared = useShared()
  const t = useAppTheme()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    try {
      const data = await authApi.login(email, password)
      login(data.accessToken)
      navigate('/dashboard')
    } catch (err) {
      setError(err?.error || 'Login failed')
    }
  }

  return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={shared.card}>
          <Logo />
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <input placeholder="Email" type="email" aria-label="Email" autoComplete="email" value={email}
              onChange={e => setEmail(e.target.value)} required style={shared.input} />
            <PasswordInput placeholder="Password" aria-label="Password" autoComplete="current-password" value={password}
              onChange={e => setPassword(e.target.value)} required style={shared.input} />
            {error && <p style={shared.error}>{error}</p>}
            <button type="submit" style={shared.btn}>Login</button>
          </form>
          <p style={{ textAlign: 'center', marginTop: 16, fontSize: 14, color: t.muted }}>
            No account? <Link to="/register" style={shared.link}>Register</Link>
            {' · '}
            <Link to="/forgot-password" style={shared.link}>Forgot password?</Link>
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
