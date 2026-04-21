package com.swiftmatch.api.error;

import java.util.UUID;

public class IngestionTimeoutException extends RuntimeException {

    private final UUID driverId;

    public IngestionTimeoutException(UUID driverId, Throwable cause) {
        super("Kafka producer exceeded 200 ms for driverId=" + driverId, cause);
        this.driverId = driverId;
    }

    public UUID getDriverId() {
        return driverId;
    }
}
