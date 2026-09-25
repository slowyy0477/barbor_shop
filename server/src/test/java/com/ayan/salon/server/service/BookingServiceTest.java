package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.Booking;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.DomainTypes.BookingStatus;
import com.ayan.salon.server.domain.DomainTypes.PaymentMethod;
import com.ayan.salon.server.domain.repository.AuditLogRepository;
import com.ayan.salon.server.domain.repository.BookingRepository;
import com.ayan.salon.server.domain.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ActorContext ownerActor = new ActorContext(owner, salon, ActorRole.OWNER, Set.of());

    @Mock BookingRepository bookings;
    @Mock IdempotencyRecordRepository idempotencyRecords;
    @Mock AuditLogRepository auditRecords;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, new IdempotencyService(idempotencyRecords), new AuditService(auditRecords));
    }

    @Test
    void cancellationRequiresReasonAndDoesNotTouchBooking() {
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(ownerActor, salon,
                UUID.randomUUID(), BookingStatus.CANCELLED, "  ", "booking-cancel-1"));
        verify(bookings, never()).lockBySalonIdAndId(any(), any());
    }

    @Test
    void ownerCanCancelConfirmedBookingAndAuditTransition() {
        Booking booking = booking();
        booking.confirm();
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));

        Booking result = service.updateStatus(ownerActor, salon, booking.getId(), BookingStatus.CANCELLED,
                "Customer called to cancel", "booking-cancel-2");

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(auditRecords).save(any());
        verify(idempotencyRecords).save(any());
    }

    @Test
    void noShowAndCompletionAreTerminallySeparated() {
        Booking booking = booking();
        booking.confirm();
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));

        Booking noShow = service.updateStatus(ownerActor, salon, booking.getId(), BookingStatus.NO_SHOW,
                "Did not arrive", "booking-noshow-1");
        assertEquals(BookingStatus.NO_SHOW, noShow.getStatus());
        assertThrows(WalletService.RuleViolationException.class, () -> service.updateStatus(ownerActor, salon,
                booking.getId(), BookingStatus.COMPLETED, "", "booking-complete-1"));
    }

    @Test
    void repeatedSameStatusWithNewKeyIsIdempotentAtStateBoundary() {
        Booking booking = booking();
        booking.confirm();
        booking.cancel();
        when(bookings.lockBySalonIdAndId(salon, booking.getId())).thenReturn(java.util.Optional.of(booking));

        Booking result = service.updateStatus(ownerActor, salon, booking.getId(), BookingStatus.CANCELLED,
                "Repeat callback", "booking-cancel-3");

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(auditRecords, never()).save(any());
        verify(idempotencyRecords).save(any());
    }

    private Booking booking() {
        return new Booking(salon, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400), 30_000, 0, PaymentMethod.CASH);
    }
}
