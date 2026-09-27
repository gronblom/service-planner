package serviceplanner.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * A job at a location. It must be finished by {@code deadline} (null means no deadline) and can only
 * be done by a worker who has every skill in {@code requiredSkills}.
 */
public record Task(String id, String name, GeoPoint location, int durationMinutes,
                   LocalDateTime deadline, Set<String> requiredSkills) {
    public Task {
        requiredSkills = requiredSkills == null ? Set.of() : Set.copyOf(requiredSkills);
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("Task " + id + " has a negative duration");
        }
    }
}
