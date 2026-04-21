package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.CityBbox;
import com.swiftmatch.common.ride.Coord;
import org.springframework.stereotype.Component;

/**
 * Gate before matching: both pickup and dropoff must sit inside the
 * active city bbox. Differs from location ingestion, which accepts out-of-bbox
 * points with a WARN.
 */
@Component
public class RideBboxValidator {

    private final CityBbox bbox;

    public RideBboxValidator(CityBbox bbox) {
        this.bbox = bbox;
    }

    public void validate(Coord pickup, Coord dropoff) {
        if (!bbox.contains(pickup.lat(), pickup.lng())) {
            throw new OutOfServiceAreaException("pickup");
        }
        if (!bbox.contains(dropoff.lat(), dropoff.lng())) {
            throw new OutOfServiceAreaException("dropoff");
        }
    }
}
