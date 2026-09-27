# Service Planner

Plans service technicians' working days: which worker does which task, in what order, with
deadlines, required skills and a lunch break taken into account. Routing uses
[jsprit](https://github.com/graphhopper/jsprit) with travel times from
[GraphHopper](https://github.com/graphhopper/graphhopper).

- `backend/`: Java 27 + Maven. See [backend/README.md](backend/README.md).
- `frontend/`: TypeScript web UI (not started yet).
