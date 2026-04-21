package com.swiftmatch.api.ride;

import com.swiftmatch.api.config.CityBbox;
import com.swiftmatch.common.ride.Coord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideBboxValidatorTest {

    private final CityBbox sf = new CityBbox(37.70, -122.52, 37.82, -122.36);
    private final RideBboxValidator validator = new RideBboxValidator(sf);

    private static final Coord INSIDE = new Coord(37.7749, -122.4194);
    private static final Coord OUTSIDE = new Coord(0.0, 0.0);

    @Test
    void both_inside_passes() {
        assertThatCode(() -> validator.validate(INSIDE, INSIDE)).doesNotThrowAnyException();
    }

    @Test
    void outside_pickup_names_pickup_field() {
        assertThatThrownBy(() -> validator.validate(OUTSIDE, INSIDE))
                .isInstanceOf(OutOfServiceAreaException.class)
                .satisfies(ex -> {
                    OutOfServiceAreaException e = (OutOfServiceAreaException) ex;
                    assert "pickup".equals(e.getField());
                });
    }

    @Test
    void outside_dropoff_names_dropoff_field() {
        assertThatThrownBy(() -> validator.validate(INSIDE, OUTSIDE))
                .isInstanceOf(OutOfServiceAreaException.class)
                .satisfies(ex -> {
                    OutOfServiceAreaException e = (OutOfServiceAreaException) ex;
                    assert "dropoff".equals(e.getField());
                });
    }
}
