# Graph Workspace Batch I Performance Design

**Status:** Approved by the user on 2026-08-21. The user delegated the scope decision after independent Frontier review; this revision adopts the canonical Task 37/38 allowlists.

## Goal

Implement Graph Workspace Tasks 37 and 38 sequentially. Task 37 adds deterministic generated workloads, a production-component performance diagnostic, exact fixture outputs, and regression tripwires. Task 38 consumes the Task 37 ledger, calibrates only measured bottlenecks, and records a machine-auditable strict-target report.

Task 37 is fully green and committed before any Task 38 source file is changed. The Task 37 commit contains exactly its six listed paths. The Task 38 commit contains exactly its seven listed paths. The design document and executable Batch I plan are separate planning commits and are not part of either implementation allowlist.

## Scope decision and production boundary

The canonical allowlists do not include package-private seams in `ProjectionBatcher`, `GraphUpdateCoordinator`, or `GraphPainter`. The diagnostic therefore measures a deterministic composition of existing public production components rather than claiming direct control of coordinator debounce, coordinator stale-generation discard, or private painter methods.

The measured public composition is:

1. deterministic `WorkspaceDocument` and `MapSnapshot` input creation;
2. `ProjectionEngine.project(ProjectionInput)`;
3. `ProjectionDiff.between(previous, next)`;
4. the public GraphStream-free layout boundary through `GraphStreamLayoutFactory.create(LayoutCalibration)` and `LayoutEngine`;
5. `GraphGeometryEngine.computeHulls`, `MapTierCorrection.apply`, and `LabelPlacementEngine.place`;
6. immutable `CanvasState.of` creation;
7. `GraphCanvas.setCanvasState` completed on the EDT; and
8. explicit `GraphCanvas.paint(Graphics)` on a deterministic `BufferedImage` surface.

`AcceptedBatch` is constructed directly with the injected diagnostic clock. Its timestamp anchors the component-composition first-frame metric; it does not claim to measure `ProjectionBatcher` debounce or `GraphUpdateCoordinator` queue latency. The diagnostic records this boundary in its output and report. EDT swap ends when the `setCanvasState` operation's EDT runnable has completed, using an injected/controlled EDT executor in tests and `SwingUtilities.invokeAndWait` in the real diagnostic. Repaint means the duration of an explicit `GraphCanvas.paint` call, not asynchronous repaint-manager queue latency.

Failure, stale-generation, and closed-publication cases are tested at the observable component boundaries. A failed operation, missing stage sample, non-finite duration, rejected operation, or bounded EDT timeout is recorded as a diagnostic failure and causes a nonzero diagnostic exit; no sample is silently omitted. Coordinator-internal silent discard behavior is outside this diagnostic's claim and is not presented as measured.

## Task 37 allowlist

Task 37 modifies exactly:

