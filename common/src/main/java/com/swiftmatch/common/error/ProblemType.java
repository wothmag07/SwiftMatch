package com.swiftmatch.common.error;

import java.net.URI;

/**
 * RFC 7807 problem type URIs.
 * MVP-binding set per Amendment 001 §4.4: only these three are guaranteed stable.
 * The extra types (driver-on-trip etc.) are not in the binding catalog but still use
 * the same base URI scheme for consistency.
 */
public enum ProblemType {
    VALIDATION("validation"),
    NOT_FOUND("not-found"),
    NO_DRIVER_FOUND("no-driver-found"),
    DRIVER_ON_TRIP("driver-on-trip"),
    INVALID_RIDE_TRANSITION("invalid-ride-transition");

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
