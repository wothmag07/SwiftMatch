package com.swiftmatch.indexer.location;

import com.swiftmatch.common.location.LocationEvent;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies one {@link LocationEvent} to the Redis hot state:
 * {@code GEOADD drivers:active <lng> <lat> <driverId>} and
 * {@code EXPIRE driver:{id}:heartbeat 30} per [SRS-LOC-5].
 *
 * <p>Executed as a single pipelined unit so both commands hit Redis in one round-trip.
 * Exceptions propagate to the consumer, which lets {@code DefaultErrorHandler} retry.
 */
@Component
public class LocationRedisWriter {

    static final String ACTIVE_GEO_KEY = "drivers:active";
    static final long HEARTBEAT_TTL_SECONDS = 30L;

    private final StringRedisTemplate redis;

    public LocationRedisWriter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void index(LocationEvent event) {
        String id = event.driverId().toString();
        redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.geoCommands().geoAdd(
                    ACTIVE_GEO_KEY.getBytes(),
                    new Point(event.lng(), event.lat()),
                    id.getBytes());
            connection.keyCommands().expire(
                    heartbeatKey(id).getBytes(),
                    HEARTBEAT_TTL_SECONDS);
            return null;
        });
    }

    static String heartbeatKey(String id) {
        return "driver:" + id + ":heartbeat";
    }
}
