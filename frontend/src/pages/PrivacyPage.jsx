import { useNavigate } from 'react-router-dom'
import { useAppTheme } from '../theme'
import DarkModeToggle from '../components/DarkModeToggle'

export default function PrivacyPage() {
  const navigate = useNavigate()
  const t = useAppTheme()
  return (
    <div style={{ minHeight: '100vh', background: t.bg, fontFamily: 'system-ui, sans-serif', padding: '40px 24px' }}>
      <div style={{ position: 'fixed', top: 12, right: 16, zIndex: 1000 }}><DarkModeToggle /></div>
      <div style={{ maxWidth: 720, margin: '0 auto', background: t.cardBg, borderRadius: 12, padding: 40, boxShadow: '0 4px 24px rgba(30,58,138,0.08)' }}>
        <img src="/logo/marvex-logo-primary-light.svg" alt="Marvex" style={{ height: 40, marginBottom: 4 }} />
        <h1 style={{ color: t.primary, fontSize: 22, margin: '16px 0 8px' }}>Privacy Policy</h1>
        <p style={{ color: t.muted, fontSize: 13, marginBottom: 32 }}>Last updated: March 2026</p>

        {[
          ['1. Who We Are', 'Marvex ("we", "us") operates the Marvex payment platform. We are the data controller for personal data collected through this service.'],
          ['2. Data We Collect', 'We collect: email address, business name, bank account details (sort code, account number, payee name), payment transaction records, and IP addresses for security purposes.'],
          ['3. How We Use Your Data', 'We use your data to: provide and operate the payment service, send transactional emails (verification, payment notifications), prevent fraud and abuse, and comply with legal obligations.'],
          ['4. Legal Basis', 'We process your data on the basis of: contract performance (to provide the service you signed up for), and legitimate interests (security, fraud prevention).'],
          ['5. Data Retention', 'We retain your account data for as long as your account is active. Payment records are retained for 7 years to comply with financial regulations. You may request deletion of your account at any time via My Account → Delete Account.'],
          ['6. Your Rights', 'Under GDPR you have the right to: access your data, correct inaccurate data, delete your data (right to erasure), and object to processing. To exercise these rights, contact us at privacy@marvex.app'],
          ['7. Data Sharing', 'We share your data with: our Open Banking provider (Yapily) to process payments, and our email provider (Gmail) to send transactional emails. We do not sell your data to third parties.'],
          ['8. Security', 'We use industry-standard security measures including bcrypt password hashing, JWT authentication, HTTPS encryption, and HMAC-verified webhooks.'],
          ['9. Cookies', 'We use a single httpOnly cookie for session management (refresh token). We do not use tracking or analytics cookies.'],
          ['10. Contact', 'For privacy enquiries: privacy@marvex.app'],
        ].map(([title, body]) => (
          <div key={title} style={{ marginBottom: 24 }}>
            <h2 style={{ fontSize: 15, color: t.dark, margin: '0 0 6px' }}>{title}</h2>
            <p style={{ fontSize: 14, color: t.muted, lineHeight: 1.6, margin: 0 }}>{body}</p>
          </div>
        ))}

        <button onClick={() => navigate(-1)} style={{ marginTop: 16, padding: '10px 24px', background: t.primary, color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 }}>
          ← Back
        </button>
      </div>
    </div>
  )
}
