package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    /** PostgreSQL atomic first-writer claim used by distributed HTTP workers. */
    @Modifying
    @Query(value = "insert into idempotency_records (idempotency_key, salon_id, operation, response_json, request_fingerprint, created_at) values (:key, :salonId, :operation, '__IN_PROGRESS__', :fingerprint, now()) on conflict (idempotency_key) do nothing", nativeQuery = true)
    int claim(@Param("key") String key, @Param("salonId") java.util.UUID salonId,
              @Param("operation") String operation, @Param("fingerprint") String fingerprint);
}
