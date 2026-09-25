package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authenticated identity passed into application services. Implementations must
 * be backed by a verified session/token; the mobile client must never be allowed to
 * manufacture this object or choose its role.
 */
public record ActorContext(UUID actorId, UUID salonId, ActorRole role, Set<String> permissions) {
    public boolean has(String permission) { return role == ActorRole.OWNER || permissions != null && permissions.contains(permission); }
    public void requireSalon(UUID expectedSalonId) { if (!expectedSalonId.equals(salonId)) throw new AuthorizationException("Cross-salon access denied"); }
    public void require(String permission) { if (!has(permission)) throw new AuthorizationException("Permission required: " + permission); }
    public static class AuthorizationException extends RuntimeException { public AuthorizationException(String message) { super(message); } }
}
