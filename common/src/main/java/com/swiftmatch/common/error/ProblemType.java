package com.swiftmatch.common.error;

import java.net.URI;

/**
 * RFC 7807 problem type URIs.
 * MVP-binding set: only {@code validation}, {@code not-found}, and {@code no-driver-found} are guaranteed stable.
 * The extra types (driver-on-trip etc.) are not in the binding catalog but still use
 * the same base URI scheme for consistency.
 */
public enum ProblemType {
    VALIDATION("validation"),
    NOT_FOUND("not-found"),
    NO_DRIVER_FOUND("no-driver-found"),
    DRIVER_ON_TRIP("driver-on-trip"),
    INVALID_RIDE_TRANSITION("invalid-ride-transition"),
    INGESTION_TIMEOUT("ingestion-timeout"),
    OUT_OF_SERVICE_AREA("out-of-service-area"),
    RIDER_HAS_ACTIVE_RIDE("rider-has-active-ride");

    private static final String BASE = "https://swiftmatch.local/errors/";

    private final String slug;

    ProblemType(String slug) {
        this.slug = slug;
    }

    public URI uri() {
        return URI.create(BASE + slug);
    }

    public String slug() {
        return slug;
    }
}
