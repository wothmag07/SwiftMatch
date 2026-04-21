package com.swiftmatch.common.ride;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code ride.request.v1} (key = riderId) at PENDING insert time.
 * No consumer in this branch — future stream-service broadcasts {@code ride-created}
 * from it and k6 can tail it for throughput validation.
 */
public record RideRequestEvent(
        UUID rideId,
        UUID riderId,
        Coord pickup,
        Coord dropoff,
        Instant requestedAt
) {
}
