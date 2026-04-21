package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.LockConfig;
import com.swiftmatch.api.config.MatchConfig;
import com.swiftmatch.common.ride.Coord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core matcher (radius-expansion loop with distributed lock). Pure Redis state transitions; DB writes and Kafka
 * publishes stay in {@link RideService}.
 *
 * <p>Algorithm:
 * <pre>
 *   for radius in [2, 5, 10] km:
 *     candidates = geoIndex.candidatesNear(pickup, radius)    // nearest first
 *     for c in candidates:
 *        if statusReader.readStatus(c.id) == AVAILABLE
 *           if redisson.lock("lock:driver:{id}").tryLock(0, 10s):
 *              try:
 *                 if statusReader.readStatus(c.id) == AVAILABLE:   // double-check
 *                    return c
 *              finally: unlock
 *     if elapsed &gt; timeoutMs: break
 *   return empty
 * </pre>
 *
 * <p>Safety: the partial unique index {@code rides_one_active_per_driver} in V1 is the
 * authoritative last-line invariant; the Redisson lock is the fast path
 * that should keep the DB from ever seeing a conflict.
 */
@Component
public class RideMatcher {

    private static final Logger log = LoggerFactory.getLogger(RideMatcher.class);
    private static final String AVAILABLE = "AVAILABLE";

    private final DriverGeoIndex geoIndex;
    private final DriverStatusReader statusReader;
    private final DriverStatusWriter statusWriter;
    private final RedissonClient redisson;
    private final MatchConfig match;
    private final LockConfig lockConfig;
    private final Clock clock;

    public RideMatcher(DriverGeoIndex geoIndex,
                       DriverStatusReader statusReader,
                       DriverStatusWriter statusWriter,
                       RedissonClient redisson,
                       MatchConfig match,
                       LockConfig lockConfig,
                       Clock clock) {
        this.geoIndex = geoIndex;
        this.statusReader = statusReader;
        this.statusWriter = statusWriter;
        this.redisson = redisson;
        this.match = match;
        this.lockConfig = lockConfig;
        this.clock = clock;
    }

    public Optional<Match> match(Coord pickup) {
        long startMillis = clock.millis();

        for (int radiusKm : match.radiiKm()) {
            if (elapsed(startMillis) > match.timeoutMs()) {
                log.warn("Matcher budget {}ms exhausted before radius {}km", match.timeoutMs(), radiusKm);
                break;
            }

            List<DriverGeoIndex.Candidate> candidates = geoIndex.candidatesNear(pickup, radiusKm);
            for (DriverGeoIndex.Candidate c : candidates) {
                if (elapsed(startMillis) > match.timeoutMs()) {
                    log.warn("Matcher budget {}ms exhausted mid-radius {}km", match.timeoutMs(), radiusKm);
                    return Optional.empty();
                }
                Optional<Match> m = tryCandidate(c);
                if (m.isPresent()) return m;
            }
        }
        return Optional.empty();
    }

    private Optional<Match> tryCandidate(DriverGeoIndex.Candidate candidate) {
        UUID driverId;
        try {
            driverId = UUID.fromString(candidate.driverId());
        } catch (IllegalArgumentException e) {
            log.warn("Skipping non-UUID driver entry in drivers:active: {}", candidate.driverId());
            return Optional.empty();
        }

        if (!AVAILABLE.equals(statusReader.readStatus(driverId))) {
            return Optional.empty();
        }

        RLock lock = redisson.getLock("lock:driver:" + candidate.driverId());
        boolean acquired;
        try {
            acquired = lock.tryLock(lockConfig.tryLockMs(), lockConfig.leaseMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (!acquired) {
            return Optional.empty();
        }

        try {
            if (!AVAILABLE.equals(statusReader.readStatus(driverId))) {
                return Optional.empty();
            }
            // Flip to ON_TRIP *inside* the lock so the next matcher's pre-lock
            // status check sees ON_TRIP and skips this driver. If the caller's
            // subsequent Postgres UPDATE blows up on the partial unique index
            // (the partial unique index backstop), the driver stays ON_TRIP in
            // Redis until the 1 h TTL expires — acceptable because the
            // heartbeat reaper is deferred; rare path in practice.
            statusWriter.markOnTrip(driverId);
            return Optional.of(new Match(driverId, candidate.lat(), candidate.lng()));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private long elapsed(long startMillis) {
        return clock.millis() - startMillis;
    }

    public record Match(UUID driverId, double lat, double lng) {
    }
}
