package com.cornerchair.salon.model;

import java.util.List;

/** Validation and reward-release automation for refer-and-earn. */
public final class ReferralRules {
    private ReferralRules() {
    }

    public static boolean isSelfReferral(String referrerCustomerId, String referredCustomerId) {
        return IdentityRules.samePerson(referrerCustomerId, referredCustomerId, null, null);
    }

    /**
     * Identity-aware variant for callers that have verified phone values. This catches aliases
     * such as 03001234567, +92 300 1234567, and 0092 300 1234567 even when customer IDs differ.
     */
    public static boolean isSelfReferral(String referrerCustomerId,
                                          String referredCustomerId,
                                          String referrerPhone,
                                          String referredPhone) {
        return IdentityRules.samePerson(referrerCustomerId, referredCustomerId,
                referrerPhone, referredPhone);
    }

    public static void requireDifferentIdentity(String referrerCustomerId,
                                                String referredCustomerId,
                                                String referrerPhone,
                                                String referredPhone) {
        if (isSelfReferral(referrerCustomerId, referredCustomerId, referrerPhone, referredPhone)) {
            throw new SelfReferralException(referrerCustomerId, referredCustomerId);
        }
    }

    /** Claims a code once for a new customer and prevents multiple referrers owning that customer. */
    public static Referral claimUnique(Referral referral,
                                       String referredCustomerId,
                                       String firstEligibleBookingId,
                                       List<Referral> existing,
                                       long claimedAtMillis) {
        if (referral == null || existing == null) {
            throw new IllegalArgumentException("referral and existing referrals are required");
        }
        if (isSelfReferral(referral.getReferrerCustomerId(), referredCustomerId)) {
            throw new SelfReferralException(referral.getReferrerCustomerId(), referredCustomerId);
        }
        for (Referral other : existing) {
            if (other == null || other.getId().equals(referral.getId())) {
                continue;
            }
            if (IdentityRules.sameId(other.getReferredCustomerId(), referredCustomerId)
                    && other.getStatus() != ReferralStatus.CANCELLED
                    && other.getStatus() != ReferralStatus.INVALID) {
                throw new DuplicateReferralClaimException(referredCustomerId);
            }
        }
        return referral.claim(referredCustomerId, firstEligibleBookingId, claimedAtMillis);
    }

    /**
     * Claims a referral after comparing the verified referrer and new-customer phone identities.
     * The legacy claimUnique overload remains available for ID-only data migrations.
     */
    public static Referral claimUniqueByIdentity(Referral referral,
                                                 String referredCustomerId,
                                                 String firstEligibleBookingId,
                                                 List<Referral> existing,
                                                 long claimedAtMillis,
                                                 String referrerPhone,
                                                 String referredPhone) {
        if (referral == null) {
            throw new IllegalArgumentException("referral is required");
        }
        requireDifferentIdentity(referral.getReferrerCustomerId(), referredCustomerId,
                referrerPhone, referredPhone);
        return claimUnique(referral, referredCustomerId, firstEligibleBookingId, existing,
                claimedAtMillis);
    }

    /**
     * Releases the referrer's salon credit and returns the new customer's invoice discount. The
     * discount is deliberately not written to the wallet ledger.
     */
    public static RewardReleaseResult releaseAfterPaidFirstVisit(Referral referral,
                                                                  String referredCustomerId,
                                                                  String qualifyingBookingId,
                                                                  String completedVisitId,
                                                                  long paidAmountPkr,
                                                                  boolean visitCompleted,
                                                                  boolean isFirstPaidVisit,
                                                                  long referrerRewardsReleasedThisMonthPkr,
                                                                  long monthlyRewardCapPkr,
                                                                  WalletLedger referrerLedger,
                                                                  String rewardTransactionId,
                                                                  long releasedAtMillis,
                                                                  SalonSettings settings) {
        if (referral == null || referrerLedger == null || settings == null) {
            throw new IllegalArgumentException("referral, ledger, and settings are required");
        }
        DomainTime.requireNonBlank(referredCustomerId, "referredCustomerId");
        DomainTime.requireNonBlank(qualifyingBookingId, "qualifyingBookingId");
        DomainTime.requireNonBlank(completedVisitId, "completedVisitId");
        DomainTime.requireNonBlank(rewardTransactionId, "rewardTransactionId");
        if (ReferralRules.isSelfReferral(referral.getReferrerCustomerId(), referredCustomerId)) {
            throw new SelfReferralException(referral.getReferrerCustomerId(), referredCustomerId);
        }
        if (referral.getStatus() != ReferralStatus.CLAIMED) {
            throw new IllegalStateException("referral is not pending reward release: " + referral.getStatus());
        }
        if (!referral.getReferredCustomerId().equals(referredCustomerId)) {
            throw new IllegalArgumentException("visit customer does not match referral claim");
        }
        if (!referral.getFirstEligibleBookingId().equals(qualifyingBookingId)) {
            throw new IllegalStateException("visit is not the referral's first eligible booking");
        }
        if (!visitCompleted || !isFirstPaidVisit || paidAmountPkr <= 0) {
            throw new IllegalStateException("referral requires a completed paid first visit");
        }
        if (referrerRewardsReleasedThisMonthPkr < 0 || monthlyRewardCapPkr < 0) {
            throw new IllegalArgumentException("monthly reward totals cannot be negative");
        }
        if (monthlyRewardCapPkr > 0
                && referrerRewardsReleasedThisMonthPkr + referral.getReferrerRewardPkr()
                > monthlyRewardCapPkr) {
            throw new MonthlyReferralCapException(monthlyRewardCapPkr);
        }
        if (!referral.getSalonId().equals(settings.getSalonId())) {
            throw new IllegalArgumentException("settings belong to a different salon");
        }
        Referral released = referral.release(completedVisitId, releasedAtMillis);
        WalletLedger.WalletRewardResult reward = referrerLedger.recordReferralReward(
                rewardTransactionId,
                referral.getReferrerCustomerId(),
                referral.getSalonId(),
                referral.getReferrerRewardPkr(),
                referral.getId(),
                releasedAtMillis,
                settings);
        return new RewardReleaseResult(released, reward.getLedger(), reward.getReward(),
                referral.getNewCustomerDiscountPkr());
    }

