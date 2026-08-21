# Graph Workspace Batch I Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> The controller dispatches one fresh child at a time and reviews each task
> before the next task starts.

**Goal:** Implement backlog Task 37's generated graph performance diagnostic, then calibrate the measured production bottlenecks in backlog Task 38 until the strict reference ceilings pass.

**Architecture:** Task 37 stays in graph-plugin test sources and composes existing public projection, layout, geometry, canvas, and Swing EDT APIs. It generates valid persisted workspace documents plus in-memory native connector snapshots, records deterministic stage ledgers, and emits three exact fixtures. Task 38 consumes that ledger, changes only measured calibration/runtime files, preserves the existing immutable and deterministic contracts, and writes a machine-auditable report.

**Tech Stack:** Java 8 source/bytecode compatibility, Java 21 Zulu build JDK, Gradle, JUnit 4, AssertJ, GraphStream 1.3 behind the existing `LayoutEngine` boundary, AWT/Swing headless rendering, and the existing secure `WorkspaceXmlCodec`.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-i-performance` on branch `2026-08-10-graph-workspace-batch-i-performance`; do not edit the base checkout.
- Execute Task 1 completely, commit it, and verify its commit before changing any Task 2 source file.
- Build every Gradle command with `~/.sdkman/candidates/java/21.0.8-zulu/bin/java`; invoke `gradle`, never `gradlew`.
- Keep Java source and bytecode compatible with the repository's Java 8 target; do not use records, lambdas in APIs that require Java 9 types, `var`, `List.of`, `Path.of`, or other newer language/library conveniences.
- Follow red-green-refactor: add or extend the allowed test first, run the prescribed red command and confirm the expected failure, implement the smallest complete behavior, then run focused and module gates.
- Do not add a production API, a reflection-based production bypass, a second graph/layout implementation, or a new runtime dependency.
- Task 37 may modify exactly these six paths: `freeplane_plugin_graph/build.gradle`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/NearestRankPercentile.java`, `GeneratedWorkspace.java`, `PerformanceMeasurements.java`, `GraphWorkspacePerformanceDiagnostic.java`, and `PerformanceTripwiresShould.java`.
- Task 38 may modify exactly these seven paths: `LayoutCalibration.java`, `PerceptualIdlePolicy.java`, `GraphUpdateCoordinator.java`, `GraphStreamLayoutEngine.java`, `GraphGeometryEngine.java`, `GraphPainter.java`, and `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`.
- Stage names are exactly `snapshot`, `projection`, `diff`, `mutation`, `force`, `correction`, `hull`, `label`, `full-worker`, `edt-swap`, `repaint`, and `accepted-batch-first-frame`.
- The fixed diagnostic seed is `20260810`. The reference scenario is 20 active maps, 2,000 projected nodes, 1,200 projected enclosures, 5,000 unique projected edge keys and contributors split into 3,500 native same-map contributors and 1,500 cross-map workspace relationships, 2,000 direct containment links, 1,180 hierarchy links, 3,200 particles, and 8,180 springs.
- Reference sampling is 400 warm-up samples followed by 300 measured samples. Variant sampling is exactly the counts in Task 1's scenario table. Warm-up samples never enter a percentile.
- `NearestRankPercentile.of(sortedNanos, p)` uses `ceil(p * N) - 1`, requires a nonempty ascending collection of nonnegative nanoseconds and `0.0 < p <= 1.0`, and never mutates its input.
- The strict reference ceilings are force p95 <= 50 ms, full-worker p95 <= 100 ms, accepted-batch-first-frame p95 <= 150 ms and p99 <= 300 ms, and EDT-swap p95 <= 2 ms. Normal tripwires are exactly five times those ceilings.
- The diagnostic measures public production-component composition. It does not claim to observe `ProjectionBatcher` debounce, `GraphUpdateCoordinator` stale/closed publication discard, `GraphCanvas` stale-state rejection, or private `GraphPainter` methods.
- Same-map edges are valid only as `ConnectorSnapshot` data in `MapSnapshot.withConnectors`; persisted `GraphRelationshipRecord` endpoints must be distinct nodes from different maps. Connector source and target identities must be persisted nodes present in the same snapshot tree.
- The real diagnostic uses a relative monotonic `NanoClock` based on checked `System.nanoTime() - origin`; tests may inject a deterministic clock with the same nonnegative/nondecreasing contract. Swing tests and diagnostics use the real EDT with bounded five-second operation waits and deterministic headless rendering.
- Preserve deterministic seeds, immutable state publication, O(N+E) rebuild behavior, SpringBox quality `0.10`, ordered force multipliers, aggregate cross-map displacement cap `0.005`, rigid pinned-map correction, and the existing GraphStream package boundary during Task 2 calibration.
- Every implementation commit starts with `2026-08-10-graph-workspace:`. Before each implementation commit, assert an empty index, stage only the task allowlist, compare `git diff --cached --name-only` to that allowlist, run `git diff --check`, and verify the commit's names with `git show --name-only --format=`.
- Do not edit this plan after SDD initialization. The plan digest is pinned by the controller; a needed plan correction requires a new human-approved continuation plan.

