import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { accountApi } from '../api'
import { useAuth } from '../hooks/AuthContext'
import { useAppTheme, useShared } from '../theme'
import PasswordInput from '../components/PasswordInput'
import DarkModeToggle from '../components/DarkModeToggle'
import { INSTITUTIONS } from '../constants/institutions'
import { SORT_CODE_PATTERN, SORT_CODE_TITLE, ACCOUNT_NUMBER_PATTERN, ACCOUNT_NUMBER_TITLE } from '../constants/validation'

export default function MyAccountPage() {
  const { logout } = useAuth()
  const t = useAppTheme()
  const shared = useShared()
  const navigate = useNavigate()
  const [form, setForm] = useState(null)
  const [email, setEmail] = useState('')
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    accountApi.get().then(data => {
      setEmail(data.email)
      setForm({ businessName: data.businessName, institutionId: data.institutionId,
        payeeName: data.payeeName, sortCode: data.sortCode, accountNumber: data.accountNumber })
    }).catch(() => {
      setError('Failed to load account details')
    })
  }, [])

  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '' })
  const [pwError, setPwError] = useState(null)
  const [pwSuccess, setPwSuccess] = useState(false)
  const [pwLoading, setPwLoading] = useState(false)
  const redirectTimeoutRef = useRef(null)

  useEffect(() => {
    // Clear the pending post-password-change redirect if the user navigates
    // away before it fires, so we don't call logout()/navigate() on an
    // unmounted page.
    return () => { if (redirectTimeoutRef.current) clearTimeout(redirectTimeoutRef.current) }
  }, [])

  const handlePasswordChange = async (e) => {
    e.preventDefault()
    setPwError(null)
    setPwSuccess(false)
    setPwLoading(true)
    try {
      await accountApi.changePassword(pwForm)
      setPwSuccess(true)
      setPwForm({ currentPassword: '', newPassword: '' })
      redirectTimeoutRef.current = setTimeout(() => { logout(); navigate('/login') }, 1500)
    } catch (err) {
      setPwError(err?.error || 'Failed to change password')
    } finally {
      setPwLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!window.confirm('Are you sure you want to delete your account? This cannot be undone.')) return
    try {
      await accountApi.delete()
      logout()
      navigate('/register')
    } catch (err) {
      setError(err?.error || 'Failed to delete account')
    }
  }

  const set = (k) => (e) => { setSuccess(false); setForm(f => ({ ...f, [k]: e.target.value })) }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    setSuccess(false)
    setLoading(true)
    try {
      await accountApi.update(form)
      setSuccess(true)
    } catch (err) {
      setError(err?.error || 'Failed to save changes')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: t.bg, fontFamily: 'system-ui, sans-serif' }}>
      <div style={{ background: t.primary, padding: '0 32px', height: 56, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <img src="/logo/marvex-logo-reversed-navy.svg" alt="Marvex" style={{ height: 32 }} />
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <DarkModeToggle />
          <button onClick={() => navigate('/dashboard')} style={{ padding: '6px 16px', background: 'transparent', color: '#fff', border: `1.5px solid ${t.accent}`, borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
            ← Dashboard
          </button>
        </div>
      </div>

      <div style={{ maxWidth: 520, margin: '36px auto', padding: '0 24px' }}>
        <div style={{ background: t.cardBg, borderRadius: 12, padding: 32, boxShadow: '0 2px 12px rgba(30,58,138,0.08)' }}>
          <h2 style={{ margin: '0 0 4px', color: t.primary, fontSize: 18 }}>My Account</h2>
          <p style={{ color: '#6B7280', fontSize: 13, marginBottom: 24 }}>View and update your account details.</p>

          {!form ? (
            <p style={{ color: '#6B7280' }}>{error || 'Loading…'}</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Email</label>
                  <input value={email} disabled autoComplete="off" style={{ ...shared.input, background: '#F3F4F6', color: '#9CA3AF' }} />
                </div>
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Business Name</label>
                  <input value={form.businessName} onChange={set('businessName')} required style={shared.input} />
                </div>
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Institution</label>
                  <select value={form.institutionId} onChange={set('institutionId')} required style={{ ...shared.input, background: '#fff' }}>
                    <option value="">Select your bank…</option>
                    {INSTITUTIONS.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
                  </select>
                </div>
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Payee Name</label>
                  <input value={form.payeeName} onChange={set('payeeName')} required style={shared.input} />
                </div>
                <div style={{ display: 'flex', gap: 12 }}>
                  <div style={{ flex: 1 }}>
                    <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Sort Code</label>
                    <input value={form.sortCode} onChange={set('sortCode')} required pattern={SORT_CODE_PATTERN} title={SORT_CODE_TITLE} maxLength={6} style={shared.input} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label style={{ fontSize: 12, fontWeight: 600, color: t.dark, display: 'block', marginBottom: 5 }}>Account Number</label>
                    <input value={form.accountNumber} onChange={set('accountNumber')} required pattern={ACCOUNT_NUMBER_PATTERN} title={ACCOUNT_NUMBER_TITLE} maxLength={8} style={shared.input} />
                  </div>
                </div>
                {error && <p style={shared.error}>{error}</p>}
                {success && <p style={{ color: '#16A34A', fontSize: 13 }}>✓ Changes saved successfully</p>}
                <button type="submit" disabled={loading} style={shared.btn}>
                  {loading ? 'Saving…' : 'Save Changes'}
                </button>
              </form>

              <hr style={{ margin: '24px 0', border: 'none', borderTop: '1px solid #E5E7EB' }} />
              <h3 style={{ margin: '0 0 16px', color: t.primary, fontSize: 15 }}>Change Password</h3>
              <form onSubmit={handlePasswordChange} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <PasswordInput placeholder="Current password" aria-label="Current password" autoComplete="current-password" value={pwForm.currentPassword}
                  onChange={e => setPwForm({ ...pwForm, currentPassword: e.target.value })} required style={shared.input} />
                <PasswordInput placeholder="New password (min 8 characters)" aria-label="New password" autoComplete="new-password" value={pwForm.newPassword}
                  onChange={e => setPwForm({ ...pwForm, newPassword: e.target.value })} required minLength={8} style={shared.input} />
                {pwError && <p style={shared.error}>{pwError}</p>}
                {pwSuccess && <p style={{ color: '#16A34A', fontSize: 13 }}>✓ Password changed — redirecting to login…</p>}
                <button type="submit" disabled={pwLoading} style={shared.btn}>
                  {pwLoading ? 'Saving…' : 'Change Password'}
                </button>
              </form>

              <hr style={{ margin: '24px 0', border: 'none', borderTop: '1px solid #E5E7EB' }} />
              <button type="button" onClick={handleDelete} style={{ ...shared.btn, background: '#DC2626' }}>
                Delete Account
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
