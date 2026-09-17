package com.marvex.entity;

import com.marvex.constants.PaymentConstants;
import com.marvex.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency = PaymentConstants.CURRENCY_GBP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;

    @Column(name = "payment_idempotency_id", length = 64)
    private String paymentIdempotencyId;

    @Column(name = "consent_token")
    private String consentToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 255)
    private String note;

    @Column(name = "flow_trace_id", length = 36)
    private String flowTraceId;

    @Column(name = "payment_access_token_hash")
    private String paymentAccessTokenHash;

    @Column(name = "payment_access_token_expires_at")
    private Instant paymentAccessTokenExpiresAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
