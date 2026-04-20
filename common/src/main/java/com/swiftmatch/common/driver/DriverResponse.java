package com.swiftmatch.common.driver;

import java.time.Instant;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String name,
        String phone,
        String vehicle,
        DriverStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
