package com.marvex.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenUtilTest {

    @Test
    void sha256_sameInput_producesSameHash() {
        String first = TokenUtil.sha256("raw-token-value");
        String second = TokenUtil.sha256("raw-token-value");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void sha256_differentInputs_produceDifferentHashes() {
        String first = TokenUtil.sha256("raw-token-value-1");
        String second = TokenUtil.sha256("raw-token-value-2");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void sha256_producesBase64EncodedThirtyTwoByteDigest() {
        String hash = TokenUtil.sha256("raw-token-value");

        byte[] decoded = Base64.getDecoder().decode(hash);
        assertThat(decoded).hasSize(32);
    }

    @Test
    void sha256_nullInput_throwsIllegalArgumentExceptionWithClearMessage() {
        assertThatThrownBy(() -> TokenUtil.sha256(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("input must not be null");
    }
}
