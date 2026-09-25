package com.ayan.salon.server.config;

import com.ayan.salon.server.service.ActorContext;

/** Integrate this with the production OTP/session/JWT issuer. */
public interface IdentityVerifier {
    ActorContext verify(String bearerToken);
}
