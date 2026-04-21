package com.swiftmatch.api.ride;

import com.swiftmatch.common.ride.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface RideRepository extends JpaRepository<RideEntity, UUID> {

    boolean existsByRiderIdAndStatusIn(UUID riderId, Collection<RideStatus> statuses);
}
