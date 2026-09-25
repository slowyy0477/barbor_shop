package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sign-in PIN for one salon account (customer, owner or staff). Only a salted
 * PBKDF2 digest is persisted. Lockout state lives here so a stolen mobile
 * number cannot be brute-forced at line speed.
 */
@Entity
@Table(name = "sign_in_pins")
public class SignInPin extends TenantEntity {
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;
    @Column(name = "pin_hash", nullable = false, length = 256)
    private String pinHash;
    @Column(name = "pin_salt", nullable = false, length = 64)
    private String pinSalt;
    @Column(nullable = false)
    private int iterations;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected SignInPin() {}

    public SignInPin(UUID salonId, UUID actorId, String pinHash, String pinSalt, int iterations) {
        super(salonId);
        if (actorId == null) throw new IllegalArgumentException("Account id is required");
        if (pinHash == null || pinHash.isBlank()) throw new IllegalArgumentException("PIN digest is required");
        if (pinSalt == null || pinSalt.isBlank()) throw new IllegalArgumentException("PIN salt is required");
        if (iterations < 10_000) throw new IllegalArgumentException("PIN hashing iterations are too low");
        this.actorId = actorId;
        this.pinHash = pinHash;
        this.pinSalt = pinSalt;
        this.iterations = iterations;
        this.updatedAt = Instant.now();
    }

    public UUID getActorId() { return actorId; }
    public String getPinHash() { return pinHash; }
    public String getPinSalt() { return pinSalt; }
    public int getIterations() { return iterations; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    /** Records a wrong PIN and locks the account once the allowance is spent. */
    public void registerFailure(int maxAttempts, Duration lockDuration) {
        this.attemptCount++;
        if (maxAttempts > 0 && this.attemptCount >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockDuration);
            this.attemptCount = 0;
        }
        this.updatedAt = Instant.now();
    }

    public void registerSuccess() {
        this.attemptCount = 0;
        this.lockedUntil = null;
        this.updatedAt = Instant.now();
    }

    public void replace(String pinHash, String pinSalt, int iterations) {
        if (pinHash == null || pinHash.isBlank()) throw new IllegalArgumentException("PIN digest is required");
        if (pinSalt == null || pinSalt.isBlank()) throw new IllegalArgumentException("PIN salt is required");
        if (iterations < 10_000) throw new IllegalArgumentException("PIN hashing iterations are too low");
        this.pinHash = pinHash;
        this.pinSalt = pinSalt;
        this.iterations = iterations;
        this.updatedAt = Instant.now();
    }
}
