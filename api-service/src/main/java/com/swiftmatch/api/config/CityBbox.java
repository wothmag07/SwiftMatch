package com.swiftmatch.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-area bounding box per [SRS-§4.11]. Coordinates outside this bbox are
 * still accepted on ingestion (per [SRS-LOC-9]) but logged at WARN.
 */
@ConfigurationProperties("swiftmatch.city.bbox")
public record CityBbox(
        double southLat,
        double westLng,
        double northLat,
        double eastLng
) {
    public boolean contains(double lat, double lng) {
        return lat >= southLat && lat <= northLat
                && lng >= westLng && lng <= eastLng;
    }
}
