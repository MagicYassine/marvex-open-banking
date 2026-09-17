package com.marvex.repository;

import com.marvex.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * GDPR erasure: clears the PII-bearing columns for a merchant's audit history while
     * keeping the event_type/outcome/created_at/trace_id trail intact for fraud/AML
     * purposes. audit_log rows cannot be deleted (see V1__init.sql / V5) — deleting the
     * whole audit trail on self-service account deletion previously destroyed the record
     * of exactly the actions (e.g. payout bank-detail changes) that matter most.
     */
    @Modifying
    @Query(value = "UPDATE audit_log SET detail = NULL, ip_address = NULL, user_agent = NULL, failure_reason = NULL " +
                   "WHERE merchant_id = :merchantId", nativeQuery = true)
    void pseudonymizeByMerchantId(UUID merchantId);
}
