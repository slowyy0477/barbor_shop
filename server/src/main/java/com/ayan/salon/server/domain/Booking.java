package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import static com.ayan.salon.server.domain.DomainTypes.BookingStatus;
import static com.ayan.salon.server.domain.DomainTypes.PaymentMethod;

@Entity
@Table(name = "bookings")
public class Booking extends TenantEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "staff_id")
    private UUID staffId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "total_minor", nullable = false)
    private long totalMinor;
    @Column(name = "add_on_minor", nullable = false)
    private long addOnMinor;
    /** JSON snapshot of catalog IDs/names/prices selected at booking time. */
    @Column(name = "add_on_snapshot", length = 4000)
    private String addOnSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 24)
    private PaymentMethod paymentMethod;
    @Column(name = "wallet_payment_reference", length = 120)
    private String walletPaymentReference;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Version
    private long version;

    protected Booking() {}
    public Booking(UUID salonId, UUID customerId, UUID serviceId, UUID staffId, Instant startsAt, Instant endsAt,
                   long totalMinor, long addOnMinor, PaymentMethod paymentMethod) {
        super(salonId); if (totalMinor <= 0 || !endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Invalid booking amount/time");
        this.customerId = customerId; this.serviceId = serviceId; this.staffId = staffId; this.startsAt = startsAt; this.endsAt = endsAt;
        this.totalMinor = totalMinor; this.addOnMinor = addOnMinor; this.paymentMethod = paymentMethod;
    }
    public UUID getCustomerId() { return customerId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getStaffId() { return staffId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public long getTotalMinor() { return totalMinor; }
    public long getAddOnMinor() { return addOnMinor; }
    public String getAddOnSnapshot() { return addOnSnapshot; }
    public void setAddOnSnapshot(String addOnSnapshot) { this.addOnSnapshot = addOnSnapshot; }
    public BookingStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getWalletPaymentReference() { return walletPaymentReference; }
    public void confirm() { if (status != BookingStatus.PENDING) throw new IllegalStateException("Booking is not pending"); status = BookingStatus.CONFIRMED; }
    public void cancel() { if (status == BookingStatus.COMPLETED) throw new IllegalStateException("Completed booking cannot be cancelled"); status = BookingStatus.CANCELLED; }
    public void noShow() { if (status != BookingStatus.CONFIRMED && status != BookingStatus.PENDING) throw new IllegalStateException("Booking cannot be marked no-show"); status = BookingStatus.NO_SHOW; }
    public void complete(String paymentReference) { if (status != BookingStatus.CONFIRMED && status != BookingStatus.PENDING) throw new IllegalStateException("Booking cannot be completed"); status = BookingStatus.COMPLETED; walletPaymentReference = paymentReference; completedAt = Instant.now(); }
}
