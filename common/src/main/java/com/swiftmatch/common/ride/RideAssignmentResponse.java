package com.swiftmatch.common.ride;

import java.time.Instant;
import java.util.UUID;

/**
 * 200 response body for {@code POST /v1/rides}.
 */
public record RideAssignmentResponse(
        UUID rideId,
        RideStatus status,
        AssignedDriver driver,
        Instant requestedAt,
        Instant assignedAt
) {
}
