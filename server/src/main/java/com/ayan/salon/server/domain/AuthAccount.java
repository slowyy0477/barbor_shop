package com.ayan.salon.server.domain;

import com.ayan.salon.server.domain.DomainTypes.AccountStatus;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Server-managed login identity for owners and staff. Customer identities stay
 * attached to the customers table so a verified mobile number has one owner.
 * No password or OTP is stored here.
 */
@Entity
@Table(name = "auth_accounts")
public class AuthAccount extends TenantEntity {
    @Column(name = "phone_hash", nullable = false, length = 128)
    private String phoneHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ActorRole role;
    @Column(name = "permissions_json", nullable = false, columnDefinition = "text")
    private String permissionsJson = "[]";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountStatus status = AccountStatus.ACTIVE;
    @Version
    private long version;

    protected AuthAccount() {}

    public AuthAccount(UUID salonId, String phoneHash, ActorRole role, String permissionsJson) {
        super(salonId);
        if (phoneHash == null || phoneHash.isBlank()) throw new IllegalArgumentException("Phone hash is required");
        if (role == null || role == ActorRole.CUSTOMER) throw new IllegalArgumentException("Owner or staff role is required");
        this.phoneHash = phoneHash;
        this.role = role;
        this.permissionsJson = permissionsJson == null ? "[]" : permissionsJson;
    }

    public String getPhoneHash() { return phoneHash; }
    public ActorRole getRole() { return role; }
    public String getPermissionsJson() { return permissionsJson; }
    public AccountStatus getStatus() { return status; }
    public boolean isActive() { return status == AccountStatus.ACTIVE; }
    public void update(String permissionsJson, AccountStatus status) {
        this.permissionsJson = permissionsJson == null ? "[]" : permissionsJson;
        if (status != null) this.status = status;
    }
}
