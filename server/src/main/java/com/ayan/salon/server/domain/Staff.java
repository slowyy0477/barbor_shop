package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import static com.ayan.salon.server.domain.DomainTypes.ActorRole;

@Entity
@Table(name = "staff")
public class Staff extends TenantEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 32)
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ActorRole role;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "permissions_json", nullable = false, columnDefinition = "text")
    private String permissionsJson = "[]";
    @Version
    private long version;

    protected Staff() {}
    public Staff(UUID salonId, String name, String phone, ActorRole role) { super(salonId); this.name = name; this.phone = phone; this.role = role; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public ActorRole getRole() { return role; }
    public boolean isActive() { return active; }
    public String getPermissionsJson() { return permissionsJson; }
    public void update(String name, String phone, ActorRole role, boolean active, String permissionsJson) { this.name = name; this.phone = phone; this.role = role; this.active = active; this.permissionsJson = permissionsJson == null ? "[]" : permissionsJson; }
}
