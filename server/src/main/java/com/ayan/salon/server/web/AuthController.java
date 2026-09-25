package com.ayan.salon.server.web;

import com.ayan.salon.server.service.AuthService;
import com.ayan.salon.server.service.SessionTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    /** Shared name of the device cookie, used only as a fallback for the header. */
    static final String DEVICE_COOKIE = "ayan_device";
    private final boolean trustForwardedHeaders;

    public AuthController(AuthService auth,
                          @Value("${ayan.auth.trust-forwarded-headers:true}") boolean trustForwardedHeaders) {
        this.auth = auth;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    /**
     * SMS verification codes are switched off. The salon asked for mobile
     * number plus password only, so every path that used to hand out a code now
     * refuses with a message that tells the client what to do instead.
     */
    @PostMapping("/otp/request")
    public AuthService.OtpRequestResult requestOtp(@Valid @RequestBody OtpRequest request) {
        throw new SmsDisabledException();
    }

    @PostMapping("/otp/verify")
    public SessionResponse verify(@Valid @RequestBody OtpVerifyRequest request) {
        throw new SmsDisabledException();
    }

    @PostMapping("/customer/register")
    public SessionResponse register(@Valid @RequestBody CustomerRegisterRequest request, HttpServletRequest http) {
        throw new SmsDisabledException();
    }

    /**
     * Self-service signup: mobile number, name and a 10 to 20 character
     * password. No SMS step exists any more, so the device key and the
     * requesting network are what keep one phone from creating many accounts.
     */
    @PostMapping("/customer/signup")
    public SessionResponse signup(@Valid @RequestBody CustomerSignupRequest request,
                                  HttpServletRequest http, HttpServletResponse response) {
        String deviceKey = resolveDeviceKey(request.deviceId(), http, response);
        return SessionResponse.from(auth.registerCustomerWithPassword(request.salonId(), request.phone(),
                request.name(), request.marketingConsent(), request.password(),
                deviceKey, clientIp(http), http.getHeader("User-Agent")));
    }

    /**
     * Documented name of the same signup. Both spellings are mapped because the
     * bundled WebView and the published web page can be updated independently,
     * and a stale copy must never be the reason an account cannot be created.
     */
    @PostMapping("/password/register")
    public SessionResponse registerWithPassword(@Valid @RequestBody CustomerSignupRequest request,
                                                HttpServletRequest http, HttpServletResponse response) {
        return signup(request, http, response);
    }

    /** Documented name of {@link #verifyPassword}. */
    @PostMapping("/password/login")
    public SessionResponse loginWithPassword(@Valid @RequestBody PinVerifyRequest request,
                                             HttpServletRequest http, HttpServletResponse response) {
        return verifyPassword(request, http, response);
    }

    /** Mobile number plus password sign in. */
    @PostMapping("/password/verify")
    public SessionResponse verifyPassword(@Valid @RequestBody PinVerifyRequest request,
                                          HttpServletRequest http, HttpServletResponse response) {
        String deviceKey = resolveDeviceKey(http, response);
        return SessionResponse.from(auth.loginWithPassword(request.salonId(), request.phone(), request.pin(),
                deviceKey, clientIp(http), http.getHeader("User-Agent")));
    }

    /** Kept so an already-installed APK keeps signing in while it updates. */
    @PostMapping("/pin/verify")
    public SessionResponse verifyPin(@Valid @RequestBody PinVerifyRequest request,
                                     HttpServletRequest http, HttpServletResponse response) {
        return verifyPassword(request, http, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) auth.revoke(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }

    /**
     * The phone reaches this server through a free tunnel, so the socket address
     * is always the tunnel client and every phone would share one "network".
     * The tunnel writes the real address into a forwarding header. Reading it is
     * what makes the per-network signup cap meaningful at all; the device cap in
     * AuthService is the check that does not depend on it.
     */
    private String clientIp(HttpServletRequest request) {
        // The forwarding header is only meaningful when the request arrived
        // from the tunnel client or the shop network itself. A request that
        // arrives directly from a public address must not be able to choose its
        // own value for the per-network signup cap.
        if (trustForwardedHeaders && isLocalPeer(request.getRemoteAddr())) {
            String cloudflare = trimToNull(request.getHeader("CF-Connecting-IP"));
            if (cloudflare != null) return cloudflare;
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                int comma = forwarded.indexOf(',');
                String first = trimToNull(comma < 0 ? forwarded : forwarded.substring(0, comma));
                if (first != null) return first;
            }
            String realIp = trimToNull(request.getHeader("X-Real-IP"));
            if (realIp != null) return realIp;
        }
        return request.getRemoteAddr();
    }

    /** True for the loopback and private ranges a local tunnel client uses. */
    static boolean isLocalPeer(String address) {
        if (address == null || address.isBlank()) return false;
        String value = address.trim();
        if (value.startsWith("127.") || value.startsWith("::1") || value.startsWith("0:0:0:0:0:0:0:1")) return true;
        if (value.startsWith("10.") || value.startsWith("192.168.") || value.startsWith("169.254.")) return true;
        if (value.startsWith("172.")) {
            String[] parts = value.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        // IPv6 unique local addresses (fc00::/7).
        return value.startsWith("fc") || value.startsWith("fd");
    }

    /**
     * The device key identifies one installed app. The client sends it as a
     * header; the cookie is a fallback for a browser that talks to the server
     * on its own origin. Nothing personal is stored, only a salted digest.
     */
    private static String resolveDeviceKey(HttpServletRequest request, HttpServletResponse response) {
        return resolveDeviceKey(null, request, response);
    }

    private static String resolveDeviceKey(String bodyDeviceId, HttpServletRequest request, HttpServletResponse response) {
        String header = trimToNull(bodyDeviceId);
        if (header == null) header = trimToNull(request.getHeader("X-Device-Id"));
        if (header != null && header.length() <= 200) {
            writeDeviceCookie(response, header, request.isSecure());
            return header;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (DEVICE_COOKIE.equals(cookie.getName()) && trimToNull(cookie.getValue()) != null) {
                    return cookie.getValue();
                }
            }
        }
        String generated = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        writeDeviceCookie(response, generated, request.isSecure());
        return generated;
    }

    private static void writeDeviceCookie(HttpServletResponse response, String value, boolean secure) {
        Cookie cookie = new Cookie(DEVICE_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        try {
            response.addCookie(cookie);
        } catch (IllegalStateException ignored) {
            // Some deployment paths hand this method an already-committed
            // response. The client header stays the primary device key, so a
            // missing fallback cookie never blocks a signup.
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record OtpRequest(@NotNull UUID salonId, @NotBlank String phone) {}
    public record OtpVerifyRequest(@NotNull UUID salonId, @NotNull UUID challengeId, @NotBlank String code) {}
    public record CustomerRegisterRequest(@NotNull UUID salonId, @NotNull UUID challengeId, @NotBlank String code,
                                          @NotBlank String phone, @NotBlank String name, boolean marketingConsent,
                                          String pin) {}
    public record CustomerSignupRequest(@NotNull UUID salonId, @NotBlank String phone, @NotBlank String name,
                                        boolean marketingConsent, @NotBlank String password, String deviceId) {}
    /**
     * The mobile number is optional: the salon owner can open the owner
     * workspace with the long owner password alone when no SMS provider is
     * connected. Customers always sign in with their number and password.
     */
    public record PinVerifyRequest(@NotNull UUID salonId, String phone, @NotBlank String pin) {}
    public record SessionResponse(String accessToken, Instant expiresAt, UUID salonId, UUID actorId, String role) {
        static SessionResponse from(SessionTokenService.IssuedSession value) {
            return new SessionResponse(value.token(), value.expiresAt(), value.salonId(), value.actorId(), value.role().name());
        }
    }

    /** Thrown by the retired SMS-code routes; mapped to 410 by ApiExceptionHandler. */
    public static class SmsDisabledException extends RuntimeException {
        public SmsDisabledException() {
            super("SMS verification codes are switched off. Sign in with your mobile number and password.");
        }
    }
}
