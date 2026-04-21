package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.KafkaTopicsConfig;
import com.swiftmatch.common.ride.RideRequestEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget publish to {@code ride.request.v1} (key = riderId).
 * No consumer in this branch — future stream-service broadcasts ride-created
 * from it. Intentionally non-blocking; a Kafka outage shouldn't fail the POST.
 */
@Component
public class RideRequestPublisher {

    private final KafkaTemplate<String, Object> template;

    public RideRequestPublisher(KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    public void publish(RideRequestEvent event) {
        template.send(KafkaTopicsConfig.RIDE_REQUEST_TOPIC, event.riderId().toString(), event);
    }
}
