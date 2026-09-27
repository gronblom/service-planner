package serviceplanner.solver;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import serviceplanner.model.GeoPoint;
import serviceplanner.model.Plan;
import serviceplanner.model.PlanRequest;
import serviceplanner.model.Shift;
import serviceplanner.model.Task;
import serviceplanner.model.Worker;
import serviceplanner.travel.Leg;
import serviceplanner.travel.TravelTimes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a {@link PlanRequest} into a jsprit problem and back.
 *
 * <p>Each shift becomes one jsprit vehicle that starts and ends at the worker's home and carries the
 * worker's skills. Times are seconds since midnight of the first shift's date, so a task can land
 * on any shift that finishes it before its deadline.
 *
 * <p>Lunch is an allowance, not a timed stop: a shift with lunch simply has {@code lunchMinutes}
 * less time for driving and tasks, and the worker takes the break whenever it suits them.
 */
public class Planner {
    public static final int DEFAULT_ITERATIONS = 2000;

    private final TravelTimes travelTimes;
    private final int iterations;

    public Planner(TravelTimes travelTimes) {
        this(travelTimes, DEFAULT_ITERATIONS);
    }

    public Planner(TravelTimes travelTimes, int iterations) {
        this.travelTimes = travelTimes;
        this.iterations = iterations;
    }

    public Plan plan(PlanRequest request) {
        Map<String, Worker> workers = request.workers().stream()
                .collect(Collectors.toMap(Worker::id, Function.identity()));
        List<Plan.UnassignedTask> unassigned = new ArrayList<>();
        if (request.shifts().isEmpty()) {
            request.tasks().forEach(t -> unassigned.add(new Plan.UnassignedTask(t, "no shifts to plan")));
            return new Plan(List.of(), unassigned);
        }

        LocalDateTime origin = request.shifts().stream()
                .map(s -> s.date().atStartOfDay())
                .min(Comparator.naturalOrder())
                .orElseThrow();
        VehicleType car = VehicleTypeImpl.Builder.newInstance("car").build();

        VehicleRoutingProblem.Builder problem = VehicleRoutingProblem.Builder.newInstance()
                .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE)
                .setRoutingCost(new TravelTimeCosts(travelTimes));

        Map<String, Shift> shiftsByVehicle = new HashMap<>();
        for (Shift shift : request.shifts()) {
            Worker worker = workers.get(shift.workerId());
            if (worker == null) {
                throw new IllegalArgumentException("Shift " + shift.id() + " refers to unknown worker " + shift.workerId());
            }
            int lunch = shift.lunch() ? request.lunchMinutes() : 0;
            double start = seconds(origin, shift.startsAt());
            double end = seconds(origin, shift.endsAt()) - lunch * 60.0;
            Location home = location("home:" + worker.id(), worker.home());
            problem.addVehicle(VehicleImpl.Builder.newInstance(shift.id())
                    .setType(car)
                    .setStartLocation(home)
                    .setEndLocation(home)
                    .setEarliestStart(start)
                    .setLatestArrival(Math.max(start, end))
                    .addAllSkills(worker.skills())
                    .build());
            shiftsByVehicle.put(shift.id(), shift);
        }

        double horizonEnd = request.shifts().stream()
                .mapToDouble(s -> seconds(origin, s.endsAt()))
                .max()
                .orElseThrow();
        Map<String, Task> tasksById = new HashMap<>();
        for (Task task : request.tasks()) {
            String reason = obviousReason(task, request, workers, origin);
            if (reason != null) {
                unassigned.add(new Plan.UnassignedTask(task, reason));
                continue;
            }
            double latestStart = task.deadline() == null
                    ? horizonEnd
                    : seconds(origin, task.deadline()) - task.durationMinutes() * 60.0;
            problem.addJob(Service.Builder.newInstance(task.id())
                    .setName(task.name())
                    .setLocation(location("task:" + task.id(), task.location()))
                    .setServiceTime(task.durationMinutes() * 60.0)
                    .setTimeWindow(TimeWindow.newInstance(0, latestStart))
                    .addAllRequiredSkills(task.requiredSkills())
                    .build());
            tasksById.put(task.id(), task);
        }

