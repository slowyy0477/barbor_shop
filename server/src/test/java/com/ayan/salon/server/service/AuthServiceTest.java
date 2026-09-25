package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.AuthSession;
import com.ayan.salon.server.domain.AuthAccount;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.SignInPin;
import com.ayan.salon.server.domain.SignupGuard;
import com.ayan.salon.server.domain.OtpChallenge;
import com.ayan.salon.server.domain.Staff;
import com.ayan.salon.server.domain.repository.AuthAccountRepository;
import com.ayan.salon.server.domain.repository.AuthSessionRepository;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.SignInPinRepository;
import com.ayan.salon.server.domain.repository.OtpChallengeRepository;
import com.ayan.salon.server.domain.repository.StaffRepository;
import com.ayan.salon.server.domain.repository.WalletRepository;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final String phone = "03001234567";

    @Mock CustomerRepository customers;
    @Mock StaffRepository staff;
    @Mock AuthAccountRepository accounts;
    @Mock SignInPinRepository pins;
    @Mock com.ayan.salon.server.domain.repository.SignupGuardRepository signupGuards;
    @Mock WalletRepository wallets;
    @Mock OtpChallengeRepository challenges;
    @Mock OtpDeliveryGateway delivery;
    @Mock AuthSessionRepository authSessions;

    private SessionTokenService sessions;
    private AuthService service;

    @BeforeEach
    void setUp() {
        sessions = new SessionTokenService(authSessions, "unit-test-secret", Duration.ofHours(1));
        service = new AuthService(customers, staff, accounts, pins, signupGuards, wallets, challenges, delivery, sessions,
                Duration.ofMinutes(5), 5, 3, 1);
        lenient().when(challenges.countBySalonIdAndPhoneHashAndCreatedAtGreaterThanEqual(eq(salon), anyString(), any(Instant.class)))
                .thenReturn(0L);
        lenient().doAnswer(invocation -> invocation.getArgument(0)).when(challenges).save(any(OtpChallenge.class));
        lenient().doAnswer(invocation -> invocation.getArgument(0)).when(authSessions).save(any(AuthSession.class));
        lenient().doAnswer(invocation -> invocation.getArgument(0)).when(pins).save(any(SignInPin.class));
    }

    /** Builds a stored PIN row the way registration would. */
    private SignInPin storedPin(Customer customer, String pin) {
        String salt = PinHasher.newSalt();
        return new SignInPin(salon, customer.getId(), PinHasher.hash(pin, salt, PinHasher.DEFAULT_ITERATIONS),
                salt, PinHasher.DEFAULT_ITERATIONS);
    }

    @Test
    void pinSignInVerifiesTheDigestAndIssuesACustomerSession() {
        Customer customer = new Customer(salon, "Ayan Customer", phone, PhoneIdentity.sha256(phone));
        SignInPin record = storedPin(customer, "4821");
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone))).thenReturn(Optional.of(customer));
        when(pins.lockBySalonIdAndActorId(salon, customer.getId())).thenReturn(Optional.of(record));

        SessionTokenService.IssuedSession issued = service.loginWithPin(salon, phone, "4821", "test");

        assertEquals(customer.getId(), issued.actorId());
        assertEquals(ActorRole.CUSTOMER, issued.role());
        assertEquals(0, record.getAttemptCount());
    }

    @Test
    void repeatedWrongPinsLockTheAccountInsteadOfGuessing() {
        Customer customer = new Customer(salon, "Ayan Customer", phone, PhoneIdentity.sha256(phone));
        SignInPin record = storedPin(customer, "4821");
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone))).thenReturn(Optional.of(customer));
        when(pins.lockBySalonIdAndActorId(salon, customer.getId())).thenReturn(Optional.of(record));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(AuthService.AuthorizationException.class,
                    () -> service.loginWithPin(salon, phone, "0000", "test"));
        }

        assertTrue(record.isLocked(Instant.now()), "The account must lock after the attempt allowance");
        assertThrows(AuthService.AuthorizationException.class,
                () -> service.loginWithPin(salon, phone, "4821", "test"));
    }

    @Test
    void pinSignInWithoutAStoredPinAsksForTheVerificationCode() {
        Customer customer = new Customer(salon, "Ayan Customer", phone, PhoneIdentity.sha256(phone));
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone))).thenReturn(Optional.of(customer));
        when(pins.lockBySalonIdAndActorId(salon, customer.getId())).thenReturn(Optional.empty());

        assertThrows(AuthService.PinNotConfiguredException.class,
                () -> service.loginWithPin(salon, phone, "4821", "test"));
    }

    @Test
    void changingAPinRequiresTheCurrentOne() {
        Customer customer = new Customer(salon, "Ayan Customer", phone, PhoneIdentity.sha256(phone));
        SignInPin record = storedPin(customer, "4821");
        when(pins.lockBySalonIdAndActorId(salon, customer.getId())).thenReturn(Optional.of(record));

        // A short keypad PIN is no longer a valid part of the app, so the new
        // secret must be a 10 to 20 character password.
        assertThrows(AuthService.AuthorizationException.class,
                () -> service.setActorPin(salon, customer.getId(), "WrongPass123", "NewSecret2026"));
        assertThrows(IllegalArgumentException.class,
                () -> service.setActorPin(salon, customer.getId(), "4821", "9090"));

        service.setActorPin(salon, customer.getId(), "4821", "NewSecret2026");

        assertTrue(PinHasher.matches("NewSecret2026", record.getPinSalt(), record.getIterations(), record.getPinHash()),
                "The stored digest must change to the new password");
    }

    @Test
    void passwordSignupCreatesTheAccountWithoutAnySmsStep() {
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wallets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionTokenService.IssuedSession session = service.registerCustomerWithPassword(
                salon, phone, "Ali Raza", true, "AliRaza2026x", "device-1", "203.0.113.7", "unit-test");

        assertEquals("CUSTOMER", session.role().name());
        verify(signupGuards).save(any(SignupGuard.class));
        verify(challenges, org.mockito.Mockito.never()).save(any(OtpChallenge.class));
    }

    @Test
    void passwordSignupRejectsASecondAccountFromTheSameDevice() {
        SignupGuard claimed = new SignupGuard(salon, "hashed", "hashed-ip", UUID.randomUUID());
        when(signupGuards.findFirstBySalonIdAndDeviceHash(eq(salon), anyString()))
                .thenReturn(Optional.of(claimed));

        assertThrows(AuthService.ConflictException.class, () -> service.registerCustomerWithPassword(
                salon, phone, "Second Person", false, "Second2026xy", "device-1", "203.0.113.7", "unit-test"));
    }

    @Test
    void passwordSignupRejectsMoreAccountsThanTheNetworkAllows() {
        when(signupGuards.countBySalonIdAndIpHash(eq(salon), anyString())).thenReturn(3L);

        assertThrows(AuthService.ConflictException.class, () -> service.registerCustomerWithPassword(
                salon, phone, "Fourth Person", false, "Fourth2026xy", "device-9", "203.0.113.7", "unit-test"));
    }

    @Test
    void passwordSignupStillRefusesAShortPin() {
        assertThrows(IllegalArgumentException.class, () -> service.registerCustomerWithPassword(
                salon, phone, "Short Pin", false, "1234", "device-2", "203.0.113.7", "unit-test"));
    }

    @Test
    void otpRequestStoresOnlyAChallengeHashAndDeliversCode() {
        ArgumentCaptor<OtpChallenge> challenge = ArgumentCaptor.forClass(OtpChallenge.class);
        ArgumentCaptor<String> deliveredCode = ArgumentCaptor.forClass(String.class);

        AuthService.OtpRequestResult result = service.requestOtp(salon, phone, "127.0.0.1");

        verify(challenges).save(challenge.capture());
        verify(delivery).send(eq(phone), deliveredCode.capture());
        assertEquals(result.challengeId(), challenge.getValue().getId());
        assertEquals(6, deliveredCode.getValue().length());
        assertEquals(sessions.hashCode(deliveredCode.getValue()), challenge.getValue().getCodeHash());
        assertTrue(challenge.getValue().getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void wrongOtpIncrementsAttemptsAndLocksTheChallenge() {
        OtpChallenge challenge = new OtpChallenge(salon, PhoneIdentity.sha256(phone),
                sessions.hashCode("123456"), Instant.now().plusSeconds(300), 3, null);
        when(challenges.lockBySalonIdAndId(salon, challenge.getId())).thenReturn(Optional.of(challenge));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThrows(AuthService.AuthorizationException.class,
                    () -> service.verifyOtp(salon, challenge.getId(), "000000", "test"));
        }

        assertEquals(3, challenge.getAttemptCount());
        assertThrows(AuthService.AuthorizationException.class,
                () -> service.verifyOtp(salon, challenge.getId(), "123456", "test"));
    }

    @Test
    void correctOtpIsSingleUseAndIssuesACustomerSession() {
        Customer customer = new Customer(salon, "Ayan Customer", phone, PhoneIdentity.sha256(phone));
        OtpChallenge challenge = new OtpChallenge(salon, PhoneIdentity.sha256(phone),
                sessions.hashCode("123456"), Instant.now().plusSeconds(300), 5, null);
        when(challenges.lockBySalonIdAndId(salon, challenge.getId())).thenReturn(Optional.of(challenge));
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone)))
                .thenReturn(Optional.of(customer));

        SessionTokenService.IssuedSession issued = service.verifyOtp(salon, challenge.getId(), "123456", "test");

        assertEquals(customer.getId(), issued.actorId());
        assertEquals(ActorRole.CUSTOMER, issued.role());
        assertTrue(challenge.isConsumed());
        assertThrows(AuthService.AuthorizationException.class,
                () -> service.verifyOtp(salon, challenge.getId(), "123456", "test"));
    }

    @Test
    void challengeCannotBeUsedFromAnotherSalon() {
        UUID otherSalon = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(challenges.lockBySalonIdAndId(otherSalon, challengeId)).thenReturn(Optional.empty());

        assertThrows(AuthService.AuthorizationException.class,
                () -> service.verifyOtp(otherSalon, challengeId, "123456", "test"));
    }

    @Test
    void otpRejectsAmbiguousCustomerAndPrivilegedIdentityInsteadOfChoosingCustomer() {
        Customer customer = new Customer(salon, "Customer", phone, PhoneIdentity.sha256(phone));
        AuthAccount ownerAccount = new AuthAccount(salon, PhoneIdentity.sha256(phone), ActorRole.OWNER, "[]");
        OtpChallenge challenge = new OtpChallenge(salon, PhoneIdentity.sha256(phone),
                sessions.hashCode("123456"), Instant.now().plusSeconds(300), 5, null);
        when(challenges.lockBySalonIdAndId(salon, challenge.getId())).thenReturn(Optional.of(challenge));
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone)))
                .thenReturn(Optional.of(customer));
        when(accounts.findBySalonIdAndPhoneHashAndStatus(salon, PhoneIdentity.sha256(phone),
                com.ayan.salon.server.domain.DomainTypes.AccountStatus.ACTIVE))
                .thenReturn(Optional.of(ownerAccount));

        assertThrows(AuthService.ConflictException.class,
                () -> service.verifyOtp(salon, challenge.getId(), "123456", "test"));
        assertTrue(challenge.isConsumed(), "An ambiguous challenge must not remain reusable");
    }

    @Test
    void customerRegistrationCannotClaimAStaffPhone() {
        Staff staffMember = new Staff(salon, "Barber", phone, ActorRole.BARBER);
        OtpChallenge challenge = new OtpChallenge(salon, PhoneIdentity.sha256(phone),
                sessions.hashCode("123456"), Instant.now().plusSeconds(300), 5, null);
        when(challenges.lockBySalonIdAndId(salon, challenge.getId())).thenReturn(Optional.of(challenge));
        when(customers.findBySalonIdAndPhoneHash(salon, PhoneIdentity.sha256(phone)))
                .thenReturn(Optional.empty());
        when(staff.findBySalonIdAndActiveTrue(salon)).thenReturn(java.util.List.of(staffMember));

        assertThrows(AuthService.ConflictException.class,
                () -> service.registerCustomer(salon, challenge.getId(), "123456", phone,
                        "New customer", false, "test"));
        assertTrue(challenge.isConsumed(), "A rejected registration challenge must not be reusable");
        verify(wallets, org.mockito.Mockito.never()).save(any());
    }
}
