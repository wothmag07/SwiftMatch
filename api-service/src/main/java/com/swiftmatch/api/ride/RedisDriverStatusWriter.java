package com.swiftmatch.api.ride;

import com.swiftmatch.api.driver.DriverStatusRedisWriter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Delegates to the existing {@link DriverStatusRedisWriter} so the Redis key
 * scheme stays owned by the driver package.
 */
@Component
public class RedisDriverStatusWriter implements DriverStatusWriter {

    private final DriverStatusRedisWriter delegate;

    public RedisDriverStatusWriter(DriverStatusRedisWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void markOnTrip(UUID driverId) {
        delegate.markOnTrip(driverId);
    }
}
