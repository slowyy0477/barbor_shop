package com.ayan.salon.server.web;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.domain.repository.*;
import com.ayan.salon.server.service.ActorContext;
import com.ayan.salon.server.service.AuthService;
import com.ayan.salon.server.service.AuthenticatedActorResolver;
import com.ayan.salon.server.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Read APIs used by the authenticated customer app; money remains server-owned. */
@RestController
@RequestMapping("/api/salons/{salonId}/me")
public class CustomerController {
    private final CustomerRepository customers;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final BookingRepository bookings;
    private final VisitRepository visits;
    private final ReminderRepository reminders;
    private final AuthenticatedActorResolver actors;
    private final AuthService auth;

    public CustomerController(CustomerRepository customers, WalletRepository wallets,
                              WalletTransactionRepository transactions, BookingRepository bookings,
                              VisitRepository visits, ReminderRepository reminders, AuthService auth,
                              AuthenticatedActorResolver actors) {
        this.customers = customers; this.wallets = wallets; this.transactions = transactions;
        this.bookings = bookings; this.visits = visits; this.reminders = reminders; this.auth = auth;
        this.actors = actors;
    }

    /**
     * Sets or changes the customer's sign-in PIN. A first-time PIN only needs the
     * verified session; changing one requires the current PIN.
     */
    @PutMapping("/pin")
    public ResponseEntity<Void> setPin(@PathVariable UUID salonId,
                                       @org.springframework.web.bind.annotation.RequestBody
                                       @jakarta.validation.Valid ApiDtos.PinRequest request) {
        ActorContext actor = customerActor(salonId);
        auth.setActorPin(salonId, actor.actorId(), request.currentPin(), request.pin());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ProfileResponse profile(@PathVariable UUID salonId) {
        ActorContext actor = customerActor(salonId);
        Customer customer = customers.findBySalonIdAndId(salonId, actor.actorId())
                .orElseThrow(() -> new WalletService.NotFoundException("Customer profile not found"));
        return ProfileResponse.from(customer, visits.findBySalonIdAndCustomerIdOrderByCompletedAtDesc(salonId, actor.actorId()));
    }

    @PatchMapping
    public ProfileResponse updateProfile(@PathVariable UUID salonId,
                                         @org.springframework.web.bind.annotation.RequestBody
                                         @jakarta.validation.Valid ApiDtos.CustomerProfileRequest request) {
        ActorContext actor = customerActor(salonId);
        Customer customer = customers.findBySalonIdAndId(salonId, actor.actorId())
                .filter(value -> value.getStatus() == DomainTypes.AccountStatus.ACTIVE)
                .orElseThrow(() -> new WalletService.NotFoundException("Customer profile not found"));
        customer.updateProfile(request.name(), request.marketingConsent());
        customers.save(customer);
        return ProfileResponse.from(customer, visits.findBySalonIdAndCustomerIdOrderByCompletedAtDesc(salonId, actor.actorId()));
    }

    @GetMapping("/wallet")
    public Wallet wallet(@PathVariable UUID salonId) {
        ActorContext actor = customerActor(salonId);
        return wallets.findBySalonIdAndCustomerId(salonId, actor.actorId())
                .orElseThrow(() -> new WalletService.NotFoundException("Wallet not found"));
    }

    @GetMapping("/wallet/transactions")
    public List<WalletTransaction> walletTransactions(@PathVariable UUID salonId) {
        ActorContext actor = customerActor(salonId);
        return transactions.findBySalonIdAndCustomerIdOrderByOccurredAtDesc(salonId, actor.actorId());
    }

    @GetMapping("/bookings")
    public List<Booking> bookings(@PathVariable UUID salonId) {
        ActorContext actor = customerActor(salonId);
        return bookings.findBySalonIdAndCustomerIdOrderByStartsAtDesc(salonId, actor.actorId());
    }

    @GetMapping("/reminders")
    public List<Reminder> reminders(@PathVariable UUID salonId) {
        ActorContext actor = customerActor(salonId);
        return reminders.findBySalonIdAndCustomerIdOrderByDueAtDesc(salonId, actor.actorId());
    }

    private ActorContext customerActor(UUID salonId) {
        ActorContext actor = actors.require();
        actor.requireSalon(salonId);
        if (actor.role() != com.ayan.salon.server.domain.DomainTypes.ActorRole.CUSTOMER) {
            throw new ActorContext.AuthorizationException("Customer session required");
        }
        return actor;
    }

    public record ProfileResponse(UUID id, String name, String phone, boolean phoneVerified,
                                  boolean marketingConsent, java.time.Instant createdAt,
                                  List<Visit> visits) {
        static ProfileResponse from(Customer customer, List<Visit> visits) {
            return new ProfileResponse(customer.getId(), customer.getName(), customer.getPhone(),
                    customer.isPhoneVerified(), customer.isMarketingConsent(), customer.getCreatedAt(), visits);
        }
    }
}
