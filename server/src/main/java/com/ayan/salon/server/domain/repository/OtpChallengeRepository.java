package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.OtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c where c.id = :id and c.salonId = :salonId")
    Optional<OtpChallenge> lockBySalonIdAndId(@Param("salonId") UUID salonId, @Param("id") UUID id);

    long countBySalonIdAndPhoneHashAndCreatedAtGreaterThanEqual(UUID salonId, String phoneHash, Instant since);
}
