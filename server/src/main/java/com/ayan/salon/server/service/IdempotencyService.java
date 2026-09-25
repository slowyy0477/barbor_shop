package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.IdempotencyRecord;
import com.ayan.salon.server.domain.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {
    /** Keep this aligned with the database column and the HTTP header contract. */
    public static final int MAX_KEY_LENGTH = 160;

    private final IdempotencyRecordRepository repository;
    private final ConcurrentHashMap<String, Object> localClaims = new ConcurrentHashMap<>();
    /**
     * A process-local fallback is used only when an adapter cannot execute the
     * PostgreSQL native claim (for example, a lightweight unit-test repository).
     * PostgreSQL remains the source of truth in a deployed instance.
     */
    private final ConcurrentHashMap<String, IdempotencyRecord> localFallbackRecords = new ConcurrentHashMap<>();
    public IdempotencyService(IdempotencyRecordRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public String existing(String key, UUID salonId, String operation) {
        String normalizedKey = normalizeKey(key);
        IdempotencyRecord stored = lookup(normalizedKey);
        if (stored == null) return null;
        return java.util.Optional.of(stored).map(record -> {
            if (!Objects.equals(record.getSalonId(), salonId) || !Objects.equals(record.getOperation(), operation)) throw new IllegalArgumentException("Idempotency key was used for another operation");
            if ("__IN_PROGRESS__".equals(record.getResponseJson())) throw new ConflictException("A request with this idempotency key is still in progress");
            return record.getResponseJson();
        }).orElse(null);
    }

    @Transactional
    public void record(String key, UUID salonId, String operation, String response) {
        String normalizedKey = normalizeKey(key);
        IdempotencyRecord stored = lookup(normalizedKey);
        if (stored != null) {
            IdempotencyRecord existing = stored;
            if (!Objects.equals(existing.getSalonId(), salonId) || !Objects.equals(existing.getOperation(), operation)) {
                throw new IllegalArgumentException("Idempotency key was used for another operation");
            }
            if ("__IN_PROGRESS__".equals(existing.getResponseJson())) {
                existing.complete(response, existing.getRequestFingerprint());
                if (isLocalFallback(normalizedKey, existing)) {
                    localFallbackRecords.put(normalizedKey, existing);
                } else {
                    repository.save(existing);
                }
                return;
            }
            if (!Objects.equals(existing.getResponseJson(), response)) {
                throw new ConflictException("Idempotency key was already used for a different response");
            }
            return;
        }
        IdempotencyRecord created = new IdempotencyRecord(normalizedKey, salonId, operation, response);
        IdempotencyRecord saved = repository.save(created);
        if (saved == null) localFallbackRecords.put(normalizedKey, created);
    }

    /**
     * Atomically claims a mutation key. Returns the prior response when a request
     * already completed, or null when the caller owns a new claim. A request with
     * the same key but a different fingerprint is rejected.
     */
    @Transactional
    public String begin(String key, UUID salonId, String operation, String requestFingerprint) {
        String normalizedKey = normalizeKey(key);
        String fingerprint = normalizeFingerprint(requestFingerprint);
        Object lock = localClaims.computeIfAbsent(normalizedKey, ignored -> new Object());
        try {
            synchronized (lock) {
                IdempotencyRecord prior = lookup(normalizedKey);
                if (prior != null) return validatePrior(prior, salonId, operation, fingerprint);
                int inserted;
                try {
                    inserted = repository.claim(normalizedKey, salonId, operation, fingerprint);
                } catch (RuntimeException unsupported) {
                    // Unit-test repositories and non-PostgreSQL adapters may not expose
                    // the native claim; the local lock still prevents same-process races.
                    inserted = 0;
                }
                if (inserted == 1) {
                    // A real repository exposes the inserted row immediately. Keep a
                    // fallback claim only for minimal adapters that report success
                    // without materializing the entity for a subsequent lookup.
                    if (lookup(normalizedKey) == null) {
                        localFallbackRecords.put(normalizedKey,
                                new IdempotencyRecord(normalizedKey, salonId, operation, "__IN_PROGRESS__", fingerprint));
                    }
                    return null;
                }
                prior = lookup(normalizedKey);
                if (prior == null) {
                    IdempotencyRecord claim = new IdempotencyRecord(normalizedKey, salonId, operation, "__IN_PROGRESS__", fingerprint);
                    IdempotencyRecord saved = repository.save(claim);
                    if (saved == null) localFallbackRecords.put(normalizedKey, claim);
                    return null;
                }
                return validatePrior(prior, salonId, operation, fingerprint);
            }
        } finally {
            localClaims.remove(normalizedKey, lock);
        }
    }

    @Transactional
    public void complete(String key, UUID salonId, String operation, String response, String requestFingerprint) {
        String normalizedKey = normalizeKey(key);
        IdempotencyRecord record = lookup(normalizedKey);
        if (record == null) throw new IllegalStateException("Idempotency claim was not found");
        if (!Objects.equals(record.getSalonId(), salonId) || !Objects.equals(record.getOperation(), operation)) {
            throw new IllegalArgumentException("Idempotency key was used for another operation");
        }
        String fingerprint = normalizeFingerprint(requestFingerprint);
        if (record.getRequestFingerprint() != null && !record.getRequestFingerprint().isBlank() && !record.getRequestFingerprint().equals(fingerprint)) {
            throw new ConflictException("Idempotency key was already used for a different request");
        }
        if (!"__IN_PROGRESS__".equals(record.getResponseJson()) && !Objects.equals(record.getResponseJson(), response)) {
            throw new ConflictException("Idempotency key was already used for a different response");
        }
        record.complete(response, fingerprint);
        if (isLocalFallback(normalizedKey, record)) {
            localFallbackRecords.put(normalizedKey, record);
        } else {
            IdempotencyRecord saved = repository.save(record);
            if (saved == null) localFallbackRecords.put(normalizedKey, record);
            else localFallbackRecords.remove(normalizedKey);
        }
    }

    public void release(String key) {
        String normalizedKey = normalizeKey(key);
        repository.deleteById(normalizedKey);
        localFallbackRecords.remove(normalizedKey);
    }

    /**
     * Builds an unambiguous request fingerprint from typed mutation fields. A
     * length prefix prevents concatenation collisions such as ["ab", "c"] and
     * ["a", "bc"]. Callers should pass already-canonical enum/UUID/time values.
     */
    public static String fingerprintFields(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        if (fields != null) {
            for (Object field : fields) {
                String value = field == null ? "<null>" : String.valueOf(field);
                canonical.append(value.length()).append(':').append(value).append(';');
            }
        }
        return fingerprint(canonical.toString());
    }

    private IdempotencyRecord lookup(String normalizedKey) {
        IdempotencyRecord stored = repository.findById(normalizedKey).orElse(null);
        if (stored != null) {
            localFallbackRecords.remove(normalizedKey);
            return stored;
        }
        return localFallbackRecords.get(normalizedKey);
    }

    private boolean isLocalFallback(String normalizedKey, IdempotencyRecord record) {
        return localFallbackRecords.get(normalizedKey) == record;
    }

    private static String validatePrior(IdempotencyRecord prior, UUID salonId, String operation, String fingerprint) {
        if (!Objects.equals(prior.getSalonId(), salonId) || !Objects.equals(prior.getOperation(), operation)) {
            throw new IllegalArgumentException("Idempotency key was used for another operation");
        }
        if (prior.getRequestFingerprint() != null && !prior.getRequestFingerprint().isBlank()
                && !prior.getRequestFingerprint().equals(fingerprint)) {
            throw new ConflictException("Idempotency key was already used for a different request");
        }
        if ("__IN_PROGRESS__".equals(prior.getResponseJson())) {
            throw new ConflictException("A request with this idempotency key is still in progress");
        }
        return prior.getResponseJson();
    }

    public static String fingerprint(String canonicalRequest) {
        if (canonicalRequest == null) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception error) { throw new IllegalStateException("Unable to fingerprint request", error); }
    }

    private static String normalizeFingerprint(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        if (normalized.length() > 128) throw new IllegalArgumentException("Request fingerprint is too long");
        return normalized;
    }

    public static UUID responseId(String response) { return UUID.fromString(response); }

    /**
     * Header values are opaque, but surrounding whitespace is not meaningful. Keep
     * one canonical representation before reading or writing the primary key.
     */
    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        String normalized = key.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Idempotency-Key is required");
        if (normalized.length() > MAX_KEY_LENGTH) throw new IllegalArgumentException("Idempotency-Key is too long");
        return normalized;
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
