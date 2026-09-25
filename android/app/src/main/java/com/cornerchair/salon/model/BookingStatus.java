package com.cornerchair.salon.model;

/** Booking states used by both the customer and owner views. */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean canTransitionTo(BookingStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case PENDING:
                // Walk-ins can be completed directly; online requests normally pass through
                // CONFIRMED first.
                return target == CONFIRMED || target == COMPLETED
                        || target == CANCELLED || target == NO_SHOW;
            case CONFIRMED:
                return target == COMPLETED || target == CANCELLED || target == NO_SHOW;
            default:
                return false;
        }
    }
}
