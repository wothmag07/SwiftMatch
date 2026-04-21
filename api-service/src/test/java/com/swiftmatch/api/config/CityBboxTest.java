package com.swiftmatch.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CityBboxTest {

    private final CityBbox sf = new CityBbox(37.70, -122.52, 37.82, -122.36);

    @Test
    void contains_point_inside_bbox() {
        assertThat(sf.contains(37.7749, -122.4194)).isTrue();
    }

    @Test
    void rejects_point_north_of_bbox() {
        assertThat(sf.contains(37.90, -122.4194)).isFalse();
    }

    @Test
    void rejects_point_east_of_bbox() {
        assertThat(sf.contains(37.7749, -122.20)).isFalse();
    }

    @Test
    void accepts_boundary_corners_inclusively() {
        assertThat(sf.contains(37.70, -122.52)).isTrue();
        assertThat(sf.contains(37.82, -122.36)).isTrue();
    }

    @Test
    void rejects_antipode() {
        assertThat(sf.contains(0.0, 0.0)).isFalse();
    }
}
