package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "services")
public class ServiceOffering extends TenantEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 600)
    private String description;
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(length = 80)
    private String category;
    @Column(nullable = false)
    private boolean active = true;
    @Version
    private long version;

    protected ServiceOffering() {}
    public ServiceOffering(UUID salonId, String name, long priceMinor, int durationMinutes, String category) {
        super(salonId); if (priceMinor < 0 || durationMinutes <= 0) throw new IllegalArgumentException("Invalid service price/duration");
        this.name = name; this.priceMinor = priceMinor; this.durationMinutes = durationMinutes; this.category = category;
    }
    public String getName() { return name; }
    public long getPriceMinor() { return priceMinor; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getCategory() { return category; }
    public boolean isActive() { return active; }
    public void update(String name, long priceMinor, int durationMinutes, String category, boolean active) { if (priceMinor < 0 || durationMinutes <= 0) throw new IllegalArgumentException("Invalid service price/duration"); this.name = name; this.priceMinor = priceMinor; this.durationMinutes = durationMinutes; this.category = category; this.active = active; }
}
