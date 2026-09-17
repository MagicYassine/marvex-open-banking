package com.marvex.provider;

/**
 * The raw status vocabulary returned by {@link PaymentProvider#verifyPayment}
 * (and consumed by webhook payloads carrying the same values). Shared across
 * provider, service and email code that branches on these exact strings —
 * centralised here so the wire values can't drift out of sync between them.
 */
public final class ProviderStatus {

    private ProviderStatus() {}

    public static final String PAID = "PAID";
    public static final String PENDING = "PENDING";
    public static final String FAILED = "FAILED";
}
