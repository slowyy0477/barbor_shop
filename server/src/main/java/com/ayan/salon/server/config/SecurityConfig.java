package com.ayan.salon.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, BearerIdentityFilter identityFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentTypeOptions(content -> {})
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31536000)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/info", "/error", "/api/auth/**", "/api/public/**").permitAll()
                        // The phone UI is served from this same origin
                        // (ops/sync-server-web.ps1 copies it into the jar), so the
                        // shell, its assets and the PWA manifest must be readable
                        // before anyone has a session. Every /api/** route keeps
                        // its own identity check; nothing here widens API access.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/", "/index.html", "/favicon.ico", "/manifest.webmanifest", "/service-worker.js",
                                "/*.js", "/*.css", "/*.svg", "/*.png", "/*.ico", "/*.webmanifest", "/icons/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(identityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
