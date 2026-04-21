package com.swiftmatch.api.ride;

import java.util.UUID;

/**
 * Thrown by {@link RideService} after the matcher exhausts {@code radiiKm} without
 * assigning a driver, or runs past the {@code timeoutMs} budget. Maps to 503
 * {@code no-driver-found}.
 */
public class NoDriverFoundException extends RuntimeException {

    private final UUID rideId;

    public NoDriverFoundException(UUID rideId) {
        super("No AVAILABLE driver found for ride " + rideId);
        this.rideId = rideId;
    }

    public UUID getRideId() {
        return rideId;
    }
}
