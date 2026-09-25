package com.ayan.salon.server.web;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/salons/{salonId}")
public class SalonController {
    private final SalonService salonService;
    private final BookingService bookingService;
    private final AuthService authService;
    private final AuthenticatedActorResolver actors;
    public SalonController(SalonService salonService, BookingService bookingService, AuthService authService,
                           AuthenticatedActorResolver actors) {
        this.salonService = salonService;
        this.bookingService = bookingService;
        this.authService = authService;
        this.actors = actors;
    }

    @PutMapping("/settings")
    public SalonSettings updateSettings(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.SettingsRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateSettings(actor, salonId, request.name(), request.address(), request.phone(), request.reminderDays(), request.depositBonusMinor(), request.referrerRewardMinor(), request.newCustomerDiscountMinor(), request.minimumWithdrawalMinor(), request.maximumDailyWithdrawalMinor(), request.consumePromoFirst(), request.primaryColor(), request.logoUri());
    }
    @GetMapping("/settings")
    public SalonSettings getSettings(@PathVariable UUID salonId) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.getSettings(actor, salonId);
    }
    @PutMapping("/branding")
    public SalonSettings updateBranding(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.BrandingRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateBranding(actor, salonId, request.primaryColor(), request.logoUri());
    }
    @PostMapping("/customers")
    public Customer createCustomer(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.CustomerRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return salonService.createCustomer(actor, salonId, request.name(), request.phone(), request.marketingConsent()); }
    @PutMapping("/customers/{customerId}")
    public Customer updateCustomer(@PathVariable UUID salonId, @PathVariable UUID customerId,
                                   @Valid @RequestBody ApiDtos.CustomerRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateCustomer(actor, salonId, customerId, request.name(), request.phone(), request.marketingConsent());
    }
    /**
     * Sets a new password for one customer. No SMS code exists any more, so this
     * owner action is the only recovery path for a customer who forgot it.
     */
    @PostMapping("/customers/{customerId}/password-reset")
    public ResponseEntity<Void> resetCustomerPassword(@PathVariable UUID salonId, @PathVariable UUID customerId,
                                                      @Valid @RequestBody ApiDtos.PasswordResetRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        if (actor.role() != DomainTypes.ActorRole.OWNER && !actor.has("manage_customers")) {
            throw new ActorContext.AuthorizationException("Only the salon owner can set a customer password");
        }
        authService.resetCustomerPassword(salonId, customerId, request.password());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/services")
    public ServiceOffering createService(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.ServiceRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return salonService.createService(actor, salonId, request.name(), request.priceMinor(), request.durationMinutes(), request.category()); }
    @GetMapping("/services")
    public List<ServiceOffering> services(@PathVariable UUID salonId,
                                           @RequestParam(defaultValue = "false") boolean includeInactive) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.listServices(actor, salonId, includeInactive);
    }
    @PutMapping("/services/{serviceId}")
    public ServiceOffering updateService(@PathVariable UUID salonId, @PathVariable UUID serviceId,
                                         @Valid @RequestBody ApiDtos.ServiceRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateService(actor, salonId, serviceId, request.name(), request.priceMinor(),
                request.durationMinutes(), request.category(), request.active());
    }
    @GetMapping("/add-ons")
    public List<AddOn> addOns(@PathVariable UUID salonId,
                              @RequestParam(required = false) UUID serviceId,
                              @RequestParam(defaultValue = "false") boolean includeInactive) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.listAddOns(actor, salonId, serviceId, includeInactive);
    }
    @PostMapping("/add-ons")
    public AddOn createAddOn(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.AddOnRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.createAddOn(actor, salonId, request.name(), request.priceMinor(), request.durationMinutes(), request.serviceId(), request.description(), request.active() == null || request.active());
    }
    @PutMapping("/add-ons/{addOnId}")
    public AddOn updateAddOn(@PathVariable UUID salonId, @PathVariable UUID addOnId, @Valid @RequestBody ApiDtos.AddOnRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateAddOn(actor, salonId, addOnId, request.name(), request.priceMinor(), request.durationMinutes(), request.serviceId(), request.description(), request.active());
    }
    @DeleteMapping("/add-ons/{addOnId}")
    public AddOn archiveAddOn(@PathVariable UUID salonId, @PathVariable UUID addOnId) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.archiveAddOn(actor, salonId, addOnId);
    }
    @GetMapping({"/haircut-styles", "/styles"})
    public List<HaircutStyle> haircutStyles(@PathVariable UUID salonId,
                                            @RequestParam(defaultValue = "false") boolean includeInactive) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        // Customers receive active cards only; inactive records are restricted to
        // owner/manager permissions for admin catalog maintenance.
        return salonService.listHaircutStyles(actor, salonId, includeInactive);
    }
    @PostMapping({"/haircut-styles", "/styles"})
    public HaircutStyle createHaircutStyle(@PathVariable UUID salonId,
                                           @Valid @RequestBody ApiDtos.HaircutStyleRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.createHaircutStyle(actor, salonId, request.name(), request.photoUri(), request.photoAltText(),
                request.priceMinor(), request.description(), request.displayOrder(), request.serviceId());
    }
    @PutMapping({"/haircut-styles/{styleId}", "/styles/{styleId}"})
    public HaircutStyle updateHaircutStyle(@PathVariable UUID salonId, @PathVariable UUID styleId,
                                           @Valid @RequestBody ApiDtos.HaircutStyleRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateHaircutStyle(actor, salonId, styleId, request.name(), request.photoUri(), request.photoAltText(),
                request.priceMinor(), request.description(), request.displayOrder(), request.active(), request.serviceId());
    }
    @PatchMapping({"/haircut-styles/{styleId}/photo", "/styles/{styleId}/photo"})
    public HaircutStyle updateHaircutStylePhoto(@PathVariable UUID salonId, @PathVariable UUID styleId,
                                                @Valid @RequestBody ApiDtos.HaircutStylePhotoRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateHaircutStylePhoto(actor, salonId, styleId, request.photoUri(), request.photoAltText());
    }
    @DeleteMapping({"/haircut-styles/{styleId}", "/styles/{styleId}"})
    public HaircutStyle archiveHaircutStyle(@PathVariable UUID salonId, @PathVariable UUID styleId) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.archiveHaircutStyle(actor, salonId, styleId);
    }
    @PostMapping("/staff")
    public Staff createStaff(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.StaffRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return salonService.createStaff(actor, salonId, request.name(), request.phone(), request.role()); }
    @GetMapping("/staff")
    public List<Staff> staff(@PathVariable UUID salonId,
                             @RequestParam(defaultValue = "false") boolean includeInactive) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.listStaff(actor, salonId, includeInactive);
    }
    @PutMapping("/staff/{staffId}")
    public Staff updateStaff(@PathVariable UUID salonId, @PathVariable UUID staffId,
                             @Valid @RequestBody ApiDtos.StaffRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return salonService.updateStaff(actor, salonId, staffId, request.name(), request.phone(), request.role(),
                request.active(), request.permissionsJson());
    }
    @PostMapping("/bookings")
    public Booking createBooking(@PathVariable UUID salonId,
                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                 @Valid @RequestBody ApiDtos.BookingRequest request) {
        ActorContext actor = actors.require();
        actor.requireSalon(salonId);
        if (request.addOnIds() != null && !request.addOnIds().isEmpty()) {
            return salonService.createBooking(actor, salonId, request.customerId(), request.serviceId(), request.staffId(), request.startsAt(), request.paymentMethod(), request.addOnIds(), idempotencyKey);
        }
        return salonService.createBooking(actor, salonId, request.customerId(), request.serviceId(), request.staffId(), request.startsAt(), request.paymentMethod(), request.addOnMinor(), request.addOnMinutes(), idempotencyKey);
    }
    @PostMapping("/bookings/{bookingId}/status")
    public Booking updateBookingStatus(@PathVariable UUID salonId, @PathVariable UUID bookingId,
                                       @RequestHeader("Idempotency-Key") String key,
                                       @Valid @RequestBody ApiDtos.BookingStatusRequest request) {
        ActorContext actor = actors.require();
        actor.requireSalon(salonId);
        return bookingService.updateStatus(actor, salonId, bookingId, request.status(), request.reason(), key);
    }
    @PostMapping("/referrals")
    public Referral createReferral(@PathVariable UUID salonId, @Valid @RequestBody ApiDtos.ReferralRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return salonService.createReferral(actor, salonId, request.referrerId(), request.referredId(), request.code()); }
    @GetMapping("/payment-methods")
    public List<PaymentMethodConfig> paymentMethods(@PathVariable UUID salonId,
                                                    @RequestParam(defaultValue = "false") boolean includeInactive) {
        ActorContext actor = actors.require();
        return salonService.paymentMethods(actor, salonId, includeInactive);
    }
    @PutMapping("/payment-methods/{providerCode}")
    public PaymentMethodConfig updatePaymentMethod(@PathVariable UUID salonId, @PathVariable String providerCode, @Valid @RequestBody ApiDtos.PaymentMethodRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        if (!providerCode.equalsIgnoreCase(request.providerCode())) throw new IllegalArgumentException("Provider path/body mismatch");
        return salonService.updatePaymentMethod(actor, salonId, providerCode, request.displayName(), request.accountTitle(), request.accountReference(), request.accountToken(), request.instructions(), request.enabled(), request.sortOrder(), request.mode());
    }
}
