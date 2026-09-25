package com.cornerchair.salon.model;

/**
 * Immutable cash-withdrawal request. Only the paid/cash wallet bucket is eligible; promotional
 * referral/bonus credit can never be withdrawn. Store a token or encrypted destination reference,
 * not a provider PIN or secret, in a production persistence layer.
 */
public final class Withdrawal {
    private final String id;
    private final String salonId;
    private final String customerId;
    private final long amountPkr;
    private final PaymentProvider provider;
    private final String destinationReference;
    private final WithdrawalStatus status;
    private final long requestedAtMillis;
    private final long updatedAtMillis;
    private final String reviewedBy;
    private final String reviewReason;

    private Withdrawal(String id,
                       String salonId,
                       String customerId,
                       long amountPkr,
                       PaymentProvider provider,
                       String destinationReference,
                       WithdrawalStatus status,
                       long requestedAtMillis,
                       long updatedAtMillis,
                       String reviewedBy,
                       String reviewReason) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(customerId, "customerId");
        if (amountPkr <= 0L || provider == null || status == null) {
            throw new IllegalArgumentException("positive amount, provider, and status are required");
        }
        DomainTime.requireNonBlank(destinationReference, "destinationReference");
        if (requestedAtMillis < 0L || updatedAtMillis < requestedAtMillis) {
            throw new IllegalArgumentException("invalid withdrawal timestamps");
        }
        this.id = id;
        this.salonId = salonId;
        this.customerId = customerId;
        this.amountPkr = amountPkr;
        this.provider = provider;
        this.destinationReference = destinationReference.trim();
        this.status = status;
        this.requestedAtMillis = requestedAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.reviewedBy = reviewedBy == null ? "" : reviewedBy;
        this.reviewReason = reviewReason == null ? "" : reviewReason;
    }

    public static Withdrawal request(String id,
                                     String salonId,
                                     String customerId,
                                     long amountPkr,
                                     PaymentProvider provider,
                                     String destinationReference,
                                     long requestedAtMillis) {
        return new Withdrawal(id, salonId, customerId, amountPkr, provider, destinationReference,
                WithdrawalStatus.PENDING, requestedAtMillis, requestedAtMillis, null, null);
    }

    public String getId() {
        return id;
    }

    public String getSalonId() {
        return salonId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public long getAmountPkr() {
        return amountPkr;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getDestinationReference() {
        return destinationReference;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public long getRequestedAtMillis() {
        return requestedAtMillis;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public Withdrawal approve(String actorId, long atMillis) {
        DomainTime.requireNonBlank(actorId, "actorId");
        if (status == WithdrawalStatus.APPROVED || status == WithdrawalStatus.COMPLETED) {
            return this;
        }
        if (status != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("withdrawal cannot be approved from " + status);
        }
        return copy(WithdrawalStatus.APPROVED, actorId, "Approved for payout", atMillis);
    }

    public Withdrawal complete(String actorId, long atMillis) {
        DomainTime.requireNonBlank(actorId, "actorId");
        if (status == WithdrawalStatus.COMPLETED) {
            return this;
        }
        if (status != WithdrawalStatus.APPROVED) {
            throw new IllegalStateException("withdrawal must be approved before completion");
        }
        return copy(WithdrawalStatus.COMPLETED, actorId, "Payout sent", atMillis);
    }

    public Withdrawal reject(String actorId, String reason, long atMillis) {
        DomainTime.requireNonBlank(actorId, "actorId");
        DomainTime.requireNonBlank(reason, "reason");
        if (status == WithdrawalStatus.REJECTED) {
            return this;
        }
        if (status != WithdrawalStatus.PENDING && status != WithdrawalStatus.APPROVED) {
            throw new IllegalStateException("withdrawal cannot be rejected from " + status);
        }
        return copy(WithdrawalStatus.REJECTED, actorId, reason, atMillis);
    }

    public Withdrawal cancel(String reason, long atMillis) {
        DomainTime.requireNonBlank(reason, "reason");
        if (status == WithdrawalStatus.CANCELLED) {
            return this;
        }
        if (status != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("withdrawal cannot be cancelled from " + status);
        }
        return copy(WithdrawalStatus.CANCELLED, "", reason, atMillis);
    }

    private Withdrawal copy(WithdrawalStatus nextStatus,
                            String actorId,
                            String reason,
                            long atMillis) {
        if (atMillis < updatedAtMillis) {
            throw new IllegalArgumentException("withdrawal timestamps cannot move backwards");
        }
        return new Withdrawal(id, salonId, customerId, amountPkr, provider, destinationReference,
                nextStatus, requestedAtMillis, atMillis, actorId, reason);
    }
}
