package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visits")
public class Visit extends TenantEntity {
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "booking_id", nullable = false, unique = true) private UUID bookingId;
    @Column(name = "service_id", nullable = false) private UUID serviceId;
    @Column(name = "staff_id") private UUID staffId;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(name = "next_due_at") private Instant nextDueAt;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    protected Visit() {}
    public Visit(UUID salonId, UUID customerId, UUID bookingId, UUID serviceId, UUID staffId, Instant completedAt, Instant nextDueAt, long totalMinor) { super(salonId); this.customerId = customerId; this.bookingId = bookingId; this.serviceId = serviceId; this.staffId = staffId; this.completedAt = completedAt; this.nextDueAt = nextDueAt; this.totalMinor = totalMinor; }
    public UUID getBookingId() { return bookingId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getStaffId() { return staffId; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getNextDueAt() { return nextDueAt; }
    public long getTotalMinor() { return totalMinor; }
}
