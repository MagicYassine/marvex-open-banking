package com.marvex.repository;

import com.marvex.entity.RefreshToken;
import com.marvex.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.merchant = :merchant AND r.revoked = false")
    void revokeAllForMerchant(Merchant merchant);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE (r.revoked = true OR r.expiresAt < :cutoff) AND r.expiresAt < :cutoff")
    void deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
