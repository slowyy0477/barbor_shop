package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.SignupGuard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SignupGuardRepository extends JpaRepository<SignupGuard, UUID> {
    Optional<SignupGuard> findFirstBySalonIdAndDeviceHash(UUID salonId, String deviceHash);

    long countBySalonIdAndIpHash(UUID salonId, String ipHash);

    long countBySalonId(UUID salonId);
}
