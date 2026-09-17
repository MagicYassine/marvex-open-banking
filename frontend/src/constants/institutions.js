// Institutions offered when a merchant sets/changes their payout bank
// (registration + account settings). Kept as a static list here since these
// forms select the merchant's own payout account, separate from
// paymentsApi.institutions() which lists banks a *payer* can pay from.
const LIVE_INSTITUTIONS = [
  { id: 'monzo_ob', name: 'Monzo' },
  { id: 'lloyds_ob', name: 'Lloyds Bank' },
  { id: 'barclays_ob', name: 'Barclays' },
  { id: 'hsbc_ob', name: 'HSBC' },
  { id: 'natwest_ob', name: 'NatWest' },
  { id: 'santander_ob', name: 'Santander' },
  { id: 'starling_ob', name: 'Starling Bank' },
]

// The sandbox institution never moves real money — a merchant whose payout
// account is set to it would show customer payments as PAID while actually
// receiving nothing. Only offer it outside production builds.
export const INSTITUTIONS = import.meta.env.DEV
  ? [...LIVE_INSTITUTIONS, { id: 'mock-sandbox', name: 'Sandbox (Testing)' }]
  : LIVE_INSTITUTIONS
