// Authorization header scheme for the in-memory access token — used by both
// the request helpers in api/client.js and DashboardPage's direct STOMP
// connection headers.
export const BEARER_PREFIX = 'Bearer '

// Where an expired/invalid session sends the user — must match the
// <Route path="/login"> in App.jsx.
export const LOGIN_PATH = '/login'
