package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "salon_settings")
public class SalonSettings {
    @jakarta.persistence.Id
    private UUID salonId;
    @Version
    private long version;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String address;
    @Column(nullable = false)
    private String phone;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private String timezone;
    @Column(nullable = false)
    private int openingMinute;
    @Column(nullable = false)
    private int closingMinute;
    @Column(nullable = false)
    private int haircutReminderDays;
    @Column(nullable = false)
    private long depositBonusMinor;
    @Column(nullable = false)
    private long referralReferrerMinor;
    @Column(nullable = false)
    private long referralNewCustomerMinor;
    @Column(nullable = false)
    private long minimumWithdrawalMinor;
    @Column(nullable = false)
    private long maximumDailyWithdrawalMinor;
    @Column(nullable = false)
    private boolean consumePromoFirst;
    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;
    /** URI for an owner-managed logo asset; raw image bytes are kept out of the API. */
    @Column(name = "logo_uri", length = 2048)
    private String logoUri;
    @Column(nullable = false)
    private Instant updatedAt;

    protected SalonSettings() {}

    public SalonSettings(UUID salonId, String name, String address, String phone) {
        this.salonId = salonId;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.currency = "PKR";
        this.timezone = "Asia/Karachi";
        this.openingMinute = 8 * 60;
        this.closingMinute = 23 * 60;
        this.haircutReminderDays = 25;
        this.depositBonusMinor = 5_000;
        this.referralReferrerMinor = 10_000;
        this.referralNewCustomerMinor = 10_000;
        this.minimumWithdrawalMinor = 10_000;
        this.maximumDailyWithdrawalMinor = 1_000_000;
        this.consumePromoFirst = true;
        this.primaryColor = "#0F766E";
        this.logoUri = null;
        this.updatedAt = Instant.now();
    }

    public UUID getSalonId() { return salonId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getCurrency() { return currency; }
    public String getTimezone() { return timezone; }
    public int getOpeningMinute() { return openingMinute; }
    public int getClosingMinute() { return closingMinute; }
    public int getHaircutReminderDays() { return haircutReminderDays; }
    public long getDepositBonusMinor() { return depositBonusMinor; }
    public long getReferralReferrerMinor() { return referralReferrerMinor; }
    public long getReferralNewCustomerMinor() { return referralNewCustomerMinor; }
    public long getMinimumWithdrawalMinor() { return minimumWithdrawalMinor; }
    public long getMaximumDailyWithdrawalMinor() { return maximumDailyWithdrawalMinor; }
    public boolean isConsumePromoFirst() { return consumePromoFirst; }
    public String getPrimaryColor() { return primaryColor; }
    public String getLogoUri() { return logoUri; }
    /** Alias for clients that use URL terminology for image assets. */
    public String getLogoUrl() { return logoUri; }

    public void update(String name, String address, String phone, int haircutReminderDays,
                       long depositBonusMinor, long referralReferrerMinor, long referralNewCustomerMinor,
                       long minimumWithdrawalMinor, long maximumDailyWithdrawalMinor, boolean consumePromoFirst) {
        update(name, address, phone, haircutReminderDays, depositBonusMinor, referralReferrerMinor,
                referralNewCustomerMinor, minimumWithdrawalMinor,
                maximumDailyWithdrawalMinor, consumePromoFirst, null, null);
    }

    /**
     * Full settings update including optional branding.  Null branding fields
     * preserve the existing value for backwards-compatible clients; an empty
     * logo URI explicitly clears the logo.
     */
    public void update(String name, String address, String phone, int haircutReminderDays,
                       long depositBonusMinor, long referralReferrerMinor, long referralNewCustomerMinor,
                       long minimumWithdrawalMinor, long maximumDailyWithdrawalMinor, boolean consumePromoFirst,
                       String primaryColor, String logoUri) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.haircutReminderDays = haircutReminderDays;
        this.depositBonusMinor = depositBonusMinor;
        this.referralReferrerMinor = referralReferrerMinor;
        this.referralNewCustomerMinor = referralNewCustomerMinor;
        this.minimumWithdrawalMinor = minimumWithdrawalMinor;
        this.maximumDailyWithdrawalMinor = maximumDailyWithdrawalMinor;
        this.consumePromoFirst = consumePromoFirst;
        if (primaryColor != null && !primaryColor.isBlank()) this.primaryColor = normalizePrimaryColor(primaryColor);
        if (logoUri != null) this.logoUri = normalizeLogoUri(logoUri);
        this.updatedAt = Instant.now();
    }

    /** Owner-only branding update used by the lightweight admin endpoint. */
    public void updateBranding(String primaryColor, String logoUri) {
        if ((primaryColor == null || primaryColor.isBlank()) && logoUri == null) {
            throw new IllegalArgumentException("At least one branding value is required");
        }
        if (primaryColor != null && !primaryColor.isBlank()) this.primaryColor = normalizePrimaryColor(primaryColor);
        if (logoUri != null) this.logoUri = normalizeLogoUri(logoUri);
        this.updatedAt = Instant.now();
    }

    public static String normalizePrimaryColor(String value) {
        if (value == null || !value.trim().matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Primary color must be a six-digit hex value such as #0F766E");
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static String normalizeLogoUri(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 2048 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Logo URI is invalid or too long");
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:")) {
            throw new IllegalArgumentException("Logo URI scheme is not allowed");
        }
        return normalized;
    }

}
