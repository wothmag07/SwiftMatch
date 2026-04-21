package com.swiftmatch.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics the api-service produces to. Spring's KafkaAdmin picks these
 * up at startup and creates them if absent. Partition counts per [SRS-§6.1].
 *
 * <p>DLT topic intentionally omitted: Amendment 001 §4.2 defers DLT to BL-2.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final String DRIVER_LOCATION_TOPIC = "driver.location.v1";

    @Bean
    public NewTopic driverLocationV1() {
        return TopicBuilder.name(DRIVER_LOCATION_TOPIC)
                .partitions(12)
                .replicas(1)
                .build();
    }
}
