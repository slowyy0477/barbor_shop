package com.ayan.salon.server.web;

import com.ayan.salon.server.domain.DomainTypes;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}
    public record DepositRequest(@Positive long amountMinor, @NotBlank String providerCode, @NotBlank String providerReference, String proofUri) {}
    public record ReviewRequest(@NotBlank String reason) {}
    public record WithdrawalRequest(@Positive long amountMinor, @NotBlank String providerCode, @NotBlank String destinationToken) {}
    public record BookingRequest(@NotNull UUID customerId, @NotNull UUID serviceId, @NotNull UUID staffId,
                                 @NotNull Instant startsAt, @NotNull DomainTypes.PaymentMethod paymentMethod,
                                 @PositiveOrZero long addOnMinor, @PositiveOrZero int addOnMinutes,
                                 @Size(max = 10) List<@NotNull UUID> addOnIds) {
        /** Source-compatible constructor for clients that used client totals. */
        public BookingRequest(UUID customerId, UUID serviceId, UUID staffId, Instant startsAt,
                              DomainTypes.PaymentMethod paymentMethod, long addOnMinor, int addOnMinutes) {
            this(customerId, serviceId, staffId, startsAt, paymentMethod, addOnMinor, addOnMinutes, List.of());
        }
    }
    public record BookingStatusRequest(@NotNull DomainTypes.BookingStatus status, String reason) {}
    public record ReferralReleaseRequest(@NotNull UUID qualifyingBookingId) {}
    public record SettingsRequest(@NotBlank String name, @NotBlank String address, @NotBlank String phone, @PositiveOrZero int reminderDays, @PositiveOrZero long depositBonusMinor, @PositiveOrZero long referrerRewardMinor, @PositiveOrZero long newCustomerDiscountMinor, @PositiveOrZero long minimumWithdrawalMinor, @PositiveOrZero long maximumDailyWithdrawalMinor, boolean consumePromoFirst, @JsonAlias({"themeColor", "brandColor"}) String primaryColor, @JsonAlias({"logoUrl", "logo"}) String logoUri) {
        /** Source-compatible constructor for clients that predate branding fields. */
        public SettingsRequest(String name, String address, String phone, int reminderDays,
                               long depositBonusMinor, long referrerRewardMinor, long newCustomerDiscountMinor,
                               long minimumWithdrawalMinor, long maximumDailyWithdrawalMinor,
                               boolean consumePromoFirst) {
            this(name, address, phone, reminderDays, depositBonusMinor, referrerRewardMinor,
                    newCustomerDiscountMinor, minimumWithdrawalMinor, maximumDailyWithdrawalMinor,
                    consumePromoFirst, null, null);
        }
    }
    /** The server canonicalizes and hashes the mobile number; clients must not choose the identity hash. */
    public record CustomerRequest(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Size(max = 32) String phone,
                                  boolean marketingConsent) {}
    public record CustomerProfileRequest(@NotBlank @Size(max = 120) String name,
                                         boolean marketingConsent) {}
    /**
     * Owner-set replacement password. SMS recovery is switched off, so the
     * salon owner is the only one who can unlock a customer who forgot the
     * password. Only the 10 to 20 character password shape is accepted.
     */
    public record PasswordResetRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9@#$%^&*!._+\\-]{10,20}") String password) {}
    /**
     * A 4 to 6 digit sign-in PIN, or the 10-20 character owner password that
     * the same salted-digest routine accepts. The current secret is required
     * only when one already exists.
     */
    public record PinRequest(@NotBlank @Pattern(regexp = "(\\d{4,6})|([A-Za-z0-9@#$%^&*!._+\\-]{10,20})") String pin,
                             String currentPin) {}
    public record ServiceRequest(@NotBlank String name, @PositiveOrZero long priceMinor, @Positive int durationMinutes, String category, boolean active) {
        public ServiceRequest(String name, long priceMinor, int durationMinutes, String category) {
            this(name, priceMinor, durationMinutes, category, true);
        }
    }
    /**
     * Nullable active flag lets JSON distinguish an omitted value from an
     * explicit archive request. Creates default to active; updates preserve the
     * existing state when the flag is omitted.
     */
    public record AddOnRequest(@NotBlank String name, @PositiveOrZero long priceMinor, @Positive int durationMinutes,
                               UUID serviceId, String description, Boolean active) {}
    public record HaircutStyleRequest(@NotBlank String name, @NotBlank @JsonAlias({"photoUrl", "imageUrl"}) String photoUri, String photoAltText,
                                      @PositiveOrZero long priceMinor, String description,
                                      @PositiveOrZero int displayOrder, boolean active,
                                      @JsonAlias({"linkedServiceId"}) UUID serviceId) {}
    public record HaircutStylePhotoRequest(@NotBlank @JsonAlias({"photoUrl", "imageUrl"}) String photoUri, String photoAltText) {}
    public record BrandingRequest(@JsonAlias({"themeColor", "brandColor"}) String primaryColor,
                                   @JsonAlias({"logoUrl", "logo"}) String logoUri) {}
    public record StaffRequest(@NotBlank String name, String phone, @NotNull DomainTypes.ActorRole role, boolean active, String permissionsJson) {
        public StaffRequest(String name, String phone, DomainTypes.ActorRole role) {
            this(name, phone, role, true, "[]");
        }
    }
    public record ReferralRequest(@NotNull UUID referrerId, @NotNull UUID referredId, @NotBlank String code) {}
    public record PaymentMethodRequest(@NotBlank String providerCode, @NotBlank String displayName, String accountTitle, String accountToken, String instructions, boolean enabled, @PositiveOrZero int sortOrder, DomainTypes.ProviderMode mode, String accountReference) {
        public PaymentMethodRequest(String providerCode, String displayName, String accountTitle, String accountToken, String instructions, boolean enabled, int sortOrder, DomainTypes.ProviderMode mode) {
            this(providerCode, displayName, accountTitle, accountToken, instructions, enabled, sortOrder, mode, null);
        }
    }
    public record WalletAdjustmentRequest(@NotNull UUID customerId, long cashDeltaMinor, long promoDeltaMinor, @NotBlank String reason) {}
    public record RefundRequest(@NotNull UUID customerId, @NotNull UUID bookingId, @NotBlank String reason) {}
    public record ReversalRequest(@NotNull UUID customerId, @NotNull UUID transactionId, @NotBlank String reason) {}
}
