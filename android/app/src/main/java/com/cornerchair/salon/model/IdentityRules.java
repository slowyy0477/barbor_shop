package com.cornerchair.salon.model;

/**
 * Normalization helpers for verified customer identity checks. Phone values should come from the
 * verified customer record, never from an untrusted referral text field.
 */
public final class IdentityRules {
    private IdentityRules() {
    }

    /** Normalizes customer IDs for stable comparisons without changing their stored value. */
    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String trimmed = value.trim();
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                result.append(Character.toLowerCase(character));
            }
        }
        return result.toString();
    }

    /**
     * Converts common Pakistan local/international formats to a local 03xxxxxxxxx form. For other
     * countries it still strips punctuation, allowing exact verified values to compare safely.
     */
    public static String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder digitsBuilder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                digitsBuilder.append(character);
            }
        }
        String digits = digitsBuilder.toString();
        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("92") && digits.length() == 12) {
            digits = "0" + digits.substring(2);
        } else if (digits.length() == 10 && digits.charAt(0) == '3') {
            digits = "0" + digits;
        }
        return digits;
    }

    public static boolean sameId(String left, String right) {
        String normalizedLeft = normalizeId(left);
        String normalizedRight = normalizeId(right);
        return normalizedLeft.length() > 0
                && normalizedLeft.equals(normalizedRight);
    }

    public static boolean samePhone(String left, String right) {
        String normalizedLeft = normalizePhone(left);
        String normalizedRight = normalizePhone(right);
        // Avoid treating short numeric database IDs as phone numbers.
        return normalizedLeft.length() >= 7
                && normalizedLeft.equals(normalizedRight);
    }

    /** Compares IDs first and verified phones second. */
    public static boolean samePerson(String leftId,
                                     String rightId,
                                     String leftPhone,
                                     String rightPhone) {
        return sameId(leftId, rightId) || samePhone(leftPhone, rightPhone);
    }
}
