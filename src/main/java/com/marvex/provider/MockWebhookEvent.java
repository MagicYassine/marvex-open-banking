package com.marvex.provider;

/**
 * Published after the initiate transaction commits so the mock webhook fires
 * against an already-visible payment row, avoiding the 404 race condition.
 */
public record MockWebhookEvent(String providerPaymentId, String outcome) {}
