package com.swiftmatch.indexer.location;

import com.swiftmatch.common.location.LocationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Buffers {@link LocationEvent}s in memory and flushes them to
 * {@code location_history} either when the queue reaches {@code batchSize}
 * or every {@code batchIntervalMs} (whichever comes first).
 *
 * <p>By design the Kafka consumer acks before the buffer flushes; an indexer
 * crash can drop up to one in-flight batch. Acceptable in MVP: {@code
 * location_history} is not read by anything the demo relies on.
 */
@Component
public class LocationHistoryBuffer {

    private static final Logger log = LoggerFactory.getLogger(LocationHistoryBuffer.class);
    private static final String INSERT_SQL =
            "INSERT INTO location_history (driver_id, lat, lng, recorded_at) VALUES (?, ?, ?, ?)";

    private final ConcurrentLinkedQueue<LocationEvent> queue = new ConcurrentLinkedQueue<>();
    private final JdbcOperations jdbc;
    private final int batchSize;

    public LocationHistoryBuffer(JdbcOperations jdbc,
                                 @Value("${swiftmatch.history.batchSize:500}") int batchSize) {
        this.jdbc = jdbc;
        this.batchSize = batchSize;
    }

    public void enqueue(LocationEvent event) {
        queue.add(event);
        if (queue.size() >= batchSize) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${swiftmatch.history.batchIntervalMs:500}")
    public void flush() {
        List<LocationEvent> batch = drain();
        if (batch.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, event) -> {
                ps.setObject(1, event.driverId());
                ps.setDouble(2, event.lat());
                ps.setDouble(3, event.lng());
                ps.setTimestamp(4, Timestamp.from(event.recordedAt()));
            });
        } catch (RuntimeException e) {
            log.error("location_history batch flush failed size={} cause={}", batch.size(), e.toString());
        }
    }

    private List<LocationEvent> drain() {
        List<LocationEvent> drained = new ArrayList<>(Math.min(batchSize, queue.size()));
        for (int i = 0; i < batchSize; i++) {
            LocationEvent event = queue.poll();
            if (event == null) {
                break;
            }
            drained.add(event);
        }
        return drained;
    }

    int queueSize() {
        return queue.size();
    }
}
