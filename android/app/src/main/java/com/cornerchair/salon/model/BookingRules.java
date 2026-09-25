package com.cornerchair.salon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Slot reservation and duplicate-booking rules. */
public final class BookingRules {
    private BookingRules() {
    }

    public static List<Booking> confirmBooking(List<Booking> existing,
                                               Booking pending,
                                               long confirmedAtMillis) {
        if (existing == null || pending == null) {
            throw new IllegalArgumentException("existing bookings and pending booking are required");
        }
        if (pending.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("only pending bookings can be confirmed");
        }
        Booking sameId = null;
        for (Booking booking : existing) {
            if (booking != null && booking.getId().equals(pending.getId())) {
                if (sameId != null) {
                    throw new BookingIdReuseException(pending.getId(),
                            "multiple records already use this booking id");
                }
                sameId = booking;
                if (!booking.sameImmutableDetails(pending)) {
                    throw new BookingIdReuseException(pending.getId(),
                            "immutable booking fields differ from the stored request");
                }
                if (booking.getStatus() == BookingStatus.CONFIRMED) {
                    // Safe retry: confirmation has already committed.
                    return Collections.unmodifiableList(new ArrayList<Booking>(existing));
                }
                if (booking.getStatus() != BookingStatus.PENDING) {
                    throw new IllegalStateException("booking already finished with " + booking.getStatus());
                }
            }
        }
        for (Booking booking : existing) {
            if (booking == null || booking.getId().equals(pending.getId())) {
                continue;
            }
            if (blocksSlot(booking) && booking.overlaps(pending)) {
                throw new SlotUnavailableException(pending.getDateKey(), pending.getStartMinutes(),
                        pending.getStaffId());
            }
            if (blocksSlot(booking)
                    && booking.getCustomerId().equals(pending.getCustomerId())
                    && booking.getServiceCycleKey().equals(pending.getServiceCycleKey())) {
                throw new DuplicateBookingException(pending.getServiceCycleKey());
            }
        }
        ArrayList<Booking> result = new ArrayList<Booking>(existing);
        Booking confirmed = pending.confirm(confirmedAtMillis);
        boolean replaced = false;
        for (int index = 0; index < result.size(); index++) {
            Booking booking = result.get(index);
            if (booking != null && booking.getId().equals(pending.getId())) {
                result.set(index, confirmed);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            result.add(confirmed);
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isSlotAvailable(List<Booking> existing, Booking candidate) {
        if (existing == null || candidate == null) {
            return false;
        }
        for (Booking booking : existing) {
            if (booking != null && blocksSlot(booking) && booking.overlaps(candidate)) {
                return false;
            }
        }
        return true;
    }

    /** Pending requests are held briefly so two customers cannot be confirmed into one slot. */
    private static boolean blocksSlot(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED;
    }

    public static final class SlotUnavailableException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final String dateKey;
        private final int startMinutes;
        private final String staffId;

        private SlotUnavailableException(String dateKey, int startMinutes, String staffId) {
            super("slot is unavailable on " + dateKey + " at minute " + startMinutes
                    + (staffId == null || staffId.length() == 0 ? "" : " for staff " + staffId));
            this.dateKey = dateKey;
            this.startMinutes = startMinutes;
            this.staffId = staffId == null ? "" : staffId;
        }

        public String getDateKey() {
            return dateKey;
        }

        public int getStartMinutes() {
            return startMinutes;
        }

        public String getStaffId() {
            return staffId;
        }
    }

    public static final class DuplicateBookingException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final String cycleKey;

        private DuplicateBookingException(String cycleKey) {
            super("booking already exists for service cycle " + cycleKey);
            this.cycleKey = cycleKey;
        }

        public String getCycleKey() {
            return cycleKey;
        }
    }

    public static final class BookingIdReuseException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final String bookingId;
        private final String detail;

        private BookingIdReuseException(String bookingId, String detail) {
            super("booking id " + bookingId + " cannot be reused: " + detail);
            this.bookingId = bookingId;
            this.detail = detail;
        }

        public String getBookingId() {
            return bookingId;
        }

        public String getDetail() {
            return detail;
        }
    }
}
