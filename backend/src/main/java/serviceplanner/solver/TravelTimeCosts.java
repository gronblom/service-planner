package serviceplanner.solver;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import serviceplanner.model.GeoPoint;
import serviceplanner.travel.TravelTimes;

/**
 * Feeds our travel times to jsprit. The cost of a leg is its driving time in seconds, so the
 * solver minimises total driving time.
 */
class TravelTimeCosts extends AbstractForwardVehicleRoutingTransportCosts {
    private final TravelTimes travelTimes;

    TravelTimeCosts(TravelTimes travelTimes) {
        this.travelTimes = travelTimes;
    }

    @Override
    public double getTransportTime(Location from, Location to, double departureTime, Driver driver, Vehicle vehicle) {
        return travelTimes.between(point(from), point(to)).seconds();
    }

    @Override
    public double getTransportCost(Location from, Location to, double departureTime, Driver driver, Vehicle vehicle) {
        return getTransportTime(from, to, departureTime, driver, vehicle);
    }

    @Override
    public double getDistance(Location from, Location to, double departureTime, Vehicle vehicle) {
        return travelTimes.between(point(from), point(to)).meters();
    }

    /** Locations carry x = longitude, y = latitude. */
    static GeoPoint point(Location location) {
        return new GeoPoint(location.getCoordinate().getY(), location.getCoordinate().getX());
    }
}
