package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id @Column(nullable = false, length = 160) private String idempotencyKey;
    @Column(name = "salon_id", nullable = false) private UUID salonId;
    @Column(name = "operation", nullable = false, length = 80) private String operation;
    @Column(name = "response_json", nullable = false, columnDefinition = "text") private String responseJson;
    @Column(name = "request_fingerprint", nullable = false, length = 128) private String requestFingerprint;
    @Column(nullable = false) private Instant createdAt;
    protected IdempotencyRecord() {}
    public IdempotencyRecord(String key, UUID salonId, String operation, String responseJson) { this(key, salonId, operation, responseJson, ""); }
    public IdempotencyRecord(String key, UUID salonId, String operation, String responseJson, String requestFingerprint) { this.idempotencyKey = key; this.salonId = salonId; this.operation = operation; this.responseJson = responseJson; this.requestFingerprint = requestFingerprint == null ? "" : requestFingerprint; this.createdAt = Instant.now(); }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getSalonId() { return salonId; }
    public String getOperation() { return operation; }
    public String getResponseJson() { return responseJson; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void complete(String responseJson, String requestFingerprint) { this.responseJson = responseJson; if (requestFingerprint != null) this.requestFingerprint = requestFingerprint; }
}
