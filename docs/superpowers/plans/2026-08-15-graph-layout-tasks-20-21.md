# Graph Layout Tasks 20 and 21 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Implement the GraphStream-private force layout engine and its serialized, correction-aware worker for Graph Workspace.

**Architecture:** Task 1 creates a GraphStream-free public layout boundary and a package-private GraphStream 1.3 adapter based on `MultiGraph` and typed SpringBox particles. Task 2 owns all asynchronous execution on one worker thread, decorates immutable layout frames with map-separation conflicts and perceptual idle measurements, and preserves the Task 1 adapter boundary.

**Tech Stack:** Java 8 source and bytecode, Gradle, JUnit 4, AssertJ, GraphStream gs-core 1.3, pherd 1.0, mbox2 1.0, existing immutable projection and geometry types.

## Global Constraints

- Use `~/.sdkman/candidates/java/21.0.8-zulu/bin/java`; compile all production and test code for Java 8.
- Use escalated `gradle`, never `gradlew` or Maven; run focused tests with `-PTestLoggingFull`.
- Touch only the exact file allowlist for the active task; before staging assert the index is empty, then compare the staged file list exactly to that allowlist.
- Public APIs in `org.freeplane.plugin.graph.layout` are immutable and GraphStream-free. No externally visible signature may expose an `org.graphstream` type.
- Keep GraphStream implementation classes package-private in `org.freeplane.plugin.graph.layout.graphstream`; `GraphStreamLayoutFactory` is the only public construction seam.
- Use `MultiGraph`, attach the layout sink before graph population, use SpringBox quality `0.10`, and never instantiate `LayoutRunner`.
- `layout.weight` must not represent stiffness. Preserve `containment < hierarchy < sameMap` using initial values `0.15`, `0.30`, and `1.0`.
- Aggregate cross-map displacement is capped once per particle per step at vector magnitude `0.005`; do not cap each edge independently.
- Derive seeds from canonical UTF-8 key bytes, not `hashCode()` or `toString()`: encode workspace UUID; nodes as map UUID plus persisted node ID or structural-path sequence; anchors as ordered encoded enclosure endpoints; use SHA-256 lower 64 bits for `Random`.
- Active pin coordinates are layout-space coordinates and must be exact. Dormant pins create no force. A prominence-scaled node must never move a pinned neighbor or exceed the aggregate cap.
- `HullIntersection.minimumSeparatingTranslation(hullA, hullB)` is the vector applied to the second hull B. Map correction translates every node and anchor of a movable map uniformly: both movable maps receive `-T/2` for A and `+T/2` for B; a sole movable A receives `-T`; a sole movable B receives `+T`. A map with any active pin is rigid. Never move a pinned map or only its root anchor.
- The worker owns one executor and all GraphStream mutation and `compute()` calls occur only there. It never touches Swing.
- `LayoutWorker` computes temporary hulls from raw engine positions, applies correction, measures idle against the previous corrected frame, and returns an immutable corrected frame.
- Perceptual idle measures all matching node and anchor positions. Key-set changes reset stability. `PerceptualIdlePolicy.spikeDefaults()` is 8 frames, RMS `0.02`, max `0.05`.
- Run the named mutation checks after green, restore the exact production SHA-256, rerun the focused suite, and leave no mutant diff.

## Task 1: Private GraphStream typed physics

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutEngine.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutRequest.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

**Interfaces:**
- Consumes: `WorkspaceId`; `GraphProjection` with ordered `nodes()`, `enclosures()`, `edges()`, `pins()`, and `prominence()`; `ProjectionDiff`; `PinProjection`; `LayoutPoint`; `LayoutPositions`; `ProjectedNodeKey`; `EnclosureHullKey`; and `ProjectedEndpointKey`.
- Produces: `public interface LayoutEngine extends AutoCloseable { LayoutFrame apply(LayoutRequest request); LayoutFrame step(); void reset(); void close(); }`.
- Produces: `public final class LayoutCalibration { public static LayoutCalibration spikeDefaults(); public double containment(); public double hierarchy(); public double sameMap(); }`, with `spikeDefaults()` values 0.15, 0.30, and 1.0.
- Produces: `public final class LayoutRequest { public static LayoutRequest of(WorkspaceId workspace, GraphProjection projection, ProjectionDiff diff, List<PinProjection> pins); public WorkspaceId workspace(); public GraphProjection projection(); public ProjectionDiff diff(); public List<PinProjection> pins(); }` using defensive ordered copies.
- Produces: `public final class LayoutFrame { public static LayoutFrame of(long stepIndex, LayoutPositions positions, boolean failed); public long stepIndex(); public LayoutPositions positions(); public boolean failed(); }` using immutable values and nonnegative step indices.
- Produces: `public final class GraphStreamLayoutFactory { public static LayoutEngine create(LayoutCalibration calibration); }`. The factory signature and all public layout types remain GraphStream-free.
- Defines: `GraphStreamLayoutEngine`, `TypedSpringBox`, and `TypedNodeParticle` as package-private implementation classes. The engine represents every projected node and enclosure anchor, projected edges, direct-node containment springs, and parent-child enclosure springs.

