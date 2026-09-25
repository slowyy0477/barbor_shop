package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

import static com.ayan.salon.server.domain.DomainTypes.PaymentMethod;
import static com.ayan.salon.server.domain.DomainTypes.WithdrawalStatus;

@Entity
@Table(name = "withdrawals")
public class Withdrawal extends TenantEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @Column(nullable = false, length = 3)
    private String currency = "PKR";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentMethod provider;
    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;
    @JsonIgnore
    @Column(name = "destination_token", nullable = false, length = 180)
    private String destinationToken;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status = WithdrawalStatus.PENDING;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "review_reason", length = 500)
    private String reviewReason;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Version
    private long version;

    protected Withdrawal() {}
    public Withdrawal(UUID salonId, UUID customerId, long amountMinor, PaymentMethod provider, String providerCode, String destinationToken) {
        super(salonId); if (amountMinor <= 0) throw new IllegalArgumentException("Withdrawal amount must be positive");
        this.customerId = customerId; this.amountMinor = amountMinor; this.provider = provider; this.providerCode = providerCode; this.destinationToken = destinationToken;
    }
    public UUID getCustomerId() { return customerId; }
    public long getAmountMinor() { return amountMinor; }
    public PaymentMethod getProvider() { return provider; }
    public String getProviderCode() { return providerCode; }
    @JsonIgnore
    public String getDestinationToken() { return destinationToken; }
    public WithdrawalStatus getStatus() { return status; }
    public void approve(UUID actor) { transition(WithdrawalStatus.PENDING, WithdrawalStatus.APPROVED); reviewedBy = actor; reviewedAt = Instant.now(); }
    public void complete(UUID actor) { transition(WithdrawalStatus.APPROVED, WithdrawalStatus.COMPLETED); reviewedBy = actor; reviewedAt = Instant.now(); }
    public void reject(UUID actor, String reason) { if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Rejection reason required"); if (status != WithdrawalStatus.PENDING && status != WithdrawalStatus.APPROVED) throw new IllegalStateException("Withdrawal cannot be rejected"); status = WithdrawalStatus.REJECTED; reviewedBy = actor; reviewReason = reason; reviewedAt = Instant.now(); }
    private void transition(WithdrawalStatus expected, WithdrawalStatus next) { if (status != expected) throw new IllegalStateException("Invalid withdrawal state: " + status); status = next; }
}
