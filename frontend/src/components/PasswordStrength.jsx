export const PASSWORD_RULES = [
  { test: v => v.length >= 8,          label: 'At least 8 characters' },
  { test: v => /[A-Z]/.test(v),        label: 'One uppercase letter' },
  { test: v => /[a-z]/.test(v),        label: 'One lowercase letter' },
  { test: v => /[0-9]/.test(v),        label: 'One number' },
  { test: v => /[^A-Za-z0-9]/.test(v), label: 'One special character' },
]

export function validatePassword(value) {
  const failed = PASSWORD_RULES.filter(r => !r.test(value))
  if (failed.length > 0) return `Password must include: ${failed.map(r => r.label.toLowerCase()).join(', ')}`
  return null
}

export function PasswordStrength({ value }) {
  if (!value) return null
  const score = PASSWORD_RULES.filter(r => r.test(value)).length
  const color = score <= 2 ? '#DC2626' : score <= 3 ? '#F59E0B' : '#16A34A'
  const label = score <= 2 ? 'Weak' : score <= 3 ? 'Fair' : score === 4 ? 'Good' : 'Strong'
  return (
    <div style={{ marginTop: 6 }}>
      <div style={{ display: 'flex', gap: 4, marginBottom: 4 }}>
        {PASSWORD_RULES.map((_, i) => (
          <div key={i} style={{ flex: 1, height: 3, borderRadius: 2, background: i < score ? color : '#E5E7EB', transition: 'background 0.2s' }} />
        ))}
      </div>
      <div style={{ fontSize: 11, color, fontWeight: 600 }}>{label}</div>
      <ul style={{ margin: '4px 0 0', padding: '0 0 0 16px', fontSize: 11, color: '#6B7280' }}>
        {PASSWORD_RULES.filter(r => !r.test(value)).map(r => <li key={r.label}>{r.label}</li>)}
      </ul>
    </div>
  )
}
