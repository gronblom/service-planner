# Backend

A first experiment with [jsprit](https://github.com/graphhopper/jsprit): it plans service tasks
onto workers' shifts and prints the result. There's no HTTP API yet.

## Run it

```sh
cd backend
mvn -q compile exec:java                                   # uses examples/three-days.json
mvn -q compile exec:java -Dexec.args="path/to/input.json"
```

This runs with straight-line travel estimates by default. To use real road times, point it at a
self-hosted GraphHopper server (for example the Docker image):

```sh
GRAPHHOPPER_URL=http://localhost:8989 GRAPHHOPPER_PROFILE=car mvn -q compile exec:java
```

`ITERATIONS` (default 2000) sets how long jsprit searches.

Tests: `mvn test`.

## Input

See `examples/three-days.json`.

- **workers**: `id`, `name`, `home` (`lat`/`lon`, where the car starts and ends), `skills` (any strings).
- **shifts**: `workerId`, `date`, `start`, `end`, and optionally `lunch` (default `true`).
- **tasks**: `id`, `name`, `location`, `durationMinutes`, `deadline` (the task must be *finished*
  by then; leave it out for no deadline) and `requiredSkills` (the worker needs all of them).
- **lunchMinutes**: length of the lunch allowance (default 30).

## How it maps onto jsprit

| Planner         | jsprit                                                                 |
|-----------------|------------------------------------------------------------------------|
| Shift           | A vehicle starting and ending at the worker's home, available `start`-`end` |
| Worker skills   | Vehicle skills                                                          |
| Task            | A service with a service time and a time window ending at `deadline - duration` |
| Required skills | Service required skills                                                 |
| Lunch           | Shortens the shift by `lunchMinutes`; the worker takes it whenever they like |

Times are seconds since midnight of the first shift, so a task can go on any shift that finishes it
before its deadline. jsprit minimises total driving time and leaves out tasks that can't fit;
those come back as unassigned, with a reason.

## Travel times

`CachingTravelTimes` keeps every leg in memory and asks the underlying source only once per leg.
Coordinates are rounded to 4 decimals (about 11 m north-south) first, so nearby points share a
cache entry. Open-source GraphHopper has no matrix endpoint, so `GraphHopperTravelTimes` calls
`/route` once per missing leg.
