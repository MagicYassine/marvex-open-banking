import { api } from './client'

export const accountApi = {
  get: () => api.get('/account'),
  update: (data) => api.put('/account', data),
  changePassword: (data) => api.put('/account/password', data),
  delete: () => api.delete('/account'),
  export: async () => {
    const blob = await api.getBlob('/account/export')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `marvex-export-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
  },
}

export const authApi = {
  register: (email) =>
    api.post('/auth/register', { email }, true),
  login: (email, password) =>
    api.post('/auth/login', { email, password }, true),
  logout: () =>
    api.post('/auth/logout', null, false),
}

export const paymentsApi = {
  create: (amount, currency, note) => api.post('/payments', { amount, currency, note }),
  list: (page = 0, size = 20) => api.get(`/payments?page=${page}&size=${size}`),
  get: (id) => api.get(`/payments/${id}`),
  regenerate: (id) => api.post(`/payments/${id}/regenerate`, {}),
  initiate: (id, token, institutionId) => api.post(`/payments/${id}/initiate`, { token, institutionId }, true),
  institutions: () => api.get('/institutions'),
  submit: (id, token, consent) => api.post(`/payments/${id}/submit`, { token, consent }, true),
  confirm: (id, token) => api.post(`/payments/${id}/confirm`, { token }, true),
  sendInvoice: (id, recipientEmail) => api.post(`/payments/${id}/invoice`, { recipientEmail }),
  analytics: () => api.get('/payments/analytics'),
}
