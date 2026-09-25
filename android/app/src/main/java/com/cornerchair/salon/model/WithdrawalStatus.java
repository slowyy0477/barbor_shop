package com.cornerchair.salon.model;

/** Customer withdrawal lifecycle. A pending request holds cash until completed or rejected. */
public enum WithdrawalStatus {
    PENDING,
    APPROVED,
    COMPLETED,
    REJECTED,
    CANCELLED
}
