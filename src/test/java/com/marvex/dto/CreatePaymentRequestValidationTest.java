package com.marvex.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real Jakarta Bean Validation constraints on CreatePaymentRequest
 * (no mocks, no Spring context) — in particular the currency restriction added
 * during the security review, since Yapily/EmailService/CSV export are GBP-only
 * but nothing previously stopped a merchant from submitting a different currency.
 */
class CreatePaymentRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validGbpPayment_hasNoViolations() {
        var req = new CreatePaymentRequest(new BigDecimal("5.00"), "GBP", "Table 4");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void nonGbpCurrency_isRejected() {
        var req = new CreatePaymentRequest(new BigDecimal("5.00"), "USD", null);
        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getPropertyPath)
            .extracting(Object::toString)
            .contains("currency");
    }

    @Test
    void lowercaseGbp_isRejected_currencyMustMatchExactCase() {
        var req = new CreatePaymentRequest(new BigDecimal("5.00"), "gbp", null);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void zeroAmount_isRejected() {
        var req = new CreatePaymentRequest(BigDecimal.ZERO, "GBP", null);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void negativeAmount_isRejected() {
        var req = new CreatePaymentRequest(new BigDecimal("-5.00"), "GBP", null);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void noteOverMaxLength_isRejected() {
        var req = new CreatePaymentRequest(new BigDecimal("5.00"), "GBP", "x".repeat(256));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void noteAtMaxLength_isAccepted() {
        var req = new CreatePaymentRequest(new BigDecimal("5.00"), "GBP", "x".repeat(255));
        assertThat(validator.validate(req)).isEmpty();
    }
}
