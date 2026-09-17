package com.marvex.webhook;

import com.marvex.dto.WebhookRequest;
import com.marvex.entity.Merchant;
import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import com.marvex.service.PaymentService;
import com.marvex.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock PaymentService paymentService;
    @InjectMocks WebhookService webhookService;

    private static final String SECRET = "test-secret";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", SECRET);
    }

    @Test
    void validSignature_processingPayment_updatesToPaid() throws Exception {
        String providerPaymentId = "mock-123";
        String status = "PAID";
        String sig = sign(providerPaymentId, status);

        Payment payment = payment(PaymentStatus.PROCESSING);
        when(paymentService.findByProviderPaymentId(providerPaymentId)).thenReturn(payment);

        webhookService.process(new WebhookRequest(providerPaymentId, status, sig));

        verify(paymentService).updateStatusFromWebhook(payment, PaymentStatus.PAID);
    }

    @Test
    void validSignature_processingPayment_updatesToFailed() throws Exception {
        String providerPaymentId = "mock-456";
        String status = "FAILED";
        String sig = sign(providerPaymentId, status);

        Payment payment = payment(PaymentStatus.PROCESSING);
        when(paymentService.findByProviderPaymentId(providerPaymentId)).thenReturn(payment);

        webhookService.process(new WebhookRequest(providerPaymentId, status, sig));

        verify(paymentService).updateStatusFromWebhook(payment, PaymentStatus.FAILED);
    }

    @Test
    void invalidSignature_throws401() {
        assertThatThrownBy(() ->
            webhookService.process(new WebhookRequest("mock-123", "PAID", "bad-sig"))
        ).isInstanceOf(AppException.class).hasMessageContaining("signature");
    }

    @Test
    void alreadyPaid_idempotent_noMutation() throws Exception {
        String providerPaymentId = "mock-789";
        String status = "PAID";
        String sig = sign(providerPaymentId, status);

        Payment payment = payment(PaymentStatus.PAID);
        when(paymentService.findByProviderPaymentId(providerPaymentId)).thenReturn(payment);

        webhookService.process(new WebhookRequest(providerPaymentId, status, sig));

        verify(paymentService, never()).updateStatusFromWebhook(any(), any());
    }

    @Test
    void alreadyFailed_idempotent_noMutation() throws Exception {
        String providerPaymentId = "mock-999";
        String status = "PAID";
        String sig = sign(providerPaymentId, status);

        Payment payment = payment(PaymentStatus.FAILED);
        when(paymentService.findByProviderPaymentId(providerPaymentId)).thenReturn(payment);

        webhookService.process(new WebhookRequest(providerPaymentId, status, sig));

        verify(paymentService, never()).updateStatusFromWebhook(any(), any());
    }

    @Test
    void alreadyExpired_idempotent_noMutation() throws Exception {
        String providerPaymentId = "mock-1000";
        String status = "FAILED";
        String sig = sign(providerPaymentId, status);

        Payment payment = payment(PaymentStatus.EXPIRED);
        when(paymentService.findByProviderPaymentId(providerPaymentId)).thenReturn(payment);

        webhookService.process(new WebhookRequest(providerPaymentId, status, sig));

        verify(paymentService, never()).updateStatusFromWebhook(any(), any());
    }

    private Payment payment(PaymentStatus status) {
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setStatus(status);
        p.setMerchant(merchant);
        return p;
    }

    private String sign(String providerPaymentId, String status) throws Exception {
        String payload = String.format("{\"providerPaymentId\":\"%s\",\"status\":\"%s\"}", providerPaymentId, status);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
    }
}
