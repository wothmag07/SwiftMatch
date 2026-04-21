package com.swiftmatch.api.ride;

import com.swiftmatch.common.ride.Coord;
import com.swiftmatch.common.ride.RideRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Separate bean for the {@link RideService} tx boundaries. Kept distinct so Spring's
 * @Transactional proxy is honored (self-invocation from RideService would silently
 * bypass it).
 */
@Component
public class RideRegistrar {

    private static final Logger log = LoggerFactory.getLogger(RideRegistrar.class);

    private final RideRepository rideRepository;
    private final RideRequestPublisher requestPublisher;

    public RideRegistrar(RideRepository rideRepository,
                         RideRequestPublisher requestPublisher) {
        this.rideRepository = rideRepository;
        this.requestPublisher = requestPublisher;
    }

    @Transactional
    public RideEntity createPending(UUID riderId, Coord pickup, Coord dropoff) {
        RideEntity ride = new RideEntity(
                UUID.randomUUID(),
                riderId,
                pickup.lat(), pickup.lng(),
                dropoff.lat(), dropoff.lng()
        );
        rideRepository.save(ride);
        requestPublisher.publish(new RideRequestEvent(
                ride.getId(), riderId, pickup, dropoff, ride.getRequestedAt()
        ));
        return ride;
    }

    @Transactional
    public RideEntity assign(UUID rideId, UUID driverId, Instant assignedAt) {
        // Redis status is already ON_TRIP (set inside the matcher's Redisson critical section).
        // This UPDATE is the authoritative DB write; the partial unique index is the last-line
        // invariant (the partial unique index).
        RideEntity ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalStateException("Ride vanished mid-match: " + rideId));
        ride.assignTo(driverId, assignedAt);
        try {
            rideRepository.saveAndFlush(ride);
        } catch (DataIntegrityViolationException e) {
            log.error("Invariant tripped: driver {} already ON_TRIP; ride {} rolled back",
                    driverId, rideId, e);
            throw e;
        }
        return ride;
    }

    @Transactional
    public void markNoDriverFound(UUID rideId) {
        rideRepository.findById(rideId).ifPresent(RideEntity::markNoDriverFound);
    }
}
