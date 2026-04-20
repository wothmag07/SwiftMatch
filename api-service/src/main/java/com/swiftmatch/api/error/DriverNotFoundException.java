package com.swiftmatch.api.error;

import java.util.UUID;

public class DriverNotFoundException extends NotFoundException {

    private final UUID driverId;

    public DriverNotFoundException(UUID driverId) {
        super("Driver not found: " + driverId);
        this.driverId = driverId;
    }

    public UUID getDriverId() {
        return driverId;
    }
}
