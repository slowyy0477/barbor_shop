package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import static com.ayan.salon.server.domain.DomainTypes.ReminderStatus;

@Entity
@Table(name = "reminders")
public class Reminder extends TenantEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.SCHEDULED;
    @Column(nullable = false)
    private boolean optedOut;
    @Column(name = "source_visit_id")
    private UUID sourceVisitId;

    protected Reminder() {}
    public Reminder(UUID salonId, UUID customerId, UUID serviceId, Instant dueAt, UUID sourceVisitId) { super(salonId); this.customerId = customerId; this.serviceId = serviceId; this.dueAt = dueAt; this.sourceVisitId = sourceVisitId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getServiceId() { return serviceId; }
    public Instant getDueAt() { return dueAt; }
    public ReminderStatus getStatus() { return status; }
    public boolean isOptedOut() { return optedOut; }
    public void optOut() { optedOut = true; status = ReminderStatus.CANCELLED; }
    /**
     * A booking can be made after a reminder has already been dispatched. Keep
     * that reminder in history and mark either state as converted/booked.
     */
    public void booked() { if (status == ReminderStatus.SCHEDULED || status == ReminderStatus.SENT) status = ReminderStatus.BOOKED; }
    public void sent() { if (status == ReminderStatus.SCHEDULED) status = ReminderStatus.SENT; }
}
