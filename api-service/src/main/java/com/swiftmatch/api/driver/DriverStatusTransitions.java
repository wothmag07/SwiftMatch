package com.swiftmatch.api.driver;

import com.swiftmatch.api.error.DriverOnTripException;
import com.swiftmatch.common.driver.DriverStatus;

/**
 * Pure functions encoding the allowed driver-status transitions.
 * Kept state-less and framework-free for easy unit testing.
 */
public final class DriverStatusTransitions {

    private DriverStatusTransitions() {
    }

    /**
     * Resolve the target state when the driver requests "go online".
     * <p>
     * - OFFLINE   → AVAILABLE
     * - AVAILABLE → AVAILABLE (idempotent no-op)
     * - ON_TRIP   → ON_TRIP   (no-op; trip completion will free them)
     */
    public static DriverStatus applyOnline(DriverStatus current) {
        return switch (current) {
            case OFFLINE, AVAILABLE -> DriverStatus.AVAILABLE;
            case ON_TRIP -> DriverStatus.ON_TRIP;
        };
    }

    /**
     * Resolve the target state when the driver requests "go offline".
     * <p>
     * - OFFLINE   → OFFLINE (idempotent)
     * - AVAILABLE → OFFLINE
     * - ON_TRIP   → throws {@link DriverOnTripException}
     */
    public static DriverStatus applyOffline(DriverStatus current, java.util.UUID driverId) {
        return switch (current) {
            case OFFLINE, AVAILABLE -> DriverStatus.OFFLINE;
            case ON_TRIP -> throw new DriverOnTripException(driverId);
        };
    }
}
