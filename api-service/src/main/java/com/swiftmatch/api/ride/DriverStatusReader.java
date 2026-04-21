package com.swiftmatch.api.ride;

import java.util.UUID;

/**
 * Seam over {@code GET driver:{id}:status} used by {@link RideMatcher}.
 * Null when the key is absent (= driver offline or TTL expired).
 */
public interface DriverStatusReader {

    String readStatus(UUID driverId);
}
