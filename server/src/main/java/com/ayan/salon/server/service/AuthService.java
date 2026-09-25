package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.AuthAccount;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.OtpChallenge;
import com.ayan.salon.server.domain.SignInPin;
import com.ayan.salon.server.domain.Staff;
import com.ayan.salon.server.domain.DomainTypes.AccountStatus;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.repository.AuthAccountRepository;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.OtpChallengeRepository;
import com.ayan.salon.server.domain.repository.StaffRepository;
import com.ayan.salon.server.domain.repository.SignInPinRepository;
import com.ayan.salon.server.domain.repository.WalletRepository;
import com.ayan.salon.server.domain.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** OTP challenge lifecycle and server-issued session creation. */
@Service
public class AuthService {
    /** Wrong-PIN allowance before the account is temporarily locked. */
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final Duration PIN_LOCK = Duration.ofMinutes(15);
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final AuthAccountRepository accounts;
    private final SignInPinRepository pins;
    private final WalletRepository wallets;
    private final OtpChallengeRepository challenges;
    private final OtpDeliveryGateway delivery;
    private final SessionTokenService sessions;
    private final Duration challengeLifetime;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public AuthService(CustomerRepository customers, StaffRepository staff, AuthAccountRepository accounts,
                       SignInPinRepository pins,
                       WalletRepository wallets,
                       OtpChallengeRepository challenges, OtpDeliveryGateway delivery,
                       SessionTokenService sessions,
                       @Value("${ayan.auth.otp.challenge-lifetime:PT5M}") Duration challengeLifetime,
                       @Value("${ayan.auth.otp.max-attempts:5}") int maxAttempts) {
        this.customers = customers;
        this.staff = staff;
        this.accounts = accounts;
        this.pins = pins;
        this.wallets = wallets;
        this.challenges = challenges;
        this.delivery = delivery;
        this.sessions = sessions;
        if (challengeLifetime == null || challengeLifetime.isNegative() || challengeLifetime.isZero() || challengeLifetime.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("OTP challenge lifetime must be between one minute and 30 minutes");
        }
        if (maxAttempts < 3 || maxAttempts > 10) throw new IllegalArgumentException("OTP max attempts must be between 3 and 10");
        this.challengeLifetime = challengeLifetime;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public OtpRequestResult requestOtp(UUID salonId, String phone, String requestIp) {
        String canonical = PhoneIdentity.canonicalPakistani(phone);
        String phoneHash = PhoneIdentity.sha256(canonical);
        Instant since = Instant.now().minus(Duration.ofMinutes(10));
        if (challenges.countBySalonIdAndPhoneHashAndCreatedAtGreaterThanEqual(salonId, phoneHash, since) >= 3) {
            throw new RateLimitException("Too many OTP requests; try again later");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge(salonId, phoneHash, sessions.hashCode(code),
                Instant.now().plus(challengeLifetime), maxAttempts, requestIp == null ? null : PhoneIdentity.sha256(requestIp));
        challenges.save(challenge);
        try {
            delivery.send(canonical, code);
        } catch (RuntimeException deliveryFailure) {
            challenges.delete(challenge);
            throw deliveryFailure;
        }
        // Do not reveal whether a phone belongs to an account. The challenge id is
        // safe to return and is required for the subsequent verification request.
        return new OtpRequestResult(challenge.getId(), challenge.getExpiresAt(),
                "If this number is registered, a verification code has been sent.");
    }

    @Transactional(noRollbackFor = {
            AuthService.AuthorizationException.class,
            AuthService.UnknownAccountException.class,
            AuthService.ConflictException.class,
            AuthService.RegistrationPhoneRequiredException.class,
            ActorContext.AuthorizationException.class
    })
    public SessionTokenService.IssuedSession verifyOtp(UUID salonId, UUID challengeId, String code, String userAgent) {
        OtpChallenge challenge = consumeChallenge(salonId, challengeId, code);
        Identity identity = findIdentity(salonId, challenge.getPhoneHash());
        if (identity == null) throw new UnknownAccountException("No active salon account was found for this number");
        return sessions.issue(salonId, identity.actorId(), identity.role(), identity.permissions(), userAgent);
    }

    @Transactional(noRollbackFor = {
            AuthService.AuthorizationException.class,
            AuthService.UnknownAccountException.class,
            AuthService.ConflictException.class,
            AuthService.RegistrationPhoneRequiredException.class,
            ActorContext.AuthorizationException.class
    })
    public SessionTokenService.IssuedSession registerCustomer(UUID salonId, UUID challengeId, String code,
                                                               String name, boolean marketingConsent, String userAgent) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Customer name is required");
        OtpChallenge challenge = consumeChallenge(salonId, challengeId, code);
        String phoneHash = challenge.getPhoneHash();
        if (customers.findBySalonIdAndPhoneHash(salonId, phoneHash).isPresent()) {
            throw new ConflictException("A customer account already exists for this number");
        }
        // The verified challenge stores only a digest. The canonical number is
        // intentionally not recoverable from it, so registration accepts the
        // phone again and verifies that it hashes to the same challenge identity.
        throw new RegistrationPhoneRequiredException("Submit the canonical phone number with the verified challenge");
    }

    /** Registration variant that keeps the raw phone out of persisted challenge rows. */
    @Transactional(noRollbackFor = {
            AuthService.AuthorizationException.class,
            AuthService.UnknownAccountException.class,
            AuthService.ConflictException.class,
            AuthService.RegistrationPhoneRequiredException.class,
            ActorContext.AuthorizationException.class
    })
    public SessionTokenService.IssuedSession registerCustomer(UUID salonId, UUID challengeId, String code,
                                                               String phone, String name, boolean marketingConsent,
                                                               String userAgent) {
        return registerCustomer(salonId, challengeId, code, phone, name, marketingConsent, null, userAgent);
    }

    /**
     * Registration with an optional sign-in PIN. A customer who later signs in
     * with the PIN never needs the SMS provider, which is what makes the
     * laptop-only pilot usable without an OTP account.
     */
    @Transactional(noRollbackFor = {
            AuthService.AuthorizationException.class,
            AuthService.UnknownAccountException.class,
            AuthService.ConflictException.class,
            AuthService.RegistrationPhoneRequiredException.class,
            ActorContext.AuthorizationException.class
    })
    public SessionTokenService.IssuedSession registerCustomer(UUID salonId, UUID challengeId, String code,
                                                               String phone, String name, boolean marketingConsent,
                                                               String pin, String userAgent) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Customer name is required");
        if (pin != null && !pin.isBlank() && !PinHasher.validPin(pin)) {
            throw new IllegalArgumentException("Choose a 4 to 6 digit PIN");
        }
        String canonical = PhoneIdentity.canonicalPakistani(phone);
        OtpChallenge challenge = consumeChallenge(salonId, challengeId, code);
        if (!PhoneIdentity.sha256(canonical).equals(challenge.getPhoneHash())) {
            throw new ActorContext.AuthorizationException("Phone does not match the OTP challenge");
        }
        if (customers.findBySalonIdAndPhone(salonId, canonical).isPresent()
                || customers.findBySalonIdAndPhoneHash(salonId, challenge.getPhoneHash()).isPresent()) {
            throw new ConflictException("A customer account already exists for this number");
        }
        if (accounts.findBySalonIdAndPhoneHashAndStatus(salonId, challenge.getPhoneHash(), AccountStatus.ACTIVE).isPresent()
                || hasActiveStaffIdentity(salonId, challenge.getPhoneHash())) {
            throw new ConflictException("This number is reserved for a salon team account");
        }
        Customer customer = customers.save(new Customer(salonId, name.trim(), canonical, challenge.getPhoneHash()));
        customer.verifyPhone();
        customer.setMarketingConsent(marketingConsent);
        wallets.save(new Wallet(salonId, customer.getId()));
        if (pin != null && !pin.isBlank()) assignPin(salonId, customer.getId(), pin);
        return sessions.issue(salonId, customer.getId(), ActorRole.CUSTOMER, customerPermissions(), userAgent);
    }

