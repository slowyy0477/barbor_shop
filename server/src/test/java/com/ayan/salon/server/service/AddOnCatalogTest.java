package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.AddOn;
import com.ayan.salon.server.domain.Booking;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.ServiceOffering;
import com.ayan.salon.server.domain.Staff;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.DomainTypes.PaymentMethod;
import com.ayan.salon.server.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddOnCatalogTest {
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
    @Mock AddOnRepository addOns;

    private SalonService salonService;

    @BeforeEach
    void setUp() {
        salonService = new SalonService(settings, customers, wallets, services, staff, bookings, referrals,
                reminders, paymentMethods, audit, new IdempotencyService(idempotencyRecords), null, addOns);
        lenient().when(reminders.findBySalonIdAndCustomerIdAndServiceIdAndStatusIn(any(), any(), any(), anyList()))
                .thenReturn(List.of());
    }

    @Test
    void bookingUsesCatalogValuesAndKeepsAnImmutableSnapshot() {
        UUID customerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Customer customer = new Customer(salon, "Customer", "03001234567", "hash");
        ServiceOffering service = new ServiceOffering(salon, "Haircut", 80_000, 35, "Hair");
        Staff barber = new Staff(salon, "Barber", "03007654321", ActorRole.BARBER);
        AddOn beard = new AddOn(salon, "Beard Trim", 9_900, 12, service.getId(), "Shape and trim");
        Instant startsAt = Instant.now().plusSeconds(3600);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        when(services.findBySalonIdAndId(salon, service.getId())).thenReturn(Optional.of(service));
        when(staff.lockBySalonIdAndId(salon, staffId)).thenReturn(Optional.of(barber));
        when(addOns.findActiveApplicable(salon, service.getId())).thenReturn(List.of(beard));
        when(bookings.hasOverlap(eq(salon), eq(staffId), eq(startsAt), any(Instant.class), anyList())).thenReturn(false);
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = salonService.createBooking(ownerActor, salon, customerId, service.getId(), staffId,
                startsAt, PaymentMethod.CASH, List.of(beard.getId()), "catalog-booking-1");

        assertEquals(89_900, booking.getTotalMinor());
        assertEquals(47 * 60L, booking.getEndsAt().getEpochSecond() - startsAt.getEpochSecond());
        String snapshot = booking.getAddOnSnapshot();
        assertEquals(true, snapshot.contains("Beard Trim"));
        assertEquals(true, snapshot.contains("9900"));

        beard.update("Beard Trim", 12_500, 20, service.getId(), "Updated", true);
        assertEquals(snapshot, booking.getAddOnSnapshot());
    }

    @Test
    void inactiveOrCrossSalonAddOnIdsAreRejected() {
        UUID customerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Customer customer = new Customer(salon, "Customer", "03001234567", "hash");
        ServiceOffering service = new ServiceOffering(salon, "Haircut", 80_000, 35, "Hair");
        Staff barber = new Staff(salon, "Barber", "03007654321", ActorRole.BARBER);
        AddOn unavailable = new AddOn(UUID.randomUUID(), "Other salon", 9_900, 12, service.getId(), null);
        Instant startsAt = Instant.now().plusSeconds(3600);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        when(services.findBySalonIdAndId(salon, service.getId())).thenReturn(Optional.of(service));
        when(staff.lockBySalonIdAndId(salon, staffId)).thenReturn(Optional.of(barber));
        when(addOns.findActiveApplicable(salon, service.getId())).thenReturn(List.of());

        assertThrows(WalletService.NotFoundException.class,
                () -> salonService.createBooking(ownerActor, salon, customerId, service.getId(), staffId,
                        startsAt, PaymentMethod.CASH, List.of(unavailable.getId()), "catalog-booking-2"));
        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void ownerCrudDefaultsNewAddOnToActiveWhenRequested() {
        when(addOns.save(any(AddOn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddOn created = salonService.createAddOn(ownerActor, salon, "Head Massage", 19_900, 18,
                null, "Relax", true);

        assertEquals(true, created.isActive());
        when(addOns.findBySalonIdAndId(salon, created.getId())).thenReturn(Optional.of(created));
        AddOn archived = salonService.archiveAddOn(ownerActor, salon, created.getId());
        assertEquals(false, archived.isActive());
    }
}
