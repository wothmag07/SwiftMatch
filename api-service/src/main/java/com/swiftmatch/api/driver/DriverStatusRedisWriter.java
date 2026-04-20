package com.swiftmatch.api.driver;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Thin wrapper around the driver status + heartbeat keys in Redis.
 * TTLs come from [SRS-DRV-5] (30s for AVAILABLE / heartbeat) and [SRS-MAT-4] (3600s for ON_TRIP).
 * See [SRS-§6.1] for the key catalog.
 */
@Component
public class DriverStatusRedisWriter {

    static final Duration AVAILABLE_TTL = Duration.ofSeconds(30);
    static final Duration ON_TRIP_TTL = Duration.ofHours(1);
    static final String ACTIVE_GEO_KEY = "drivers:active";

    private final StringRedisTemplate redis;

    public DriverStatusRedisWriter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void markAvailable(UUID driverId) {
        String id = driverId.toString();
        redis.opsForValue().set(statusKey(id), "AVAILABLE", AVAILABLE_TTL);
        redis.opsForValue().set(heartbeatKey(id), Instant.now().toString(), AVAILABLE_TTL);
    }

    public void markOnTrip(UUID driverId) {
        redis.opsForValue().set(statusKey(driverId.toString()), "ON_TRIP", ON_TRIP_TTL);
    }

    /**
     * Fully remove a driver from all live-state keys. Called on offline, crash, or trip completion.
     */
    public void clear(UUID driverId) {
        String id = driverId.toString();
        redis.delete(statusKey(id));
        redis.delete(heartbeatKey(id));
        redis.opsForZSet().remove(ACTIVE_GEO_KEY, id);
    }

    static String statusKey(String id) {
        return "driver:" + id + ":status";
    }

    static String heartbeatKey(String id) {
        return "driver:" + id + ":heartbeat";
    }
}
