// In-memory access token store — never persisted to localStorage
let accessToken = null

export const getToken = () => accessToken
export const setToken = (t) => { accessToken = t }
export const clearToken = () => { accessToken = null }
