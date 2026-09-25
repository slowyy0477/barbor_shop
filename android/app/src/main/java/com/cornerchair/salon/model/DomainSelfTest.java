package com.cornerchair.salon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tiny no-dependency smoke test. Run with a JDK via:
 *   java com.cornerchair.salon.model.DomainSelfTest
 * Android builds do not call main; the methods are also useful as executable documentation.
 */
public final class DomainSelfTest {
    private DomainSelfTest() {
    }

    public static void main(String[] args) {
        run();
        System.out.println("Ayan Beauty Salon domain checks passed");
    }

    public static void run() {
        final long day = DomainTime.MILLIS_PER_DAY;
        final long now = 10L * day;
        SalonSettings settings = SalonSettings.samplePkr("salon-1");

        WalletLedger ledger = new WalletLedger();
        WalletLedger.WalletPaymentResult topUp = ledger.recordConfirmedPayment(
                "pay-1", "cust-1", "salon-1", 500L, "Wallet top-up", now, settings);
        assertEquals(500L, topUp.getLedger().getPaidBalanceAt(now), "paid top-up");
        assertEquals(50L, topUp.getLedger().getBonusBalanceAt(now), "bonus top-up");
        assertEquals(550L, topUp.getLedger().getTotalBalanceAt(now), "total top-up");
        // Retrying the same confirmed callback is idempotent.
        assertEquals(2, topUp.getLedger().recordConfirmedPayment(
                "pay-1", "cust-1", "salon-1", 500L, "Wallet top-up", now, settings)
                .getLedger().getTransactions().size(), "payment retry");

        BookingAddOn beard = new BookingAddOn("beard", "Beard Trim", 99L, 12);
        BookingAddOn massage = new BookingAddOn("massage", "Head Massage", 199L, 18);
        List<BookingAddOn> addOns = new ArrayList<BookingAddOn>();
        addOns.add(beard);
        addOns.add(massage);
        Booking pending = new Booking("book-1", "salon-1", "cust-1", "haircut", "staff-1",
                "2026-09-10", 600, 30, 500L, addOns, now);
        assertEquals(798L, pending.getTotalPkr(), "booking total");
        assertEquals(60, pending.getDurationMinutes(), "booking duration");
        List<Booking> confirmedList = BookingRules.confirmBooking(
                Collections.singletonList(pending), pending, now + 1);
        Booking confirmed = confirmedList.get(0);
        assertEquals(BookingStatus.CONFIRMED, confirmed.getStatus(), "booking confirmation");

        Booking completed = confirmed.complete(now + 2);
        Visit visit = Visit.fromCompletedBooking("visit-1", completed, now + 2, 798L, 25, settings);
        assertEquals(now + 25L * day + 2L, visit.getNextServiceDueAtMillis(), "25-day reminder");
        ReminderRules.ScheduleResult scheduled = ReminderRules.scheduleAfterCompletedVisit(
                "rem-1", visit, false, Collections.<Reminder>emptyList());
        assertEquals(ReminderStatus.SCHEDULED, scheduled.getReminder().getStatus(), "reminder scheduled");
        List<Reminder> suppressed = ReminderRules.suppressForConfirmedBooking(
                scheduled.getReminders(), new Booking("book-2", "salon-1", "cust-1", "haircut",
                        "staff-1", "2026-09-30", 600, 30, 500L,
                        Collections.<BookingAddOn>emptyList(), now));
        assertEquals(ReminderStatus.BOOKED, suppressed.get(0).getStatus(), "reminder suppression");

        Referral referral = Referral.create("ref-1", "salon-1", "cust-1", "AYAN100", 100L, 100L, now);
        Referral claimed = ReferralRules.claimUnique(referral, "cust-2", "book-new",
                Collections.<Referral>emptyList(), now + 3);
        ReferralRules.RewardReleaseResult released = ReferralRules.releaseAfterPaidFirstVisit(
                claimed, "cust-2", "book-new", "visit-new", 300L, true, true,
                0L, 0L, new WalletLedger(),
                "reward-1", now + 4, settings);
        assertEquals(ReferralStatus.RELEASED, released.getReferral().getStatus(), "referral release");
        // This referral intentionally uses an explicit legacy/PDF value to prove rewards remain
        // Referral rewards are configurable per campaign; the sample Ayan setting is PKR 100.
        assertEquals(100L, released.getNewCustomerDiscountPkr(), "invoice discount");
        assertEquals(100L, released.getReferrerLedger().getBonusBalanceAt(now + 4), "referrer credit");

        boolean selfReferralRejected = false;
        try {
            referral.claim("cust-1", "book-self", now + 5);
        } catch (ReferralRules.SelfReferralException expected) {
            selfReferralRejected = true;
        }
        assertTrue(selfReferralRejected, "self referral rejection");
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
