package serviceplanner.model;

import java.util.List;

/** Everything the planner needs: who works when, what needs doing, and how long lunch is. */
public record PlanRequest(List<Worker> workers, List<Shift> shifts, List<Task> tasks, Integer lunchMinutes) {
    public static final int DEFAULT_LUNCH_MINUTES = 30;

    public PlanRequest {
        workers = workers == null ? List.of() : List.copyOf(workers);
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        if (lunchMinutes == null) {
            lunchMinutes = DEFAULT_LUNCH_MINUTES;
        }
    }
}
