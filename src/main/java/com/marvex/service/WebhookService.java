package com.marvex.service;

import com.marvex.constants.WebhookConstants;
import com.marvex.dto.WebhookRequest;
import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import com.marvex.provider.ProviderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final PaymentService paymentService;

    @Value("${webhook.secret}")
    private String webhookSecret;

    @Transactional
    public void process(WebhookRequest req) {
        validateSignature(req);

        Payment payment = paymentService.findByProviderPaymentId(req.providerPaymentId());

        // Idempotency: already terminal → no-op
        PaymentStatus current = payment.getStatus();
        if (current == PaymentStatus.PAID || current == PaymentStatus.FAILED || current == PaymentStatus.EXPIRED) {
            log.info("Webhook ignored — payment {} already in terminal state {}", payment.getId(), current);
            return;
        }

        PaymentStatus newStatus = ProviderStatus.PAID.equals(req.status()) ? PaymentStatus.PAID : PaymentStatus.FAILED;
        paymentService.updateStatusFromWebhook(payment, newStatus);
        log.info("Webhook processed — payment {} → {}", payment.getId(), newStatus);
    }

    private void validateSignature(WebhookRequest req) {
        try {
            String payload = String.format(WebhookConstants.PAYLOAD_FORMAT,
                    req.providerPaymentId(), req.status());
            Mac mac = Mac.getInstance(WebhookConstants.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), WebhookConstants.HMAC_ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            // Constant-time comparison — avoids leaking signature bytes via response-time differences
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), req.signature().getBytes(StandardCharsets.UTF_8))) {
                throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WebhookService] Signature validation failed - providerPaymentId={}, error={}",
                req.providerPaymentId(), e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Signature validation failed");
        }
    }
}
