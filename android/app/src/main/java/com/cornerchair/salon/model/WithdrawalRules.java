package com.cornerchair.salon.model;

/** Withdrawal policy and reservation rules. Server persistence must apply these atomically. */
public final class WithdrawalRules {
    private WithdrawalRules() {
    }

    public static RequestResult request(Withdrawal withdrawal,
                                        WalletLedger ledger,
                                        boolean phoneVerified,
                                        boolean recentAuthentication,
                                        long minimumPkr,
                                        long maximumDailyPkr,
                                        long alreadyWithdrawnOrReservedTodayPkr,
                                        long requestedAtMillis) {
        if (withdrawal == null || ledger == null) {
            throw new IllegalArgumentException("withdrawal and ledger are required");
        }
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("only pending withdrawals can be reserved");
        }
        if (!phoneVerified || !recentAuthentication) {
            throw new WithdrawalVerificationException(phoneVerified, recentAuthentication);
        }
        if (minimumPkr < 0L || maximumDailyPkr < 0L || alreadyWithdrawnOrReservedTodayPkr < 0L) {
            throw new IllegalArgumentException("withdrawal limits cannot be negative");
        }
        if (withdrawal.getAmountPkr() < minimumPkr) {
            throw new WithdrawalLimitException("minimum", minimumPkr);
        }
        if (maximumDailyPkr > 0L
                && alreadyWithdrawnOrReservedTodayPkr + withdrawal.getAmountPkr() > maximumDailyPkr) {
            throw new WithdrawalLimitException("daily maximum", maximumDailyPkr);
        }
        WalletLedger.WalletWithdrawalResult hold = ledger.recordWithdrawalHold(
                withdrawal.getId(), withdrawal.getCustomerId(), withdrawal.getSalonId(),
                withdrawal.getAmountPkr(), requestedAtMillis);
        return new RequestResult(withdrawal, hold.getLedger(), hold.getHold());
    }

    public static ReviewResult reject(Withdrawal withdrawal,
                                      WalletLedger ledger,
                                      String actorId,
                                      String reason,
                                      long reviewedAtMillis) {
        if (withdrawal == null || ledger == null) {
            throw new IllegalArgumentException("withdrawal and ledger are required");
        }
        Withdrawal rejected = withdrawal.reject(actorId, reason, reviewedAtMillis);
        WalletLedger.WalletWithdrawalResult release = ledger.releaseWithdrawalHold(
                withdrawal.getId(), withdrawal.getCustomerId(), withdrawal.getSalonId(),
                withdrawal.getAmountPkr(), reason, reviewedAtMillis);
        return new ReviewResult(rejected, release.getLedger(), release.getRelease());
    }

    public static final class RequestResult {
        private final Withdrawal withdrawal;
        private final WalletLedger ledger;
        private final WalletTransaction hold;

        private RequestResult(Withdrawal withdrawal, WalletLedger ledger, WalletTransaction hold) {
            this.withdrawal = withdrawal;
            this.ledger = ledger;
            this.hold = hold;
        }

        public Withdrawal getWithdrawal() {
            return withdrawal;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getHold() {
            return hold;
        }
    }

    public static final class ReviewResult {
        private final Withdrawal withdrawal;
        private final WalletLedger ledger;
        private final WalletTransaction release;

        private ReviewResult(Withdrawal withdrawal,
                             WalletLedger ledger,
                             WalletTransaction release) {
            this.withdrawal = withdrawal;
            this.ledger = ledger;
            this.release = release;
        }

        public Withdrawal getWithdrawal() {
            return withdrawal;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getRelease() {
            return release;
        }
    }

    public static final class WithdrawalVerificationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private WithdrawalVerificationException(boolean phoneVerified,
                                                boolean recentAuthentication) {
            super("withdrawal verification failed: phoneVerified=" + phoneVerified
                    + ", recentAuthentication=" + recentAuthentication);
        }
    }

    public static final class WithdrawalLimitException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final String limitName;
        private final long limitPkr;

        private WithdrawalLimitException(String limitName, long limitPkr) {
            super(limitName + " withdrawal limit is " + limitPkr + " PKR");
            this.limitName = limitName;
            this.limitPkr = limitPkr;
        }

        public String getLimitName() {
            return limitName;
        }

        public long getLimitPkr() {
            return limitPkr;
        }
    }
}
