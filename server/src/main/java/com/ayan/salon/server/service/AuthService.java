package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.AuthAccount;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.OtpChallenge;
import com.ayan.salon.server.domain.SignInPin;
import com.ayan.salon.server.domain.SignupGuard;
import com.ayan.salon.server.domain.Staff;
import com.ayan.salon.server.domain.DomainTypes.AccountStatus;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.repository.AuthAccountRepository;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.OtpChallengeRepository;
import com.ayan.salon.server.domain.repository.StaffRepository;
import com.ayan.salon.server.domain.repository.SignInPinRepository;
import com.ayan.salon.server.domain.repository.SignupGuardRepository;
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
    /** Owner accounts tried by a password-only sign in before asking for a number. */
    private static final int MAX_PASSWORD_ONLY_OWNERS = 5;
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final AuthAccountRepository accounts;
    private final SignInPinRepository pins;
    private final SignupGuardRepository signupGuards;
    private final WalletRepository wallets;
    private final OtpChallengeRepository challenges;
    private final OtpDeliveryGateway delivery;
    private final SessionTokenService sessions;
    private final Duration challengeLifetime;
    private final int maxAttempts;
    /** Self-service signup cap for one network address; 0 switches the cap off. */
    private final int maxSignupsPerIp;
    /** Self-service signup cap for one device; 0 switches the cap off. */
    private final int maxSignupsPerDevice;
    private final SecureRandom random = new SecureRandom();

    public AuthService(CustomerRepository customers, StaffRepository staff, AuthAccountRepository accounts,
                       SignInPinRepository pins,
                       SignupGuardRepository signupGuards,
                       WalletRepository wallets,
                       OtpChallengeRepository challenges, OtpDeliveryGateway delivery,
                       SessionTokenService sessions,
                       @Value("${ayan.auth.otp.challenge-lifetime:PT5M}") Duration challengeLifetime,
                       @Value("${ayan.auth.otp.max-attempts:5}") int maxAttempts,
                       @Value("${ayan.auth.signup.max-per-network:3}") int maxSignupsPerIp,
                       @Value("${ayan.auth.signup.max-per-device:1}") int maxSignupsPerDevice) {
        this.customers = customers;
        this.staff = staff;
        this.accounts = accounts;
        this.pins = pins;
        this.signupGuards = signupGuards;
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
        if (maxSignupsPerIp < 0 || maxSignupsPerIp > 1000) throw new IllegalArgumentException("Signup cap per network must be between 0 and 1000");
        if (maxSignupsPerDevice < 0 || maxSignupsPerDevice > 10) throw new IllegalArgumentException("Signup cap per device must be between 0 and 10");
        this.maxSignupsPerIp = maxSignupsPerIp;
        this.maxSignupsPerDevice = maxSignupsPerDevice;
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
     * Legacy code-verified registration kept only so an old client build keeps
     * working. The app now uses {@link #registerCustomerWithPassword}. The
     * password rule is already the 10 to 20 character one.
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
        if (pin != null && !pin.isBlank() && !PinHasher.validPassword(pin)) {
            throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);
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
        if (pin != null && !pin.isBlank()) assignPassword(salonId, customer.getId(), pin);
        return sessions.issue(salonId, customer.getId(), ActorRole.CUSTOMER, customerPermissions(), userAgent);
    }

    /**
     * Self-service signup without any SMS step. The mobile number is the
     * account name and a 10 to 20 character password is the only secret, which
     * is what the salon asked for after switching the verification codes off.
     *
     * <p>Abuse is bounded in two independent ways: one account per device and a
     * small number of accounts per internet connection. Both are configurable
     * and a value of zero switches that particular cap off. Only digests of the
     * device key and the network address are persisted.
     */
    @Transactional
    public SessionTokenService.IssuedSession registerCustomerWithPassword(UUID salonId, String phone, String name,
                                                                          boolean marketingConsent, String password,
                                                                          String deviceKey, String requestIp,
                                                                          String userAgent) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Please enter your name");
        String trimmedName = name.trim();
        if (trimmedName.length() > 120) throw new IllegalArgumentException("That name is too long");
        if (!PinHasher.validPassword(password)) throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);

        String canonical = PhoneIdentity.canonicalPakistani(phone);
        String phoneHash = PhoneIdentity.sha256(canonical);
        if (customers.findBySalonIdAndPhone(salonId, canonical).isPresent()
                || customers.findBySalonIdAndPhoneHash(salonId, phoneHash).isPresent()) {
            throw new ConflictException("An account already exists for this mobile number. Sign in with your password instead.");
        }
        if (accounts.findBySalonIdAndPhoneHashAndStatus(salonId, phoneHash, AccountStatus.ACTIVE).isPresent()
                || hasActiveStaffIdentity(salonId, phoneHash)) {
            throw new ConflictException("This number is reserved for a salon team account");
        }

        String deviceHash = deviceHash(deviceKey);
        String ipHash = networkHash(requestIp);
        if (maxSignupsPerDevice > 0 && deviceHash != null
                && signupGuards.findFirstBySalonIdAndDeviceHash(salonId, deviceHash).isPresent()) {
            throw new ConflictException("This phone already created a salon account. Sign in with that mobile number and password, "
                    + "or ask the salon owner to reset it.");
        }
        if (maxSignupsPerIp > 0 && signupGuards.countBySalonIdAndIpHash(salonId, ipHash) >= maxSignupsPerIp) {
            throw new ConflictException("This internet connection already created " + maxSignupsPerIp
                    + " accounts. Sign in to your own account, or ask the salon owner to raise the limit.");
        }

        Customer customer = customers.save(new Customer(salonId, trimmedName, canonical, phoneHash));
        customer.verifyPhone();
        customer.setMarketingConsent(marketingConsent);
        wallets.save(new Wallet(salonId, customer.getId()));
        assignPassword(salonId, customer.getId(), password);
        signupGuards.save(new SignupGuard(salonId, deviceHash, ipHash, customer.getId()));
        return sessions.issue(salonId, customer.getId(), ActorRole.CUSTOMER, customerPermissions(), userAgent);
    }

    /**
     * Password sign-in for any verified salon account (customer, owner or staff).
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
        if (!PinHasher.validSecret(pin)) {
            throw new AuthorizationException("Enter your password: 10 to 20 characters with at least one letter and one number");
        }
        String rawPhone = phone == null ? "" : phone.trim();
        if (rawPhone.replaceAll("\\D", "").isEmpty()) {
            // No SMS provider is connected on the laptop server, so the salon
            // owner must always be able to open the owner workspace with just
            // the long owner password. Short PINs still require a mobile number.
            return loginOwnerPasswordOnly(salonId, pin, userAgent);
        }
        String canonical = PhoneIdentity.canonicalPakistani(phone);
        String phoneHash = PhoneIdentity.sha256(canonical);
        Identity identity = findIdentity(salonId, phoneHash);
        if (identity == null) throw new UnknownAccountException("No active salon account was found for this number");
        SignInPin record = pins.lockBySalonIdAndActorId(salonId, identity.actorId())
                .orElseThrow(() -> new PinNotConfiguredException(
                        "No password is set for this mobile number yet. Ask the salon owner to set one."));
        Instant now = Instant.now();
        if (record.isLocked(now)) {
            throw new AuthorizationException("Too many incorrect password attempts. Please try again in a few minutes.");
        }
        if (!PinHasher.matches(pin, record.getPinSalt(), record.getIterations(), record.getPinHash())) {
            record.registerFailure(MAX_PIN_ATTEMPTS, PIN_LOCK);
            pins.save(record);
            throw new AuthorizationException("Incorrect mobile number or password");
        }
        record.registerSuccess();
        pins.save(record);
        return sessions.issue(salonId, identity.actorId(), identity.role(), identity.permissions(), userAgent);
    }

    /**
     * Mobile number plus password sign in, with the device key remembered.
     *
     * <p>A successful customer sign in also claims the device for that account
     * when the device has no signup yet. Accounts created before this release
     * therefore become "one account per phone" as soon as their owner signs in
     * once, without ever locking anybody out of an existing account.
     */
    @Transactional
    public SessionTokenService.IssuedSession loginWithPassword(UUID salonId, String phone, String password,
                                                               String deviceKey, String requestIp, String userAgent) {
        SessionTokenService.IssuedSession session = loginWithPin(salonId, phone, password, userAgent);
        if (session.role() == ActorRole.CUSTOMER) bindDevice(salonId, session.actorId(), deviceKey, requestIp);
        return session;
    }

    private void bindDevice(UUID salonId, UUID customerId, String deviceKey, String requestIp) {
        if (maxSignupsPerDevice <= 0) return;
        String hash = deviceHash(deviceKey);
        if (hash == null) return;
        if (signupGuards.findFirstBySalonIdAndDeviceHash(salonId, hash).isPresent()) return;
        signupGuards.save(new SignupGuard(salonId, hash, networkHash(requestIp), customerId));
    }

    /**
     * Owner sign in with the long password and no mobile number. Only the
     * 10-20 character password shape is accepted here: a 4-6 digit PIN alone
     * would be too easy to guess without the mobile number as a second key.
     * Wrong passwords are charged against every configured owner account, so
     * the usual five-try lockout still applies.
     */
    private SessionTokenService.IssuedSession loginOwnerPasswordOnly(UUID salonId, String password, String userAgent) {
        if (PinHasher.validPin(password)) {
            throw new AuthorizationException("Add the owner mobile number to sign in with a short PIN, or type the longer owner password.");
        }
        java.util.List<AuthAccount> owners = accounts.findBySalonIdAndRoleAndStatus(salonId, ActorRole.OWNER, AccountStatus.ACTIVE);
        if (owners == null) owners = java.util.List.of();
        if (owners.isEmpty()) {
            throw new UnknownAccountException("No owner sign-in is configured for this salon yet");
        }
        if (owners.size() > MAX_PASSWORD_ONLY_OWNERS) {
            throw new AuthorizationException("Too many owner accounts to sign in without a mobile number. Type the owner mobile number.");
        }
        Instant now = Instant.now();
        java.util.List<AuthAccount> ownersWithPin = new java.util.ArrayList<>(owners.size());
        java.util.List<SignInPin> candidates = new java.util.ArrayList<>(owners.size());
        for (AuthAccount owner : owners) {
            pins.lockBySalonIdAndActorId(salonId, owner.getId()).ifPresent(record -> {
                ownersWithPin.add(owner);
                candidates.add(record);
            });
        }
        if (candidates.isEmpty()) {
            throw new PinNotConfiguredException("No owner password is set yet. Add the owner password on the salon laptop first.");
        }
        boolean unlockedCandidate = false;
        for (int index = 0; index < candidates.size(); index++) {
            SignInPin record = candidates.get(index);
            if (record.isLocked(now)) continue;
            unlockedCandidate = true;
            if (PinHasher.matches(password, record.getPinSalt(), record.getIterations(), record.getPinHash())) {
                AuthAccount owner = ownersWithPin.get(index);
                record.registerSuccess();
                pins.save(record);
                return sessions.issue(salonId, owner.getId(), owner.getRole(),
                        parsePermissions(owner.getPermissionsJson()), userAgent);
            }
        }
        if (!unlockedCandidate) {
            throw new AuthorizationException("Too many incorrect password attempts. Try again in a few minutes.");
        }
        for (SignInPin record : candidates) {
            if (record.isLocked(now)) continue;
            record.registerFailure(MAX_PIN_ATTEMPTS, PIN_LOCK);
            pins.save(record);
        }
        throw new AuthorizationException("Incorrect owner password");
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
        if (!PinHasher.validPassword(newPin)) {
            throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);
        }
        java.util.Optional<SignInPin> existing = pins.lockBySalonIdAndActorId(salonId, actorId);
        if (existing.isEmpty()) {
            assignPassword(salonId, actorId, newPin);
            return;
        }
        SignInPin record = existing.get();
        if (record.isLocked(Instant.now())) {
            throw new AuthorizationException("Too many incorrect password attempts. Try again in a few minutes.");
        }
        if (!PinHasher.matches(currentPin, record.getPinSalt(), record.getIterations(), record.getPinHash())) {
            record.registerFailure(MAX_PIN_ATTEMPTS, PIN_LOCK);
            pins.save(record);
            throw new AuthorizationException("Your current password is incorrect");
        }
        String salt = PinHasher.newSalt();
        record.replace(PinHasher.hash(newPin.trim(), salt, PinHasher.DEFAULT_ITERATIONS), salt, PinHasher.DEFAULT_ITERATIONS);
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
        if (!PinHasher.validSecret(pin)) {
            throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);
        }
        java.util.Optional<SignInPin> existing = pins.findBySalonIdAndActorId(salonId, actorId);
        if (existing.isPresent()) {
            SignInPin record = existing.get();
            if (PinHasher.matches(pin, record.getPinSalt(), record.getIterations(), record.getPinHash())) return;
            String salt = PinHasher.newSalt();
            record.replace(PinHasher.hash(pin.trim(), salt, PinHasher.DEFAULT_ITERATIONS), salt, PinHasher.DEFAULT_ITERATIONS);
            record.registerSuccess();
            pins.save(record);
            return;
        }
        writeSecret(salonId, actorId, pin);
    }

    /**
     * Owner-driven password reset for one customer.
     *
     * <p>No SMS code is sent any more, so this is the only recovery path: the
     * customer tells the salon owner a new password at the counter and can sign
     * in with it immediately. It also clears a lockout left by five wrong
     * attempts, which is exactly what a customer who forgot the password
     * needs.</p>
     */
    @Transactional
    public void resetCustomerPassword(UUID salonId, UUID customerId, String password) {
        if (!PinHasher.validPassword(password)) throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);
        if (customers.findBySalonIdAndId(salonId, customerId).isEmpty()) {
            throw new WalletService.NotFoundException("No customer with that id exists in this salon");
        }
        java.util.Optional<SignInPin> existing = pins.lockBySalonIdAndActorId(salonId, customerId);
        if (existing.isEmpty()) {
            writeSecret(salonId, customerId, password);
            return;
        }
        SignInPin record = existing.get();
        String salt = PinHasher.newSalt();
        record.replace(PinHasher.hash(password.trim(), salt, PinHasher.DEFAULT_ITERATIONS), salt,
                PinHasher.DEFAULT_ITERATIONS);
        record.registerSuccess();
        pins.save(record);
    }

    /**
     * Writes a password for an account. Only the 10 to 20 character password
     * shape is accepted, which is the single rule the whole app now uses.
     */
    private void assignPassword(UUID salonId, UUID actorId, String password) {
        if (!PinHasher.validPassword(password)) throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);
        writeSecret(salonId, actorId, password);
    }

    /**
     * Digest writer that also tolerates the legacy 4 to 6 digit shape. It is
     * reached only by the laptop bootstrap, so an owner who set a short PIN
     * before this release can still sign in and then change it to a password.
     */
    private void writeSecret(UUID salonId, UUID actorId, String secret) {
        if (!PinHasher.validSecret(secret)) throw new IllegalArgumentException(PinHasher.PASSWORD_RULE);
        String salt = PinHasher.newSalt();
        pins.save(new SignInPin(salonId, actorId,
                PinHasher.hash(secret.trim(), salt, PinHasher.DEFAULT_ITERATIONS), salt, PinHasher.DEFAULT_ITERATIONS));
    }

    /** Salted digest of the client device key, or null when the client sent none. */
    private static String deviceHash(String deviceKey) {
        if (deviceKey == null) return null;
        String value = deviceKey.trim();
        if (value.isEmpty() || value.length() > 200) return null;
        return PhoneIdentity.sha256("device:" + value);
    }

    /** Salted digest of the requesting network address; never stored in the clear. */
    private static String networkHash(String requestIp) {
        String value = requestIp == null ? "" : requestIp.trim();
        return PhoneIdentity.sha256("network:" + (value.isEmpty() ? "unknown" : value));
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
    /** The number exists but has no password yet, so the salon owner must set one. */
    public static class PinNotConfiguredException extends RuntimeException { public PinNotConfiguredException(String message) { super(message); } }
}
