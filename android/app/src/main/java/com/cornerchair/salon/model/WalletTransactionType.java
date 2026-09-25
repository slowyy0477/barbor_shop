package com.cornerchair.salon.model;

/** Append-only wallet events. A reversal is a new event; the original is never edited. */
public enum WalletTransactionType {
    PAID_CREDIT,
    BONUS_CREDIT,
    DEBIT,
    REFUND,
    REVERSAL,
    MANUAL_ADJUSTMENT,
    REFERRAL_REWARD,
    /** A salon service debit, with the bucket component recorded on each row. */
    SERVICE_PAYMENT,
    /** Cash withdrawal hold/debit. The hold is appended when a request is submitted. */
    WITHDRAWAL,
    /** Release of a rejected/cancelled withdrawal hold. */
    WITHDRAWAL_RELEASE
}
