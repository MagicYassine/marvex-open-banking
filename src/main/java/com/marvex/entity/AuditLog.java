package com.marvex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "detail")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "trace_id", length = 36)
    private String traceId;

    /** MERCHANT | SYSTEM | WEBHOOK */
    @Column(name = "actor_type", length = 16)
    private String actorType;

    /** SUCCESS | FAILURE */
    @Column(name = "outcome", length = 8)
    private String outcome;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