## Task 1: Implement Backlog Task 37 Generated Performance Diagnostics

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/build.gradle:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/NearestRankPercentile.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`

**Interfaces:**

- Consumes the public production types `WorkspaceDocument`, `WorkspaceXmlCodec`, `WorkspaceMigrationRegistry`, `MapSnapshot`, `NodeSnapshot`, `ConnectorSnapshot`, `ConnectorDescriptor`, `ProjectionInput`, `ProjectionEngine`, `ProjectionDiff`, `LayoutRequest`, `LayoutWorker`, `GraphStreamLayoutFactory`, `GraphGeometryEngine`, `MapTierCorrection`, `LabelPlacementEngine`, `CanvasState`, `GraphCanvas`, and `NanoClock`.
- Produces `NearestRankPercentile.of(List<Long> sortedNanos, double percentile): long`, which returns the nearest-rank value without sorting or mutating the supplied list.
- Produces a deterministic `GeneratedWorkspace` API with a fixed-seed scenario factory, scenario metadata, `ProjectionInput` construction, `WorkspaceDocument` access, snapshot access, and `writeFixtures(Path outputDirectory)` that writes only the three required `.fpg` files.
- Produces `PerformanceMeasurements` with the exact stage names above, successful warm-up/measured sample storage, checked duration recording, percentile summaries, invariant/failure counters, and deterministic CSV serialization with the header `scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass`.
- Produces `GraphWorkspacePerformanceDiagnostic.main(String[] args)` for the Gradle `JavaExec` task and a callable test entry point that accepts output directory, strict-mode flag, and an injectable `NanoClock`.
- Produces `build/graph-performance/two-map.fpg`, `three-map.fpg`, `reference-2000-5000.fpg`, and `performance-ledger.csv`; the diagnostic exits nonzero for any invariant, serialization, timing, lifecycle, or cleanup failure.

### Step 1: Establish the exact Task 37 baseline and red tests

- [ ] Confirm the worktree is at the planning commits with no staged or unstaged source changes, Java is the required Zulu 21 executable, and the existing graph-plugin suite passes before Task 37 edits:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-i-performance
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

- [ ] Add `PerformanceTripwiresShould.java` containing the first falsifiable tests for percentile rank, invalid percentile inputs, ascending-input validation, deterministic scenario identity/count metadata, exact fixture names, exact CSV header/row ordering, and strict/normal threshold arithmetic. Keep every test in this one allowed test file; do not create a seventh test path.
- [ ] Add the `graphPerformanceDiagnostic` task registration in `freeplane_plugin_graph/build.gradle` without relying on the missing Java classes. Register it as a `JavaExec` task depending on `testClasses`, set `classpath = sourceSets.test.runtimeClasspath`, set the main class to `org.freeplane.plugin.graph.performance.GraphWorkspacePerformanceDiagnostic`, propagate `graphStrictPerformance`, set `java.awt.headless=true`, `user.language=en`, and `user.country=US`, declare `build/graph-performance` as an output directory, and pass the output directory as an argument.
- [ ] Run the required red command and preserve the failure evidence in the child report:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PTestLoggingFull
```

Expected result: task configuration succeeds, then Java execution fails because `GraphWorkspacePerformanceDiagnostic` is not yet compiled. Do not treat a successful diagnostic at this point as red evidence.

