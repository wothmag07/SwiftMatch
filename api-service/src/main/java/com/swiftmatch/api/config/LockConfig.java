package com.swiftmatch.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson lock tuning for the matcher's critical section.
 * {@code tryLockMs=0} = non-blocking acquire; {@code leaseMs=10000} = 10 s fencing TTL.
 */
@ConfigurationProperties("swiftmatch.lock")
public record LockConfig(long tryLockMs, long leaseMs) {

    public LockConfig {
        if (tryLockMs < 0) tryLockMs = 0L;
        if (leaseMs <= 0) leaseMs = 10_000L;
    }
}
