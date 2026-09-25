package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface StaffRepository extends JpaRepository<Staff, UUID> {
    Optional<Staff> findBySalonIdAndId(UUID salonId, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Staff s where s.salonId = :salonId and s.id = :id")
    Optional<Staff> lockBySalonIdAndId(@Param("salonId") UUID salonId, @Param("id") UUID id);
    List<Staff> findBySalonId(UUID salonId);
    List<Staff> findBySalonIdAndActiveTrue(UUID salonId);
}
