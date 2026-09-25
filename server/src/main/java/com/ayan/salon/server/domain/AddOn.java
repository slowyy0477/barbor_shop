package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/** Salon-owned optional extra. Prices and duration are always server-derived. */
@Entity
@Table(name = "add_ons")
public class AddOn extends TenantEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 600)
    private String description;
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(nullable = false)
    private boolean active = true;
    @Version
    private long version;

    protected AddOn() {}
    public AddOn(UUID salonId, String name, long priceMinor, int durationMinutes, UUID serviceId, String description) {
        super(salonId);
        validate(name, priceMinor, durationMinutes);
        this.name = name.trim(); this.priceMinor = priceMinor; this.durationMinutes = durationMinutes;
        this.serviceId = serviceId; this.description = normalizeDescription(description);
    }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPriceMinor() { return priceMinor; }
    public int getDurationMinutes() { return durationMinutes; }
    public UUID getServiceId() { return serviceId; }
    public boolean isActive() { return active; }
    public void update(String name, long priceMinor, int durationMinutes, UUID serviceId, String description, boolean active) {
        validate(name, priceMinor, durationMinutes);
        this.name = name.trim(); this.priceMinor = priceMinor; this.durationMinutes = durationMinutes;
        this.serviceId = serviceId; this.description = normalizeDescription(description); this.active = active;
    }
    public void archive() { active = false; }
    private static void validate(String name, long priceMinor, int durationMinutes) {
        if (name == null || name.isBlank() || name.trim().length() > 120) throw new IllegalArgumentException("Add-on name is required");
        if (priceMinor < 0) throw new IllegalArgumentException("Add-on price cannot be negative");
        if (durationMinutes <= 0 || durationMinutes > 24 * 60) throw new IllegalArgumentException("Add-on duration is invalid");
    }
    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > 600) throw new IllegalArgumentException("Add-on description is too long");
        return value.trim();
    }
}
