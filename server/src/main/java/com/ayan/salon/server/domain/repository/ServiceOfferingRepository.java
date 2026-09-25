package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {
    Optional<ServiceOffering> findBySalonIdAndId(UUID salonId, UUID id);
    List<ServiceOffering> findBySalonIdOrderByName(UUID salonId);
    List<ServiceOffering> findBySalonIdAndActiveTrueOrderByName(UUID salonId);
}