- [ ] **Step 1: Write the boundary and force tests**

Create `GraphStreamBoundaryShould` to assert that public layout classes and method signatures contain no GraphStream type, factory-created engines expose only `LayoutEngine`, source contains no `LayoutRunner`, and `LayoutCalibration.spikeDefaults()` has quality-compatible ordered strengths. Create `TypedForcesShould` fixtures using existing immutable projection builders and test: equal requests yield identical positions; changed workspace IDs yield distinct deterministic seeds; visible nodes and enclosure anchors appear in `LayoutPositions`; parallel projected edges do not fail; direct containment and hierarchy topology survive; 100 add/remove cycles return to baseline coverage; aggregate cross-map fan-out never moves a particle farther than `0.005`; a higher `NodeProminence.scale()` increases separation; and an active pinned neighbor remains exactly at its supplied coordinates.

- [ ] **Step 2: Run the tests red**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould' --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' -PTestLoggingFull
```

Confirm the failure is missing layout production classes, not an unrelated baseline failure.

- [ ] **Step 3: Add immutable public layout values**

Implement the four public layout values before GraphStream code. Validate nulls, nonnegative frame indices, finite generated positions, immutable list/map copies, and matching request projection/diff generations. Implement `LayoutFrame.reset()` semantics in the engine, not in the value: it must discard solver state, rebuild from the last accepted request, reseed deterministically, and reset the next frame index to zero.

- [ ] **Step 4: Implement the GraphStream-private engine**

Create `GraphStreamLayoutFactory.create` and package-private `GraphStreamLayoutEngine`. Construct a `MultiGraph`, attach the sink before adding nodes or edges, retain unaffected particles on `apply`, remove obsolete particles and springs, and seed new particles with the canonical SHA-256 encoding from Global Constraints. Use one particle for each node and anchor, create relationship, direct containment, and hierarchy links from the ordered projection, set active pins exactly, and ignore dormant pins. `step()` computes one frame, snapshots ordered node/anchor positions, increments its index, and never leaks GraphStream values. `reset()` rebuilds from the last request; `close()` releases adapter resources idempotently.

- [ ] **Step 5: Implement typed force behavior**

Use `TypedSpringBox` and `TypedNodeParticle` to apply calibration multipliers directly to typed force contributions. Classify an edge as cross-map when its endpoint map IDs differ. Accumulate all cross-map contributions per particle before one vector-magnitude clamp of `0.005`; apply same-map, containment, and hierarchy contributions without that aggregate cross-map budget. Set node separation radius to `8.0 * prominence.scale()` and anchor radius to `8.0`; do not add prominence to spring attraction. Pin enforcement wins over every movement contribution.

- [ ] **Step 6: Run the focused tests green**

Run the Step 2 command and then:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:verifyGraphBundle -PTestLoggingFull
```

Confirm the focused tests, Java 8 bytecode verification, dependency checksums, and bundle verification pass.

- [ ] **Step 7: Prove the aggregate-cap test is falsifiable**

Record SHA-256 for `TypedSpringBox.java`. Temporarily clamp each cross-map edge independently instead of the accumulated per-particle vector. Run the aggregate-fanout test and confirm it fails. Restore exactly by SHA-256, rerun the focused suite, and verify no mutant diff remains.

- [ ] **Step 8: Commit Task 1**

Run `git diff --check`; assert the index is empty; stage exactly the ten files listed in this task; compare `git diff --cached --name-only` to that exact list; and commit:

```bash
git commit -m "2026-08-10-graph-workspace: Add private GraphStream physics"
```