### Step 2: Implement and test nearest-rank percentile semantics

- [ ] Create `NearestRankPercentile.java` in package `org.freeplane.plugin.graph.performance` with a Java 8 static method `public static long of(List<Long> sortedNanos, double percentile)`. Validate non-null/nonempty input, finite percentile, `0.0 < percentile && percentile <= 1.0`, nonnegative values, ascending order, and checked index arithmetic. Compute `long rank = (long) Math.ceil(percentile * size)` and return the element at `(int) rank - 1` only after proving the rank is within `1..size`.
- [ ] Extend `PerformanceTripwiresShould` with boundary tests for one element, p50/p95/p99 on 300 samples, p=1.0, invalid p values, empty input, descending input, negative values, and preservation of the original list order.
- [ ] Run only the percentile tests and confirm they pass while generator/diagnostic tests remain red:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould' -PTestLoggingFull
```

### Step 3: Implement deterministic workspace and snapshot generation

- [ ] Create `GeneratedWorkspace.java` with a fixed seed constant `20260810`, canonical UUID namespaces, canonical map order, and immutable Java 8 metadata classes. Use `WorkspaceDocument.createVersion1(workspaceId).toBuilder()` and the public model factories. Use approved map colors from `MapReference`, relative URIs such as `maps/m00.mm`, positive unique map/relationship sequences, and deterministic IDs.
- [ ] Build each map tree from one non-leaf root, the exact non-root enclosure count, and persisted structural leaves. The reference tree has 20 roots, 1,180 non-root enclosures, and 2,000 visible leaves. Give every enclosure at least one direct leaf, assign remaining leaves round-robin, and ensure each non-root enclosure has exactly one enclosure parent. Use `SourceNodeKey.persisted(NodeReference)` for every connector endpoint and `SafeNodeLabel.of("node-full-<id>", "node-<id>")` for visible leaves; use nonempty deterministic labels for enclosures. Assert after `ProjectionEngine.project` that node/enclosure/containment/hierarchy counts equal the scenario metadata.
- [ ] Construct native same-map contributors only through `ConnectorDescriptor.of(source, target, arrowAtSource, arrowAtTarget, sourceLabel, middleLabel, targetLabel)`, `ConnectorSnapshot.of(occurrence, descriptor)`, and `MapSnapshot.withConnectors`. Assert source and target are distinct persisted nodes in the same map, both are in that map tree, every source/occurrence key is unique, and the projected contributor has `connectorDescriptor().isPresent()`.
- [ ] Construct cross-map contributors only through `GraphRelationshipRecord.of(relationshipId, sequence, sourceReference, targetReference, direction, emptyUnknownXml)`. Assert map IDs differ, endpoint nodes exist in their trees, relationship sequences/IDs are unique, and the projected contributor has `graphRelationship().isPresent()`.
- [ ] Allocate unordered endpoint pairs with a deterministic `Set<String>` key based on canonical map/node IDs. Never reuse a pair in either direction. For the reference scenario allocate 3,500 native contributors and 1,500 relationship contributors; after projection assert exactly 5,000 edge keys, exactly 5,000 total contributors, exactly 3,500 native contributors, and exactly 1,500 relationship contributors. Count direct nodes and direct child enclosures separately so the 2,000 containment and 1,180 hierarchy links are independently verified.
- [ ] Implement these exact scenario metadata values and use them to drive generation rather than duplicating literals in the diagnostic:

| Scenario | Maps | Nodes | Enclosures | Native | Cross-map | Containment | Hierarchy | Warm-up | Measured |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `two-map` | 2 | 120 | 40 | 120 | 120 | 120 | 38 | 20 | 30 |
| `three-map-clustered` | 3 | 180 | 60 | 180 | 180 | 180 | 57 | 20 | 30 |
| `reference-2000-5000` | 20 | 2,000 | 1,200 | 3,500 | 1,500 | 2,000 | 1,180 | 400 | 300 |
| `skewed-reference` | 20 | 2,000 | 1,200 | 3,500 | 1,500 | 2,000 | 1,180 | 20 | 30 |
| `one-pinned-map` | 3 | 180 | 60 | 180 | 180 | 180 | 57 | 20 | 30 |
| `two-pinned-maps` | 3 | 180 | 60 | 180 | 180 | 180 | 57 | 20 | 30 |

- [ ] For `two-map`, allocate 60 nodes and 20 enclosures per map, 60 native contributors per map, and all 120 cross-map contributors on pair (0,1). For `three-map-clustered`, allocate 60 nodes and 20 enclosures per map, 60 native contributors per map, 150 cross-map contributors on pair (0,1), and 30 on pair (1,2), with none on pair (0,2). For `reference-2000-5000`, allocate 100 nodes and 60 enclosures per map, 175 native contributors per map, and cycle lexicographically through unordered map pairs until 1,500 unique cross-map pairs are emitted.
- [ ] For `skewed-reference`, use node counts `[1600, 21, 21, ..., 21, 22]`, enclosure counts `[960, 12, 12, ..., 12, 24]`, and native counts `[2800, 37, 37, ..., 37, 34]`, where the repeated value occurs for maps 1 through 18 and map 19 owns the final value. Cycle cross-map contributors only through pairs (0,1) through (0,19). Assert map 0 owns exactly 80 percent of nodes and enclosures.
- [ ] For both pinned variants, use three maps with 60 nodes and 20 enclosures per map, 180 native contributors, and cross-map contributors split 120 on (0,1) plus 60 on (1,2). Add `PinRecord.of(NodeReference.of(mapId, PersistedNodeId.of("m00-n0001")), 0.0, 0.0, emptyUnknownXml)` for `one-pinned-map`; add the same coordinate for `m00-n0001` and `m01-n0001` for `two-pinned-maps`. Assert the one-pin result has zero rigid conflicts. For the two-pin result, assert raw root hulls overlap before correction and the corrected worker frame reports exactly one conflict for maps (0,1), listing both pin identities.
- [ ] Use `WorkspaceXmlCodec` with `new WorkspaceMigrationRegistry(Collections.<WorkspaceMigration>emptyList())`; call `byte[] bytes = codec.write(document, path)` and then `Files.write(path, bytes)` because the codec returns bytes. Read each written file back with `codec.read(path)` and assert equality with the generated `WorkspaceDocument`. `writeFixtures` must create only `two-map.fpg`, `three-map.fpg`, and `reference-2000-5000.fpg` under the supplied directory.

### Step 4: Implement checked measurements and deterministic ledger serialization

- [ ] Create `PerformanceMeasurements.java` as Java 8 classes/enums, not records. Define the exact `Stage` enum order from `snapshot` through `accepted-batch-first-frame`, a per-scenario sample collection, and a summary containing `warmupCount`, `measuredCount`, p50, p95, p99, max, normal threshold, strict threshold, failure count, discard count, and pass.
- [ ] Implement `recordWarmup(Stage, long)` and `recordMeasured(Stage, long)` so negative durations, missing stage names, and nondecreasing-clock violations fail immediately. Store successful durations as `Long`, sort a defensive copy for percentile calculation, and keep the original insertion order out of the serialized result.
- [ ] Implement `writeCsv(Path)` with UTF-8 output, the exact header `scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass`, one row per scenario/stage in scenario-table/stage order, no locale-dependent formatting, and a final newline. Use `-1` for a threshold that does not apply to a diagnostic-only row. For a successful run, warmup/measured counts must equal the scenario metadata; failed samples increment `failureCount`, are not converted to durations, and make the diagnostic fail.
- [ ] Define normal thresholds in nanoseconds as 250,000,000 for force, 500,000,000 for full-worker, 750,000,000 p95 and 1,500,000,000 p99 for first-frame, and 10,000,000 for EDT swap. Define strict thresholds as 50,000,000, 100,000,000, 150,000,000/300,000,000, and 2,000,000. Use `-1` for non-gated stages and for strict thresholds on variants.

### Step 5: Implement the public-component diagnostic

- [ ] Create `GraphWorkspacePerformanceDiagnostic.java` with `main(String[] args)`, a ten-minute wall-clock deadline, an output-directory argument defaulting to `build/graph-performance`, and strict-mode selection from the `graphStrictPerformance` system property. Use a relative `NanoClock` that captures one origin and rejects negative, backward, or overflowed readings. Expose a test entry point accepting `Path`, `boolean`, and `NanoClock` without adding production code.
- [ ] Generate fixtures before timing, then for each scenario construct immutable `ProjectionInput` values with `ProjectionInput.of(generation, workspace, snapshots, availability)`. Use `ProjectionEngine.project(input)` for each current projection. Build a valid previous projection before the sample and call `ProjectionDiff.between(previous, current)` for every diff sample. Snapshot timing starts before input construction and ends after `ProjectionInput` validation; it is complete before the `AcceptedBatch` timestamp is created.
- [ ] For direct layout stages, create `LayoutRequest.of(workspace.id(), projection, diff, projection.pins())`, create one `LayoutEngine` through `GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())` outside the timed call, run timed owner-thread `apply` for `mutation`, compute raw hull, run public `MapTierCorrection.apply(projection, rawPositions, rawHull)` for `correction`, compute corrected-position hull for `hull`, run `LabelPlacementEngine.place(projection, correctedHull, fixedMetrics)` for `label`, run timed owner-thread `step` for `force`, and close the engine on its owner thread. Reject failed frames and assert exact node/anchor coverage and finite positions.
- [ ] For `full-worker`, create one public `LayoutWorker(LayoutCalibration.spikeDefaults())` per scenario, submit each fresh generation/request in canonical warm-up/measured order, use `submit` rather than `step`, wait no longer than five seconds on its `CompletionStage`, reject failed frames, and close the worker after the scenario. The worker's internal sequence is the production first-frame `submit`, raw hull, and map correction; recompute a hull from corrected positions before labels.
- [ ] For `accepted-batch-first-frame`, construct `new AcceptedBatch(generation, clock.nanoTime(), EnumSet.of(ChangeKind.STRUCTURE))` after snapshot timing and before projection timing. Start the elapsed measurement at `acceptedAtNanos()`. Run projection, diff, `LayoutWorker.submit`, bounded completion, corrected-position hull, labels, `CanvasState.of(generation, projection, frame, labeledGeometry, OperationalStatus.SETTLING)`, and a non-EDT `GraphCanvas.setCanvasState(state)`. End only when the production setter's internal EDT assignment returns. Do not include snapshot construction, coordinator debounce, or queue time before the accepted timestamp.
- [ ] Configure one `GraphCanvas` per scenario on the real EDT with `setSize(1024, 768)`, `setDoubleBuffered(false)`, `setTheme(GraphTheme.resolve(CanvasTheme.LIGHT))`, `setPaintState(GraphPaintState.empty())`, `setViewport(GraphViewport.of(0.0, 0.0, 1.0))`, `setShowArrowheads(true)`, and `setDimUnrelated(false)`. Use a fresh `BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB)` per repaint and set `KEY_RENDERING` to `VALUE_RENDER_SPEED` before calling the production paint path; `GraphPainter` is expected to apply its own fixed antialiasing hint. Use a fixed non-antialiased `FontRenderContext` for the geometry text metrics. Measure `canvas.paint(graphics)` inside a real EDT task with a five-second `Future.get` bound. Require the resulting image checksum to differ from an all-background image for a nonempty state.
- [ ] Implement lifecycle probes only for public contracts: call `LayoutSettleLoop.start` with mismatched batch/projection generations and assert immediate `IllegalArgumentException` before work is queued; close a `LayoutWorker` and assert its public `submit` and `step` stages complete exceptionally. Record failures rather than swallowing them. Do not use reflection to reach package-private coordinator or painter code.
- [ ] After every scenario, close the worker and direct engines, dispose image graphics, remove temporary files, and assert no diagnostic-owned executor remains alive. Use bounded cleanup and make cleanup failure fail the process.

### Step 6: Run focused green verification and the generated diagnostic

- [ ] Run the allowed performance test class and confirm percentile, generator, CSV, lifecycle, invariant, and deterministic serialization tests pass:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould' -PTestLoggingFull
```

