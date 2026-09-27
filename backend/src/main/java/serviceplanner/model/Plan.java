package serviceplanner.model;

import java.time.LocalDateTime;
import java.util.List;

/** The planner's answer: one route per shift, plus the tasks nobody could do. */
public record Plan(List<ShiftRoute> routes, List<UnassignedTask> unassigned) {

    public record ShiftRoute(Shift shift, Worker worker, LocalDateTime departs, LocalDateTime returns,
                             int lunchMinutes, List<Stop> stops, long drivingSeconds, double drivingMeters) {
        /** Working time left in the shift after driving, tasks and lunch. */
        public long slackMinutes() {
            long shiftMinutes = java.time.Duration.between(shift.startsAt(), shift.endsAt()).toMinutes();
            long busyMinutes = java.time.Duration.between(departs, returns).toMinutes();
            return shiftMinutes - busyMinutes - lunchMinutes;
        }
    }

    public record Stop(Task task, LocalDateTime arrives, LocalDateTime starts, LocalDateTime ends) {
    }

    public record UnassignedTask(Task task, String reason) {
    }
}
