package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends TenantEntity {
    @Column(name = "actor_id") private UUID actorId;
    @Column(nullable = false, length = 80) private String action;
    @Column(name = "target_type", nullable = false, length = 80) private String targetType;
    @Column(name = "target_id") private UUID targetId;
    @Column(length = 4000) private String details;
    @Column(nullable = false) private Instant occurredAt;
    protected AuditLog() {}
    public AuditLog(UUID salonId, UUID actorId, String action, String targetType, UUID targetId, String details) { super(salonId); this.actorId = actorId; this.action = action; this.targetType = targetType; this.targetId = targetId; this.details = details; this.occurredAt = Instant.now(); }
    public String getAction() { return action; }
}