- [ ] Run the diagnostic in normal mode and verify exact output names, byte-identical rerun behavior, exact CSV header, exact row count (six scenarios times twelve stages), count fields, native/relationship contributor totals, finite paint checksum, and normal tripwires:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PTestLoggingFull
sha256sum build/graph-performance/two-map.fpg build/graph-performance/three-map.fpg build/graph-performance/reference-2000-5000.fpg build/graph-performance/performance-ledger.csv
```

- [ ] Run the complete graph-plugin suite, including bundle and Java 8 compatibility checks:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test :freeplane_plugin_graph:check -PTestLoggingFull
```

- [ ] Run the diagnostic a second time, compare fixture and structural ledger hashes byte-for-byte, and assert the only generated files below `build/graph-performance` are the three fixtures and `performance-ledger.csv`.

### Step 7: Commit exactly the Task 37 allowlist

- [ ] Run `git diff --check`, assert no Task 2 path or unlisted generated source is changed, assert the index is empty, then stage exactly:

```text
freeplane_plugin_graph/build.gradle
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/NearestRankPercentile.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java
```

- [ ] Verify `git diff --cached --name-only` equals that six-line list exactly, commit with `git commit -m "2026-08-10-graph-workspace: Add graph performance diagnostics"`, verify the commit names with `git show --name-only --format=`, and report the commit SHA plus fresh test/diagnostic evidence. Do not begin Task 2 before this verification is complete.

