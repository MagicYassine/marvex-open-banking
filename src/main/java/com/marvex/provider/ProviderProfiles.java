package com.marvex.provider;

/**
 * The {@code payment.provider} profile switch that selects which
 * {@link PaymentProvider} bean (and, for the mock profile, which webhook
 * listener) gets wired up. Shared across the {@code @ConditionalOnProperty}
 * annotations that gate those beans — a typo in one but not the others would
 * silently leave no provider (or the wrong one) active.
 */
public final class ProviderProfiles {

    private ProviderProfiles() {}

    public static final String PROPERTY = "payment.provider";
    public static final String YAPILY = "yapily";
    public static final String MOCK = "mock";
}
