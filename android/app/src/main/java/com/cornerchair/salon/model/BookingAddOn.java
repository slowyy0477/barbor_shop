package com.cornerchair.salon.model;

/** Optional booking add-on. Add-ons are explicit and never selected by default. */
public final class BookingAddOn {
    private final String id;
    private final String name;
    private final long pricePkr;
    private final int extraDurationMinutes;

    public BookingAddOn(String id, String name, long pricePkr, int extraDurationMinutes) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(name, "name");
        if (pricePkr < 0 || extraDurationMinutes < 0) {
            throw new IllegalArgumentException("add-on price and duration cannot be negative");
        }
        this.id = id;
        this.name = name;
        this.pricePkr = pricePkr;
        this.extraDurationMinutes = extraDurationMinutes;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPricePkr() {
        return pricePkr;
    }

    public int getExtraDurationMinutes() {
        return extraDurationMinutes;
    }

    /** Value comparison used when validating idempotent booking retries. */
    public boolean sameDetails(BookingAddOn other) {
        return other != null
                && pricePkr == other.pricePkr
                && extraDurationMinutes == other.extraDurationMinutes
                && id.equals(other.id)
                && name.equals(other.name);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof BookingAddOn && sameDetails((BookingAddOn) object);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (int) (pricePkr ^ (pricePkr >>> 32));
        result = 31 * result + extraDurationMinutes;
        return result;
    }
}
