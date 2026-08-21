# Graph Workspace Batch I Performance Design

**Status:** Approved by the user on 2026-08-21. The user delegated the scope decision after independent Frontier review; this revision preserves the canonical Task 37/38 allowlists and makes the public observation boundary executable.

## Goal

Implement Graph Workspace Tasks 37 and 38 sequentially. Task 37 adds deterministic generated workloads, a production-component performance diagnostic, exact fixture outputs, and regression tripwires. Task 38 consumes the Task 37 ledger, calibrates only measured bottlenecks, and records a machine-auditable strict-target report.

Task 37 is fully green and committed before any Task 38 source file is changed. The Task 37 commit contains exactly its six listed paths. The Task 38 commit contains exactly its seven listed paths. The design document and executable Batch I plan are separate planning commits and are not part of either implementation allowlist.

## Scope decision and production boundary

The canonical allowlists do not include package-private seams in `ProjectionBatcher`, `GraphUpdateCoordinator`, or `GraphPainter`. The diagnostic therefore measures a deterministic composition of existing public production components rather than claiming direct control of coordinator debounce, coordinator stale-generation discard, canvas stale-state rejection, or private painter methods.

The measured public composition is:

1. deterministic `WorkspaceDocument` and `MapSnapshot` input creation;
2. `ProjectionEngine.project(ProjectionInput)`;
3. `ProjectionDiff.between(previous, current)`;
4. the public GraphStream-backed layout abstraction through `GraphStreamLayoutFactory.create(LayoutCalibration)` and `LayoutEngine`;
5. `GraphGeometryEngine.computeHulls`, `MapTierCorrection.apply`, and `LabelPlacementEngine.place`;
6. immutable `CanvasState.of` creation;
7. `GraphCanvas.setCanvasState` completed on the real Swing EDT; and
8. explicit inherited public `GraphCanvas.paint(Graphics)` on a deterministic `BufferedImage` surface.

The first-frame composition uses the same operation order as production: `LayoutWorker.submit(request)` (not `step`) creates the first raw frame, the worker performs its production raw-hull and map-correction work, the diagnostic recomputes a hull from the corrected positions, `LabelPlacementEngine.place` decorates it, `CanvasState.of` creates immutable state, and `GraphCanvas.setCanvasState` publishes it. The direct probe separately calls `LayoutEngine.apply(request)`, then `LayoutEngine.step()` for the force sample, and closes that engine on the same owner thread. Every direct engine operation, including `apply`, `step`, and `close`, stays on that one thread because the GraphStream implementation enforces owner-thread affinity.

`AcceptedBatch` is constructed directly with the diagnostic `NanoClock`. Its timestamp anchors the component-composition first-frame metric; it does not measure `ProjectionBatcher` debounce or `GraphUpdateCoordinator` queue latency. Snapshot construction is complete before `AcceptedBatch` creation and is measured separately. The diagnostic records this boundary in every ledger and in the final report.

`GraphCanvas` has no injectable executor. Both tests and the real diagnostic use the real Swing EDT. Setup is performed once through `SwingUtilities.invokeAndWait`; each state swap is invoked from the diagnostic thread through the production `GraphCanvas.setCanvasState`, whose completion means its internal EDT runnable has assigned the state. The wrapper waits at most five seconds for each canvas operation and the Java entry point has a ten-minute process deadline; timeout or interruption fails the run and triggers executor, worker, and EDT cleanup. Repaint is measured inside an EDT task around one explicit `canvas.paint(image.getGraphics())` call, with a five-second wait. It does not infer asynchronous repaint-manager completion.

The fixed rendering setup is: `java.awt.headless=true`, `CanvasTheme.LIGHT`, `GraphPaintState.empty()`, `GraphViewport.of(0.0, 0.0, 1.0)`, `showArrowheads=true`, `dimUnrelated=false`, component size `1024 x 768`, `BufferedImage.TYPE_INT_ARGB`, image size `1024 x 768`, and a fresh image per sample. Text metrics use `Font("Dialog", Font.PLAIN, 12)` and a fixed non-antialiased `FontRenderContext`; no UI-manager theme or system font is used. The paint invariant requires a finite call and a non-background pixel checksum.

