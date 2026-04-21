package com.swiftmatch.api.ride;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis-backed {@link DriverStatusReader}. Key scheme matches
 * {@link com.swiftmatch.api.driver.DriverStatusRedisWriter}.
 */
@Component
public class RedisDriverStatusReader implements DriverStatusReader {

    private final StringRedisTemplate redis;

    public RedisDriverStatusReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String readStatus(UUID driverId) {
        return redis.opsForValue().get("driver:" + driverId + ":status");
    }
}
