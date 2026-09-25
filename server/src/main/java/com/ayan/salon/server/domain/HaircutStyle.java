package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * A salon-owned visual style card.  Images are referenced by a URI so the API
 * never has to persist raw image bytes or accept unbounded base64 payloads.
 */
@Entity
@Table(name = "haircut_styles")
public class HaircutStyle extends TenantEntity {
    private static final int MAX_PHOTO_URI_LENGTH = 2048;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "photo_uri", nullable = false, length = MAX_PHOTO_URI_LENGTH)
    private String photoUri;
    @Column(name = "photo_alt_text", length = 160)
    private String photoAltText;
    @Column(length = 600)
    private String description;
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(nullable = false)
    private boolean active = true;
    /** Optional link to a bookable service in the same salon. */
    @Column(name = "service_id")
    private UUID serviceId;
    @Version
    private long version;

    protected HaircutStyle() {}

    public HaircutStyle(UUID salonId, String name, String photoUri, long priceMinor,
                        String description, int displayOrder) {
        super(salonId);
        validate(name, photoUri, priceMinor, displayOrder);
        this.name = name.trim();
        this.photoUri = photoUri.trim();
        this.photoAltText = this.name;
        this.description = normalizeDescription(description);
        this.priceMinor = priceMinor;
        this.displayOrder = displayOrder;
    }

    /** Compatibility overload for callers that do not yet provide alt text. */
    public HaircutStyle(UUID salonId, String name, String photoUri, long priceMinor,
                        String description) {
        this(salonId, name, photoUri, priceMinor, description, 0);
    }

    public String getName() { return name; }
    public String getPhotoUri() { return photoUri; }
    /** Alias useful to clients that call image references URLs. */
    public String getPhotoUrl() { return photoUri; }
    public String getPhotoAltText() { return photoAltText; }
    public String getDescription() { return description; }
    public long getPriceMinor() { return priceMinor; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public UUID getServiceId() { return serviceId; }

    /**
     * Links this style to a bookable service, or clears the link when null.
     * Salon ownership of the service is validated by the service layer before
     * this is called.
     */
    public void linkService(UUID serviceId) { this.serviceId = serviceId; }

    public void update(String name, String photoUri, long priceMinor, String description,
                       int displayOrder, boolean active) {
        validate(name, photoUri, priceMinor, displayOrder);
        this.name = name.trim();
        this.photoUri = photoUri.trim();
        this.photoAltText = this.name;
        this.description = normalizeDescription(description);
        this.priceMinor = priceMinor;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public void updatePhoto(String photoUri, String photoAltText) {
        if (photoUri == null || photoUri.isBlank() || photoUri.trim().length() > MAX_PHOTO_URI_LENGTH) {
            throw new IllegalArgumentException("Photo URI is required and must be at most 2048 characters");
        }
        rejectUnsafeUri(photoUri);
        if (photoAltText != null && (photoAltText.trim().length() > 160
                || photoAltText.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Photo alt text is too long");
        }
        this.photoUri = photoUri.trim();
        this.photoAltText = photoAltText == null || photoAltText.isBlank() ? this.name : photoAltText.trim();
    }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    private static void validate(String name, String photoUri, long priceMinor, int displayOrder) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new IllegalArgumentException("Haircut style name is required and must be at most 120 characters");
        }
        if (photoUri == null || photoUri.isBlank() || photoUri.trim().length() > MAX_PHOTO_URI_LENGTH) {
            throw new IllegalArgumentException("Photo URI is required and must be at most 2048 characters");
        }
        rejectUnsafeUri(photoUri);
        if (priceMinor < 0) throw new IllegalArgumentException("Haircut style price cannot be negative");
        if (displayOrder < 0) throw new IllegalArgumentException("Display order cannot be negative");
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > 600) throw new IllegalArgumentException("Haircut style description is too long");
        return value.trim();
    }

    private static void rejectUnsafeUri(String value) {
        String normalized = value.trim();
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Photo URI contains control characters");
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:")) {
            throw new IllegalArgumentException("Photo URI scheme is not allowed");
        }
    }
}
