package com.cornerchair.salon.model;

/**
 * Public receiving instructions for one provider at one salon.
 *
 * The account number is the salon's receiving address, not a customer PIN or provider secret.
 * API secrets and access tokens must remain in a server-side secret store.
 */
public final class PaymentProviderConfig {
    private final String salonId;
    private final PaymentProvider provider;
    private final String displayName;
    private final String accountTitle;
    private final String accountNumber;
    private final String qrCodeUri;
    private final String instructions;
    private final PaymentMode mode;
    private final boolean enabled;
    private final int sortOrder;
    private final long updatedAtMillis;

    public PaymentProviderConfig(String salonId,
                                 PaymentProvider provider,
                                 String displayName,
                                 String accountTitle,
                                 String accountNumber,
                                 String qrCodeUri,
                                 String instructions,
                                 PaymentMode mode,
                                 boolean enabled,
                                 int sortOrder,
                                 long updatedAtMillis) {
        DomainTime.requireNonBlank(salonId, "salonId");
        if (provider == null || mode == null) {
            throw new IllegalArgumentException("provider and mode are required");
        }
        DomainTime.requireNonBlank(displayName, "displayName");
        DomainTime.requireNonBlank(accountTitle, "accountTitle");
        DomainTime.requireNonBlank(accountNumber, "accountNumber");
        DomainTime.requireNonBlank(instructions, "instructions");
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder cannot be negative");
        }
        this.salonId = salonId;
        this.provider = provider;
        this.displayName = displayName;
        this.accountTitle = accountTitle;
        this.accountNumber = accountNumber;
        this.qrCodeUri = qrCodeUri == null ? "" : qrCodeUri;
        this.instructions = instructions;
        this.mode = mode;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.updatedAtMillis = updatedAtMillis;
    }

    public String getSalonId() {
        return salonId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccountTitle() {
        return accountTitle;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getQrCodeUri() {
        return qrCodeUri;
    }

    public String getInstructions() {
        return instructions;
    }

    public PaymentMode getMode() {
        return mode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    /** Account details can be shown in a compact owner list without exposing the full address. */
    public String getMaskedAccountNumber() {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "********" + accountNumber.substring(accountNumber.length() - 4);
    }

    public PaymentProviderConfig withEnabled(boolean nextEnabled, long atMillis) {
        return copy(displayName, accountTitle, accountNumber, qrCodeUri, instructions, mode,
                nextEnabled, sortOrder, atMillis);
    }

    public PaymentProviderConfig withDetails(String nextDisplayName,
                                             String nextAccountTitle,
                                             String nextAccountNumber,
                                             String nextQrCodeUri,
                                             String nextInstructions,
                                             PaymentMode nextMode,
                                             int nextSortOrder,
                                             long atMillis) {
        return copy(nextDisplayName, nextAccountTitle, nextAccountNumber, nextQrCodeUri,
                nextInstructions, nextMode, enabled, nextSortOrder, atMillis);
    }

    private PaymentProviderConfig copy(String nextDisplayName,
                                       String nextAccountTitle,
                                       String nextAccountNumber,
                                       String nextQrCodeUri,
                                       String nextInstructions,
                                       PaymentMode nextMode,
                                       boolean nextEnabled,
                                       int nextSortOrder,
                                       long atMillis) {
        return new PaymentProviderConfig(salonId, provider, nextDisplayName, nextAccountTitle,
                nextAccountNumber, nextQrCodeUri, nextInstructions, nextMode, nextEnabled,
                nextSortOrder, atMillis);
    }
}
