package com.marvex.dto;

import com.marvex.constants.PaymentConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal amount,
    // The payment provider (Yapily UK Open Banking) and all merchant bank details
    // (sort code / account number) only support GBP — reject anything else up front
    // rather than silently processing a mismatched currency as GBP downstream.
    @NotBlank @Pattern(regexp = PaymentConstants.CURRENCY_GBP, message = "Only GBP is supported") String currency,
    @Size(max = 255) String note
) {}
