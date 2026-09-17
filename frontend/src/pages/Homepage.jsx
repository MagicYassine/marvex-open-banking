import { Link } from 'react-router-dom'

const NAV = '#0B2447'
const BLUE = '#378ADD'
const BG = '#f4f7fc'

export default function Homepage() {
  return (
    <div style={{ fontFamily: "'Sora', 'Inter', Arial, sans-serif", margin: 0, padding: 0, background: BG, color: '#1a1a2e' }}>

      {/* Header */}
      <header style={{ background: NAV, color: '#fff', padding: '20px 40px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
        <img src="/logo/marvex-logo-reversed-navy.svg" alt="Marvex" style={{ height: 36 }} />
        <nav style={{ display: 'flex', gap: 28, flexWrap: 'wrap' }}>
          {[['Home', '/'], ['About', '#about'], ['Features', '#features'], ['Contact', 'mailto:hello@marvex.co.uk']].map(([label, href]) => (
            <a key={label} href={href} style={{ color: '#fff', textDecoration: 'none', fontSize: '1em', opacity: 0.85 }}>{label}</a>
          ))}
        </nav>
      </header>

      {/* Hero */}
      <section style={{ background: `linear-gradient(135deg, ${NAV} 0%, #163366 100%)`, color: '#fff', padding: '100px 20px', textAlign: 'center' }}>
        <h2 style={{ fontSize: '2.8em', margin: '0 0 16px', fontWeight: 700, lineHeight: 1.2 }}>
          Instant Open Banking Payments.<br />
          <span style={{ color: BLUE }}>No cards. No friction.</span>
        </h2>
        <p style={{ fontSize: '1.15em', margin: '0 auto 36px', maxWidth: 560, opacity: 0.88 }}>
          Marvex lets merchants accept QR and pay-by-link payments directly from customers' bank accounts — fast, secure, and FCA-regulated.
        </p>
        <div style={{ display: 'flex', gap: 16, justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link to="/register" style={{ background: BLUE, color: '#fff', padding: '14px 32px', borderRadius: 6, textDecoration: 'none', fontSize: '1.05em', fontWeight: 600 }}>
            Get Started Free
          </Link>
          <a href="#features" style={{ background: 'transparent', color: '#fff', padding: '14px 32px', borderRadius: 6, textDecoration: 'none', fontSize: '1.05em', fontWeight: 600, border: '2px solid rgba(255,255,255,0.6)' }}>
            Learn More
          </a>
        </div>
      </section>

      {/* About */}
      <section id="about" style={{ padding: '60px 20px', textAlign: 'center' }}>
        <div style={{ maxWidth: 720, margin: '0 auto' }}>
          <h2 style={{ fontSize: '1.9em', marginBottom: 20, color: NAV }}>About Marvex</h2>
          <p style={{ fontSize: '1.05em', lineHeight: 1.7, color: '#444' }}>
            Marvex is a UK Open Banking payment platform built for modern merchants. Whether you run a market stall, a restaurant, or an e-commerce store, Marvex gives you a single dashboard to generate QR codes and payment links — with funds settling directly to your bank account, no intermediaries, no card fees.
          </p>
        </div>
      </section>

      {/* Features */}
      <section id="features" style={{ padding: '50px 20px', textAlign: 'center', background: '#fff' }}>
        <h2 style={{ fontSize: '1.9em', marginBottom: 36, color: NAV }}>Core Features</h2>
        <div style={{ display: 'flex', justifyContent: 'center', gap: 32, flexWrap: 'wrap' }}>
          {[
            { title: 'QR · Link', body: 'Two payment channels in one platform — print a QR or share a payment link.' },
            { title: 'Bank-Grade Security', body: 'SHA-256 hashed tokens, HMAC webhook verification, and JWT-secured sessions keep every transaction safe.' },
            { title: 'Real-Time Updates', body: 'Instant WebSocket notifications and email alerts the moment a payment lands.' },
            { title: 'Full Transparency', body: 'Live dashboard, analytics, and one-click CSV export for complete visibility over your revenue.' },
          ].map(({ title, body }) => (
            <div key={title} style={{ background: BG, padding: '28px 24px', borderRadius: 10, boxShadow: '0 2px 12px rgba(11,36,71,0.08)', width: 220 }}>
              <h3 style={{ marginBottom: 12, fontSize: '1.15em', color: NAV }}>{title}</h3>
              <p style={{ fontSize: '0.93em', color: '#555', lineHeight: 1.6 }}>{body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section id="get-started" style={{ padding: '70px 20px', textAlign: 'center' }}>
        <h2 style={{ fontSize: '1.9em', marginBottom: 14, color: NAV }}>Ready to Accept Open Banking Payments?</h2>
        <p style={{ fontSize: '1.05em', color: '#555', marginBottom: 32 }}>Join Marvex today — set up in minutes, no hardware required.</p>
        <Link to="/register" style={{ background: BLUE, color: '#fff', padding: '14px 36px', borderRadius: 6, textDecoration: 'none', fontSize: '1.05em', fontWeight: 600 }}>
          Create Your Account
        </Link>
      </section>

      {/* Footer */}
      <footer style={{ background: NAV, color: '#fff', padding: '24px 20px', textAlign: 'center' }}>
        <p style={{ margin: '0 0 10px', opacity: 0.8, fontSize: '0.9em' }}>&copy; 2026 Marvex Ltd. All rights reserved.</p>
        <div style={{ display: 'flex', justifyContent: 'center', gap: 20, flexWrap: 'wrap' }}>
          {[['Terms of Service', '/terms'], ['Privacy Policy', '/privacy'], ['Contact', 'mailto:hello@marvex.co.uk']].map(([label, href]) => (
            <a key={label} href={href} style={{ color: '#fff', textDecoration: 'none', fontSize: '0.9em', opacity: 0.8 }}>{label}</a>
          ))}
        </div>
      </footer>

    </div>
  )
}
