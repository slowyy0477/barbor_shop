package com.ayan.salon.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small-instance guardrail for abuse-prone endpoints. In a multi-instance
 * deployment put the same limits at the edge (or use a shared Redis limiter)
 * because an in-memory bucket is intentionally local to one process.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int generalPerMinute;
    private final int otpPerMinute;
    private final int authPerMinute;
    private final int adminPerMinute;
    private final boolean enabled;

    public RateLimitFilter(
            @Value("${ayan.rate-limit.enabled:true}") boolean enabled,
            @Value("${ayan.rate-limit.general-per-minute:120}") int generalPerMinute,
            @Value("${ayan.rate-limit.otp-per-minute:5}") int otpPerMinute,
            @Value("${ayan.rate-limit.auth-per-minute:20}") int authPerMinute,
            @Value("${ayan.rate-limit.admin-per-minute:60}") int adminPerMinute) {
        this.enabled = enabled;
        this.generalPerMinute = bounded(generalPerMinute, 10, 10000);
        this.otpPerMinute = bounded(otpPerMinute, 1, 100);
        this.authPerMinute = bounded(authPerMinute, 2, 500);
        this.adminPerMinute = bounded(adminPerMinute, 5, 1000);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/actuator/") || path.equals("/error")
                || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = limitFor(path);
        String identity = clientIdentity(request);
        String key = identity + "|" + bucketName(path);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        long now = System.nanoTime();
        if (!bucket.allow(now, limit)) {
            // Servlet's response constants do not include 429 on all supported
            // containers; keep the HTTP status explicit and portable.
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests; try again later\"}");
            return;
        }
        // Opportunistic cleanup prevents a long-lived process from retaining a
        // bucket for every transient scanner IP forever.
        if (buckets.size() > 10_000 && (now & 0x3f) == 0) buckets.entrySet().removeIf(entry -> entry.getValue().stale(now));
        chain.doFilter(request, response);
    }

    private int limitFor(String path) {
        if (path.startsWith("/api/auth/otp")) return otpPerMinute;
        if (path.startsWith("/api/auth")) return authPerMinute;
        if (path.contains("/admin") || path.contains("/settings") || path.contains("/reports")) return adminPerMinute;
        return generalPerMinute;
    }

    private static String bucketName(String path) {
        if (path.startsWith("/api/auth/otp")) return "otp";
        if (path.startsWith("/api/auth")) return "auth";
        if (path.contains("/admin") || path.contains("/settings") || path.contains("/reports")) return "admin";
        return "general";
    }

    private static String clientIdentity(HttpServletRequest request) {
        String value = request.getRemoteAddr();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static int bounded(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class Bucket {
        private final ArrayDeque<Long> hits = new ArrayDeque<>();
        synchronized boolean allow(long now, int limit) {
            long cutoff = now - Duration.ofMinutes(1).toNanos();
            while (!hits.isEmpty() && hits.peekFirst() <= cutoff) hits.removeFirst();
            if (hits.size() >= limit) return false;
            hits.addLast(now);
            return true;
        }
        synchronized boolean stale(long now) {
            return hits.isEmpty() || hits.peekLast() < now - Duration.ofMinutes(5).toNanos();
        }
    }
}
