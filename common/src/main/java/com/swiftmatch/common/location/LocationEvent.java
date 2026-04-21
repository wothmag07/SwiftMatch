package com.swiftmatch.common.location;

import java.time.Instant;
import java.util.UUID;

public record LocationEvent(
        UUID driverId,
        double lat,
        double lng,
        Instant recordedAt,
        Instant ingestedAt
) {
}
