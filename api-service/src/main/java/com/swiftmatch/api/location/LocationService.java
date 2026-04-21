package com.swiftmatch.api.location;

import com.swiftmatch.api.config.CityBbox;
import com.swiftmatch.common.location.LocationEvent;
import com.swiftmatch.common.location.LocationUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Validates a single location update against the configured city bbox (WARN-only
 * per [SRS-LOC-9]) and hands a {@link LocationEvent} to {@link LocationProducer}.
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationProducer producer;
    private final CityBbox bbox;

    public LocationService(LocationProducer producer, CityBbox bbox) {
        this.producer = producer;
        this.bbox = bbox;
    }

    public void ingest(UUID driverId, LocationUpdateRequest request) {
        if (!bbox.contains(request.lat(), request.lng())) {
            log.warn("location outside city bbox accepted driverId={} lat={} lng={}",
                    driverId, request.lat(), request.lng());
        }
        LocationEvent event = new LocationEvent(
                driverId,
                request.lat(),
                request.lng(),
                request.recordedAt(),
                Instant.now()
        );
        producer.publish(driverId, event);
    }
}
