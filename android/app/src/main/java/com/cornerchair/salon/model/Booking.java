package com.cornerchair.salon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable salon booking with explicit status transitions and optional add-ons. */
public final class Booking {
    private final String id;
    private final String salonId;
    private final String customerId;
    private final String serviceId;
    private final String staffId;
    private final String dateKey;
    private final int startMinutes;
    private final int durationMinutes;
    private final long basePricePkr;
    private final List<BookingAddOn> addOns;
    private final BookingStatus status;
    private final long createdAtMillis;
    private final long updatedAtMillis;
    private final String serviceCycleKey;

    /** New customer bookings begin in PENDING until an owner confirms the slot. */
    public Booking(String id,
                   String salonId,
                   String customerId,
                   String serviceId,
                   String staffId,
                   String dateKey,
                   int startMinutes,
                   int durationMinutes,
                   long basePricePkr,
                   List<BookingAddOn> addOns,
                   long createdAtMillis) {
        this(id, salonId, customerId, serviceId, staffId, dateKey, startMinutes, durationMinutes,
                basePricePkr, addOns, BookingStatus.PENDING, createdAtMillis, createdAtMillis, null);
    }

    public Booking(String id,
                   String salonId,
                   String customerId,
                   String serviceId,
                   String staffId,
                   String dateKey,
                   int startMinutes,
                   int durationMinutes,
                   long basePricePkr,
                   List<BookingAddOn> addOns,
                   BookingStatus status,
                   long createdAtMillis,
                   long updatedAtMillis,
                   String serviceCycleKey) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(customerId, "customerId");
        DomainTime.requireNonBlank(serviceId, "serviceId");
        DomainTime.requireNonBlank(dateKey, "dateKey");
        if (startMinutes < 0 || startMinutes >= 24 * 60) {
            throw new IllegalArgumentException("startMinutes must be within the day");
        }
        if (durationMinutes <= 0 || startMinutes + durationMinutes > 24 * 60) {
            throw new IllegalArgumentException("duration must fit within the day");
        }
        if (basePricePkr < 0) {
            throw new IllegalArgumentException("basePricePkr cannot be negative");
        }
        if (addOns == null || status == null) {
            throw new IllegalArgumentException("addOns and status are required");
        }
        this.id = id;
        this.salonId = salonId;
        this.customerId = customerId;
        this.serviceId = serviceId;
        this.staffId = staffId == null ? "" : staffId;
        this.dateKey = dateKey;
        this.startMinutes = startMinutes;
        this.durationMinutes = durationMinutes;
        this.basePricePkr = basePricePkr;
        ArrayList<BookingAddOn> copiedAddOns = new ArrayList<BookingAddOn>(addOns);
        int addOnMinutes = 0;
        for (BookingAddOn addOn : copiedAddOns) {
            if (addOn == null) {
                throw new IllegalArgumentException("addOns cannot contain null");
            }
            addOnMinutes += addOn.getExtraDurationMinutes();
        }
        if (startMinutes + durationMinutes + addOnMinutes > 24 * 60) {
            throw new IllegalArgumentException("duration including add-ons must fit within the day");
        }
        this.addOns = Collections.unmodifiableList(copiedAddOns);
        this.status = status;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.serviceCycleKey = serviceCycleKey == null || serviceCycleKey.trim().length() == 0
                ? customerId + "|" + serviceId + "|" + dateKey
                : serviceCycleKey;
    }

    public String getId() {
        return id;
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

    public String getDateKey() {
        return dateKey;
    }

    public int getStartMinutes() {
        return startMinutes;
    }

    public int getEndMinutes() {
        int addOnMinutes = 0;
        for (BookingAddOn addOn : addOns) {
            addOnMinutes += addOn.getExtraDurationMinutes();
        }
        return startMinutes + durationMinutes + addOnMinutes;
    }

    public int getDurationMinutes() {
        int total = durationMinutes;
        for (BookingAddOn addOn : addOns) {
            total += addOn.getExtraDurationMinutes();
        }
        return total;
    }

    public long getBasePricePkr() {
        return basePricePkr;
    }

    public long getAddOnRevenuePkr() {
        long total = 0L;
        for (BookingAddOn addOn : addOns) {
            total += addOn.getPricePkr();
        }
        return total;
    }

    public long getTotalPkr() {
        return basePricePkr + getAddOnRevenuePkr();
    }

    public List<BookingAddOn> getAddOns() {
        return addOns;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public String getServiceCycleKey() {
        return serviceCycleKey;
    }

    /** Stable customer/service family key used to suppress a due reminder after booking. */
    public String getServiceFamilyKey() {
        return customerId + "|" + serviceId;
    }

    /**
     * Compares every booking field that must remain stable across a status transition. The status
     * and updated timestamp are intentionally excluded because confirmation changes those fields.
     */
    public boolean sameImmutableDetails(Booking other) {
        if (other == null
                || !id.equals(other.id)
                || !salonId.equals(other.salonId)
                || !customerId.equals(other.customerId)
                || !serviceId.equals(other.serviceId)
                || !staffId.equals(other.staffId)
                || !dateKey.equals(other.dateKey)
                || startMinutes != other.startMinutes
                || durationMinutes != other.durationMinutes
                || basePricePkr != other.basePricePkr
                || createdAtMillis != other.createdAtMillis
                || !serviceCycleKey.equals(other.serviceCycleKey)
                || addOns.size() != other.addOns.size()) {
            return false;
        }
        for (int index = 0; index < addOns.size(); index++) {
            if (!addOns.get(index).sameDetails(other.addOns.get(index))) {
                return false;
            }
        }
        return true;
    }

    public boolean reservesSlot() {
        return status == BookingStatus.CONFIRMED;
    }

    public boolean overlaps(Booking other) {
        if (other == null || !salonId.equals(other.salonId) || !dateKey.equals(other.dateKey)) {
            return false;
        }
        if (staffId.length() > 0 && other.staffId.length() > 0 && !staffId.equals(other.staffId)) {
            return false;
        }
        return startMinutes < other.getEndMinutes() && other.startMinutes < getEndMinutes();
    }

    public Booking confirm(long atMillis) {
        return withStatus(BookingStatus.CONFIRMED, atMillis);
    }

    public Booking complete(long atMillis) {
        return withStatus(BookingStatus.COMPLETED, atMillis);
    }

    public Booking cancel(long atMillis) {
        return withStatus(BookingStatus.CANCELLED, atMillis);
    }

    public Booking markNoShow(long atMillis) {
        return withStatus(BookingStatus.NO_SHOW, atMillis);
    }

    public Booking withStatus(BookingStatus nextStatus, long atMillis) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("cannot change booking from " + status + " to " + nextStatus);
        }
        return new Booking(id, salonId, customerId, serviceId, staffId, dateKey, startMinutes,
                durationMinutes, basePricePkr, addOns, nextStatus, createdAtMillis, atMillis,
                serviceCycleKey);
    }
}
