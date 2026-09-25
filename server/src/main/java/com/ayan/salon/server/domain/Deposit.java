package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import static com.ayan.salon.server.domain.DomainTypes.DepositStatus;
import static com.ayan.salon.server.domain.DomainTypes.PaymentMethod;

@Entity
@Table(name = "deposits")
public class Deposit extends TenantEntity {
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PaymentMethod provider;
    @Column(name = "provider_code", nullable = false, length = 32) private String providerCode;
    @Column(name = "provider_reference", nullable = false, length = 180) private String providerReference;
    @Column(name = "proof_uri", length = 500) private String proofUri;
    @Column(name = "bonus_snapshot_minor", nullable = false) private long bonusSnapshotMinor;
    /** Actual amount granted after the account-level first-deposit check. */
    @Column(name = "bonus_granted_minor", nullable = false) private long bonusGrantedMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DepositStatus status = DepositStatus.PENDING;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "review_reason", length = 500) private String reviewReason;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Version private long version;
    protected Deposit() {}
    public Deposit(UUID salonId, UUID customerId, long amountMinor, PaymentMethod provider, String providerCode, String providerReference, String proofUri, long bonusSnapshotMinor) {
        super(salonId); if (amountMinor <= 0 || bonusSnapshotMinor < 0) throw new IllegalArgumentException("Invalid deposit amount");
        this.customerId = customerId; this.amountMinor = amountMinor; this.currency = "PKR"; this.provider = provider; this.providerCode = providerCode; this.providerReference = providerReference; this.proofUri = proofUri; this.bonusSnapshotMinor = bonusSnapshotMinor;
    }
    public UUID getCustomerId() { return customerId; }
    public long getAmountMinor() { return amountMinor; }
    public long getBonusSnapshotMinor() { return bonusSnapshotMinor; }
    public long getBonusGrantedMinor() { return bonusGrantedMinor; }
    public PaymentMethod getProvider() { return provider; }
    public String getProviderCode() { return providerCode; }
    public String getProviderReference() { return providerReference; }
    public DepositStatus getStatus() { return status; }
    public void recordGrantedBonus(long amountMinor) {
        if (status != DepositStatus.APPROVED) throw new IllegalStateException("Deposit must be approved before recording a bonus");
        if (amountMinor < 0 || amountMinor > bonusSnapshotMinor) throw new IllegalArgumentException("Granted bonus is outside the deposit snapshot");
        bonusGrantedMinor = amountMinor;
    }
    public void approve(UUID actor) { ensurePending(); status = DepositStatus.APPROVED; reviewedBy = actor; reviewedAt = Instant.now(); }
    public void reject(UUID actor, String reason) { ensurePending(); if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Rejection reason required"); status = DepositStatus.REJECTED; reviewedBy = actor; reviewReason = reason; reviewedAt = Instant.now(); }
    private void ensurePending() { if (status != DepositStatus.PENDING) throw new IllegalStateException("Deposit already reviewed"); }
}
