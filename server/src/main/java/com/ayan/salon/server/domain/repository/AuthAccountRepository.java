package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {
    Optional<AuthAccount> findBySalonIdAndPhoneHashAndStatus(
            UUID salonId, String phoneHash, com.ayan.salon.server.domain.DomainTypes.AccountStatus status);

    /** Owner accounts of one salon, used by password-only owner sign in. */
    List<AuthAccount> findBySalonIdAndRoleAndStatus(
            UUID salonId,
            com.ayan.salon.server.domain.DomainTypes.ActorRole role,
            com.ayan.salon.server.domain.DomainTypes.AccountStatus status);
}
