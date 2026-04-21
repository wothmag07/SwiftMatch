package com.swiftmatch.api.ride;

import java.util.UUID;

/**
 * Thrown when a rider already has a ride in PENDING / ASSIGNED / ON_TRIP.
 * Maps to 409 {@code rider-has-active-ride}. Per PRD edge-case doc; server-side
 * guard that complements the UI's disabled-button pattern for duplicate submissions.
 */
public class RiderHasActiveRideException extends RuntimeException {

    private final UUID riderId;

    public RiderHasActiveRideException(UUID riderId) {
        super("Rider " + riderId + " already has an active ride");
        this.riderId = riderId;
    }

    public UUID getRiderId() {
        return riderId;
    }
}
