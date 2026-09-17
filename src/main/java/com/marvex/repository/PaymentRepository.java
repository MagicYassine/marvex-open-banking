package com.marvex.repository;

import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Page<Payment> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    @Query("SELECT p FROM Payment p WHERE p.status IN (:pending, :processing) AND p.expiresAt < :now")
    List<Payment> findExpired(@Param("now") Instant now,
                              @Param("pending") PaymentStatus pending,
                              @Param("processing") PaymentStatus processing);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.merchant.id = :merchantId AND p.status = :status")
    long sumPaidAmount(@Param("merchantId") UUID merchantId, @Param("status") PaymentStatus status);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.merchant.id = :merchantId AND p.status = :status AND p.updatedAt >= :since")
    long countPaidSince(@Param("merchantId") UUID merchantId, @Param("since") Instant since, @Param("status") PaymentStatus status);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.merchant.id = :merchantId AND p.status = :status")
    long countPaid(@Param("merchantId") UUID merchantId, @Param("status") PaymentStatus status);

    @Modifying
    @Query("UPDATE Payment p SET p.paymentAccessTokenHash = NULL, p.paymentAccessTokenExpiresAt = NULL " +
           "WHERE p.paymentAccessTokenHash IS NOT NULL AND p.paymentAccessTokenExpiresAt < :cutoff")
    int purgeExpiredPaymentTokens(@Param("cutoff") Instant cutoff);
}
