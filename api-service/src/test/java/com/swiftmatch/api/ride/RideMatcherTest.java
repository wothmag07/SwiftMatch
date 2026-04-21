package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.LockConfig;
import com.swiftmatch.api.config.MatchConfig;
import com.swiftmatch.common.ride.Coord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RideMatcher} without Redis. Uses {@link Proxy} stubs because
 * Mockito's inline maker is unstable on JDK 25 (see LocationHistoryBufferTest note)
 * and {@link RedissonClient} / {@link RLock} both expose very large surfaces that
 * make hand-written subclasses impractical.
 */
class RideMatcherTest {

    private static final Coord PICKUP = new Coord(37.7749, -122.4194);
    private static final MatchConfig CFG = new MatchConfig(List.of(2, 5, 10), 8000L);
    private static final LockConfig LOCK = new LockConfig(0L, 10_000L);

    private StubGeoIndex geo;
    private StubStatusReader status;
    private StubStatusWriter writes;
    private StubRedisson redisson;
    private MutableClock clock;
    private RideMatcher matcher;

    @BeforeEach
    void setUp() {
        geo = new StubGeoIndex();
        status = new StubStatusReader();
        writes = new StubStatusWriter();
        redisson = new StubRedisson();
        clock = new MutableClock();
        matcher = new RideMatcher(geo, status, writes, redisson.proxy(), CFG, LOCK, clock);
    }

    @Test
    void happy_path_returns_first_available_candidate() {
        UUID driverId = UUID.randomUUID();
        geo.at(2, candidate(driverId, 37.77, -122.42));
        status.set(driverId, "AVAILABLE");
        redisson.lockAcquirable(driverId);

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isPresent();
        assertThat(match.get().driverId()).isEqualTo(driverId);
        assertThat(match.get().lat()).isEqualTo(37.77);
        assertThat(match.get().lng()).isEqualTo(-122.42);
        assertThat(redisson.timesUnlocked(driverId)).isEqualTo(1);
        assertThat(writes.onTripFor).containsExactly(driverId);
    }

    @Test
    void lock_contested_falls_through_to_next_candidate() {
        UUID contested = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        geo.at(2, candidate(contested, 37.77, -122.42), candidate(next, 37.78, -122.43));
        status.set(contested, "AVAILABLE");
        status.set(next, "AVAILABLE");
        redisson.lockUnacquirable(contested);
        redisson.lockAcquirable(next);

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isPresent();
        assertThat(match.get().driverId()).isEqualTo(next);
    }

    @Test
    void status_race_after_lock_acquired_releases_and_moves_on() {
        UUID racer = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        geo.at(2, candidate(racer, 37.77, -122.42), candidate(next, 37.78, -122.43));
        status.script(racer, "AVAILABLE", "ON_TRIP");
        status.set(next, "AVAILABLE");
        redisson.lockAcquirable(racer);
        redisson.lockAcquirable(next);

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isPresent();
        assertThat(match.get().driverId()).isEqualTo(next);
        assertThat(redisson.timesUnlocked(racer)).as("lock on losing racer released").isEqualTo(1);
    }

    @Test
    void empty_first_radius_expands_to_second() {
        UUID farDriver = UUID.randomUUID();
        geo.at(5, candidate(farDriver, 37.80, -122.46));
        status.set(farDriver, "AVAILABLE");
        redisson.lockAcquirable(farDriver);

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isPresent();
        assertThat(match.get().driverId()).isEqualTo(farDriver);
    }

    @Test
    void status_not_available_is_skipped_without_grabbing_lock() {
        UUID offline = UUID.randomUUID();
        geo.at(2, candidate(offline, 37.77, -122.42));
        status.set(offline, "ON_TRIP");

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isEmpty();
        assertThat(redisson.getLockCallsFor(offline)).isZero();
    }

    @Test
    void no_candidates_across_all_radii_returns_empty() {
        Optional<RideMatcher.Match> match = matcher.match(PICKUP);
        assertThat(match).isEmpty();
        assertThat(geo.searchedRadii).containsExactly(2, 5, 10);
    }

    @Test
    void budget_exhaustion_bails_before_next_radius() {
        UUID driverId = UUID.randomUUID();
        geo.at(10, candidate(driverId, 37.90, -122.50));
        status.set(driverId, "AVAILABLE");
        redisson.lockAcquirable(driverId);

        // Clock jumps past the 8 s budget during the first (empty) GEOSEARCH.
        geo.onCandidatesNear((radius) -> {
            if (radius == 2) clock.advanceMs(9_000);
        });

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isEmpty();
        assertThat(geo.searchedRadii).containsExactly(2);
    }

