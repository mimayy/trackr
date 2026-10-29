package com.uniace.trackr.util;

/** Distance maths for geofencing. */
public final class GeoUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoUtil() {
    }

    /**
     * Great-circle distance in metres between two positions.
     *
     * <p>The haversine formula is accurate to well under a metre over the
     * distances a geofence cares about, and needs no external library.
     */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Whether a reading falls inside a geofence.
     *
     * <p>The reading's own accuracy is added to the radius: a position reported
     * as "within 30 m of here" that lands 110 m from a 100 m fence really might
     * be inside it, and refusing it would punish the employee for their phone's
     * GPS rather than for where they are standing.
     */
    public static boolean withinGeofence(double distanceMeters, double radiusMeters, Double accuracyMeters) {
        double tolerance = accuracyMeters == null ? 0 : Math.max(0, accuracyMeters);
        return distanceMeters <= radiusMeters + tolerance;
    }

    /** Rounds to one decimal so stored distances stay readable. */
    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
