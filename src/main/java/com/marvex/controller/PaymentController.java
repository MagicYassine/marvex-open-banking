package com.marvex.controller;

import com.marvex.dto.*;
import com.marvex.entity.Merchant;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import com.marvex.service.PaymentService;
import com.marvex.util.LogMask;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PaymentController {

    private static final String PAYMENT_TOKEN_REQUIRED = "Payment token required";

    private final PaymentService paymentService;

    @GetMapping("/analytics")
    public AnalyticsResponse analytics(@AuthenticationPrincipal Merchant merchant) {
        return paymentService.getAnalytics(merchant.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest req,
                                  @AuthenticationPrincipal Merchant merchant) {
        log.info("[PaymentController] POST /payments - merchantId={}, amount={}, currency={}", merchant.getId(), req.amount(), req.currency());
        PaymentResponse result = paymentService.createPayment(req, merchant);
        log.info("[PaymentController] POST /payments - created paymentId={}", result.paymentId());
        return result;
    }

    @GetMapping
    public PageResponse<PaymentResponse> list(@AuthenticationPrincipal Merchant merchant,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        log.info("[PaymentController] GET /payments - merchantId={}, page={}, size={}", merchant.getId(), page, size);
        return paymentService.getMerchantPayments(merchant.getId(), page, size);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        log.info("[PaymentController] GET /payments/{}", id);
        return paymentService.getPayment(id);
    }

    @PostMapping("/{id}/regenerate")
    public PaymentResponse regenerate(@PathVariable UUID id,
                                      @AuthenticationPrincipal Merchant merchant) {
        log.info("[PaymentController] POST /payments/{}/regenerate - merchantId={}", id, merchant.getId());
        PaymentResponse result = paymentService.regeneratePayment(id, merchant);
        log.info("[PaymentController] POST /payments/{}/regenerate - done", id);
        return result;
    }

    @PostMapping("/{id}/initiate")
    public InitiatePaymentResponse initiate(@PathVariable UUID id,
                                            @RequestBody Map<String, String> body) {
        log.info("[PaymentController] POST /payments/{}/initiate", id);
        String token = body.get("token");
        String institutionId = body.get("institutionId");
        if (token == null) throw new AppException(HttpStatus.UNAUTHORIZED, PAYMENT_TOKEN_REQUIRED);
        if (institutionId == null) throw new AppException(HttpStatus.BAD_REQUEST, "institutionId required");
        return paymentService.initiate(id, token, institutionId);
    }

    @PostMapping("/{id}/submit")
    public Map<String, String> submit(@PathVariable UUID id,
                                      @RequestBody Map<String, String> body) {
        log.info("[PaymentController] POST /payments/{}/submit", id);
        String token = body.get("token");
        String consent = body.get("consent");
        if (token == null) throw new AppException(HttpStatus.UNAUTHORIZED, PAYMENT_TOKEN_REQUIRED);
        if (consent == null) throw new AppException(HttpStatus.BAD_REQUEST, "Consent token required");
        PaymentStatus status = paymentService.submitWithConsent(id, token, consent);
        log.info("[PaymentController] POST /payments/{}/submit - status={}", id, status);
        return Map.of("status", status.name());
    }

    @PostMapping("/{id}/confirm")
    public Map<String, String> confirm(@PathVariable UUID id,
                                       @RequestBody Map<String, String> body) {
        log.info("[PaymentController] POST /payments/{}/confirm", id);
        String token = body.get("token");
        if (token == null) throw new AppException(HttpStatus.UNAUTHORIZED, PAYMENT_TOKEN_REQUIRED);
        PaymentStatus status = paymentService.confirm(id, token);
        log.info("[PaymentController] POST /payments/{}/confirm - status={}", id, status);
        return Map.of("status", status.name());
    }

    @PostMapping("/{id}/invoice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendInvoice(@PathVariable UUID id,
                            @Valid @RequestBody SendInvoiceRequest req,
                            @AuthenticationPrincipal Merchant merchant) {
        log.info("[PaymentController] POST /payments/{}/invoice - to={}", id, LogMask.maskEmail(req.recipientEmail()));
        paymentService.sendInvoice(id, merchant, req.recipientEmail());
    }

}
