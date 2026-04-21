package com.swiftmatch.api.location;

import com.swiftmatch.api.config.KafkaTopicsConfig;
import com.swiftmatch.api.error.IngestionTimeoutException;
import com.swiftmatch.common.location.LocationEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes {@link LocationEvent}s to the {@code driver.location.v1} topic.
 * Blocks on the producer future for at most 200 ms (the ingestion timeout budget) — on timeout
 * or send failure the caller sees {@link IngestionTimeoutException}.
 */
@Component
public class LocationProducer {

    private static final long SEND_TIMEOUT_MS = 200L;

    private final KafkaTemplate<String, LocationEvent> template;

    public LocationProducer(KafkaTemplate<String, LocationEvent> template) {
        this.template = template;
    }

    public void publish(UUID driverId, LocationEvent event) {
        try {
            template.send(KafkaTopicsConfig.DRIVER_LOCATION_TOPIC, driverId.toString(), event)
                    .get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IngestionTimeoutException(driverId, e);
        } catch (ExecutionException e) {
            throw new IngestionTimeoutException(driverId, e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionTimeoutException(driverId, e);
        }
    }
}
