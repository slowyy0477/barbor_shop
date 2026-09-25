package com.ayan.salon.server.domain.repository;

import com.ayan.salon.server.domain.Booking;
import com.ayan.salon.server.domain.DomainTypes.BookingStatus;
import com.ayan.salon.server.domain.DomainTypes.PaymentMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.salonId = :salonId and b.id = :id")
    Optional<Booking> lockBySalonIdAndId(@Param("salonId") UUID salonId, @Param("id") UUID id);
    @Query("select count(b) > 0 from Booking b where b.salonId=:salonId and b.staffId=:staffId and b.status in :statuses and b.startsAt < :endsAt and b.endsAt > :startsAt")
    boolean hasOverlap(@Param("salonId") UUID salonId, @Param("staffId") UUID staffId, @Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt, @Param("statuses") List<com.ayan.salon.server.domain.DomainTypes.BookingStatus> statuses);
    @Query("select coalesce(sum(b.totalMinor), 0) from Booking b where b.salonId = :salonId and b.customerId = :customerId and b.paymentMethod = :paymentMethod and b.status in :statuses")
    long sumActiveWalletTotals(@Param("salonId") UUID salonId,
                               @Param("customerId") UUID customerId,
                               @Param("paymentMethod") PaymentMethod paymentMethod,
                               @Param("statuses") List<BookingStatus> statuses);
    long countBySalonIdAndCustomerIdAndStatus(UUID salonId, UUID customerId, BookingStatus status);
    long countBySalonIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(UUID salonId, BookingStatus status, Instant from, Instant to);
    List<Booking> findBySalonIdAndCustomerIdOrderByStartsAtDesc(UUID salonId, UUID customerId);
    List<Booking> findBySalonIdOrderByStartsAtDesc(UUID salonId);
}
