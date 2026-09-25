package com.cornerchair.salon.model;

/** Completed service record used for history, repeat-customer metrics, and reminders. */
public final class Visit {
    private final String id;
    private final String bookingId;
    private final String salonId;
    private final String customerId;
    private final String serviceId;
    private final String staffId;
    private final long completedAtMillis;
    private final long paidAmountPkr;
    private final long addOnRevenuePkr;
    private final long nextServiceDueAtMillis;
    private final int repeatCycleDays;

    public Visit(String id,
                 String bookingId,
                 String salonId,
                 String customerId,
                 String serviceId,
                 String staffId,
                 long completedAtMillis,
                 long paidAmountPkr,
                 long addOnRevenuePkr,
                 long nextServiceDueAtMillis) {
        this(id, bookingId, salonId, customerId, serviceId, staffId, completedAtMillis,
                paidAmountPkr, addOnRevenuePkr, nextServiceDueAtMillis, 0);
    }

    public Visit(String id,
                 String bookingId,
                 String salonId,
                 String customerId,
                 String serviceId,
                 String staffId,
                 long completedAtMillis,
                 long paidAmountPkr,
                 long addOnRevenuePkr,
                 long nextServiceDueAtMillis,
                 int repeatCycleDays) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(bookingId, "bookingId");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(customerId, "customerId");
        DomainTime.requireNonBlank(serviceId, "serviceId");
        if (paidAmountPkr < 0 || addOnRevenuePkr < 0) {
            throw new IllegalArgumentException("visit amounts cannot be negative");
        }
        this.id = id;
        this.bookingId = bookingId;
        this.salonId = salonId;
        this.customerId = customerId;
        this.serviceId = serviceId;
        this.staffId = staffId == null ? "" : staffId;
        this.completedAtMillis = completedAtMillis;
        this.paidAmountPkr = paidAmountPkr;
        this.addOnRevenuePkr = addOnRevenuePkr;
        this.nextServiceDueAtMillis = nextServiceDueAtMillis;
        this.repeatCycleDays = repeatCycleDays;
    }

    public static Visit fromCompletedBooking(String visitId,
                                             Booking booking,
                                             long completedAtMillis,
                                             long paidAmountPkr,
                                             SalonSettings settings) {
        return fromCompletedBooking(visitId, booking, completedAtMillis, paidAmountPkr,
                settings == null ? 0 : settings.getReminderCycleDays(), settings);
    }

    /** Uses the service's editable repeat cycle, falling back to salon default when zero. */
    public static Visit fromCompletedBooking(String visitId,
                                             Booking booking,
                                             long completedAtMillis,
                                             long paidAmountPkr,
                                             int serviceRepeatCycleDays,
                                             SalonSettings settings) {
        if (booking == null || settings == null) {
            throw new IllegalArgumentException("booking and settings are required");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("visit requires a completed booking");
        }
        int repeatDays = serviceRepeatCycleDays > 0
                ? serviceRepeatCycleDays : settings.getReminderCycleDays();
        return new Visit(
                visitId,
                booking.getId(),
                booking.getSalonId(),
                booking.getCustomerId(),
                booking.getServiceId(),
                booking.getStaffId(),
                completedAtMillis,
                paidAmountPkr,
                booking.getAddOnRevenuePkr(),
                DomainTime.plusDays(completedAtMillis, repeatDays),
                repeatDays);
    }

    public String getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getSalonId() {
        return salonId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getStaffId() {
        return staffId;
    }

    public long getCompletedAtMillis() {
        return completedAtMillis;
    }

    public long getPaidAmountPkr() {
        return paidAmountPkr;
    }

    public long getAddOnRevenuePkr() {
        return addOnRevenuePkr;
    }

    public long getNextServiceDueAtMillis() {
        return nextServiceDueAtMillis;
    }

    public int getRepeatCycleDays() {
        return repeatCycleDays;
    }

    public boolean isPaid() {
        return paidAmountPkr > 0;
    }

    public String getCycleKey() {
        return customerId + "|" + serviceId;
    }
}
