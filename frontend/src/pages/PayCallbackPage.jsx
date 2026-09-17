import { useEffect } from 'react'
import { useParams, useSearchParams, useNavigate } from 'react-router-dom'
import { paymentsApi } from '../api'
import { shared, Logo } from '../theme'
import { PAYMENT_TOKEN_STORAGE_KEY_PREFIX } from '../constants/payment'

export default function PayCallbackPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  useEffect(() => {
    const consent = searchParams.get('consent')
    const token = sessionStorage.getItem(`${PAYMENT_TOKEN_STORAGE_KEY_PREFIX}${id}`)
    const error = searchParams.get('error')

    if (error) {
      let reason = searchParams.get('error-description')
      try { reason = reason ? atob(reason) : null } catch { /* not base64, use as-is */ }
      navigate(`/pay/${id}/failed${token ? `?token=${token}` : ''}`, {
        state: { reason, cancelled: error === 'access_denied' },
      })
      return
    }

    if (!consent || !token) { navigate(`/pay/${id}/failed`); return }

    paymentsApi.submit(id, token, consent)
      .then(() => navigate(`/pay/${id}/success?token=${token}`))
      .catch(() => navigate(`/pay/${id}/failed`))
  }, [])

  return (
    <div style={shared.page}>
      <div style={{ ...shared.card, textAlign: 'center' }}>
        <Logo />
        <p style={{ color: '#6B7280' }}>Processing your payment…</p>
      </div>
    </div>
  )
}
