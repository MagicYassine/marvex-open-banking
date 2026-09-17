package com.marvex.repository;

import com.marvex.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByEmail(String email);
    Optional<Merchant> findByVerificationToken(String token);
    Optional<Merchant> findByPasswordResetToken(String token);

    @Modifying
    @Query("UPDATE Merchant m SET m.verificationToken = NULL, m.verificationTokenExpiresAt = NULL " +
           "WHERE m.verificationToken IS NOT NULL AND m.verificationTokenExpiresAt < :cutoff")
    int purgeExpiredVerificationTokens(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE Merchant m SET m.passwordResetToken = NULL, m.passwordResetTokenExpiresAt = NULL " +
           "WHERE m.passwordResetToken IS NOT NULL AND m.passwordResetTokenExpiresAt < :cutoff")
    int purgeExpiredPasswordResetTokens(@Param("cutoff") Instant cutoff);
}
