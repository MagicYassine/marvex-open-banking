import { useTheme } from './hooks/useTheme'

// Static light theme — used as fallback / for non-hook contexts
export const theme = {
  primary: '#1E3A8A',
  accent: '#3B82F6',
  white: '#FFFFFF',
  dark: '#374151',
  bg: '#F0F4FF',
}

export function useAppTheme() {
  const { tokens } = useTheme()
  return tokens
}

export function useShared() {
  const t = useAppTheme()
  return {
    page: {
      minHeight: '100vh',
      background: t.bg,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: 'system-ui, sans-serif',
    },
    card: {
      background: t.cardBg,
      borderRadius: 12,
      padding: 32,
      width: '100%',
      maxWidth: 420,
      boxShadow: '0 4px 24px rgba(0,0,0,0.15)',
    },
    logo: {
      fontSize: 26,
      fontWeight: 800,
      color: t.primary,
      letterSpacing: '-0.5px',
      marginBottom: 4,
      textAlign: 'center',
    },
    tagline: {
      fontSize: 13,
      color: t.accent,
      textAlign: 'center',
      marginBottom: 28,
      fontWeight: 500,
    },
    input: {
      padding: '11px 14px',
      fontSize: 14,
      borderRadius: 6,
      border: `1.5px solid ${t.inputBorder}`,
      width: '100%',
      boxSizing: 'border-box',
      outline: 'none',
      background: t.inputBg,
      color: t.dark,
    },
    btn: {
      padding: '12px 0',
      width: '100%',
      background: t.primary,
      color: '#FFFFFF',
      border: 'none',
      borderRadius: 6,
      cursor: 'pointer',
      fontSize: 15,
      fontWeight: 600,
      letterSpacing: '0.2px',
    },
    btnAccent: {
      padding: '12px 0',
      width: '100%',
      background: t.accent,
      color: '#FFFFFF',
      border: 'none',
      borderRadius: 6,
      cursor: 'pointer',
      fontSize: 15,
      fontWeight: 600,
    },
    error: { color: t.errorColor, fontSize: 13, margin: '4px 0 0' },
    link: { color: t.accent, textDecoration: 'none', fontWeight: 500 },
  }
}

// Static shared — kept for pages not yet migrated (will use light theme)
export const shared = {
  page: {
    minHeight: '100vh',
    background: theme.bg,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily: 'system-ui, sans-serif',
  },
  card: {
    background: theme.white,
    borderRadius: 12,
    padding: 32,
    width: '100%',
    maxWidth: 420,
    boxShadow: '0 4px 24px rgba(30,58,138,0.10)',
  },
  logo: {
    fontSize: 26,
    fontWeight: 800,
    color: theme.primary,
    letterSpacing: '-0.5px',
    marginBottom: 4,
    textAlign: 'center',
  },
  tagline: {
    fontSize: 13,
    color: theme.accent,
    textAlign: 'center',
    marginBottom: 28,
    fontWeight: 500,
  },
  input: {
    padding: '11px 14px',
    fontSize: 14,
    borderRadius: 6,
    border: `1.5px solid #D1D5DB`,
    width: '100%',
    boxSizing: 'border-box',
    outline: 'none',
  },
  btn: {
    padding: '12px 0',
    width: '100%',
    background: theme.primary,
    color: theme.white,
    border: 'none',
    borderRadius: 6,
    cursor: 'pointer',
    fontSize: 15,
    fontWeight: 600,
    letterSpacing: '0.2px',
  },
  btnAccent: {
    padding: '12px 0',
    width: '100%',
    background: theme.accent,
    color: theme.white,
    border: 'none',
    borderRadius: 6,
    cursor: 'pointer',
    fontSize: 15,
    fontWeight: 600,
  },
  error: { color: '#DC2626', fontSize: 13, margin: '4px 0 0' },
  link: { color: theme.accent, textDecoration: 'none', fontWeight: 500 },
}

export function Logo() {
  const t = useAppTheme()
  return (
    <>
      <img src="/logo/marvex-logo-primary-light.svg" alt="Marvex" style={{ height: 48, display: 'block', margin: '0 auto 4px' }} />
      <div style={{ fontSize: 13, color: t.accent, textAlign: 'center', marginBottom: 28, fontWeight: 500 }}>
        Instant Open Banking Payments
      </div>
    </>
  )
}
