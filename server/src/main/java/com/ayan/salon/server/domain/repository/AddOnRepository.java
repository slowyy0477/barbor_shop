package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.AddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnRepository extends JpaRepository<AddOn, UUID> {
    Optional<AddOn> findBySalonIdAndId(UUID salonId, UUID id);
    List<AddOn> findBySalonIdOrderByName(UUID salonId);
    List<AddOn> findBySalonIdAndActiveTrueOrderByName(UUID salonId);
    @Query("select a from AddOn a where a.salonId = :salonId and a.active = true and (a.serviceId is null or a.serviceId = :serviceId) order by a.name")
    List<AddOn> findActiveApplicable(@Param("salonId") UUID salonId, @Param("serviceId") UUID serviceId);
}
