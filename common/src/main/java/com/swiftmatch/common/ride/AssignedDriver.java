package com.swiftmatch.common.ride;

import java.util.UUID;

/**
 * Driver snapshot embedded in a successful ride assignment response.
 * {@code etaSeconds} is great-circle ÷ simulator.speedKmh pre-OSRM; OSRM-derived once M4 lands.
 */
public record AssignedDriver(
        UUID id,
        String name,
        String vehicle,
        double lat,
        double lng,
        int etaSeconds
) {
}
