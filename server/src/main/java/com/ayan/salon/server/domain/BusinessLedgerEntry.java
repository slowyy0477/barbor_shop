package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_ledger")
public class BusinessLedgerEntry extends TenantEntity {
    @Column(nullable = false, length = 32) private String type;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "PKR";
    @Column(name = "reference_id", nullable = false, length = 120) private String referenceId;
    @Column(length = 500) private String reason;
    @Column(nullable = false) private Instant occurredAt;
    protected BusinessLedgerEntry() {}
    public BusinessLedgerEntry(UUID salonId, String type, long amountMinor, String referenceId, String reason) { super(salonId); if (amountMinor <= 0) throw new IllegalArgumentException("Amount must be positive"); this.type = type; this.amountMinor = amountMinor; this.referenceId = referenceId; this.reason = reason; this.occurredAt = Instant.now(); }
}