    /**
     * PIN sign-in for any verified salon account (customer, owner or staff).
     * The stored digest is compared in constant time and repeated failures lock
     * the account for a short window.
     */
    @Transactional(noRollbackFor = {
            AuthService.AuthorizationException.class,
            AuthService.UnknownAccountException.class,
            AuthService.ConflictException.class,
            AuthService.PinNotConfiguredException.class,
            ActorContext.AuthorizationException.class
    })
    public SessionTokenService.IssuedSession loginWithPin(UUID salonId, String phone, String pin, String userAgent) {
        if (pin == null || !pin.trim().matches("\\d{4,6}")) {
            throw new AuthorizationException("Enter your 4 to 6 digit PIN");
        }
        String canonical = PhoneIdentity.canonicalPakistani(phone);
        String phoneHash = PhoneIdentity.sha256(canonical);
        Identity identity = findIdentity(salonId, phoneHash);
        if (identity == null) throw new UnknownAccountException("No active salon account was found for this number");
        SignInPin record = pins.lockBySalonIdAndActorId(salonId, identity.actorId())
                .orElseThrow(() -> new PinNotConfiguredException(
                        "No sign-in PIN is set for this number. Use the verification code instead."));
        Instant now = Instant.now();
        if (record.isLocked(now)) {
            throw new AuthorizationException("Too many incorrect PIN attempts. Use the verification code or try again in a few minutes.");
        }
        if (!PinHasher.matches(pin, record.getPinSalt(), record.getIterations(), record.getPinHash())) {
            record.registerFailure(MAX_PIN_ATTEMPTS, PIN_LOCK);
            pins.save(record);
            throw new AuthorizationException("Incorrect PIN");
        }
        record.registerSuccess();
        pins.save(record);
        return sessions.issue(salonId, identity.actorId(), identity.role(), identity.permissions(), userAgent);
    }

