package com.uniace.trackr.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Distance and geofence maths. */
class GeoUtilTest {

    private static final double MANILA_OFFICE_LAT = 14.554729;
    private static final double MANILA_OFFICE_LON = 121.024445;

    @Nested
    @DisplayName("distance")
    class Distance {

        @Test
        @DisplayName("is zero at the same point")
        void samePoint() {
            assertThat(GeoUtil.distanceMeters(
                    MANILA_OFFICE_LAT, MANILA_OFFICE_LON, MANILA_OFFICE_LAT, MANILA_OFFICE_LON))
                    .isCloseTo(0, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("is accurate over the short distances a geofence cares about")
        void shortDistance() {
            // 0.001 degrees of latitude is about 111 m anywhere on Earth.
            double metres = GeoUtil.distanceMeters(
                    MANILA_OFFICE_LAT, MANILA_OFFICE_LON, MANILA_OFFICE_LAT + 0.001, MANILA_OFFICE_LON);

            assertThat(metres).isBetween(110.0, 112.0);
        }

        @Test
        @DisplayName("is symmetric")
        void symmetric() {
            double forward = GeoUtil.distanceMeters(14.5547, 121.0244, 14.4795, 121.0198);
            double backward = GeoUtil.distanceMeters(14.4795, 121.0198, 14.5547, 121.0244);

            assertThat(forward).isCloseTo(backward, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("spans the two branches sensibly")
        void betweenBranches() {
            // Manila to Cebu is roughly 570 km.
            assertThat(GeoUtil.distanceMeters(14.5547, 121.0244, 10.3157, 123.8854))
                    .isBetween(560_000.0, 590_000.0);
        }
    }

    @Nested
    @DisplayName("geofence checks")
    class Geofence {

        @Test
        @DisplayName("a position well inside the radius is inside")
        void clearlyInside() {
            assertThat(GeoUtil.withinGeofence(40, 100, 10.0)).isTrue();
        }

        @Test
        @DisplayName("a position well outside the radius is outside")
        void clearlyOutside() {
            assertThat(GeoUtil.withinGeofence(400, 100, 10.0)).isFalse();
        }

        @Test
        @DisplayName("the reading's own accuracy widens the fence rather than punishing the employee")
        void accuracyIsTolerated() {
            // 110 m from a 100 m fence, but the phone only claims to be accurate
            // to 30 m - the employee really might be standing inside it.
            assertThat(GeoUtil.withinGeofence(110, 100, 30.0)).isTrue();

            // With a confident reading, the same distance is genuinely outside.
            assertThat(GeoUtil.withinGeofence(110, 100, 5.0)).isFalse();
        }

        @Test
        @DisplayName("a missing accuracy reading applies no tolerance")
        void nullAccuracy() {
            assertThat(GeoUtil.withinGeofence(99, 100, null)).isTrue();
            assertThat(GeoUtil.withinGeofence(101, 100, null)).isFalse();
        }

        @Test
        @DisplayName("exactly on the boundary counts as inside")
        void onBoundary() {
            assertThat(GeoUtil.withinGeofence(100, 100, 0.0)).isTrue();
        }

        @Test
        @DisplayName("a nonsensical negative accuracy is ignored, not subtracted")
        void negativeAccuracy() {
            assertThat(GeoUtil.withinGeofence(100, 100, -50.0)).isTrue();
        }
    }

    @Test
    @DisplayName("stored distances are rounded to one decimal")
    void rounding() {
        assertThat(GeoUtil.round1(123.456)).isEqualTo(123.5);
        assertThat(GeoUtil.round1(0.04)).isEqualTo(0.0);
    }
}
