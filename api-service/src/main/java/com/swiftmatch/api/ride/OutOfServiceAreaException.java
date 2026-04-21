package com.swiftmatch.api.ride;

/**
 * Thrown when a ride's pickup or dropoff falls outside the city bbox. Maps to
 * 400 {@code out-of-service-area}.
 */
public class OutOfServiceAreaException extends RuntimeException {

    private final String field;

    public OutOfServiceAreaException(String field) {
        super(field + " is outside the active service area");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
