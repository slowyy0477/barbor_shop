package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findBySalonIdAndId(UUID salonId, UUID id);
    Optional<Customer> findBySalonIdAndPhone(UUID salonId, String phone);
    Optional<Customer> findBySalonIdAndPhoneHash(UUID salonId, String phoneHash);
    List<Customer> findBySalonId(UUID salonId);
    List<Customer> findBySalonIdAndPhoneHashOrSalonIdAndAppInstanceHash(UUID salonId1, String phoneHash, UUID salonId2, String appInstanceHash);
    long countBySalonIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID salonId, Instant from, Instant to);
}
