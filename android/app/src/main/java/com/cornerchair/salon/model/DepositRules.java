package com.cornerchair.salon.model;

/** Atomic-looking deposit operations. Production callers must execute the result in a DB transaction. */
public final class DepositRules {
    private DepositRules() {
    }

    /**
     * Approves a pending claim and appends paid/bonus credit. Re-running the same approval is
     * idempotent because the deposit id is the wallet payment reference.
     */
    public static ApprovalResult approve(Deposit deposit,
                                          String actorId,
                                          long approvedAtMillis,
                                          WalletLedger ledger,
                                          SalonSettings settings) {
        if (deposit == null || ledger == null || settings == null) {
            throw new IllegalArgumentException("deposit, ledger, and settings are required");
        }
        if (!deposit.getSalonId().equals(settings.getSalonId())) {
            throw new IllegalArgumentException("deposit and settings belong to different salons");
        }
        DomainTime.requireNonBlank(actorId, "actorId");
        if (deposit.getStatus() == DepositStatus.REJECTED
                || deposit.getStatus() == DepositStatus.CANCELLED) {
            throw new IllegalStateException("deposit is not approvable: " + deposit.getStatus());
        }
        Deposit approved = deposit.getStatus() == DepositStatus.APPROVED
                ? deposit : deposit.approve(actorId, approvedAtMillis);
        WalletLedger.WalletPaymentResult payment = ledger.recordConfirmedPayment(
                deposit.getId(), deposit.getCustomerId(), deposit.getSalonId(), deposit.getAmountPkr(),
                "Manual " + deposit.getProvider().getDisplayName() + " deposit", approvedAtMillis,
                settings);
        return new ApprovalResult(approved, payment.getLedger(), payment);
    }

    public static final class ApprovalResult {
        private final Deposit deposit;
        private final WalletLedger ledger;
        private final WalletLedger.WalletPaymentResult payment;

        private ApprovalResult(Deposit deposit,
                               WalletLedger ledger,
                               WalletLedger.WalletPaymentResult payment) {
            this.deposit = deposit;
            this.ledger = ledger;
            this.payment = payment;
        }

        public Deposit getDeposit() {
            return deposit;
        }

        public WalletLedger getLedger() {
            return ledger;
        }

        public WalletLedger.WalletPaymentResult getPayment() {
            return payment;
        }
    }
}
