package com.marvex.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setup() {
        jwtUtil = new JwtUtil(SECRET, 900_000L);
    }

    @Test
    void generateAccessToken_producesParseableThreePartJwt() {
        String token = jwtUtil.generateAccessToken(UUID.randomUUID());

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void extractMerchantId_roundTripsSubjectClaim() {
        UUID merchantId = UUID.randomUUID();
        String token = jwtUtil.generateAccessToken(merchantId);

        assertThat(jwtUtil.extractMerchantId(token)).isEqualTo(merchantId);
    }

    @Test
    void isValid_expiredToken_returnsFalse() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil(SECRET, 1L);
        String token = shortLived.generateAccessToken(UUID.randomUUID());

        Thread.sleep(20);

        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void isValid_tamperedSignature_returnsFalse() {
        String token = jwtUtil.generateAccessToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_signedWithDifferentSecret_returnsFalse() {
        JwtUtil otherKey = new JwtUtil("a-completely-different-secret-key-also-256-bits-long-enough", 900_000L);
        String token = otherKey.generateAccessToken(UUID.randomUUID());

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void isValid_malformedToken_returnsFalse() {
        assertThat(jwtUtil.isValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void isValid_emptyToken_returnsFalse() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }
}
