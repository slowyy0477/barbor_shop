package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.ayan.salon.server.domain.DomainTypes.AccountStatus;

@Entity
@Table(name = "customers")
public class Customer extends TenantEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(name = "phone_hash", nullable = false, length = 128)
    @JsonIgnore
    private String phoneHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountStatus status = AccountStatus.ACTIVE;
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;
    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;
    @Column(name = "app_instance_hash", length = 128)
    @JsonIgnore
    private String appInstanceHash;
    @Column(name = "last_ip_hash", length = 128)
    @JsonIgnore
    private String lastIpHash;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Version
    private long version;

    protected Customer() {}

    public Customer(UUID salonId, String name, String phone, String phoneHash) {
        super(salonId);
        this.name = name;
        this.phone = phone;
        this.phoneHash = phoneHash;
    }

    public String getName() { return name; }
    public String getPhone() { return phone; }
    @JsonIgnore
    public String getPhoneHash() { return phoneHash; }
    public AccountStatus getStatus() { return status; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public boolean isMarketingConsent() { return marketingConsent; }
    @JsonIgnore
    public String getAppInstanceHash() { return appInstanceHash; }
    @JsonIgnore
    public String getLastIpHash() { return lastIpHash; }
    public void verifyPhone() { this.phoneVerified = true; }
    public void setMarketingConsent(boolean value) { this.marketingConsent = value; }
    /** Customer-controlled profile fields; the verified mobile identity is immutable. */
    public void updateProfile(String name, boolean marketingConsent) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new IllegalArgumentException("Customer name is required");
        }
        this.name = name.trim();
        this.marketingConsent = marketingConsent;
    }
    /**
     * Owner-managed profile update. The service layer must provide a canonical
     * Pakistani mobile number and its server-derived hash; changing either
     * invalidates the previous phone verification.
     */
    public void updateOwnerProfile(String name, String canonicalPhone, String canonicalPhoneHash,
                                   boolean marketingConsent) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (canonicalPhone == null || !canonicalPhone.matches("03\\d{9}")) {
            throw new IllegalArgumentException("Canonical Pakistani mobile number required");
        }
        if (canonicalPhoneHash == null || canonicalPhoneHash.isBlank()) {
            throw new IllegalArgumentException("Customer phone hash is required");
        }
        boolean phoneIdentityChanged = !Objects.equals(this.phone, canonicalPhone)
                || !Objects.equals(this.phoneHash, canonicalPhoneHash);
        this.name = name.trim();
        this.phone = canonicalPhone;
        this.phoneHash = canonicalPhoneHash;
        this.marketingConsent = marketingConsent;
        if (phoneIdentityChanged) this.phoneVerified = false;
    }
    public void setRiskSignals(String appInstanceHash, String lastIpHash) { this.appInstanceHash = appInstanceHash; this.lastIpHash = lastIpHash; }
    public void requestDeletion() { this.status = AccountStatus.DELETION_REQUESTED; }
    public void deactivate() { this.status = AccountStatus.DEACTIVATED; this.deletedAt = Instant.now(); }
}
