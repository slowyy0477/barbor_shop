package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * One row per self-service customer signup. Only a salted digest of the device
 * key and of the requesting network address is stored, never the raw device id
 * or IP. The rows exist so the salon can enforce "one account per phone" and a
 * small per-network signup cap without keeping personal browsing data.
 */
@Entity
@Table(name = "signup_guards")
public class SignupGuard extends TenantEntity {
    @Column(name = "device_hash", length = 128)
    private String deviceHash;
    @Column(name = "ip_hash", nullable = false, length = 128)
    private String ipHash;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Version
    private long version;

    protected SignupGuard() {}

    public SignupGuard(UUID salonId, String deviceHash, String ipHash, UUID customerId) {
        super(salonId);
        if (ipHash == null || ipHash.isBlank()) throw new IllegalArgumentException("Network identity is required");
        if (customerId == null) throw new IllegalArgumentException("Customer is required");
        this.deviceHash = (deviceHash == null || deviceHash.isBlank()) ? null : deviceHash;
        this.ipHash = ipHash;
        this.customerId = customerId;
    }

    public String getDeviceHash() { return deviceHash; }
    public String getIpHash() { return ipHash; }
    public UUID getCustomerId() { return customerId; }
}