## Task 2: Calibrate Backlog Task 38 Against Strict Production Targets

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Create: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`

**Interfaces:**

- Consumes the committed Task 37 `graphPerformanceDiagnostic` task and `build/graph-performance/performance-ledger.csv` schema, the exact Task 37 commit SHA, and the six allowed runtime classes.
- Produces a byte-preserved `build/graph-performance/strict-baseline-ledger.csv`, a final `performance-ledger.csv`, a strict-passing reference run, and `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md` containing the exact baseline/final rows and calibration rationale.
- Does not alter the Task 37 test sources, Gradle diagnostic registration, fixtures, public APIs, GraphStream imports, or any file outside the seven-path allowlist.

### Step 1: Verify the Task 37 handoff and run the untouched strict baseline

- [ ] Confirm `git status --short --branch` is clean, `git log -1 --format=%s` is the Task 37 commit subject, and `git show --name-only --format= HEAD` lists exactly the six Task 37 paths. If any condition fails, stop and report the handoff blocker before editing.
- [ ] Run the strict diagnostic at the unmodified Task 37 commit and preserve the full command output, environment, fixture hashes, and exit status:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-i-performance
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull
sha256sum build/graph-performance/two-map.fpg build/graph-performance/three-map.fpg build/graph-performance/reference-2000-5000.fpg build/graph-performance/performance-ledger.csv
cp build/graph-performance/performance-ledger.csv build/graph-performance/strict-baseline-ledger.csv
```

