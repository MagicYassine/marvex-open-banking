package com.marvex.service;

import com.marvex.entity.AuditLog;
import com.marvex.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditPersisterTest {

    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AuditPersister auditPersister;

    @Test
    void persist_mapsAllFieldsOntoAuditLogEntity() {
        UUID merchantId = UUID.randomUUID();

        auditPersister.persist(merchantId, "EVENT_TYPE", "detail", "trace-1",
                "1.2.3.4", "agent", AuditService.MERCHANT, AuditService.SUCCESS, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo(merchantId);
        assertThat(saved.getEventType()).isEqualTo("EVENT_TYPE");
        assertThat(saved.getDetail()).isEqualTo("detail");
        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getIpAddress()).isEqualTo("1.2.3.4");
        assertThat(saved.getUserAgent()).isEqualTo("agent");
        assertThat(saved.getActorType()).isEqualTo(AuditService.MERCHANT);
        assertThat(saved.getOutcome()).isEqualTo(AuditService.SUCCESS);
        assertThat(saved.getFailureReason()).isNull();
    }

    @Test
    void persist_failureOutcome_setsFailureReason() {
        UUID merchantId = UUID.randomUUID();

        auditPersister.persist(merchantId, "EVENT_TYPE", "detail", "trace-1",
                "1.2.3.4", "agent", AuditService.WEBHOOK, AuditService.FAILURE, "bad signature");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getFailureReason()).isEqualTo("bad signature");
        assertThat(captor.getValue().getOutcome()).isEqualTo(AuditService.FAILURE);
    }
}
