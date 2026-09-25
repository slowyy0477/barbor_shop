package com.ayan.salon.server.config;

import com.ayan.salon.server.service.ActorContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Optional fail-closed fallback. Enable only with the explicit "identity-fallback" profile. */
@Component
@Profile("identity-fallback")
public class RejectingIdentityVerifier implements IdentityVerifier {
    @Override public ActorContext verify(String bearerToken) { throw new ActorContext.AuthorizationException("Production identity verifier is not configured"); }
}
