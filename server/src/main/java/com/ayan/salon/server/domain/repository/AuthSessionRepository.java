package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.tokenHash = :tokenHash")
    Optional<AuthSession> lockByTokenHash(@Param("tokenHash") String tokenHash);
    Optional<AuthSession> findByTokenHash(String tokenHash);
}
