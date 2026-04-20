package com.swiftmatch.api.error;

/**
 * Base exception for all 404-mapped errors.
 * Subclasses should provide a human-readable detail message suitable for Problem+JSON.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
