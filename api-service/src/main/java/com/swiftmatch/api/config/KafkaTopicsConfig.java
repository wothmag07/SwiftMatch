package com.swiftmatch.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics the api-service produces to. Spring's KafkaAdmin picks these
 * up at startup and creates them if absent.
 *
 * <p>DLT topic intentionally omitted — dead-letter topic support is deferred.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final String DRIVER_LOCATION_TOPIC = "driver.location.v1";
    public static final String RIDE_REQUEST_TOPIC = "ride.request.v1";
    public static final String RIDE_ASSIGNMENT_TOPIC = "ride.assignment.v1";

    @Bean
    public NewTopic driverLocationV1() {
        return TopicBuilder.name(DRIVER_LOCATION_TOPIC)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rideRequestV1() {
        return TopicBuilder.name(RIDE_REQUEST_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rideAssignmentV1() {
        return TopicBuilder.name(RIDE_ASSIGNMENT_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
