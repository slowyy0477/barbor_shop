package com.ayan.salon.server.web;

import com.ayan.salon.server.service.ActorContext;
import com.ayan.salon.server.service.IdempotencyService;
import com.ayan.salon.server.service.WalletService;
import com.ayan.salon.server.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MissingServletRequestPartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception e) { return body("BAD_REQUEST", e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> uploadTooLarge(Exception e) {
        return body("PAYLOAD_TOO_LARGE", "Uploaded image is too large");
    }
    @ExceptionHandler(WalletService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(Exception e) { return body("NOT_FOUND", e.getMessage()); }
    @ExceptionHandler({WalletService.ConflictException.class, WalletService.RuleViolationException.class, IdempotencyService.ConflictException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(Exception e) { return body("CONFLICT", e.getMessage()); }
    @ExceptionHandler({AuthService.AuthorizationException.class, AuthService.UnknownAccountException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> authentication(Exception e) { return body("UNAUTHORIZED", e.getMessage()); }
    @ExceptionHandler(AuthService.RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> rateLimited(Exception e) { return body("RATE_LIMITED", e.getMessage()); }
    @ExceptionHandler(AuthService.ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> authConflict(Exception e) { return body("CONFLICT", e.getMessage()); }
    @ExceptionHandler(AuthService.PinNotConfiguredException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> pinNotConfigured(Exception e) { return body("PIN_NOT_SET", e.getMessage()); }
    @ExceptionHandler(ActorContext.AuthorizationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> forbidden(Exception e) { return body("FORBIDDEN", e.getMessage()); }
    /**
     * The SMS-code routes are retired. Gone (410) tells an old client that the
     * endpoint will not come back, instead of a retryable failure.
     */
    @ExceptionHandler(AuthController.SmsDisabledException.class)
    @ResponseStatus(HttpStatus.GONE)
    public Map<String, Object> smsDisabled(Exception e) { return body("SMS_DISABLED", e.getMessage()); }
    /**
     * A unique index rejected the write. The service pre-checks every case it
     * can, so this is the last line of defence for a simultaneous double submit
     * such as two taps on "Create account" or the same device signing up twice.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> duplicateWrite(DataIntegrityViolationException e) {
        return body("CONFLICT", "That record already exists. Please sign in instead of creating it again.");
    }
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> state(Exception e) { return body("INVALID_STATE", e.getMessage()); }
    private Map<String, Object> body(String code, String message) { return Map.of("code", code, "message", message == null ? code : message, "at", Instant.now().toString()); }
}
