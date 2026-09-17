import { useEffect } from 'react'
import { useParams, useSearchParams, useLocation } from 'react-router-dom'
import { paymentsApi } from '../api'
import { Logo } from '../theme'
import { PAYMENT_TOKEN_STORAGE_KEY_PREFIX } from '../constants/payment'

export default function PayFailedPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const token = searchParams.get('token') || sessionStorage.getItem(`${PAYMENT_TOKEN_STORAGE_KEY_PREFIX}${id}`)
  const { reason, cancelled } = location.state || {}

  useEffect(() => {
    if (token) paymentsApi.confirm(id, token).catch(() => {})
  }, [id, token])

  return (
    <div className="app-page">
      <div className="app-card app-card--center">
        <Logo />
        <span className="app-emoji">❌</span>
        <h2 style={{ color: '#DC2626' }}>{cancelled ? 'Payment Cancelled' : 'Payment Failed'}</h2>
        <p style={{ color: '#6B7280', marginBottom: 24 }}>
          {cancelled ? 'You cancelled the bank authorization before it completed.' : (reason || 'Your payment could not be completed.')}
        </p>
        {token && (
          <button onClick={() => window.location.href = `/pay/${id}?token=${token}`} className="app-btn">
            Try Again
          </button>
        )}
      </div>
    </div>
  )
}