    @Test
    void non_uuid_entry_in_geo_index_is_skipped_not_fatal() {
        UUID valid = UUID.randomUUID();
        geo.at(2,
                new DriverGeoIndex.Candidate("not-a-uuid", 37.77, -122.42),
                candidate(valid, 37.78, -122.43));
        status.set(valid, "AVAILABLE");
        redisson.lockAcquirable(valid);

        Optional<RideMatcher.Match> match = matcher.match(PICKUP);

        assertThat(match).isPresent();
        assertThat(match.get().driverId()).isEqualTo(valid);
    }

    private static DriverGeoIndex.Candidate candidate(UUID id, double lat, double lng) {
        return new DriverGeoIndex.Candidate(id.toString(), lat, lng);
    }

    // ----------------------- stubs -----------------------

    private static final class StubGeoIndex implements DriverGeoIndex {
        final Map<Integer, List<Candidate>> byRadius = new HashMap<>();
        final List<Integer> searchedRadii = new ArrayList<>();
        private java.util.function.IntConsumer sideEffect = r -> { };

        void at(int radiusKm, Candidate... candidates) {
            byRadius.put(radiusKm, List.of(candidates));
        }

        void onCandidatesNear(java.util.function.IntConsumer hook) {
            this.sideEffect = hook;
        }

        @Override
        public List<Candidate> candidatesNear(Coord pickup, int radiusKm) {
            searchedRadii.add(radiusKm);
            sideEffect.accept(radiusKm);
            return byRadius.getOrDefault(radiusKm, List.of());
        }
    }

    private static final class StubStatusWriter implements DriverStatusWriter {
        final List<UUID> onTripFor = new ArrayList<>();

        @Override
        public void markOnTrip(UUID driverId) {
            onTripFor.add(driverId);
        }
    }

    private static final class StubStatusReader implements DriverStatusReader {
        private final Map<UUID, List<String>> script = new HashMap<>();

        void set(UUID id, String value) {
            script.put(id, new ArrayList<>(List.of(value)));
        }

        /** Scripted sequence of reads; last value sticks after the list is drained. */
        void script(UUID id, String... values) {
            script.put(id, new ArrayList<>(List.of(values)));
        }

        @Override
        public String readStatus(UUID driverId) {
            List<String> seq = script.get(driverId);
            if (seq == null || seq.isEmpty()) return null;
            return seq.size() == 1 ? seq.get(0) : seq.remove(0);
        }
    }

    /**
     * Hand-rolled RedissonClient / RLock stub built on {@link Proxy} so we only
     * implement the few methods RideMatcher actually invokes.
     */
    private static final class StubRedisson {
        private final Map<String, LockHandle> locks = new HashMap<>();
        private final Map<UUID, Boolean> acquirable = new HashMap<>();
        private final Map<UUID, Integer> getLockCalls = new HashMap<>();

        void lockAcquirable(UUID driverId) { acquirable.put(driverId, true); }
        void lockUnacquirable(UUID driverId) { acquirable.put(driverId, false); }

        int timesUnlocked(UUID driverId) {
            LockHandle h = locks.get("lock:driver:" + driverId);
            return h == null ? 0 : h.unlocked;
        }

        int getLockCallsFor(UUID driverId) {
            return getLockCalls.getOrDefault(driverId, 0);
        }

        RedissonClient proxy() {
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    (p, method, args) -> {
                        if ("getLock".equals(method.getName())
                                && args != null && args.length == 1 && args[0] instanceof String name) {
                            UUID id = UUID.fromString(name.substring("lock:driver:".length()));
                            getLockCalls.merge(id, 1, Integer::sum);
                            LockHandle h = locks.computeIfAbsent(name,
                                    n -> new LockHandle(acquirable.getOrDefault(id, true)));
                            return rLockProxy(h);
                        }
                        throw new UnsupportedOperationException(
                                "RedissonClient." + method.getName() + " not stubbed");
                    });
        }

        private static RLock rLockProxy(LockHandle handle) {
            return (RLock) Proxy.newProxyInstance(
                    RLock.class.getClassLoader(),
                    new Class<?>[]{RLock.class},
                    (p, method, args) -> switch (method.getName()) {
                        case "tryLock" -> {
                            if (args != null && args.length == 3) {
                                if (handle.acquirable) {
                                    handle.held = true;
                                    yield Boolean.TRUE;
                                }
                                yield Boolean.FALSE;
                            }
                            throw new UnsupportedOperationException("tryLock arity " + (args == null ? 0 : args.length));
                        }
                        case "isHeldByCurrentThread" -> handle.held;
                        case "unlock" -> {
                            handle.held = false;
                            handle.unlocked++;
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(
                                "RLock." + method.getName() + " not stubbed");
                    });
        }

        private static final class LockHandle {
            final boolean acquirable;
            boolean held;
            int unlocked;

            LockHandle(boolean acquirable) { this.acquirable = acquirable; }
        }
    }

    /** Clock we can advance between operations for budget-exit assertions. */
    private static final class MutableClock extends Clock {
        private long millis = 0L;

        void advanceMs(long delta) { millis += delta; }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }
}
