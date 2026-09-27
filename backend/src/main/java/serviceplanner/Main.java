package serviceplanner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import serviceplanner.model.Plan;
import serviceplanner.model.PlanRequest;
import serviceplanner.solver.Planner;
import serviceplanner.travel.CachingTravelTimes;
import serviceplanner.travel.GraphHopperTravelTimes;
import serviceplanner.travel.StraightLineTravelTimes;
import serviceplanner.travel.TravelTimes;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Runs the planner on a JSON file and prints the plan.
 *
 * <pre>
 * mvn -q compile exec:java -Dexec.args="examples/three-days.json"
 * GRAPHHOPPER_URL=http://localhost:8989 mvn -q compile exec:java
 * </pre>
 */
public class Main {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static void main(String[] args) throws IOException {
        Path input = Path.of(args.length > 0 ? args[0] : "examples/three-days.json");
        PlanRequest request = objectMapper().readValue(input.toFile(), PlanRequest.class);

        String graphHopperUrl = System.getenv("GRAPHHOPPER_URL");
        TravelTimes source = graphHopperUrl == null || graphHopperUrl.isBlank()
                ? new StraightLineTravelTimes()
                : new GraphHopperTravelTimes(graphHopperUrl, System.getenv().getOrDefault("GRAPHHOPPER_PROFILE", "car"));
        CachingTravelTimes travelTimes = new CachingTravelTimes(source);
        int iterations = Integer.parseInt(System.getenv().getOrDefault("ITERATIONS", String.valueOf(Planner.DEFAULT_ITERATIONS)));

        long started = System.nanoTime();
        Plan plan = new Planner(travelTimes, iterations).plan(request);
        long millis = (System.nanoTime() - started) / 1_000_000;

        print(plan);
        System.out.printf(Locale.ROOT, "%nTravel times: %s, %d legs cached, %d lookups answered from cache.%n",
                graphHopperUrl == null || graphHopperUrl.isBlank() ? "straight-line estimate" : "GraphHopper at " + graphHopperUrl,
                travelTimes.size(), travelTimes.hits());
        System.out.printf(Locale.ROOT, "Solved %d tasks on %d shifts in %d ms (%d iterations).%n",
                request.tasks().size(), request.shifts().size(), millis, iterations);
    }

    public static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    static void print(Plan plan) {
        for (Plan.ShiftRoute route : plan.routes()) {
            System.out.printf(Locale.ROOT, "%n%s  %s  %s-%s  (lunch %d min, driving %d min / %.1f km, slack %d min)%n",
                    route.shift().date(), route.worker().name(), HH_MM.format(route.shift().start()),
                    HH_MM.format(route.shift().end()), route.lunchMinutes(), route.drivingSeconds() / 60,
                    route.drivingMeters() / 1000, route.slackMinutes());
            if (route.stops().isEmpty()) {
                System.out.println("  no tasks");
                continue;
            }
            System.out.printf("  %s  leave home%n", HH_MM.format(route.departs()));
            for (Plan.Stop stop : route.stops()) {
                System.out.printf(Locale.ROOT, "  %s-%s  %-4s %-20s due %s %s%n",
                        HH_MM.format(stop.starts()), HH_MM.format(stop.ends()), stop.task().id(), stop.task().name(),
                        stop.task().deadline() == null ? "-" : stop.task().deadline().toLocalDate() + " " + HH_MM.format(stop.task().deadline()),
                        stop.task().requiredSkills().isEmpty() ? "" : stop.task().requiredSkills());
            }
            System.out.printf("  %s  back home%n", HH_MM.format(route.returns()));
        }
        if (!plan.unassigned().isEmpty()) {
            System.out.println("\nUnassigned:");
            for (Plan.UnassignedTask u : plan.unassigned()) {
                System.out.printf("  %-4s %-20s %s%n", u.task().id(), u.task().name(), u.reason());
            }
        }
    }
}
