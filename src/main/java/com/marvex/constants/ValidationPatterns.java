package com.marvex.constants;

/**
 * UK bank detail validation, shared between {@code CompleteRegistrationRequest}
 * and {@code UpdateAccountRequest} — both collect the same sort code / account
 * number fields and must enforce identical rules.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {}

    public static final String SORT_CODE_REGEX = "\\d{6}";
    public static final String SORT_CODE_MESSAGE = "Sort code must be exactly 6 digits";

    public static final String ACCOUNT_NUMBER_REGEX = "\\d{8}";
    public static final String ACCOUNT_NUMBER_MESSAGE = "Account number must be exactly 8 digits";
}
