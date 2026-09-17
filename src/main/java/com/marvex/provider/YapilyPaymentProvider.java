package com.marvex.provider;

import com.marvex.constants.PaymentConstants;
import com.marvex.exception.AppException;
import com.marvex.yapily.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@ConditionalOnProperty(name = ProviderProfiles.PROPERTY, havingValue = ProviderProfiles.YAPILY, matchIfMissing = true)
public class YapilyPaymentProvider implements PaymentProvider {

    @Value("${yapily.base-url}")
    private String baseUrl;

    @Value("${yapily.basic-token}")
    private String basicToken;

    @Value("${yapily.user-uuid}")
    private String userUuid;

    @Value("${yapily.callback}")
    private String callback;

    private final RestTemplate restTemplate;

    public YapilyPaymentProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public ProviderPaymentResponse createPayment(long amount, String reference, String institutionId, String payeeName, String sortCode, String accountNumber) {
        log.info("[Yapily] createPayment - reference={}, amount={}", reference, amount);
        String paymentIdempotencyId = UUID.randomUUID().toString().replace("-", "");
        PaymentAuthRequest body = buildAuthRequest(paymentIdempotencyId, amount, reference, institutionId, payeeName, sortCode, accountNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, YapilyConstants.BASIC_AUTH_PREFIX + basicToken);

        try {
            ResponseEntity<YapilyAuthResponse> response = restTemplate.exchange(
                baseUrl + "/payment-auth-requests?raw=true",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                YapilyAuthResponse.class
            );
            log.info("[Yapily] createPayment - HTTP {}", response.getStatusCode());
            YapilyAuthResponse resp = response.getBody();
            String providerPaymentId = resp != null ? resp.getId() : UUID.randomUUID().toString();
            String redirectUrl = resp != null ? resp.getAuthorisationUrl() : null;
            log.info("[Yapily] createPayment - providerPaymentId={}, redirectUrl={}", providerPaymentId, redirectUrl);
            return new ProviderPaymentResponse(providerPaymentId, redirectUrl, paymentIdempotencyId);
        } catch (RestClientException e) {
            log.error("[Yapily] createPayment failed - reference={}, error={}", reference, e.getMessage(), e);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Unable to initiate payment with provider, please try again later");
        }
    }

