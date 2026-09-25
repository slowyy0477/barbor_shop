package com.ayan.salon.server.domain;

import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @jakarta.persistence.Id
    private UUID id;
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;
    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    private ActorRole role;
    @Column(name = "permissions_json", nullable = false, columnDefinition = "text")
    private String permissionsJson = "[]";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    protected AuthSession() {}

    public AuthSession(String tokenHash, UUID salonId, UUID actorId, ActorRole role,
                       String permissionsJson, Instant expiresAt, String userAgent) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.salonId = salonId;
        this.actorId = actorId;
        this.role = role;
        this.permissionsJson = permissionsJson == null ? "[]" : permissionsJson;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.lastSeenAt = this.createdAt;
        this.userAgent = userAgent;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public UUID getSalonId() { return salonId; }
    public UUID getActorId() { return actorId; }
    public ActorRole getRole() { return role; }
    public String getPermissionsJson() { return permissionsJson; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean activeAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void touch(Instant now) { lastSeenAt = now; }
    public void revoke(Instant now) { revokedAt = now; }
}
