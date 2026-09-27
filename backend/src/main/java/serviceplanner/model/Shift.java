package serviceplanner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One working day for one worker. When {@code lunch} is true (the default), the plan reserves a
 * lunch allowance that the worker takes whenever they like during the shift.
 */
public record Shift(String workerId, LocalDate date, LocalTime start, LocalTime end, Boolean lunch) {
    public Shift {
        if (lunch == null) {
            lunch = true;
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Shift for " + workerId + " on " + date + " ends before it starts");
        }
    }

    public LocalDateTime startsAt() {
        return date.atTime(start);
    }

    public LocalDateTime endsAt() {
        return date.atTime(end);
    }

    public String id() {
        return workerId + "@" + date;
    }
}