## Task 2: Owned layout worker, correction, and idle measurement

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutConflict.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/MapTierCorrection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicyShould.java`

**Interfaces:**
- Consumes: all Task 1 public layout types; `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions)`; `GraphGeometry`; `HullIntersection.minimumSeparatingTranslation(HullGeometry, HullGeometry)`; `MapReferenceId`; and ordered `GraphProjection` map-root enclosures and pins.
- Produces: `public final class LayoutConflict { public MapReferenceId firstMap(); public MapReferenceId secondMap(); public List<PinProjection> blockingPins(); }`, validating two distinct ordered map IDs and immutable active-pin copies.
- Produces: `public final class PerceptualIdlePolicy { public static final class IdleMeasurement { public double rms(); public double max(); public int consecutiveStableFrames(); public boolean idle(); } public static PerceptualIdlePolicy spikeDefaults(); public PerceptualIdlePolicy(int consecutive, double rms, double max); public IdleMeasurement observe(LayoutPositions before, LayoutPositions after); }`.
- Produces: `public final class MapTierCorrection { public static final class CorrectionResult { public LayoutPositions positions(); public List<LayoutConflict> conflicts(); } public CorrectionResult apply(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry); }`.
- Produces: `public final class LayoutWorker implements AutoCloseable { public CompletionStage<LayoutFrame> submit(LayoutRequest request); public CompletionStage<LayoutFrame> step(); public void pause(); public void restart(); public LayoutFrame lastValidFrame(); public void close(); }`. Provide a public GraphStream-free construction path and package-visible injectable engine factory/policy constructors for deterministic tests.
- Extends: `LayoutFrame` with `public List<LayoutConflict> conflicts()` and `public PerceptualIdlePolicy.IdleMeasurement idle()`. Retain Task 1's raw-frame factory and add a decoration factory so Task 1's adapter needs no modification.

- [ ] **Step 1: Write correction, idle, and worker tests**

Create `MapTierCorrectionShould` to construct two and three map-root hull fixtures and assert deterministic pair order, exact `-T/2` for the first hull's map and `+T/2` for the second hull's map, the signed full translation when one map is rigid, no movement plus one conflict when both maps are rigid, every node and anchor in a moved map has the same delta, and no pinned node moves. Create `PerceptualIdlePolicyShould` to assert finite constructor validation, RMS/max over both nodes and anchors, streak reset when either key set changes, immediate idle for two empty frames, and the exact injectable spike default values. Create `LayoutWorkerShould` using a fake `LayoutEngine` to assert one-at-a-time serialized calls, pin/dormant/unpin transitions, raw-frame temporary geometry then correction, decorated conflicts and idle values, failure preserving `lastValidFrame`, normal pause/restart behavior, failed-engine replacement on restart, pre-submit/paused completed stages, close cancellation of queued stages, and 25 close cycles without a surviving worker thread.

- [ ] **Step 2: Run the tests red**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould' --tests 'org.freeplane.plugin.graph.layout.MapTierCorrectionShould' --tests 'org.freeplane.plugin.graph.layout.PerceptualIdlePolicyShould' -PTestLoggingFull
```

Confirm the failure is missing Task 2 classes or frame diagnostics.

- [ ] **Step 3: Implement immutable diagnostics and idle policy**

Implement `LayoutConflict`, `CorrectionResult`, `IdleMeasurement`, and immutable frame decoration. `spikeDefaults()` returns exactly `(8, 0.02, 0.05)`. `observe` requires finite positions, compares the union of matching node and anchor coordinates, computes RMS as `sqrt(sum(dx*dx + dy*dy) / count)`, computes max as the largest Euclidean displacement, and resets to zero when either key set changes. Both-empty matching frames return an immediately idle measurement with the required stable-frame count.

- [ ] **Step 4: Implement deterministic map-tier correction**

Iterate map-root `ProjectedEnclosure` values in their stable projection order and process each unordered pair once. Obtain current hulls from `GraphGeometry`; skip pairs whose root hull is absent or whose separating translation is `(0, 0)`. For movable pairs use `T = HullIntersection.minimumSeparatingTranslation(hullA, hullB)`, which moves the second hull B clear of A. Translate all node and anchor positions of map A by `-T/2` and map B by `+T/2`. If exactly one map has any active pin, translate only B by `+T` when A is rigid, or only A by `-T` when B is rigid. If both maps are rigid, leave all positions unchanged and add one ordered conflict containing both maps' active pins. Preserve input ordering and return immutable positions/conflicts.

- [ ] **Step 5: Implement the one-executor worker**

Make `LayoutWorker` own one named single-thread executor and a GraphStream-free `Supplier<LayoutEngine>`. Queue `submit` and `step` as `CompletableFuture` work on that executor. `submit` installs the request through `engine.apply`; `step` calls `engine.step`. For every successful raw frame, compute temporary hulls with `GraphGeometryEngine`, apply `MapTierCorrection`, measure idle against the prior corrected frame, decorate the frame, and update the retained last-valid frame. Catch engine or geometry failures, retain the old valid frame, and return a decorated failed frame. `pause` causes later `step` calls to return completed retained-frame stages; normal `restart` resumes without reset, while failed `restart` closes/replaces the engine before the next submitted request. Track pending futures so `close` cancels unresolved ones with `CancellationException`, closes the engine on its owner thread where possible, shuts down the executor, and rejects later work.

- [ ] **Step 6: Run the focused tests green**

Run the Step 2 command, then run Task 1's focused suite as a compatibility gate:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.*Should' -PTestLoggingFull
```

Confirm both tasks' tests pass together.

- [ ] **Step 7: Prove correction tests reject invalid shortcuts**

Record SHA-256 for `MapTierCorrection.java`. First mutate correction to translate only the map-root anchor; confirm the whole-map uniform translation test fails. Restore. Then mutate it to translate a map containing an active pin; confirm the pinned-map test fails. Restore the original SHA-256, rerun Step 6, and verify no mutant diff remains.

- [ ] **Step 8: Commit Task 2**

Run `git diff --check`; assert the index is empty; stage exactly the eight files listed in this task; compare `git diff --cached --name-only` to that exact list; and commit:

```bash
git commit -m "2026-08-10-graph-workspace: Add the owned layout worker"
```
