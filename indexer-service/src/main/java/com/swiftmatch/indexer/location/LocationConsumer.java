package com.swiftmatch.indexer.location;

import com.swiftmatch.common.location.LocationEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code driver.location.v1} in consumer group {@code indexer}.
 * Side effects: Redis GEO + heartbeat refresh, and a batched insert to
 * {@code location_history}.
 *
 * <p>Ack fires after Redis success; Postgres flush is asynchronous (see
 * {@link LocationHistoryBuffer}).
 */
@Component
public class LocationConsumer {

    private final LocationRedisWriter redisWriter;
    private final LocationHistoryBuffer historyBuffer;

    public LocationConsumer(LocationRedisWriter redisWriter, LocationHistoryBuffer historyBuffer) {
        this.redisWriter = redisWriter;
        this.historyBuffer = historyBuffer;
    }

    @KafkaListener(topics = "driver.location.v1", groupId = "indexer")
    public void onEvent(LocationEvent event, Acknowledgment ack) {
        redisWriter.index(event);
        historyBuffer.enqueue(event);
        ack.acknowledge();
    }
}
