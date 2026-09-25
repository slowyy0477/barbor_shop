package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {
    @jakarta.persistence.Id
    private UUID customerId;
    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;
    @Column(name = "cash_available_minor", nullable = false)
    private long cashAvailableMinor;
    @Column(name = "cash_reserved_minor", nullable = false)
    private long cashReservedMinor;
    @Column(name = "promo_available_minor", nullable = false)
    private long promoAvailableMinor;
    /**
     * A first-deposit promotion is an account-level benefit, not a per-deposit
     * benefit.  This flag lives on the locked wallet row so concurrent owner
     * approvals for the same customer cannot both grant it.
     */
    @Column(name = "first_deposit_bonus_claimed", nullable = false)
    private boolean firstDepositBonusClaimed;
    @Version
    private long version;

    protected Wallet() {}
    public Wallet(UUID salonId, UUID customerId) { this.salonId = salonId; this.customerId = customerId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getSalonId() { return salonId; }
    public long getCashAvailableMinor() { return cashAvailableMinor; }
    public long getCashReservedMinor() { return cashReservedMinor; }
    public long getPromoAvailableMinor() { return promoAvailableMinor; }
    public boolean isFirstDepositBonusClaimed() { return firstDepositBonusClaimed; }
    public boolean hasClaimedFirstDepositBonus() { return firstDepositBonusClaimed; }
    /**
     * Atomically claims the first-deposit promotion in the current transaction.
     * The caller must hold the repository's pessimistic wallet lock.
     */
    public boolean claimFirstDepositBonus() {
        if (firstDepositBonusClaimed) return false;
        firstDepositBonusClaimed = true;
        return true;
    }
    public long usableMinor() { return cashAvailableMinor + promoAvailableMinor; }
    public long withdrawableMinor() { return cashAvailableMinor; }
    public void creditCash(long amount) { requirePositive(amount); cashAvailableMinor = Math.addExact(cashAvailableMinor, amount); }
    public void creditPromo(long amount) { requirePositive(amount); promoAvailableMinor = Math.addExact(promoAvailableMinor, amount); }
    public void debitCash(long amount) { requirePositive(amount); if (cashAvailableMinor < amount) throw new InsufficientBalanceException("Insufficient cash balance"); cashAvailableMinor -= amount; }
    public void debitPromo(long amount) { requirePositive(amount); if (promoAvailableMinor < amount) throw new InsufficientBalanceException("Insufficient promotional balance"); promoAvailableMinor -= amount; }
    public void reserveCash(long amount) { requirePositive(amount); if (cashAvailableMinor < amount) throw new InsufficientBalanceException("Insufficient cash balance"); cashAvailableMinor -= amount; cashReservedMinor += amount; }
    public void releaseCash(long amount) { requirePositive(amount); if (cashReservedMinor < amount) throw new IllegalStateException("Reservation exceeds reserved balance"); cashReservedMinor -= amount; cashAvailableMinor += amount; }
    public void completeReservedCash(long amount) { requirePositive(amount); if (cashReservedMinor < amount) throw new IllegalStateException("Reservation exceeds reserved balance"); cashReservedMinor -= amount; }
    public void debitForService(long amount, boolean consumePromoFirst) {
        requirePositive(amount);
        if (usableMinor() < amount) throw new InsufficientBalanceException("Insufficient usable wallet balance");
        if (consumePromoFirst) {
            long promo = Math.min(promoAvailableMinor, amount); promoAvailableMinor -= promo; cashAvailableMinor -= amount - promo;
        } else {
            long cash = Math.min(cashAvailableMinor, amount); cashAvailableMinor -= cash; promoAvailableMinor -= amount - cash;
        }
    }
    public void refund(long cash, long promo) { if (cash < 0 || promo < 0 || cash + promo == 0) throw new IllegalArgumentException("Refund must be positive"); cashAvailableMinor += cash; promoAvailableMinor += promo; }
    private static void requirePositive(long amount) { if (amount <= 0) throw new IllegalArgumentException("Amount must be positive"); }
    public static class InsufficientBalanceException extends RuntimeException { public InsufficientBalanceException(String message) { super(message); } }
}
