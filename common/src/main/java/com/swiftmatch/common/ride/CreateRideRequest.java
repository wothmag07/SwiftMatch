package com.swiftmatch.common.ride;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /v1/rides}.
 * Idempotency key is intentionally absent — deferred.
 */
public record CreateRideRequest(
        @NotNull UUID riderId,
        @NotNull @Valid Coord pickup,
        @NotNull @Valid Coord dropoff
) {
}
