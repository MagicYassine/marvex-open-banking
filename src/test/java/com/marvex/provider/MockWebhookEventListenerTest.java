package com.marvex.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockWebhookEventListenerTest {

    private static final String WEBHOOK_URL = "http://localhost:8080/webhooks/payment";

    @Mock RestTemplate restTemplate;

    private MockWebhookEventListener listener;

    @BeforeEach
    void setup() {
        listener = new MockWebhookEventListener();
        ReflectionTestUtils.setField(listener, "webhookUrl", WEBHOOK_URL);
        ReflectionTestUtils.setField(listener, "webhookSecret", "webhook-shared-secret");
        ReflectionTestUtils.setField(listener, "restTemplate", restTemplate);
    }

    @Test
    void onMockWebhookEvent_postsSignedPayloadMatchingManualHmac() throws Exception {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        listener.onMockWebhookEvent(new MockWebhookEvent("provider-1", "PAID"));

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(Void.class));

        Map<?, ?> sent = captor.getValue();
        String expectedPayload = "{\"providerPaymentId\":\"provider-1\",\"status\":\"PAID\"}";
        assertThat(sent.get("providerPaymentId")).isEqualTo("provider-1");
        assertThat(sent.get("status")).isEqualTo("PAID");
        assertThat(sent.get("signature")).isEqualTo(hmac("webhook-shared-secret", expectedPayload));
    }

    @Test
    void onMockWebhookEvent_differentSecrets_produceDifferentSignaturesForSamePayload() throws Exception {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        listener.onMockWebhookEvent(new MockWebhookEvent("provider-2", "FAILED"));
        ArgumentCaptor<Map> firstCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), firstCaptor.capture(), eq(Void.class));
        String firstSignature = (String) firstCaptor.getValue().get("signature");

        reset(restTemplate);
        ReflectionTestUtils.setField(listener, "webhookSecret", "a-completely-different-secret");
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        listener.onMockWebhookEvent(new MockWebhookEvent("provider-2", "FAILED"));
        ArgumentCaptor<Map> secondCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), secondCaptor.capture(), eq(Void.class));
        String secondSignature = (String) secondCaptor.getValue().get("signature");

        assertThat(firstSignature).isNotEqualTo(secondSignature);
    }

    @Test
    void onMockWebhookEvent_restTemplateThrows_exceptionIsSwallowed() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(Void.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatCode(() -> listener.onMockWebhookEvent(new MockWebhookEvent("provider-3", "PAID")))
                .doesNotThrowAnyException();
    }

    @Test
    void onMockWebhookEvent_isWiredAsAsyncAfterCommitTransactionalListener() throws Exception {
        Method method = MockWebhookEventListener.class.getDeclaredMethod("onMockWebhookEvent", MockWebhookEvent.class);

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
    }
}
