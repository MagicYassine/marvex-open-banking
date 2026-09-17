import { useState, useEffect, useMemo, useCallback } from 'react'
import { setToken, clearToken } from '../api/tokenStore'
import { authApi } from '../api'
import { AuthContext } from './AuthContext'

export function AuthProvider({ children }) {
  const [loggedIn, setLoggedIn] = useState(false)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    // Attempt silent refresh on load — restores session if refresh cookie is still valid
    fetch('/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (data?.accessToken) { setToken(data.accessToken); setLoggedIn(true) }
      })
      .catch(() => {})
      .finally(() => setReady(true))
  }, [])

  const login = useCallback((accessToken) => {
    setToken(accessToken)
    setLoggedIn(true)
  }, [])

  const logout = useCallback(async () => {
    try { await authApi.logout() } catch (_) {}
    clearToken()
    setLoggedIn(false)
  }, [])

  const value = useMemo(() => ({ loggedIn, login, logout }), [loggedIn, login, logout])

  if (!ready) return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: 32, height: 32, border: '3px solid #E5E7EB', borderTopColor: '#1E3A8A', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
    </div>
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}
