package com.marvex.constants;

public final class PaymentConstants {

    private PaymentConstants() {}

    /** The only currency the payment provider and merchant bank details support. */
    public static final String CURRENCY_GBP = "GBP";

    /** STOMP topic prefix a merchant's payment updates are broadcast/subscribed on. */
    public static final String PAYMENT_TOPIC_PREFIX = "/topic/payments/";

    /** Path prefix for the payer-facing payment link. */
    public static final String PAYMENT_URL_PREFIX = "/pay/";
}
