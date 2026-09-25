package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.NotificationOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationOutbox n where n.status = 'PENDING' and n.nextAttemptAt <= :now order by n.createdAt")
    List<NotificationOutbox> lockDue(@Param("now") Instant now, Pageable page);
    long countByStatus(String status);
}
