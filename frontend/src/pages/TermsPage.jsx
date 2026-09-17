import { useNavigate } from 'react-router-dom'
import { useAppTheme } from '../theme'
import DarkModeToggle from '../components/DarkModeToggle'

export default function TermsPage() {
  const navigate = useNavigate()
  const t = useAppTheme()
  return (
    <div style={{ minHeight: '100vh', background: t.bg, fontFamily: 'system-ui, sans-serif', padding: '40px 24px' }}>
      <div style={{ position: 'fixed', top: 12, right: 16, zIndex: 1000 }}><DarkModeToggle /></div>
      <div style={{ maxWidth: 720, margin: '0 auto', background: t.cardBg, borderRadius: 12, padding: 40, boxShadow: '0 4px 24px rgba(30,58,138,0.08)' }}>
        <img src="/logo/marvex-logo-primary-light.svg" alt="Marvex" style={{ height: 40, marginBottom: 4 }} />
        <h1 style={{ color: t.primary, fontSize: 22, margin: '16px 0 8px' }}>Terms & Conditions</h1>
        <p style={{ color: t.muted, fontSize: 13, marginBottom: 32 }}>Last updated: March 2026</p>

        {[
          ['1. Acceptance of Terms', 'By registering for and using Marvex, you agree to be bound by these Terms & Conditions. If you do not agree, please do not use the service.'],
          ['2. Service Description', 'Marvex provides a QR code-based payment platform enabling merchants to accept payments via Open Banking. We act as a technology intermediary and do not hold, process or transmit funds directly.'],
          ['3. Merchant Obligations', 'You must provide accurate business and bank account information. You are responsible for ensuring your use of the service complies with all applicable laws and regulations, including those relating to your business activities.'],
          ['4. Payment Processing', 'Payments are processed via Open Banking through our licensed payment provider. We do not guarantee the availability of any specific bank or payment method. Payment outcomes are subject to your customer\'s bank approval.'],
          ['5. Data & Privacy', 'We collect and process personal data as described in our Privacy Policy. By using the service you consent to such processing.'],
          ['6. Limitation of Liability', 'To the maximum extent permitted by law, Marvex shall not be liable for any indirect, incidental or consequential damages arising from your use of the service.'],
          ['7. Termination', 'We reserve the right to suspend or terminate your account at any time if we believe you are in breach of these terms or applicable law.'],
          ['8. Changes to Terms', 'We may update these terms from time to time. Continued use of the service after changes constitutes acceptance of the new terms.'],
          ['9. Governing Law', 'These terms are governed by the laws of England and Wales.'],
          ['10. Contact', 'For any questions regarding these terms, please contact us at support@marvex.app'],
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
