package com.swiftmatch.api.location;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftmatch.common.location.LocationEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the location ingestion controller against the live compose stack
 * (Kafka on localhost:9094). Asserts 202 + message lands on driver.location.v1.
 *
 * <p>Opt-in via {@code RUN_IT=true}; requires {@code docker compose up -d kafka}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@EnabledIfEnvironmentVariable(named = "RUN_IT", matches = "true")
class LocationIngestionIT {

    @Autowired
    MockMvc mockMvc;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void valid_location_returns_202_and_publishes_to_kafka() throws Exception {
        UUID driverId = UUID.randomUUID();

        try (Consumer<String, String> consumer = newConsumer("it-ingestion-" + driverId)) {
            consumer.subscribe(List.of("driver.location.v1"));
            // prime the consumer so it joins the group before we send
            consumer.poll(Duration.ofMillis(500));

            mockMvc.perform(post("/v1/drivers/{id}/location", driverId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"lat":37.7749,"lng":-122.4194,"recordedAt":"2026-04-20T12:00:00Z"}
                                    """))
                    .andExpect(status().isAccepted());

            AtomicReference<ConsumerRecord<String, String>> hit = new AtomicReference<>();
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (driverId.toString().equals(record.key())) {
                        hit.set(record);
                        return true;
                    }
                }
                return false;
            });

            ConsumerRecord<String, String> record = hit.get();
            assertThat(record).isNotNull();
            LocationEvent event = json.readValue(record.value(), LocationEvent.class);
            assertThat(event.driverId()).isEqualTo(driverId);
            assertThat(event.lat()).isEqualTo(37.7749);
            assertThat(event.lng()).isEqualTo(-122.4194);
        }
    }

    @Test
    void out_of_bbox_is_accepted_per_srs_loc_9() throws Exception {
        UUID driverId = UUID.randomUUID();
        mockMvc.perform(post("/v1/drivers/{id}/location", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":0.0,"lng":0.0,"recordedAt":"2026-04-20T12:00:05Z"}
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void invalid_lat_returns_400_problem_json() throws Exception {
        UUID driverId = UUID.randomUUID();
        mockMvc.perform(post("/v1/drivers/{id}/location", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":200.0,"lng":-122.4194,"recordedAt":"2026-04-20T12:00:10Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("validation")));
    }

    @Test
    void malformed_body_returns_400_problem_json() throws Exception {
        UUID driverId = UUID.randomUUID();
        mockMvc.perform(post("/v1/drivers/{id}/location", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":\"banana\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("validation")));
    }

    private Consumer<String, String> newConsumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
