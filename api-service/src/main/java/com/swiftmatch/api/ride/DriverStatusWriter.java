package com.swiftmatch.api.ride;

import java.util.UUID;

/**
 * Narrow write seam used by {@link RideMatcher} to flip a driver to ON_TRIP
 * <em>inside</em> the Redisson critical section. Kept separate from
 * {@link DriverStatusReader} because the matcher only needs one write path
 * (status → ON_TRIP) and wider operations belong on the driver package.
 */
public interface DriverStatusWriter {

    void markOnTrip(UUID driverId);
}
