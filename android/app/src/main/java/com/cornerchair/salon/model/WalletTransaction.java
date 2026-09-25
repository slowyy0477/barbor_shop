package com.cornerchair.salon.model;

/**
 * Immutable append-only wallet ledger entry. Amounts are signed deltas in whole PKR: credits
 * are positive and debits are negative.
 */
public final class WalletTransaction {
    private final String id;
    private final String customerId;
    private final String salonId;
    private final WalletBucket bucket;
    private final WalletTransactionType type;
    private final long deltaPkr;
    private final String reason;
    private final String referenceId;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final String reversesTransactionId;

    public WalletTransaction(String id,
                             String customerId,
                             String salonId,
                             WalletBucket bucket,
                             WalletTransactionType type,
                             long deltaPkr,
                             String reason,
                             String referenceId,
                             long createdAtMillis,
                             long expiresAtMillis,
                             String reversesTransactionId) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(customerId, "customerId");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(reason, "reason");
        if (bucket == null || type == null) {
            throw new IllegalArgumentException("bucket and type are required");
        }
        if (deltaPkr == 0) {
            throw new IllegalArgumentException("transaction amount cannot be zero");
        }
        if (expiresAtMillis < 0 || (expiresAtMillis > 0 && expiresAtMillis <= createdAtMillis)) {
            throw new IllegalArgumentException("expiresAtMillis must be zero or after createdAtMillis");
        }
        this.id = id;
        this.customerId = customerId;
        this.salonId = salonId;
        this.bucket = bucket;
        this.type = type;
        this.deltaPkr = deltaPkr;
        this.reason = reason;
        this.referenceId = referenceId == null ? "" : referenceId;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.reversesTransactionId = reversesTransactionId == null ? "" : reversesTransactionId;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getSalonId() {
        return salonId;
    }

    public WalletBucket getBucket() {
        return bucket;
    }

    public WalletTransactionType getType() {
        return type;
    }

    public long getDeltaPkr() {
        return deltaPkr;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public String getReversesTransactionId() {
        return reversesTransactionId;
    }

    public boolean isCredit() {
        return deltaPkr > 0;
    }

    public boolean isExpiredAt(long nowMillis) {
        return isCredit() && expiresAtMillis > 0 && nowMillis >= expiresAtMillis;
    }

    @Override
    public String toString() {
        return "WalletTransaction{" + id + ", " + bucket + ", " + deltaPkr + " PKR, " + type + "}";
    }
}
