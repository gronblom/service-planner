package serviceplanner.model;

import java.util.Set;

/**
 * A service technician and their car. Skills are free-form strings ("electrical", "gas", "lift"),
 * matched exactly against the skills a task requires.
 */
public record Worker(String id, String name, GeoPoint home, Set<String> skills) {
    public Worker {
        skills = skills == null ? Set.of() : Set.copyOf(skills);
    }
}
