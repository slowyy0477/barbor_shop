package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.Withdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.ayan.salon.server.domain.DomainTypes.WithdrawalStatus;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Withdrawal w where w.salonId = :salonId and w.id = :id")
    Optional<Withdrawal> lockBySalonIdAndId(@Param("salonId") UUID salonId, @Param("id") UUID id);
    @Query("select coalesce(sum(w.amountMinor), 0) from Withdrawal w where w.salonId = :salonId and w.customerId = :customerId and w.createdAt >= :since and w.status <> :rejected")
    long sumActiveSince(@Param("salonId") UUID salonId, @Param("customerId") UUID customerId, @Param("since") Instant since, @Param("rejected") WithdrawalStatus rejected);
    java.util.List<Withdrawal> findBySalonIdOrderByCreatedAtDesc(UUID salonId);
}
