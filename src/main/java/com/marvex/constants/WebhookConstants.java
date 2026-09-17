package com.marvex.constants;

/**
 * Webhook HMAC signing format, shared between whichever side signs a payload
 * (MockWebhookEventListener) and whichever side verifies it (WebhookService).
 * The signature is only valid if both compute it over byte-identical input,
 * so this must never drift between the two independently.
 */
public final class WebhookConstants {

    private WebhookConstants() {}

    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String PAYLOAD_FORMAT = "{\"providerPaymentId\":\"%s\",\"status\":\"%s\"}";
}
