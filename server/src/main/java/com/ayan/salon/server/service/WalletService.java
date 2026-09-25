package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.domain.DomainTypes.*;
import com.ayan.salon.server.domain.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Authoritative money application service. Every mutating method is transactional and
 * locks the customer's wallet (and the target aggregate) before calculating a result.
 * Amounts are PKR minor units (1 PKR = 100 minor units).
 */
@Service
public class WalletService {
    private final WalletRepository wallets;
    private final DepositRepository deposits;
    private final WithdrawalRepository withdrawals;
    private final WalletTransactionRepository ledger;
    private final BookingRepository bookings;
    private final ReferralRepository referrals;
    private final SalonSettingsRepository settings;
    private final VisitRepository visits;
    private final BusinessLedgerRepository businessLedger;
    private final ReminderService reminderService;
    private final ServiceOfferingRepository serviceOfferings;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final NotificationGateway notifications;

    public WalletService(WalletRepository wallets, DepositRepository deposits, WithdrawalRepository withdrawals,
                         WalletTransactionRepository ledger, BookingRepository bookings, ReferralRepository referrals,
                         SalonSettingsRepository settings, VisitRepository visits, BusinessLedgerRepository businessLedger,
                         AuditService audit, IdempotencyService idempotency, ReminderService reminderService,
                         ServiceOfferingRepository serviceOfferings) {
        this(wallets, deposits, withdrawals, ledger, bookings, referrals, settings, visits, businessLedger,
                audit, idempotency, reminderService, serviceOfferings, null);
    }

    @Autowired
    public WalletService(WalletRepository wallets, DepositRepository deposits, WithdrawalRepository withdrawals,
                         WalletTransactionRepository ledger, BookingRepository bookings, ReferralRepository referrals,
                         SalonSettingsRepository settings, VisitRepository visits, BusinessLedgerRepository businessLedger,
                         AuditService audit, IdempotencyService idempotency, ReminderService reminderService,
                         ServiceOfferingRepository serviceOfferings, NotificationGateway notifications) {
        this.wallets = wallets; this.deposits = deposits; this.withdrawals = withdrawals; this.ledger = ledger; this.bookings = bookings;
        this.referrals = referrals; this.settings = settings; this.visits = visits; this.businessLedger = businessLedger; this.audit = audit; this.idempotency = idempotency; this.reminderService = reminderService; this.serviceOfferings = serviceOfferings; this.notifications = notifications;
    }

    @Transactional
    public Deposit submitDeposit(ActorContext actor, long amountMinor, String providerCode, String providerReference,
                                 String proofUri, String idempotencyKey) {
        actor.require("submit_deposit");
        requireCustomerActor(actor, "Only a customer session can submit a wallet deposit");
        if (amountMinor <= 0 || providerCode == null || providerCode.isBlank() || providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("Invalid manual deposit");
        String canonicalProvider = providerCode.trim().toUpperCase(java.util.Locale.ROOT);
        String canonicalReference = providerReference.trim();
        String canonicalProof = proofUri == null ? "" : proofUri.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields(
                "deposit.submit", actor.actorId(), amountMinor, canonicalProvider, canonicalReference, canonicalProof);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "deposit.submit", requestFingerprint);
        if (existing != null) return deposits.findById(UUID.fromString(existing)).orElseThrow();
        if (deposits.existsBySalonIdAndProviderReferenceIgnoreCase(actor.salonId(), canonicalReference)) throw new ConflictException("Provider reference already submitted");
        SalonSettings s = settings.findById(actor.salonId()).orElseThrow(() -> new NotFoundException("Salon settings not found"));
        Deposit deposit = deposits.save(new Deposit(actor.salonId(), actor.actorId(), amountMinor, PaymentMethod.MANUAL_PROVIDER, canonicalProvider, canonicalReference, canonicalProof, s.getDepositBonusMinor()));
        idempotency.complete(idempotencyKey, actor.salonId(), "deposit.submit", deposit.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "DEPOSIT_SUBMITTED", "Deposit", deposit.getId(), deposit.getProviderCode());
        return deposit;
    }

