package com.swiftmatch.common.ride;

/**
 * Mirrors the CHECK constraint on {@code rides.status} in V1__initial.sql.
 * Transitions: PENDING → ASSIGNED → ON_TRIP → COMPLETED; lateral → NO_DRIVER_FOUND,
 * CANCELLED, FAILED.
 */
public enum RideStatus {
    PENDING,
    ASSIGNED,
    ON_TRIP,
    COMPLETED,
    CANCELLED,
    NO_DRIVER_FOUND,
    FAILED
}