- [ ] Parse and archive the baseline CSV rows by scenario/stage. Identify the largest p95/p99 over its applicable threshold, distinguish direct force, worker, first-frame, EDT, hull, label, mutation, and repaint evidence, and record the measured reason before changing any source. A strict failure without a measured bottleneck is not permission for a speculative edit.

### Step 2: Apply only evidence-backed calibration changes

- [ ] For a force or full-worker bottleneck, consider only `LayoutCalibration.java` and `GraphStreamLayoutEngine.java`. Preserve the fixed SpringBox quality `0.10`, deterministic SHA-256 seed/initial positions, particle/link topology, ordered containment/hierarchy/same-map multipliers, exact pin behavior, O(N+E) synchronization, immutable `LayoutFrame`, and the package-private `GraphStreamLayoutEngine` boundary. Do not remove particles, edges, or correction stages merely to lower the timing.
- [ ] For a first-frame or settling-policy bottleneck, consider only `PerceptualIdlePolicy.java` and `GraphUpdateCoordinator.java`. Preserve generation ordering, coalescing semantics, stale/closed publication safety, immutable `CanvasState` publication, listener ordering, and the existing single active execution path. Change a threshold or scheduling condition only when the baseline ledger and a focused existing regression explain the measured cost.
- [ ] For hull or correction evidence, consider only `GraphGeometryEngine.java`. Preserve exact node/anchor coverage, finite-coordinate validation, enclosure parent/child ordering, label-independent hull construction, rigid pinned-map correction inputs, and the aggregate cross-map displacement cap `0.005`. Keep the algorithm linear in projected nodes plus enclosure links wherever the current contract requires it.
- [ ] For repaint evidence, consider only `GraphPainter.java`. Preserve all visible rendering branches, arrowhead/dim settings, deterministic light-theme setup, labels, pins, hulls, accessibility-visible state, and no package-boundary leakage. Do not hide content or switch off labels to meet a timing ceiling.
- [ ] Make one narrow calibration wave at a time. After each wave, run the focused existing tests for the touched class and the normal diagnostic. Keep a change only if its named stage improves against the baseline without violating structural counts, finite coordinates, pin conflicts, deterministic fixture hashes, lifecycle probes, or any other gated metric. Revert an unproductive wave by editing the touched file back to its pre-wave content; do not use destructive Git reset/checkout commands.

