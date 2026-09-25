package com.cornerchair.salon.model;

/** Manual deposit lifecycle. A customer claim never credits a wallet by itself. */
public enum DepositStatus {
    PENDING_VERIFICATION,
    APPROVED,
    REJECTED,
    CANCELLED
}
