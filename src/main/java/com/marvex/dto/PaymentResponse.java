package com.marvex.dto;

import com.marvex.enums.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    long amount,
    String currency,
    PaymentStatus status,
    String paymentUrl,
    Instant expiresAt,
    String note
) {}
