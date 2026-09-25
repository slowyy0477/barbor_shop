package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.HaircutStyle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HaircutStyleRepository extends JpaRepository<HaircutStyle, UUID> {
    Optional<HaircutStyle> findBySalonIdAndId(UUID salonId, UUID id);
    List<HaircutStyle> findBySalonIdOrderByDisplayOrderAscNameAsc(UUID salonId);
    List<HaircutStyle> findBySalonIdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID salonId);
}
