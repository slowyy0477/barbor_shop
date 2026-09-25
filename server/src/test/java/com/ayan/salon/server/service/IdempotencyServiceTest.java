package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.IdempotencyRecord;
import com.ayan.salon.server.domain.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final UUID otherSalon = UUID.randomUUID();
    @Mock IdempotencyRecordRepository repository;
    private final Map<String, IdempotencyRecord> records = new HashMap<>();
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
        lenient().when(repository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(records.get(invocation.getArgument(0))));
        lenient().when(repository.save(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            records.put(record.getIdempotencyKey(), record);
            return record;
        });
    }

    @Test
    void trimsKeysBeforeReadingAndWriting() {
        service.record("  booking-1  ", salon, "booking.create", "response-1");

        assertEquals("response-1", service.existing("booking-1", salon, "booking.create"));
        assertEquals("response-1", service.existing("\u2003booking-1\u2003", salon, "booking.create"));
        assertEquals(1, records.size());
        assertEquals("booking-1", records.keySet().iterator().next());
    }

    @Test
    void rejectsBlankAndOversizedKeysForBothOperations() {
        String oversized = "x".repeat(IdempotencyService.MAX_KEY_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> service.existing("   ", salon, "booking.create"));
        assertThrows(IllegalArgumentException.class, () -> service.record("\t\n", salon, "booking.create", "response"));
        assertThrows(IllegalArgumentException.class, () -> service.existing(oversized, salon, "booking.create"));
        assertThrows(IllegalArgumentException.class, () -> service.record(oversized, salon, "booking.create", "response"));
    }

    @Test
    void rejectsReuseWhenTheStoredResponseDiffers() {
        service.record("booking-2", salon, "booking.create", "response-1");

        assertThrows(IdempotencyService.ConflictException.class,
                () -> service.record("booking-2", salon, "booking.create", "response-2"));
        assertEquals("response-1", service.existing("booking-2", salon, "booking.create"));
    }

    @Test
    void rejectsReuseAcrossSalonOrOperation() {
        service.record("shared-key", salon, "booking.create", "response-1");

        assertThrows(IllegalArgumentException.class,
                () -> service.existing("shared-key", otherSalon, "booking.create"));
        assertThrows(IllegalArgumentException.class,
                () -> service.record("shared-key", salon, "booking.status", "response-1"));
    }

    @Test
    void beginAndCompleteReplayTheOriginalResponseForTheSameFingerprint() {
        String fingerprint = IdempotencyService.fingerprintFields("amount", 5000, "provider", "EASYPAISA");

        assertNull(service.begin("claim-1", salon, "deposit.submit", fingerprint));
        service.complete("claim-1", salon, "deposit.submit", "deposit-id-1", fingerprint);

        assertEquals("deposit-id-1", service.begin("claim-1", salon, "deposit.submit", fingerprint));
    }

    @Test
    void beginRejectsDifferentFingerprintAfterAClaim() {
        String first = IdempotencyService.fingerprintFields("amount", 5000);
        String second = IdempotencyService.fingerprintFields("amount", 7000);

        service.begin("claim-2", salon, "deposit.submit", first);

        assertThrows(IdempotencyService.ConflictException.class,
                () -> service.begin("claim-2", salon, "deposit.submit", second));
        assertThrows(IdempotencyService.ConflictException.class,
                () -> service.complete("claim-2", salon, "deposit.submit", "deposit-id-2", second));
    }

    @Test
    void onlyOneConcurrentCallerOwnsTheFirstClaim() throws Exception {
        String fingerprint = IdempotencyService.fingerprintFields("booking", "same-request");
        int callers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            ArrayList<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return service.begin("claim-concurrent", salon, "booking.create", fingerprint) == null
                                ? "OWNER" : "REPLAY";
                    } catch (IdempotencyService.ConflictException expected) {
                        return "CONFLICT";
                    }
                }));
            }
            ready.await();
            start.countDown();
            long owners = 0;
            long conflicts = 0;
            for (Future<String> result : results) {
                if ("OWNER".equals(result.get())) owners++;
                if ("CONFLICT".equals(result.get())) conflicts++;
            }
            assertEquals(1, owners);
            assertEquals(callers - 1, conflicts);
        } finally {
            executor.shutdownNow();
        }
    }
}
