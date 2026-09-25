package com.cornerchair.salon.model;

/** Granular server-side permissions. Never infer authorization from a client-supplied role. */
public enum Permission {
    MANAGE_STAFF("manage_staff"),
    MANAGE_SERVICES("manage_services"),
    MANAGE_PRICES("manage_prices"),
    APPROVE_DEPOSITS("approve_deposits"),
    APPROVE_WITHDRAWALS("approve_withdrawals"),
    COMPLETE_SERVICE("complete_service"),
    VIEW_CUSTOMERS("view_customers"),
    VIEW_FINANCIAL_REPORTS("view_financial_reports"),
    MODIFY_BUSINESS_SETTINGS("modify_business_settings"),
    VIEW_AUDIT_LOG("view_audit_log"),
    MANAGE_REFERRALS("manage_referrals"),
    MANAGE_BOOKINGS("manage_bookings"),
    MANAGE_WALLET("manage_wallet"),
    REVIEW_FRAUD("review_fraud");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
