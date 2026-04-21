package com.swiftmatch.api.ride;

import com.swiftmatch.api.driver.DriverEntity;
import com.swiftmatch.api.driver.DriverRepository;
import com.swiftmatch.api.rider.RiderService;
import com.swiftmatch.common.ride.AssignedDriver;
import com.swiftmatch.common.ride.Coord;
import com.swiftmatch.common.ride.CreateRideRequest;
import com.swiftmatch.common.ride.RideAssignmentEvent;
import com.swiftmatch.common.ride.RideAssignmentResponse;
import com.swiftmatch.common.ride.RideStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates ride creation and matching.
 *
 * <p>Uses {@link RideRegistrar} for DB writes so each tx commits independently —
 * PENDING insert commits before the matcher loop, and ASSIGNED / NO_DRIVER_FOUND
 * each run in their own short tx. The matcher itself is in-memory + Redis, no tx.
 */
@Service
public class RideService {

    private static final List<RideStatus> ACTIVE_STATUSES =
            List.of(RideStatus.PENDING, RideStatus.ASSIGNED, RideStatus.ON_TRIP);

    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;
    private final RiderService riderService;
    private final RideBboxValidator bboxValidator;
    private final RideMatcher matcher;
    private final RideRegistrar registrar;
    private final EtaEstimator etaEstimator;
    private final RideAssignmentPublisher assignmentPublisher;
    private final Clock clock;

    public RideService(RideRepository rideRepository,
                       DriverRepository driverRepository,
                       RiderService riderService,
                       RideBboxValidator bboxValidator,
                       RideMatcher matcher,
                       RideRegistrar registrar,
                       EtaEstimator etaEstimator,
                       RideAssignmentPublisher assignmentPublisher,
                       Clock clock) {
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.riderService = riderService;
        this.bboxValidator = bboxValidator;
        this.matcher = matcher;
        this.registrar = registrar;
        this.etaEstimator = etaEstimator;
        this.assignmentPublisher = assignmentPublisher;
        this.clock = clock;
    }

    public RideAssignmentResponse request(CreateRideRequest request) {
        bboxValidator.validate(request.pickup(), request.dropoff());
        riderService.requireExisting(request.riderId());
        if (rideRepository.existsByRiderIdAndStatusIn(request.riderId(), ACTIVE_STATUSES)) {
            throw new RiderHasActiveRideException(request.riderId());
        }

        RideEntity ride = registrar.createPending(request.riderId(), request.pickup(), request.dropoff());

        Optional<RideMatcher.Match> hit = matcher.match(request.pickup());
        if (hit.isEmpty()) {
            registrar.markNoDriverFound(ride.getId());
            throw new NoDriverFoundException(ride.getId());
        }

        return buildResponse(ride.getId(), ride.getRequestedAt(), request, hit.get());
    }

    private RideAssignmentResponse buildResponse(UUID rideId, Instant requestedAt,
                                                 CreateRideRequest request, RideMatcher.Match hit) {
        Instant assignedAt = clock.instant();
        registrar.assign(rideId, hit.driverId(), assignedAt);

        DriverEntity driver = driverRepository.findById(hit.driverId())
                .orElseThrow(() -> new IllegalStateException(
                        "Driver " + hit.driverId() + " in Redis GEO but absent from Postgres"));

        int etaSeconds = etaEstimator.secondsFromGreatCircle(
                new Coord(hit.lat(), hit.lng()), request.pickup());

        assignmentPublisher.publish(new RideAssignmentEvent(
                rideId, request.riderId(), hit.driverId(),
                request.pickup(), request.dropoff(), assignedAt
        ));

        return new RideAssignmentResponse(
                rideId,
                RideStatus.ASSIGNED,
                new AssignedDriver(driver.getId(), driver.getName(), driver.getVehicle(),
                        hit.lat(), hit.lng(), etaSeconds),
                requestedAt,
                assignedAt
        );
    }
}
