package com.ayan.salon.server.domain;

public final class DomainTypes {
    private DomainTypes() {}

    public enum ActorRole { OWNER, MANAGER, BARBER, STAFF, RECEPTIONIST, CUSTOMER }
    public enum AccountStatus { ACTIVE, SUSPENDED, DEACTIVATED, DELETION_REQUESTED, DELETED }
    public enum BookingStatus { PENDING, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW }
    public enum PaymentMethod { WALLET, CASH, MANUAL_PROVIDER }
    public enum DepositStatus { PENDING, APPROVED, REJECTED }
    public enum WithdrawalStatus { PENDING, APPROVED, COMPLETED, REJECTED }
    public enum ReferralStatus { REGISTERED, PHONE_VERIFIED, QUALIFIED, REWARD_PENDING, REWARD_GRANTED, REJECTED }
    public enum ReminderStatus { SCHEDULED, SENT, BOOKED, CANCELLED }
    public enum LedgerType {
        DEPOSIT_CASH,
        DEPOSIT_BONUS,
        WITHDRAWAL_RESERVE,
        WITHDRAWAL_RELEASE,
        WITHDRAWAL_COMPLETE,
        SERVICE_PAYMENT,
        REFERRAL_BONUS,
        REFUND,
        ADMIN_ADJUSTMENT,
        REVERSAL
    }
    public enum LedgerDirection { CREDIT, DEBIT, RESERVE, RELEASE }
    public enum ProviderMode { MANUAL, OFFICIAL_API }
}
