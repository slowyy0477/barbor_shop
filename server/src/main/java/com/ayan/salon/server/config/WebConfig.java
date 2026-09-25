package com.ayan.salon.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the token-authenticated API.
 *
 * <p>This endpoint authenticates with a bearer token and never with a cookie
 * ({@code allowCredentials(false)}), so the origin list is not a security
 * boundary: a hostile page that called it would still have no session. It is,
 * however, a reliability boundary that used to break the installed app.</p>
 *
 * <p>The Android shell loads its bundled copy from
 * {@code file:///android_asset/index.html}, and a {@code file://} page sends
 * {@code Origin: file://}. Spring answered the preflight with
 * {@code 403 Invalid CORS request} and an empty body, which the app could only
 * show as "Request Failed (403)". Any unknown origin behaved the same way, and
 * the free tunnel mints a brand new hostname on every restart. The default is
 * therefore every origin; {@code AYAN_WEB_ALLOWED_ORIGINS} can still narrow it
 * to a comma separated list when the salon moves to a fixed domain.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    /** Device key the app sends so the server can enforce one account per phone. */
    static final String DEVICE_HEADER = "X-Device-Id";

    private final String allowedOrigins;

    public WebConfig(@Value("${ayan.web.allowed-origins:*}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
        CorsRegistration registration = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key",
                        "X-Requested-With", DEVICE_HEADER)
                .exposedHeaders("Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
        if (origins.length == 0) {
            // An explicitly blank setting must not silently block every phone.
            registration.allowedOriginPatterns("*");
        } else if (java.util.Arrays.asList(origins).contains("*")) {
            registration.allowedOrigins("*");
        } else {
            registration.allowedOrigins(origins);
        }
    }
}
