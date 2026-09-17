package com.marvex.service;

import com.marvex.dto.AccountResponse;
import com.marvex.dto.ChangePasswordRequest;
import com.marvex.dto.UpdateAccountRequest;
import com.marvex.entity.Merchant;
import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import com.marvex.repository.AuditLogRepository;
import com.marvex.repository.MerchantRepository;
import com.marvex.repository.PaymentRepository;
import com.marvex.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock MerchantRepository merchantRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock PaymentRepository paymentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AccountService accountService;

    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setEmail("merchant@test.com");
        merchant.setBusinessName("Old Biz");
        merchant.setPasswordHash("hashed-current");
        merchant.setSortCode("040004");
        merchant.setAccountNumber("12345678");
        merchant.setPayeeName("Old Payee");
        merchant.setInstitutionId("monzo_ob");
    }

    @Test
    void getAccount_returnsMappedResponseAndLogsAccess() {
        AccountResponse resp = accountService.getAccount(merchant);

        assertThat(resp.email()).isEqualTo("merchant@test.com");
        assertThat(resp.accountNumber()).isEqualTo("12345678");
        verify(auditService).log(merchant.getId(), "ACCOUNT_ACCESSED", null);
    }

    @Test
    void updateAccount_changesFields_masksAccountNumberInAuditDetail_leavesSortCodeUnmasked() {
        UpdateAccountRequest req = new UpdateAccountRequest("New Biz", "monzo_ob", "New Payee", "112233", "87654321");

        AccountResponse resp = accountService.updateAccount(merchant, req);

        assertThat(resp.businessName()).isEqualTo("New Biz");
        assertThat(merchant.getAccountNumber()).isEqualTo("87654321");
        verify(merchantRepository).save(merchant);

        var detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(merchant.getId()), eq("ACCOUNT_UPDATED"), detailCaptor.capture());
        String detail = detailCaptor.getValue();
        // Sort code is a low-sensitivity routing identifier — kept in full for the AML trail.
        assertThat(detail).contains("sortCode:040004->112233");
        // Account number is masked to last 4 digits both before and after.
        assertThat(detail).contains("accountNumber:****5678->****4321");
        assertThat(detail).doesNotContain("12345678").doesNotContain("87654321");
    }

    @Test
    void updateAccount_noChanges_producesEmptyDetail() {
        UpdateAccountRequest same = new UpdateAccountRequest(
            merchant.getBusinessName(), merchant.getInstitutionId(), merchant.getPayeeName(),
            merchant.getSortCode(), merchant.getAccountNumber());

        accountService.updateAccount(merchant, same);

        verify(auditService).log(merchant.getId(), "ACCOUNT_UPDATED", "");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsAndLogsFailure() {
        when(passwordEncoder.matches("wrong", "hashed-current")).thenReturn(false);
        ChangePasswordRequest req = new ChangePasswordRequest("wrong", "NewPassword1!");

        assertThatThrownBy(() -> accountService.changePassword(merchant, req))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("incorrect");

        verify(auditService).log(eq(merchant.getId()), eq("PASSWORD_CHANGE_FAILED"), isNull(),
            isNull(), eq(AuditService.MERCHANT), eq(AuditService.FAILURE), anyString());
        verify(merchantRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllForMerchant(any());
    }

    @Test
    void changePassword_correctCurrentPassword_updatesHashAndRevokesSessions() {
        when(passwordEncoder.matches("current", "hashed-current")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("hashed-new");
        ChangePasswordRequest req = new ChangePasswordRequest("current", "NewPassword1!");

        accountService.changePassword(merchant, req);

        assertThat(merchant.getPasswordHash()).isEqualTo("hashed-new");
        verify(merchantRepository).save(merchant);
        verify(refreshTokenRepository).revokeAllForMerchant(merchant);
        verify(auditService).log(merchant.getId(), "PASSWORD_CHANGED", null);
    }

    @Test
    void deleteAccount_pseudonymizesAuditHistoryAndDeletesMerchantBeforeLoggingDeletionEvent() {
        // Locks in the fix: audit rows must be pseudonymized (PII cleared), never deleted —
        // deleting them destroyed the fraud/AML trail. The ACCOUNT_DELETED event must be
        // logged *after* both operations, not before — logging before would race with the
        // (genuinely async) audit write, non-deterministically leaking or losing the
        // erasure record. See AccountService javadoc/comment on deleteAccount for the full
        // reasoning.
        accountService.deleteAccount(merchant);

        InOrder order = inOrder(auditLogRepository, merchantRepository, auditService);
        order.verify(auditLogRepository).pseudonymizeByMerchantId(merchant.getId());
        order.verify(merchantRepository).deleteById(merchant.getId());
        order.verify(auditService).log(eq(merchant.getId()), eq("ACCOUNT_DELETED"), contains(merchant.getEmail()));
    }

    @Test
    void exportPaymentsCsv_writesHeaderAndEscapesCommasAndQuotesInNote() throws Exception {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.setAmount(1050);
        payment.setCurrency("GBP");
        payment.setStatus(PaymentStatus.PAID);
        payment.setNote("Table 4, \"VIP\" booking");
        payment.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        payment.setUpdatedAt(Instant.parse("2026-01-01T00:05:00Z"));

        Page<Payment> page = new PageImpl<>(List.of(payment));
        when(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(eq(merchant.getId()), any()))
            .thenReturn(page);

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        accountService.exportPaymentsCsv(merchant, response);

        String csv = sw.toString();
        assertThat(csv).contains("payment_id,amount_pence,amount_gbp,currency,status,note,created_at,updated_at");
        assertThat(csv).contains("10.50");
        // A note containing a comma and a quote must be quoted, with the inner quote doubled.
        assertThat(csv).contains("\"Table 4, \"\"VIP\"\" booking\"");
        verify(response).setHeader(eq("Content-Disposition"), contains(merchant.getId().toString()));
        verify(auditService).log(merchant.getId(), "DATA_EXPORT", "format=CSV scope=payments");
    }
}
