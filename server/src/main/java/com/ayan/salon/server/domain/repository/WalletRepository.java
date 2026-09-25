package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.salonId = :salonId and w.customerId = :customerId")
    Optional<Wallet> lockBySalonIdAndCustomerId(@Param("salonId") UUID salonId, @Param("customerId") UUID customerId);
    Optional<Wallet> findBySalonIdAndCustomerId(UUID salonId, UUID customerId);
    java.util.List<Wallet> findBySalonId(UUID salonId);
}
