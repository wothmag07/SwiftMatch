package com.swiftmatch.indexer.location;

import com.swiftmatch.common.location.LocationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a java.lang.reflect.Proxy rather than Mockito because Mockito's inline
 * mock maker cannot instrument JdbcTemplate on Java 25 + Byte Buddy current.
 * We only care about one method on {@link JdbcOperations}; the rest throw.
 */
class LocationHistoryBufferTest {

    private BatchRecorder recorder;
    private LocationHistoryBuffer buffer;

    @BeforeEach
    void setUp() {
        recorder = new BatchRecorder();
        JdbcOperations proxy = (JdbcOperations) Proxy.newProxyInstance(
                JdbcOperations.class.getClassLoader(),
                new Class<?>[]{JdbcOperations.class},
                (p, method, args) -> {
                    if ("batchUpdate".equals(method.getName()) && args != null && args.length == 4
                            && args[3] instanceof ParameterizedPreparedStatementSetter) {
                        @SuppressWarnings("unchecked")
                        Collection<LocationEvent> batch = (Collection<LocationEvent>) args[1];
                        recorder.record(batch);
                        return new int[][]{{batch.size()}};
                    }
                    throw new UnsupportedOperationException(
                            "Unexpected call on JdbcOperations proxy: " + method.getName());
                });
        buffer = new LocationHistoryBuffer(proxy, 3);
    }

    @Test
    void enqueue_below_threshold_does_not_flush() {
        buffer.enqueue(event());
        buffer.enqueue(event());

        assertThat(recorder.calls).isEmpty();
        assertThat(buffer.queueSize()).isEqualTo(2);
    }

    @Test
    void enqueue_at_threshold_flushes_synchronously() {
        buffer.enqueue(event());
        buffer.enqueue(event());
        buffer.enqueue(event());

        assertThat(recorder.calls).hasSize(1);
        assertThat(recorder.calls.get(0)).hasSize(3);
        assertThat(buffer.queueSize()).isZero();
    }

    @Test
    void scheduled_flush_drains_remainder() {
        buffer.enqueue(event());
        buffer.enqueue(event());

        buffer.flush();

        assertThat(recorder.calls).hasSize(1);
        assertThat(recorder.calls.get(0)).hasSize(2);
        assertThat(buffer.queueSize()).isZero();
    }

    @Test
    void flush_on_empty_queue_is_noop() {
        buffer.flush();
        assertThat(recorder.calls).isEmpty();
    }

    private static LocationEvent event() {
        Instant now = Instant.parse("2026-04-20T12:00:00Z");
        return new LocationEvent(UUID.randomUUID(), 37.77, -122.41, now, now);
    }

    private static final class BatchRecorder {
        final List<List<LocationEvent>> calls = new ArrayList<>();

        void record(Collection<LocationEvent> batch) {
            calls.add(new ArrayList<>(batch));
        }
    }
}
