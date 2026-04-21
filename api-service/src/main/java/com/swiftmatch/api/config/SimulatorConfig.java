package com.swiftmatch.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simulator parameters used server-side by the ETA estimator. The other simulator
 * knobs (tickIntervalMs, driverCount, declineProbability) live on simulator-service.
 * {@code speedKmh} is read here because {@link com.swiftmatch.api.ride.EtaEstimator}
 * needs it for the great-circle ETA fallback.
 */
@ConfigurationProperties("swiftmatch.simulator")
public record SimulatorConfig(double speedKmh) {

    public SimulatorConfig {
        if (speedKmh <= 0) speedKmh = 30.0;
    }
}