    /**
     * Sets or changes the PIN for the signed-in account. Changing an existing
     * PIN requires the current one; a first-time PIN only needs the session.
     */
    @Transactional(noRollbackFor = {
            AuthService.AuthorizationException.class,
            AuthService.UnknownAccountException.class,
            ActorContext.AuthorizationException.class
    })
    public void setActorPin(UUID salonId, UUID actorId, String currentPin, String newPin) {
        if (!PinHasher.validPin(newPin)) throw new IllegalArgumentException("Choose a 4 to 6 digit PIN");
        java.util.Optional<SignInPin> existing = pins.lockBySalonIdAndActorId(salonId, actorId);
        if (existing.isEmpty()) {
            assignPin(salonId, actorId, newPin);
            return;
        }
        SignInPin record = existing.get();
        if (record.isLocked(Instant.now())) {
            throw new AuthorizationException("Too many incorrect PIN attempts. Try again in a few minutes.");
        }
        if (!PinHasher.matches(currentPin, record.getPinSalt(), record.getIterations(), record.getPinHash())) {
            record.registerFailure(MAX_PIN_ATTEMPTS, PIN_LOCK);
            pins.save(record);
            throw new AuthorizationException("Current PIN is incorrect");
        }
        String salt = PinHasher.newSalt();
        record.replace(PinHasher.hash(newPin, salt, PinHasher.DEFAULT_ITERATIONS), salt, PinHasher.DEFAULT_ITERATIONS);
        record.registerSuccess();
        pins.save(record);
    }

    /**
     * Makes the configured PIN the account's PIN, creating it on first run and
     * replacing it only when the configured value changed. Used by the
     * first-owner bootstrap so restarting the laptop server never resets it.
     */
    @Transactional
    public void ensureActorPin(UUID salonId, UUID actorId, String pin) {
        if (!PinHasher.validPin(pin)) throw new IllegalArgumentException("Choose a 4 to 6 digit PIN");
        java.util.Optional<SignInPin> existing = pins.findBySalonIdAndActorId(salonId, actorId);
        if (existing.isPresent()) {
            SignInPin record = existing.get();
            if (PinHasher.matches(pin, record.getPinSalt(), record.getIterations(), record.getPinHash())) return;
            String salt = PinHasher.newSalt();
            record.replace(PinHasher.hash(pin, salt, PinHasher.DEFAULT_ITERATIONS), salt, PinHasher.DEFAULT_ITERATIONS);
            record.registerSuccess();
            pins.save(record);
            return;
        }
        assignPin(salonId, actorId, pin);
    }

    private void assignPin(UUID salonId, UUID actorId, String pin) {
        if (!PinHasher.validPin(pin)) throw new IllegalArgumentException("Choose a 4 to 6 digit PIN");
        String salt = PinHasher.newSalt();
        pins.save(new SignInPin(salonId, actorId,
                PinHasher.hash(pin, salt, PinHasher.DEFAULT_ITERATIONS), salt, PinHasher.DEFAULT_ITERATIONS));
    }

