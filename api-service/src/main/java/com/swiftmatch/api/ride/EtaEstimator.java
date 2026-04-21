package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.SimulatorConfig;
import com.swiftmatch.common.ride.Coord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Great-circle ETA fallback. Until OSRM lands ({@code chore/osrm-routing-engine})
 * this is the *only* path — every call logs WARN to make the fallback visible in logs.
 */
@Component
public class EtaEstimator {

    private static final Logger log = LoggerFactory.getLogger(EtaEstimator.class);
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private final double speedKmh;

    public EtaEstimator(SimulatorConfig simulator) {
        this.speedKmh = simulator.speedKmh();
    }

    public int secondsFromGreatCircle(Coord from, Coord to) {
        double meters = haversineMeters(from, to);
        double metersPerSecond = (speedKmh * 1000.0) / 3600.0;
        int seconds = (int) Math.round(meters / metersPerSecond);
        log.warn("ETA via great-circle fallback (OSRM pending M4): {} m / {} m/s ≈ {} s",
                (long) meters, metersPerSecond, seconds);
        return seconds;
    }

    static double haversineMeters(Coord a, Coord b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }
}
