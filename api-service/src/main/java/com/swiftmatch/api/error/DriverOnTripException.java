package com.swiftmatch.api.error;

import java.util.UUID;

/**
 * Thrown when a caller attempts to transition a driver to OFFLINE while they are ON_TRIP.
 * Maps to HTTP 409.
 */
public class DriverOnTripException extends RuntimeException {

    private final UUID driverId;

    public DriverOnTripException(UUID driverId) {
        super("Driver " + driverId + " is currently on a trip and cannot go offline");
        this.driverId = driverId;
    }

    public UUID getDriverId() {
        return driverId;
    }
}
