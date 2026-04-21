package com.swiftmatch.common.ride;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code ride.assignment.v1} (key = rideId) after the matcher wins
 * the Redisson lock and the rides row is UPDATEd to ASSIGNED. Consumed by
 * stream-service (SSE fan-out) and simulator-service (drive the assigned driver).
 */
public record RideAssignmentEvent(
        UUID rideId,
        UUID riderId,
        UUID driverId,
        Coord pickup,
        Coord dropoff,
        Instant assignedAt
) {
}
