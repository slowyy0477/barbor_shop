package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {
    Optional<AuthAccount> findBySalonIdAndPhoneHashAndStatus(
            UUID salonId, String phoneHash, com.ayan.salon.server.domain.DomainTypes.AccountStatus status);
}
