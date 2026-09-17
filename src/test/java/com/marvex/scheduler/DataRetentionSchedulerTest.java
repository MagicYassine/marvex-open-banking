package com.marvex.scheduler;

import com.marvex.repository.MerchantRepository;
import com.marvex.repository.PaymentRepository;
import com.marvex.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionSchedulerTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock PaymentRepository paymentRepository;
    @InjectMocks DataRetentionScheduler scheduler;

    @Test
    void purgeExpiredData_refreshTokenCutoff_is30DaysBeforeNow() {
        Instant before = Instant.now();
        scheduler.purgeExpiredData();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpiredBefore(captor.capture());

        Instant cutoff = captor.getValue();
        assertThat(cutoff).isBetween(before.minus(30, ChronoUnit.DAYS).minusSeconds(2),
                after.minus(30, ChronoUnit.DAYS).plusSeconds(2));
    }

    @Test
    void purgeExpiredData_verificationPasswordResetAndPaymentTokenCutoffs_useCurrentInstant() {
        when(merchantRepository.purgeExpiredVerificationTokens(any())).thenReturn(2);
        when(merchantRepository.purgeExpiredPasswordResetTokens(any())).thenReturn(1);
        when(paymentRepository.purgeExpiredPaymentTokens(any())).thenReturn(3);

        Instant before = Instant.now();
        scheduler.purgeExpiredData();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> verifCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> resetCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> paymentCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(merchantRepository).purgeExpiredVerificationTokens(verifCaptor.capture());
        verify(merchantRepository).purgeExpiredPasswordResetTokens(resetCaptor.capture());
        verify(paymentRepository).purgeExpiredPaymentTokens(paymentCaptor.capture());

        assertThat(verifCaptor.getValue()).isBetween(before, after);
        assertThat(resetCaptor.getValue()).isBetween(before, after);
        assertThat(paymentCaptor.getValue()).isBetween(before, after);
        assertThat(verifCaptor.getValue()).isEqualTo(resetCaptor.getValue()).isEqualTo(paymentCaptor.getValue());
    }

    @Test
    void purgeExpiredData_zeroRowsAffected_doesNotThrow() {
        when(merchantRepository.purgeExpiredVerificationTokens(any())).thenReturn(0);
        when(merchantRepository.purgeExpiredPasswordResetTokens(any())).thenReturn(0);
        when(paymentRepository.purgeExpiredPaymentTokens(any())).thenReturn(0);

        assertThatCode(scheduler::purgeExpiredData).doesNotThrowAnyException();
    }
}
