package com.swiftmatch.common.rider;

import java.time.Instant;
import java.util.UUID;

public record RiderResponse(
        UUID id,
        String name,
        String phone,
        Instant createdAt
) {
}
