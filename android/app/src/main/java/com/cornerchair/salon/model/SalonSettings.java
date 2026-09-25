package com.cornerchair.salon.model;

/**
 * Versioned business settings. Keeping these values in one immutable object lets an owner
 * change prices and reward rules later without changing the domain code.
 */
public final class SalonSettings {
    private final String salonId;
    private final String currencyCode;
    private final int walletBonusPercent;
    private final int walletExpiryDays;
    private final int reminderCycleDays;
    private final long referrerRewardPkr;
    private final long newCustomerDiscountPkr;

    public SalonSettings(String salonId,
                         String currencyCode,
                         int walletBonusPercent,
                         int walletExpiryDays,
                         int reminderCycleDays,
                         long referrerRewardPkr,
                         long newCustomerDiscountPkr) {
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(currencyCode, "currencyCode");
        if (walletBonusPercent < 0 || walletBonusPercent > 100) {
            throw new IllegalArgumentException("walletBonusPercent must be between 0 and 100");
        }
        if (walletExpiryDays < 0 || reminderCycleDays <= 0) {
            throw new IllegalArgumentException("expiry cannot be negative and reminder cycle must be positive");
        }
        if (referrerRewardPkr < 0 || newCustomerDiscountPkr < 0) {
            throw new IllegalArgumentException("referral rewards cannot be negative");
        }
        this.salonId = salonId;
        this.currencyCode = currencyCode;
        this.walletBonusPercent = walletBonusPercent;
        this.walletExpiryDays = walletExpiryDays;
        this.reminderCycleDays = reminderCycleDays;
        this.referrerRewardPkr = referrerRewardPkr;
        this.newCustomerDiscountPkr = newCustomerDiscountPkr;
    }

    /**
     * Ayan Beauty Salon defaults, expressed in PKR. The referrer receives PKR 100 promotional
     * credit and a new customer receives PKR 100 off the first qualifying visit. Both values are
     * editable by the owner and historical rewards retain their original snapshots.
     * Historical transactions retain their original values.
     */
    public static SalonSettings samplePkr(String salonId) {
        return new SalonSettings(salonId, "PKR", 10, 180, 25, 100L, 100L);
    }

    public String getSalonId() {
        return salonId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public int getWalletBonusPercent() {
        return walletBonusPercent;
    }

    public int getWalletExpiryDays() {
        return walletExpiryDays;
    }

    public int getReminderCycleDays() {
        return reminderCycleDays;
    }

    public long getReferrerRewardPkr() {
        return referrerRewardPkr;
    }

    public long getNewCustomerDiscountPkr() {
        return newCustomerDiscountPkr;
    }

    public long calculateBonusPkr(long paidAmountPkr) {
        if (paidAmountPkr < 0) {
            throw new IllegalArgumentException("paid amount cannot be negative");
        }
        return (paidAmountPkr * walletBonusPercent) / 100L;
    }

    public long creditExpiryAt(long createdAtMillis) {
        return walletExpiryDays == 0
                ? 0L
                : DomainTime.plusDays(createdAtMillis, walletExpiryDays);
    }
}
