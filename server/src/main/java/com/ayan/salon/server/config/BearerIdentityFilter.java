package com.ayan.salon.server.config;

import com.ayan.salon.server.service.ActorContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class BearerIdentityFilter extends OncePerRequestFilter {
    private final IdentityVerifier verifier;
    public BearerIdentityFilter(IdentityVerifier verifier) { this.verifier = verifier; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                ActorContext actor = verifier.verify(authorization.substring(7));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(actor.actorId().toString(), null, List.of());
                authentication.setDetails(actor); SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException invalid) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication token"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
