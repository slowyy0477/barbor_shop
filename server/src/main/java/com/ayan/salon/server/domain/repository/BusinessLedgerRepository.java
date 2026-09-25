package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.BusinessLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface BusinessLedgerRepository extends JpaRepository<BusinessLedgerEntry, UUID> {
    @Query("select coalesce(sum(b.amountMinor), 0) from BusinessLedgerEntry b where b.salonId=:salonId and b.type='SERVICE_REVENUE' and b.occurredAt >= :from and b.occurredAt < :to")
    long sumServiceRevenue(@Param("salonId") UUID salonId, @Param("from") Instant from, @Param("to") Instant to);
}