    @Override
    public String submitPayment(String consent, String paymentIdempotencyId, long amount, String reference, String payeeName, String sortCode, String accountNumber) {
        log.info("[Yapily] submitPayment - reference={}, amount={}", reference, amount);
        // The payment submission body must be byte-for-byte the same Initiation/Risk
        // data the consent (payment-auth-request) was granted against — including
        // paymentIdempotencyId — or Yapily rejects it with UK.OBIE.Resource.Mismatch.
        PaymentRequest body = new PaymentRequest();
        body.setType(YapilyConstants.PAYMENT_TYPE_DOMESTIC);
        body.setPaymentIdempotencyId(paymentIdempotencyId);
        body.setReference(reference);
        body.setContextType(YapilyConstants.CONTEXT_TYPE_OTHER);
        body.setAmount(new Amount(String.format("%.2f", amount / 100.0), PaymentConstants.CURRENCY_GBP));
        body.setPayee(new Payee(payeeName, identifications(sortCode, accountNumber)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, YapilyConstants.BASIC_AUTH_PREFIX + basicToken);
        headers.set(YapilyConstants.CONSENT_HEADER, consent);

        try {
            ResponseEntity<YapilyPaymentResponse> response = restTemplate.exchange(
                baseUrl + "/payments?raw=True",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                YapilyPaymentResponse.class
            );
            log.info("[Yapily] submitPayment - HTTP {}", response.getStatusCode());
            YapilyPaymentResponse resp = response.getBody();
            String id = resp != null ? resp.getId() : UUID.randomUUID().toString();
            log.info("[Yapily] submitPayment - providerPaymentId={}, initiationStatus={}", id, resp != null ? resp.getStatus() : null);
            return id;
        } catch (RestClientException e) {
            log.error("[Yapily] submitPayment failed - reference={}, error={}", reference, e.getMessage(), e);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Unable to submit payment consent to provider, please try again later");
        }
    }

    @Override
    public String verifyPayment(String providerPaymentId, String consent) {
        log.info("[Yapily] verifyPayment - providerPaymentId={}", providerPaymentId);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, YapilyConstants.BASIC_AUTH_PREFIX + basicToken);
        // Yapily's GET /payments/{id}/details marks the Consent header as mandatory —
        // there's no valid call without it. A null consent here means the payment
        // hasn't been through submitWithConsent yet; callers must not reach this
        // point in that case (see PaymentService.confirm's consentToken == null guard).
        if (consent != null) {
            headers.set(YapilyConstants.CONSENT_HEADER, consent);
        }
        try {
            ResponseEntity<YapilyPaymentStatusResponse> response = restTemplate.exchange(
                baseUrl + "/payments/" + providerPaymentId + "/details",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                YapilyPaymentStatusResponse.class
            );
            YapilyPaymentStatusResponse resp = response.getBody();
            if (resp == null) return ProviderStatus.FAILED;
            // "PENDING" (ISO PDNG/RCVD/ACSP/ACTC/...) is not terminal — the Institution is
            // still processing the initiation and may still settle. Only report FAILED once
            // the Institution has actually rejected it, or we'll wrongly kill payments that
            // just haven't finished settling by the time the client calls confirm.
            String result = "COMPLETED".equals(resp.getStatus()) ? ProviderStatus.PAID
                : "PENDING".equals(resp.getStatus()) ? ProviderStatus.PENDING
                : ProviderStatus.FAILED;
            if (!ProviderStatus.PAID.equals(result)) {
                YapilyPaymentStatusDetails details = resp.getStatusDetails();
                String statusReason = details != null ? details.getStatusReason() : null;
                String statusReasonDescription = details != null ? details.getStatusReasonDescription() : null;
                String isoCode = details != null && details.getIsoStatus() != null ? details.getIsoStatus().getCode() : null;
                String isoName = details != null && details.getIsoStatus() != null ? details.getIsoStatus().getName() : null;
                log.info("[Yapily] verifyPayment - status={}, rawStatus={}, statusReason={}, statusReasonDescription={}, isoStatus={}({})",
                    result, resp.getStatus(), statusReason, statusReasonDescription, isoCode, isoName);
            } else {
                log.info("[Yapily] verifyPayment - status={}", result);
            }
            return result;
        } catch (RestClientException e) {
            log.error("[Yapily] verifyPayment failed - providerPaymentId={}, error={}", providerPaymentId, e.getMessage(), e);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Unable to verify payment status with provider, please try again later");
        }
    }

    private PaymentAuthRequest buildAuthRequest(String paymentIdempotencyId, long amount, String reference, String institutionId, String payeeName, String sortCode, String accountNumber) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setType(YapilyConstants.PAYMENT_TYPE_DOMESTIC);
        paymentRequest.setPaymentIdempotencyId(paymentIdempotencyId);
        paymentRequest.setReference(reference);
        paymentRequest.setContextType(YapilyConstants.CONTEXT_TYPE_OTHER);
        paymentRequest.setAmount(new Amount(String.format("%.2f", amount / 100.0), PaymentConstants.CURRENCY_GBP));
        paymentRequest.setPayee(new Payee(payeeName, identifications(sortCode, accountNumber)));

        PaymentAuthRequest req = new PaymentAuthRequest();
        req.setUserUuid(userUuid);
        req.setInstitutionId(institutionId);
        req.setCallback(callback + "/" + reference + "/callback");
        req.setPaymentRequest(paymentRequest);
        return req;
    }

    private List<AccountIdentification> identifications(String sortCode, String accountNumber) {
        return List.of(
            new AccountIdentification("SORT_CODE", sortCode),
            new AccountIdentification("ACCOUNT_NUMBER", accountNumber)
        );
    }
}