- `freeplane_plugin_graph/build.gradle`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/NearestRankPercentile.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`

The Gradle task is named `graphPerformanceDiagnostic`. It is a `JavaExec` task that depends on `testClasses`, uses `sourceSets.test.runtimeClasspath`, invokes `org.freeplane.plugin.graph.performance.GraphWorkspacePerformanceDiagnostic`, and writes deterministic outputs below `build/graph-performance/`. It declares that directory as an output, overwrites the three named fixture files on each run, propagates the presence of `-PgraphStrictPerformance` as a boolean system property, and exits nonzero for any invariant, timing, serialization, or cleanup failure. A diagnostic deadline of ten minutes is enforced by the Java entry point. Normal CI does not enable the strict property.

## Generated workload model

`GeneratedWorkspace` owns one fixed seed, `20260810`, and a fixed canonical identity namespace. It does not call `GraphWorkspaceStore.create()` because that factory generates a random workspace ID. The generated workspace UUID, 20 map UUIDs, persisted node IDs, relationship IDs, relationship sequences, map registration order, tree child order, connector occurrence order, pin coordinates, and all random choices are derived from the fixed seed and serialized in canonical order. Re-running generation produces byte-identical `.fpg` files and equal projection inputs.

The reference scenario is exactly:

- 20 active, available map registrations;
- 2,000 projected visible nodes;
- 1,200 projected enclosures/anchors;
- 5,000 projected edges, with 3,500 native same-map connector contributors and 1,500 cross-map workspace-relationship contributors;
- 2,000 direct containment links;
- 1,180 enclosure hierarchy links;
- 3,200 particles (`2,000 + 1,200`); and
- 8,180 springs (`5,000 + 2,000 + 1,180`).

The generator asserts these counts after projection and before timing samples. Workspace XML cannot encode same-map relationships: `GraphRelationshipRecord` requires endpoints from distinct maps. Therefore the 1,500 cross-map contributors are stored in the `.fpg` document, while the 3,500 same-map contributors are supplied through deterministic in-memory `MapSnapshot.withConnectors(...)` data for the same registered maps. The output fixture is the persisted workspace input; the diagnostic ledger separately records the native snapshot input and post-projection counts. Every edge endpoint pair is generated uniquely after projection so contributor grouping cannot reduce the required 5,000 projected-edge count.

The required fixture files are exactly:

- `build/graph-performance/two-map.fpg`
- `build/graph-performance/three-map.fpg`
- `build/graph-performance/reference-2000-5000.fpg`

The reference fixture is the third file. The first two are deterministic small fixtures used for structural and stress checks; no additional fixture files are required or emitted.

## Variant matrix

All variants use the same fixed seed namespace and canonical ordering. Variants are generated in memory unless they are one of the three required fixture files.

| Scenario | Maps | Nodes | Enclosures | Edges | Special construction | Gate |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `two-map` | 2 | 120 | 40 | 240 | balanced native/cross-map mix | structural and normal tripwires |
| `three-map-clustered` | 3 | 180 | 60 | 360 | cross-map endpoints concentrated in two map pairs | structural and normal tripwires |
| `reference-2000-5000` | 20 | 2,000 | 1,200 | 5,000 | exact production reference counts above | strict and normal tripwires |
| `skewed-reference` | 20 | 2,000 | 1,200 | 5,000 | first map owns at least 80 percent of projected nodes; unique cross-map clusters | structural and normal tripwires |
| `one-pinned-map` | 3 | 180 | 60 | 360 | one map has at least one active pin at fixed finite coordinates | structural and normal tripwires |
| `two-pinned-maps` | 3 | 180 | 60 | 360 | two maps have active pins with an overlapping hull pair | structural and normal tripwires; conflict invariant |

The two pinned variants assert exact pinned coordinates, rigid-map correction, and the expected conflict/no-conflict result. The clustered and skewed variants assert deterministic ordering, complete key coverage, finite output, and no cross-map displacement above the fixed `0.005` aggregate cap. Only the reference scenario is judged against the strict production ceilings.

## Measurement ledger

`PerformanceMeasurements` stores nonnegative integer nanoseconds keyed by these exact stages:

- `snapshot`: deterministic input snapshot construction/copy;
- `projection`: `ProjectionEngine.project`;
- `diff`: `ProjectionDiff.between`;
- `mutation`: layout engine request application and graph synchronization;
- `force`: one layout-engine `step` including GraphStream compute and immutable position capture;
- `correction`: `MapTierCorrection.apply` using a previously measured hull;
- `hull`: `GraphGeometryEngine.computeHulls`;
- `label`: `LabelPlacementEngine.place`;
- `full-worker`: the public `LayoutWorker` request/step composition through its completed frame;
- `edt-swap`: completion of `GraphCanvas.setCanvasState` on the EDT;
- `repaint`: one explicit `GraphCanvas.paint(Graphics)` call; and
- `accepted-batch-first-frame`: elapsed component-composition time from `AcceptedBatch.acceptedAtNanos()` through projection, layout, geometry, labels, immutable `CanvasState` creation, and completed EDT canvas-state assignment.

The direct stage probes and the end-to-end public `LayoutWorker` probe are both retained: direct stages identify bottlenecks, while `full-worker` and `accepted-batch-first-frame` validate composition. A sample is one complete execution of the named stage for one generated scenario. The reference scenario performs 400 warm-up samples followed by 300 measured samples. Warm-up samples are excluded from percentiles but must satisfy structural invariants. Every measured stage must produce exactly 300 samples or the run fails.

The real diagnostic uses one monotonic `System.nanoTime` origin for accepted-batch creation and all end timestamps. The test clock is injectable and must reject backward movement, negative values, and duration overflow. Every timestamp pair is checked before subtraction. Queue wait and EDT execution are reported separately where the public operation permits the distinction; no asynchronous repaint completion is inferred.

## Percentiles and gates

`NearestRankPercentile.of(sortedNanos, p)` requires a nonempty ascending collection of nonnegative nanoseconds and `0.0 < p <= 1.0`; it does not mutate the input. It uses the exact index `ceil(p * N) - 1`, with a checked integer conversion. Invalid `p`, empty input, unsorted input, negative values, and overflow fail explicitly.

Strict targets for the reference scenario are:

- force p95 <= 50 ms;
- full-worker p95 <= 100 ms;
- accepted-batch-first-frame p95 <= 150 ms and p99 <= 300 ms; and
- EDT swap p95 <= 2 ms.

Normal CI uses exact five-times-strict ceilings for the same metrics: 250 ms, 500 ms, 750 ms/1,500 ms, and 10 ms respectively. Structural invariants and deterministic fixture checks always apply. Strict mode additionally fails on the strict ceilings. Variant scenarios use structural and normal tripwires only.

## Task 38 calibration allowlist

Task 38 modifies exactly:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`

Task 38 first runs `graphPerformanceDiagnostic` with `-PgraphStrictPerformance` and saves the unmodified baseline ledger. Only a measured bottleneck may justify a change. Calibration preserves O(N+E) rebuild behavior unless the ledger proves it insufficient, SpringBox quality `0.10`, ordered force multipliers, aggregate cross-map cap `0.005`, rigid pinned-map correction, deterministic seeds, immutable publication, and the GraphStream package boundary. It does not add a user-selected performance tier or a second execution path.

The report must include: exact commit IDs; Java, Gradle, OS, CPU, RAM, JVM options, headless/font settings, and timestamp; commands; fixture SHA-256 values; scenario counts and failure/discard counts; clock definition; every stage's sample count, p50, p95, p99, and maximum; normal and strict thresholds; baseline-versus-final results; exact calibration diffs and measured rationale; invariant results; cleanup/thread results; and residual machine-specific hardware risk. The report must state that coordinator debounce and private painter internals are outside the Task 37 observation boundary.

The canonical backlog line that says Task 38 consumes `Task36 exact diagnostics` is treated as a documentation typo for this Batch I execution; the executable Batch I plan pins the dependency to Task 37 without modifying unrelated backlog files.

## Verification

Task 37 follows red-first TDD: add the Gradle/task and tripwire tests, run the prescribed missing-class failure, implement the six-file allowlist, run focused tests and the diagnostic, verify exact fixture names/counts/checksums, run the graph-plugin suite, and commit only the six paths.

Task 38 runs the strict baseline before source edits, calibrates only measured bottlenecks, reruns focused tests and strict diagnostics, validates the complete report against the ledger, runs the graph-plugin suite and required compatibility checks, and commits only the seven paths. Each implementation commit starts with `2026-08-10-graph-workspace:`.
