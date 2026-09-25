package com.ayan.salon.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS is explicit and environment-controlled; credentials are never wildcarded. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String allowedOrigins;
    public WebConfig(@Value("${ayan.web.allowed-origins:http://localhost:4173}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins;
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key", "X-Requested-With")
                .exposedHeaders("Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
