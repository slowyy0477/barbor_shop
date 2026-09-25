package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.AuditLog;
import com.ayan.salon.server.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    public AuditService(AuditLogRepository repository) { this.repository = repository; }
    public void record(UUID salonId, UUID actorId, String action, String targetType, UUID targetId, String details) {
        repository.save(new AuditLog(salonId, actorId, action, targetType, targetId, details));
    }
}
