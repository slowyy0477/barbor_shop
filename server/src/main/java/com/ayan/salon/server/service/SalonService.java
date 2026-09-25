package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.domain.DomainTypes.*;
import com.ayan.salon.server.domain.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SalonService {
    private final SalonSettingsRepository settings;
    private final CustomerRepository customers;
    private final WalletRepository wallets;
    private final ServiceOfferingRepository services;
    private final StaffRepository staff;
    private final BookingRepository bookings;
    private final ReferralRepository referrals;
    private final ReminderRepository reminders;
    private final PaymentMethodConfigRepository paymentMethods;
    private final HaircutStyleRepository haircutStyles;
    private final AddOnRepository addOns;
    private final AuthAccountRepository authAccounts;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    /**
     * Kept for direct domain callers that predate booking idempotency. HTTP callers
     * use the authenticated constructor below and the keyed createBooking overload.
     */
    public SalonService(SalonSettingsRepository settings, CustomerRepository customers, WalletRepository wallets,
                        ServiceOfferingRepository services, StaffRepository staff, BookingRepository bookings,
                        ReferralRepository referrals, ReminderRepository reminders, PaymentMethodConfigRepository paymentMethods, AuditService audit) {
        this(settings, customers, wallets, services, staff, bookings, referrals, reminders, paymentMethods, audit, null, null, null, null);
    }

    /** Compatibility constructor for direct service callers and existing tests. */
    public SalonService(SalonSettingsRepository settings, CustomerRepository customers, WalletRepository wallets,
                        ServiceOfferingRepository services, StaffRepository staff, BookingRepository bookings,
                        ReferralRepository referrals, ReminderRepository reminders, PaymentMethodConfigRepository paymentMethods,
                        AuditService audit, IdempotencyService idempotency) {
        this(settings, customers, wallets, services, staff, bookings, referrals, reminders, paymentMethods, audit, idempotency, null, null, null);
    }

    /** Compatibility constructor for tests that provide the haircut catalog but not add-ons. */
    public SalonService(SalonSettingsRepository settings, CustomerRepository customers, WalletRepository wallets,
                        ServiceOfferingRepository services, StaffRepository staff, BookingRepository bookings,
                        ReferralRepository referrals, ReminderRepository reminders, PaymentMethodConfigRepository paymentMethods,
                        AuditService audit, IdempotencyService idempotency, HaircutStyleRepository haircutStyles) {
        this(settings, customers, wallets, services, staff, bookings, referrals, reminders, paymentMethods, audit, idempotency, haircutStyles, null, null);
    }

    public SalonService(SalonSettingsRepository settings, CustomerRepository customers, WalletRepository wallets,
                        ServiceOfferingRepository services, StaffRepository staff, BookingRepository bookings,
                        ReferralRepository referrals, ReminderRepository reminders, PaymentMethodConfigRepository paymentMethods,
                        AuditService audit, IdempotencyService idempotency, HaircutStyleRepository haircutStyles,
                        AddOnRepository addOns) {
        this(settings, customers, wallets, services, staff, bookings, referrals, reminders, paymentMethods,
                audit, idempotency, haircutStyles, addOns, null);
    }

    @Autowired
    public SalonService(SalonSettingsRepository settings, CustomerRepository customers, WalletRepository wallets,
                        ServiceOfferingRepository services, StaffRepository staff, BookingRepository bookings,
                        ReferralRepository referrals, ReminderRepository reminders, PaymentMethodConfigRepository paymentMethods,
                        AuditService audit, IdempotencyService idempotency, HaircutStyleRepository haircutStyles,
                        AddOnRepository addOns, AuthAccountRepository authAccounts) {
        this.settings = settings; this.customers = customers; this.wallets = wallets; this.services = services; this.staff = staff; this.bookings = bookings; this.referrals = referrals; this.reminders = reminders; this.paymentMethods = paymentMethods; this.audit = audit; this.idempotency = idempotency; this.haircutStyles = haircutStyles; this.addOns = addOns; this.authAccounts = authAccounts;
    }

    @Transactional
    public SalonSettings updateSettings(ActorContext actor, UUID salonId, String name, String address, String phone,
                                        int reminderDays, long depositBonus, long referrerReward, long newCustomerDiscount,
                                        long minWithdrawal, long maxDailyWithdrawal, boolean consumePromoFirst) {
        actor.requireSalon(salonId); actor.require("modify_business_settings");
        if (reminderDays < 0 || depositBonus < 0 || referrerReward < 0 || newCustomerDiscount < 0 || minWithdrawal < 0 || maxDailyWithdrawal < 0) throw new IllegalArgumentException("Settings amounts cannot be negative");
        SalonSettings value = settings.findById(salonId).orElseThrow(() -> new WalletService.NotFoundException("Salon settings not found"));
        value.update(name, address, phone, reminderDays, depositBonus, referrerReward, newCustomerDiscount, minWithdrawal, maxDailyWithdrawal, consumePromoFirst);
        audit.record(salonId, actor.actorId(), "SETTINGS_UPDATED", "SalonSettings", salonId, "Owner settings changed");
        return value;
    }

    /** Full settings update with optional owner-managed branding fields. */
    @Transactional
    public SalonSettings updateSettings(ActorContext actor, UUID salonId, String name, String address, String phone,
                                        int reminderDays, long depositBonus, long referrerReward, long newCustomerDiscount,
                                        long minWithdrawal, long maxDailyWithdrawal, boolean consumePromoFirst,
                                        String primaryColor, String logoUri) {
        actor.requireSalon(salonId); actor.require("modify_business_settings");
        if (reminderDays < 0 || depositBonus < 0 || referrerReward < 0 || newCustomerDiscount < 0 || minWithdrawal < 0 || maxDailyWithdrawal < 0) throw new IllegalArgumentException("Settings amounts cannot be negative");
        SalonSettings value = settings.findById(salonId).orElseThrow(() -> new WalletService.NotFoundException("Salon settings not found"));
        value.update(name, address, phone, reminderDays, depositBonus, referrerReward, newCustomerDiscount,
                minWithdrawal, maxDailyWithdrawal, consumePromoFirst, primaryColor, logoUri);
        audit.record(salonId, actor.actorId(), "SETTINGS_UPDATED", "SalonSettings", salonId, "Owner settings and branding changed");
        return value;
    }

    @Transactional(readOnly = true)
    public SalonSettings getSettings(ActorContext actor, UUID salonId) {
        actor.requireSalon(salonId);
        // The full settings object includes withdrawal/referral policy. Public
        // branding is exposed separately by the read-only catalog endpoint.
        actor.require("modify_business_settings");
        return settings.findById(salonId).orElseThrow(() -> new WalletService.NotFoundException("Salon settings not found"));
    }

    @Transactional
    public SalonSettings updateBranding(ActorContext actor, UUID salonId, String primaryColor, String logoUri) {
        actor.requireSalon(salonId); actor.require("modify_business_settings");
        SalonSettings value = settings.findById(salonId).orElseThrow(() -> new WalletService.NotFoundException("Salon settings not found"));
        value.updateBranding(primaryColor, logoUri);
        audit.record(salonId, actor.actorId(), "BRANDING_UPDATED", "SalonSettings", salonId, "Salon theme/logo changed");
        return value;
    }

    @Transactional
    public Customer createCustomer(ActorContext actor, UUID salonId, String name, String phone, boolean marketingConsent) {
        actor.requireSalon(salonId); actor.require("manage_customers");
        if (name == null || name.isBlank() || phone == null || phone.isBlank()) throw new IllegalArgumentException("Customer name/phone required");
        String canonicalPhone = canonicalPakistaniPhone(phone);
        String phoneHash = hashPhone(canonicalPhone);
        if (phoneAlreadyExists(salonId, canonicalPhone, phoneHash)) throw new WalletService.ConflictException("Customer phone already exists");
        if (privilegedPhoneAlreadyExists(salonId, phoneHash)) {
            throw new WalletService.ConflictException("Customer phone is reserved for a salon team account");
        }
        Customer customer = customers.save(new Customer(salonId, name.trim(), canonicalPhone, phoneHash)); customer.setMarketingConsent(marketingConsent); wallets.save(new Wallet(salonId, customer.getId()));
        audit.record(salonId, actor.actorId(), "CUSTOMER_CREATED", "Customer", customer.getId(), null); return customer;
    }

    @Transactional
    public Customer updateCustomer(ActorContext actor, UUID salonId, UUID customerId,
                                   String name, String phone, boolean marketingConsent) {
        actor.requireSalon(salonId);
        actor.require("manage_customers");
        if (customerId == null) throw new IllegalArgumentException("Customer id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Customer name is required");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Customer phone is required");

        Customer customer = customers.findBySalonIdAndId(salonId, customerId)
                .orElseThrow(() -> new WalletService.NotFoundException("Customer not found"));
        String canonicalPhone = canonicalPakistaniPhone(phone);
        String phoneHash = hashPhone(canonicalPhone);
        if (phoneAlreadyExists(salonId, canonicalPhone, phoneHash, customerId)) {
            throw new WalletService.ConflictException("Customer phone already exists");
        }
        if (privilegedPhoneAlreadyExists(salonId, phoneHash)) {
            throw new WalletService.ConflictException("Customer phone is reserved for a salon team account");
        }
        boolean phoneChanged = !canonicalPhone.equals(customer.getPhone())
                || !phoneHash.equals(customer.getPhoneHash());
        customer.updateOwnerProfile(name, canonicalPhone, phoneHash, marketingConsent);
        Customer saved = customers.save(customer);
        // Never put the raw phone number or its hash in the audit trail.
        audit.record(salonId, actor.actorId(), "CUSTOMER_UPDATED", "Customer", saved.getId(),
                "phoneChanged=" + phoneChanged + ";marketingConsent=" + marketingConsent);
        return saved;
    }

    /** Compatibility overload: the client-supplied hash is deliberately ignored. */
    @Deprecated
    public Customer createCustomer(ActorContext actor, UUID salonId, String name, String phone, String ignoredPhoneHash, boolean marketingConsent) {
        return createCustomer(actor, salonId, name, phone, marketingConsent);
    }

    @Transactional
    public ServiceOffering createService(ActorContext actor, UUID salonId, String name, long priceMinor, int durationMinutes, String category) {
        actor.requireSalon(salonId); actor.require("manage_services");
        ServiceOffering service = services.save(new ServiceOffering(salonId, name, priceMinor, durationMinutes, category)); audit.record(salonId, actor.actorId(), "SERVICE_CREATED", "ServiceOffering", service.getId(), name); return service;
    }

    @Transactional(readOnly = true)
    public List<ServiceOffering> listServices(ActorContext actor, UUID salonId, boolean includeInactive) {
        actor.requireSalon(salonId);
        if (includeInactive) actor.require("manage_services");
        return includeInactive ? services.findBySalonIdOrderByName(salonId) : services.findBySalonIdAndActiveTrueOrderByName(salonId);
    }

    @Transactional
    public ServiceOffering updateService(ActorContext actor, UUID salonId, UUID serviceId, String name, long priceMinor,
                                         int durationMinutes, String category, boolean active) {
        actor.requireSalon(salonId); actor.require("manage_services");
        ServiceOffering value = services.findBySalonIdAndId(salonId, serviceId)
                .orElseThrow(() -> new WalletService.NotFoundException("Service not found"));
        value.update(name, priceMinor, durationMinutes, category, active);
        ServiceOffering saved = services.save(value);
        audit.record(salonId, actor.actorId(), "SERVICE_UPDATED", "ServiceOffering", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AddOn> listAddOns(ActorContext actor, UUID salonId, UUID serviceId, boolean includeInactive) {
        actor.requireSalon(salonId);
        requireAddOnRepository();
        if (includeInactive) actor.require("manage_services");
        if (!includeInactive) return addOns.findActiveApplicable(salonId, serviceId);
        return addOns.findBySalonIdOrderByName(salonId);
    }

    @Transactional
    public AddOn createAddOn(ActorContext actor, UUID salonId, String name, long priceMinor, int durationMinutes,
                             UUID serviceId, String description, boolean active) {
        actor.requireSalon(salonId); actor.require("manage_services"); requireAddOnRepository();
        if (serviceId != null) services.findBySalonIdAndId(salonId, serviceId)
                .orElseThrow(() -> new WalletService.NotFoundException("Service for add-on was not found"));
        AddOn value = new AddOn(salonId, name, priceMinor, durationMinutes, serviceId, description);
        if (!active) value.archive();
        AddOn saved = addOns.save(value);
        audit.record(salonId, actor.actorId(), "ADD_ON_CREATED", "AddOn", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public AddOn updateAddOn(ActorContext actor, UUID salonId, UUID addOnId, String name, long priceMinor,
                             int durationMinutes, UUID serviceId, String description, boolean active) {
        return updateAddOn(actor, salonId, addOnId, name, priceMinor, durationMinutes, serviceId, description, Boolean.valueOf(active));
    }

    /** Nullable active preserves the current state when an update omits it. */
    @Transactional
    public AddOn updateAddOn(ActorContext actor, UUID salonId, UUID addOnId, String name, long priceMinor,
                             int durationMinutes, UUID serviceId, String description, Boolean active) {
        actor.requireSalon(salonId); actor.require("manage_services"); requireAddOnRepository();
        if (serviceId != null) services.findBySalonIdAndId(salonId, serviceId)
                .orElseThrow(() -> new WalletService.NotFoundException("Service for add-on was not found"));
        AddOn value = addOns.findBySalonIdAndId(salonId, addOnId)
                .orElseThrow(() -> new WalletService.NotFoundException("Add-on not found"));
        value.update(name, priceMinor, durationMinutes, serviceId, description, active == null ? value.isActive() : active);
        AddOn saved = addOns.save(value);
        audit.record(salonId, actor.actorId(), "ADD_ON_UPDATED", "AddOn", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public AddOn archiveAddOn(ActorContext actor, UUID salonId, UUID addOnId) {
        actor.requireSalon(salonId); actor.require("manage_services"); requireAddOnRepository();
        AddOn value = addOns.findBySalonIdAndId(salonId, addOnId)
                .orElseThrow(() -> new WalletService.NotFoundException("Add-on not found"));
        value.archive();
        AddOn saved = addOns.save(value);
        audit.record(salonId, actor.actorId(), "ADD_ON_ARCHIVED", "AddOn", saved.getId(), saved.getName());
        return saved;
    }

    private void requireAddOnRepository() {
        if (addOns == null) throw new IllegalStateException("Add-on catalog is not configured");
    }

    @Transactional(readOnly = true)
    public List<HaircutStyle> listHaircutStyles(UUID salonId, boolean includeInactive) {
        if (haircutStyles == null) throw new IllegalStateException("Haircut style catalog is not configured");
        return includeInactive
                ? haircutStyles.findBySalonIdOrderByDisplayOrderAscNameAsc(salonId)
                : haircutStyles.findBySalonIdAndActiveTrueOrderByDisplayOrderAscNameAsc(salonId);
    }

    @Transactional(readOnly = true)
    public List<HaircutStyle> listHaircutStyles(ActorContext actor, UUID salonId, boolean includeInactive) {
        actor.requireSalon(salonId);
        if (includeInactive) actor.require("manage_services");
        return listHaircutStyles(salonId, includeInactive);
    }

    @Transactional
    public HaircutStyle createHaircutStyle(ActorContext actor, UUID salonId, String name, String photoUri,
                                           String photoAltText, long priceMinor, String description, int displayOrder,
                                           UUID serviceId) {
        actor.requireSalon(salonId); actor.require("manage_services");
        requireHaircutStyleRepository();
        requireBookableService(salonId, serviceId);
        HaircutStyle style = new HaircutStyle(salonId, name, photoUri, priceMinor, description, displayOrder);
        if (photoAltText != null && !photoAltText.isBlank()) style.updatePhoto(photoUri, photoAltText);
        style.linkService(serviceId);
        HaircutStyle saved = haircutStyles.save(style);
        audit.record(salonId, actor.actorId(), "HAIRCUT_STYLE_CREATED", "HaircutStyle", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public HaircutStyle updateHaircutStyle(ActorContext actor, UUID salonId, UUID styleId, String name, String photoUri,
                                           String photoAltText, long priceMinor, String description, int displayOrder,
                                           boolean active, UUID serviceId) {
        actor.requireSalon(salonId); actor.require("manage_services");
        requireHaircutStyleRepository();
        requireBookableService(salonId, serviceId);
        HaircutStyle style = haircutStyles.findBySalonIdAndId(salonId, styleId)
                .orElseThrow(() -> new WalletService.NotFoundException("Haircut style not found"));
        style.update(name, photoUri, priceMinor, description, displayOrder, active);
        if (photoAltText != null && !photoAltText.isBlank()) style.updatePhoto(photoUri, photoAltText);
        // A null serviceId intentionally clears the booking link.
        style.linkService(serviceId);
        HaircutStyle saved = haircutStyles.save(style);
        audit.record(salonId, actor.actorId(), "HAIRCUT_STYLE_UPDATED", "HaircutStyle", saved.getId(), saved.getName());
        return saved;
    }

    /** Replaces only the image reference/alt text, useful for an admin upload flow. */
    @Transactional
    public HaircutStyle updateHaircutStylePhoto(ActorContext actor, UUID salonId, UUID styleId,
                                                String photoUri, String photoAltText) {
        actor.requireSalon(salonId); actor.require("manage_services");
        requireHaircutStyleRepository();
        HaircutStyle style = haircutStyles.findBySalonIdAndId(salonId, styleId)
                .orElseThrow(() -> new WalletService.NotFoundException("Haircut style not found"));
        style.updatePhoto(photoUri, photoAltText);
        HaircutStyle saved = haircutStyles.save(style);
        audit.record(salonId, actor.actorId(), "HAIRCUT_STYLE_PHOTO_UPDATED", "HaircutStyle", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public HaircutStyle archiveHaircutStyle(ActorContext actor, UUID salonId, UUID styleId) {
        actor.requireSalon(salonId); actor.require("manage_services");
        requireHaircutStyleRepository();
        HaircutStyle style = haircutStyles.findBySalonIdAndId(salonId, styleId)
                .orElseThrow(() -> new WalletService.NotFoundException("Haircut style not found"));
        style.deactivate();
        HaircutStyle saved = haircutStyles.save(style);
        audit.record(salonId, actor.actorId(), "HAIRCUT_STYLE_ARCHIVED", "HaircutStyle", saved.getId(), saved.getName());
        return saved;
    }

    private void requireHaircutStyleRepository() {
        if (haircutStyles == null) throw new IllegalStateException("Haircut style catalog is not configured");
    }

    /**
     * A style may only point at a service that belongs to the same salon, so a
     * crafted request can never link (or leak) another tenant's service.
     */
    private void requireBookableService(UUID salonId, UUID serviceId) {
        if (serviceId == null) return;
        if (services == null) throw new IllegalStateException("Service catalogue is not configured");
        services.findBySalonIdAndId(salonId, serviceId)
                .orElseThrow(() -> new WalletService.NotFoundException("Linked service not found for this salon"));
    }

    @Transactional
    public Staff createStaff(ActorContext actor, UUID salonId, String name, String phone, ActorRole role) {
        actor.requireSalon(salonId); actor.require("manage_staff");
        if (role == ActorRole.OWNER) throw new IllegalArgumentException("Owner identity is managed by authentication");
        Staff member = staff.save(new Staff(salonId, name, phone, role)); audit.record(salonId, actor.actorId(), "STAFF_CREATED", "Staff", member.getId(), role.name()); return member;
    }

    @Transactional(readOnly = true)
    public List<Staff> listStaff(ActorContext actor, UUID salonId, boolean includeInactive) {
        actor.requireSalon(salonId); if (includeInactive) actor.require("manage_staff");
        return includeInactive ? staff.findBySalonId(salonId) : staff.findBySalonIdAndActiveTrue(salonId);
    }

    @Transactional
    public Staff updateStaff(ActorContext actor, UUID salonId, UUID staffId, String name, String phone, ActorRole role,
                             boolean active, String permissionsJson) {
        actor.requireSalon(salonId); actor.require("manage_staff");
        if (role == ActorRole.OWNER) throw new IllegalArgumentException("Owner identity is managed by authentication");
        Staff value = staff.findBySalonIdAndId(salonId, staffId)
                .orElseThrow(() -> new WalletService.NotFoundException("Staff member not found"));
        value.update(name, phone, role, active, permissionsJson);
        Staff saved = staff.save(value);
        audit.record(salonId, actor.actorId(), "STAFF_UPDATED", "Staff", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public Booking createBooking(ActorContext actor, UUID salonId, UUID customerId, UUID serviceId, UUID staffId,
                                 Instant startsAt, PaymentMethod paymentMethod, long addOnMinor, int addOnMinutes) {
        return createBookingInternal(actor, salonId, customerId, serviceId, staffId, startsAt, paymentMethod, addOnMinor, addOnMinutes, List.of(), null);
    }

    /**
     * Idempotent HTTP-facing booking creation. A retry with the same key returns the
     * original booking instead of reserving another copy of the slot.
     */
    @Transactional
    public Booking createBooking(ActorContext actor, UUID salonId, UUID customerId, UUID serviceId, UUID staffId,
                                 Instant startsAt, PaymentMethod paymentMethod, long addOnMinor, int addOnMinutes,
                                 String idempotencyKey) {
        if (idempotency == null) throw new IllegalStateException("Booking idempotency is not configured");
        return createBookingInternal(actor, salonId, customerId, serviceId, staffId, startsAt, paymentMethod, addOnMinor, addOnMinutes, List.of(), idempotencyKey);
    }

    /** HTTP booking variant: the server prices catalog IDs and snapshots their values. */
    @Transactional
    public Booking createBooking(ActorContext actor, UUID salonId, UUID customerId, UUID serviceId, UUID staffId,
                                 Instant startsAt, PaymentMethod paymentMethod, List<UUID> addOnIds,
                                 String idempotencyKey) {
        if (idempotency == null) throw new IllegalStateException("Booking idempotency is not configured");
        return createBookingInternal(actor, salonId, customerId, serviceId, staffId, startsAt, paymentMethod, 0, 0,
                addOnIds == null ? List.of() : addOnIds, idempotencyKey);
    }

    private Booking createBookingInternal(ActorContext actor, UUID salonId, UUID customerId, UUID serviceId, UUID staffId,
                                           Instant startsAt, PaymentMethod paymentMethod, long addOnMinor, int addOnMinutes,
                                           List<UUID> requestedAddOnIds, String idempotencyKey) {
        actor.requireSalon(salonId); actor.require("create_booking");
        if (startsAt == null) throw new IllegalArgumentException("Booking start is required");
        if (paymentMethod == null) throw new IllegalArgumentException("Payment method is required");
        // Preserve the HTTP contract: an invalid idempotency key is rejected
        // before any booking-specific validation or repository work.
        if (idempotencyKey != null) IdempotencyService.normalizeKey(idempotencyKey);
        if (idempotencyKey != null && staffId == null) throw new WalletService.RuleViolationException("A staff member is required for an HTTP booking");
        if (addOnMinor < 0 || addOnMinutes < 0) throw new IllegalArgumentException("Add-on values cannot be negative");
        if ((addOnMinor == 0) != (addOnMinutes == 0)) throw new IllegalArgumentException("Add-on amount and duration must be provided together");
        List<UUID> addOnIds = requestedAddOnIds == null ? List.of() : List.copyOf(requestedAddOnIds);
        if (!addOnIds.isEmpty() && (addOnMinor != 0 || addOnMinutes != 0)) {
            throw new WalletService.RuleViolationException("Use add-on catalog IDs instead of client totals");
        }
        if (addOnIds.size() > 10 || new java.util.HashSet<>(addOnIds).size() != addOnIds.size()) {
            throw new WalletService.RuleViolationException("Add-on selection is invalid");
        }
        if (addOnIds.isEmpty() && (addOnMinor != 0 || addOnMinutes != 0)) {
            // Legacy callers are not allowed to invent prices or duration.
            throw new WalletService.RuleViolationException("Add-ons must use server catalog IDs");
        }

        String requestFingerprint = null;
        if (idempotencyKey != null) {
            requestFingerprint = IdempotencyService.fingerprintFields(
                    "booking.create", actor.actorId(), customerId, serviceId, staffId,
                    startsAt, paymentMethod, canonicalIdList(addOnIds));
            String existing = idempotency.begin(idempotencyKey, salonId, "booking.create", requestFingerprint);
            if (existing != null) {
                UUID existingId = IdempotencyService.responseId(existing);
                return bookings.findById(existingId)
                        .filter(value -> salonId.equals(value.getSalonId()))
                        .orElseThrow(() -> new WalletService.NotFoundException("Booking not found"));
            }
        }

        if (!startsAt.isAfter(Instant.now())) throw new WalletService.RuleViolationException("Booking must start in the future");

        requireCustomerOwnership(actor, customerId);
        Customer customer = customers.findBySalonIdAndId(salonId, customerId)
                .filter(value -> value.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new WalletService.NotFoundException("Active customer not found"));
        ServiceOffering service = services.findBySalonIdAndId(salonId, serviceId).filter(ServiceOffering::isActive).orElseThrow(() -> new WalletService.NotFoundException("Service not found"));
        if (staffId != null) staff.lockBySalonIdAndId(salonId, staffId).filter(Staff::isActive).orElseThrow(() -> new WalletService.NotFoundException("Staff member not found"));
        long catalogAddOnMinor = 0;
        int catalogAddOnMinutes = 0;
        String addOnSnapshot = null;
        boolean hasCatalogAddOns = !addOnIds.isEmpty();
        if (!addOnIds.isEmpty()) {
            requireAddOnRepository();
            List<AddOn> available = addOns.findActiveApplicable(salonId, serviceId);
            java.util.Map<UUID, AddOn> byId = new java.util.HashMap<>();
            available.forEach(value -> byId.put(value.getId(), value));
            java.util.List<AddOn> selected = new java.util.ArrayList<>();
            for (UUID addOnId : addOnIds) {
                AddOn value = byId.get(addOnId);
                if (value == null) throw new WalletService.NotFoundException("Selected add-on is unavailable for this service");
                selected.add(value);
                try {
                    catalogAddOnMinor = Math.addExact(catalogAddOnMinor, value.getPriceMinor());
                    catalogAddOnMinutes = Math.addExact(catalogAddOnMinutes, value.getDurationMinutes());
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException("Add-on total is too large");
                }
            }
            addOnSnapshot = addOnSnapshot(selected);
        }
        final long totalMinor;
        final int totalDurationMinutes;
        try {
            totalMinor = Math.addExact(service.getPriceMinor(), hasCatalogAddOns ? catalogAddOnMinor : addOnMinor);
            totalDurationMinutes = Math.addExact(service.getDurationMinutes(), hasCatalogAddOns ? catalogAddOnMinutes : addOnMinutes);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Booking amount or duration is too large");
        }
        final Instant endsAt;
        try {
            endsAt = startsAt.plusSeconds(Math.multiplyExact((long) totalDurationMinutes, 60L));
        } catch (ArithmeticException | java.time.DateTimeException overflow) {
            throw new IllegalArgumentException("Booking time is out of range");
        }
        if (staffId != null && bookings.hasOverlap(salonId, staffId, startsAt, endsAt, java.util.List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) throw new WalletService.ConflictException("Selected slot is already reserved");
        if (paymentMethod == PaymentMethod.WALLET) {
            // Lock the wallet while checking outstanding wallet bookings. This
            // serializes concurrent confirmations for the same customer and
            // prevents two bookings from spending the same credit.
            Wallet wallet = wallets.lockBySalonIdAndCustomerId(salonId, customerId)
                    .orElseThrow(() -> new WalletService.NotFoundException("Wallet not found"));
            long committed = bookings.sumActiveWalletTotals(salonId, customerId, PaymentMethod.WALLET,
                    java.util.List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
            if (committed > wallet.usableMinor() || totalMinor > wallet.usableMinor() - committed) {
                throw new WalletService.RuleViolationException("Wallet balance is already committed to another booking");
            }
        }
        long snapshotAddOnMinor = hasCatalogAddOns ? catalogAddOnMinor : addOnMinor;
        Booking booking = new Booking(salonId, customer.getId(), service.getId(), staffId, startsAt, endsAt, totalMinor, snapshotAddOnMinor, paymentMethod);
        booking.setAddOnSnapshot(addOnSnapshot);
        booking.confirm();
        booking = bookings.save(booking);
        reminders.findBySalonIdAndCustomerIdAndServiceIdAndStatusIn(salonId, customerId, serviceId,
                        java.util.List.of(ReminderStatus.SCHEDULED, ReminderStatus.SENT))
                .forEach(reminder -> { reminder.booked(); reminders.save(reminder); });
        if (idempotencyKey != null) idempotency.complete(idempotencyKey, salonId, "booking.create", booking.getId().toString(), requestFingerprint);
        audit.record(salonId, actor.actorId(), "BOOKING_CONFIRMED", "Booking", booking.getId(), "slot=" + startsAt); return booking;
    }

    private static String canonicalIdList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(String::valueOf).sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private static String addOnSnapshot(List<AddOn> selected) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) json.append(',');
            AddOn value = selected.get(i);
            json.append("{\"id\":\"").append(value.getId()).append("\",\"name\":\"")
                    .append(value.getName().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\",\"priceMinor\":").append(value.getPriceMinor())
                    .append(",\"durationMinutes\":").append(value.getDurationMinutes()).append('}');
        }
        return json.append(']').toString();
    }

    @Transactional
    public Referral createReferral(ActorContext actor, UUID salonId, UUID referrerId, UUID referredId, String code) {
        actor.requireSalon(salonId); actor.require("create_referral");
        if (referrerId == null || referredId == null) throw new IllegalArgumentException("Referral customers are required");
        if (referrerId.equals(referredId)) throw new IllegalArgumentException("Self referral is not allowed");
        // Customer tokens may only claim a referral for their own profile.
        if (actor.role() == ActorRole.CUSTOMER && !actor.actorId().equals(referredId)) {
            throw new ActorContext.AuthorizationException("Customer referral ownership mismatch");
        }
        requireActiveCustomer(salonId, referrerId, "Referrer");
        requireActiveCustomer(salonId, referredId, "Referred customer");
        if (code == null || code.isBlank() || code.trim().length() > 32) throw new IllegalArgumentException("Referral code is required");
        if (referrals.findBySalonIdAndReferredCustomerId(salonId, referredId).isPresent()) throw new WalletService.ConflictException("Customer already has a referral");
        SalonSettings s = settings.findById(salonId).orElseThrow(() -> new WalletService.NotFoundException("Salon settings not found"));
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        Referral referral = referrals.save(new Referral(salonId, referrerId, referredId, normalizedCode, s.getReferralReferrerMinor(), s.getReferralNewCustomerMinor()));
        audit.record(salonId, actor.actorId(), "REFERRAL_CREATED", "Referral", referral.getId(), "code=" + normalizedCode); return referral;
    }

    private Customer requireActiveCustomer(UUID salonId, UUID customerId, String label) {
        return customers.findBySalonIdAndId(salonId, customerId)
                .filter(value -> value.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new WalletService.NotFoundException("Active " + label.toLowerCase(Locale.ROOT) + " not found"));
    }

    private void requireCustomerOwnership(ActorContext actor, UUID customerId) {
        if (actor.role() == ActorRole.CUSTOMER && !actor.actorId().equals(customerId)) {
            throw new ActorContext.AuthorizationException("Customer booking ownership mismatch");
        }
    }

    private boolean phoneAlreadyExists(UUID salonId, String canonicalPhone, String phoneHash) {
        return phoneAlreadyExists(salonId, canonicalPhone, phoneHash, null);
    }

    private boolean phoneAlreadyExists(UUID salonId, String canonicalPhone, String phoneHash, UUID excludedCustomerId) {
        java.util.function.Predicate<Customer> isOtherCustomer = value -> excludedCustomerId == null
                || !excludedCustomerId.equals(value.getId());
        java.util.Optional<Customer> byPhone = customers.findBySalonIdAndPhone(salonId, canonicalPhone);
        if (byPhone.isPresent() && isOtherCustomer.test(byPhone.get())) return true;
        java.util.Optional<Customer> byHash = customers.findBySalonIdAndPhoneHash(salonId, phoneHash);
        if (byHash.isPresent() && isOtherCustomer.test(byHash.get())) return true;
        // This catches pre-hardening rows that used a different display format.
        List<Customer> existing = customers.findBySalonId(salonId);
        return existing != null && existing.stream().anyMatch(value -> isOtherCustomer.test(value)
                && (canonicalPhone.equals(safeCanonicalPhone(value.getPhone())) || phoneHash.equals(value.getPhoneHash())));
    }

    private boolean privilegedPhoneAlreadyExists(UUID salonId, String phoneHash) {
        if (authAccounts != null
                && authAccounts.findBySalonIdAndPhoneHashAndStatus(salonId, phoneHash, AccountStatus.ACTIVE).isPresent()) {
            return true;
        }
        if (staff == null) return false;
        List<Staff> activeStaff = staff.findBySalonIdAndActiveTrue(salonId);
        return activeStaff != null && activeStaff.stream()
                .filter(value -> value.getPhone() != null)
                .anyMatch(value -> phoneHash.equals(hashPhone(safeCanonicalPhone(value.getPhone()))));
    }

    private static String safeCanonicalPhone(String phone) {
        try { return canonicalPakistaniPhone(phone); }
        catch (IllegalArgumentException ignored) { return ""; }
    }

    private static String canonicalPakistaniPhone(String value) {
        return PhoneIdentity.canonicalPakistani(value);
    }

    private static String hashPhone(String canonicalPhone) {
        return PhoneIdentity.sha256(canonicalPhone);
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentMethodConfig> enabledPaymentMethods(UUID salonId) {
        return paymentMethods.findBySalonIdAndEnabledTrueOrderBySortOrder(salonId);
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentMethodConfig> paymentMethods(ActorContext actor, UUID salonId, boolean includeInactive) {
        actor.requireSalon(salonId);
        if (includeInactive) actor.require("modify_business_settings");
        return includeInactive
                ? paymentMethods.findBySalonIdOrderBySortOrder(salonId)
                : paymentMethods.findBySalonIdAndEnabledTrueOrderBySortOrder(salonId);
    }

    @Transactional
    public PaymentMethodConfig updatePaymentMethod(ActorContext actor, UUID salonId, String providerCode, String displayName,
                                                   String accountTitle, String accountToken, String instructions,
                                                   boolean enabled, int sortOrder, ProviderMode mode) {
        return updatePaymentMethod(actor, salonId, providerCode, displayName, accountTitle, null, accountToken,
                instructions, enabled, sortOrder, mode);
    }

    @Transactional
    public PaymentMethodConfig updatePaymentMethod(ActorContext actor, UUID salonId, String providerCode, String displayName,
                                                   String accountTitle, String accountReference, String accountToken, String instructions,
                                                   boolean enabled, int sortOrder, ProviderMode mode) {
        actor.requireSalon(salonId); actor.require("modify_business_settings");
        if (providerCode == null || providerCode.isBlank() || displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Provider code and display name are required");
        PaymentMethodConfig config = paymentMethods.findBySalonIdAndProvider(salonId, providerCode.trim().toUpperCase(java.util.Locale.ROOT))
                .orElseGet(() -> new PaymentMethodConfig(salonId, providerCode.trim().toUpperCase(java.util.Locale.ROOT), displayName.trim(), accountTitle, accountReference, accountToken, sortOrder));
        // Secret provider tokens are never edited by the browser settings form.
        // A null/blank token therefore preserves the existing value.
        String nextToken = accountToken == null || accountToken.isBlank() ? config.getAccountToken() : accountToken;
        String nextReference = accountReference == null ? config.getAccountReference() : accountReference.trim();
        config.update(displayName.trim(), accountTitle, nextReference, nextToken, instructions, enabled, sortOrder, mode == null ? ProviderMode.MANUAL : mode);
        PaymentMethodConfig saved = paymentMethods.save(config);
        audit.record(salonId, actor.actorId(), "PAYMENT_METHOD_UPDATED", "PaymentMethodConfig", saved.getId(), providerCode);
        return saved;
    }
}
