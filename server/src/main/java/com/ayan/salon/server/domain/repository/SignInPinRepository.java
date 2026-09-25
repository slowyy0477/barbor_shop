package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.SignInPin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SignInPinRepository extends JpaRepository<SignInPin, UUID> {
    Optional<SignInPin> findBySalonIdAndActorId(UUID salonId, UUID actorId);

    /** Serializes concurrent PIN attempts for one account so lockout cannot be raced. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SignInPin p where p.salonId = :salonId and p.actorId = :actorId")
    Optional<SignInPin> lockBySalonIdAndActorId(@Param("salonId") UUID salonId, @Param("actorId") UUID actorId);
}
