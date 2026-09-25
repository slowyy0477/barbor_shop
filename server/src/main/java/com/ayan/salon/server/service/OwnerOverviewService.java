package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owner-only read model for the operational dashboard.  The response contains
 * server-owned records and sanitized entity JSON; it is never available to a
 * customer session.
 */
@Service
public class OwnerOverviewService {
    private final DashboardService dashboard;
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final VisitRepository visits;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final DepositRepository deposits;
    private final WithdrawalRepository withdrawals;
    private final ReferralRepository referrals;
    private final ReminderRepository reminders;

    public OwnerOverviewService(DashboardService dashboard,
                                CustomerRepository customers,
                                BookingRepository bookings,
                                VisitRepository visits,
                                WalletRepository wallets,
                                WalletTransactionRepository transactions,
                                DepositRepository deposits,
                                WithdrawalRepository withdrawals,
                                ReferralRepository referrals,
                                ReminderRepository reminders) {
        this.dashboard = dashboard;
        this.customers = customers;
        this.bookings = bookings;
        this.visits = visits;
        this.wallets = wallets;
        this.transactions = transactions;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.referrals = referrals;
        this.reminders = reminders;
    }

    @Transactional(readOnly = true)
    public Overview get(ActorContext actor) {
        actor.require("view_financial_reports");
        var salonId = actor.salonId();
        return new Overview(
                dashboard.today(actor),
                customers.findBySalonId(salonId),
                bookings.findBySalonIdOrderByStartsAtDesc(salonId),
                visits.findBySalonIdOrderByCompletedAtDesc(salonId),
                wallets.findBySalonId(salonId),
                transactions.findBySalonIdOrderByOccurredAtDesc(salonId),
                deposits.findBySalonIdOrderByCreatedAtDesc(salonId),
                withdrawals.findBySalonIdOrderByCreatedAtDesc(salonId),
                referrals.findBySalonIdOrderByCreatedAtDesc(salonId),
                reminders.findBySalonIdOrderByDueAtDesc(salonId));
    }

    public record Overview(
            DashboardService.Summary summary,
            List<Customer> customers,
            List<Booking> bookings,
            List<Visit> visits,
            List<Wallet> wallets,
            List<WalletTransaction> walletTransactions,
            List<Deposit> deposits,
            List<Withdrawal> withdrawals,
            List<Referral> referrals,
            List<Reminder> reminders) {}
}
