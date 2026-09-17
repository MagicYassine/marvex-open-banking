package com.marvex.provider;

public record ProviderPaymentResponse(String providerPaymentId, String redirectUrl, String paymentIdempotencyId) {}
