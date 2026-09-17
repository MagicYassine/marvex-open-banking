import { useEffect, useState, useRef } from 'react'
import { paymentsApi, accountApi } from '../api'
import { getToken } from '../api/tokenStore'
import { useAuth } from '../hooks/AuthContext'
import { useNavigate } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import DarkModeToggle from '../components/DarkModeToggle'
import { useAppTheme, useShared } from '../theme'
import { BEARER_PREFIX } from '../constants/api'
import { CURRENCY_GBP } from '../constants/payment'
import { PAYMENT_STATUS } from '../constants/paymentStatus'

const statusColor = { PAID: '#16A34A', FAILED: '#DC2626', PENDING: '#D97706', PROCESSING: '#3B82F6', EXPIRED: '#9CA3AF', REFUNDED: '#6B7280' }

export default function DashboardPage() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const t = useAppTheme()
  const shared = useShared()
  const [payments, setPayments] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState(null)
  const stompRef = useRef(null)
  const audioCtxRef = useRef(null)
  const [showBankPicker, setShowBankPicker] = useState(false)
  const [institutions, setInstitutions] = useState([])
  const [selectedBank, setSelectedBank] = useState(null)
  const [pendingAmount, setPendingAmount] = useState(null)
  const [pendingNote, setPendingNote] = useState(null)
  const [invoicePaymentId, setInvoicePaymentId] = useState(null)
  const [invoiceEmail, setInvoiceEmail] = useState('')
  const [invoiceStatus, setInvoiceStatus] = useState(null)
  const [analytics, setAnalytics] = useState(null)

  const loadPayments = (p = 0) => {
    paymentsApi.list(p).then(data => {
      setPayments(data.content)
      setTotalPages(data.totalPages)
      setPage(data.page)
    }).catch(() => {
      setError('Failed to load payments')
    })
  }

  useEffect(() => {
    loadPayments(0)
    paymentsApi.analytics().then(setAnalytics).catch(() => {})
  }, [])

  useEffect(() => {
    // Unlock AudioContext on first user interaction (browser autoplay policy)
    const unlock = () => {
      if (!audioCtxRef.current) {
        audioCtxRef.current = new (window.AudioContext || window.webkitAudioContext)()
      } else if (audioCtxRef.current.state === 'suspended') {
        audioCtxRef.current.resume()
      }
    }
    window.addEventListener('click', unlock, { once: true })
    return () => window.removeEventListener('click', unlock)
  }, [])

  useEffect(() => {
    const beep = () => {
      try {
        const ctx = audioCtxRef.current || new (window.AudioContext || window.webkitAudioContext)()
        audioCtxRef.current = ctx
        if (ctx.state === 'suspended') ctx.resume()
        // Two-tone "cha-ching": high note then slightly lower
        [[880, 0, 0.15], [1100, 0.18, 0.35]].forEach(([freq, start, end]) => {
          const osc = ctx.createOscillator()
          const gain = ctx.createGain()
          osc.connect(gain)
          gain.connect(ctx.destination)
          osc.frequency.value = freq
          gain.gain.setValueAtTime(0, ctx.currentTime + start)
          gain.gain.linearRampToValueAtTime(0.4, ctx.currentTime + start + 0.02)
          gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + end)
          osc.start(ctx.currentTime + start)
          osc.stop(ctx.currentTime + end)
        })
      } catch (_) { /* audio is a nice-to-have, never block on it */ }
    }

    let cancelled = false

    accountApi.get().then(data => {
      // The effect may have been cleaned up (component unmounted) while this
      // request was in flight — don't open a WS connection nothing will ever
      // close.
      if (cancelled) return
      const client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5000,
        // Re-read the access token on every (re)connect attempt, not just
        // the first — a silent token refresh elsewhere in the app would
        // otherwise leave this socket authenticating with a stale token
        // forever after its first reconnect.
        beforeConnect: () => { client.connectHeaders = { Authorization: `${BEARER_PREFIX}${getToken()}` } },
        onConnect: () => {
          client.subscribe(`/topic/payments/${data.id}`, (msg) => {
            try {
              const parsed = JSON.parse(msg.body)
              if (parsed.event === 'payment_paid') beep()
            } catch (_) {}
            loadPayments(0)
            paymentsApi.analytics().then(setAnalytics).catch(() => {})
          })
        },
      })
      stompRef.current = client
      client.activate()
    })

    return () => {
      cancelled = true
      stompRef.current?.deactivate()
    }
  }, [])

  const handleCreate = async (e) => {
    e.preventDefault()
    setError(null)
    setPendingAmount(parseFloat(amount))
    setPendingNote(note.trim() || null)
    if (institutions.length === 0) {
      try {
        const data = await paymentsApi.institutions()
        const list = (data?.data ?? []).filter(i => i.features?.includes('INITIATE_DOMESTIC_SINGLE_PAYMENT'))
        setInstitutions(list)
      } catch {
        setError('Failed to load banks')
        return
      }
    }
    setSelectedBank(null)
    setShowBankPicker(true)
  }

  const handleConfirmBank = async () => {
    if (!selectedBank) return
    setError(null)
    try {
      const data = await paymentsApi.create(pendingAmount, CURRENCY_GBP, pendingNote)
      navigate('/payment-qr', { state: { paymentUrl: data.paymentUrl, institutionId: selectedBank, amount: data.amount, currency: CURRENCY_GBP, note: pendingNote } })
      setShowBankPicker(false)
      loadPayments(0)
    } catch (err) {
      setError(err?.error || 'Failed to create payment')
      setShowBankPicker(false)
    }
  }

  const handleRegenerate = async (paymentId) => {
    try {
      const data = await paymentsApi.regenerate(paymentId)
      navigate('/payment-qr', { state: { paymentUrl: data.paymentUrl, institutionId: selectedBank, amount: data.amount, currency: data.currency } })
    } catch (err) {
      setError(err?.error || 'Failed to regenerate payment')
    }
  }

  const handleSendInvoice = async (e) => {
    e.preventDefault()
    setInvoiceStatus(null)
    try {
      await paymentsApi.sendInvoice(invoicePaymentId, invoiceEmail)
      setInvoiceStatus('sent')
    } catch (err) {
      setInvoiceStatus(err?.error || 'Failed to send invoice')
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: t.bg, fontFamily: 'system-ui, sans-serif' }}>
      {/* Navbar */}
      <div style={{ background: t.primary, padding: '0 32px', height: 56, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <img src="/logo/marvex-logo-reversed-navy.svg" alt="Marvex" style={{ height: 32 }} />
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <DarkModeToggle />
          <button onClick={() => navigate('/my-account')} style={{ padding: '6px 16px', background: 'transparent', color: t.white, border: `1.5px solid ${t.accent}`, borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
            My Account
          </button>
          <button onClick={() => accountApi.export().catch(() => {})} style={{ padding: '6px 16px', background: 'transparent', color: t.white, border: `1.5px solid ${t.accent}`, borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
            ⬇ Export Data
          </button>
          <button onClick={logout} style={{ padding: '6px 16px', background: 'transparent', color: t.white, border: `1.5px solid ${t.accent}`, borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
            Logout
          </button>
        </div>
      </div>

      <div style={{ maxWidth: 900, margin: '36px auto', padding: '0 24px' }}>
        {/* Analytics Cards */}
        {analytics && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 28 }}>
            {[
              { label: 'Total Revenue', value: `£${(analytics.totalRevenuePence / 100).toFixed(2)}` },
              { label: 'Paid Today', value: analytics.paidToday },
              { label: 'Paid This Week', value: analytics.paidThisWeek },
              { label: 'Total Paid', value: analytics.totalPaid },
            ].map(({ label, value }) => (
              <div key={label} style={{ background: t.cardBg, borderRadius: 10, padding: '16px 20px', boxShadow: '0 2px 8px rgba(30,58,138,0.07)', textAlign: 'center' }}>
                <div style={{ fontSize: 22, fontWeight: 800, color: t.primary }}>{value}</div>
                <div style={{ fontSize: 12, color: '#6B7280', marginTop: 4, fontWeight: 600 }}>{label}</div>
              </div>
            ))}
          </div>
        )}

        {/* Create Payment */}
        <div style={{ background: t.cardBg, borderRadius: 12, padding: 24, marginBottom: 28, boxShadow: '0 2px 12px rgba(30,58,138,0.08)' }}>
          <h3 style={{ margin: '0 0 16px', color: t.primary, fontSize: 16, fontWeight: 700 }}>New Payment</h3>
          <form onSubmit={handleCreate} style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <div>
              <label style={{ fontSize: 12, color: t.dark, fontWeight: 600, display: 'block', marginBottom: 6 }}>Amount (£)</label>
              <input type="number" placeholder="e.g. 1.50" value={amount}
                onChange={e => setAmount(e.target.value)} min={0.01} step={0.01} required
                style={{ ...shared.input, width: 120 }} />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: 12, color: t.dark, fontWeight: 600, display: 'block', marginBottom: 6 }}>Note (optional)</label>
              <input type="text" placeholder="e.g. Table 4, Order #123" value={note}
                onChange={e => setNote(e.target.value)} maxLength={255}
                style={{ ...shared.input }} />
            </div>
            <button type="submit" style={{ ...shared.btn, width: 'auto', padding: '11px 28px' }}>
              Generate
            </button>
          </form>
          {error && <p style={shared.error}>{error}</p>}
        </div>

        {/* Payments Table */}
        <div style={{ background: t.cardBg, borderRadius: 12, padding: 24, boxShadow: '0 2px 12px rgba(30,58,138,0.08)' }}>
          <h3 style={{ margin: '0 0 16px', color: t.primary, fontSize: 16, fontWeight: 700 }}>Payments</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr style={{ borderBottom: `2px solid ${t.bg}` }}>
                {['ID', 'Amount', 'Note', 'Status', 'Expires', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 12px', color: t.dark, fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {payments.map(p => (
                <tr key={p.paymentId} style={{ borderBottom: `1px solid ${t.bg}` }}>
                  <td style={{ padding: '10px 12px', color: '#6B7280', fontFamily: 'monospace' }}>{p.paymentId?.slice(0, 8)}…</td>
                  <td style={{ padding: '10px 12px', fontWeight: 600, color: t.dark }}>£{(p.amount / 100).toFixed(2)}</td>
                  <td style={{ padding: '10px 12px', color: t.dark, fontSize: 13 }}>{p.note || <span style={{ color: '#9CA3AF' }}>—</span>}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ background: `${statusColor[p.status]}18`, color: statusColor[p.status], padding: '3px 10px', borderRadius: 20, fontSize: 12, fontWeight: 700 }}>
                      {p.status}
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px', color: '#6B7280', fontSize: 12 }}>{p.expiresAt ? new Date(p.expiresAt).toLocaleString() : '-'}</td>
                  <td style={{ padding: '10px 12px' }}>
                    {(p.status === PAYMENT_STATUS.EXPIRED || p.status === PAYMENT_STATUS.PENDING) && (
                      <button onClick={() => handleRegenerate(p.paymentId)}
                        style={{ padding: '4px 12px', fontSize: 12, fontWeight: 600, background: t.accent, color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer' }}>
                        Regenerate QR
                      </button>
                    )}
                    {p.status === PAYMENT_STATUS.PAID && (
                      <div style={{ display: 'flex', gap: 6 }}>
                        <button onClick={() => { setInvoicePaymentId(p.paymentId); setInvoiceEmail(''); setInvoiceStatus(null) }}
                          style={{ padding: '4px 12px', fontSize: 12, fontWeight: 600, background: '#16A34A', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer' }}>
                          Send Invoice
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
              {payments.length === 0 && (
                <tr><td colSpan={5} style={{ padding: 24, textAlign: 'center', color: '#9CA3AF' }}>No payments yet</td></tr>
              )}
            </tbody>
          </table>
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 16, marginTop: 16 }}>
              <button onClick={() => loadPayments(page - 1)} disabled={page === 0}
                style={{ padding: '6px 16px', borderRadius: 6, border: `1px solid ${t.accent}`, background: t.cardBg, color: t.accent, cursor: page === 0 ? 'not-allowed' : 'pointer', opacity: page === 0 ? 0.4 : 1 }}>
                ← Prev
              </button>
              <span style={{ fontSize: 13, color: t.dark }}>Page {page + 1} of {totalPages}</span>
              <button onClick={() => loadPayments(page + 1)} disabled={page >= totalPages - 1}
                style={{ padding: '6px 16px', borderRadius: 6, border: `1px solid ${t.accent}`, background: t.cardBg, color: t.accent, cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer', opacity: page >= totalPages - 1 ? 0.4 : 1 }}>
                Next →
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Bank Picker Modal */}
      {showBankPicker && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: t.cardBg, borderRadius: 12, padding: 28, maxWidth: 520, width: '90%', maxHeight: '80vh', overflowY: 'auto' }}>
            <h3 style={{ margin: '0 0 4px', color: t.primary, fontSize: 16, fontWeight: 700 }}>Select Customer's Bank</h3>
            <p style={{ margin: '0 0 16px', color: '#6B7280', fontSize: 13 }}>Choose the bank your customer will pay from</p>
            {institutions.length === 0 ? (
              <p style={{ textAlign: 'center', color: '#6B7280' }}>Loading banks…</p>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginBottom: 20 }}>
                {institutions.map(inst => (
                  <button key={inst.id} onClick={() => setSelectedBank(inst.id)}
                    style={{ border: selectedBank === inst.id ? `2px solid ${t.primary}` : '1.5px solid #D1D5DB', borderRadius: 8, padding: '10px 6px', background: selectedBank === inst.id ? '#EFF6FF' : t.cardBg, cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                    {inst.media?.find(m => m.type === 'icon')?.source
                      ? <img src={inst.media.find(m => m.type === 'icon').source} alt={inst.name} style={{ width: 32, height: 32, objectFit: 'contain' }} />
                      : <div style={{ width: 32, height: 32, background: '#E5E7EB', borderRadius: 4 }} />}
                    <span style={{ fontSize: 11, color: t.dark, textAlign: 'center', lineHeight: 1.2 }}>{inst.name}</span>
                  </button>
                ))}
              </div>
            )}
            <div style={{ display: 'flex', gap: 10 }}>
              <button onClick={() => setShowBankPicker(false)}
                style={{ flex: 1, padding: '10px', borderRadius: 6, border: `1.5px solid ${t.accent}`, background: 'transparent', color: t.accent, cursor: 'pointer', fontWeight: 600 }}>
                Cancel
              </button>
              <button onClick={handleConfirmBank} disabled={!selectedBank}
                style={{ flex: 1, ...shared.btn, opacity: selectedBank ? 1 : 0.5 }}>
                Generate QR
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Invoice Modal */}
      {invoicePaymentId && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: t.cardBg, borderRadius: 12, padding: 28, maxWidth: 420, width: '90%' }}>
            <h3 style={{ margin: '0 0 4px', color: t.primary, fontSize: 16, fontWeight: 700 }}>Send Invoice</h3>
            <p style={{ margin: '0 0 16px', color: '#6B7280', fontSize: 13 }}>Enter the recipient's email address</p>
            {invoiceStatus === 'sent' ? (
              <>
                <p style={{ color: '#16A34A', fontWeight: 600, textAlign: 'center', margin: '16px 0' }}>✅ Invoice sent successfully</p>
                <button onClick={() => setInvoicePaymentId(null)} style={{ ...shared.btn }}>Close</button>
              </>
            ) : (
              <form onSubmit={handleSendInvoice}>
                <input type="email" placeholder="customer@example.com" value={invoiceEmail}
                  onChange={e => setInvoiceEmail(e.target.value)} required
                  style={{ ...shared.input, marginBottom: 12 }} />
                {invoiceStatus && <p style={shared.error}>{invoiceStatus}</p>}
                <div style={{ display: 'flex', gap: 10 }}>
                  <button type="button" onClick={() => setInvoicePaymentId(null)}
                    style={{ flex: 1, padding: '10px', borderRadius: 6, border: `1.5px solid ${t.accent}`, background: 'transparent', color: t.accent, cursor: 'pointer', fontWeight: 600 }}>
                    Cancel
                  </button>
                  <button type="submit" style={{ flex: 1, ...shared.btn }}>Send</button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
