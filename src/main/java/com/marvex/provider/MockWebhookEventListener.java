package com.marvex.provider;

import com.marvex.constants.WebhookConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.Map;

/**
 * Fires the mock webhook after the initiate transaction commits, so the payment
 * row is visible in the DB when the webhook handler looks it up.
 *
 * Kept in a separate bean because @TransactionalEventListener on a JDK-proxied
 * bean (one that implements an interface) requires the method to be on the
 * interface — extracting it here avoids that constraint entirely.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = ProviderProfiles.PROPERTY, havingValue = ProviderProfiles.MOCK)
public class MockWebhookEventListener {

    @Value("${mock.provider.webhook-url}")
    private String webhookUrl;

    @Value("${webhook.secret}")
    private String webhookSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMockWebhookEvent(MockWebhookEvent event) {
        try {
            String payload = String.format(WebhookConstants.PAYLOAD_FORMAT,
                    event.providerPaymentId(), event.outcome());
            String signature = hmacSha256(webhookSecret, payload);
            restTemplate.postForEntity(webhookUrl, Map.of(
                    "providerPaymentId", event.providerPaymentId(),
                    "status", event.outcome(),
                    "signature", signature
            ), Void.class);
            log.info("[MockWebhookEventListener] webhook fired for providerPaymentId={}, outcome={}",
                    event.providerPaymentId(), event.outcome());
        } catch (Exception e) {
            log.error("[MockWebhookEventListener] webhook failed for {}: {}",
                    event.providerPaymentId(), e.getMessage());
        }
    }

    private String hmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance(WebhookConstants.HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(), WebhookConstants.HMAC_ALGORITHM));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes()));
    }
}
