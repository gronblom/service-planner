package serviceplanner.travel;

/** Driving time and distance between two points. */
public record Leg(double seconds, double meters) {
    public static final Leg ZERO = new Leg(0, 0);
}
