import DarkModeToggle from './DarkModeToggle'
import { useAppTheme } from '../theme'

export default function PageShell({ children }) {
  const t = useAppTheme()
  return (
    <div style={{ minHeight: '100vh', background: t.bg, fontFamily: 'system-ui, sans-serif', position: 'relative' }}>
      <div style={{ position: 'fixed', top: 12, right: 16, zIndex: 1000 }}>
        <DarkModeToggle />
      </div>
      {children}
    </div>
  )
}
