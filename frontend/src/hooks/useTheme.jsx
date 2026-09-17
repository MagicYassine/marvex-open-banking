const tokens = {
  primary: '#1E3A8A',
  accent: '#3B82F6',
  white: '#FFFFFF',
  dark: '#374151',
  bg: '#F0F4FF',
  cardBg: '#FFFFFF',
  border: '#D1D5DB',
  muted: '#6B7280',
  surface: '#F0F4FF',
  navBg: '#FFFFFF',
  inputBg: '#FFFFFF',
  inputBorder: '#D1D5DB',
  errorColor: '#DC2626',
}

export function useTheme() {
  return { dark: false, toggleDark: () => {}, tokens }
}

export function ThemeProvider({ children }) {
  return children
}
