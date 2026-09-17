import { useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useShared, useAppTheme, Logo } from '../theme'
import PageShell from '../components/PageShell'
import { PasswordStrength, validatePassword } from '../components/PasswordStrength'
import PasswordInput from '../components/PasswordInput'
import { INSTITUTIONS } from '../constants/institutions'
import { SORT_CODE_PATTERN, SORT_CODE_TITLE, ACCOUNT_NUMBER_PATTERN, ACCOUNT_NUMBER_TITLE } from '../constants/validation'

export default function CompleteRegistrationPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')

  const [form, setForm] = useState({ password: '', businessName: '', institutionId: '', payeeName: '', sortCode: '', accountNumber: '' })
  const [error, setError] = useState(null)
  const [pwError, setPwError] = useState(null)
  const [loading, setLoading] = useState(false)
  const shared = useShared()
  const t = useAppTheme()

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    const pwErr = validatePassword(form.password)
    if (pwErr) { setPwError(pwErr); return }
    setPwError(null)
    setError(null)
    setLoading(true)
    try {
      await api.post('/auth/complete-registration', { token, ...form }, true)
      navigate('/login')
    } catch (err) {
      setError(err?.error || 'Failed to complete registration')
    } finally {
      setLoading(false)
    }
  }

  if (!token) return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={{ ...shared.card, textAlign: 'center' }}>
          <Logo />
          <p style={{ color: t.errorColor }}>Invalid or missing verification link.</p>
        </div>
      </div>
    </PageShell>
  )

  return (
    <PageShell>
      <div style={{ ...shared.page, background: 'transparent' }}>
        <div style={shared.card}>
          <Logo />
          <h2 style={{ color: t.primary, margin: '0 0 4px', fontSize: 18 }}>Complete Registration</h2>
          <p style={{ color: t.muted, fontSize: 13, marginBottom: 20 }}>Set up your payment details to start accepting payments.</p>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Password</label>
              <PasswordInput placeholder="Choose a strong password" autoComplete="new-password" value={form.password}
                onChange={e => { set('password')(e); setPwError(null) }} required style={{ ...shared.input, borderColor: pwError ? '#DC2626' : undefined }} />
              <PasswordStrength value={form.password} />
              {pwError && <p style={{ ...shared.error, marginTop: 4 }}>{pwError}</p>}
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Business Name</label>
              <input placeholder="Your business name" value={form.businessName}
                onChange={set('businessName')} required style={shared.input} />
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Institution</label>
              <select value={form.institutionId} onChange={set('institutionId')} required
                style={{ ...shared.input, background: t.inputBg }}>
                <option value="">Select your bank…</option>
                {INSTITUTIONS.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
              </select>
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Payee Name</label>
              <input placeholder="Full name on bank account" value={form.payeeName}
                onChange={set('payeeName')} required style={shared.input} />
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <div style={{ flex: 1 }}>
                <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Sort Code</label>
                <input placeholder="040004" value={form.sortCode}
                  onChange={set('sortCode')} required pattern={SORT_CODE_PATTERN} title={SORT_CODE_TITLE} maxLength={6} style={shared.input} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Account Number</label>
                <input placeholder="07475213" value={form.accountNumber}
                  onChange={set('accountNumber')} required pattern={ACCOUNT_NUMBER_PATTERN} title={ACCOUNT_NUMBER_TITLE} maxLength={8} style={shared.input} />
              </div>
            </div>
            {error && (
              <p style={shared.error}>
                {error.includes('register again') ? (
                  <>Link already used or invalid. Please <a href="/register" style={shared.link}>register again</a> to get a new link.</>
                ) : error}
              </p>
            )}
            <button type="submit" disabled={loading} style={shared.btn}>
              {loading ? 'Saving…' : 'Complete Registration'}
            </button>
          </form>
        </div>
      </div>
    </PageShell>
  )
}
