package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
public interface VisitRepository extends JpaRepository<Visit, UUID> {
    Optional<Visit> findBySalonIdAndBookingId(UUID salonId, UUID bookingId);
    long countBySalonIdAndCustomerId(UUID salonId, UUID customerId);
    List<Visit> findBySalonIdAndCustomerIdOrderByCompletedAtDesc(UUID salonId, UUID customerId);
    List<Visit> findBySalonIdOrderByCompletedAtDesc(UUID salonId);
}
