package com.marvex.service;

import com.marvex.constants.PaymentConstants;
import com.marvex.dto.*;
import com.marvex.entity.Merchant;
import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import com.marvex.provider.PaymentProvider;
import com.marvex.provider.ProviderPaymentResponse;
import com.marvex.provider.ProviderStatus;
import com.marvex.provider.YapilyPaymentProvider;
import com.marvex.repository.PaymentRepository;
import com.marvex.util.PayoutInstitutions;
import com.marvex.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;
    private final AuditService auditService;
    private final InstitutionService institutionService;

    @Value("${payment.max-amount-pence:100000}")
    private long maxAmountPence;

    private static final long EXPIRY_MINUTES = 10;

    private static final String PAYMENT_NOT_FOUND = "Payment not found";
    private static final String NOT_YOUR_PAYMENT = "Not your payment";

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest req, Merchant merchant) {
        log.info("[PaymentService] createPayment - merchant={}, amount={}, currency={}", merchant.getId(), req.amount(), req.currency());
        if (!merchant.isVerified()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Account not verified. Please complete registration via the email link.");
        }
        long amountPence = req.amount().movePointRight(2).longValueExact();
        if (amountPence > maxAmountPence) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                "Amount exceeds maximum allowed payment of " + (maxAmountPence / 100.0));
        }
        String rawToken = UUID.randomUUID().toString();
        String flowTraceId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(EXPIRY_MINUTES * 60);
        String sanitisedNote = req.note() != null ? req.note().replaceAll("[<>\"'&]", "") : null;

        Payment payment = new Payment();
        payment.setAmount(amountPence);
        payment.setCurrency(req.currency());
        payment.setNote(sanitisedNote);
        payment.setFlowTraceId(flowTraceId);
        payment.setMerchant(merchant);
        payment.setExpiresAt(expiresAt);
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(expiresAt);
        paymentRepository.save(payment);

        String paymentUrl = PaymentConstants.PAYMENT_URL_PREFIX + payment.getId() + "?token=" + rawToken;
        auditService.log(merchant.getId(), "PAYMENT_CREATED",
            "paymentId=" + payment.getId() + " amount=" + amountPence,
            payment.getFlowTraceId());
        log.info("[PaymentService] createPayment - created paymentId={}", payment.getId());
        return toResponse(payment, paymentUrl);
    }

    @Transactional
    public InitiatePaymentResponse initiate(UUID paymentId, String rawToken, String institutionId) {
        log.info("[PaymentService] initiate - paymentId={}, institutionId={}", paymentId, institutionId);
        Payment payment = getAndValidateToken(paymentId, rawToken);

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("[PaymentService] initiate - already PAID, paymentId={}", paymentId);
            auditService.log(payment.getMerchant().getId(), "PAYMENT_INITIATE_SKIPPED",
                "paymentId=" + paymentId + " reason=already_paid", payment.getFlowTraceId());
            return new InitiatePaymentResponse(paymentId.toString(), PaymentStatus.PAID, null);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("[PaymentService] initiate - invalid status={} for paymentId={}", payment.getStatus(), paymentId);
            auditService.log(payment.getMerchant().getId(), "PAYMENT_INITIATE_REJECTED",
                "paymentId=" + paymentId + " status=" + payment.getStatus(), payment.getFlowTraceId());
            throw new AppException(HttpStatus.BAD_REQUEST, "Payment cannot be initiated in status: " + payment.getStatus());
        }

        // Only the real Yapily provider moves money, so only its institution catalog
        // is worth checking against — a payer (whoever holds the payment link) could
        // otherwise pass any institutionId, including a provider sandbox/test bank
        // that settles nothing while the payment still reports PAID.
        if (paymentProvider instanceof YapilyPaymentProvider && !institutionService.isLiveInstitution(institutionId)) {
            log.warn("[PaymentService] initiate - rejected invalid institutionId={} for paymentId={}", institutionId, paymentId);
            auditService.log(payment.getMerchant().getId(), "PAYMENT_INITIATE_REJECTED",
                "paymentId=" + paymentId + " institutionId=" + institutionId + " reason=invalid_institution",
                payment.getFlowTraceId(), AuditService.MERCHANT, AuditService.FAILURE, PayoutInstitutions.INVALID_INSTITUTION_MESSAGE);
            throw new AppException(HttpStatus.BAD_REQUEST, PayoutInstitutions.INVALID_INSTITUTION_MESSAGE);
        }

        ProviderPaymentResponse providerResp;
        try {
            providerResp = paymentProvider.createPayment(
                payment.getAmount(), paymentId.toString(),
                institutionId,
                payment.getMerchant().getPayeeName(),
                payment.getMerchant().getSortCode(),
                payment.getMerchant().getAccountNumber()
            );
        } catch (AppException e) {
            // Payment stays PENDING here (nothing was written to it yet this transaction),
            // so the customer can retry on the same link — but without this, a provider
            // timeout/error left zero trace: support/ops had no way to distinguish "link
            // never opened" from "provider call failed" until the 10-minute expiry swept it up.
            auditService.log(payment.getMerchant().getId(), "PAYMENT_INITIATE_FAILED",
                "paymentId=" + paymentId + " institutionId=" + institutionId,
                payment.getFlowTraceId(), AuditService.MERCHANT, AuditService.FAILURE, e.getMessage());
            log.warn("[PaymentService] initiate - provider createPayment failed, paymentId={}, reason={}",
                paymentId, e.getMessage());
            throw e;
        }
        payment.setProviderPaymentId(providerResp.providerPaymentId());
        payment.setPaymentIdempotencyId(providerResp.paymentIdempotencyId());
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        auditService.log(payment.getMerchant().getId(), "PAYMENT_INITIATED",
            "paymentId=" + paymentId + " institutionId=" + institutionId
            + " providerPaymentId=" + providerResp.providerPaymentId(), payment.getFlowTraceId());
        log.info("[PaymentService] initiate - status=PROCESSING, providerPaymentId={}", providerResp.providerPaymentId());
        return new InitiatePaymentResponse(paymentId.toString(), PaymentStatus.PROCESSING, providerResp.redirectUrl());
    }

    @Transactional
    public PaymentStatus submitWithConsent(UUID paymentId, String rawToken, String consent) {
        log.info("[PaymentService] submitWithConsent - paymentId={}", paymentId);
        Payment payment = getAndValidateToken(paymentId, rawToken);

        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("[PaymentService] submitWithConsent - invalid status={} for paymentId={}", payment.getStatus(), paymentId);
            throw new AppException(HttpStatus.BAD_REQUEST, "Payment must be in PROCESSING status to submit consent");
        }

        String providerPaymentId;
        try {
            providerPaymentId = paymentProvider.submitPayment(
                consent, payment.getPaymentIdempotencyId(), payment.getAmount(), paymentId.toString(),
                payment.getMerchant().getPayeeName(),
                payment.getMerchant().getSortCode(),
                payment.getMerchant().getAccountNumber()
            );
        } catch (AppException e) {
            auditService.log(payment.getMerchant().getId(), "PAYMENT_SUBMIT_CONSENT_FAILED",
                "paymentId=" + paymentId,
                payment.getFlowTraceId(), AuditService.MERCHANT, AuditService.FAILURE, e.getMessage());
            log.warn("[PaymentService] submitWithConsent - provider submitPayment failed, paymentId={}, reason={}",
                paymentId, e.getMessage());
            throw e;
        }
        payment.setProviderPaymentId(providerPaymentId);
        payment.setConsentToken(consent);
        paymentRepository.save(payment);

        auditService.log(payment.getMerchant().getId(), "PAYMENT_CONSENT_SUBMITTED",
            "paymentId=" + paymentId + " providerPaymentId=" + providerPaymentId, payment.getFlowTraceId());
        log.info("[PaymentService] submitWithConsent - done, providerPaymentId={}", providerPaymentId);
        return payment.getStatus();
    }

    @Transactional
    public PaymentStatus confirm(UUID paymentId, String rawToken) {
        log.info("[PaymentService] confirm - paymentId={}", paymentId);
        Payment payment = getAndValidateToken(paymentId, rawToken);

        if (isTerminal(payment.getStatus())) {
            log.info("[PaymentService] confirm - already terminal status={}, paymentId={}", payment.getStatus(), paymentId);
            auditService.log(payment.getMerchant().getId(), "PAYMENT_CONFIRM_SKIPPED",
                "paymentId=" + paymentId + " reason=already_terminal status=" + payment.getStatus(), payment.getFlowTraceId());
            return payment.getStatus();
        }

        if (payment.getConsentToken() == null) {
            // initiate() moves the payment to PROCESSING as soon as the customer is
            // redirected to their bank, well before submitWithConsent() ever runs — so
            // confirm() can legitimately be polled (e.g. success page loaded from
            // sessionStorage in another tab) before consent exists yet. Calling Yapily
            // here is pointless: GET /payments/{id}/details always 400s with "Consent
            // token is missing from header" since the header is mandatory. Report
            // PROCESSING so the frontend keeps backing off instead of surfacing a
            // provider 502 for something that isn't a provider problem.
            log.info("[PaymentService] confirm - no consent token yet, still PROCESSING, paymentId={}", paymentId);
            return payment.getStatus();
        }

        String providerStatus;
        try {
            providerStatus = paymentProvider.verifyPayment(payment.getProviderPaymentId(), payment.getConsentToken());
        } catch (AppException e) {
            auditService.log(payment.getMerchant().getId(), "PAYMENT_CONFIRM_FAILED",
                "paymentId=" + paymentId,
                payment.getFlowTraceId(), AuditService.MERCHANT, AuditService.FAILURE, e.getMessage());
            log.warn("[PaymentService] confirm - provider verifyPayment failed, paymentId={}, reason={}",
                paymentId, e.getMessage());
            throw e;
        }
        PaymentStatus newStatus = ProviderStatus.PAID.equals(providerStatus) ? PaymentStatus.PAID
            : ProviderStatus.PENDING.equals(providerStatus) ? PaymentStatus.PROCESSING
            : PaymentStatus.FAILED;
        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        auditService.log(payment.getMerchant().getId(), "PAYMENT_" + newStatus.name(),
            "paymentId=" + paymentId + " providerStatus=" + providerStatus,
            payment.getFlowTraceId());
        log.info("[PaymentService] confirm - paymentId={}, newStatus={}", paymentId, newStatus);
        broadcastEvent(payment);
        return newStatus;
    }

    @Transactional
    public PaymentResponse regeneratePayment(UUID paymentId, Merchant merchant) {
        log.info("[PaymentService] regeneratePayment - paymentId={}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, PAYMENT_NOT_FOUND));
        if (!payment.getMerchant().getId().equals(merchant.getId()))
            throw new AppException(HttpStatus.FORBIDDEN, NOT_YOUR_PAYMENT);
        if (payment.getStatus() == PaymentStatus.PAID)
            throw new AppException(HttpStatus.BAD_REQUEST, "Payment already completed");
        if (payment.getStatus() == PaymentStatus.PROCESSING)
            throw new AppException(HttpStatus.BAD_REQUEST, "Payment is already being processed");

        PaymentStatus previousStatus = payment.getStatus();
        String rawToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(EXPIRY_MINUTES * 60);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setExpiresAt(expiresAt);
        payment.setPaymentAccessTokenHash(TokenUtil.sha256(rawToken));
        payment.setPaymentAccessTokenExpiresAt(expiresAt);
        paymentRepository.save(payment);

        auditService.log(merchant.getId(), "PAYMENT_REGENERATED",
            "paymentId=" + paymentId + " previousStatus=" + previousStatus, payment.getFlowTraceId());
        log.info("[PaymentService] regeneratePayment - new token issued for paymentId={}", paymentId);
        return toResponse(payment, PaymentConstants.PAYMENT_URL_PREFIX + payment.getId() + "?token=" + rawToken);
    }

    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, PAYMENT_NOT_FOUND));
        return toResponse(payment, null);
    }

    public PageResponse<PaymentResponse> getMerchantPayments(UUID merchantId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
        var content = result.getContent().stream().map(p -> toResponse(p, null)).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public AnalyticsResponse getAnalytics(UUID merchantId) {
        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new AnalyticsResponse(
            paymentRepository.sumPaidAmount(merchantId, PaymentStatus.PAID),
            paymentRepository.countPaidSince(merchantId, startOfToday, PaymentStatus.PAID),
            paymentRepository.countPaidSince(merchantId, startOfWeek, PaymentStatus.PAID),
            paymentRepository.countPaid(merchantId, PaymentStatus.PAID)
        );
    }

    @Transactional
    public void expireStalePayments() {
        List<Payment> expired = paymentRepository.findExpired(Instant.now(), PaymentStatus.PENDING, PaymentStatus.PROCESSING);
        expired.forEach(p -> {
            p.setStatus(PaymentStatus.EXPIRED);
            auditService.log(p.getMerchant().getId(), "PAYMENT_EXPIRED",
                "paymentId=" + p.getId(),
                p.getFlowTraceId(), AuditService.SYSTEM, AuditService.SUCCESS, null);
        });
        paymentRepository.saveAll(expired);
        if (!expired.isEmpty()) log.info("Expired {} payments", expired.size());
    }

    public void updateStatusFromWebhook(Payment payment, PaymentStatus newStatus) {
        payment.setStatus(newStatus);
        paymentRepository.save(payment);
        auditService.log(payment.getMerchant().getId(), "PAYMENT_WEBHOOK_" + newStatus.name(),
            "paymentId=" + payment.getId() + " providerPaymentId=" + payment.getProviderPaymentId()
           ,
            payment.getFlowTraceId(), AuditService.WEBHOOK, AuditService.SUCCESS, null);
        broadcastEvent(payment);
        try {
            emailService.sendPaymentNotification(
                payment.getMerchant().getEmail(),
                payment.getMerchant().getBusinessName(),
                payment.getAmount(),
                newStatus.name()
            );
        } catch (Exception e) {
            log.warn("[PaymentService] Failed to send payment notification email: {}", e.getMessage());
        }
    }

    public Payment findByProviderPaymentId(String providerPaymentId) {
        return paymentRepository.findByProviderPaymentId(providerPaymentId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, PAYMENT_NOT_FOUND));
    }

    public void sendInvoice(UUID paymentId, Merchant merchant, String recipientEmail) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, PAYMENT_NOT_FOUND));
        if (!payment.getMerchant().getId().equals(merchant.getId()))
            throw new AppException(HttpStatus.FORBIDDEN, NOT_YOUR_PAYMENT);
        if (payment.getStatus() != PaymentStatus.PAID)
            throw new AppException(HttpStatus.BAD_REQUEST, "Invoice can only be sent for completed payments");
        emailService.sendInvoiceEmail(
            recipientEmail, merchant.getBusinessName(),
            payment.getId(), payment.getAmount(), payment.getCurrency(), payment.getUpdatedAt()
        );
        auditService.log(merchant.getId(), "INVOICE_SENT",
            "paymentId=" + paymentId + " recipient=" + recipientEmail, payment.getFlowTraceId());
        log.info("[PaymentService] invoice sent for paymentId={} to {}", paymentId, recipientEmail);
    }

    private Payment getAndValidateToken(UUID paymentId, String rawToken) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, PAYMENT_NOT_FOUND));
        if (payment.getPaymentAccessTokenExpiresAt().isBefore(Instant.now())) {
            log.warn("[PaymentService] token expired for paymentId={}", paymentId);
            auditService.log(payment.getMerchant().getId(), "PAYMENT_TOKEN_EXPIRED",
                "paymentId=" + paymentId,
                payment.getFlowTraceId(), AuditService.MERCHANT, AuditService.FAILURE, "Token expired");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Payment token expired");
        }
        if (!TokenUtil.sha256(rawToken).equals(payment.getPaymentAccessTokenHash())) {
            log.warn("[PaymentService] invalid token for paymentId={}", paymentId);
            auditService.log(payment.getMerchant().getId(), "PAYMENT_TOKEN_INVALID",
                "paymentId=" + paymentId,
                payment.getFlowTraceId(), AuditService.MERCHANT, AuditService.FAILURE, "Token mismatch");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid payment token");
        }
        return payment;
    }

    private void broadcastEvent(Payment payment) {
        String event = payment.getStatus() == PaymentStatus.PAID ? "payment_paid" : "payment_failed";
        messagingTemplate.convertAndSend(
            PaymentConstants.PAYMENT_TOPIC_PREFIX + payment.getMerchant().getId(),
            Map.of("event", event, "paymentId", payment.getId(), "amount", payment.getAmount())
        );
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.PAID || status == PaymentStatus.FAILED || status == PaymentStatus.EXPIRED;
    }

    private PaymentResponse toResponse(Payment p, String paymentUrl) {
        return new PaymentResponse(p.getId(), p.getAmount(), p.getCurrency(),
            p.getStatus(), paymentUrl, p.getExpiresAt(), p.getNote());
    }
}
