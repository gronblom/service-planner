package serviceplanner.solver;

import org.junit.jupiter.api.Test;
import serviceplanner.model.GeoPoint;
import serviceplanner.model.Plan;
import serviceplanner.model.PlanRequest;
import serviceplanner.model.Shift;
import serviceplanner.model.Task;
import serviceplanner.model.Worker;
import serviceplanner.travel.CachingTravelTimes;
import serviceplanner.travel.StraightLineTravelTimes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final GeoPoint DEPOT = new GeoPoint(52.37, 4.90);

    private final Planner planner = new Planner(new CachingTravelTimes(new StraightLineTravelTimes()), 500);

    private static Shift shift(String worker, LocalDate date, Boolean lunch) {
        return new Shift(worker, date, LocalTime.of(8, 0), LocalTime.of(16, 0), lunch);
    }

    private static Task task(String id, GeoPoint where, int minutes, LocalDateTime deadline, String... skills) {
        return new Task(id, id, where, minutes, deadline, Set.of(skills));
    }

    private static GeoPoint near(int i) {
        return new GeoPoint(52.37 + (i % 5) * 0.004, 4.90 + (i / 5) * 0.006);
    }

    @Test
    void tasksGoOnlyToWorkersWithTheSkills() {
        List<Worker> workers = List.of(
                new Worker("elec", "Elec", DEPOT, Set.of("electrical")),
                new Worker("gas", "Gas", DEPOT, Set.of("electrical", "gas")));
        List<Task> tasks = IntStream.range(0, 6)
                .mapToObj(i -> task("g" + i, near(i), 30, null, "gas"))
                .toList();

        Plan plan = planner.plan(new PlanRequest(workers,
                List.of(shift("elec", MONDAY, true), shift("gas", MONDAY, true)), tasks, 30));

        assertTrue(plan.unassigned().isEmpty(), plan.unassigned().toString());
        for (Plan.ShiftRoute route : plan.routes()) {
            if (route.worker().id().equals("elec")) {
                assertTrue(route.stops().isEmpty(), "electrician got gas work: " + route.stops());
            }
        }
    }

    @Test
    void unknownSkillIsReportedAsUnassigned() {
        Plan plan = planner.plan(new PlanRequest(
                List.of(new Worker("w", "W", DEPOT, Set.of("electrical"))),
                List.of(shift("w", MONDAY, true)),
                List.of(task("crane-job", near(1), 60, null, "crane")), 30));

        assertEquals(1, plan.unassigned().size());
        assertTrue(plan.unassigned().get(0).reason().contains("crane"));
    }

    @Test
    void everyAssignedTaskFinishesBeforeItsDeadline() {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            LocalDate due = MONDAY.plusDays(i % 3);
            tasks.add(task("t" + i, near(i), 45, due.atTime(i % 2 == 0 ? 12 : 16, 0)));
        }
        List<Shift> shifts = IntStream.range(0, 3).mapToObj(d -> shift("w", MONDAY.plusDays(d), true)).toList();

        Plan plan = planner.plan(new PlanRequest(List.of(new Worker("w", "W", DEPOT, Set.of())), shifts, tasks, 30));

        int stops = 0;
        for (Plan.ShiftRoute route : plan.routes()) {
            for (Plan.Stop stop : route.stops()) {
                assertFalse(stop.ends().isAfter(stop.task().deadline()), stop.toString());
                assertFalse(stop.starts().isBefore(route.shift().startsAt()), stop.toString());
                stops++;
            }
            assertFalse(route.returns().isAfter(route.shift().endsAt().minusMinutes(route.lunchMinutes())), route.toString());
        }
        assertEquals(20, stops + plan.unassigned().size());
        assertTrue(stops >= 15, "only " + stops + " of 20 tasks planned");
    }

    @Test
    void lunchAllowanceTakesHalfAnHourFromTheShift() {
        // Sixteen 30-minute tasks at the worker's home fill an 8-hour shift exactly.
        List<Task> tasks = IntStream.range(0, 16).mapToObj(i -> task("t" + i, DEPOT, 30, null)).toList();
        List<Worker> workers = List.of(new Worker("w", "W", DEPOT, Set.of()));

        Plan withLunch = planner.plan(new PlanRequest(workers, List.of(shift("w", MONDAY, true)), tasks, 30));
        Plan withoutLunch = planner.plan(new PlanRequest(workers, List.of(shift("w", MONDAY, false)), tasks, 30));

        assertEquals(1, withLunch.unassigned().size());
        assertEquals(15, withLunch.routes().get(0).stops().size());
        assertEquals(0, withLunch.routes().get(0).slackMinutes());
        assertTrue(withoutLunch.unassigned().isEmpty());
        assertEquals(16, withoutLunch.routes().get(0).stops().size());
    }

    @Test
    void urgentTaskLandsOnTheFirstDay() {
        List<Shift> shifts = List.of(shift("w", MONDAY, true), shift("w", MONDAY.plusDays(1), true));
        List<Task> tasks = List.of(
                task("urgent", near(3), 60, MONDAY.atTime(12, 0)),
                task("later", near(7), 60, MONDAY.plusDays(1).atTime(16, 0)));

        Plan plan = planner.plan(new PlanRequest(List.of(new Worker("w", "W", DEPOT, Set.of())), shifts, tasks, 30));

        Plan.ShiftRoute monday = plan.routes().stream().filter(r -> r.shift().date().equals(MONDAY)).findFirst().orElseThrow();
        assertTrue(monday.stops().stream().anyMatch(s -> s.task().id().equals("urgent")));
        assertTrue(plan.unassigned().isEmpty());
    }

    @Test
    void deadlineBeforeTheFirstShiftIsUnassigned() {
        Plan plan = planner.plan(new PlanRequest(
                List.of(new Worker("w", "W", DEPOT, Set.of())),
                List.of(shift("w", MONDAY, true)),
                List.of(task("late", near(1), 30, MONDAY.minusDays(1).atTime(12, 0))), 30));

        assertEquals(1, plan.unassigned().size());
        assertTrue(plan.unassigned().get(0).reason().contains("before the first shift"));
    }
}
