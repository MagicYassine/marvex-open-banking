package com.marvex.service;

import com.marvex.dto.CreatePaymentRequest;
import com.marvex.dto.InitiatePaymentResponse;
import com.marvex.dto.PaymentResponse;
import com.marvex.entity.Merchant;
import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import com.marvex.provider.MockPaymentProvider;
import com.marvex.provider.ProviderPaymentResponse;
import com.marvex.repository.PaymentRepository;
import com.marvex.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock MockPaymentProvider paymentProvider;
    @Mock @SuppressWarnings("unused") SimpMessagingTemplate messagingTemplate;
    @Mock @SuppressWarnings("unused") EmailService emailService;
    @Mock @SuppressWarnings("unused") AuditService auditService;
    @InjectMocks PaymentService paymentService;

    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setEmail("test@test.com");
        merchant.setBusinessName("Test");
        merchant.setVerified(true);
        ReflectionTestUtils.setField(paymentService, "maxAmountPence", 100_000L);
    }

    @Test
    void createPayment_savesAndReturnsPaymentUrl() {
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PaymentResponse resp = paymentService.createPayment(new CreatePaymentRequest(new BigDecimal("5.00"), "GBP", null), merchant);
        assertThat(resp.paymentUrl()).contains("/pay/");
        assertThat(resp.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void initiate_pendingPayment_transitionsToProcessing() {
        Payment payment = pendingPayment();
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentProvider.createPayment(anyLong(), anyString(), any(), any(), any(), any()))
            .thenReturn(new ProviderPaymentResponse("mock-123", "http://redirect", "idem-123"));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InitiatePaymentResponse resp = paymentService.initiate(payment.getId(), rawToken, "MONZO");
        assertThat(resp.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(resp.redirectUrl()).isEqualTo("http://redirect");
        assertThat(payment.getPaymentIdempotencyId()).isEqualTo("idem-123");
    }

    @Test
    void initiate_providerFailure_auditsFailureAndLeavesPaymentPending() {
        Payment payment = pendingPayment();
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        AppException providerError = new AppException(HttpStatus.BAD_GATEWAY,
            "Unable to initiate payment with provider, please try again later");
        when(paymentProvider.createPayment(anyLong(), anyString(), any(), any(), any(), any()))
            .thenThrow(providerError);

        assertThatThrownBy(() -> paymentService.initiate(payment.getId(), rawToken, "MONZO"))
            .isSameAs(providerError);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProviderPaymentId()).isNull();
        verify(paymentRepository, never()).save(any());
        verify(auditService).log(eq(merchant.getId()), eq("PAYMENT_INITIATE_FAILED"), anyString(),
            any(), eq(AuditService.MERCHANT), eq(AuditService.FAILURE), eq(providerError.getMessage()));
    }

    @Test
    void initiate_expiredToken_throws() {
        Payment payment = pendingPayment();
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().minusSeconds(1));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.initiate(payment.getId(), rawToken, "MONZO"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void initiate_alreadyPaid_returnsPaidNoMutation() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PAID);
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        InitiatePaymentResponse resp = paymentService.initiate(payment.getId(), rawToken, "MONZO");
        assertThat(resp.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(resp.redirectUrl()).isNull();
        verify(paymentProvider, never()).createPayment(anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    void confirm_processingPayment_transitionsToPaid() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProviderPaymentId("mock-123");
        payment.setConsentToken("consent-abc");
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyPayment("mock-123", "consent-abc")).thenReturn("PAID");
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentStatus status = paymentService.confirm(payment.getId(), rawToken);
        assertThat(status).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void confirm_pendingProviderStatus_staysProcessingNotFailed() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProviderPaymentId("mock-123");
        payment.setConsentToken("consent-abc");
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyPayment("mock-123", "consent-abc")).thenReturn("PENDING");
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentStatus status = paymentService.confirm(payment.getId(), rawToken);
        assertThat(status).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void confirm_alreadyTerminal_returnsCurrentStatusNoMutation() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PAID);
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        PaymentStatus status = paymentService.confirm(payment.getId(), rawToken);
        assertThat(status).isEqualTo(PaymentStatus.PAID);
        verify(paymentProvider, never()).verifyPayment(any(), any());
    }

    @Test
    void confirm_noConsentTokenYet_staysProcessingWithoutCallingProvider() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProviderPaymentId("mock-123");
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        PaymentStatus status = paymentService.confirm(payment.getId(), rawToken);
        assertThat(status).isEqualTo(PaymentStatus.PROCESSING);
        verify(paymentProvider, never()).verifyPayment(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirm_providerFailure_auditsFailureAndLeavesStatusUnchanged() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProviderPaymentId("mock-123");
        payment.setConsentToken("consent-abc");
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        AppException providerError = new AppException(HttpStatus.BAD_GATEWAY,
            "Unable to verify payment status with provider, please try again later");
        when(paymentProvider.verifyPayment("mock-123", "consent-abc")).thenThrow(providerError);

        assertThatThrownBy(() -> paymentService.confirm(payment.getId(), rawToken))
            .isSameAs(providerError);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verify(paymentRepository, never()).save(any());
        verify(auditService).log(eq(merchant.getId()), eq("PAYMENT_CONFIRM_FAILED"), anyString(),
            any(), eq(AuditService.MERCHANT), eq(AuditService.FAILURE), eq(providerError.getMessage()));
    }

    @Test
    void expireStalePayments_setsExpiredStatus() {
        Payment p1 = pendingPayment();
        Payment p2 = pendingPayment();
        p2.setStatus(PaymentStatus.PROCESSING);

        when(paymentRepository.findExpired(any(), any(), any())).thenReturn(List.of(p1, p2));
        when(paymentRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        paymentService.expireStalePayments();

        assertThat(p1.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(p2.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void createPayment_notesAreSanitised() {
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PaymentResponse resp = paymentService.createPayment(
            new CreatePaymentRequest(new BigDecimal("5.00"), "GBP", "<script>alert('xss')</script>Table 4"), merchant);
        assertThat(resp.note()).doesNotContain("<").doesNotContain(">");
    }


    @Test
    void createPayment_amountAboveConfiguredMax_throws() {
        ReflectionTestUtils.setField(paymentService, "maxAmountPence", 1_000L); // £10 cap

        assertThatThrownBy(() ->
            paymentService.createPayment(new CreatePaymentRequest(new BigDecimal("10.01"), "GBP", null), merchant))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("exceeds maximum");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_amountAtConfiguredMax_succeeds() {
        ReflectionTestUtils.setField(paymentService, "maxAmountPence", 1_000L); // £10 cap
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentResponse resp = paymentService.createPayment(
            new CreatePaymentRequest(new BigDecimal("10.00"), "GBP", null), merchant);

        assertThat(resp.amount()).isEqualTo(1000);
    }

    @Test
    void createPayment_unverifiedMerchant_throws() {
        merchant.setVerified(false);
        assertThatThrownBy(() ->
            paymentService.createPayment(new CreatePaymentRequest(new BigDecimal("5.00"), "GBP", null), merchant))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("verified");
    }

    @Test
    void initiate_invalidToken_throws() {
        Payment payment = pendingPayment();
        payment.setPaymentAccessTokenHash(TokenUtil.sha256("correct-token"));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.initiate(payment.getId(), "wrong-token", "MONZO"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    void regenerate_paidPayment_throws() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.regeneratePayment(payment.getId(), merchant))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("completed");
    }

    @Test
    void regenerate_wrongMerchant_throws() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.EXPIRED);
        Merchant other = new Merchant();
        other.setId(UUID.randomUUID());
        payment.setMerchant(other);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.regeneratePayment(payment.getId(), merchant))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Not your payment");
    }

    @Test
    void regenerate_processingPayment_throws() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.regeneratePayment(payment.getId(), merchant))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("processed");
    }

    @Test
    void submitWithConsent_notProcessing_throws() {
        Payment payment = pendingPayment(); // PENDING, not PROCESSING
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.submitWithConsent(payment.getId(), rawToken, "consent-token"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("PROCESSING");
    }

    @Test
    void submitWithConsent_reusesIdempotencyIdFromInitiate() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setPaymentIdempotencyId("idem-123");
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentProvider.submitPayment(eq("consent-token"), eq("idem-123"), anyLong(), anyString(), any(), any(), any()))
            .thenReturn("submit-provider-1");
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        paymentService.submitWithConsent(payment.getId(), rawToken, "consent-token");

        verify(paymentProvider).submitPayment(eq("consent-token"), eq("idem-123"), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void submitWithConsent_providerFailure_auditsFailureAndLeavesStatusUnchanged() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setPaymentIdempotencyId("idem-123");
        String rawToken = "test-token";
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(Instant.now().plusSeconds(600));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        AppException providerError = new AppException(HttpStatus.BAD_GATEWAY,
            "Unable to submit payment consent to provider, please try again later");
        when(paymentProvider.submitPayment(eq("consent-token"), eq("idem-123"), anyLong(), anyString(), any(), any(), any()))
            .thenThrow(providerError);

        assertThatThrownBy(() -> paymentService.submitWithConsent(payment.getId(), rawToken, "consent-token"))
            .isSameAs(providerError);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getProviderPaymentId()).isNull();
        verify(paymentRepository, never()).save(any());
        verify(auditService).log(eq(merchant.getId()), eq("PAYMENT_SUBMIT_CONSENT_FAILED"), anyString(),
            any(), eq(AuditService.MERCHANT), eq(AuditService.FAILURE), eq(providerError.getMessage()));
    }

    @Test
    void sendInvoice_notPaid_throws() {
        Payment payment = pendingPayment(); // PENDING
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.sendInvoice(payment.getId(), merchant, "customer@test.com"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("completed");
    }

    @Test
    void sendInvoice_wrongMerchant_throws() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PAID);
        Merchant other = new Merchant();
        other.setId(UUID.randomUUID());
        payment.setMerchant(other);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.sendInvoice(payment.getId(), merchant, "customer@test.com"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Not your payment");
    }

    @Test
    void getPayment_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(id))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void confirm_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirm(id, "any-token"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("not found");
    }

    private Payment pendingPayment() {
        Payment p = new Payment();
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setAmount(500);
        p.setCurrency("GBP");
        p.setStatus(PaymentStatus.PENDING);
        p.setMerchant(merchant);
        p.setExpiresAt(Instant.now().plusSeconds(600));
        return p;
    }
}
