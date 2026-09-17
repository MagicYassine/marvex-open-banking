package com.marvex.scheduler;

import com.marvex.repository.MerchantRepository;
import com.marvex.repository.PaymentRepository;
import com.marvex.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentRepository paymentRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredData() {
        Instant now = Instant.now();

        // Refresh tokens: delete rows expired more than 30 days ago
        Instant refreshCutoff = now.minus(30, ChronoUnit.DAYS);
        refreshTokenRepository.deleteExpiredBefore(refreshCutoff);
        log.info("[DataRetentionScheduler] purged refresh tokens expired before {}", refreshCutoff);

        // Verification tokens: null out expired tokens (column on Merchant)
        int verif = merchantRepository.purgeExpiredVerificationTokens(now);
        if (verif > 0) log.info("[DataRetentionScheduler] nulled {} expired verification tokens", verif);

        // Password reset tokens: null out expired tokens
        int reset = merchantRepository.purgeExpiredPasswordResetTokens(now);
        if (reset > 0) log.info("[DataRetentionScheduler] nulled {} expired password reset tokens", reset);

        // Payment access token hashes: null out expired hashes
        int paymentTokens = paymentRepository.purgeExpiredPaymentTokens(now);
        if (paymentTokens > 0) log.info("[DataRetentionScheduler] nulled {} expired payment access token hashes", paymentTokens);
    }
}
