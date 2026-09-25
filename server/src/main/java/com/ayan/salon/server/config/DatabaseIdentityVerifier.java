package com.ayan.salon.server.config;

import com.ayan.salon.server.service.ActorContext;
import com.ayan.salon.server.service.SessionTokenService;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Production verifier backed by revocable, database-stored opaque sessions. */
@Component
@Primary
@Profile("!dev & !test")
public class DatabaseIdentityVerifier implements IdentityVerifier {
    private final SessionTokenService sessions;
    public DatabaseIdentityVerifier(SessionTokenService sessions) { this.sessions = sessions; }
    @Override public ActorContext verify(String bearerToken) { return sessions.verify(bearerToken); }
}
