package serviceplanner.travel;

import org.junit.jupiter.api.Test;
import serviceplanner.model.GeoPoint;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachingTravelTimesTest {
    private final AtomicInteger calls = new AtomicInteger();
    private final CachingTravelTimes cache = new CachingTravelTimes((from, to) -> {
        calls.incrementAndGet();
        return new Leg(600, 5000);
    });

    @Test
    void asksOncePerLeg() {
        GeoPoint a = new GeoPoint(52.3700, 4.9000);
        GeoPoint b = new GeoPoint(52.3800, 4.9100);

        cache.between(a, b);
        cache.between(a, b);

        assertEquals(1, calls.get());
        assertEquals(1, cache.hits());
    }

    @Test
    void pointsAFewMetresApartShareAnEntry() {
        GeoPoint b = new GeoPoint(52.3800, 4.9100);

        cache.between(new GeoPoint(52.37001, 4.90002), b);
        cache.between(new GeoPoint(52.36998, 4.89997), b);

        assertEquals(1, calls.get());
    }

    @Test
    void directionsAreCachedSeparately() {
        GeoPoint a = new GeoPoint(52.3700, 4.9000);
        GeoPoint b = new GeoPoint(52.3800, 4.9100);

        cache.between(a, b);
        cache.between(b, a);

        assertEquals(2, calls.get());
    }

    @Test
    void samePlaceCostsNothing() {
        GeoPoint a = new GeoPoint(52.3700, 4.9000);

        assertEquals(Leg.ZERO, cache.between(a, new GeoPoint(52.37002, 4.90001)));
        assertEquals(0, calls.get());
    }
}
