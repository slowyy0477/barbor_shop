package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findBySalonIdAndId(UUID salonId, UUID id);
}
