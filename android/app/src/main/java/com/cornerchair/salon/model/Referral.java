package com.cornerchair.salon.model;

/** Immutable referral state. Rewards remain pending until the first paid visit is completed. */
public final class Referral {
    private final String id;
    private final String salonId;
    private final String referrerCustomerId;
    private final String referredCustomerId;
    private final String code;
    private final long referrerRewardPkr;
    private final long newCustomerDiscountPkr;
    private final ReferralStatus status;
    private final String firstEligibleBookingId;
    private final String completedVisitId;
    private final String rejectionReason;
    private final long createdAtMillis;
    private final long updatedAtMillis;

    private Referral(String id,
                     String salonId,
                     String referrerCustomerId,
                     String referredCustomerId,
                     String code,
                     long referrerRewardPkr,
                     long newCustomerDiscountPkr,
                     ReferralStatus status,
                     String firstEligibleBookingId,
                     String completedVisitId,
                     String rejectionReason,
                     long createdAtMillis,
                     long updatedAtMillis) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(referrerCustomerId, "referrerCustomerId");
        DomainTime.requireNonBlank(code, "code");
        if (IdentityRules.sameId(referrerCustomerId, referredCustomerId)) {
            throw new IllegalArgumentException("a customer cannot refer themselves");
        }
        if (referrerRewardPkr < 0 || newCustomerDiscountPkr < 0 || status == null) {
            throw new IllegalArgumentException("invalid referral reward or status");
        }
        this.id = id;
        this.salonId = salonId;
        this.referrerCustomerId = referrerCustomerId;
        this.referredCustomerId = referredCustomerId == null ? "" : referredCustomerId;
        this.code = code;
        this.referrerRewardPkr = referrerRewardPkr;
        this.newCustomerDiscountPkr = newCustomerDiscountPkr;
        this.status = status;
        this.firstEligibleBookingId = firstEligibleBookingId == null ? "" : firstEligibleBookingId;
        this.completedVisitId = completedVisitId == null ? "" : completedVisitId;
        this.rejectionReason = rejectionReason == null ? "" : rejectionReason;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
    }

    public static Referral create(String id,
                                  String salonId,
                                  String referrerCustomerId,
                                  String code,
                                  long referrerRewardPkr,
                                  long newCustomerDiscountPkr,
                                  long createdAtMillis) {
        return new Referral(id, salonId, referrerCustomerId, null, code, referrerRewardPkr,
                newCustomerDiscountPkr, ReferralStatus.PENDING, null, null, null,
                createdAtMillis, createdAtMillis);
    }

    public String getId() {
        return id;
    }

    public String getSalonId() {
        return salonId;
    }

    public String getReferrerCustomerId() {
        return referrerCustomerId;
    }

    public String getReferredCustomerId() {
        return referredCustomerId;
    }

    public String getCode() {
        return code;
    }

    public long getReferrerRewardPkr() {
        return referrerRewardPkr;
    }

    public long getNewCustomerDiscountPkr() {
        return newCustomerDiscountPkr;
    }

    public ReferralStatus getStatus() {
        return status;
    }

    public String getFirstEligibleBookingId() {
        return firstEligibleBookingId;
    }

    public String getCompletedVisitId() {
        return completedVisitId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public boolean isRewardPending() {
        return status == ReferralStatus.PENDING || status == ReferralStatus.CLAIMED;
    }

    public Referral claim(String newCustomerId, String firstBookingId, long atMillis) {
        return claim(newCustomerId, firstBookingId, atMillis, null, null);
    }

    /** Claims with verified phone identities when customer IDs alone are not sufficient. */
    public Referral claim(String newCustomerId,
                          String firstBookingId,
                          long atMillis,
                          String referrerPhone,
                          String referredPhone) {
        DomainTime.requireNonBlank(newCustomerId, "newCustomerId");
        DomainTime.requireNonBlank(firstBookingId, "firstBookingId");
        if (ReferralRules.isSelfReferral(referrerCustomerId, newCustomerId,
                referrerPhone, referredPhone)) {
            throw new ReferralRules.SelfReferralException(referrerCustomerId, newCustomerId);
        }
        if (status != ReferralStatus.PENDING) {
            throw new IllegalStateException("referral cannot be claimed from " + status);
        }
        return new Referral(id, salonId, referrerCustomerId, newCustomerId, code,
                referrerRewardPkr, newCustomerDiscountPkr, ReferralStatus.CLAIMED,
                firstBookingId, null, null, createdAtMillis, atMillis);
    }

    public Referral release(String completedVisitId, long atMillis) {
        DomainTime.requireNonBlank(completedVisitId, "completedVisitId");
        if (status != ReferralStatus.CLAIMED) {
            throw new IllegalStateException("referral rewards require a claimed referral; state is " + status);
        }
        return new Referral(id, salonId, referrerCustomerId, referredCustomerId, code,
                referrerRewardPkr, newCustomerDiscountPkr, ReferralStatus.RELEASED,
                firstEligibleBookingId, completedVisitId, null, createdAtMillis, atMillis);
    }

    public Referral cancel(String reason, long atMillis) {
        DomainTime.requireNonBlank(reason, "reason");
        if (!isRewardPending()) {
            throw new IllegalStateException("only pending referrals can be cancelled");
        }
        return new Referral(id, salonId, referrerCustomerId, referredCustomerId, code,
                referrerRewardPkr, newCustomerDiscountPkr, ReferralStatus.CANCELLED,
                firstEligibleBookingId, null, reason, createdAtMillis, atMillis);
    }

    public Referral invalidate(String reason, long atMillis) {
        DomainTime.requireNonBlank(reason, "reason");
        if (status == ReferralStatus.RELEASED) {
            throw new IllegalStateException("released referrals cannot be invalidated");
        }
        return new Referral(id, salonId, referrerCustomerId, referredCustomerId, code,
                referrerRewardPkr, newCustomerDiscountPkr, ReferralStatus.INVALID,
                firstEligibleBookingId, completedVisitId, reason, createdAtMillis, atMillis);
    }
}
