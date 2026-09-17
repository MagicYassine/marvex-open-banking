import { useEffect, useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { paymentsApi } from '../api'
import { Logo } from '../theme'
import { PAYMENT_STATUS } from '../constants/paymentStatus'
import { PAYMENT_TOKEN_STORAGE_KEY_PREFIX } from '../constants/payment'

// The bank may still be settling the payment when we first check — PROCESSING
// isn't a failure, it means "ask again shortly". Poll until it resolves to a
// terminal PAID/FAILED, backing off between attempts (Yapily's docs ask
// integrators not to hammer GET /payments/{id}/details — it's rate-limited
// and webhooks are the recommended way to get terminal-status updates).
// Backoff: 2s, 2.8s, 3.9s, 5.5s, 7.7s, 10.8s, then capped at 15s — ~12
// attempts covers roughly 2.5 minutes before we give up and let the user
// manually recheck.
const POLL_BASE_MS = 2000
const POLL_MULTIPLIER = 1.4
const POLL_MAX_INTERVAL_MS = 15000
const MAX_ATTEMPTS = 12

function nextDelay(attempt) {
  return Math.min(POLL_BASE_MS * (POLL_MULTIPLIER ** attempt), POLL_MAX_INTERVAL_MS)
}

export default function PaySuccessPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || sessionStorage.getItem(`${PAYMENT_TOKEN_STORAGE_KEY_PREFIX}${id}`)
  const [status, setStatus] = useState(null)
  const [error, setError] = useState(null)
  const [timedOut, setTimedOut] = useState(false)
  const [stillChecking, setStillChecking] = useState(false)
  const attemptsRef = useRef(0)

  useEffect(() => {
    if (!token) { setError('Missing payment token'); return }
    let cancelled = false
    let timer

    const poll = () => {
      const attempt = attemptsRef.current
      attemptsRef.current += 1
      paymentsApi.confirm(id, token)
        .then(data => {
          if (cancelled) return
          if (data.status === PAYMENT_STATUS.PROCESSING) {
            if (attemptsRef.current >= MAX_ATTEMPTS) { setTimedOut(true); return }
            if (attempt >= 2) setStillChecking(true) // reassure after a few rounds, not on every tick
            timer = setTimeout(poll, nextDelay(attempt))
            return
          }
          setStatus(data.status)
        })
        .catch(() => { if (!cancelled) setError('Could not confirm payment. Please contact support.') })
    }
    poll()

    return () => { cancelled = true; clearTimeout(timer) }
  }, [id, token])

  return (
    <div className="app-page">
      <div className="app-card app-card--center">
        <Logo />
        {error ? (
          <>
            <span className="app-emoji app-emoji--md">⚠️</span>
            <h2>Error</h2>
            <p style={{ color: '#6B7280' }}>{error}</p>
          </>
        ) : timedOut ? (
          <>
            <span className="app-emoji app-emoji--md">⏳</span>
            <h2 style={{ color: '#374151' }}>Still Processing</h2>
            <p style={{ color: '#6B7280', marginBottom: 24 }}>
              Your bank is still processing this payment. This can occasionally take a few minutes — click below to check again.
            </p>
            <button onClick={() => window.location.reload()} className="app-btn">
              Check Again
            </button>
          </>
        ) : !status ? (
          <p style={{ color: '#374151' }}>
            {stillChecking ? 'Still confirming with your bank — this can take a moment…' : 'Confirming payment…'}
          </p>
        ) : status === PAYMENT_STATUS.PAID ? (
          <>
            <span className="app-emoji">✅</span>
            <h2 style={{ color: '#16A34A' }}>Payment Successful</h2>
            <p style={{ color: '#6B7280' }}>Your payment has been confirmed. Thank you!</p>
          </>
        ) : (
          <>
            <span className="app-emoji">❌</span>
            <h2 style={{ color: '#DC2626' }}>Payment Failed</h2>
            <p style={{ color: '#6B7280', marginBottom: 24 }}>Your payment could not be completed.</p>
            {token && (
              <button onClick={() => window.location.href = `/pay/${id}?token=${token}`} className="app-btn">
                Try Again
              </button>
            )}
          </>
        )}
      </div>
    </div>
  )
}
