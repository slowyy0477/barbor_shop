package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.Referral;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.salonId=:salonId and r.id=:id")
    Optional<Referral> lockBySalonIdAndId(@Param("salonId") UUID salonId, @Param("id") UUID id);
    Optional<Referral> findBySalonIdAndReferredCustomerId(UUID salonId, UUID referredCustomerId);
    Optional<Referral> findBySalonIdAndCodeIgnoreCase(UUID salonId, String code);
    java.util.List<Referral> findBySalonIdOrderByCreatedAtDesc(UUID salonId);
}
