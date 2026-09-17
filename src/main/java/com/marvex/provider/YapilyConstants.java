package com.marvex.provider;

/**
 * Wire-format literals for Yapily API requests, shared between
 * {@link YapilyPaymentProvider} and {@code InstitutionService} (both call
 * Yapily directly with Basic auth).
 */
public final class YapilyConstants {

    private YapilyConstants() {}

    public static final String BASIC_AUTH_PREFIX = "Basic ";
    public static final String CONSENT_HEADER = "Consent";
    public static final String PAYMENT_TYPE_DOMESTIC = "DOMESTIC_PAYMENT";
    public static final String CONTEXT_TYPE_OTHER = "OTHER";
}
