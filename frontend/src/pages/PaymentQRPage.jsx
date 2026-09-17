import { useLocation, useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { theme } from '../theme'
import { useState } from 'react'
import { CURRENCY_GBP } from '../constants/payment'

export default function PaymentQRPage() {
  const { state } = useLocation()
  const navigate = useNavigate()
  const [copied, setCopied] = useState(false)

  if (!state?.paymentUrl) { navigate('/dashboard'); return null }

  const fullUrl = `${window.location.origin}${state.paymentUrl}${state.institutionId ? `&institutionId=${state.institutionId}` : ''}`

  const handleCopy = () => {
    navigator.clipboard.writeText(fullUrl).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="qr-page" style={{ background: theme.bg }}>
      <div className="qr-card" style={{ background: '#fff' }}>
        <img src="/logo/marvex-logo-primary-light.svg" alt="Marvex" className="app-logo" style={{ height: 40, marginBottom: 8 }} />
        <p style={{ color: theme.dark, fontSize: 14, marginBottom: 20 }}>Share this QR code with your customer</p>

        {state.amount && (
          <div className="qr-amount-badge" style={{ marginBottom: state.note ? 8 : 20 }}>
            <span className="qr-amount-label">Amount: </span>
            <span className="qr-amount-value">{state.currency || CURRENCY_GBP} {(state.amount / 100).toFixed(2)}</span>
          </div>
        )}
        {state.note && <p className="qr-note" style={{ marginBottom: 20 }}>{state.note}</p>}

        <div className="qr-code-wrapper" style={{ border: `2px solid ${theme.accent}` }}>
          <QRCodeSVG value={fullUrl} size={220} fgColor={theme.primary} />
        </div>

        <div className="qr-actions">
          <button onClick={handleCopy}
            className={`qr-btn qr-btn--outline${copied ? ' qr-btn--copied' : ''}`}
            style={!copied ? { border: `1.5px solid ${theme.accent}`, color: theme.accent } : {}}>
            {copied ? '✓ Copied!' : '🔗 Copy Link'}
          </button>
        </div>

        <button onClick={() => navigate('/dashboard')} className="app-btn" style={{ background: theme.primary }}>
          ← Back to Dashboard
        </button>
      </div>
    </div>
  )
}
