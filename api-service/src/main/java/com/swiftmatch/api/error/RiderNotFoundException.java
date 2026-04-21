package com.swiftmatch.api.error;

import java.util.UUID;

public class RiderNotFoundException extends NotFoundException {

    private final UUID riderId;

    public RiderNotFoundException(UUID riderId) {
        super("Rider not found: " + riderId);
        this.riderId = riderId;
    }

    public UUID getRiderId() {
        return riderId;
    }
}
