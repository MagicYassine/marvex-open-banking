package com.marvex.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditPersister auditPersister;
    @InjectMocks AuditService auditService;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void threeArgOverload_defaultsToMerchantSuccess_usesMdcTraceId_noFailureReason() {
        MDC.put("traceId", "mdc-trace-1");
        MDC.put("clientIp", "1.2.3.4");
        MDC.put("userAgent", "agent-x");
        UUID merchantId = UUID.randomUUID();

        auditService.log(merchantId, "PAYMENT_CREATED", "detail-1");

        verify(auditPersister).persist(merchantId, "PAYMENT_CREATED", "detail-1",
                "mdc-trace-1", "1.2.3.4", "agent-x", AuditService.MERCHANT, AuditService.SUCCESS, null);
    }

    @Test
    void fourArgOverload_prefersExplicitFlowTraceIdOverMdc_defaultsToMerchantSuccess() {
        MDC.put("traceId", "mdc-trace-2");
        UUID merchantId = UUID.randomUUID();

        auditService.log(merchantId, "PAYMENT_FLOW_STEP", "detail-2", "explicit-flow-trace");

        verify(auditPersister).persist(merchantId, "PAYMENT_FLOW_STEP", "detail-2",
                "explicit-flow-trace", null, null, AuditService.MERCHANT, AuditService.SUCCESS, null);
    }

    @Test
    void fullOverload_passesThroughActorOutcomeAndFailureReason() {
        UUID merchantId = UUID.randomUUID();

        auditService.log(merchantId, "WEBHOOK_FAILED", "detail-3", "flow-3",
                AuditService.WEBHOOK, AuditService.FAILURE, "signature mismatch");

        verify(auditPersister).persist(merchantId, "WEBHOOK_FAILED", "detail-3",
                "flow-3", null, null, AuditService.WEBHOOK, AuditService.FAILURE, "signature mismatch");
    }

    @Test
    void fullOverload_nullFlowTraceId_fallsBackToMdcTraceId() {
        MDC.put("traceId", "mdc-trace-4");
        UUID merchantId = UUID.randomUUID();

        auditService.log(merchantId, "SYSTEM_EVENT", "detail-4", null,
                AuditService.SYSTEM, AuditService.SUCCESS, null);

        verify(auditPersister).persist(merchantId, "SYSTEM_EVENT", "detail-4",
                "mdc-trace-4", null, null, AuditService.SYSTEM, AuditService.SUCCESS, null);
    }
}
