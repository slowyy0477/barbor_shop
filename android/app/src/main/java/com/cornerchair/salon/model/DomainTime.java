package com.cornerchair.salon.model;

import java.util.Calendar;
import java.util.TimeZone;

/** Small API-24-safe date helpers. Dates are calculated in UTC for deterministic rules. */
public final class DomainTime {
    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private DomainTime() {
    }

    public static long plusDays(long epochMillis, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days cannot be negative");
        }
        return epochMillis + (long) days * MILLIS_PER_DAY;
    }

    public static String utcDateKey(long epochMillis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(epochMillis);
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    public static void requireNonBlank(String value, String field) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
