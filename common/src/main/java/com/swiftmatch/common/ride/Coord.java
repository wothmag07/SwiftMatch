package com.swiftmatch.common.ride;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * A WGS84 point carried on ride requests and Kafka events. Bounds are the global
 * lat/lng domain; service-area (bbox) validation lives in api-service.
 */
public record Coord(
        @DecimalMin(value = "-90.0", inclusive = true)
        @DecimalMax(value = "90.0", inclusive = true)
        double lat,
        @DecimalMin(value = "-180.0", inclusive = true)
        @DecimalMax(value = "180.0", inclusive = true)
        double lng
) {
}