    @Transactional
    public Deposit approveDeposit(ActorContext actor, UUID depositId, String idempotencyKey) {
        actor.require("approve_deposits");
        String requestFingerprint = IdempotencyService.fingerprintFields("deposit.approve", actor.actorId(), depositId);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "deposit.approve", requestFingerprint);
        if (existing != null) return deposits.findById(UUID.fromString(existing)).orElseThrow();
        Deposit deposit = deposits.lockBySalonIdAndId(actor.salonId(), depositId).orElseThrow(() -> new NotFoundException("Deposit not found"));
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), deposit.getCustomerId()).orElseThrow(() -> new NotFoundException("Wallet not found"));
        deposit.approve(actor.actorId());
        wallet.creditCash(deposit.getAmountMinor());
        // The promotion is account-level.  Claim it while the wallet is locked
        // so two concurrent approvals cannot grant the first-deposit bonus twice.
        boolean firstDeposit = wallet.claimFirstDepositBonus();
        long grantedBonusMinor = firstDeposit ? deposit.getBonusSnapshotMinor() : 0;
        deposit.recordGrantedBonus(grantedBonusMinor);
        if (grantedBonusMinor > 0) wallet.creditPromo(grantedBonusMinor);
        ledger.save(new WalletTransaction(actor.salonId(), deposit.getCustomerId(), LedgerType.DEPOSIT_CASH, deposit.getAmountMinor(), 0, "PKR", "COMPLETED", "deposit:" + deposit.getId(), actor.actorId(), "Approved manual deposit"));
        if (grantedBonusMinor > 0) ledger.save(new WalletTransaction(actor.salonId(), deposit.getCustomerId(), LedgerType.DEPOSIT_BONUS, 0, grantedBonusMinor, "PKR", "COMPLETED", "deposit-bonus:" + deposit.getId(), actor.actorId(), "First approved deposit bonus"));
        businessLedger.save(new BusinessLedgerEntry(actor.salonId(), "CUSTOMER_DEPOSIT", deposit.getAmountMinor(), "deposit:" + deposit.getId(), "Manual provider deposit approved"));
        enqueueDepositReceipt(actor.salonId(), deposit, wallet, grantedBonusMinor);
        idempotency.complete(idempotencyKey, actor.salonId(), "deposit.approve", deposit.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "DEPOSIT_APPROVED", "Deposit", deposit.getId(), "cash=" + deposit.getAmountMinor() + ", bonus=" + grantedBonusMinor + ", firstDeposit=" + firstDeposit);
        return deposit;
    }

    @Transactional
    public Deposit rejectDeposit(ActorContext actor, UUID depositId, String reason, String idempotencyKey) {
        actor.require("approve_deposits");
        String normalizedReason = reason == null || reason.isBlank() ? "" : reason.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields("deposit.reject", actor.actorId(), depositId, normalizedReason);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "deposit.reject", requestFingerprint);
        if (existing != null) return deposits.findById(UUID.fromString(existing)).orElseThrow();
        Deposit deposit = deposits.lockBySalonIdAndId(actor.salonId(), depositId).orElseThrow(() -> new NotFoundException("Deposit not found"));
        deposit.reject(actor.actorId(), normalizedReason);
        idempotency.complete(idempotencyKey, actor.salonId(), "deposit.reject", deposit.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "DEPOSIT_REJECTED", "Deposit", deposit.getId(), normalizedReason);
        return deposit;
    }

    @Transactional
    public Withdrawal requestWithdrawal(ActorContext actor, long amountMinor, String providerCode, String destinationToken, String idempotencyKey) {
        actor.require("request_withdrawal");
        requireCustomerActor(actor, "Only a customer session can request a withdrawal");
        if (amountMinor <= 0 || providerCode == null || providerCode.isBlank() || destinationToken == null || destinationToken.isBlank()) throw new IllegalArgumentException("Invalid withdrawal");
        String canonicalProvider = providerCode.trim().toUpperCase(java.util.Locale.ROOT);
        String canonicalDestination = destinationToken.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields(
                "withdrawal.request", actor.actorId(), amountMinor, canonicalProvider, canonicalDestination);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "withdrawal.request", requestFingerprint);
        if (existing != null) return withdrawals.findById(UUID.fromString(existing)).orElseThrow();
        SalonSettings s = settings.findById(actor.salonId()).orElseThrow(() -> new NotFoundException("Salon settings not found"));
        if (amountMinor < s.getMinimumWithdrawalMinor()) throw new RuleViolationException("Below minimum withdrawal");
        Instant dayStart = ZonedDateTime.now(ZoneId.of(s.getTimezone())).toLocalDate().atStartOfDay(ZoneId.of(s.getTimezone())).toInstant();
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), actor.actorId()).orElseThrow(() -> new NotFoundException("Wallet not found"));
        if (withdrawals.sumActiveSince(actor.salonId(), actor.actorId(), dayStart, WithdrawalStatus.REJECTED) + amountMinor > s.getMaximumDailyWithdrawalMinor()) throw new RuleViolationException("Daily withdrawal limit exceeded");
        wallet.reserveCash(amountMinor);
        Withdrawal withdrawal = withdrawals.save(new Withdrawal(actor.salonId(), actor.actorId(), amountMinor, PaymentMethod.MANUAL_PROVIDER, canonicalProvider, canonicalDestination));
        ledger.save(new WalletTransaction(actor.salonId(), actor.actorId(), LedgerType.WITHDRAWAL_RESERVE, amountMinor, 0, "PKR", "PENDING", "withdrawal:" + withdrawal.getId(), actor.actorId(), "Cash reserved for owner review"));
        idempotency.complete(idempotencyKey, actor.salonId(), "withdrawal.request", withdrawal.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "WITHDRAWAL_REQUESTED", "Withdrawal", withdrawal.getId(), "amount=" + amountMinor);
        return withdrawal;
    }

    @Transactional
    public Withdrawal approveWithdrawal(ActorContext actor, UUID withdrawalId, String idempotencyKey) {
        actor.require("approve_withdrawals");
        String requestFingerprint = IdempotencyService.fingerprintFields("withdrawal.approve", actor.actorId(), withdrawalId);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "withdrawal.approve", requestFingerprint);
        if (existing != null) return withdrawals.findById(UUID.fromString(existing)).orElseThrow();
        Withdrawal withdrawal = withdrawals.lockBySalonIdAndId(actor.salonId(), withdrawalId).orElseThrow(() -> new NotFoundException("Withdrawal not found"));
        withdrawal.approve(actor.actorId());
        idempotency.complete(idempotencyKey, actor.salonId(), "withdrawal.approve", withdrawal.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "WITHDRAWAL_APPROVED", "Withdrawal", withdrawal.getId(), null);
        return withdrawal;
    }

    @Transactional
    public Withdrawal completeWithdrawal(ActorContext actor, UUID withdrawalId, String idempotencyKey) {
        actor.require("approve_withdrawals");
        String requestFingerprint = IdempotencyService.fingerprintFields("withdrawal.complete", actor.actorId(), withdrawalId);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "withdrawal.complete", requestFingerprint);
        if (existing != null) return withdrawals.findById(UUID.fromString(existing)).orElseThrow();
        Withdrawal withdrawal = withdrawals.lockBySalonIdAndId(actor.salonId(), withdrawalId).orElseThrow(() -> new NotFoundException("Withdrawal not found"));
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), withdrawal.getCustomerId()).orElseThrow(() -> new NotFoundException("Wallet not found"));
        withdrawal.complete(actor.actorId());
        wallet.completeReservedCash(withdrawal.getAmountMinor());
        ledger.save(new WalletTransaction(actor.salonId(), withdrawal.getCustomerId(), LedgerType.WITHDRAWAL_COMPLETE, withdrawal.getAmountMinor(), 0, "PKR", "COMPLETED", "withdrawal:" + withdrawal.getId(), actor.actorId(), "Owner confirmed external payout"));
        idempotency.complete(idempotencyKey, actor.salonId(), "withdrawal.complete", withdrawal.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "WITHDRAWAL_COMPLETED", "Withdrawal", withdrawal.getId(), null);
        return withdrawal;
    }

    @Transactional
    public Withdrawal rejectWithdrawal(ActorContext actor, UUID withdrawalId, String reason, String idempotencyKey) {
        actor.require("approve_withdrawals");
        String normalizedReason = reason == null || reason.isBlank() ? "" : reason.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields("withdrawal.reject", actor.actorId(), withdrawalId, normalizedReason);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "withdrawal.reject", requestFingerprint);
        if (existing != null) return withdrawals.findById(UUID.fromString(existing)).orElseThrow();
        Withdrawal withdrawal = withdrawals.lockBySalonIdAndId(actor.salonId(), withdrawalId).orElseThrow(() -> new NotFoundException("Withdrawal not found"));
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), withdrawal.getCustomerId()).orElseThrow(() -> new NotFoundException("Wallet not found"));
        withdrawal.reject(actor.actorId(), normalizedReason);
        wallet.releaseCash(withdrawal.getAmountMinor());
        ledger.save(new WalletTransaction(actor.salonId(), withdrawal.getCustomerId(), LedgerType.WITHDRAWAL_RELEASE, withdrawal.getAmountMinor(), 0, "PKR", "COMPLETED", "withdrawal-release:" + withdrawal.getId(), actor.actorId(), normalizedReason));
        idempotency.complete(idempotencyKey, actor.salonId(), "withdrawal.reject", withdrawal.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "WITHDRAWAL_REJECTED", "Withdrawal", withdrawal.getId(), normalizedReason);
        return withdrawal;
    }

    @Transactional
    public Booking completeServicePayment(ActorContext actor, UUID bookingId, String idempotencyKey) {
        actor.require("complete_service");
        String requestFingerprint = IdempotencyService.fingerprintFields("service.complete", actor.actorId(), bookingId);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "service.complete", requestFingerprint);
        if (existing != null) return bookings.findById(UUID.fromString(existing)).orElseThrow();
        Booking booking = bookings.lockBySalonIdAndId(actor.salonId(), bookingId).orElseThrow(() -> new NotFoundException("Booking not found"));
        if ((actor.role() == ActorRole.BARBER || actor.role() == ActorRole.STAFF)
                && !actor.actorId().equals(booking.getStaffId())) {
            throw new ActorContext.AuthorizationException("Staff may only complete their assigned booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new RuleViolationException("Booking cannot be completed from status " + booking.getStatus());
        }
        SalonSettings s = settings.findById(actor.salonId()).orElseThrow(() -> new NotFoundException("Salon settings not found"));
        PaymentMethod paymentMethod = booking.getPaymentMethod();
        if (paymentMethod != PaymentMethod.WALLET && paymentMethod != PaymentMethod.CASH && paymentMethod != PaymentMethod.MANUAL_PROVIDER) {
            throw new RuleViolationException("Booking payment method is invalid");
        }

        long cashSpent = 0;
        long promoSpent = 0;
        if (paymentMethod == PaymentMethod.WALLET) {
            Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), booking.getCustomerId()).orElseThrow(() -> new NotFoundException("Wallet not found"));
            long cashBefore = wallet.getCashAvailableMinor();
            long promoBefore = wallet.getPromoAvailableMinor();
            wallet.debitForService(booking.getTotalMinor(), s.isConsumePromoFirst());
            cashSpent = cashBefore - wallet.getCashAvailableMinor();
            promoSpent = promoBefore - wallet.getPromoAvailableMinor();
            ledger.save(new WalletTransaction(actor.salonId(), booking.getCustomerId(), LedgerType.SERVICE_PAYMENT, cashSpent, promoSpent, "PKR", "COMPLETED", "service:" + booking.getId(), actor.actorId(), "Completed salon service"));
            enqueueServicePaymentReceipt(actor.salonId(), booking, wallet, cashSpent, promoSpent);
        }

        Instant completedAt = Instant.now();
        booking.complete("service:" + booking.getId());
        long reminderDays = s.getHaircutReminderDays();
        Instant nextDueAt = reminderDays > 0 ? completedAt.plusSeconds(Math.multiplyExact(reminderDays, 86_400L)) : null;
        Visit visit = visits.findBySalonIdAndBookingId(actor.salonId(), booking.getId()).orElseGet(() -> visits.save(new Visit(actor.salonId(), booking.getCustomerId(), booking.getId(), booking.getServiceId(), booking.getStaffId(), completedAt, nextDueAt, booking.getTotalMinor())));
        Instant nextDue = visit.getNextDueAt();
        if (nextDue != null) reminderService.schedule(actor.salonId(), booking.getCustomerId(), booking.getServiceId(), nextDue, visit.getId());
        businessLedger.save(new BusinessLedgerEntry(actor.salonId(), "SERVICE_REVENUE", booking.getTotalMinor(), "service:" + booking.getId(), paymentMethod + " service payment"));
        idempotency.complete(idempotencyKey, actor.salonId(), "service.complete", booking.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "SERVICE_COMPLETED", "Booking", booking.getId(), "method=" + paymentMethod + ", cash=" + cashSpent + ", promo=" + promoSpent);
        return booking;
    }

    private void enqueueDepositReceipt(UUID salonId, Deposit deposit, Wallet wallet, long grantedBonusMinor) {
        if (notifications == null) return;
        notifications.send(salonId, deposit.getCustomerId(), "WALLET_DEPOSIT_RECEIPT",
                "{\"event\":\"wallet_deposit_approved\",\"depositId\":\"" + deposit.getId()
                        + "\",\"paidCreditMinor\":" + deposit.getAmountMinor()
                        + ",\"bonusCreditMinor\":" + grantedBonusMinor
                        + ",\"cashBalanceMinor\":" + wallet.getCashAvailableMinor()
                        + ",\"promoBalanceMinor\":" + wallet.getPromoAvailableMinor()
                        + ",\"currency\":\"PKR\"}");
    }

    private void enqueueServicePaymentReceipt(UUID salonId, Booking booking, Wallet wallet,
                                              long cashSpent, long promoSpent) {
        if (notifications == null) return;
        notifications.send(salonId, booking.getCustomerId(), "WALLET_PAYMENT_RECEIPT",
                "{\"event\":\"wallet_service_payment\",\"bookingId\":\"" + booking.getId()
                        + "\",\"serviceId\":\"" + booking.getServiceId()
                        + "\",\"totalMinor\":" + booking.getTotalMinor()
                        + ",\"paidCreditMinor\":" + cashSpent
                        + ",\"bonusCreditMinor\":" + promoSpent
                        + ",\"cashBalanceMinor\":" + wallet.getCashAvailableMinor()
                        + ",\"promoBalanceMinor\":" + wallet.getPromoAvailableMinor()
                        + ",\"currency\":\"PKR\"}");
    }

    @Transactional
    public Referral releaseReferral(ActorContext actor, UUID referralId, UUID qualifyingBookingId, String idempotencyKey) {
        actor.require("approve_referrals");
        String requestFingerprint = IdempotencyService.fingerprintFields("referral.release", actor.actorId(), referralId, qualifyingBookingId);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "referral.release", requestFingerprint);
        if (existing != null) return referrals.findById(UUID.fromString(existing)).orElseThrow();
        Referral referral = referrals.lockBySalonIdAndId(actor.salonId(), referralId).orElseThrow(() -> new NotFoundException("Referral not found"));
        if (referral.getStatus() == ReferralStatus.REWARD_GRANTED) throw new ConflictException("Referral reward has already been released");
        if (referral.getStatus() != ReferralStatus.PHONE_VERIFIED && referral.getStatus() != ReferralStatus.REWARD_PENDING) {
            throw new RuleViolationException("Referral phone verification is required before release");
        }
        Booking booking = bookings.lockBySalonIdAndId(actor.salonId(), qualifyingBookingId).orElseThrow(() -> new NotFoundException("Qualifying booking not found"));
        if (!booking.getCustomerId().equals(referral.getReferredCustomerId()) || booking.getStatus() != BookingStatus.COMPLETED || booking.getTotalMinor() <= 0 || booking.getWalletPaymentReference() == null || booking.getWalletPaymentReference().isBlank()) {
            throw new RuleViolationException("Referral requires the referred customer's completed paid first visit");
        }
        Visit qualifyingVisit = visits.findBySalonIdAndBookingId(actor.salonId(), qualifyingBookingId).orElseThrow(() -> new RuleViolationException("Referral requires a completed visit record"));
        if (!qualifyingVisit.getCustomerId().equals(referral.getReferredCustomerId()) || !qualifyingVisit.getBookingId().equals(qualifyingBookingId) || !qualifyingVisit.getServiceId().equals(booking.getServiceId()) || qualifyingVisit.getTotalMinor() <= 0 || visits.countBySalonIdAndCustomerId(actor.salonId(), referral.getReferredCustomerId()) != 1) {
            throw new RuleViolationException("Referral requires the referred customer's first paid visit");
        }
        Wallet referrerWallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), referral.getReferrerCustomerId()).orElseThrow(() -> new NotFoundException("Referrer wallet not found"));
        if (referral.getStatus() == ReferralStatus.PHONE_VERIFIED) referral.markQualified(qualifyingBookingId);
        else if (!qualifyingBookingId.equals(referral.getQualifyingBookingId())) throw new RuleViolationException("Referral qualifying booking does not match pending reward");
        referrerWallet.creditPromo(referral.getReferrerRewardMinor());
        referral.release();
        ledger.save(new WalletTransaction(actor.salonId(), referral.getReferrerCustomerId(), LedgerType.REFERRAL_BONUS, 0, referral.getReferrerRewardMinor(), "PKR", "COMPLETED", "referral:" + referral.getId(), actor.actorId(), "Promotional, non-withdrawable reward"));
        idempotency.complete(idempotencyKey, actor.salonId(), "referral.release", referral.getId().toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "REFERRAL_REWARDS_RELEASED", "Referral", referral.getId(), "newCustomerDiscount=" + referral.getNewCustomerDiscountMinor());
        return referral;
    }

    @Transactional
    public Wallet adjustWallet(ActorContext actor, UUID customerId, long cashDeltaMinor, long promoDeltaMinor, String reason, String idempotencyKey) {
        actor.require("adjust_wallet");
        if ((cashDeltaMinor == 0 && promoDeltaMinor == 0) || reason == null || reason.isBlank()) throw new IllegalArgumentException("Adjustment amount and reason are required");
        String normalizedReason = reason.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields("wallet.adjust", actor.actorId(), customerId, cashDeltaMinor, promoDeltaMinor, normalizedReason);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "wallet.adjust", requestFingerprint);
        if (existing != null) return wallets.findById(customerId).orElseThrow(() -> new NotFoundException("Wallet not found"));
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), customerId).orElseThrow(() -> new NotFoundException("Wallet not found"));
        long cash = Math.abs(cashDeltaMinor), promo = Math.abs(promoDeltaMinor);
        if (cashDeltaMinor > 0) wallet.creditCash(cash); else if (cashDeltaMinor < 0) wallet.debitCash(cash);
        if (promoDeltaMinor > 0) wallet.creditPromo(promo); else if (promoDeltaMinor < 0) wallet.debitPromo(promo);
        LedgerDirection direction = cashDeltaMinor < 0 || promoDeltaMinor < 0 ? LedgerDirection.DEBIT : LedgerDirection.CREDIT;
        ledger.save(new WalletTransaction(actor.salonId(), customerId, LedgerType.ADMIN_ADJUSTMENT, direction, cash, promo, "PKR", "COMPLETED", "adjustment:" + idempotencyKey, actor.actorId(), normalizedReason));
        idempotency.complete(idempotencyKey, actor.salonId(), "wallet.adjust", customerId.toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "WALLET_ADJUSTED", "Wallet", customerId, normalizedReason);
        return wallet;
    }

    @Transactional
    public Wallet refundServicePayment(ActorContext actor, UUID customerId, UUID bookingId, String reason, String idempotencyKey) {
        actor.require("refund_service");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Refund reason is required");
        String normalizedReason = reason.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields("service.refund", actor.actorId(), customerId, bookingId, normalizedReason);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "service.refund", requestFingerprint);
        if (existing != null) return wallets.findById(customerId).orElseThrow(() -> new NotFoundException("Wallet not found"));
        WalletTransaction original = ledger.findBySalonIdAndReferenceIdAndType(actor.salonId(), "service:" + bookingId, LedgerType.SERVICE_PAYMENT).orElseThrow(() -> new NotFoundException("Service payment ledger entry not found"));
        if (!original.getCustomerId().equals(customerId)) throw new RuleViolationException("Customer does not own this service payment");
        if (ledger.existsBySalonIdAndReferenceIdAndType(actor.salonId(), "refund:" + bookingId, LedgerType.REFUND)) throw new ConflictException("Service payment has already been refunded");
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), customerId).orElseThrow(() -> new NotFoundException("Wallet not found"));
        wallet.refund(original.getCashAmountMinor(), original.getPromoAmountMinor());
        ledger.save(new WalletTransaction(actor.salonId(), customerId, LedgerType.REFUND, LedgerDirection.CREDIT, original.getCashAmountMinor(), original.getPromoAmountMinor(), "PKR", "COMPLETED", "refund:" + bookingId, actor.actorId(), normalizedReason));
        businessLedger.save(new BusinessLedgerEntry(actor.salonId(), "SERVICE_REFUND", original.getCashAmountMinor() + original.getPromoAmountMinor(), "refund:" + bookingId, normalizedReason));
        idempotency.complete(idempotencyKey, actor.salonId(), "service.refund", bookingId.toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "SERVICE_REFUNDED", "Booking", bookingId, normalizedReason);
        return wallet;
    }

    @Transactional
    public Wallet reverseTransaction(ActorContext actor, UUID customerId, UUID transactionId, String reason, String idempotencyKey) {
        actor.require("reverse_transaction");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reversal reason is required");
        String normalizedReason = reason.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields("wallet.reverse", actor.actorId(), customerId, transactionId, normalizedReason);
        String existing = idempotency.begin(idempotencyKey, actor.salonId(), "wallet.reverse", requestFingerprint);
        if (existing != null) return wallets.findById(customerId).orElseThrow(() -> new NotFoundException("Wallet not found"));
        WalletTransaction original = ledger.findById(transactionId).orElseThrow(() -> new NotFoundException("Ledger transaction not found"));
        if (!original.getSalonId().equals(actor.salonId()) || !original.getCustomerId().equals(customerId)) throw new RuleViolationException("Transaction ownership mismatch");
        if (ledger.existsBySalonIdAndReferenceIdAndType(actor.salonId(), "reversal:" + transactionId, LedgerType.REVERSAL)) throw new ConflictException("Transaction has already been reversed");
        Wallet wallet = wallets.lockBySalonIdAndCustomerId(actor.salonId(), customerId).orElseThrow(() -> new NotFoundException("Wallet not found"));
        LedgerDirection reversalDirection;
        switch (original.getDirection()) {
            case CREDIT -> { if (original.getCashAmountMinor() > 0) wallet.debitCash(original.getCashAmountMinor()); if (original.getPromoAmountMinor() > 0) wallet.debitPromo(original.getPromoAmountMinor()); reversalDirection = LedgerDirection.DEBIT; }
            case DEBIT -> { wallet.refund(original.getCashAmountMinor(), original.getPromoAmountMinor()); reversalDirection = LedgerDirection.CREDIT; }
            case RESERVE -> { wallet.releaseCash(original.getCashAmountMinor()); reversalDirection = LedgerDirection.RELEASE; }
            case RELEASE -> { wallet.reserveCash(original.getCashAmountMinor()); reversalDirection = LedgerDirection.RESERVE; }
            default -> throw new RuleViolationException("Unsupported transaction direction");
        }
        ledger.save(new WalletTransaction(actor.salonId(), customerId, LedgerType.REVERSAL, reversalDirection, original.getCashAmountMinor(), original.getPromoAmountMinor(), "PKR", "COMPLETED", "reversal:" + transactionId, actor.actorId(), normalizedReason));
        idempotency.complete(idempotencyKey, actor.salonId(), "wallet.reverse", transactionId.toString(), requestFingerprint);
        audit.record(actor.salonId(), actor.actorId(), "WALLET_TRANSACTION_REVERSED", "WalletTransaction", transactionId, normalizedReason);
        return wallet;
    }

    private static void requireCustomerActor(ActorContext actor, String message) {
        if (actor.role() != ActorRole.CUSTOMER) throw new ActorContext.AuthorizationException(message);
    }

    public static class NotFoundException extends RuntimeException { public NotFoundException(String message) { super(message); } }
    public static class ConflictException extends RuntimeException { public ConflictException(String message) { super(message); } }
    public static class RuleViolationException extends RuntimeException { public RuleViolationException(String message) { super(message); } }
}
