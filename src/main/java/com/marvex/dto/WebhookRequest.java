package com.marvex.dto;

import jakarta.validation.constraints.NotBlank;

public record WebhookRequest(
    @NotBlank String providerPaymentId,
    @NotBlank String status,
    @NotBlank String signature
) {}
