package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.AuthSession;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.repository.AuthSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/** Opaque bearer sessions. Only a SHA-256 token digest is stored in PostgreSQL. */
@Service
public class SessionTokenService {
    private final AuthSessionRepository sessions;
    private final byte[] secret;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public SessionTokenService(AuthSessionRepository sessions,
                               @Value("${ayan.auth.session-secret:}") String configuredSecret,
                               @Value("${ayan.auth.session-lifetime:PT12H}") Duration lifetime) {
        this.sessions = sessions;
        String source = configuredSecret == null ? "" : configuredSecret.trim();
        if (source.isBlank()) {
            byte[] ephemeral = new byte[32];
            random.nextBytes(ephemeral);
            this.secret = ephemeral;
            org.slf4j.LoggerFactory.getLogger(SessionTokenService.class)
                    .warn("AYAN_AUTH_SESSION_SECRET is not configured; sessions will be invalid after restart");
        } else {
            this.secret = source.getBytes(StandardCharsets.UTF_8);
        }
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Session lifetime must be between one minute and 30 days");
        }
        this.lifetime = lifetime;
    }

    @Transactional
    public IssuedSession issue(UUID salonId, UUID actorId, ActorRole role, Set<String> permissions, String userAgent) {
        String token = randomToken();
        Instant expires = Instant.now().plus(lifetime);
        String permissionJson = permissions == null || permissions.isEmpty()
                ? "[]" : permissions.stream().sorted().map(value -> "\"" + value.replace("\"", "") + "\"").reduce((a, b) -> a + "," + b).map(value -> "[" + value + "]").orElse("[]");
        sessions.save(new AuthSession(hashToken(token), salonId, actorId, role, permissionJson, expires, trimUserAgent(userAgent)));
        return new IssuedSession(token, expires, salonId, actorId, role);
    }

    @Transactional
    public ActorContext verify(String token) {
        if (token == null || token.isBlank() || token.length() > 512) throw new ActorContext.AuthorizationException("Invalid session token");
        AuthSession session = sessions.lockByTokenHash(hashToken(token)).orElseThrow(() -> new ActorContext.AuthorizationException("Session not found"));
        Instant now = Instant.now();
        if (!session.activeAt(now)) throw new ActorContext.AuthorizationException("Session expired or revoked");
        session.touch(now);
        return new ActorContext(session.getActorId(), session.getSalonId(), session.getRole(), parsePermissions(session.getPermissionsJson()));
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        sessions.lockByTokenHash(hashToken(token)).ifPresent(value -> value.revoke(Instant.now()));
    }

    public String hashCode(String code) {
        if (code == null) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash OTP", error);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception error) { throw new IllegalStateException("Unable to hash session token", error); }
    }

    private static Set<String> parsePermissions(String json) {
        if (json == null || json.isBlank()) return Set.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"([^\\\"]{1,80})\\\"").matcher(json);
        while (matcher.find()) result.add(matcher.group(1));
        return Set.copyOf(result);
    }

    private static String trimUserAgent(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    public record IssuedSession(String token, Instant expiresAt, UUID salonId, UUID actorId, ActorRole role) {}
}
