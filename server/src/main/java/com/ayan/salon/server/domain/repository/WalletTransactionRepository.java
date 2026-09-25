package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    List<WalletTransaction> findBySalonIdAndCustomerIdOrderByOccurredAtDesc(UUID salonId, UUID customerId);
    List<WalletTransaction> findBySalonIdOrderByOccurredAtDesc(UUID salonId);
    boolean existsBySalonIdAndReferenceIdAndType(UUID salonId, String referenceId, com.ayan.salon.server.domain.DomainTypes.LedgerType type);
    Optional<WalletTransaction> findBySalonIdAndReferenceIdAndType(UUID salonId, String referenceId, com.ayan.salon.server.domain.DomainTypes.LedgerType type);
}
