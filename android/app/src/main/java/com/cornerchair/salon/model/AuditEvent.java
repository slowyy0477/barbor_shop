package com.cornerchair.salon.model;

/**
 * Append-only audit event. Snapshots are deliberately opaque strings (JSON in a real backend) so
 * this model stays free of a serialization dependency. Never overwrite an existing event.
 */
public final class AuditEvent {
    private final String id;
    private final String salonId;
    private final String actorId;
    private final AuditAction action;
    private final String entityType;
    private final String entityId;
    private final String reason;
    private final String beforeSnapshot;
    private final String afterSnapshot;
    private final String requestId;
    private final String deviceOrRequestMetadata;
    private final long createdAtMillis;

    public AuditEvent(String id,
                      String salonId,
                      String actorId,
                      AuditAction action,
                      String entityType,
                      String entityId,
                      String reason,
                      String beforeSnapshot,
                      String afterSnapshot,
                      String requestId,
                      String deviceOrRequestMetadata,
                      long createdAtMillis) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(actorId, "actorId");
        DomainTime.requireNonBlank(entityType, "entityType");
        DomainTime.requireNonBlank(entityId, "entityId");
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (requiresReason(action)) {
            DomainTime.requireNonBlank(reason, "reason");
        }
        if (createdAtMillis < 0L) {
            throw new IllegalArgumentException("createdAtMillis cannot be negative");
        }
        this.id = id;
        this.salonId = salonId;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reason = reason == null ? "" : reason;
        this.beforeSnapshot = beforeSnapshot == null ? "" : beforeSnapshot;
        this.afterSnapshot = afterSnapshot == null ? "" : afterSnapshot;
        this.requestId = requestId == null ? "" : requestId;
        this.deviceOrRequestMetadata = deviceOrRequestMetadata == null ? "" : deviceOrRequestMetadata;
        this.createdAtMillis = createdAtMillis;
    }

    private static boolean requiresReason(AuditAction action) {
        switch (action) {
            case DEPOSIT_REJECTED:
            case WITHDRAWAL_REJECTED:
            case WALLET_ADJUSTED:
            case WALLET_REVERSED:
            case ACCOUNT_SUSPENDED:
                return true;
            default:
                return false;
        }
    }

    public String getId() {
        return id;
    }

    public String getSalonId() {
        return salonId;
    }

    public String getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getReason() {
        return reason;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDeviceOrRequestMetadata() {
        return deviceOrRequestMetadata;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
