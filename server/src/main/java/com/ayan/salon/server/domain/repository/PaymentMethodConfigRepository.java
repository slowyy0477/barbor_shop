package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.PaymentMethodConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfig, UUID> {
    List<PaymentMethodConfig> findBySalonIdAndEnabledTrueOrderBySortOrder(UUID salonId);
    List<PaymentMethodConfig> findBySalonIdOrderBySortOrder(UUID salonId);
    Optional<PaymentMethodConfig> findBySalonIdAndProvider(UUID salonId, String provider);
}
