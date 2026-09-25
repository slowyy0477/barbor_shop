package com.ayan.salon.server.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves identity established by an authentication provider; it never trusts role fields from JSON/mobile input. */
@Component
public class AuthenticatedActorResolver {
    public ActorContext require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getDetails() instanceof ActorContext actor)) {
            throw new ActorContext.AuthorizationException("Authenticated salon session required");
        }
        return actor;
    }
}
