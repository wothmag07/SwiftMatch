package com.swiftmatch.api.ride;

import com.swiftmatch.common.ride.Coord;

import java.util.List;

/**
 * Seam over Redis {@code GEOSEARCH} used by {@link RideMatcher}. Production impl
 * wraps {@code StringRedisTemplate.opsForGeo()}; tests substitute a trivial stub.
 *
 * <p>Results are returned nearest-first and include the driver's last-known coords
 * so the matcher doesn't need a second round-trip to ask "where is this driver".
 */
public interface DriverGeoIndex {

    /**
     * @param pickup rider pickup point
     * @param radiusKm search radius in kilometres
     * @return candidates within {@code radiusKm} sorted by ascending distance,
     *         capped at {@code 10} results
     */
    List<Candidate> candidatesNear(Coord pickup, int radiusKm);

    record Candidate(String driverId, double lat, double lng) {
    }
}
