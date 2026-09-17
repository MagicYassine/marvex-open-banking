package com.marvex.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = ProviderProfiles.PROPERTY, havingValue = ProviderProfiles.MOCK)
public class MockPaymentProvider implements PaymentProvider {

    @Value("${mock.provider.outcome:" + ProviderStatus.PAID + "}")
    private String outcome;

    @Value("${app.base-url}")
    private String baseUrl;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ProviderPaymentResponse createPayment(long amount, String reference, String institutionId,
                                                  String payeeName, String sortCode, String accountNumber) {
        String providerPaymentId = "mock-" + UUID.randomUUID();
        String redirectUrl = baseUrl + "/mock-bank/" + reference;
        // Publish event — MockWebhookEventListener fires the webhook after the
        // enclosing transaction commits, so the payment row is visible in the DB.
        eventPublisher.publishEvent(new MockWebhookEvent(providerPaymentId, outcome));
        return new ProviderPaymentResponse(providerPaymentId, redirectUrl, "mock-idempotency-" + UUID.randomUUID());
    }

    @Override
    public String submitPayment(String consent, String paymentIdempotencyId, long amount, String reference,
                                String payeeName, String sortCode, String accountNumber) {
        return "mock-" + UUID.randomUUID();
    }

    @Override
    public String verifyPayment(String providerPaymentId, String consent) {
        return outcome;
    }
}
