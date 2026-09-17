package com.marvex.service;

import com.marvex.constants.MdcKeys;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String MERCHANT = "MERCHANT";
    public static final String SYSTEM   = "SYSTEM";
    public static final String WEBHOOK  = "WEBHOOK";
    public static final String SUCCESS  = "SUCCESS";
    public static final String FAILURE  = "FAILURE";

    private final AuditPersister auditPersister;

    /** Merchant-initiated success event (per-request traceId). */
    public void log(UUID merchantId, String eventType, String detail) {
        log(merchantId, eventType, detail, null, MERCHANT, SUCCESS, null);
    }

    /** Payment flow event — uses flowTraceId to correlate all steps. */
    public void log(UUID merchantId, String eventType, String detail, String flowTraceId) {
        log(merchantId, eventType, detail, flowTraceId, MERCHANT, SUCCESS, null);
    }

    /** Full control — actor, outcome, failure reason, optional flowTraceId. */
    public void log(UUID merchantId, String eventType, String detail,
                    String flowTraceId, String actorType, String outcome, String failureReason) {
        // Capture MDC on calling thread before the async handoff — MDC is thread-local
        // and won't be visible on AuditPersister's worker thread otherwise.
        String traceId   = flowTraceId != null ? flowTraceId : MDC.get(MdcKeys.TRACE_ID);
        String clientIp  = MDC.get(MdcKeys.CLIENT_IP);
        String userAgent = MDC.get(MdcKeys.USER_AGENT);
        auditPersister.persist(merchantId, eventType, detail, traceId, clientIp, userAgent, actorType, outcome, failureReason);
    }
}
