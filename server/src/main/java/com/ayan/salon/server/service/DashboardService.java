package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.DomainTypes.BookingStatus;
import com.ayan.salon.server.domain.repository.BookingRepository;
import com.ayan.salon.server.domain.repository.BusinessLedgerRepository;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.UUID;

@Service
public class DashboardService {
    private final BookingRepository bookings;
    private final BusinessLedgerRepository businessLedger;
    private final CustomerRepository customers;
    public DashboardService(BookingRepository bookings, BusinessLedgerRepository businessLedger, CustomerRepository customers) { this.bookings = bookings; this.businessLedger = businessLedger; this.customers = customers; }

    public Summary today(ActorContext actor) {
        actor.require("view_financial_reports");
        ZoneId zone = ZoneId.of("Asia/Karachi");
        LocalDate today = LocalDate.now(zone);
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        long completed = bookings.countBySalonIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(actor.salonId(), BookingStatus.COMPLETED, from, to);
        long cancelled = bookings.countBySalonIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(actor.salonId(), BookingStatus.CANCELLED, from, to);
        long noShow = bookings.countBySalonIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(actor.salonId(), BookingStatus.NO_SHOW, from, to);
        long revenue = businessLedger.sumServiceRevenue(actor.salonId(), from, to);
        long newCustomers = customers.countBySalonIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(actor.salonId(), from, to);
        return new Summary(today, revenue, completed, cancelled, noShow, newCustomers, completed == 0 ? 0 : revenue / completed);
    }
    public record Summary(LocalDate date, long completedRevenueMinor, long completedBookings, long cancellations, long noShows, long newCustomers, long averageBillMinor) {}
}
