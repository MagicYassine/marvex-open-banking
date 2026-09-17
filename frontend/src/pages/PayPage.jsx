import { useEffect, useState } from 'react'
import { useParams, useSearchParams, useNavigate } from 'react-router-dom'
import { paymentsApi } from '../api'
import { Logo } from '../theme'
import { PAYMENT_STATUS } from '../constants/paymentStatus'
import { PAYMENT_TOKEN_STORAGE_KEY_PREFIX } from '../constants/payment'

export default function PayPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const institutionId = searchParams.get('institutionId')
  const navigate = useNavigate()
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!token) { setError('Invalid payment link'); return }
    paymentsApi.get(id)
      .then(p => {
        if (p.status === PAYMENT_STATUS.PAID) { navigate(`/pay/${id}/success?token=${token}`); return }
        if (p.status === PAYMENT_STATUS.EXPIRED) { setError('This payment link has expired'); return }
        if (p.status === PAYMENT_STATUS.FAILED) { navigate(`/pay/${id}/failed`); return }
        return paymentsApi.initiate(id, token, institutionId)
          .then(data => {
            if (data.status === PAYMENT_STATUS.PAID) navigate(`/pay/${id}/success?token=${token}`)
            else if (data.redirectUrl) { sessionStorage.setItem(`${PAYMENT_TOKEN_STORAGE_KEY_PREFIX}${id}`, token); window.location.href = data.redirectUrl }
          })
      })
      .catch(err => setError(err?.error || 'Payment initiation failed'))
  }, [id, token, institutionId])

  if (error) return (
    <div className="app-page">
      <div className="app-card app-card--center">
        <Logo />
        <h2>Payment unavailable</h2>
        <p style={{ color: '#6B7280' }}>{error}</p>
      </div>
    </div>
  )

  return (
    <div className="app-page">
      <div className="app-card app-card--center">
        <Logo />
        <p style={{ color: '#6B7280', marginTop: 16 }}>Connecting to your bank…</p>
        <div className="pay-spinner" />
      </div>
    </div>
  )
}
