package com.marvex.dto;

import com.marvex.enums.PaymentStatus;

public record InitiatePaymentResponse(
    String paymentId,
    PaymentStatus status,
    String redirectUrl
) {}
