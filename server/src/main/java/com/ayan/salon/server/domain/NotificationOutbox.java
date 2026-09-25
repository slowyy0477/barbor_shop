package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Durable notification intent. Delivery is retried without changing business state. */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {
    @jakarta.persistence.Id
    private UUID id;
    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(nullable = false, length = 32)
    private String channel;
    @Column(nullable = false, length = 80)
    private String template;
    @Column(nullable = false, length = 4000)
    private String payload;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Version
    private long version;

    protected NotificationOutbox() {}

    public NotificationOutbox(UUID salonId, UUID customerId, String channel, String template,
                              String payload, Instant nextAttemptAt) {
        this.id = UUID.randomUUID();
        this.salonId = salonId;
        this.customerId = customerId;
        this.channel = channel;
        this.template = template;
        this.payload = payload == null ? "" : payload;
        this.status = "PENDING";
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSalonId() { return salonId; }
    public UUID getCustomerId() { return customerId; }
    public String getChannel() { return channel; }
    public String getTemplate() { return template; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastError() { return lastError; }
    public boolean pendingAt(Instant now) { return "PENDING".equals(status) && !nextAttemptAt.isAfter(now); }
    public void markSent(Instant now) { status = "SENT"; sentAt = now; lastError = null; }
    public void markFailure(String message, Instant nextAttempt) {
        attempts++;
        status = attempts >= 10 ? "FAILED" : "PENDING";
        lastError = message == null ? "Delivery failed" : message.substring(0, Math.min(1000, message.length()));
        nextAttemptAt = nextAttempt;
    }
}
