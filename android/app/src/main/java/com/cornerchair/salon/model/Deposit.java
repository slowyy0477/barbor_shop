package com.cornerchair.salon.model;

/**
 * Customer-submitted manual deposit claim. The proof URI is evidence only; an owner must verify
 * the provider account and approve the claim before a wallet ledger entry is created.
 */
public final class Deposit {
    private final String id;
    private final String salonId;
    private final String customerId;
    private final long amountPkr;
    private final PaymentProvider provider;
    private final String providerReference;
    private final String proofUri;
    private final DepositStatus status;
    private final long submittedAtMillis;
    private final long updatedAtMillis;
    private final String reviewedBy;
    private final String reviewReason;

    private Deposit(String id,
                    String salonId,
                    String customerId,
                    long amountPkr,
                    PaymentProvider provider,
                    String providerReference,
                    String proofUri,
                    DepositStatus status,
                    long submittedAtMillis,
                    long updatedAtMillis,
                    String reviewedBy,
                    String reviewReason) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(customerId, "customerId");
        if (amountPkr <= 0L || provider == null || status == null) {
            throw new IllegalArgumentException("positive amount, provider, and status are required");
        }
        if (submittedAtMillis < 0L || updatedAtMillis < submittedAtMillis) {
            throw new IllegalArgumentException("invalid deposit timestamps");
        }
        this.id = id;
        this.salonId = salonId;
        this.customerId = customerId;
        this.amountPkr = amountPkr;
        this.provider = provider;
        this.providerReference = providerReference == null ? "" : providerReference.trim();
        this.proofUri = proofUri == null ? "" : proofUri.trim();
        this.status = status;
        this.submittedAtMillis = submittedAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.reviewedBy = reviewedBy == null ? "" : reviewedBy;
        this.reviewReason = reviewReason == null ? "" : reviewReason;
    }

    public static Deposit submit(String id,
                                 String salonId,
                                 String customerId,
                                 long amountPkr,
                                 PaymentProvider provider,
                                 String providerReference,
                                 String proofUri,
                                 long submittedAtMillis) {
        return new Deposit(id, salonId, customerId, amountPkr, provider, providerReference,
                proofUri, DepositStatus.PENDING_VERIFICATION, submittedAtMillis,
                submittedAtMillis, null, null);
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

    public String getProviderReference() {
        return providerReference;
    }

    public String getProofUri() {
        return proofUri;
    }

    public DepositStatus getStatus() {
        return status;
    }

    public long getSubmittedAtMillis() {
        return submittedAtMillis;
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

    public Deposit approve(String actorId, long atMillis) {
        DomainTime.requireNonBlank(actorId, "actorId");
        if (status == DepositStatus.APPROVED) {
            return this;
        }
        if (status != DepositStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("deposit cannot be approved from " + status);
        }
        return copy(DepositStatus.APPROVED, atMillis, actorId, "Verified against provider account");
    }

    public Deposit reject(String actorId, String reason, long atMillis) {
        DomainTime.requireNonBlank(actorId, "actorId");
        DomainTime.requireNonBlank(reason, "reason");
        if (status == DepositStatus.REJECTED) {
            return this;
        }
        if (status != DepositStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("deposit cannot be rejected from " + status);
        }
        return copy(DepositStatus.REJECTED, atMillis, actorId, reason);
    }

    public Deposit cancel(String reason, long atMillis) {
        DomainTime.requireNonBlank(reason, "reason");
        if (status == DepositStatus.CANCELLED) {
            return this;
        }
        if (status != DepositStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("deposit cannot be cancelled from " + status);
        }
        return copy(DepositStatus.CANCELLED, atMillis, "", reason);
    }

    private Deposit copy(DepositStatus nextStatus,
                         long atMillis,
                         String actorId,
                         String reason) {
        if (atMillis < updatedAtMillis) {
            throw new IllegalArgumentException("deposit timestamps cannot move backwards");
        }
        return new Deposit(id, salonId, customerId, amountPkr, provider, providerReference, proofUri,
                nextStatus, submittedAtMillis, atMillis, actorId, reason);
    }
}
