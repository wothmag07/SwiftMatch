package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.SimulatorConfig;
import com.swiftmatch.common.ride.Coord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtaEstimatorTest {

    private final EtaEstimator estimator = new EtaEstimator(new SimulatorConfig(30.0));

    @Test
    void zero_distance_is_zero_seconds() {
        Coord p = new Coord(37.7749, -122.4194);
        assertThat(estimator.secondsFromGreatCircle(p, p)).isZero();
    }

    @Test
    void one_km_at_30kmh_is_about_120_seconds() {
        // 1 km north of the SF center.
        Coord from = new Coord(37.7749, -122.4194);
        Coord to = new Coord(37.7839, -122.4194);
        int eta = estimator.secondsFromGreatCircle(from, to);
        assertThat(eta).isBetween(115, 125);
    }

    @Test
    void haversine_sanity_between_sf_and_oakland() {
        // ~13 km across the bay.
        Coord sf = new Coord(37.7749, -122.4194);
        Coord oakland = new Coord(37.8044, -122.2712);
        double meters = EtaEstimator.haversineMeters(sf, oakland);
        assertThat(meters).isBetween(12_500.0, 14_000.0);
    }
}
