// Mirrors com.marvex.enums.PaymentStatus on the backend — the values a
// payment's `status` field can take across the dashboard, pay flow and
// success/failure pages.
export const PAYMENT_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  PAID: 'PAID',
  FAILED: 'FAILED',
  EXPIRED: 'EXPIRED',
}
