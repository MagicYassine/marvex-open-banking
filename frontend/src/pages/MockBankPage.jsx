import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { paymentsApi } from '../api'
import { Logo } from '../theme'
import { PAYMENT_TOKEN_STORAGE_KEY_PREFIX } from '../constants/payment'

export default function MockBankPage() {
  const { id } = useParams()
  const [payment, setPayment] = useState(null)
  const token = sessionStorage.getItem(`${PAYMENT_TOKEN_STORAGE_KEY_PREFIX}${id}`)

  useEffect(() => {
    paymentsApi.get(id).then(setPayment).catch(() => {})
  }, [id])

  const goTo = (outcome) => {
    const path = outcome === 'pay' ? `/pay/${id}/success` : `/pay/${id}/failed`
    window.location.href = path + (token ? `?token=${token}` : '')
  }

  return (
    <div className="app-page">
      <div className="app-card app-card--center">
        <Logo />
        <span className="app-emoji">🏦</span>
        <h2>Mock Bank Payment</h2>
        {payment && (
          <p style={{ color: '#374151' }}>Amount: <strong>£{(payment.amount / 100).toFixed(2)}</strong></p>
        )}
        <p style={{ color: '#9CA3AF', fontSize: 13 }}>Payment ref: {id}</p>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 16 }}>
          <button onClick={() => goTo('pay')} className="app-btn" style={{ background: '#16A34A' }}>
            Confirm Payment
          </button>
          <button onClick={() => goTo('cancel')} className="app-btn" style={{ background: '#EF4444' }}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
