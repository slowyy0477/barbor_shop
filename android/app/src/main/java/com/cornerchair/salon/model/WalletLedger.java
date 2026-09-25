package com.cornerchair.salon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable wallet ledger for one customer at one salon. Every mutating-looking method returns
 * a new ledger, preserving the complete transaction history for audits and reversals.
 */
public final class WalletLedger {
    private final List<WalletTransaction> transactions;
    /** Empty strings mean this ledger is unbound and will acquire its scope on first append. */
    private final String scopedCustomerId;
    private final String scopedSalonId;

    public WalletLedger() {
        this.transactions = Collections.emptyList();
        this.scopedCustomerId = "";
        this.scopedSalonId = "";
    }

    /** Creates an explicitly scoped empty ledger for one customer at one salon. */
    public WalletLedger(String customerId, String salonId) {
        DomainTime.requireNonBlank(customerId, "customerId");
        DomainTime.requireNonBlank(salonId, "salonId");
        this.transactions = Collections.emptyList();
        this.scopedCustomerId = customerId;
        this.scopedSalonId = salonId;
    }

    public WalletLedger(List<WalletTransaction> transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("transactions are required");
        }
        ArrayList<WalletTransaction> copy = new ArrayList<WalletTransaction>(transactions);
        Set<String> ids = new HashSet<String>();
        String customerId = "";
        String salonId = "";
        for (WalletTransaction transaction : copy) {
            if (transaction == null) {
                throw new IllegalArgumentException("transactions cannot contain null");
            }
            if (!ids.add(transaction.getId())) {
                throw new IllegalArgumentException("duplicate wallet transaction id: " + transaction.getId());
            }
            if (customerId.length() == 0) {
                customerId = transaction.getCustomerId();
                salonId = transaction.getSalonId();
            } else if (!customerId.equals(transaction.getCustomerId())
                    || !salonId.equals(transaction.getSalonId())) {
                throw new LedgerScopeException(customerId, salonId,
                        transaction.getCustomerId(), transaction.getSalonId());
            }
        }
        this.transactions = Collections.unmodifiableList(copy);
        this.scopedCustomerId = customerId;
        this.scopedSalonId = salonId;
    }

    public List<WalletTransaction> getTransactions() {
        return transactions;
    }

    /** Returns the customer scope, or an empty string for a new unbound ledger. */
    public String getCustomerId() {
        return scopedCustomerId;
    }

    /** Returns the salon scope, or an empty string for a new unbound ledger. */
    public String getSalonId() {
        return scopedSalonId;
    }

    public boolean isScoped() {
        return scopedCustomerId.length() > 0;
    }

    public long getPaidBalancePkr() {
        return getBalanceIgnoringExpiry(WalletBucket.PAID);
    }

    public long getBonusBalancePkr() {
        return getBalanceIgnoringExpiry(WalletBucket.BONUS);
    }

    public long getTotalBalancePkr() {
        return getPaidBalancePkr() + getBonusBalancePkr();
    }

    public long getPaidBalanceAt(long nowMillis) {
        return getBalanceAt(WalletBucket.PAID, nowMillis);
    }

    public long getBonusBalanceAt(long nowMillis) {
        return getBalanceAt(WalletBucket.BONUS, nowMillis);
    }

    public long getTotalBalanceAt(long nowMillis) {
        return getPaidBalanceAt(nowMillis) + getBonusBalanceAt(nowMillis);
    }

    private long getBalanceAt(WalletBucket bucket, long nowMillis) {
        // Allocate debits against the credit lots that were usable when the debit happened.
        // This prevents an expired credit from disappearing while its historical debit remains
        // as a negative balance. The list is append-only and therefore already deterministic.
        ArrayList<CreditLot> lots = new ArrayList<CreditLot>();
        long unallocatedDebit = 0L;
        for (WalletTransaction transaction : transactions) {
            if (nowMillis != Long.MAX_VALUE && transaction.getCreatedAtMillis() > nowMillis) {
                continue;
            }
            if (transaction.getBucket() != bucket) {
                continue;
            }
            if (transaction.getDeltaPkr() > 0) {
                lots.add(new CreditLot(transaction.getDeltaPkr(), transaction.getExpiresAtMillis(),
                        transaction.getCreatedAtMillis()));
            } else {
                long remainingDebit = -transaction.getDeltaPkr();
                for (CreditLot lot : lots) {
                    if (remainingDebit == 0L) {
                        break;
                    }
                    if (lot.remainingPkr <= 0L || !lot.isUsableAt(transaction.getCreatedAtMillis())) {
                        continue;
                    }
                    long consumed = Math.min(remainingDebit, lot.remainingPkr);
                    lot.remainingPkr -= consumed;
                    remainingDebit -= consumed;
                }
                unallocatedDebit += remainingDebit;
            }
        }
        long balance = -unallocatedDebit;
        for (CreditLot lot : lots) {
            if (lot.remainingPkr > 0L && lot.isUsableAt(nowMillis)) {
                balance += lot.remainingPkr;
            }
        }
        return balance;
    }

    /** Raw ledger balance, useful for owner history screens; expiry-aware balances use get*At. */
    private long getBalanceIgnoringExpiry(WalletBucket bucket) {
        long balance = 0L;
        for (WalletTransaction transaction : transactions) {
            if (transaction.getBucket() == bucket) {
                balance += transaction.getDeltaPkr();
            }
        }
        return balance;
    }

    /**
     * Records one confirmed payment as separate paid-credit and bonus-credit entries. The sample
     * configuration turns PKR 500 into PKR 500 paid + PKR 50 bonus = PKR 550 total.
     */
    public WalletPaymentResult recordConfirmedPayment(String paymentId,
                                                      String customerId,
                                                      String salonId,
                                                      long paidAmountPkr,
                                                      String reason,
                                                      long createdAtMillis,
                                                      SalonSettings settings) {
        DomainTime.requireNonBlank(paymentId, "paymentId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(reason, "reason");
        if (paidAmountPkr <= 0) {
            throw new IllegalArgumentException("paidAmountPkr must be positive");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings are required");
        }
        if (!salonId.equals(settings.getSalonId())) {
            throw new LedgerScopeException(customerId, salonId, customerId, settings.getSalonId());
        }
        WalletTransaction existingPaid = null;
        WalletTransaction existingBonus = null;
        for (WalletTransaction transaction : transactions) {
            if (paymentId.equals(transaction.getReferenceId())) {
                if (transaction.getType() == WalletTransactionType.PAID_CREDIT) {
                    if (!customerId.equals(transaction.getCustomerId())
                            || !salonId.equals(transaction.getSalonId())
                            || transaction.getDeltaPkr() != paidAmountPkr) {
                        throw new IllegalStateException("payment reference was already used with different details");
                    }
                    existingPaid = transaction;
                } else if (transaction.getType() == WalletTransactionType.BONUS_CREDIT) {
                    existingBonus = transaction;
                }
            }
        }
        if (existingPaid != null) {
            // A retried payment callback must not create a second top-up.
            return new WalletPaymentResult(this, existingPaid, existingBonus);
        }
        long bonusAmountPkr = settings.calculateBonusPkr(paidAmountPkr);
        long expiresAtMillis = settings.creditExpiryAt(createdAtMillis);
        WalletTransaction paid = new WalletTransaction(
                paymentId + "-paid",
                customerId,
                salonId,
                WalletBucket.PAID,
                WalletTransactionType.PAID_CREDIT,
                paidAmountPkr,
                reason,
                paymentId,
                createdAtMillis,
                expiresAtMillis,
                null);
        WalletLedger next = append(paid);
        WalletTransaction bonus = null;
        if (bonusAmountPkr > 0) {
            bonus = new WalletTransaction(
                    paymentId + "-bonus",
                    customerId,
                    salonId,
                    WalletBucket.BONUS,
                    WalletTransactionType.BONUS_CREDIT,
                    bonusAmountPkr,
                    "Bonus for " + reason,
                    paymentId,
                    createdAtMillis,
                    expiresAtMillis,
                    null);
            next = next.append(bonus);
        }
        return new WalletPaymentResult(next, paid, bonus);
    }

    /** Adds a salon-credit reward to the bonus bucket after a paid referral visit. */
    public WalletRewardResult recordReferralReward(String rewardId,
                                                   String customerId,
                                                   String salonId,
                                                   long rewardPkr,
                                                   String referralId,
                                                   long createdAtMillis,
                                                   SalonSettings settings) {
        DomainTime.requireNonBlank(rewardId, "rewardId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(referralId, "referralId");
        if (rewardPkr <= 0) {
            throw new IllegalArgumentException("rewardPkr must be positive");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings are required");
        }
        if (!salonId.equals(settings.getSalonId())) {
            throw new LedgerScopeException(customerId, salonId, customerId, settings.getSalonId());
        }
        for (WalletTransaction transaction : transactions) {
            if (rewardId.equals(transaction.getId())) {
                if (transaction.getType() != WalletTransactionType.REFERRAL_REWARD
                        || transaction.getBucket() != WalletBucket.BONUS
                        || transaction.getDeltaPkr() != rewardPkr
                        || !referralId.equals(transaction.getReferenceId())
                        || !customerId.equals(transaction.getCustomerId())
                        || !salonId.equals(transaction.getSalonId())) {
                    throw new IllegalStateException("reward transaction id was already used with different details");
                }
                return new WalletRewardResult(this, transaction);
            }
            if (transaction.getType() == WalletTransactionType.REFERRAL_REWARD
                    && referralId.equals(transaction.getReferenceId())) {
                if (!customerId.equals(transaction.getCustomerId())
                        || !salonId.equals(transaction.getSalonId())
                        || transaction.getDeltaPkr() != rewardPkr) {
                    throw new IllegalStateException("referral reward reference was already used with different details");
                }
                // Retry of the same referral release with a regenerated request id.
                return new WalletRewardResult(this, transaction);
            }
        }
        WalletTransaction reward = new WalletTransaction(
                rewardId,
                customerId,
                salonId,
                WalletBucket.BONUS,
                WalletTransactionType.REFERRAL_REWARD,
                rewardPkr,
                "Referral reward",
                referralId,
                createdAtMillis,
                settings.creditExpiryAt(createdAtMillis),
                null);
        return new WalletRewardResult(append(reward), reward);
    }

    /**
     * Debits paid credit first and then bonus credit. This makes the split visible while allowing
     * a customer to spend the combined usable balance.
     */
    public WalletDebitResult debit(String debitId,
                                   String customerId,
                                   String salonId,
                                   long amountPkr,
                                   String reason,
                                   String referenceId,
                                   long createdAtMillis) {
        DomainTime.requireNonBlank(debitId, "debitId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(reason, "reason");
        if (amountPkr <= 0) {
            throw new IllegalArgumentException("amountPkr must be positive");
        }
        WalletTransaction existingPaid = find(debitId + "-paid");
        WalletTransaction existingBonus = find(debitId + "-bonus");
        if (existingPaid != null || existingBonus != null) {
            validateDebitRetry(existingPaid, WalletBucket.PAID, customerId, salonId,
                    reason, referenceId);
            validateDebitRetry(existingBonus, WalletBucket.BONUS, customerId, salonId,
                    reason, referenceId);
            long existingAmount = (existingPaid == null ? 0L : -existingPaid.getDeltaPkr())
                    + (existingBonus == null ? 0L : -existingBonus.getDeltaPkr());
            if (existingAmount != amountPkr) {
                throw new IllegalStateException("debit reference was already used for another amount");
            }
            return new WalletDebitResult(this, existingPaid, existingBonus);
        }
        long paidAvailable = getPaidBalanceAt(createdAtMillis);
        long bonusAvailable = getBonusBalanceAt(createdAtMillis);
        if (amountPkr > paidAvailable + bonusAvailable) {
            throw new InsufficientWalletCreditException(amountPkr, paidAvailable + bonusAvailable);
        }

        long paidDebit = Math.min(amountPkr, paidAvailable);
        long bonusDebit = amountPkr - paidDebit;
        WalletLedger next = this;
        WalletTransaction paidEntry = null;
        WalletTransaction bonusEntry = null;
        if (paidDebit > 0) {
            paidEntry = new WalletTransaction(
                    debitId + "-paid",
                    customerId,
                    salonId,
                    WalletBucket.PAID,
                    WalletTransactionType.DEBIT,
                    -paidDebit,
                    reason,
                    referenceId,
                    createdAtMillis,
                    0L,
                    null);
            next = next.append(paidEntry);
        }
        if (bonusDebit > 0) {
            bonusEntry = new WalletTransaction(
                    debitId + "-bonus",
                    customerId,
                    salonId,
                    WalletBucket.BONUS,
                    WalletTransactionType.DEBIT,
                    -bonusDebit,
                    reason,
                    referenceId,
                    createdAtMillis,
                    0L,
                    null);
            next = next.append(bonusEntry);
        }
        return new WalletDebitResult(next, paidEntry, bonusEntry);
    }

    private void validateDebitRetry(WalletTransaction transaction,
                                    WalletBucket expectedBucket,
                                    String customerId,
                                    String salonId,
                                    String reason,
                                    String referenceId) {
        if (transaction == null) {
            return;
        }
        if (transaction.getType() != WalletTransactionType.DEBIT
                || transaction.getBucket() != expectedBucket
                || transaction.getDeltaPkr() >= 0L
                || !customerId.equals(transaction.getCustomerId())
                || !salonId.equals(transaction.getSalonId())
                || !reason.equals(transaction.getReason())
                || !sameNullable(referenceId, transaction.getReferenceId())) {
            throw new IllegalStateException("debit reference was already used with different details");
        }
    }

    /**
     * Atomically applies a salon service payment and reports the cash/promotional split. When
     * promotionalFirst is true (the Ayan default), referral/bonus credit is consumed before cash.
     * Each component is a separate immutable SERVICE_PAYMENT row, and paymentId makes retries
     * idempotent. A production repository must commit the read/append under a database lock.
     */
    public ServicePaymentResult recordServicePayment(String paymentId,
                                                     String customerId,
                                                     String salonId,
                                                     long amountPkr,
                                                     String reason,
                                                     String referenceId,
                                                     boolean promotionalFirst,
                                                     long createdAtMillis) {
        DomainTime.requireNonBlank(paymentId, "paymentId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(reason, "reason");
        if (amountPkr <= 0L) {
            throw new IllegalArgumentException("service amount must be positive");
        }
        WalletTransaction existingCash = null;
        WalletTransaction existingPromotional = null;
        long existingAmount = 0L;
        for (WalletTransaction transaction : transactions) {
            if (paymentId.equals(transaction.getReferenceId())
                    && transaction.getType() == WalletTransactionType.SERVICE_PAYMENT) {
                if (!customerId.equals(transaction.getCustomerId())
                        || !salonId.equals(transaction.getSalonId())
                        || transaction.getDeltaPkr() >= 0L) {
                    throw new IllegalStateException("service payment reference was already used with different details");
                }
                long component = -transaction.getDeltaPkr();
                existingAmount += component;
                if (transaction.getBucket() == WalletBucket.PAID) {
                    existingCash = transaction;
                } else if (transaction.getBucket() == WalletBucket.BONUS) {
                    existingPromotional = transaction;
                }
            }
        }
        if (existingAmount > 0L) {
            if (existingAmount != amountPkr) {
                throw new IllegalStateException("service payment reference was already used for another amount");
            }
            return new ServicePaymentResult(this, existingCash, existingPromotional);
        }

        long paidAvailable = getPaidBalanceAt(createdAtMillis);
        long promotionalAvailable = getBonusBalanceAt(createdAtMillis);
        if (amountPkr > paidAvailable + promotionalAvailable) {
            throw new InsufficientWalletCreditException(amountPkr, paidAvailable + promotionalAvailable);
        }
        long promotionalComponent = promotionalFirst
                ? Math.min(amountPkr, promotionalAvailable)
                : Math.max(0L, amountPkr - paidAvailable);
        long cashComponent = amountPkr - promotionalComponent;
        WalletLedger next = this;
        WalletTransaction cashEntry = null;
        WalletTransaction promotionalEntry = null;
        if (cashComponent > 0L) {
            cashEntry = new WalletTransaction(
                    paymentId + "-cash",
                    customerId,
                    salonId,
                    WalletBucket.PAID,
                    WalletTransactionType.SERVICE_PAYMENT,
                    -cashComponent,
                    reason,
                    paymentId,
                    createdAtMillis,
                    0L,
                    null);
            next = next.append(cashEntry);
        }
        if (promotionalComponent > 0L) {
            promotionalEntry = new WalletTransaction(
                    paymentId + "-promotional",
                    customerId,
                    salonId,
                    WalletBucket.BONUS,
                    WalletTransactionType.SERVICE_PAYMENT,
                    -promotionalComponent,
                    reason,
                    paymentId,
                    createdAtMillis,
                    0L,
                    null);
            next = next.append(promotionalEntry);
        }
        return new ServicePaymentResult(next, cashEntry, promotionalEntry);
    }

    public WalletLedger recordRefund(String refundId,
                                     String customerId,
                                     String salonId,
                                     WalletBucket bucket,
                                     long amountPkr,
                                     String reason,
                                     String referenceId,
                                     long createdAtMillis) {
        DomainTime.requireNonBlank(refundId, "refundId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(reason, "reason");
        if (bucket == null || amountPkr <= 0) {
            throw new IllegalArgumentException("refund bucket and positive amount are required");
        }
        WalletTransaction existing = find(refundId);
        if (existing != null) {
            if (existing.getType() != WalletTransactionType.REFUND
                    || existing.getBucket() != bucket
                    || existing.getDeltaPkr() != amountPkr
                    || !customerId.equals(existing.getCustomerId())
                    || !salonId.equals(existing.getSalonId())
                    || !reason.equals(existing.getReason())
                    || !sameNullable(referenceId, existing.getReferenceId())) {
                throw new IllegalStateException("refund id was already used with different details");
            }
            return this;
        }
        return append(new WalletTransaction(
                refundId,
                customerId,
                salonId,
                bucket,
                WalletTransactionType.REFUND,
                amountPkr,
                reason,
                referenceId,
                createdAtMillis,
                0L,
                null));
    }

    /**
     * Places a cash-only hold for a withdrawal. The hold is an append-only negative ledger event
     * and therefore immediately lowers the cash available to a second withdrawal request. It is
     * deliberately separate from a normal service debit so owner reports can distinguish money
     * reserved for payout from money spent in the salon.
     *
     * <p>Production persistence must commit this operation under a customer-wallet transaction
     * (and a unique reference constraint) so two concurrent requests cannot both use one balance.
     */
    public WalletWithdrawalResult recordWithdrawalHold(String withdrawalId,
                                                       String customerId,
                                                       String salonId,
                                                       long amountPkr,
                                                       long createdAtMillis) {
        DomainTime.requireNonBlank(withdrawalId, "withdrawalId");
        requireScope(customerId, salonId);
        if (amountPkr <= 0L) {
            throw new IllegalArgumentException("withdrawal amount must be positive");
        }
        WalletTransaction existing = findWithdrawalEvent(withdrawalId, WalletTransactionType.WITHDRAWAL);
        if (existing != null) {
            validateWithdrawalDetails(existing, customerId, salonId, amountPkr);
            return new WalletWithdrawalResult(this, existing, null);
        }
        long availableCash = getPaidBalanceAt(createdAtMillis);
        if (amountPkr > availableCash) {
            throw new InsufficientCashException(amountPkr, availableCash);
        }
        WalletTransaction hold = new WalletTransaction(
                withdrawalId + "-hold",
                customerId,
                salonId,
                WalletBucket.PAID,
                WalletTransactionType.WITHDRAWAL,
                -amountPkr,
                "Withdrawal reserved",
                withdrawalId,
                createdAtMillis,
                0L,
                null);
        return new WalletWithdrawalResult(append(hold), hold, null);
    }

    /**
     * Releases a previously reserved cash hold after a withdrawal is rejected/cancelled. A
     * release is itself immutable and idempotent; the original hold is never edited or deleted.
     */
    public WalletWithdrawalResult releaseWithdrawalHold(String withdrawalId,
                                                         String customerId,
                                                         String salonId,
                                                        long amountPkr,
                                                        String reason,
                                                        long createdAtMillis) {
        DomainTime.requireNonBlank(withdrawalId, "withdrawalId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(reason, "reason");
        if (amountPkr <= 0L) {
            throw new IllegalArgumentException("withdrawal amount must be positive");
        }
        WalletTransaction hold = findWithdrawalEvent(withdrawalId, WalletTransactionType.WITHDRAWAL);
        if (hold == null) {
            throw new IllegalStateException("withdrawal hold not found: " + withdrawalId);
        }
        validateWithdrawalDetails(hold, customerId, salonId, amountPkr);
        WalletTransaction existingRelease = findWithdrawalEvent(withdrawalId,
                WalletTransactionType.WITHDRAWAL_RELEASE);
        if (existingRelease != null) {
            validateWithdrawalReleaseDetails(existingRelease, customerId, salonId,
                    amountPkr, reason, hold.getId());
            return new WalletWithdrawalResult(this, hold, existingRelease);
        }
        WalletTransaction release = new WalletTransaction(
                withdrawalId + "-release",
                customerId,
                salonId,
                WalletBucket.PAID,
                WalletTransactionType.WITHDRAWAL_RELEASE,
                amountPkr,
                reason,
                withdrawalId,
                createdAtMillis,
                0L,
                hold.getId());
        return new WalletWithdrawalResult(append(release), hold, release);
    }

    /** Returns true when a withdrawal hold has been recorded for the given request. */
    public boolean hasWithdrawalHold(String withdrawalId) {
        return withdrawalId != null
                && findWithdrawalEvent(withdrawalId, WalletTransactionType.WITHDRAWAL) != null;
    }

    private WalletTransaction findWithdrawalEvent(String withdrawalId, WalletTransactionType type) {
        for (WalletTransaction transaction : transactions) {
            if (type == transaction.getType() && withdrawalId.equals(transaction.getReferenceId())) {
                return transaction;
            }
        }
        return null;
    }

    private static void validateWithdrawalDetails(WalletTransaction transaction,
                                                   String customerId,
                                                   String salonId,
                                                   long amountPkr) {
        if (!customerId.equals(transaction.getCustomerId())
                || !salonId.equals(transaction.getSalonId())
                || -transaction.getDeltaPkr() != amountPkr) {
            throw new IllegalStateException("withdrawal reference was already used with different details");
        }
    }

    private static void validateWithdrawalReleaseDetails(WalletTransaction transaction,
                                                         String customerId,
                                                         String salonId,
                                                         long amountPkr,
                                                         String reason,
                                                         String holdId) {
        if (transaction.getType() != WalletTransactionType.WITHDRAWAL_RELEASE
                || transaction.getBucket() != WalletBucket.PAID
                || transaction.getDeltaPkr() != amountPkr
                || !customerId.equals(transaction.getCustomerId())
                || !salonId.equals(transaction.getSalonId())
                || !reason.equals(transaction.getReason())
                || !holdId.equals(transaction.getReversesTransactionId())
                || !holdId.equals(transaction.getReferenceId())) {
            throw new IllegalStateException(
                    "withdrawal release reference was already used with different details");
        }
    }

    /** Manual adjustments are allowed only with a reason and remain in the immutable history. */
    public WalletLedger manualAdjustment(String adjustmentId,
                                         String customerId,
                                         String salonId,
                                         WalletBucket bucket,
                                         long signedAmountPkr,
                                         String reason,
                                         long createdAtMillis,
                                         SalonSettings settings) {
        DomainTime.requireNonBlank(adjustmentId, "adjustmentId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(reason, "reason");
        if (bucket == null || signedAmountPkr == 0) {
            throw new IllegalArgumentException("adjustment bucket and non-zero amount are required");
        }
        long expiry = signedAmountPkr > 0 && settings != null
                ? settings.creditExpiryAt(createdAtMillis)
                : 0L;
        if (settings != null && !salonId.equals(settings.getSalonId())) {
            throw new LedgerScopeException(customerId, salonId, customerId, settings.getSalonId());
        }
        WalletTransaction existing = find(adjustmentId);
        if (existing != null) {
            if (existing.getType() != WalletTransactionType.MANUAL_ADJUSTMENT
                    || existing.getBucket() != bucket
                    || existing.getDeltaPkr() != signedAmountPkr
                    || !customerId.equals(existing.getCustomerId())
                    || !salonId.equals(existing.getSalonId())
                    || !reason.equals(existing.getReason())
                    || !adjustmentId.equals(existing.getReferenceId())) {
                throw new IllegalStateException("adjustment id was already used with different details");
            }
            return this;
        }
        return append(new WalletTransaction(
                adjustmentId,
                customerId,
                salonId,
                bucket,
                WalletTransactionType.MANUAL_ADJUSTMENT,
                signedAmountPkr,
                reason,
                adjustmentId,
                createdAtMillis,
                expiry,
                null));
    }

    /** Creates an opposite transaction while retaining the original entry. */
    public WalletLedger reverse(String reversalId,
                                String customerId,
                                String salonId,
                                String transactionId,
                                String reason,
                                long createdAtMillis) {
        DomainTime.requireNonBlank(reversalId, "reversalId");
        requireScope(customerId, salonId);
        DomainTime.requireNonBlank(transactionId, "transactionId");
        DomainTime.requireNonBlank(reason, "reason");
        WalletTransaction original = find(transactionId);
        if (original == null) {
            throw new IllegalArgumentException("transaction not found: " + transactionId);
        }
        if (!customerId.equals(original.getCustomerId()) || !salonId.equals(original.getSalonId())) {
            throw new LedgerScopeException(original.getCustomerId(), original.getSalonId(),
                    customerId, salonId);
        }
        WalletTransaction existingReversal = find(reversalId);
        if (existingReversal != null) {
            if (existingReversal.getType() != WalletTransactionType.REVERSAL
                    || existingReversal.getBucket() != original.getBucket()
                    || existingReversal.getDeltaPkr() != -original.getDeltaPkr()
                    || !transactionId.equals(existingReversal.getReversesTransactionId())
                    || !reason.equals(existingReversal.getReason())
                    || !customerId.equals(existingReversal.getCustomerId())
                    || !salonId.equals(existingReversal.getSalonId())) {
                throw new IllegalStateException("reversal id was already used with different details");
            }
            return this;
        }
        for (WalletTransaction transaction : transactions) {
            if (transactionId.equals(transaction.getReversesTransactionId())) {
                throw new IllegalStateException("transaction already reversed: " + transactionId);
            }
        }
        long expiry = original.isCredit() ? 0L : original.getExpiresAtMillis();
        return append(new WalletTransaction(
                reversalId,
                customerId,
                salonId,
                original.getBucket(),
                WalletTransactionType.REVERSAL,
                -original.getDeltaPkr(),
                reason,
                original.getReferenceId(),
                createdAtMillis,
                expiry,
                transactionId));
    }

    private WalletTransaction find(String id) {
        for (WalletTransaction transaction : transactions) {
            if (id.equals(transaction.getId())) {
                return transaction;
            }
        }
        return null;
    }

    private void requireScope(String customerId, String salonId) {
        DomainTime.requireNonBlank(customerId, "customerId");
        DomainTime.requireNonBlank(salonId, "salonId");
        if (isScoped() && (!scopedCustomerId.equals(customerId) || !scopedSalonId.equals(salonId))) {
            throw new LedgerScopeException(scopedCustomerId, scopedSalonId, customerId, salonId);
        }
    }

    private static boolean sameNullable(String left, String right) {
        String normalizedLeft = left == null ? "" : left;
        String normalizedRight = right == null ? "" : right;
        return normalizedLeft.equals(normalizedRight);
    }

    private WalletLedger append(WalletTransaction transaction) {
        for (WalletTransaction existing : transactions) {
            if (existing.getId().equals(transaction.getId())) {
                throw new IllegalStateException("duplicate wallet transaction id: " + transaction.getId());
            }
        }
        ArrayList<WalletTransaction> next = new ArrayList<WalletTransaction>(transactions);
        next.add(transaction);
        return new WalletLedger(next);
    }

    private static final class CreditLot {
        private long remainingPkr;
        private final long expiresAtMillis;
        private final long createdAtMillis;

        private CreditLot(long amountPkr, long expiresAtMillis, long createdAtMillis) {
            this.remainingPkr = amountPkr;
            this.expiresAtMillis = expiresAtMillis;
            this.createdAtMillis = createdAtMillis;
        }

        private boolean isUsableAt(long atMillis) {
            return atMillis >= createdAtMillis
                    && (expiresAtMillis == 0L || atMillis < expiresAtMillis);
        }
    }

    public static final class WalletPaymentResult {
        private final WalletLedger ledger;
        private final WalletTransaction paidCredit;
        private final WalletTransaction bonusCredit;

        private WalletPaymentResult(WalletLedger ledger,
                                    WalletTransaction paidCredit,
                                    WalletTransaction bonusCredit) {
            this.ledger = ledger;
            this.paidCredit = paidCredit;
            this.bonusCredit = bonusCredit;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getPaidCredit() {
            return paidCredit;
        }

        public WalletTransaction getBonusCredit() {
            return bonusCredit;
        }
    }

    public static final class WalletDebitResult {
        private final WalletLedger ledger;
        private final WalletTransaction paidDebit;
        private final WalletTransaction bonusDebit;

        private WalletDebitResult(WalletLedger ledger,
                                  WalletTransaction paidDebit,
                                  WalletTransaction bonusDebit) {
            this.ledger = ledger;
            this.paidDebit = paidDebit;
            this.bonusDebit = bonusDebit;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getPaidDebit() {
            return paidDebit;
        }

        public WalletTransaction getBonusDebit() {
            return bonusDebit;
        }

        public long getAmountPkr() {
            long paid = paidDebit == null ? 0L : -paidDebit.getDeltaPkr();
            long bonus = bonusDebit == null ? 0L : -bonusDebit.getDeltaPkr();
            return paid + bonus;
        }
    }

    public static final class ServicePaymentResult {
        private final WalletLedger ledger;
        private final WalletTransaction cashComponent;
        private final WalletTransaction promotionalComponent;

        private ServicePaymentResult(WalletLedger ledger,
                                     WalletTransaction cashComponent,
                                     WalletTransaction promotionalComponent) {
            this.ledger = ledger;
            this.cashComponent = cashComponent;
            this.promotionalComponent = promotionalComponent;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getCashComponent() {
            return cashComponent;
        }

        public WalletTransaction getPromotionalComponent() {
            return promotionalComponent;
        }

        public long getCashComponentPkr() {
            return cashComponent == null ? 0L : -cashComponent.getDeltaPkr();
        }

        public long getPromotionalComponentPkr() {
            return promotionalComponent == null ? 0L : -promotionalComponent.getDeltaPkr();
        }

        public long getTotalPkr() {
            return getCashComponentPkr() + getPromotionalComponentPkr();
        }
    }

    public static final class WalletRewardResult {
        private final WalletLedger ledger;
        private final WalletTransaction reward;

        private WalletRewardResult(WalletLedger ledger, WalletTransaction reward) {
            this.ledger = ledger;
            this.reward = reward;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getReward() {
            return reward;
        }
    }

    public static final class WalletWithdrawalResult {
        private final WalletLedger ledger;
        private final WalletTransaction hold;
        private final WalletTransaction release;

        private WalletWithdrawalResult(WalletLedger ledger,
                                       WalletTransaction hold,
                                       WalletTransaction release) {
            this.ledger = ledger;
            this.hold = hold;
            this.release = release;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletTransaction getHold() {
            return hold;
        }

        public WalletTransaction getRelease() {
            return release;
        }
    }

    public static final class InsufficientWalletCreditException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final long requestedPkr;
        private final long availablePkr;

        private InsufficientWalletCreditException(long requestedPkr, long availablePkr) {
            super("wallet balance is " + availablePkr + " PKR; requested " + requestedPkr + " PKR");
            this.requestedPkr = requestedPkr;
            this.availablePkr = availablePkr;
        }

        public long getRequestedPkr() {
            return requestedPkr;
        }

        public long getAvailablePkr() {
            return availablePkr;
        }
    }

    /** Raised when an operation attempts to mix customer or salon records in one ledger. */
    public static final class LedgerScopeException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String expectedCustomerId;
        private final String expectedSalonId;
        private final String actualCustomerId;
        private final String actualSalonId;

        private LedgerScopeException(String expectedCustomerId,
                                     String expectedSalonId,
                                     String actualCustomerId,
                                     String actualSalonId) {
            super("wallet ledger is scoped to customer " + expectedCustomerId + " at salon "
                    + expectedSalonId + "; received customer " + actualCustomerId + " at salon "
                    + actualSalonId);
            this.expectedCustomerId = expectedCustomerId;
            this.expectedSalonId = expectedSalonId;
            this.actualCustomerId = actualCustomerId;
            this.actualSalonId = actualSalonId;
        }

        public String getExpectedCustomerId() {
            return expectedCustomerId;
        }

        public String getExpectedSalonId() {
            return expectedSalonId;
        }

        public String getActualCustomerId() {
            return actualCustomerId;
        }

        public String getActualSalonId() {
            return actualSalonId;
        }
    }

    public static final class InsufficientCashException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final long requestedPkr;
        private final long availableCashPkr;

        private InsufficientCashException(long requestedPkr, long availableCashPkr) {
            super("withdrawable cash is " + availableCashPkr + " PKR; requested "
                    + requestedPkr + " PKR");
            this.requestedPkr = requestedPkr;
            this.availableCashPkr = availableCashPkr;
        }

        public long getRequestedPkr() {
            return requestedPkr;
        }

        public long getAvailableCashPkr() {
            return availableCashPkr;
        }
    }
}
