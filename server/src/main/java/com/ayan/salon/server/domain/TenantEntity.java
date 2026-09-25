package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class TenantEntity {
    @Id
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Column(name = "salon_id", nullable = false, updatable = false)
    protected UUID salonId;

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    protected TenantEntity() {}

    protected TenantEntity(UUID salonId) {
        this.id = UUID.randomUUID();
        this.salonId = salonId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSalonId() { return salonId; }
    public Instant getCreatedAt() { return createdAt; }
}
