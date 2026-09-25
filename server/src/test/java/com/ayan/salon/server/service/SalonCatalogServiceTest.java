package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.HaircutStyle;
import com.ayan.salon.server.domain.SalonSettings;
import com.ayan.salon.server.domain.ServiceOffering;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalonCatalogServiceTest {
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
    @Mock HaircutStyleRepository haircutStyles;
    @Mock AuditService audit;

    private SalonService service;

    @BeforeEach
    void setUp() {
        service = new SalonService(settings, customers, wallets, services, staff, bookings, referrals,
                reminders, paymentMethods, audit, new IdempotencyService(mock(IdempotencyRecordRepository.class)), haircutStyles);
    }

    @Test
    void ownerCanCreateAndArchiveStyleWithoutDeletingHistory() {
        when(haircutStyles.save(any(HaircutStyle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        HaircutStyle created = service.createHaircutStyle(ownerActor, salon, "Fade", "https://cdn.example/fade.jpg",
                "Fade haircut", 1_500, "Low fade", 1, null);

        assertEquals("Fade", created.getName());
        assertEquals(1_500, created.getPriceMinor());
        assertTrue(created.isActive());
        assertNull(created.getServiceId());
        when(haircutStyles.findBySalonIdAndId(salon, created.getId())).thenReturn(Optional.of(created));
        HaircutStyle archived = service.archiveHaircutStyle(ownerActor, salon, created.getId());

        assertFalse(archived.isActive());
        verify(haircutStyles, times(2)).save(created);
        verify(audit, times(2)).record(eq(salon), eq(owner), anyString(), eq("HaircutStyle"), eq(created.getId()), any());
    }

    @Test
    void styleCanBeLinkedToAServiceSoCustomersCanBookTheLook() {
        UUID serviceId = UUID.randomUUID();
        when(services.findBySalonIdAndId(salon, serviceId))
                .thenReturn(Optional.of(new ServiceOffering(salon, "Signature cut", 150_000, 30, "Hair")));
        when(haircutStyles.save(any(HaircutStyle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HaircutStyle created = service.createHaircutStyle(ownerActor, salon, "Textured crop",
                "https://cdn.example/crop.jpg", "Textured crop", 120_000, "Short textured finish", 2, serviceId);

        assertEquals(serviceId, created.getServiceId());
        when(haircutStyles.findBySalonIdAndId(salon, created.getId())).thenReturn(Optional.of(created));
        HaircutStyle cleared = service.updateHaircutStyle(ownerActor, salon, created.getId(), created.getName(),
                created.getPhotoUri(), created.getPhotoAltText(), created.getPriceMinor(), created.getDescription(),
                2, true, null);

        assertNull(cleared.getServiceId(), "clearing the link must make the style informational again");
    }

    @Test
    void styleCannotLinkToAnotherSalonsService() {
        UUID foreignService = UUID.randomUUID();
        when(services.findBySalonIdAndId(salon, foreignService)).thenReturn(Optional.empty());

        assertThrows(WalletService.NotFoundException.class, () -> service.createHaircutStyle(ownerActor, salon,
                "Borrowed look", "https://cdn.example/borrowed.jpg", null, 90_000, null, 0, foreignService));
        verify(haircutStyles, never()).save(any(HaircutStyle.class));
    }

    @Test
    void activeListingHidesArchivedStylesAndInactiveListingIsPermissionGated() {
        when(haircutStyles.findBySalonIdAndActiveTrueOrderByDisplayOrderAscNameAsc(salon)).thenReturn(List.of());
        assertTrue(service.listHaircutStyles(ownerActor, salon, false).isEmpty());
        verify(haircutStyles).findBySalonIdAndActiveTrueOrderByDisplayOrderAscNameAsc(salon);

        ActorContext customer = new ActorContext(UUID.randomUUID(), salon, ActorRole.CUSTOMER, Set.of());
        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.listHaircutStyles(customer, salon, true));
        verify(haircutStyles, never()).findBySalonIdOrderByDisplayOrderAscNameAsc(salon);
    }

    @Test
    void ownerCanUpdateBrandingAndCustomerCannot() {
        SalonSettings salonSettings = new SalonSettings(salon, "Ayan", "address", "03101234567");
        when(settings.findById(salon)).thenReturn(Optional.of(salonSettings));
        SalonSettings updated = service.updateBranding(ownerActor, salon, "#123456", "https://cdn.example/logo.png");
        assertEquals("#123456", updated.getPrimaryColor());
        assertEquals("https://cdn.example/logo.png", updated.getLogoUri());

        ActorContext customer = new ActorContext(UUID.randomUUID(), salon, ActorRole.CUSTOMER, Set.of());
        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.updateBranding(customer, salon, "#FFFFFF", null));
        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.getSettings(customer, salon));
    }
}
