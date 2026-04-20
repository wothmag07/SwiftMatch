package com.swiftmatch.api.driver;

import com.swiftmatch.api.error.DriverOnTripException;
import com.swiftmatch.common.driver.DriverStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverStatusTransitionsTest {

    private final UUID driverId = UUID.randomUUID();

    @Test
    void online_from_offline_becomes_available() {
        assertThat(DriverStatusTransitions.applyOnline(DriverStatus.OFFLINE))
                .isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    void online_from_available_stays_available_idempotent() {
        assertThat(DriverStatusTransitions.applyOnline(DriverStatus.AVAILABLE))
                .isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    void online_from_on_trip_stays_on_trip_noop() {
        assertThat(DriverStatusTransitions.applyOnline(DriverStatus.ON_TRIP))
                .isEqualTo(DriverStatus.ON_TRIP);
    }

    @Test
    void offline_from_available_becomes_offline() {
        assertThat(DriverStatusTransitions.applyOffline(DriverStatus.AVAILABLE, driverId))
                .isEqualTo(DriverStatus.OFFLINE);
    }

    @Test
    void offline_from_offline_stays_offline_idempotent() {
        assertThat(DriverStatusTransitions.applyOffline(DriverStatus.OFFLINE, driverId))
                .isEqualTo(DriverStatus.OFFLINE);
    }

    @Test
    void offline_from_on_trip_is_rejected() {
        assertThatThrownBy(() -> DriverStatusTransitions.applyOffline(DriverStatus.ON_TRIP, driverId))
                .isInstanceOf(DriverOnTripException.class)
                .extracting("driverId").isEqualTo(driverId);
    }
}