        VehicleRoutingProblemSolution solution = solve(problem.build());

        List<Plan.ShiftRoute> routes = new ArrayList<>();
        Set<String> routedShifts = new HashSet<>();
        for (VehicleRoute route : solution.getRoutes()) {
            Shift shift = shiftsByVehicle.get(route.getVehicle().getId());
            routes.add(toShiftRoute(route, shift, workers.get(shift.workerId()), request, origin, tasksById));
            routedShifts.add(shift.id());
        }
        for (Shift shift : request.shifts()) {
            if (!routedShifts.contains(shift.id())) {
                routes.add(new Plan.ShiftRoute(shift, workers.get(shift.workerId()), shift.startsAt(), shift.startsAt(),
                        shift.lunch() ? request.lunchMinutes() : 0, List.of(), 0, 0));
            }
        }
        routes.sort(Comparator.comparing((Plan.ShiftRoute r) -> r.shift().date()).thenComparing(r -> r.worker().id()));

        for (Job job : solution.getUnassignedJobs()) {
            Task task = tasksById.get(job.getId());
            unassigned.add(new Plan.UnassignedTask(task, "does not fit any shift before its deadline"));
        }
        return new Plan(routes, unassigned);
    }

    private VehicleRoutingProblemSolution solve(VehicleRoutingProblem vrp) {
        if (vrp.getJobs().isEmpty()) {
            return new VehicleRoutingProblemSolution(List.of(), 0);
        }
        VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(vrp).buildAlgorithm();
        algorithm.setMaxIterations(iterations);
        return Solutions.bestOf(algorithm.searchSolutions());
    }

    /** Reasons we can tell without solving: nobody has the skills, or the deadline has already passed. */
    private static String obviousReason(Task task, PlanRequest request, Map<String, Worker> workers, LocalDateTime origin) {
        boolean someoneSkilled = request.shifts().stream()
                .map(s -> workers.get(s.workerId()))
                .anyMatch(w -> w != null && w.skills().containsAll(task.requiredSkills()));
        if (!someoneSkilled) {
            return "no worker on shift has skills " + task.requiredSkills();
        }
        if (task.deadline() != null && seconds(origin, task.deadline()) - task.durationMinutes() * 60.0 < 0) {
            return "deadline " + task.deadline() + " is before the first shift";
        }
        return null;
    }

    private Plan.ShiftRoute toShiftRoute(VehicleRoute route, Shift shift, Worker worker, PlanRequest request,
                                         LocalDateTime origin, Map<String, Task> tasksById) {
        List<Plan.Stop> stops = new ArrayList<>();
        double drivingSeconds = 0;
        double drivingMeters = 0;
        TourActivity previous = route.getStart();
        List<TourActivity> visits = new ArrayList<>(route.getActivities());
        visits.add(route.getEnd());
        for (TourActivity activity : visits) {
            Leg leg = travelTimes.between(TravelTimeCosts.point(previous.getLocation()),
                    TravelTimeCosts.point(activity.getLocation()));
            drivingSeconds += leg.seconds();
            drivingMeters += leg.meters();
            if (activity instanceof TourActivity.JobActivity jobActivity) {
                Task task = tasksById.get(jobActivity.getJob().getId());
                double start = activity.getEndTime() - activity.getOperationTime();
                stops.add(new Plan.Stop(task, time(origin, activity.getArrTime()), time(origin, start),
                        time(origin, activity.getEndTime())));
            }
            previous = activity;
        }
        return new Plan.ShiftRoute(shift, worker, time(origin, route.getStart().getEndTime()),
                time(origin, route.getEnd().getArrTime()), shift.lunch() ? request.lunchMinutes() : 0,
                stops, Math.round(drivingSeconds), drivingMeters);
    }

    private static Location location(String id, GeoPoint point) {
        return Location.Builder.newInstance()
                .setId(id)
                .setCoordinate(Coordinate.newInstance(point.lon(), point.lat()))
                .build();
    }

    private static double seconds(LocalDateTime origin, LocalDateTime time) {
        return Duration.between(origin, time).getSeconds();
    }

    private static LocalDateTime time(LocalDateTime origin, double seconds) {
        return origin.plusSeconds(Math.round(seconds));
    }
}
