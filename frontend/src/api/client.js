import { getToken, setToken, clearToken } from './tokenStore'
import { BEARER_PREFIX, LOGIN_PATH } from '../constants/api'

const BASE = ''

async function request(method, path, body, skipAuth = false) {
  const headers = { 'Content-Type': 'application/json' }
  if (!skipAuth && getToken()) headers['Authorization'] = `${BEARER_PREFIX}${getToken()}`

  const res = await fetch(BASE + path, {
    method,
    headers,
    credentials: 'include', // send httpOnly cookie
    body: body ? JSON.stringify(body) : undefined,
  })

  // Silent token refresh on 401
  if (res.status === 401 && !skipAuth) {
    const refreshed = await tryRefresh()
    if (refreshed) {
      headers['Authorization'] = `${BEARER_PREFIX}${getToken()}`
      const retry = await fetch(BASE + path, {
        method, headers, credentials: 'include',
        body: body ? JSON.stringify(body) : undefined,
      })
      if (retry.status === 401) {
        // Refresh reported success but the session still isn't valid — don't
        // leave the caller with a raw 401, force a clean re-login instead.
        clearToken()
        window.location.href = LOGIN_PATH
        return
      }
      if (!retry.ok) throw await retry.json()
      return retry.status === 204 ? null : retry.json()
    } else {
      clearToken()
      window.location.href = LOGIN_PATH
      return
    }
  }

  if (!res.ok) throw await res.json()
  return res.status === 204 ? null : res.json()
}

// Concurrent requests can all race into a 401 at once (e.g. several calls
// firing on page load with an expired access token). Without coordination
// each one would kick off its own POST /auth/refresh — a thundering herd
// that, with single-use/rotating refresh tokens, can cause all-but-one of
// them to fail and log the user out. Share a single in-flight refresh.
let refreshPromise = null

function tryRefresh() {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

async function doRefresh() {
  try {
    const res = await fetch('/auth/refresh', { method: 'POST', credentials: 'include' })
    if (!res.ok) return false
    const data = await res.json()
    setToken(data.accessToken)
    return true
  } catch {
    return false
  }
}

async function requestBlob(path) {
  const headers = {}
  if (getToken()) headers['Authorization'] = `${BEARER_PREFIX}${getToken()}`
  const res = await fetch(BASE + path, { headers, credentials: 'include' })

  if (res.status === 401) {
    const refreshed = await tryRefresh()
    if (!refreshed) {
      clearToken()
      window.location.href = LOGIN_PATH
      return
    }
    const retryHeaders = { Authorization: `${BEARER_PREFIX}${getToken()}` }
    const retry = await fetch(BASE + path, { headers: retryHeaders, credentials: 'include' })
    if (!retry.ok) throw new Error('Export failed')
    return retry.blob()
  }

  if (!res.ok) throw new Error('Export failed')
  return res.blob()
}

export const api = {
  post:    (path, body, skipAuth) => request('POST', path, body, skipAuth),
  get:     (path) => request('GET', path),
  put:     (path, body) => request('PUT', path, body),
  delete:  (path) => request('DELETE', path),
  getBlob: (path) => requestBlob(path),
}
