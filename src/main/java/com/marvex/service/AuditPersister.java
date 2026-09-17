package com.marvex.service;

import com.marvex.entity.AuditLog;
import com.marvex.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Performs the actual audit_log INSERT off the request thread.
 *
 * Kept in a separate bean (rather than a private/protected method on AuditService)
 * because Spring's @Async only intercepts calls made through the proxy — a method
 * called on `this` from within the same class bypasses the proxy entirely and runs
 * synchronously, silently defeating @Async. See MockWebhookEventListener for the
 * analogous @TransactionalEventListener case.
 */
@Component
@RequiredArgsConstructor
public class AuditPersister {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void persist(UUID merchantId, String eventType, String detail,
                         String traceId, String clientIp, String userAgent,
                         String actorType, String outcome, String failureReason) {
        AuditLog entry = new AuditLog();
        entry.setMerchantId(merchantId);
        entry.setEventType(eventType);
        entry.setDetail(detail);
        entry.setTraceId(traceId);
        entry.setIpAddress(clientIp);
        entry.setUserAgent(userAgent);
        entry.setActorType(actorType);
        entry.setOutcome(outcome);
        entry.setFailureReason(failureReason);
        auditLogRepository.save(entry);
    }
}
