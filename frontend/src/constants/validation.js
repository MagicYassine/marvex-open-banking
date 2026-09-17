// UK bank detail validation — kept identical to the backend's ValidationPatterns
// (CompleteRegistrationRequest / UpdateAccountRequest) since both sides validate
// the same sort code / account number fields.
export const SORT_CODE_PATTERN = '[0-9]{6}'
export const SORT_CODE_TITLE = 'Must be exactly 6 digits'

export const ACCOUNT_NUMBER_PATTERN = '[0-9]{8}'
export const ACCOUNT_NUMBER_TITLE = 'Must be exactly 8 digits'
