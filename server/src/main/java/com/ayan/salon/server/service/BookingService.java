package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.Booking;
import com.ayan.salon.server.domain.DomainTypes.BookingStatus;
import com.ayan.salon.server.domain.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owner-facing booking state transitions. Completion is intentionally kept in
 * {@link WalletService} because it also creates the payment ledger, visit, and reminder.
 */
@Service
public class BookingService {
    private final BookingRepository bookings;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public BookingService(BookingRepository bookings, IdempotencyService idempotency, AuditService audit) {
        this.bookings = bookings;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public Booking updateStatus(ActorContext actor, UUID salonId, UUID bookingId,
                                BookingStatus requestedStatus, String reason, String idempotencyKey) {
        actor.requireSalon(salonId);
        actor.require("manage_bookings");
        if (requestedStatus == null) throw new IllegalArgumentException("Booking status is required");
        if (requestedStatus == BookingStatus.PENDING) {
            throw new WalletService.RuleViolationException("A booking cannot be moved back to pending");
        }
        if ((requestedStatus == BookingStatus.CANCELLED || requestedStatus == BookingStatus.NO_SHOW)
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required for cancellation or no-show");
        }

        String normalizedReason = reason == null || reason.isBlank() ? "" : reason.trim();
        String requestFingerprint = IdempotencyService.fingerprintFields(
                "booking.status", actor.actorId(), bookingId, requestedStatus, normalizedReason);
        String existing = idempotency.begin(idempotencyKey, salonId, "booking.status", requestFingerprint);
        if (existing != null) {
            UUID existingId = IdempotencyService.responseId(existing);
            return bookings.findById(existingId)
                    .filter(value -> salonId.equals(value.getSalonId()))
                    .orElseThrow(() -> new WalletService.NotFoundException("Booking not found"));
        }

        Booking booking = bookings.lockBySalonIdAndId(salonId, bookingId)
                .orElseThrow(() -> new WalletService.NotFoundException("Booking not found"));
        BookingStatus currentStatus = booking.getStatus();
        if (currentStatus == requestedStatus) {
            idempotency.complete(idempotencyKey, salonId, "booking.status", booking.getId().toString(), requestFingerprint);
            return booking;
        }

        switch (requestedStatus) {
            case CONFIRMED -> {
                if (currentStatus != BookingStatus.PENDING) {
                    throw new WalletService.RuleViolationException("Only pending bookings can be confirmed");
                }
                booking.confirm();
            }
            case CANCELLED -> {
                requireOpen(currentStatus, requestedStatus);
                booking.cancel();
            }
            case NO_SHOW -> {
                requireOpen(currentStatus, requestedStatus);
                booking.noShow();
            }
            case COMPLETED -> throw new WalletService.RuleViolationException(
                    "Use the service completion endpoint to complete a booking");
            case PENDING -> throw new WalletService.RuleViolationException(
                    "A booking cannot be moved back to pending");
        }

        idempotency.complete(idempotencyKey, salonId, "booking.status", booking.getId().toString(), requestFingerprint);
        String detail = "from=" + currentStatus + ", to=" + requestedStatus
                + (normalizedReason.isEmpty() ? "" : ", reason=" + normalizedReason);
        audit.record(salonId, actor.actorId(), "BOOKING_STATUS_UPDATED", "Booking", booking.getId(), detail);
        return booking;
    }

    private void requireOpen(BookingStatus currentStatus, BookingStatus requestedStatus) {
        if (currentStatus != BookingStatus.PENDING && currentStatus != BookingStatus.CONFIRMED) {
            throw new WalletService.RuleViolationException(
                    "Booking cannot be moved from " + currentStatus + " to " + requestedStatus);
        }
    }
}
