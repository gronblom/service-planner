package serviceplanner.travel;

import serviceplanner.model.GeoPoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps every leg in memory. Coordinates are rounded to {@code decimals} places before lookup, so
 * points a few metres apart share an entry (4 decimals is about 11 m north-south). Two points that
 * round to the same cell are treated as the same place and cost nothing to drive between.
 */
public class CachingTravelTimes implements TravelTimes {
    public static final int DEFAULT_DECIMALS = 4;

    private record Key(long fromLat, long fromLon, long toLat, long toLon) {
    }

    private final Map<Key, Leg> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final TravelTimes delegate;
    private final double scale;

    public CachingTravelTimes(TravelTimes delegate) {
        this(delegate, DEFAULT_DECIMALS);
    }

    public CachingTravelTimes(TravelTimes delegate, int decimals) {
        this.delegate = delegate;
        this.scale = Math.pow(10, decimals);
    }

    @Override
    public Leg between(GeoPoint from, GeoPoint to) {
        Key key = new Key(round(from.lat()), round(from.lon()), round(to.lat()), round(to.lon()));
        if (key.fromLat() == key.toLat() && key.fromLon() == key.toLon()) {
            return Leg.ZERO;
        }
        Leg cached = cache.get(key);
        if (cached != null) {
            hits.incrementAndGet();
            return cached;
        }
        misses.incrementAndGet();
        return cache.computeIfAbsent(key, k -> delegate.between(from, to));
    }

    private long round(double degrees) {
        return Math.round(degrees * scale);
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public int size() {
        return cache.size();
    }
}
