package com.ayan.salon.server.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 helper for customer sign-in PINs. The digest is salted per
 * account and compared in constant time so a wrong PIN cannot be narrowed down
 * by response timing.
 */
final class PinHasher {
    static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHasher() {}

    static boolean validPin(String pin) {
        return pin != null && pin.trim().matches("\\d{4,6}");
    }

    static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    static String hash(String pin, String salt, int iterations) {
        if (!validPin(pin)) throw new IllegalArgumentException("Choose a 4 to 6 digit PIN");
        char[] characters = pin.trim().toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, decodeSalt(salt), iterations, KEY_BITS);
        try {
            byte[] digest = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException failure) {
            throw new IllegalStateException("PIN hashing is unavailable on this runtime", failure);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(characters, '\0');
        }
    }

    static boolean matches(String pin, String salt, int iterations, String expectedHash) {
        if (!validPin(pin) || salt == null || expectedHash == null || iterations < 10_000) return false;
        String candidate = hash(pin, salt, iterations);
        return MessageDigest.isEqual(candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] decodeSalt(String salt) {
        try {
            return Base64.getUrlDecoder().decode(salt);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("PIN salt is malformed", failure);
        }
    }
}
