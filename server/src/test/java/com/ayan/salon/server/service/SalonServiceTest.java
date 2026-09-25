package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.Booking;
import com.ayan.salon.server.domain.AuthAccount;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.IdempotencyRecord;
import com.ayan.salon.server.domain.Referral;
import com.ayan.salon.server.domain.ServiceOffering;
import com.ayan.salon.server.domain.Staff;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.DomainTypes.PaymentMethod;
import com.ayan.salon.server.domain.repository.AuditLogRepository;
import com.ayan.salon.server.domain.repository.AuthAccountRepository;
import com.ayan.salon.server.domain.repository.BookingRepository;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.IdempotencyRecordRepository;
import com.ayan.salon.server.domain.repository.PaymentMethodConfigRepository;
import com.ayan.salon.server.domain.repository.ReferralRepository;
import com.ayan.salon.server.domain.repository.ReminderRepository;
import com.ayan.salon.server.domain.repository.SalonSettingsRepository;
import com.ayan.salon.server.domain.repository.ServiceOfferingRepository;
import com.ayan.salon.server.domain.repository.StaffRepository;
import com.ayan.salon.server.domain.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ActorContext ownerActor = new ActorContext(owner, salon, ActorRole.OWNER, Set.of());

    @Mock SalonSettingsRepository settings;
    @Mock CustomerRepository customers;
    @Mock WalletRepository wallets;
    @Mock ServiceOfferingRepository services;
    @Mock StaffRepository staff;
    @Mock BookingRepository bookings;
    @Mock ReferralRepository referrals;
    @Mock ReminderRepository reminders;
    @Mock PaymentMethodConfigRepository paymentMethods;
    @Mock AuditService audit;
    @Mock IdempotencyRecordRepository idempotencyRecords;
    @Mock AuthAccountRepository authAccounts;

    private SalonService service;
    private final Map<String, IdempotencyRecord> idempotencyStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyRecords.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(idempotencyStore.get(invocation.getArgument(0))));
        lenient().when(idempotencyRecords.save(org.mockito.ArgumentMatchers.any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> {
                    IdempotencyRecord value = invocation.getArgument(0);
                    idempotencyStore.put(value.getIdempotencyKey(), value);
                    return value;
                });
        service = new SalonService(settings, customers, wallets, services, staff, bookings, referrals, reminders, paymentMethods, audit,
                new IdempotencyService(idempotencyRecords));
    }

    @Test
    void rejectsInactiveCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(salon, "Inactive", "0300", "hash");
        customer.deactivate();
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(java.util.Optional.of(customer));

        assertThrows(WalletService.NotFoundException.class, () -> service.createBooking(ownerActor, salon, customerId,
                UUID.randomUUID(), null, Instant.now().plusSeconds(3600), PaymentMethod.CASH, 0, 0));
        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void rejectsPastStartAndMismatchedAddOnValues() {
        assertThrows(WalletService.RuleViolationException.class, () -> service.createBooking(ownerActor, salon,
                UUID.randomUUID(), UUID.randomUUID(), null, Instant.now().minusSeconds(1), PaymentMethod.CASH, 0, 0));

        UUID customerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(ownerActor, salon, customerId,
                UUID.randomUUID(), null, Instant.now().plusSeconds(3600), PaymentMethod.CASH, 9_900, 0));
    }

    @Test
    void acceptsFutureBookingWithScopedActiveStaffAndServerDerivedTotals() {
        UUID customerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Customer customer = new Customer(salon, "Active", "0302", "hash-3");
        ServiceOffering serviceOffering = new ServiceOffering(salon, "Haircut", 30_000, 30, "Hair");
        Staff member = new Staff(salon, "Barber", "0303", ActorRole.BARBER);
        Instant startsAt = Instant.now().plusSeconds(3600);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(java.util.Optional.of(customer));
        when(services.findBySalonIdAndId(salon, serviceOffering.getId())).thenReturn(java.util.Optional.of(serviceOffering));
        when(staff.lockBySalonIdAndId(salon, staffId)).thenReturn(java.util.Optional.of(member));
        when(bookings.hasOverlap(eq(salon), eq(staffId), eq(startsAt), any(Instant.class), anyList())).thenReturn(false);
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = service.createBooking(ownerActor, salon, customerId, serviceOffering.getId(), staffId,
                startsAt, PaymentMethod.CASH, 0, 0);

        assertEquals(30_000, booking.getTotalMinor());
        assertEquals(30 * 60L, booking.getEndsAt().getEpochSecond() - startsAt.getEpochSecond());
        verify(staff).lockBySalonIdAndId(salon, staffId);
    }

    @Test
    void rejectsClientSuppliedAddOnPriceAndDurationUntilCatalogExists() {
        assertThrows(WalletService.RuleViolationException.class, () -> service.createBooking(ownerActor, salon,
                UUID.randomUUID(), UUID.randomUUID(), null, Instant.now().plusSeconds(3600), PaymentMethod.CASH, 9_900, 15));
        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void walletBookingCountsExistingCommitmentsBeforeConfirming() {
        UUID customerId = UUID.randomUUID();
        ServiceOffering serviceOffering = new ServiceOffering(salon, "Haircut", 30_000, 30, "Hair");
        Customer customer = new Customer(salon, "Wallet customer", "03021234567", "hash-wallet");
        com.ayan.salon.server.domain.Wallet wallet = new com.ayan.salon.server.domain.Wallet(salon, customerId);
        wallet.creditCash(50_000);
        Instant startsAt = Instant.now().plusSeconds(3600);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        when(services.findBySalonIdAndId(salon, serviceOffering.getId())).thenReturn(Optional.of(serviceOffering));
        when(wallets.lockBySalonIdAndCustomerId(salon, customerId)).thenReturn(Optional.of(wallet));
        when(bookings.sumActiveWalletTotals(eq(salon), eq(customerId), eq(PaymentMethod.WALLET), anyList())).thenReturn(30_000L);

        assertThrows(WalletService.RuleViolationException.class, () -> service.createBooking(ownerActor, salon, customerId,
                serviceOffering.getId(), null, startsAt, PaymentMethod.WALLET, 0, 0));
        verify(bookings, never()).save(any(Booking.class));
        verify(wallets).lockBySalonIdAndCustomerId(salon, customerId);
    }

    @Test
    void customerCannotCreateBookingForAnotherCustomer() {
        UUID otherCustomer = UUID.randomUUID();
        ActorContext customerActor = new ActorContext(UUID.randomUUID(), salon, ActorRole.CUSTOMER, Set.of("create_booking"));

        assertThrows(ActorContext.AuthorizationException.class, () -> service.createBooking(customerActor, salon, otherCustomer,
                UUID.randomUUID(), null, Instant.now().plusSeconds(3600), PaymentMethod.CASH, 0, 0));
        verify(customers, never()).findBySalonIdAndId(any(), any());
        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void keyedBookingRejectsMissingIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(ownerActor, salon, UUID.randomUUID(),
                UUID.randomUUID(), null, Instant.now().plusSeconds(3600), PaymentMethod.CASH, 0, 0, "  "));
        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void keyedBookingRetryReturnsOriginalBookingWithoutCreatingAnotherRow() {
        UUID customerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Customer customer = new Customer(salon, "Active", "03021234567", "hash-4");
        ServiceOffering serviceOffering = new ServiceOffering(salon, "Haircut", 30_000, 30, "Hair");
        Staff member = new Staff(salon, "Barber", "03031234567", ActorRole.BARBER);
        Instant startsAt = Instant.now().plusSeconds(3600);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        when(services.findBySalonIdAndId(salon, serviceOffering.getId())).thenReturn(Optional.of(serviceOffering));
        when(staff.lockBySalonIdAndId(salon, staffId)).thenReturn(Optional.of(member));
        when(bookings.hasOverlap(eq(salon), eq(staffId), eq(startsAt), any(Instant.class), anyList())).thenReturn(false);
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookings.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return idempotencyStore.containsKey("booking-key")
                    ? Optional.ofNullable(savedBooking)
                    : Optional.empty();
        });

        Booking first = service.createBooking(ownerActor, salon, customerId, serviceOffering.getId(), staffId,
                startsAt, PaymentMethod.CASH, 0, 0, "booking-key");
        savedBooking = first;
        Booking retry = service.createBooking(ownerActor, salon, customerId, serviceOffering.getId(), staffId,
                startsAt, PaymentMethod.CASH, 0, 0, "booking-key");

        assertEquals(first.getId(), retry.getId());
        verify(bookings).save(any(Booking.class));
    }

    private Booking savedBooking;

    @Test
    void referralRequiresActiveSameSalonCustomersAndCustomerOwnership() {
        UUID referrerId = UUID.randomUUID();
        UUID referredId = UUID.randomUUID();
        Customer referrer = new Customer(salon, "Referrer", "03001234567", "hash-r");
        Customer referred = new Customer(salon, "Referred", "03011234567", "hash-d");
        when(customers.findBySalonIdAndId(salon, referrerId)).thenReturn(Optional.of(referrer));
        when(customers.findBySalonIdAndId(salon, referredId)).thenReturn(Optional.of(referred));
        when(referrals.findBySalonIdAndReferredCustomerId(salon, referredId)).thenReturn(Optional.empty());
        when(settings.findById(salon)).thenReturn(Optional.of(new com.ayan.salon.server.domain.SalonSettings(salon, "Ayan", "address", "0300")));
        when(referrals.save(any(Referral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Referral created = service.createReferral(ownerActor, salon, referrerId, referredId, " ayan-100 ");
        assertEquals("AYAN-100", created.getCode());
        verify(referrals).save(any(Referral.class));

        ActorContext anotherCustomer = new ActorContext(UUID.randomUUID(), salon, ActorRole.CUSTOMER, Set.of("create_referral"));
        assertThrows(ActorContext.AuthorizationException.class, () -> service.createReferral(anotherCustomer, salon, referrerId, referredId, "AYAN-101"));
    }

    @Test
    void customerCreationDerivesCanonicalPhoneHashAndIgnoresClientHash() {
        when(customers.findBySalonIdAndPhone(any(), any())).thenReturn(Optional.empty());
        when(customers.findBySalonIdAndPhoneHash(any(), any())).thenReturn(Optional.empty());
        when(customers.findBySalonId(any())).thenReturn(java.util.List.of());
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wallets.save(any(com.ayan.salon.server.domain.Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = service.createCustomer(ownerActor, salon, "New customer", "+92 300 1234567", "client-controlled", true);

        assertEquals("03001234567", created.getPhone());
        assertNotEquals("client-controlled", created.getPhoneHash());
        assertEquals("f81b06138a382c8581be41ab39b1597c94261d6d1561c94e8b2cd6389fc7116a", created.getPhoneHash());
        assertTrue(created.isMarketingConsent());
    }

    @Test
    void ownerCustomerUpdateCanonicalizesPhoneResetsVerificationAndAuditsWithoutPii() {
        Customer customer = new Customer(salon, "Old name", "03001234567", PhoneIdentity.sha256("03001234567"));
        UUID customerId = customer.getId();
        customer.verifyPhone();
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        when(customers.findBySalonIdAndPhone(salon, "03111234567")).thenReturn(Optional.empty());
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256("03111234567"))).thenReturn(Optional.empty());
        when(customers.findBySalonId(salon)).thenReturn(java.util.List.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer updated = service.updateCustomer(ownerActor, salon, customerId, "  New name  ", "+92 311 1234567", true);

        assertEquals("New name", updated.getName());
        assertEquals("03111234567", updated.getPhone());
        assertEquals(PhoneIdentity.sha256("03111234567"), updated.getPhoneHash());
        assertTrue(updated.isMarketingConsent());
        assertTrue(!updated.isPhoneVerified(), "Changing the phone must require verification again");
        org.mockito.ArgumentCaptor<String> details = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(salon), eq(owner), eq("CUSTOMER_UPDATED"), eq("Customer"), eq(customer.getId()), details.capture());
        assertTrue(details.getValue().contains("phoneChanged=true"));
        assertTrue(details.getValue().contains("marketingConsent=true"));
        assertTrue(!details.getValue().contains("03111234567"));
    }

    @Test
    void ownerCustomerUpdateKeepsVerificationForEquivalentPhoneAndRejectsDuplicate() {
        Customer customer = new Customer(salon, "Existing", "03001234567", PhoneIdentity.sha256("03001234567"));
        UUID customerId = customer.getId();
        customer.verifyPhone();
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        when(customers.findBySalonIdAndPhone(salon, "03001234567")).thenReturn(Optional.of(customer));
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256("03001234567"))).thenReturn(Optional.of(customer));
        when(customers.findBySalonId(salon)).thenReturn(java.util.List.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer unchangedIdentity = service.updateCustomer(ownerActor, salon, customerId, "Renamed", "+92 300 1234567", false);
        assertEquals("Renamed", unchangedIdentity.getName());
        assertTrue(unchangedIdentity.isPhoneVerified());
        assertTrue(!unchangedIdentity.isMarketingConsent());

        Customer other = new Customer(salon, "Other", "03111234567", PhoneIdentity.sha256("03111234567"));
        UUID otherId = other.getId();
        when(customers.findBySalonIdAndId(salon, otherId)).thenReturn(Optional.of(other));
        when(customers.findBySalonIdAndPhone(salon, "03001234567")).thenReturn(Optional.of(customer));
        assertThrows(WalletService.ConflictException.class,
                () -> service.updateCustomer(ownerActor, salon, otherId, "Other", "03001234567", false));
        verify(customers, org.mockito.Mockito.times(1)).save(any(Customer.class));
    }

    @Test
    void customerCannotUseOwnerCustomerUpdate() {
        ActorContext customerActor = new ActorContext(UUID.randomUUID(), salon, ActorRole.CUSTOMER, Set.of());
        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.updateCustomer(customerActor, salon, UUID.randomUUID(), "Name", "03001234567", false));
        verify(customers, never()).findBySalonIdAndId(any(), any());
    }

    @Test
    void ownerCannotCreateCustomerWithAnActiveTeamPhone() {
        String phone = "03001234567";
        AuthAccount account = new AuthAccount(salon, PhoneIdentity.sha256(phone), ActorRole.OWNER, "[]");
        when(customers.findBySalonIdAndPhone(salon, phone)).thenReturn(Optional.empty());
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone))).thenReturn(Optional.empty());
        when(customers.findBySalonId(salon)).thenReturn(java.util.List.of());
        when(authAccounts.findBySalonIdAndPhoneHashAndStatus(salon, PhoneIdentity.sha256(phone),
                com.ayan.salon.server.domain.DomainTypes.AccountStatus.ACTIVE)).thenReturn(Optional.of(account));
        SalonService guarded = new SalonService(settings, customers, wallets, services, staff, bookings, referrals,
                reminders, paymentMethods, audit, new IdempotencyService(idempotencyRecords), null, null, authAccounts);

        assertThrows(WalletService.ConflictException.class,
                () -> guarded.createCustomer(ownerActor, salon, "Blocked", phone, false));
        verify(customers, never()).save(any(Customer.class));
        verify(wallets, never()).save(any(com.ayan.salon.server.domain.Wallet.class));
    }
}
