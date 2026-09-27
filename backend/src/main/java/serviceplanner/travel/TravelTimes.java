package serviceplanner.travel;

import serviceplanner.model.GeoPoint;

/** Source of driving times between two points. Legs are directional: A to B may differ from B to A. */
public interface TravelTimes {
    Leg between(GeoPoint from, GeoPoint to);
}
