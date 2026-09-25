package com.cornerchair.salon.model;

/** Supported customer-to-salon payment rails. Provider credentials never belong in the APK. */
public enum PaymentProvider {
    EASYPAISA("Easypaisa"),
    JAZZCASH("JazzCash"),
    NAYAPAY("NayaPay"),
    SADAPAY("SadaPay");

    private final String displayName;

    PaymentProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
