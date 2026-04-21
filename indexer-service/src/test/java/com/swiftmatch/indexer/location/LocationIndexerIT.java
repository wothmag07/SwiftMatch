package com.swiftmatch.indexer.location;

import com.swiftmatch.common.location.LocationEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Produces a {@link LocationEvent} against the live Kafka broker and asserts
 * that the indexer-service's listener updates Redis GEO and Postgres
 * {@code location_history} within 5 s. Requires compose stack up + topic
 * provisioned by api-service (run it once first, or use auto-create).
 *
 * <p>Opt-in via {@code RUN_IT=true}.
 */
@SpringBootTest
@ActiveProfiles("it")
@EnabledIfEnvironmentVariable(named = "RUN_IT", matches = "true")
class LocationIndexerIT {

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @BeforeEach
    void cleanSlate() {
        jdbc.update("DELETE FROM location_history");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void event_updates_redis_geo_and_inserts_history_row() {
        UUID driverId = UUID.randomUUID();
        Instant recordedAt = Instant.parse("2026-04-20T12:00:00Z");
        LocationEvent event = new LocationEvent(driverId, 37.7749, -122.4194, recordedAt, Instant.now());

        KafkaTemplate<String, LocationEvent> template = newTemplate();
        try {
            template.send("driver.location.v1", driverId.toString(), event);
            template.flush();
        } finally {
            template.destroy();
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Point> positions = redis.opsForGeo().position("drivers:active", driverId.toString());
            assertThat(positions).hasSize(1);
            assertThat(positions.get(0).getX()).isEqualTo(-122.4194, org.assertj.core.data.Offset.offset(1e-4));
            assertThat(positions.get(0).getY()).isEqualTo(37.7749, org.assertj.core.data.Offset.offset(1e-4));

            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM location_history WHERE driver_id = ?",
                    Integer.class, driverId);
            assertThat(count).isEqualTo(1);
        });
    }

    private KafkaTemplate<String, LocationEvent> newTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ACKS_CONFIG, "all");
        ProducerFactory<String, LocationEvent> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}