Failure and lifecycle probes are limited to public observable behavior. A generation mismatch at `LayoutSettleLoop.start`, a post-close `LayoutWorker.submit`/`step` rejection, a missing stage sample, a non-finite duration, or a bounded EDT timeout is recorded as a diagnostic failure and causes a nonzero exit. The generation-mismatch probe invokes `start` with mismatched batch/projection generations and verifies rejection before any worker is queued. `GraphCanvas.setCanvasState` itself accepts any generation, so no canvas stale-state rejection is asserted. Coordinator-internal stale discard and closed-publication behavior are outside this diagnostic's claim and are not presented as measured. No failed operation is silently omitted from a sample count.

## Task 37 allowlist

Task 37 modifies exactly:

- `freeplane_plugin_graph/build.gradle`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/NearestRankPercentile.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`

The Gradle task is named `graphPerformanceDiagnostic`. It is a `JavaExec` task that depends on `testClasses`, uses `sourceSets.test.runtimeClasspath`, invokes `org.freeplane.plugin.graph.performance.GraphWorkspacePerformanceDiagnostic`, sets `java.awt.headless=true`, `user.language=en`, and `user.country=US`, and writes deterministic ordered outputs below `build/graph-performance/`. It declares that directory as an output, overwrites the three named fixture files and `performance-ledger.csv` on each run, propagates the presence of `-PgraphStrictPerformance` as a boolean system property, and exits nonzero for any invariant, timing, serialization, lifecycle, or cleanup failure. A diagnostic deadline of ten minutes is enforced by the Java entry point. Normal CI does not enable the strict property.

## Generated workload model

`GeneratedWorkspace` owns one fixed seed, `20260810`, and a fixed canonical identity namespace. It does not call `GraphWorkspaceStore.create()` because that factory generates a random workspace ID. The generated workspace UUID, map UUIDs, persisted node IDs, relationship IDs, relationship sequences, map registration order, tree child order, connector occurrence order, pin coordinates, and all random choices are derived from the fixed seed and serialized in canonical order. Re-running generation produces byte-identical `.fpg` files and equal projection inputs.

Every visible snapshot source is a persisted node or a deterministic internal enclosure. A map's connector source is exactly `SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of(nodeId)))`; its target is exactly `NodeReference.of(mapId, PersistedNodeId.of(nodeId))`. Both IDs are present as persisted nodes in that map's `NodeSnapshot` tree, the source and target are distinct, and the connector occurrence is unique for that source. The generator constructs `ConnectorDescriptor` and `ConnectorSnapshot` through their public factories, calls `MapSnapshot.withConnectors`, and asserts the API-valid endpoint contract before projection. It then asserts that projection retains exactly the expected count of native `EdgeContributor`s by summing contributors whose `connectorDescriptor()` is present. This count is distinct from the projected edge-key count.

The reference scenario is exactly:

- 20 active, available map registrations;
- 2,000 projected visible nodes;
- 1,200 projected enclosures/anchors;
- 5,000 unique projected edge keys and 5,000 contributors, split into 3,500 native same-map connector contributors and 1,500 cross-map workspace-relationship contributors;
- 2,000 direct containment links from enclosures to visible nodes;
- 1,180 hierarchy links from parent enclosures to child enclosures;
- 3,200 particles (`2,000 + 1,200`); and
- 8,180 springs (`5,000 + 2,000 + 1,180`).

The 3,200 source tree elements consist of 20 map-root enclosures, 1,180 non-root enclosures, and 2,000 visible leaf nodes. Every non-root source element has exactly one parent, so the direct-containment and hierarchy counts are independently asserted and sum to 3,180 structural links. Each enclosure owns at least one direct visible node; this prevents projection's single-child enclosure compression from changing the required 1,200 projected-enclosure count.

Workspace XML cannot encode same-map relationships: `GraphRelationshipRecord` requires endpoints from distinct maps. Therefore the 1,500 cross-map contributors are stored in the `.fpg` document with persisted `NodeReference` endpoints from different registered maps, while the 3,500 same-map contributors are supplied through deterministic in-memory `MapSnapshot.withConnectors(...)` data for the same registered maps. The fixture is the persisted workspace input; the diagnostic ledger separately records the native snapshot input and post-projection contributor counts. A canonical unordered endpoint-pair allocator skips used pairs, so no native or relationship contributor is grouped with another and the 5,000 projected edge keys are retained exactly.

The required fixture files and scenario mapping are exactly:

- `build/graph-performance/two-map.fpg` -> `two-map`;
- `build/graph-performance/three-map.fpg` -> `three-map-clustered`; and
- `build/graph-performance/reference-2000-5000.fpg` -> `reference-2000-5000`.

The first two are deterministic fixture-backed structural scenarios and the third is the reference fixture. `skewed-reference`, `one-pinned-map`, and `two-pinned-maps` are deterministic in-memory variants. No additional fixture files are emitted.

## Exact variant matrix

All variants use the fixed seed namespace, canonical map order, and exact warm-up/measured counts below. Percentiles use measured samples only.

| Scenario | Maps | Nodes | Enclosures | Native | Cross-map | Containment | Hierarchy | Warm-up | Measured |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `two-map` | 2 | 120 | 40 | 120 | 120 | 120 | 38 | 20 | 30 |
| `three-map-clustered` | 3 | 180 | 60 | 180 | 180 | 180 | 57 | 20 | 30 |
| `reference-2000-5000` | 20 | 2,000 | 1,200 | 3,500 | 1,500 | 2,000 | 1,180 | 400 | 300 |
| `skewed-reference` | 20 | 2,000 | 1,200 | 3,500 | 1,500 | 2,000 | 1,180 | 20 | 30 |
| `one-pinned-map` | 3 | 180 | 60 | 180 | 180 | 180 | 57 | 20 | 30 |
| `two-pinned-maps` | 3 | 180 | 60 | 180 | 180 | 180 | 57 | 20 | 30 |

Map allocations are exact. `two-map` has 60 nodes and 20 enclosures per map; all 120 cross-map contributors use map pair (0,1), and native contributors are 60 per map. `three-map-clustered` has 60 nodes and 20 enclosures per map; native contributors are 60 per map, and cross-map contributors are 150 on pair (0,1) plus 30 on pair (1,2), with none on pair (0,2). `reference-2000-5000` has 100 nodes and 60 enclosures per map, 175 native contributors per map, and cross-map contributors allocated by repeatedly cycling the lexicographically ordered unordered map pairs (0,1), (0,2), ..., (18,19) until 1,500 unique pairs are emitted.

`skewed-reference` has node counts `[1600, 21, 21, ..., 21, 22]` and enclosure counts `[960, 12, 12, ..., 12, 24]`, where map 0 is first and maps 1 through 18 have the repeated value. Native contributors are allocated as `[2800, 37, 37, ..., 37, 34]`, with maps 1 through 18 repeated; cross-map contributors cycle only through pairs (0,1) through (0,19). This makes map 0 own exactly 80 percent of projected nodes and 80 percent of projected enclosures while preserving unique endpoint pairs.

Both pinned variants use three maps with 60 nodes and 20 enclosures per map, native contributors 60 per map, and cross-map contributors 120 on pair (0,1) plus 60 on pair (1,2). `one-pinned-map` pins `m00-n0001` to `(0.0, 0.0)` and expects zero rigid-map conflicts. `two-pinned-maps` pins `m00-n0001` and `m01-n0001` both to `(0.0, 0.0)`, uses the fixed tree layout that places their root hulls overlapping before correction, and expects exactly one rigid conflict for map pair (0,1), with both pin IDs listed in the conflict. The generator asserts the pre-correction overlap and the diagnostic asserts the post-worker conflict result; a different result is a generation failure, not an accepted variant.

## Measurement ledger

`PerformanceMeasurements` stores nonnegative integer nanoseconds keyed by these exact stages:

- `snapshot`: start before copying/building the deterministic `ProjectionInput`, end after the input is validated;
- `projection`: start immediately before `ProjectionEngine.project(input)`, end after the immutable `GraphProjection` is returned;
- `diff`: start immediately before `ProjectionDiff.between(previous, current)`, end after the immutable diff is returned;
- `mutation`: start immediately before direct owner-thread `LayoutEngine.apply(request)`, end after the raw frame is returned;
- `force`: start immediately before direct owner-thread `LayoutEngine.step()`, end after the raw frame and immutable positions are returned;
- `correction`: start immediately before public `MapTierCorrection.apply(projection, rawPositions, rawHull)`, end after corrected positions and conflicts are returned;
- `hull`: one public `GraphGeometryEngine.computeHulls(projection, correctedPositions)` call, timed from invocation through return;
- `label`: one public `LabelPlacementEngine.place(projection, correctedHull, metrics)` call, timed from invocation through return;
- `full-worker`: one public `LayoutWorker.submit(request)` followed by bounded completion of its returned `CompletionStage` for the first frame; it uses `submit`, never `step`;
- `edt-swap`: time around a non-EDT call to `GraphCanvas.setCanvasState(state)`, ending only after its internal EDT assignment returns;
- `repaint`: time inside the real EDT task around one `GraphCanvas.paint(Graphics)` call on the fixed image; and
- `accepted-batch-first-frame`: start at `AcceptedBatch.acceptedAtNanos()`, then run projection, diff, `LayoutWorker.submit(request)` and bounded completion, recompute the corrected-position hull, place labels, create `CanvasState`, and complete `GraphCanvas.setCanvasState`; end after that setter returns.

The raw direct sequence is `apply -> raw hull -> correction -> corrected-position hull -> labels`. The worker sequence is `submit -> worker raw hull/correction -> corrected-position hull -> labels`. A direct `step` is never used as the first-frame sample. Each direct sample creates one `LayoutEngine` before its timed `apply`, uses that engine for `apply` and `step`, and closes it on the same owner thread after the timed stages. Each scenario owns one public `LayoutWorker`; its warm-up and measured samples submit fresh generation/request values in canonical order, so every sample measures the first frame for that accepted batch while preserving the production worker lifecycle. The worker is closed after the scenario, and every completion has a five-second bound; the process has a ten-minute bound.

Snapshot construction occurs before accepted-batch creation and is not included in `accepted-batch-first-frame`. The real diagnostic clock is a relative clock: it captures one `System.nanoTime()` origin before scenario generation and returns checked `System.nanoTime() - origin` values, rejecting subtraction overflow, negative readings, and backward movement. The test `NanoClock` is injectable for pure timing and ledger tests and follows the same contract. The accepted timestamp and all stage endpoints use the same clock object. The diagnostic reports elapsed durations, not absolute clock values.

The authoritative output is `build/graph-performance/performance-ledger.csv`. It is ordered by the variant-table order and the stage-list order above, and has exactly this header:

`scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass`

One row exists for every scenario/stage pair. `failureCount` includes lifecycle and operation failures assigned to that scenario; `discardCount` includes only explicitly classified public sample discards and must be zero for the direct composition. A failed operation is never converted into a duration or silently omitted: it increments `failureCount`, leaves the successful sample count short, and makes the diagnostic fail. For a successful run, `warmupCount` and `measuredCount` equal the exact variant-table counts. A threshold of `-1` means that the row is diagnostic-only and has no timing gate; gated rows contain the exact applicable normal or strict ceiling. Task 38 copies the unmodified strict run to `build/graph-performance/strict-baseline-ledger.csv`; the final run remains `performance-ledger.csv`. Ledger values are machine-dependent durations, but row order, names, counts, thresholds, and fixture bytes are deterministic.

## Percentiles and gates

`NearestRankPercentile.of(sortedNanos, p)` requires a nonempty ascending collection of nonnegative nanoseconds and `0.0 < p <= 1.0`; it does not mutate the input. It uses the exact index `ceil(p * N) - 1`, with checked conversion and multiplication. Invalid `p`, empty input, unsorted input, negative values, and overflow fail explicitly. Every percentile is computed from the `measuredCount` samples for that same scenario and stage; warm-up samples never enter a percentile.

Strict targets for the `reference-2000-5000` scenario are:

- force p95 <= 50 ms;
- full-worker p95 <= 100 ms;
- accepted-batch-first-frame p95 <= 150 ms and p99 <= 300 ms; and
- EDT swap p95 <= 2 ms.

Normal CI uses exact five-times-strict ceilings for those four metrics: 250 ms, 500 ms, 750 ms/1,500 ms, and 10 ms respectively. The same normal thresholds apply to the four gated metrics for every variant, using that variant's measured sample domain. Structural invariants, exact counts, deterministic serialization, finite positions, pin/conflict assertions, cleanup, and lifecycle probes always apply. Strict mode additionally fails on the strict ceilings for the reference scenario. The remaining stage rows are diagnostic-only but still require complete finite samples.

## Task 38 calibration allowlist

Task 38 modifies exactly:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`