    /**
     * Release variant for callers that have both verified phone values available at completion
     * time. Claim-time checks remain required, but this second check closes the gap where an
     * account is merged or its verified phone changes between referral claim and first visit.
     */
    public static RewardReleaseResult releaseAfterPaidFirstVisit(Referral referral,
                                                                  String referredCustomerId,
                                                                  String qualifyingBookingId,
                                                                  String completedVisitId,
                                                                  long paidAmountPkr,
                                                                  boolean visitCompleted,
                                                                  boolean isFirstPaidVisit,
                                                                  long referrerRewardsReleasedThisMonthPkr,
                                                                  long monthlyRewardCapPkr,
                                                                  WalletLedger referrerLedger,
                                                                  String rewardTransactionId,
                                                                  long releasedAtMillis,
                                                                  SalonSettings settings,
                                                                  String referrerPhone,
                                                                  String referredPhone) {
        if (referral == null) {
            throw new IllegalArgumentException("referral is required");
        }
        requireDifferentIdentity(referral.getReferrerCustomerId(), referredCustomerId,
                referrerPhone, referredPhone);
        return releaseAfterPaidFirstVisit(referral, referredCustomerId, qualifyingBookingId,
                completedVisitId, paidAmountPkr, visitCompleted, isFirstPaidVisit,
                referrerRewardsReleasedThisMonthPkr, monthlyRewardCapPkr, referrerLedger,
                rewardTransactionId, releasedAtMillis, settings);
    }

    public static final class RewardReleaseResult {
        private final Referral referral;
        private final WalletLedger referrerLedger;
        private final WalletTransaction referrerReward;
        private final long newCustomerDiscountPkr;

        private RewardReleaseResult(Referral referral,
                                    WalletLedger referrerLedger,
                                    WalletTransaction referrerReward,
                                    long newCustomerDiscountPkr) {
            this.referral = referral;
            this.referrerLedger = referrerLedger;
            this.referrerReward = referrerReward;
            this.newCustomerDiscountPkr = newCustomerDiscountPkr;
        }

        public Referral getReferral() {
            return referral;
        }

        public WalletLedger getReferrerLedger() {
            return referrerLedger;
        }

        public WalletTransaction getReferrerReward() {
            return referrerReward;
        }

        public long getNewCustomerDiscountPkr() {
            return newCustomerDiscountPkr;
        }
    }

    public static final class SelfReferralException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String referrerCustomerId;
        private final String referredCustomerId;

        public SelfReferralException(String referrerCustomerId, String referredCustomerId) {
            super("self-referral is not allowed");
            this.referrerCustomerId = referrerCustomerId;
            this.referredCustomerId = referredCustomerId;
        }

        public String getReferrerCustomerId() {
            return referrerCustomerId;
        }

        public String getReferredCustomerId() {
            return referredCustomerId;
        }
    }

    public static final class DuplicateReferralClaimException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final String referredCustomerId;

        public DuplicateReferralClaimException(String referredCustomerId) {
            super("new customer already belongs to another referral");
            this.referredCustomerId = referredCustomerId;
        }

        public String getReferredCustomerId() {
            return referredCustomerId;
        }
    }

    public static final class MonthlyReferralCapException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final long capPkr;

        public MonthlyReferralCapException(long capPkr) {
            super("monthly referral reward cap reached: " + capPkr + " PKR");
            this.capPkr = capPkr;
        }

        public long getCapPkr() {
            return capPkr;
        }
    }
}
