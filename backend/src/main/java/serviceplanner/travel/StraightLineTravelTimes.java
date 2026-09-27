package serviceplanner.travel;

import serviceplanner.model.GeoPoint;

/**
 * Offline estimate: great-circle distance times a detour factor, driven at a constant speed.
 * Good enough for experiments without a GraphHopper server.
 */
public class StraightLineTravelTimes implements TravelTimes {
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final double metersPerSecond;
    private final double detourFactor;

    public StraightLineTravelTimes() {
        this(40, 1.3);
    }

    public StraightLineTravelTimes(double kmPerHour, double detourFactor) {
        this.metersPerSecond = kmPerHour / 3.6;
        this.detourFactor = detourFactor;
    }

    @Override
    public Leg between(GeoPoint from, GeoPoint to) {
        double meters = haversineMeters(from, to) * detourFactor;
        return new Leg(meters / metersPerSecond, meters);
    }

    static double haversineMeters(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }
}
