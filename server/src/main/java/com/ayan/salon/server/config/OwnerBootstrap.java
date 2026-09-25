package com.ayan.salon.server.config;

import com.ayan.salon.server.domain.AuthAccount;
import com.ayan.salon.server.domain.Staff;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.repository.AuthAccountRepository;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.StaffRepository;
import com.ayan.salon.server.service.PhoneIdentity;
import com.ayan.salon.server.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Optional first-owner bootstrap. It stores only a hash and is inert without both env values. */
@Component
@Profile("!test")
public class OwnerBootstrap implements ApplicationRunner {
    private final AuthAccountRepository accounts;
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final AuthService auth;
    private final String phone;
    private final String salonId;
    private final String pin;

    public OwnerBootstrap(AuthAccountRepository accounts, CustomerRepository customers, StaffRepository staff,
                          AuthService auth,
                          @Value("${ayan.auth.owner-phone:}") String phone,
                          @Value("${ayan.auth.owner-salon-id:}") String salonId,
                          @Value("${ayan.auth.owner-pin:}") String pin) {
        this.accounts = accounts; this.customers = customers; this.staff = staff;
        this.auth = auth;
        this.phone = phone == null ? "" : phone.trim(); this.salonId = salonId == null ? "" : salonId.trim();
        this.pin = pin == null ? "" : pin.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (phone.isBlank() || salonId.isBlank()) return;
        UUID salon;
        try {
            salon = UUID.fromString(salonId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("AYAN_AUTH_OWNER_SALON_ID is not a valid salon id", invalid);
        }
        // One or more owner mobile numbers may be configured, separated by
        // commas or spaces. The owner can register a new phone while an older
        // one keeps working, and every number shares the same owner password.
        java.util.List<String> configured = java.util.Arrays.stream(phone.split("[,\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        java.util.List<String> skipped = new java.util.ArrayList<>();
        int registered = 0;
        for (String candidate : configured) {
            try {
                registerOwner(salon, candidate);
                registered++;
            } catch (IllegalStateException | IllegalArgumentException problem) {
                skipped.add(problem.getMessage());
            }
        }
        if (registered == 0 && !skipped.isEmpty()) {
            throw new IllegalStateException(String.join(" | ", skipped));
        }
        for (String warning : skipped) System.err.println("[owner-bootstrap] skipped: " + warning);
    }

    private void registerOwner(UUID salon, String configuredPhone) {
        String canonicalPhone = PhoneIdentity.canonicalPakistani(configuredPhone);
        String hash = PhoneIdentity.sha256(canonicalPhone);
        boolean customerCollision = customers.findBySalonIdAndPhone(salon, canonicalPhone).isPresent()
                || customers.findBySalonIdAndPhoneHash(salon, hash)
                .filter(value -> value.getStatus() == com.ayan.salon.server.domain.DomainTypes.AccountStatus.ACTIVE)
                .isPresent();
        boolean staffCollision = staff.findBySalonIdAndActiveTrue(salon).stream()
                .filter(value -> value.getPhone() != null)
                .anyMatch(value -> samePhoneHash(value, hash));
        if (customerCollision || staffCollision) {
            throw new IllegalStateException("Owner phone " + canonicalPhone + " already belongs to a customer or barber"
                    + " of this salon. The owner must use a different number, or that record's number has to change first.");
        }
        AuthAccount existing = accounts.findBySalonIdAndPhoneHashAndStatus(
                salon, hash, com.ayan.salon.server.domain.DomainTypes.AccountStatus.ACTIVE).orElse(null);
        if (existing != null && existing.getRole() != ActorRole.OWNER) {
            throw new IllegalStateException("Configured owner phone belongs to a non-owner salon account");
        }
        AuthAccount owner = existing == null
                ? accounts.save(new AuthAccount(salon, hash, ActorRole.OWNER, "[]"))
                : existing;
        // The first owner PIN is stored once, as a salted digest. Without an
        // OTP provider a laptop pilot would otherwise have no way to sign in.
        if (!pin.isBlank()) auth.ensureActorPin(salon, owner.getId(), pin);
    }

    private static boolean samePhoneHash(Staff value, String hash) {
        try {
            return hash.equals(PhoneIdentity.sha256(PhoneIdentity.canonicalPakistani(value.getPhone())));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