Task 38 first runs `graphPerformanceDiagnostic` with `-PgraphStrictPerformance` at the unmodified Task 37 commit and copies the resulting `performance-ledger.csv` byte-for-byte to `strict-baseline-ledger.csv`. Only a measured bottleneck may justify a source change. Calibration preserves O(N+E) rebuild behavior unless the ledger proves it insufficient, SpringBox quality `0.10`, ordered force multipliers, aggregate cross-map displacement cap `0.005`, rigid pinned-map correction, deterministic seeds, immutable publication, and the GraphStream package boundary. It does not add a user-selected performance tier or a second execution path.

The report at `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md` must include: exact commit IDs; Java, Gradle, OS, CPU, RAM, JVM options, headless/font settings, and timestamp; commands; fixture SHA-256 values; the exact CSV header and artifact paths; scenario counts and failure/discard counts; clock definition and all stage boundaries; every scenario/stage row's warm-up count, measured count, p50, p95, p99, maximum, normal threshold, strict threshold, and pass/fail; baseline-versus-final results; exact calibration diffs and measured rationale; invariant and lifecycle results; worker/EDT cleanup and thread results; and residual machine-specific hardware risk. The report must state that coordinator debounce, coordinator stale discard, canvas stale-state rejection, and private painter internals are outside the Task 37 observation boundary.

The canonical backlog line that says Task 38 consumes `Task36 exact diagnostics` is treated as a documentation typo for this Batch I execution; the executable Batch I plan pins the dependency to Task 37 without modifying unrelated backlog files.

## Verification

Task 37 follows red-first TDD: add the Gradle/task and tripwire tests, run the prescribed missing-class failure, implement the six-file allowlist, run focused tests and the diagnostic, verify exact fixture names/counts/checksums and the authoritative ledger schema, run the graph-plugin suite, and commit only the six paths.

Task 38 runs the strict baseline before source edits, calibrates only measured bottlenecks, reruns focused tests and strict diagnostics, validates both ledger artifacts and the complete report, runs the graph-plugin suite and required compatibility checks, and commits only the seven paths. Each implementation commit starts with `2026-08-10-graph-workspace:`.
