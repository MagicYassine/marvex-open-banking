package com.marvex.provider;

import com.marvex.exception.AppException;
import com.marvex.yapily.PaymentRequest;
import com.marvex.yapily.YapilyAuthResponse;
import com.marvex.yapily.YapilyAuthResponseData;
import com.marvex.yapily.YapilyPaymentResponse;
import com.marvex.yapily.YapilyPaymentResponseData;
import com.marvex.yapily.YapilyPaymentStatusData;
import com.marvex.yapily.YapilyPaymentStatusPayment;
import com.marvex.yapily.YapilyPaymentStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YapilyPaymentProviderTest {

    @Mock RestTemplate restTemplate;

    private YapilyPaymentProvider provider;

    @BeforeEach
    void setup() {
        provider = new YapilyPaymentProvider();
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.yapily.com");
        ReflectionTestUtils.setField(provider, "basicToken", "test-token");
        ReflectionTestUtils.setField(provider, "userUuid", "00000000-0000-0000-0000-000000000000");
        ReflectionTestUtils.setField(provider, "callback", "http://localhost:8080/pay");
        ReflectionTestUtils.setField(provider, "restTemplate", restTemplate);
    }

    @Test
    void createPayment_providerTimesOut_throwsBadGateway_notRawException() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyAuthResponse.class)))
            .thenThrow(new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> provider.createPayment(500, "ref-1", "MONZO", "Payee", "040004", "12345678"))
            .isInstanceOf(AppException.class)
            .extracting(ex -> ((AppException) ex).getStatus())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void createPayment_success_returnsProviderResponse() {
        YapilyAuthResponseData data = new YapilyAuthResponseData();
        ReflectionTestUtils.setField(data, "id", "provider-123");
        ReflectionTestUtils.setField(data, "authorisationUrl", "https://bank.example/auth");
        YapilyAuthResponse body = new YapilyAuthResponse();
        ReflectionTestUtils.setField(body, "data", data);

        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyAuthResponse.class)))
            .thenReturn(ResponseEntity.ok(body));

        ProviderPaymentResponse resp = provider.createPayment(500, "ref-1", "MONZO", "Payee", "040004", "12345678");

        assertThat(resp.providerPaymentId()).isEqualTo("provider-123");
        assertThat(resp.redirectUrl()).isEqualTo("https://bank.example/auth");
    }

    @Test
    void verifyPayment_providerUnreachable_throwsBadGateway() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentStatusResponse.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> provider.verifyPayment("provider-123", "consent-token"))
            .isInstanceOf(AppException.class)
            .extracting(ex -> ((AppException) ex).getStatus())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void verifyPayment_completedStatus_mapsToPaid() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentStatusResponse.class)))
            .thenReturn(ResponseEntity.ok(statusResponse("COMPLETED")));

        assertThat(provider.verifyPayment("provider-123", "consent-token")).isEqualTo("PAID");
    }

    @Test
    void verifyPayment_hitsDetailsEndpoint() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.exchange(urlCaptor.capture(), any(), any(), eq(YapilyPaymentStatusResponse.class)))
            .thenReturn(ResponseEntity.ok(statusResponse("COMPLETED")));

        provider.verifyPayment("provider-123", "consent-token");

        assertThat(urlCaptor.getValue()).isEqualTo("https://api.yapily.com/payments/provider-123/details");
    }

    @Test
    void verifyPayment_sendsConsentHeader() {
        ArgumentCaptor<HttpEntity> captor =
            ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(any(String.class), any(), captor.capture(), eq(YapilyPaymentStatusResponse.class)))
            .thenReturn(ResponseEntity.ok(statusResponse("COMPLETED")));

        provider.verifyPayment("provider-123", "consent-from-submit");

        assertThat(captor.getValue().getHeaders().getFirst("Consent")).isEqualTo("consent-from-submit");
    }

    @Test
    void verifyPayment_nonCompletedStatus_mapsToFailed() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentStatusResponse.class)))
            .thenReturn(ResponseEntity.ok(statusResponse("REJECTED")));

        assertThat(provider.verifyPayment("provider-123", "consent-token")).isEqualTo("FAILED");
    }

    @Test
    void verifyPayment_pendingStatus_mapsToPendingNotFailed() {
        // PENDING (ISO PDNG etc.) means the Institution is still processing the
        // initiation — it's not terminal, so it must not be reported as FAILED.
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentStatusResponse.class)))
            .thenReturn(ResponseEntity.ok(statusResponse("PENDING")));

        assertThat(provider.verifyPayment("provider-123", "consent-token")).isEqualTo("PENDING");
    }

    private YapilyPaymentStatusResponse statusResponse(String status) {
        YapilyPaymentStatusPayment payment = new YapilyPaymentStatusPayment();
        ReflectionTestUtils.setField(payment, "status", status);
        YapilyPaymentStatusData data = new YapilyPaymentStatusData();
        ReflectionTestUtils.setField(data, "payments", List.of(payment));
        YapilyPaymentStatusResponse body = new YapilyPaymentStatusResponse();
        ReflectionTestUtils.setField(body, "data", data);
        return body;
    }

    @Test
    void verifyPayment_nullResponseBody_mapsToFailed() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentStatusResponse.class)))
            .thenReturn(ResponseEntity.ok(null));

        assertThat(provider.verifyPayment("provider-123", "consent-token")).isEqualTo("FAILED");
    }

    @Test
    void createPayment_nullResponseBody_fallsBackToRandomUuidAndNullRedirect() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyAuthResponse.class)))
            .thenReturn(ResponseEntity.ok(null));

        ProviderPaymentResponse resp = provider.createPayment(500, "ref-1", "MONZO", "Payee", "040004", "12345678");

        assertThat(resp.providerPaymentId()).isNotNull();
        assertThatCode(() -> UUID.fromString(resp.providerPaymentId())).doesNotThrowAnyException();
        assertThat(resp.redirectUrl()).isNull();
    }

    @Test
    void submitPayment_success_returnsProviderPaymentId() {
        YapilyPaymentResponseData data = new YapilyPaymentResponseData();
        ReflectionTestUtils.setField(data, "id", "submit-provider-1");
        YapilyPaymentResponse body = new YapilyPaymentResponse();
        ReflectionTestUtils.setField(body, "data", data);

        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentResponse.class)))
            .thenReturn(ResponseEntity.ok(body));

        String id = provider.submitPayment("consent-token", "idem-1", 500, "ref-1", "Payee", "040004", "12345678");

        assertThat(id).isEqualTo("submit-provider-1");
    }

    @Test
    void submitPayment_sendsGivenIdempotencyIdInBody_notARandomOne() {
        // Regression test: Yapily rejects submission with UK.OBIE.Resource.Mismatch
        // if the paymentIdempotencyId in the /payments body doesn't match the one
        // the consent was granted against in /payment-auth-requests.
        YapilyPaymentResponseData data = new YapilyPaymentResponseData();
        ReflectionTestUtils.setField(data, "id", "submit-provider-1");
        YapilyPaymentResponse body = new YapilyPaymentResponse();
        ReflectionTestUtils.setField(body, "data", data);

        ArgumentCaptor<HttpEntity> captor =
            ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(any(String.class), any(), captor.capture(), eq(YapilyPaymentResponse.class)))
            .thenReturn(ResponseEntity.ok(body));

        provider.submitPayment("consent-token", "idem-from-initiate", 500, "ref-1", "Payee", "040004", "12345678");

        PaymentRequest sentBody = (PaymentRequest) captor.getValue().getBody();
        assertThat(sentBody.getPaymentIdempotencyId()).isEqualTo("idem-from-initiate");
    }

    @Test
    void submitPayment_providerUnreachable_throwsBadGateway() {
        when(restTemplate.exchange(any(String.class), any(), any(), eq(YapilyPaymentResponse.class)))
            .thenThrow(new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> provider.submitPayment("consent-token", "idem-1", 500, "ref-1", "Payee", "040004", "12345678"))
            .isInstanceOf(AppException.class)
            .extracting(ex -> ((AppException) ex).getStatus())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