### Step 3: Re-run strict performance and compatibility gates

- [ ] Run the strict diagnostic after the final calibration wave and require the four strict reference ceilings to pass:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull
sha256sum build/graph-performance/two-map.fpg build/graph-performance/three-map.fpg build/graph-performance/reference-2000-5000.fpg build/graph-performance/performance-ledger.csv
```

- [ ] Run the focused tests associated with every changed class, then the complete graph-plugin test and bundle gates:

```bash
env JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test :freeplane_plugin_graph:check -PTestLoggingFull
```

- [ ] Compare final fixture hashes to the baseline and assert exact equality. Compare baseline and final CSV row keys, scenario counts, sample counts, thresholds, failure/discard counts, and invariant outcomes. A changed fixture or missing row is a failure even if timings pass.
- [ ] Verify Java 8 class compatibility and GraphStream boundary by running the existing `verifyGraphBundle` task and inspecting imports/signatures; no public or exported class may expose an `org.graphstream` type.

### Step 4: Write the required performance report from verified artifacts

- [ ] Create the report only after the final strict run passes. Record the exact merge base, Task 37 implementation commit SHA, and the Task 2 pre-report calibration HEAD SHA (the parent of the report-bearing commit), plus the final seven-path commit SHA in the completion evidence. This avoids a mathematically impossible self-referential commit hash while preserving exact provenance for every source and artifact state.
- [ ] Include the exact commands, fixture SHA-256 values, `performance-ledger.csv` and `strict-baseline-ledger.csv` paths, exact CSV header, clock definition, all twelve stage boundaries, five-second operation timeout, ten-minute process deadline, and cleanup/thread assertions.
- [ ] Include one table row for every scenario/stage with `scenario`, `stage`, warm-up count, measured count, p50, p95, p99, maximum, normal threshold, strict threshold, failure count, discard count, and pass/fail. Include separate baseline and final columns or tables without changing the authoritative CSV schema.
- [ ] Explain each source diff with the baseline row that justified it, the before/after value or algorithmic change, the resulting percentile delta, and the invariant preserved. State explicitly that coordinator debounce, coordinator stale/closed discard, canvas stale-state rejection, and private painter internals are outside Task 37's observation boundary.
- [ ] Record residual machine-specific hardware risk and state whether any threshold was not applicable (`-1`) rather than silently omitting a row. Ensure the report is ASCII/UTF-8 clean and `git diff --check` passes.

### Step 5: Commit exactly the Task 38 allowlist

- [ ] Confirm the index is empty and `git status --short` contains only the six allowed runtime files plus the report. Stage exactly:

```text
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java
docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md
```

- [ ] Verify `git diff --cached --name-only` equals that seven-line list exactly, run `git diff --cached --check`, commit with `git commit -m "2026-08-10-graph-workspace: Calibrate graph performance"`, verify `git show --name-only --format= HEAD`, and rerun the strict diagnostic from the committed Task 2 HEAD. The committed report must match the final strict run's scenario/stage rows; the completion evidence must include the final commit SHA and the post-commit ledger hash.

### Plan completion gate

- [ ] Report both implementation commit SHAs, the strict baseline and final CSV hashes, exact commands and exit statuses, full graph-plugin test result, exact allowlist checks, and any residual risk. Do not claim Task 38 is complete unless the final committed HEAD has a fresh strict passing run and the report matches that run.
