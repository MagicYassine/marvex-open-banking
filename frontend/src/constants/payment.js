// The only currency the backend/payment provider supports (see
// CreatePaymentRequest on the backend) — used wherever the dashboard/QR page
// creates or displays a payment.
export const CURRENCY_GBP = 'GBP'

// sessionStorage key prefix a payment's raw access token is stashed under
// across the redirect to the customer's bank and back (PayPage sets it,
// PaySuccessPage/PayFailedPage/PayCallbackPage/MockBankPage read it).
export const PAYMENT_TOKEN_STORAGE_KEY_PREFIX = 'payment_token_'
