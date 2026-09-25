package com.ayan.salon.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Canonical Pakistani mobile handling shared by authentication and customer APIs. */
public final class PhoneIdentity {
    private PhoneIdentity() {}

    public static String canonicalPakistani(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.startsWith("0092") && digits.length() == 14) digits = "0" + digits.substring(4);
        else if (digits.startsWith("92") && digits.length() == 12) digits = "0" + digits.substring(2);
        else if (digits.matches("3\\d{9}")) digits = "0" + digits;
        if (!digits.matches("03\\d{9}")) throw new IllegalArgumentException("Complete Pakistani mobile number required");
        return digits;
    }

    public static String sha256(String canonicalPhone) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPhone.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
