package com.cornerchair.salon.model;

/** Immutable reminder lifecycle record. */
public final class Reminder {
    private final String id;
    private final String salonId;
    private final String customerId;
    private final String serviceId;
    private final String visitId;
    private final String cycleKey;
    private final long dueAtMillis;
    private final ReminderStatus status;

    public Reminder(String id,
                    String salonId,
                    String customerId,
                    String serviceId,
                    String visitId,
                    String cycleKey,
                    long dueAtMillis,
                    ReminderStatus status) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(customerId, "customerId");
        DomainTime.requireNonBlank(serviceId, "serviceId");
        DomainTime.requireNonBlank(visitId, "visitId");
        DomainTime.requireNonBlank(cycleKey, "cycleKey");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        this.id = id;
        this.salonId = salonId;
        this.customerId = customerId;
        this.serviceId = serviceId;
        this.visitId = visitId;
        this.cycleKey = cycleKey;
        this.dueAtMillis = dueAtMillis;
        this.status = status;
    }

    public static Reminder scheduled(String id, Visit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit is required");
        }
        return new Reminder(id, visit.getSalonId(), visit.getCustomerId(), visit.getServiceId(),
                visit.getId(), visit.getCycleKey(), visit.getNextServiceDueAtMillis(),
                ReminderStatus.SCHEDULED);
    }

    public static Reminder optedOut(String id, Visit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit is required");
        }
        return new Reminder(id, visit.getSalonId(), visit.getCustomerId(), visit.getServiceId(),
                visit.getId(), visit.getCycleKey(), visit.getNextServiceDueAtMillis(),
                ReminderStatus.OPTED_OUT);
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

    public String getVisitId() {
        return visitId;
    }

    public String getCycleKey() {
        return cycleKey;
    }

    public long getDueAtMillis() {
        return dueAtMillis;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == ReminderStatus.SCHEDULED || status == ReminderStatus.SENT;
    }

    public Reminder markSent() {
        return transition(ReminderStatus.SENT);
    }

    public Reminder markBooked() {
        return transition(ReminderStatus.BOOKED);
    }

    public Reminder cancel() {
        return transition(ReminderStatus.CANCELLED);
    }

    private Reminder transition(ReminderStatus target) {
        if (status == target) {
            return this;
        }
        if (!isActive()) {
            throw new IllegalStateException("cannot change reminder from " + status);
        }
        if (target != ReminderStatus.SENT && target != ReminderStatus.BOOKED
                && target != ReminderStatus.CANCELLED) {
            throw new IllegalStateException("invalid reminder transition to " + target);
        }
        return new Reminder(id, salonId, customerId, serviceId, visitId, cycleKey, dueAtMillis, target);
    }
}
