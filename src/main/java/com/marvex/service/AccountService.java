package com.marvex.service;

import com.marvex.dto.AccountResponse;
import com.marvex.dto.ChangePasswordRequest;
import com.marvex.dto.UpdateAccountRequest;
import com.marvex.entity.Merchant;
import com.marvex.exception.AppException;
import com.marvex.repository.AuditLogRepository;
import com.marvex.repository.MerchantRepository;
import com.marvex.repository.PaymentRepository;
import com.marvex.repository.RefreshTokenRepository;
import com.marvex.util.PayoutInstitutions;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final MerchantRepository merchantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;

    public AccountResponse getAccount(Merchant merchant) {
        auditService.log(merchant.getId(), "ACCOUNT_ACCESSED", null);
        return toResponse(merchant);
    }

    @Transactional
    public AccountResponse updateAccount(Merchant merchant, UpdateAccountRequest req) {
        if (!PayoutInstitutions.isValid(req.institutionId())) {
            auditService.log(merchant.getId(), "ACCOUNT_UPDATE_FAILED",
                "institutionId=" + req.institutionId(), null, AuditService.MERCHANT, AuditService.FAILURE,
                PayoutInstitutions.INVALID_INSTITUTION_MESSAGE);
            throw new AppException(HttpStatus.BAD_REQUEST, PayoutInstitutions.INVALID_INSTITUTION_MESSAGE);
        }

        // Capture before-values for AML audit trail (bank detail changes are a red flag indicator)
        String detail = buildAccountChangeDetail(merchant, req);

        merchant.setBusinessName(req.businessName());
        merchant.setInstitutionId(req.institutionId());
        merchant.setPayeeName(req.payeeName());
        merchant.setSortCode(req.sortCode());
        merchant.setAccountNumber(req.accountNumber());
        merchantRepository.save(merchant);

        auditService.log(merchant.getId(), "ACCOUNT_UPDATED", detail);
        return toResponse(merchant);
    }

    @Transactional
    public void changePassword(Merchant merchant, ChangePasswordRequest req) {
        if (merchant.getPasswordHash() == null || !passwordEncoder.matches(req.currentPassword(), merchant.getPasswordHash())) {
            auditService.log(merchant.getId(), "PASSWORD_CHANGE_FAILED", null,
                null, AuditService.MERCHANT, AuditService.FAILURE, "Current password incorrect");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        merchant.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        merchantRepository.save(merchant);
        refreshTokenRepository.revokeAllForMerchant(merchant);
        auditService.log(merchant.getId(), "PASSWORD_CHANGED", null);
    }

    @Transactional
    public void deleteAccount(Merchant merchant) {
        UUID merchantId = merchant.getId();
        String email = merchant.getEmail();
        String businessName = merchant.getBusinessName();

        // Pseudonymize rather than delete: the audit trail (payment history, login
        // events, bank-detail-change history) is the fraud/AML record and must survive
        // the merchant closing their own account. Only the PII-bearing columns are
        // cleared; event_type/outcome/created_at/trace_id are kept.
        auditLogRepository.pseudonymizeByMerchantId(merchantId);
        merchantRepository.deleteById(merchantId);
        auditService.log(merchantId, "ACCOUNT_DELETED", "email=" + email + " businessName=" + businessName);
    }

    public void exportPaymentsCsv(Merchant merchant, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"marvex-export-" + merchant.getId() + ".csv\"");

        var writer = response.getWriter();
        writer.println("payment_id,amount_pence,amount_gbp,currency,status,note,created_at,updated_at");

        paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.getId(), Pageable.unpaged())
            .forEach(p -> writer.println(
                csv(p.getId()) + "," +
                p.getAmount() + "," +
                String.format("%.2f", p.getAmount() / 100.0) + "," +
                csv(p.getCurrency()) + "," +
                csv(p.getStatus()) + "," +
                csv(p.getNote()) + "," +
                csv(p.getCreatedAt()) + "," +
                csv(p.getUpdatedAt())
            ));

        auditService.log(merchant.getId(), "DATA_EXPORT", "format=CSV scope=payments");
    }

    private AccountResponse toResponse(Merchant m) {
        return new AccountResponse(m.getId(), m.getEmail(), m.getBusinessName(),
            m.getInstitutionId(), m.getPayeeName(), m.getSortCode(), m.getAccountNumber());
    }

    private String csv(Object val) {
        if (val == null) return "";
        String s = val.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    private String buildAccountChangeDetail(Merchant before, UpdateAccountRequest after) {
        StringBuilder sb = new StringBuilder();
        if (!Objects.equals(before.getSortCode(), after.sortCode()))
            sb.append("sortCode:").append(before.getSortCode()).append("->").append(after.sortCode()).append(" ");
        if (!Objects.equals(before.getAccountNumber(), after.accountNumber()))
            sb.append("accountNumber:").append(mask(before.getAccountNumber())).append("->").append(mask(after.accountNumber())).append(" ");
        if (!Objects.equals(before.getPayeeName(), after.payeeName()))
            sb.append("payeeName:").append(before.getPayeeName()).append("->").append(after.payeeName()).append(" ");
        if (!Objects.equals(before.getBusinessName(), after.businessName()))
            sb.append("businessName:").append(before.getBusinessName()).append("->").append(after.businessName()).append(" ");
        return sb.toString().trim();
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        return "****" + s.substring(s.length() - 4);
    }
}
