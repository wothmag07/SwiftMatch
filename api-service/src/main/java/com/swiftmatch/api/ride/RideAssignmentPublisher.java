package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.KafkaTopicsConfig;
import com.swiftmatch.common.ride.RideAssignmentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to {@code ride.assignment.v1} (key = rideId) once the matcher has won the
 * Redisson lock and the rides row is UPDATEd to ASSIGNED. Downstream consumers
 * (simulator-service, stream-service) pick it up from their respective consumer groups.
 */
@Component
public class RideAssignmentPublisher {

    private final KafkaTemplate<String, Object> template;

    public RideAssignmentPublisher(KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    public void publish(RideAssignmentEvent event) {
        template.send(KafkaTopicsConfig.RIDE_ASSIGNMENT_TOPIC, event.rideId().toString(), event);
    }
}
