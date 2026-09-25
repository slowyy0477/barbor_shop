package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import static com.ayan.salon.server.domain.DomainTypes.LedgerType;
import static com.ayan.salon.server.domain.DomainTypes.LedgerDirection;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction extends TenantEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LedgerType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LedgerDirection direction;
    @Column(name = "cash_amount_minor", nullable = false)
    private long cashAmountMinor;
    @Column(name = "promo_amount_minor", nullable = false)
    private long promoAmountMinor;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "reference_id", nullable = false, length = 120)
    private String referenceId;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false)
    private Instant occurredAt;

    protected WalletTransaction() {}
    public WalletTransaction(UUID salonId, UUID customerId, LedgerType type, long cashAmountMinor, long promoAmountMinor,
                             String currency, String status, String referenceId, UUID createdBy, String reason) {
        this(salonId, customerId, type, inferDirection(type), cashAmountMinor, promoAmountMinor, currency, status, referenceId, createdBy, reason);
    }
    public WalletTransaction(UUID salonId, UUID customerId, LedgerType type, LedgerDirection direction, long cashAmountMinor, long promoAmountMinor,
                             String currency, String status, String referenceId, UUID createdBy, String reason) {
        super(salonId);
        if (cashAmountMinor < 0 || promoAmountMinor < 0 || cashAmountMinor + promoAmountMinor <= 0) throw new IllegalArgumentException("Ledger amount must be positive");
        this.customerId = customerId; this.type = type; this.direction = direction; this.cashAmountMinor = cashAmountMinor; this.promoAmountMinor = promoAmountMinor;
        this.currency = currency; this.status = status; this.referenceId = referenceId; this.createdBy = createdBy; this.reason = reason; this.occurredAt = Instant.now();
    }
    public UUID getCustomerId() { return customerId; }
    public LedgerType getType() { return type; }
    public LedgerDirection getDirection() { return direction; }
    public long getCashAmountMinor() { return cashAmountMinor; }
    public long getPromoAmountMinor() { return promoAmountMinor; }
    public String getReferenceId() { return referenceId; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
    private static LedgerDirection inferDirection(LedgerType type) {
        return switch (type) {
            case SERVICE_PAYMENT, WITHDRAWAL_COMPLETE -> LedgerDirection.DEBIT;
            case WITHDRAWAL_RESERVE -> LedgerDirection.RESERVE;
            case WITHDRAWAL_RELEASE -> LedgerDirection.RELEASE;
            default -> LedgerDirection.CREDIT;
        };
    }
}
