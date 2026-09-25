package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
    @jakarta.persistence.Id
    private UUID id;
    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;
    @Column(name = "phone_hash", nullable = false, length = 128)
    private String phoneHash;
    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(nullable = false)
    private boolean consumed;
    @Column(name = "request_ip_hash", length = 128)
    private String requestIpHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected OtpChallenge() {}

    public OtpChallenge(UUID salonId, String phoneHash, String codeHash, Instant expiresAt,
                        int maxAttempts, String requestIpHash) {
        this.id = UUID.randomUUID();
        this.salonId = salonId;
        this.phoneHash = phoneHash;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.maxAttempts = maxAttempts;
        this.requestIpHash = requestIpHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSalonId() { return salonId; }
    public String getPhoneHash() { return phoneHash; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public boolean isConsumed() { return consumed; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean usable(Instant now) { return !consumed && attemptCount < maxAttempts && expiresAt.isAfter(now); }
    public void countFailedAttempt() { attemptCount++; }
    public void consume() { consumed = true; }
}
