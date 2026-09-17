package com.marvex.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Shared token hashing utility.
 * All sensitive tokens (verification, password reset, payment access, refresh) are stored
 * as Base64(SHA-256(rawToken)). Raw tokens are never persisted.
 */
public final class TokenUtil {

    private TokenUtil() {}

    /**
     * Returns a Base64-encoded SHA-256 hash of the input.
     * Used for all token storage — verification, password reset, payment access, refresh tokens.
     */
    public static String sha256(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
