package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.domain.DomainTypes.*;
import com.ayan.salon.server.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ActorContext ownerActor = new ActorContext(owner, salon, ActorRole.OWNER, Set.of());
    @Mock WalletRepository wallets;
    @Mock DepositRepository deposits;
    @Mock WithdrawalRepository withdrawals;
    @Mock WalletTransactionRepository ledger;
    @Mock BookingRepository bookings;
    @Mock ReferralRepository referrals;
    @Mock SalonSettingsRepository settings;
    @Mock VisitRepository visits;
    @Mock BusinessLedgerRepository businessLedger;
    @Mock ReminderService reminderService;
    @Mock ServiceOfferingRepository serviceOfferings;
    @Mock AuditLogRepository auditRepository;
    @Mock IdempotencyRecordRepository idempotencyRepository;
    @Mock NotificationGateway notifications;
    private WalletService service;
    private AuditService audit;
    private IdempotencyService idempotency;

    @BeforeEach
    void setUp() {
        audit = new AuditService(auditRepository);
        idempotency = new IdempotencyService(idempotencyRepository);
        service = new WalletService(wallets, deposits, withdrawals, ledger, bookings, referrals, settings, visits,
                businessLedger, audit, idempotency, reminderService, serviceOfferings, notifications);
    }

    @Test
    void approvedDepositCreditsCashAndBonusSeparately() {
        Deposit deposit = new Deposit(salon, customer, 50_000, PaymentMethod.MANUAL_PROVIDER, "EASYPAISA", "EP-1", null, 5_000);
        Wallet wallet = new Wallet(salon, customer);
        when(deposits.lockBySalonIdAndId(salon, deposit.getId())).thenReturn(java.util.Optional.of(deposit));
        when(wallets.lockBySalonIdAndCustomerId(salon, customer)).thenReturn(java.util.Optional.of(wallet));
        Deposit result = service.approveDeposit(ownerActor, deposit.getId(), "deposit-approve-1");
        assertEquals(50_000, wallet.getCashAvailableMinor());
        assertEquals(5_000, wallet.getPromoAvailableMinor());
        assertEquals(DepositStatus.APPROVED, result.getStatus());
        ArgumentCaptor<WalletTransaction> captured = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(ledger, times(2)).save(captured.capture());
        assertEquals(2, captured.getAllValues().stream().filter(t -> t.getType() == LedgerType.DEPOSIT_CASH || t.getType() == LedgerType.DEPOSIT_BONUS).count());
        ArgumentCaptor<String> receiptPayload = ArgumentCaptor.forClass(String.class);
        verify(notifications).send(eq(salon), eq(customer), eq("WALLET_DEPOSIT_RECEIPT"), receiptPayload.capture());
        assertTrue(receiptPayload.getValue().contains("\"paidCreditMinor\":50000"));
        assertTrue(receiptPayload.getValue().contains("\"bonusCreditMinor\":5000"));
        assertFalse(receiptPayload.getValue().contains("EP-1"), "Provider references must not be copied into receipt payloads");
    }

    @Test
    void firstDepositBonusIsGrantedOnlyOnceForTheLockedWallet() {
        Deposit first = new Deposit(salon, customer, 50_000, PaymentMethod.MANUAL_PROVIDER, "EASYPAISA", "EP-FIRST", null, 5_000);
        Deposit second = new Deposit(salon, customer, 25_000, PaymentMethod.MANUAL_PROVIDER, "EASYPAISA", "EP-SECOND", null, 5_000);
        Wallet wallet = new Wallet(salon, customer);
        when(deposits.lockBySalonIdAndId(salon, first.getId())).thenReturn(java.util.Optional.of(first));
        when(deposits.lockBySalonIdAndId(salon, second.getId())).thenReturn(java.util.Optional.of(second));
        when(wallets.lockBySalonIdAndCustomerId(salon, customer)).thenReturn(java.util.Optional.of(wallet));

        service.approveDeposit(ownerActor, first.getId(), "deposit-approve-first");
        service.approveDeposit(ownerActor, second.getId(), "deposit-approve-second");

        assertEquals(75_000, wallet.getCashAvailableMinor());
        assertEquals(5_000, wallet.getPromoAvailableMinor());
        assertTrue(wallet.isFirstDepositBonusClaimed());
        assertEquals(5_000, first.getBonusGrantedMinor());
        assertEquals(0, second.getBonusGrantedMinor());
        verify(ledger, times(3)).save(any(WalletTransaction.class));
        verify(ledger, times(1)).save(argThat(value -> value.getType() == LedgerType.DEPOSIT_BONUS && value.getPromoAmountMinor() == 5_000));
    }

    @Test
    void withdrawalReservationMakesSecondReservationFail() {
        Wallet wallet = new Wallet(salon, customer); wallet.creditCash(50_000);
        ActorContext customerActor = new ActorContext(customer, salon, ActorRole.CUSTOMER, Set.of("request_withdrawal"));
        SalonSettings s = new SalonSettings(salon, "Ayan", "address", "0310");
        when(settings.findById(salon)).thenReturn(java.util.Optional.of(s));
        when(withdrawals.sumActiveSince(any(), any(), any(), any())).thenReturn(0L);
        when(wallets.lockBySalonIdAndCustomerId(salon, customer)).thenReturn(java.util.Optional.of(wallet));
        when(withdrawals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Withdrawal first = service.requestWithdrawal(customerActor, 50_000, "EASYPAISA", "token-1", "withdraw-1");
        assertEquals(0, wallet.getCashAvailableMinor());
        assertEquals(50_000, wallet.getCashReservedMinor());
        assertThrows(Wallet.InsufficientBalanceException.class, () -> service.requestWithdrawal(customerActor, 50_000, "EASYPAISA", "token-2", "withdraw-2"));
        assertEquals(WithdrawalStatus.PENDING, first.getStatus());
    }

    @Test
    void withdrawalSameKeyWithDifferentRequestIsRejectedBeforeAnotherReservation() {
        Wallet wallet = new Wallet(salon, customer);
        wallet.creditCash(100_000);
        ActorContext customerActor = new ActorContext(customer, salon, ActorRole.CUSTOMER, Set.of("request_withdrawal"));
        SalonSettings s = new SalonSettings(salon, "Ayan", "address", "0310");
        when(settings.findById(salon)).thenReturn(java.util.Optional.of(s));
        when(withdrawals.sumActiveSince(any(), any(), any(), any())).thenReturn(0L);
        when(wallets.lockBySalonIdAndCustomerId(salon, customer)).thenReturn(java.util.Optional.of(wallet));
        when(withdrawals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestWithdrawal(customerActor, 50_000, "EASYPAISA", "token-1", "withdraw-same-key");

        assertThrows(IdempotencyService.ConflictException.class,
                () -> service.requestWithdrawal(customerActor, 60_000, "EASYPAISA", "token-1", "withdraw-same-key"));
        verify(withdrawals, times(1)).save(any(Withdrawal.class));
        assertEquals(50_000, wallet.getCashReservedMinor());
    }

    @Test
    void ownerCannotSubmitCustomerDepositOrWithdrawalRequest() {
        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.submitDeposit(ownerActor, 50_000, "EASYPAISA", "EP-owner", null, "deposit-owner"));
        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.requestWithdrawal(ownerActor, 10_000, "EASYPAISA", "03001234567", "withdraw-owner"));
        verifyNoInteractions(deposits, withdrawals, settings, wallets);
    }

    @Test
    void staffCannotCompleteAnotherStaffMembersBooking() {
        UUID assignedStaff = UUID.randomUUID();
        UUID otherStaff = UUID.randomUUID();
        Booking booking = new Booking(salon, customer, UUID.randomUUID(), assignedStaff,
                Instant.now(), Instant.now().plusSeconds(1800), 30_000, 0, PaymentMethod.CASH);
        booking.confirm();
        ActorContext staffActor = new ActorContext(otherStaff, salon, ActorRole.BARBER, Set.of("complete_service"));
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));

        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.completeServicePayment(staffActor, booking.getId(), "service-other-staff"));
        verify(settings, never()).findById(any());
        verify(wallets, never()).lockBySalonIdAndCustomerId(any(), any());
    }

    @Test
    void servicePaymentConsumesPromoBeforeCashAndIsIdempotentAtStateBoundary() {
        Wallet wallet = new Wallet(salon, customer); wallet.creditCash(20_000); wallet.creditPromo(15_000);
        Booking booking = new Booking(salon, customer, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1800), 30_000, 0, PaymentMethod.WALLET); booking.confirm();
        SalonSettings s = new SalonSettings(salon, "Ayan", "address", "0310");
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));
        when(wallets.lockBySalonIdAndCustomerId(salon, customer)).thenReturn(java.util.Optional.of(wallet));
        when(settings.findById(salon)).thenReturn(java.util.Optional.of(s));
        when(visits.findBySalonIdAndBookingId(salon, booking.getId())).thenReturn(java.util.Optional.empty());
        when(visits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Booking result = service.completeServicePayment(ownerActor, booking.getId(), "service-1");
        assertEquals(0, wallet.getPromoAvailableMinor());
        assertEquals(5_000, wallet.getCashAvailableMinor());
        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        verify(ledger).save(argThat(t -> t.getType() == LedgerType.SERVICE_PAYMENT && t.getCashAmountMinor() == 15_000 && t.getPromoAmountMinor() == 15_000));
        verify(notifications).send(eq(salon), eq(customer), eq("WALLET_PAYMENT_RECEIPT"), contains("\"bookingId\":\"" + booking.getId() + "\""));
    }

    @Test
    void cashServiceCompletionCreatesVisitAndRevenueWithoutWalletDebit() {
        UUID serviceId = UUID.randomUUID();
        Booking booking = new Booking(salon, customer, serviceId, UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1800), 30_000, 0, PaymentMethod.CASH);
        booking.confirm();
        SalonSettings s = new SalonSettings(salon, "Ayan", "address", "0310");
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));
        when(settings.findById(salon)).thenReturn(java.util.Optional.of(s));
        when(visits.findBySalonIdAndBookingId(salon, booking.getId())).thenReturn(java.util.Optional.empty());
        when(visits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = service.completeServicePayment(ownerActor, booking.getId(), "cash-service-1");

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        verify(wallets, never()).lockBySalonIdAndCustomerId(any(), any());
        verify(visits).save(any(Visit.class));
        verify(reminderService).schedule(eq(salon), eq(customer), eq(serviceId), any(Instant.class), any(UUID.class));
        verify(businessLedger).save(argThat(entry -> true));
        verify(ledger, never()).save(any(WalletTransaction.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void manualProviderCompletionUsesTheSameVisitPathWithoutWalletDebit() {
        UUID serviceId = UUID.randomUUID();
        Booking booking = new Booking(salon, customer, serviceId, UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1800), 30_000, 0, PaymentMethod.MANUAL_PROVIDER);
        booking.confirm();
        SalonSettings s = new SalonSettings(salon, "Ayan", "address", "0310");
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));
        when(settings.findById(salon)).thenReturn(java.util.Optional.of(s));
        when(visits.findBySalonIdAndBookingId(salon, booking.getId())).thenReturn(java.util.Optional.empty());
        when(visits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = service.completeServicePayment(ownerActor, booking.getId(), "manual-service-1");

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        verify(wallets, never()).lockBySalonIdAndCustomerId(any(), any());
        verify(visits).save(any(Visit.class));
        verify(businessLedger).save(any(BusinessLedgerEntry.class));
    }

    @Test
    void completionRejectsAlreadyTerminalBookingBeforeWalletDebit() {
        Booking booking = new Booking(salon, customer, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1800), 30_000, 0, PaymentMethod.WALLET);
        booking.confirm();
        booking.complete("already-paid");
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));

        assertThrows(WalletService.RuleViolationException.class, () -> service.completeServicePayment(ownerActor, booking.getId(), "service-terminal-1"));
        verify(wallets, never()).lockBySalonIdAndCustomerId(any(), any());
    }

    @Test
    void referralReleaseRequiresCompletedPaidFirstVisitAndCreditsReferrerPromo() {
        UUID referred = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Referral referral = new Referral(salon, customer, referred, "AYAN-1", 5_000, 10_000);
        referral.markPhoneVerified();
        Booking booking = new Booking(salon, referred, serviceId, UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1200), 30_000, 0, PaymentMethod.CASH); booking.confirm(); booking.complete("cash");
        Visit visit = new Visit(salon, referred, booking.getId(), serviceId, booking.getStaffId(), Instant.now(), Instant.now().plusSeconds(25L * 86_400L), 30_000);
        Wallet referrerWallet = new Wallet(salon, customer);
        when(referrals.lockBySalonIdAndId(salon, referral.getId())).thenReturn(java.util.Optional.of(referral));
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));
        when(visits.findBySalonIdAndBookingId(salon, booking.getId())).thenReturn(java.util.Optional.of(visit));
        when(visits.countBySalonIdAndCustomerId(salon, referred)).thenReturn(1L);
        when(wallets.lockBySalonIdAndCustomerId(salon, customer)).thenReturn(java.util.Optional.of(referrerWallet));
        Referral result = service.releaseReferral(ownerActor, referral.getId(), booking.getId(), "referral-1");
        assertEquals(5_000, referrerWallet.getPromoAvailableMinor());
        assertEquals(ReferralStatus.REWARD_GRANTED, result.getStatus());
        verify(ledger).save(argThat(t -> t.getType() == LedgerType.REFERRAL_BONUS && t.getPromoAmountMinor() == 5_000));
    }

    @Test
    void referralReleaseRejectsUnverifiedReferralAndMissingVisit() {
        UUID referred = UUID.randomUUID();
        Referral unverified = new Referral(salon, customer, referred, "AYAN-2", 5_000, 10_000);
        when(referrals.lockBySalonIdAndId(salon, unverified.getId())).thenReturn(java.util.Optional.of(unverified));
        assertThrows(WalletService.RuleViolationException.class, () -> service.releaseReferral(ownerActor, unverified.getId(), UUID.randomUUID(), "referral-unverified"));

        Referral verified = new Referral(salon, customer, referred, "AYAN-3", 5_000, 10_000);
        verified.markPhoneVerified();
        Booking booking = new Booking(salon, referred, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1200), 30_000, 0, PaymentMethod.CASH);
        booking.confirm(); booking.complete("cash");
        when(referrals.lockBySalonIdAndId(salon, verified.getId())).thenReturn(java.util.Optional.of(verified));
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));
        when(visits.findBySalonIdAndBookingId(salon, booking.getId())).thenReturn(java.util.Optional.empty());
        assertThrows(WalletService.RuleViolationException.class, () -> service.releaseReferral(ownerActor, verified.getId(), booking.getId(), "referral-no-visit"));
    }

    @Test
    void referralReleaseRejectsAReferredCustomerWithPriorVisits() {
        UUID referred = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Referral referral = new Referral(salon, customer, referred, "AYAN-4", 5_000, 10_000);
        referral.markPhoneVerified();
        Booking booking = new Booking(salon, referred, serviceId, UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1200), 30_000, 0, PaymentMethod.CASH);
        booking.confirm();
        booking.complete("cash");
        Visit visit = new Visit(salon, referred, booking.getId(), serviceId, booking.getStaffId(), Instant.now(), Instant.now().plusSeconds(25L * 86_400L), 30_000);
        when(referrals.lockBySalonIdAndId(salon, referral.getId())).thenReturn(java.util.Optional.of(referral));
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));
        when(visits.findBySalonIdAndBookingId(salon, booking.getId())).thenReturn(java.util.Optional.of(visit));
        when(visits.countBySalonIdAndCustomerId(salon, referred)).thenReturn(2L);

        assertThrows(WalletService.RuleViolationException.class, () -> service.releaseReferral(ownerActor, referral.getId(), booking.getId(), "referral-prior-visit"));
        verify(wallets, never()).lockBySalonIdAndCustomerId(any(), any());
    }
}
