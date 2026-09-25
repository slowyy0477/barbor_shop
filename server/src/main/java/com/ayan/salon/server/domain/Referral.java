package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import static com.ayan.salon.server.domain.DomainTypes.ReferralStatus;

@Entity
@Table(name = "referrals")
public class Referral extends TenantEntity {
    @Column(name = "referrer_customer_id", nullable = false)
    private UUID referrerCustomerId;
    @Column(name = "referred_customer_id", nullable = false)
    private UUID referredCustomerId;
    @Column(nullable = false, length = 32)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReferralStatus status = ReferralStatus.REGISTERED;
    @Column(name = "referrer_reward_minor", nullable = false)
    private long referrerRewardMinor;
    @Column(name = "new_customer_discount_minor", nullable = false)
    private long newCustomerDiscountMinor;
    @Column(name = "qualifying_booking_id")
    private UUID qualifyingBookingId;
    @Column(name = "released_at")
    private Instant releasedAt;
    @Version
    private long version;

    protected Referral() {}
    public Referral(UUID salonId, UUID referrerCustomerId, UUID referredCustomerId, String code, long referrerRewardMinor, long newCustomerDiscountMinor) {
        super(salonId); if (referrerCustomerId.equals(referredCustomerId)) throw new IllegalArgumentException("Self referral is not allowed");
        this.referrerCustomerId = referrerCustomerId; this.referredCustomerId = referredCustomerId; this.code = code;
        this.referrerRewardMinor = referrerRewardMinor; this.newCustomerDiscountMinor = newCustomerDiscountMinor;
    }
    public UUID getReferrerCustomerId() { return referrerCustomerId; }
    public UUID getReferredCustomerId() { return referredCustomerId; }
    public String getCode() { return code; }
    public ReferralStatus getStatus() { return status; }
    public long getReferrerRewardMinor() { return referrerRewardMinor; }
    public long getNewCustomerDiscountMinor() { return newCustomerDiscountMinor; }
    public UUID getQualifyingBookingId() { return qualifyingBookingId; }
    public void markPhoneVerified() { if (status == ReferralStatus.REGISTERED) status = ReferralStatus.PHONE_VERIFIED; }
    public void markQualified(UUID bookingId) { if (status != ReferralStatus.PHONE_VERIFIED) throw new IllegalStateException("Referral phone verification is required"); status = ReferralStatus.REWARD_PENDING; qualifyingBookingId = bookingId; }
    public void release() { if (status != ReferralStatus.REWARD_PENDING) throw new IllegalStateException("Referral reward is not pending"); status = ReferralStatus.REWARD_GRANTED; releasedAt = Instant.now(); }
    public void reject() { if (status == ReferralStatus.REWARD_GRANTED) throw new IllegalStateException("Granted referral cannot be rejected"); status = ReferralStatus.REJECTED; }
}
