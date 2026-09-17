package com.marvex.util;

/**
 * Utility for masking PII in application logs.
 * Use these methods in log statements — never in audit log entries, which must remain unmasked.
 */
public final class LogMask {

    private LogMask() {}

    /**
     * Masks an email address, keeping the first 2 characters and the domain.
     * e.g. "test@test.com" → "te***@test.com"
     */
    public static String maskEmail(String email) {
        if (email == null) return "<null>";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at); // includes '@'
        String visible = local.length() > 2 ? local.substring(0, 2) : local.substring(0, 1);
        return visible + "***" + domain;
    }

    /**
     * Masks a token, keeping only the first 6 characters.
     * e.g. "a3f9c1b2-..." → "a3f9c1***"
     * Enough to correlate log lines without exposing the secret.
     */
    public static String maskToken(String token) {
        if (token == null) return "<null>";
        if (token.length() <= 6) return "***";
        return token.substring(0, 6) + "***";
    }
}
