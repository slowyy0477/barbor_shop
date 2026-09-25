package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop200BySalonIdOrderByOccurredAtDesc(UUID salonId);
}