    @Transactional
    public void revoke(String bearerToken) { sessions.revoke(bearerToken); }

    private OtpChallenge consumeChallenge(UUID salonId, UUID id, String code) {
        if (id == null || code == null || !code.trim().matches("\\d{6}")) throw new IllegalArgumentException("A six-digit OTP is required");
        OtpChallenge challenge = challenges.lockBySalonIdAndId(salonId, id)
                .orElseThrow(() -> new AuthorizationException("OTP challenge not found"));
        if (!challenge.usable(Instant.now())) throw new AuthorizationException("OTP expired or already used");
        if (!sessions.hashCode(code.trim()).equals(challenge.getCodeHash())) {
            challenge.countFailedAttempt();
            throw new AuthorizationException("Invalid OTP");
        }
        challenge.consume();
        return challenge;
    }

    private Identity findIdentity(UUID salonId, String phoneHash) {
        Customer customer = customers.findBySalonIdAndPhoneHash(salonId, phoneHash)
                .filter(value -> value.getStatus() == AccountStatus.ACTIVE)
                .orElse(null);
        AuthAccount account = accounts.findBySalonIdAndPhoneHashAndStatus(salonId, phoneHash, AccountStatus.ACTIVE)
                .orElse(null);
        java.util.List<Staff> staffMatches = activeStaffIdentities(salonId, phoneHash);

        int identityCount = (customer == null ? 0 : 1) + (account == null ? 0 : 1) + staffMatches.size();
        if (identityCount > 1) {
            // Never resolve an ambiguous number by privilege or query order.
            throw new ConflictException("Multiple active salon accounts share this phone number");
        }
        if (customer != null) return new Identity(customer.getId(), ActorRole.CUSTOMER, customerPermissions());
        if (account != null) return new Identity(account.getId(), account.getRole(), parsePermissions(account.getPermissionsJson()));
        if (!staffMatches.isEmpty()) {
            Staff value = staffMatches.get(0);
            return new Identity(value.getId(), value.getRole(), parsePermissions(value.getPermissionsJson()));
        }
        return null;
    }

    private boolean hasActiveStaffIdentity(UUID salonId, String phoneHash) {
        return !activeStaffIdentities(salonId, phoneHash).isEmpty();
    }

    private java.util.List<Staff> activeStaffIdentities(UUID salonId, String phoneHash) {
        java.util.List<Staff> active = staff.findBySalonIdAndActiveTrue(salonId);
        if (active == null || active.isEmpty()) return java.util.List.of();
        return active.stream()
                .filter(value -> value.getPhone() != null
                        && phoneHash.equals(PhoneIdentity.sha256(safeCanonical(value.getPhone()))))
                .toList();
    }

    private static String safeCanonical(String phone) {
        try { return PhoneIdentity.canonicalPakistani(phone); } catch (RuntimeException ignored) { return ""; }
    }

    private static Set<String> customerPermissions() {
        return Set.of("submit_deposit", "request_withdrawal", "create_booking", "create_referral");
    }

    private static Set<String> parsePermissions(String json) {
        if (json == null || json.isBlank()) return Set.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"([^\\\"]{1,80})\\\"").matcher(json);
        while (matcher.find()) result.add(matcher.group(1));
        return Set.copyOf(result);
    }

    private record Identity(UUID actorId, ActorRole role, Set<String> permissions) {}
    public record OtpRequestResult(UUID challengeId, Instant expiresAt, String message) {}
    public static class RateLimitException extends RuntimeException { public RateLimitException(String message) { super(message); } }
    public static class UnknownAccountException extends RuntimeException { public UnknownAccountException(String message) { super(message); } }
    public static class ConflictException extends RuntimeException { public ConflictException(String message) { super(message); } }
    public static class AuthorizationException extends RuntimeException { public AuthorizationException(String message) { super(message); } }
    public static class RegistrationPhoneRequiredException extends RuntimeException { public RegistrationPhoneRequiredException(String message) { super(message); } }
    /** The number exists but has no PIN yet, so the client should fall back to the verification code. */
    public static class PinNotConfiguredException extends RuntimeException { public PinNotConfiguredException(String message) { super(message); } }
}
