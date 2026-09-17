package com.marvex.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskTest {

    @Test
    void maskEmail_null_returnsNullPlaceholder() {
        assertThat(LogMask.maskEmail(null)).isEqualTo("<null>");
    }

    @Test
    void maskEmail_empty_returnsStars() {
        assertThat(LogMask.maskEmail("")).isEqualTo("***");
    }

    @Test
    void maskEmail_noAtSign_returnsStars() {
        assertThat(LogMask.maskEmail("notAnEmail")).isEqualTo("***");
    }

    @Test
    void maskEmail_atSignAtStart_returnsStars() {
        assertThat(LogMask.maskEmail("@example.com")).isEqualTo("***");
    }

    @Test
    void maskEmail_shortLocalPart_showsOnlyFirstCharacter() {
        assertThat(LogMask.maskEmail("ab@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void maskEmail_singleCharacterLocalPart_showsThatCharacter() {
        assertThat(LogMask.maskEmail("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void maskEmail_normalEmail_showsFirstTwoCharactersAndDomain() {
        assertThat(LogMask.maskEmail("testemail@gmail.com")).isEqualTo("te***@gmail.com");
    }

    @Test
    void maskToken_null_returnsNullPlaceholder() {
        assertThat(LogMask.maskToken(null)).isEqualTo("<null>");
    }

    @Test
    void maskToken_empty_returnsStars() {
        assertThat(LogMask.maskToken("")).isEqualTo("***");
    }

    @Test
    void maskToken_exactlySixCharacters_returnsStars() {
        assertThat(LogMask.maskToken("abcdef")).isEqualTo("***");
    }

    @Test
    void maskToken_shortToken_returnsStars() {
        assertThat(LogMask.maskToken("abc")).isEqualTo("***");
    }

    @Test
    void maskToken_normalLengthToken_showsFirstSixCharacters() {
        assertThat(LogMask.maskToken("a3f9c1b2-4e5d-4a6b-9c8d-1234567890ab")).isEqualTo("a3f9c1***");
    }
}
