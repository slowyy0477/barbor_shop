package com.ayan.salon.server.web;

import com.ayan.salon.server.service.AuthService;
import com.ayan.salon.server.service.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/otp/request")
    public AuthService.OtpRequestResult requestOtp(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        return auth.requestOtp(request.salonId(), request.phone(), clientIp(http));
    }

    @PostMapping("/otp/verify")
    public SessionResponse verify(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest http) {
        return SessionResponse.from(auth.verifyOtp(request.salonId(), request.challengeId(), request.code(), http.getHeader("User-Agent")));
    }

    @PostMapping("/customer/register")
    public SessionResponse register(@Valid @RequestBody CustomerRegisterRequest request, HttpServletRequest http) {
        return SessionResponse.from(auth.registerCustomer(request.salonId(), request.challengeId(), request.code(),
                request.phone(), request.name(), request.marketingConsent(), request.pin(), http.getHeader("User-Agent")));
    }

    /**
     * PIN sign-in kept alongside OTP. The verification code stays available as
     * recovery, so a forgotten PIN never locks a customer out permanently.
     */
    @PostMapping("/pin/verify")
    public SessionResponse verifyPin(@Valid @RequestBody PinVerifyRequest request, HttpServletRequest http) {
        return SessionResponse.from(auth.loginWithPin(request.salonId(), request.phone(), request.pin(), http.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) auth.revoke(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }

    private static String clientIp(HttpServletRequest request) {
        // Do not trust forwarded headers unless the reverse proxy is configured to
        // overwrite them. The direct remote address is safe for the local limiter.
        return request.getRemoteAddr();
    }

    public record OtpRequest(@NotNull UUID salonId, @NotBlank String phone) {}
    public record OtpVerifyRequest(@NotNull UUID salonId, @NotNull UUID challengeId, @NotBlank String code) {}
    public record CustomerRegisterRequest(@NotNull UUID salonId, @NotNull UUID challengeId, @NotBlank String code,
                                          @NotBlank String phone, @NotBlank String name, boolean marketingConsent,
                                          String pin) {}
    /**
     * The mobile number is optional: the salon owner can open the owner
     * workspace with the long owner password alone when no SMS provider is
     * connected. Customer PIN sign-in still needs the number because short
     * PINs are never accepted without it.
     */
    public record PinVerifyRequest(@NotNull UUID salonId, String phone, @NotBlank String pin) {}
    public record SessionResponse(String accessToken, Instant expiresAt, UUID salonId, UUID actorId, String role) {
        static SessionResponse from(SessionTokenService.IssuedSession value) {
            return new SessionResponse(value.token(), value.expiresAt(), value.salonId(), value.actorId(), value.role().name());
        }
    }
}
