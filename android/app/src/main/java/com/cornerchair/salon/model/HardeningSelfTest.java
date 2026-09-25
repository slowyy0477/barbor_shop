package com.cornerchair.salon.model;

import java.util.Collections;

/**
 * No-dependency financial/security checks for the Ayan upgrade. Run with a JDK after compiling
 * this package; Android does not invoke main(). Persistence-level concurrency still needs a DB
 * integration test once the backend exists.
 */
public final class HardeningSelfTest {
    private HardeningSelfTest() {
    }

    public static void main(String[] args) {
        run();
        System.out.println("Ayan financial hardening checks passed");
    }

    public static void run() {
        final long now = 20L * DomainTime.MILLIS_PER_DAY;
        final String salonId = "ayan-salon";
        // This check intentionally exercises a configurable PKR 50 promotional reward; the
        // current Ayan default is PKR 100 and is configured separately in the app settings.
        SalonSettings settings = new SalonSettings(salonId, "PKR", 0, 0, 25, 50L, 0L);

        PaymentProviderConfig provider = new PaymentProviderConfig(
                salonId,
                PaymentProvider.EASYPAISA,
                "Easypaisa",
                "Ayan Beauty Salon",
                "03105301460",
                "",
                "Send payment, then submit the provider reference for verification.",
                PaymentMode.MANUAL,
                true,
                1,
                now);
        assertEquals("********1460", provider.getMaskedAccountNumber(), "provider masking");
        assertTrue(provider.isEnabled(), "provider enabled");

        Deposit pendingDeposit = Deposit.submit(
                "dep-500", salonId, "customer-1", 500L, PaymentProvider.EASYPAISA,
                "EP-ABC-123", "proof://receipt", now);
        assertEquals(DepositStatus.PENDING_VERIFICATION, pendingDeposit.getStatus(),
                "deposit starts pending");
        assertEquals(0L, new WalletLedger().getPaidBalanceAt(now),
                "customer claim cannot credit wallet");

        StaffMember owner = StaffMember.owner("owner-1", salonId, "Ayan Owner", now);
        AuthorizationRules.require(owner, salonId, Permission.APPROVE_DEPOSITS);
        DepositRules.ApprovalResult approval = DepositRules.approve(
                pendingDeposit, owner.getId(), now + 1L, new WalletLedger(), settings);
        WalletLedger ledger = approval.getLedger();
        assertEquals(DepositStatus.APPROVED, approval.getDeposit().getStatus(), "deposit approval");
        assertEquals(500L, ledger.getPaidBalanceAt(now + 1L), "approved cash balance");
        assertEquals(0L, ledger.getBonusBalanceAt(now + 1L), "no automatic bonus");
        // Approval callback retry must not double-credit the account.
        DepositRules.ApprovalResult retried = DepositRules.approve(
                approval.getDeposit(), owner.getId(), now + 2L, ledger, settings);
        assertEquals(500L, retried.getLedger().getPaidBalanceAt(now + 2L),
                "idempotent deposit approval");
        assertEquals(1, retried.getLedger().getTransactions().size(),
                "single deposit ledger entry");

        ledger = retried.getLedger().recordReferralReward(
                "reward-1", "customer-1", salonId, 50L, "referral-1", now + 3L, settings)
                .getLedger();
        assertEquals(500L, ledger.getPaidBalanceAt(now + 3L), "cash remains withdrawable");
        assertEquals(50L, ledger.getBonusBalanceAt(now + 3L), "referral is promotional");

        // Ayan scenario: consume referral/promotional credit first for a PKR 300 salon service.
        WalletLedger.ServicePaymentResult servicePayment = ledger.recordServicePayment(
                "service-pay-1", "customer-1", salonId, 300L, "Haircut", "visit-1",
                true, now + 4L);
        ledger = servicePayment.getLedger();
        assertEquals(250L, ledger.getPaidBalanceAt(now + 4L), "cash after haircut");
        assertEquals(0L, ledger.getBonusBalanceAt(now + 4L), "referral consumed first");
        assertEquals(250L, servicePayment.getCashComponentPkr(), "cash service component");
        assertEquals(50L, servicePayment.getPromotionalComponentPkr(),
                "promotional service component");

        Withdrawal withdrawal = Withdrawal.request(
                "wd-200", salonId, "customer-1", 200L, PaymentProvider.JAZZCASH,
                "03001234567", now + 5L);
        WithdrawalRules.RequestResult requested = WithdrawalRules.request(
                withdrawal, ledger, true, true, 100L, 10000L, 0L, now + 5L);
        ledger = requested.getLedger();
        assertEquals(50L, ledger.getPaidBalanceAt(now + 5L),
                "withdrawal immediately reserves cash");

        boolean secondWithdrawalRejected = false;
        try {
            Withdrawal second = Withdrawal.request(
                    "wd-second", salonId, "customer-1", 200L, PaymentProvider.JAZZCASH,
                    "03001234567", now + 5L);
            WithdrawalRules.request(second, ledger, true, true, 100L, 10000L, 200L,
                    now + 5L);
        } catch (WalletLedger.InsufficientCashException expected) {
            secondWithdrawalRejected = true;
        }
        assertTrue(secondWithdrawalRejected, "concurrent withdrawal guard");

        // Rejection returns only the reserved cash; no original financial event is deleted.
        WithdrawalRules.ReviewResult rejection = WithdrawalRules.reject(
                withdrawal, ledger, owner.getId(), "Provider details could not be verified",
                now + 6L);
        ledger = rejection.getLedger();
        assertEquals(250L, ledger.getPaidBalanceAt(now + 6L), "rejected hold released");
        assertEquals(WithdrawalStatus.REJECTED, rejection.getWithdrawal().getStatus(),
                "withdrawal rejected");

        StaffMember receptionist = new StaffMember(
                "staff-1", salonId, "Reception", StaffRole.RECEPTIONIST,
                Collections.<Permission>emptySet(), true, now);
        boolean unauthorizedRejected = false;
        try {
            AuthorizationRules.require(receptionist, salonId, Permission.APPROVE_WITHDRAWALS);
        } catch (AuthorizationRules.UnauthorizedException expected) {
            unauthorizedRejected = true;
        }
        assertTrue(unauthorizedRejected, "staff cannot use owner financial permission");

        AuditEvent audit = new AuditEvent(
                "audit-dep-1", salonId, owner.getId(), AuditAction.DEPOSIT_APPROVED,
                "Deposit", pendingDeposit.getId(), "Provider account verified", "PENDING",
                "APPROVED", "request-1", "server-session", now + 1L);
        assertEquals(AuditAction.DEPOSIT_APPROVED, audit.getAction(), "deposit audit event");

        boolean emptyAdjustmentReasonRejected = false;
        try {
            new AuditEvent("audit-adjust", salonId, owner.getId(), AuditAction.WALLET_ADJUSTED,
                    "Wallet", "customer-1", "", "250", "300", "request-2", "", now);
        } catch (IllegalArgumentException expected) {
            emptyAdjustmentReasonRejected = true;
        }
        assertTrue(emptyAdjustmentReasonRejected, "adjustment audit requires reason");

        // Debit, refund, and manual-adjustment callbacks are replay-safe and reject changed
        // payloads under the same operation ID.
        WalletLedger.WalletDebitResult debit = ledger.debit(
                "debit-100", "customer-1", salonId, 100L, "Salon service", "visit-2", now + 7L);
        ledger = debit.getLedger();
        int afterDebit = ledger.getTransactions().size();
        WalletLedger.WalletDebitResult debitRetry = ledger.debit(
                "debit-100", "customer-1", salonId, 100L, "Salon service", "visit-2", now + 8L);
        assertEquals(afterDebit, debitRetry.getLedger().getTransactions().size(),
                "idempotent debit retry");
        assertEquals(100L, debitRetry.getAmountPkr(), "debit retry amount");
        boolean changedDebitRejected = false;
        try {
            ledger.debit("debit-100", "customer-1", salonId, 101L,
                    "Salon service", "visit-2", now + 8L);
        } catch (IllegalStateException expected) {
            changedDebitRejected = true;
        }
        assertTrue(changedDebitRejected, "changed debit payload rejected");

        ledger = ledger.recordRefund("refund-100", "customer-1", salonId, WalletBucket.PAID,
                25L, "Refund for cancelled add-on", "visit-2", now + 9L);
        int afterRefund = ledger.getTransactions().size();
        ledger = ledger.recordRefund("refund-100", "customer-1", salonId, WalletBucket.PAID,
                25L, "Refund for cancelled add-on", "visit-2", now + 10L);
        assertEquals(afterRefund, ledger.getTransactions().size(), "idempotent refund retry");
        boolean changedRefundRejected = false;
        try {
            ledger.recordRefund("refund-100", "customer-1", salonId, WalletBucket.PAID,
                    26L, "Refund for cancelled add-on", "visit-2", now + 10L);
        } catch (IllegalStateException expected) {
            changedRefundRejected = true;
        }
        assertTrue(changedRefundRejected, "changed refund payload rejected");

        ledger = ledger.manualAdjustment("adjust-100", "customer-1", salonId, WalletBucket.BONUS,
                10L, "Courtesy credit", now + 11L, settings);
        int afterAdjustment = ledger.getTransactions().size();
        ledger = ledger.manualAdjustment("adjust-100", "customer-1", salonId, WalletBucket.BONUS,
                10L, "Courtesy credit", now + 12L, settings);
        assertEquals(afterAdjustment, ledger.getTransactions().size(),
                "idempotent manual adjustment retry");
        boolean changedAdjustmentRejected = false;
        try {
            ledger.manualAdjustment("adjust-100", "customer-1", salonId, WalletBucket.BONUS,
                    11L, "Courtesy credit", now + 12L, settings);
        } catch (IllegalStateException expected) {
            changedAdjustmentRejected = true;
        }
        assertTrue(changedAdjustmentRejected, "changed manual adjustment rejected");

        boolean crossCustomerRejected = false;
        try {
            ledger.recordRefund("cross-customer", "customer-2", salonId, WalletBucket.PAID,
                    1L, "Should fail", "other", now + 13L);
        } catch (WalletLedger.LedgerScopeException expected) {
            crossCustomerRejected = true;
        }
        assertTrue(crossCustomerRejected, "cross-customer ledger operation rejected");

        // Role defaults may change, but owner-issued explicit grants survive a role change.
        StaffMember managerWithGrant = new StaffMember(
                "manager-1", salonId, "Manager", StaffRole.MANAGER,
                Collections.singleton(Permission.MODIFY_BUSINESS_SETTINGS), true, now);
        StaffMember barberWithGrant = managerWithGrant.withRole(StaffRole.BARBER, now + 1L);
        assertTrue(barberWithGrant.hasPermission(Permission.MODIFY_BUSINESS_SETTINGS),
                "explicit permission preserved across role change");
        assertTrue(!barberWithGrant.hasPermission(Permission.MANAGE_STAFF),
                "old role default removed on role change");

        // A booking ID is an idempotency key: retries must carry the original immutable payload.
        Booking booking = new Booking("retry-booking", salonId, "customer-1", "haircut", "staff-1",
                "2026-09-20", 600, 30, 500L, Collections.<BookingAddOn>emptyList(), now);
        java.util.List<Booking> confirmedBookings = BookingRules.confirmBooking(
                Collections.singletonList(booking), booking, now + 2L);
        Booking retryPayload = new Booking("retry-booking", salonId, "customer-1", "haircut", "staff-1",
                "2026-09-20", 600, 30, 500L, Collections.<BookingAddOn>emptyList(), now);
        java.util.List<Booking> confirmedRetry = BookingRules.confirmBooking(
                confirmedBookings, retryPayload, now + 3L);
        assertEquals(1, confirmedRetry.size(), "idempotent booking retry");
        boolean changedBookingRejected = false;
        try {
            Booking changedPayload = new Booking("retry-booking", salonId, "customer-1", "haircut",
                    "staff-2", "2026-09-20", 600, 30, 500L,
                    Collections.<BookingAddOn>emptyList(), now);
            BookingRules.confirmBooking(confirmedBookings, changedPayload, now + 3L);
        } catch (BookingRules.BookingIdReuseException expected) {
            changedBookingRejected = true;
        }
        assertTrue(changedBookingRejected, "changed booking retry rejected");

        // Verified phone aliases are treated as the same identity for referral fraud checks.
        assertTrue(IdentityRules.samePhone("03001234567", "+92 300 1234567"),
                "Pakistan phone normalization");
        assertTrue(ReferralRules.isSelfReferral("customer-a", "customer-b",
                "03001234567", "0092 300 1234567"), "phone self-referral detection");
        boolean phoneSelfReferralRejected = false;
        try {
            ReferralRules.claimUniqueByIdentity(
                    Referral.create("phone-ref", salonId, "customer-a", "PHONE", 50L, 100L, now),
                    "customer-b", "phone-booking", Collections.<Referral>emptyList(), now + 4L,
                    "03001234567", "+92 300 1234567");
        } catch (ReferralRules.SelfReferralException expected) {
            phoneSelfReferralRejected = true;
        }
        assertTrue(phoneSelfReferralRejected, "phone self-referral rejected");
    }

    private static void assertEquals(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }
}
