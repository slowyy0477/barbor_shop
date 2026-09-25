package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    List<Reminder> findBySalonIdAndCustomerIdOrderByDueAtDesc(UUID salonId, UUID customerId);
    java.util.Optional<Reminder> findBySalonIdAndCustomerIdAndServiceIdAndSourceVisitId(UUID salonId, UUID customerId, UUID serviceId, UUID sourceVisitId);
    List<Reminder> findBySalonIdAndCustomerIdAndServiceIdAndStatusIn(UUID salonId, UUID customerId, UUID serviceId, List<com.ayan.salon.server.domain.DomainTypes.ReminderStatus> statuses);
    List<Reminder> findByStatusAndDueAtBeforeAndOptedOutFalse(com.ayan.salon.server.domain.DomainTypes.ReminderStatus status, Instant before);
    void deleteBySalonIdAndCustomerIdAndServiceIdAndStatus(UUID salonId, UUID customerId, UUID serviceId, com.ayan.salon.server.domain.DomainTypes.ReminderStatus status);
    List<Reminder> findBySalonIdOrderByDueAtDesc(UUID salonId);
}
