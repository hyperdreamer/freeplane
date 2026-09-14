# Graph Boundary Separation Performance Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Bring the boundary-separation correction back under the strict
`reference-2000-5000` performance gate by removing the per-frame verification
overhead it added, without changing correction semantics, thresholds, or
workloads.

**Architecture:** The integrated correction recomputes every enclosure hull on
every round and, on every published frame, re-runs a quadratic child/parent
containment scan plus a linear membership scan inside the sibling sweep. Task 1
pins the cost with a focused deterministic profile and JDK Flight Recorder
attribution. Task 2 removes it with three semantics-preserving optimizations: an
affected-only `GraphGeometryEngine.recomputeHulls` interface, a bounded
value-keyed ancestor-containment memo and a per-frame support/cap-set cache in
`BoundarySeparationCorrection`, and early exits that stop candidate evaluation
as soon as no contributor can move. Task 3 pins each optimization with
falsifiable regression tests, and Task 4 extends the diagnostic process deadline
to 40 minutes, re-runs the strict gate, and records the evidence document.

**Tech Stack:** Java 8 source level, Gradle (`gradle :freeplane_plugin_graph:test`),
JUnit 4 + AssertJ, Freeplane graph plugin internals
(`org.freeplane.plugin.graph.layout`, `...geometry`, `...projection`), JDK Flight
Recorder via `~/.sdkman/candidates/java/21.0.8-zulu/bin/jfr`.

## Global Constraints

- Java 8 source level; UTF-8; 4-space indentation.
- No new dependencies; no `freeplane_api`, OSGi export/import, persistence schema, or public layout API change (package-private observability members are allowed).
- No threshold, workload, sample count, stage-mapping, or measurement-semantics change. The only diagnostic change authorized in this segment is `GraphWorkspacePerformanceDiagnostic.PROCESS_DEADLINE_NANOS` from 10 to 40 minutes and nothing else in that file.
- Correction semantics must stay identical: positions, rounds, violation counts, residual pairs, conflicts, and applied displacements are unchanged; an optimization may only remove work whose result is already known.
- Use `gradle`, never `gradlew`; use Java from `~/.sdkman/candidates/java/21.0.8-zulu`; run tests with `gradle :freeplane_plugin_graph:test`.
- JUnit 4 with AssertJ; name test classes `*Should`; tests must never read user Dropbox paths.
- Touch only the paths listed in the task's Ownership line.
- Baseline strict-gate reference (commit `aa6ac48b02`, ledger SHA-256 `8bd8eb5fb440a7ced1e3586ad5658af8945a3824ba258a91b01d3c485516163a`): `reference-2000-5000` p95 is 51,985,400 ns for `full-worker` and 76,344,593 ns for `accepted-batch-first-frame`; their strict budgets are 100,000,000 ns and 150,000,000 ns. `force` (268,972,130 ns) and `edt-swap` (2,700,023 ns) already fail on that untouched baseline and are environment waivers, never gate targets.

## Task 1: Focused correction profile and hotspot attribution

**Implementer tier:** Advanced

**Lane:** integration

**Depends on:** none

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/BoundaryCorrectionProfile.java, freeplane_plugin_graph/build.gradle, docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md

**Validation:** gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/BoundaryCorrectionProfile.java`
- Modify: `freeplane_plugin_graph/build.gradle` (append the `boundaryCorrectionProfile` task after the `graphPerformanceDiagnostic` task)
- Create: `docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`

**Interfaces:**
- Consumes: `GeneratedWorkspace.forScenario(String)`, `GeneratedWorkspace.document()`, `GeneratedWorkspace.snapshots()`, `GeneratedWorkspace.availability()`, `ProjectionInput.of(long, WorkspaceDocument, List<MapSnapshot>, Map<MapReferenceId, MapAvailability>)`, `ProjectionEngine.project(ProjectionInput)`, `ProjectionDiff.between(GraphProjection, GraphProjection)`, `LayoutRequest.of(WorkspaceId, GraphProjection, ProjectionDiff, List<PinProjection>)`, `LayoutCalibration.spikeDefaults()`, `GraphStreamLayoutFactory.create(LayoutCalibration)`, `LayoutEngine.apply(LayoutRequest)`, `LayoutEngine.close()`, `LayoutWorker.submit(LayoutRequest)`, `LayoutWorker.close()`, `BoundarySeparationCorrection.apply(GraphProjection, LayoutPositions, GeometryTextMetrics, List<PinProjection>)`, `BoundarySeparationResult.timings()`, `BoundarySeparationResult.diagnostics()`, `BoundarySeparationTimings.separationNanos()/hullNanos()/planNanos()/applyNanos()`, `BoundarySeparationDiagnostics.rounds()/hullViolationsDetected()/hullResidualViolations()/conflicts()`, `NearestRankPercentile.of(List<Long>, double)`.
- Produces: `public static void BoundaryCorrectionProfile.main(String[] args)` with `args[0]` the scenario wire name (default `reference-2000-5000`) and `args[1]` the output directory (default `build/boundary-correction-profile`); it writes `correction-profile.csv` with header `sample,stage,totalNanos,separationNanos,hullNanos,planNanos,applyNanos,rounds,detected,residual,conflicts` and prints per-stage p50/p95/max nanos; the Gradle task `boundaryCorrectionProfile` runs it with a JDK Flight Recorder recording written to `freeplane_plugin_graph/build/boundary-correction-profile/correction-profile.jfr`.

### Profile design

The harness reproduces the two measurement paths the diagnostic uses on the
`reference-2000-5000` workload, and adds the stage split that the ledger maps to
`SEPARATION`, `HULL`, and `CORRECTION` (`correction = plan + apply`):

1. `workerTotal` — a long-lived `LayoutWorker` and one `submit` per generation,
   the same shape as the diagnostic's `full-worker` row.
2. `directTotal`, `separation`, `hull`, `plan`, `apply` — a fresh
   `GraphStreamLayoutFactory` engine per generation, `apply(request)`, then one
   shared `BoundarySeparationCorrection.apply(...)` call, the same shape as the
   diagnostic's direct probe. The correction instance is shared across samples,
   exactly as `GraphWorkspacePerformanceDiagnostic.correctionEngine` is.

The harness never asserts a wall-clock budget; it records numbers. Hotspot
attribution comes from the always-on Flight Recorder recording inspected with
`jfr view hot-methods`.

Planner-captured reference reading at the integration base (`c758082e0c`,
unpinned, ambient host load); use it only to sanity-check your own run:

- workload: 2000 nodes, 1200 enclosures, 1180 enforced child/parent pairs, 9440 `HullGeometry.contains` invocations per correction call.
- per worker frame: `detect` p50 about 87-160 ms, `hull` about 1-5 ms when the geometry engine cache is warm, `plan` and `apply` about 0 (this scenario has no same-map violations), `NodeSeparationProjection` about 20-50 ms, `submit` total about 134-226 ms after the first frame.
- JFR hotspot order: `HullGeometry.contains` then `HullGeometry.classifyOrientation` then `TaggedSum.mergeComponent`/`TaggedSum.addTerm`, followed by `BoundarySeparationCorrection.detect` and the `ArrayList.contains`/`EnclosureHullKey.equals` pair used for the enforced-set membership test.

- [ ] **Step 1: Write the profile harness**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/BoundaryCorrectionProfile.java` with exactly this content.

```java
package org.freeplane.plugin.graph.performance;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.layout.BoundarySeparationCorrection;
import org.freeplane.plugin.graph.layout.BoundarySeparationResult;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;

/**
 * Focused, deterministic profile of the boundary-separation correction cost per published frame.
 * Writes a per-sample CSV and prints per-stage p50/p95/max; the Gradle task also records a JFR
 * profile so hot methods can be attributed with {@code jfr view hot-methods}.
 */
public final class BoundaryCorrectionProfile {
    private static final int WARMUP_SAMPLES = 40;
    private static final int MEASURED_SAMPLES = 120;
    private static final List<String> STAGES = Collections.unmodifiableList(Arrays.asList(
        "workerTotal", "directTotal", "separation", "hull", "plan", "apply"));

    private BoundaryCorrectionProfile() {
    }

    public static void main(final String[] args) throws Exception {
        final String scenario = args.length > 0 ? args[0] : "reference-2000-5000";
        final Path output = args.length > 1 ? Paths.get(args[1])
            : Paths.get("build", "boundary-correction-profile");
        Files.createDirectories(output);
        final GeneratedWorkspace generated = GeneratedWorkspace.forScenario(scenario);
        final ProjectionEngine projectionEngine = new ProjectionEngine();
        final GeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
        final BoundarySeparationCorrection corrector = new BoundarySeparationCorrection();
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        final List<String> csv = new ArrayList<String>();
        csv.add("sample,stage,totalNanos,separationNanos,hullNanos,planNanos,applyNanos,rounds,detected,residual,conflicts");
        final Map<String, List<Long>> samples = new LinkedHashMap<String, List<Long>>();
        for (final String stage : STAGES) {
            samples.put(stage, new ArrayList<Long>());
        }
        try {
            GraphProjection previous = projectionEngine.project(ProjectionInput.of(0L, generated.document(),
                generated.snapshots(), generated.availability()));
            final int total = WARMUP_SAMPLES + MEASURED_SAMPLES;
            for (int index = 0; index < total; index++) {
                final long generation = index + 1L;
                final GraphProjection current = projectionEngine.project(ProjectionInput.of(generation,
                    generated.document(), generated.snapshots(), generated.availability()));
                final ProjectionDiff diff = ProjectionDiff.between(previous, current);
                final LayoutRequest request = LayoutRequest.of(generated.document().id(), current, diff,
                    current.pins());

                final long workerStart = System.nanoTime();
                final LayoutFrame frame = worker.submit(request).toCompletableFuture().get();
                final long workerTotal = System.nanoTime() - workerStart;

                final LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults());
                try {
                    final LayoutFrame applied = engine.apply(request);
                    final long directStart = System.nanoTime();
                    final BoundarySeparationResult result = corrector.apply(current, applied.positions(),
                        metrics, request.pins());
                    final long directTotal = System.nanoTime() - directStart;
                    if (index >= WARMUP_SAMPLES) {
                        record(samples, "workerTotal", workerTotal);
                        record(samples, "directTotal", directTotal);
                        record(samples, "separation", result.timings().separationNanos());
                        record(samples, "hull", result.timings().hullNanos());
                        record(samples, "plan", result.timings().planNanos());
                        record(samples, "apply", result.timings().applyNanos());
                        csv.add(String.join(",", String.valueOf(index), "direct",
                            String.valueOf(directTotal),
                            String.valueOf(result.timings().separationNanos()),
                            String.valueOf(result.timings().hullNanos()),
                            String.valueOf(result.timings().planNanos()),
                            String.valueOf(result.timings().applyNanos()),
                            String.valueOf(result.diagnostics().rounds()),
                            String.valueOf(result.diagnostics().hullViolationsDetected()),
                            String.valueOf(result.diagnostics().hullResidualViolations()),
                            String.valueOf(result.diagnostics().conflicts().size())));
                        csv.add(String.join(",", String.valueOf(index), "worker",
                            String.valueOf(workerTotal), "-1", "-1", "-1", "-1",
                            String.valueOf(frame.boundaryDiagnostics().rounds()),
                            String.valueOf(frame.boundaryDiagnostics().hullViolationsDetected()),
                            String.valueOf(frame.boundaryDiagnostics().hullResidualViolations()),
                            String.valueOf(frame.boundaryDiagnostics().conflicts().size())));
                    }
                }
                finally {
                    engine.close();
                }
                previous = current;
            }
        }
        finally {
            worker.close();
        }
        Files.write(output.resolve("correction-profile.csv"),
            String.join("\n", csv).concat("\n").getBytes(StandardCharsets.UTF_8));
        printSummary(samples);
    }

    private static void record(final Map<String, List<Long>> samples, final String stage, final long nanos) {
        samples.get(stage).add(Long.valueOf(nanos));
    }

    private static void printSummary(final Map<String, List<Long>> samples) {
        for (final Map.Entry<String, List<Long>> entry : samples.entrySet()) {
            final List<Long> values = new ArrayList<Long>(entry.getValue());
            if (values.isEmpty()) {
                continue;
            }
            Collections.sort(values);
            System.out.println(String.format("stage=%-12s p50=%10d ns p95=%10d ns max=%10d ns samples=%d",
                entry.getKey(), NearestRankPercentile.of(values, 0.50),
                NearestRankPercentile.of(values, 0.95), values.get(values.size() - 1).longValue(),
                values.size()));
        }
    }
}
```

- [ ] **Step 2: Add the Gradle runner with Flight Recorder enabled**

Append this task to `freeplane_plugin_graph/build.gradle`, immediately after the
`graphPerformanceDiagnostic` task. The recording file path is computed at
configuration time and the output directory is created in `doFirst`, because the
JVM starts before `main` can create it.

```groovy
final File boundaryProfileDirectory = file("$buildDir/boundary-correction-profile")
tasks.register('boundaryCorrectionProfile', JavaExec) {
    dependsOn tasks.named('testClasses')
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'org.freeplane.plugin.graph.performance.BoundaryCorrectionProfile'
    systemProperty 'java.awt.headless', 'true'
    jvmArgs '-Xmx2g'
    jvmArgs('-XX:StartFlightRecording=filename='
        + new File(boundaryProfileDirectory, 'correction-profile.jfr').absolutePath + ',settings=profile')
    doFirst {
        boundaryProfileDirectory.mkdirs()
    }
    outputs.dir(boundaryProfileDirectory)
}
```

- [ ] **Step 3: Compile and run the profile**

Run: `gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks -PTestLoggingFull`
Expected: BUILD SUCCESSFUL in roughly 3-6 minutes; the console prints one
`stage=... p50=... p95=...` line per stage; `freeplane_plugin_graph/build/boundary-correction-profile/`
contains `correction-profile.csv` and `correction-profile.jfr`.

- [ ] **Step 4: Attribute the hotspots with Flight Recorder**

Run: `~/.sdkman/candidates/java/21.0.8-zulu/bin/jfr view hot-methods freeplane_plugin_graph/build/boundary-correction-profile/correction-profile.jfr`
Expected: a methods table with sample counts. Record the first ten rows
verbatim; they are the numeric hotspot attribution for the report.

- [ ] **Step 5: Record the profile in the evidence document**

Create `docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`
with exactly this structure and your measured values filled in. Keep the section
headings as written; Task 4 appends below them without rewriting this section.

```markdown
# Graph Boundary Separation Performance Evidence

## Profile (Task 1)

Command: gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks -PTestLoggingFull

Workload: reference-2000-5000, 2000 nodes, 1200 enclosures.

| stage | p50 (ns) | p95 (ns) | max (ns) |
| --- | ---: | ---: | ---: |
| workerTotal | <measured> | <measured> | <measured> |
| directTotal | <measured> | <measured> | <measured> |
| separation | <measured> | <measured> | <measured> |
| hull | <measured> | <measured> | <measured> |
| plan | <measured> | <measured> | <measured> |
| apply | <measured> | <measured> | <measured> |

JFR command: ~/.sdkman/candidates/java/21.0.8-zulu/bin/jfr view hot-methods freeplane_plugin_graph/build/boundary-correction-profile/correction-profile.jfr

| rank | hot method | samples | share |
| --- | --- | ---: | ---: |
| 1 | <measured> | <measured> | <measured> |

Hotspot list: <one bullet per hotspot naming the code path, the per-frame call
count, and the measured cost, for example the child/parent containment scan with
1180 pair checks and 9440 HullGeometry.contains invocations per frame>.
```

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/BoundaryCorrectionProfile.java freeplane_plugin_graph/build.gradle docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md
git commit -m "test(graph-performance): add focused boundary correction profile and JFR hotspots"
```

## Task 2: Remove the per-frame verification and cap-set overhead

**Implementer tier:** Capable

**Lane:** integration

**Depends on:** 1

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java, freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould --tests org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould --tests org.freeplane.plugin.graph.geometry.GraphGeometryEngineShould

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java`

**Interfaces:**
- Consumes: `GraphProjection.nodes()/enclosures()/prominence()`, `ProjectedEnclosure.hullKey()/parentHull()/directNodes()/directEnclosures()`, `LayoutPositions.nodes()/anchors()`, `GraphGeometry.nodes()/hulls()`, `HullGeometry.exactPolygon()`, `EnclosureHullKey`, `ProjectedNodeKey`, `NodeGeometry.of(LayoutPoint, double)`, and the existing private helpers `computeHull`, `cachedGeometry`, `rememberGeometry`, `support`, `polySupport`, `nodeContribution`, `inBand`, `containsInclusive`.
- Produces: `public GraphGeometry GraphGeometryEngine.recomputeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics, GraphGeometry, Set<EnclosureHullKey>)`, equal to `computeHulls` for the same inputs while reusing unaffected hull instances; package-private `BoundarySeparationCorrection.ancestorExactContainmentChecks()`, `ancestorContainmentMemoHits()`, `capTraversals()`, `capCacheHits()`.

### Why these three optimizations

The Task 1 profile names the cost drivers; the code below removes each one
without changing a single decision:

1. **Affected-only hull recomputation.** On every round after a displacement the
   correction rebuilt all 1200 hulls through `GraphGeometryEngine.computeHulls`.
   `recomputeHulls` rebuilds only the hulls whose own anchor or direct nodes
   moved, plus their ancestors, and reuses every other `HullGeometry` instance.
2. **Ancestor-containment memo.** `detect` called `parentHull.contains(vertex)`
   for all 1180 enforced child/parent pairs on every frame (9440 exact
   orientation evaluations). The verdict is a pure function of the two hull
   geometries, so a bounded value-keyed memo answers repeated frames without the
   exact scan.
3. **Cap-set and support reuse plus early exit.** Candidate evaluation traversed
   the same `(hull, unit vector, band)` up to ten times per violation. A
   per-frame cache makes each distinct traversal run once, and the two full-band
   traversals prove immediately when neither side has any movable contributor.

The `ArrayList.contains` membership scan in `detect` is also replaced by a
`LinkedHashSet`; it is the same predicate with O(1) lookup.

- [ ] **Step 1: Add the affected-only recompute interface to GraphGeometryEngine**

Add `import java.util.LinkedHashSet;` to the import block of
`GraphGeometryEngine.java`, then add these two members immediately after the
existing `computeHulls` method. Do not modify `computeHulls`, `computeHull`,
`cachedGeometry`, or `rememberGeometry`.

```java
    /**
     * Recomputes only the hulls whose subtree changed since {@code previous}, reusing every other
     * hull. The result is equal to {@link #computeHulls} for the same inputs.
     *
     * @param previous geometry computed for the same projection; null, or a geometry whose node or
     *     enclosure keys do not match the projection, falls back to a full computation
     * @param directlyAffectedHulls hulls whose own anchor or direct nodes moved since
     *     {@code previous}; every ancestor of such a hull is recomputed as well
     */
    public GraphGeometry recomputeHulls(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final GraphGeometry previous,
            final Set<EnclosureHullKey> directlyAffectedHulls) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(directlyAffectedHulls, "directlyAffectedHulls");
        final GraphGeometry cached = cachedGeometry(projection, positions);
        if (cached != null) {
            return cached;
        }
        final Map<ProjectedNodeKey, ProjectedNode> nodesByKey =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        for (final ProjectedNode node : projection.nodes()) {
            if (nodesByKey.put(node.key(), node) != null) {
                throw new IllegalArgumentException("Duplicate projected node key " + node.key());
            }
        }
        final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByKey =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosuresByKey.put(enclosure.hullKey(), enclosure) != null) {
                throw new IllegalArgumentException("Duplicate projected enclosure hull key "
                    + enclosure.hullKey());
            }
        }
        if (!positions.nodes().keySet().equals(nodesByKey.keySet())) {
            throw new IllegalArgumentException("Layout positions must cover exactly the projected nodes");
        }
        if (!positions.anchors().keySet().equals(enclosuresByKey.keySet())) {
            throw new IllegalArgumentException("Layout anchors must cover exactly the projected enclosures");
        }
        if (!projection.prominence().keySet().equals(nodesByKey.keySet())) {
            throw new IllegalArgumentException("Prominence must cover exactly the projected nodes");
        }
        if (previous == null || !previous.nodes().keySet().equals(nodesByKey.keySet())
                || !previous.hulls().keySet().equals(enclosuresByKey.keySet())) {
            return computeHulls(projection, positions, metrics);
        }
        final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (final ProjectedNode node : projection.nodes()) {
            final LayoutPoint center = positions.nodes().get(node.key());
            final double radius = BASE_RADIUS * projection.prominence().get(node.key()).scale();
            nodeGeometry.put(node.key(), NodeGeometry.of(center, radius));
        }
        final Set<EnclosureHullKey> recomputed = ancestorsOf(enclosuresByKey, directlyAffectedHulls);
        final Map<EnclosureHullKey, HullGeometry> computed =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>(previous.hulls());
        final Set<EnclosureHullKey> complete = new HashSet<EnclosureHullKey>(previous.hulls().keySet());
        complete.removeAll(recomputed);
        final Set<EnclosureHullKey> visiting = new HashSet<EnclosureHullKey>();
        for (final EnclosureHullKey hullKey : recomputed) {
            computeHull(hullKey, enclosuresByKey, positions, nodeGeometry, metrics, computed, complete,
                visiting);
        }
        final Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            hulls.put(enclosure.hullKey(), computed.get(enclosure.hullKey()));
        }
        final GraphGeometry result = GraphGeometry.of(nodeGeometry, hulls);
        rememberGeometry(projection, positions, result);
        return result;
    }

    private static Set<EnclosureHullKey> ancestorsOf(
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByKey,
            final Set<EnclosureHullKey> directlyAffectedHulls) {
        final Set<EnclosureHullKey> affected = new LinkedHashSet<EnclosureHullKey>();
        for (final EnclosureHullKey hullKey : directlyAffectedHulls) {
            if (!enclosuresByKey.containsKey(hullKey)) {
                throw new IllegalArgumentException("Unknown affected hull " + hullKey);
            }
            affected.add(hullKey);
        }
        for (final EnclosureHullKey hullKey : directlyAffectedHulls) {
            EnclosureHullKey current = hullKey;
            while (enclosuresByKey.get(current).parentHull().isPresent()) {
                current = enclosuresByKey.get(current).parentHull().get();
                if (!affected.add(current)) {
                    break;
                }
            }
        }
        return affected;
    }
```

- [ ] **Step 2: Confirm the engine interface compiles and keeps the existing tests green**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.geometry.GraphGeometryEngineShould --tests org.freeplane.plugin.graph.geometry.HullGeometryShould`
Expected: PASS; `computeHulls` behavior is untouched.

- [ ] **Step 3: Memoize the ancestor-containment verdict and fix the membership scan**

In `BoundarySeparationCorrection.java`:

1. Add `import org.freeplane.plugin.graph.geometry.GraphGeometry;` to the imports.
2. Add these fields immediately after `private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();`:

```java
    private final AncestorContainmentMemo ancestorContainment = new AncestorContainmentMemo();
    private long capTraversals;
    private long capCacheHits;
```

3. Add these package-private accessors immediately after the package-private `BoundarySeparationCorrection(int maxDisplacementRounds)` constructor:

```java
    long ancestorExactContainmentChecks() {
        return ancestorContainment.exactChecks;
    }

    long ancestorContainmentMemoHits() {
        return ancestorContainment.memoHits;
    }

    long capTraversals() {
        return capTraversals;
    }

    long capCacheHits() {
        return capCacheHits;
    }
```

4. In `detect`, replace

```java
        final List<EnclosureHullKey> enforced = new ArrayList<EnclosureHullKey>();
```

with

```java
        final Set<EnclosureHullKey> enforced = new LinkedHashSet<EnclosureHullKey>();
```

5. In `detect`, replace the whole ancestor containment loop

```java
            final HullGeometry childHull = hulls.get(child);
            final HullGeometry parentHull = hulls.get(parent);
            for (final LayoutPoint vertex : childHull.exactPolygon()) {
                if (!parentHull.contains(vertex)) {
                    final EnclosureHullKey first;
                    final EnclosureHullKey second;
                    if (CanonicalLayoutKeys.hull(child).compareTo(CanonicalLayoutKeys.hull(parent)) <= 0) {
                        first = child;
                        second = parent;
                    }
                    else {
                        first = parent;
                        second = child;
                    }
                    violations.put(CanonicalLayoutKeys.pair(child, parent), new Violation(first, second,
                        BoundaryConflict.Kind.ANCESTOR_ESCAPE, 0.0, null));
                    break;
                }
            }
```

with

```java
            final HullGeometry childHull = hulls.get(child);
            final HullGeometry parentHull = hulls.get(parent);
            if (!ancestorContainment.isContained(parentHull, childHull)) {
                final EnclosureHullKey first;
                final EnclosureHullKey second;
                if (CanonicalLayoutKeys.hull(child).compareTo(CanonicalLayoutKeys.hull(parent)) <= 0) {
                    first = child;
                    second = parent;
                }
                else {
                    first = parent;
                    second = child;
                }
                violations.put(CanonicalLayoutKeys.pair(child, parent), new Violation(first, second,
                    BoundaryConflict.Kind.ANCESTOR_ESCAPE, 0.0, null));
            }
```

`containsInclusive(parent, child)` evaluates exactly the same predicate as the
replaced loop (every child vertex inside the parent), so the verdict is
unchanged; the memo only answers it without the exact orientation work when the
same two hull geometries were already checked.

6. Append these nested classes inside `BoundarySeparationCorrection`, before its
final closing brace:

```java
    private static final class AncestorContainmentMemo {
        private final Map<HullPair, Boolean> contained = new ContainmentCache();
        private long exactChecks;
        private long memoHits;

        private synchronized boolean isContained(final HullGeometry parent, final HullGeometry child) {
            final HullPair key = new HullPair(parent, child);
            final Boolean cached = contained.get(key);
            if (cached != null) {
                memoHits++;
                return cached.booleanValue();
            }
            exactChecks++;
            final boolean result = containsInclusive(parent, child);
            contained.put(key, Boolean.valueOf(result));
            return result;
        }
    }

    private static final class ContainmentCache extends LinkedHashMap<HullPair, Boolean> {
        private static final long serialVersionUID = 1L;
        private static final int MAXIMUM_ENTRIES = 8192;

        private ContainmentCache() {
            super(1024, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<HullPair, Boolean> eldest) {
            return size() > MAXIMUM_ENTRIES;
        }
    }

    private static final class HullPair {
        private final HullGeometry parent;
        private final HullGeometry child;

        private HullPair(final HullGeometry parent, final HullGeometry child) {
            this.parent = parent;
            this.child = child;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HullPair)) {
                return false;
            }
            final HullPair that = (HullPair) other;
            return parent.equals(that.parent) && child.equals(that.child);
        }

        @Override
        public int hashCode() {
            return 31 * parent.hashCode() + child.hashCode();
        }
    }
```

- [ ] **Step 4: Confirm the correction suite still passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`
Expected: PASS, including `ancestorEscapePairKeysReportEscapingChildren` and
`frozenRealCaseViolatesBeforeAndSettlesInOneRoundAfterCorrection`.

- [ ] **Step 5: Add the per-frame support and cap-set cache with the plan early exit**

In `BoundarySeparationCorrection.java`:

1. Replace the whole `plan` method with:

```java
    private PlanOutcome plan(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedNodeKey> pinnedNodes, final List<Violation> violations) {
        final PlanOutcome outcome = new PlanOutcome();
        final Set<MapReferenceId> rigidMaps = rigidMaps(pins);
        final FrameCapCache cache = new FrameCapCache(projection, enclosuresByHull, hulls, positions, metrics,
            pinnedNodes);
        for (final Violation violation : violations) {
            if (violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE) {
                outcome.conflicts.put(violation.pairKey, conflict(violation,
                    BoundaryConflict.Reason.STRUCTURAL_ESCAPE,
                    blockingPins(violation, enclosuresByHull, pins)));
                continue;
            }
            if (!violation.first.mapReferenceId().equals(violation.second.mapReferenceId())) {
                planCrossMap(violation, rigidMaps, enclosuresByHull, pins, outcome);
            }
            else {
                planSameMap(violation, enclosuresByHull, pins, outcome, cache);
            }
        }
        capTraversals += cache.traversalsComputed;
        capCacheHits += cache.traversalHits;
        return outcome;
    }
```

2. Replace the whole `planSameMap` method with:

```java
    private static void planSameMap(final Violation violation,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins,
            final PlanOutcome outcome, final FrameCapCache cache) {
        final Candidate candidate = selectCandidate(violation, cache);
        if (candidate == null) {
            outcome.conflicts.put(violation.pairKey, conflict(violation,
                BoundaryConflict.Reason.IMMOVABLE_SIDES, blockingPins(violation, enclosuresByHull, pins)));
            return;
        }
        if (candidate.firstMagnitude > 0.0) {
            final Traversal moves = cache.traversal(violation.first, candidate.firstUnit,
                candidate.firstMagnitude);
            addMoves(outcome, moves, candidate.firstVector);
        }
        if (candidate.secondMagnitude > 0.0) {
            final Traversal moves = cache.traversal(violation.second, candidate.secondUnit,
                candidate.secondMagnitude);
            addMoves(outcome, moves, candidate.secondVector);
        }
        outcome.movedPairs.add(violation.pairKey);
    }
```

3. Replace the whole `selectCandidate` method with:

```java
    private static Candidate selectCandidate(final Violation violation, final FrameCapCache cache) {
        final LayoutPoint translation = violation.translation;
        final double magnitude = Math.hypot(translation.x(), translation.y());
        final LayoutPoint firstUnit = LayoutPoint.of(translation.x() / magnitude, translation.y() / magnitude);
        final LayoutPoint secondUnit = negate(firstUnit);
        final Traversal firstFull = cache.traversal(violation.first, firstUnit, magnitude);
        final Traversal secondFull = cache.traversal(violation.second, secondUnit, magnitude);
        if (firstFull.movedNodes.isEmpty() && firstFull.movedAnchors.isEmpty()
                && secondFull.movedNodes.isEmpty() && secondFull.movedAnchors.isEmpty()) {
            return null;
        }
        final double half = magnitude / 2.0;
        if (valid(violation.first, firstUnit, half, cache) && valid(violation.second, secondUnit, half, cache)) {
            return new Candidate(firstUnit, half, scale(translation, -0.5), secondUnit, half,
                scale(translation, 0.5));
        }
        if (valid(violation.first, firstUnit, magnitude, cache)) {
            return new Candidate(firstUnit, magnitude, negate(translation), secondUnit, 0.0,
                LayoutPoint.of(0.0, 0.0));
        }
        if (valid(violation.second, secondUnit, magnitude, cache)) {
            return new Candidate(firstUnit, 0.0, LayoutPoint.of(0.0, 0.0), secondUnit, magnitude, translation);
        }
        final Double firstDepth = complementaryDepth(violation.first, firstUnit, magnitude, cache);
        if (firstDepth != null && firstDepth.doubleValue() > 0.0 && firstDepth.doubleValue() < magnitude) {
            final double firstMagnitude = firstDepth.doubleValue();
            final double secondMagnitude = magnitude - firstMagnitude;
            if (valid(violation.first, firstUnit, firstMagnitude, cache)
                    && valid(violation.second, secondUnit, secondMagnitude, cache)) {
                return new Candidate(firstUnit, firstMagnitude, scale(translation, -firstMagnitude / magnitude),
                    secondUnit, secondMagnitude, scale(translation, secondMagnitude / magnitude));
            }
        }
        final Double secondDepth = complementaryDepth(violation.second, secondUnit, magnitude, cache);
        if (secondDepth != null && secondDepth.doubleValue() > 0.0 && secondDepth.doubleValue() < magnitude) {
            final double secondMagnitude = secondDepth.doubleValue();
            final double firstMagnitude = magnitude - secondMagnitude;
            if (valid(violation.first, firstUnit, firstMagnitude, cache)
                    && valid(violation.second, secondUnit, secondMagnitude, cache)) {
                return new Candidate(firstUnit, firstMagnitude, scale(translation, -firstMagnitude / magnitude),
                    secondUnit, secondMagnitude, scale(translation, secondMagnitude / magnitude));
            }
        }
        return null;
    }
```

The two full-band traversals warm the cache before the preference order runs;
returning `null` when both hold no movable node and no movable anchor is exactly
equivalent to the old fall-through, because every candidate shape uses a band no
larger than the violation magnitude, and `inBand` pruning makes every smaller
cap set a subset of the full-band one. The candidate preference order and tie
semantics are unchanged.

4. Replace the whole `valid` method with:

```java
    private static boolean valid(final EnclosureHullKey hull, final LayoutPoint unit, final double magnitude,
            final FrameCapCache cache) {
        final Traversal traversal = cache.traversal(hull, unit, magnitude);
        if (traversal.movedNodes.isEmpty() && traversal.movedAnchors.isEmpty()) {
            return false;
        }
        for (final PinDepth pin : traversal.pins) {
            if (pin.depth < magnitude) {
                return false;
            }
        }
        return true;
    }
```

5. Replace the whole `complementaryDepth` method with:

```java
    private static Double complementaryDepth(final EnclosureHullKey hull, final LayoutPoint unit,
            final double magnitude, final FrameCapCache cache) {
        final Traversal traversal = cache.traversal(hull, unit, magnitude);
        double best = Double.POSITIVE_INFINITY;
        for (final PinDepth pin : traversal.pins) {
            if (pin.depth > 0.0 && pin.depth < magnitude) {
                best = Math.min(best, pin.depth);
            }
        }
        return best == Double.POSITIVE_INFINITY ? null : Double.valueOf(best);
    }
```

6. Delete the whole `traverse` method and the whole static `collectCap` method;
they are replaced by `FrameCapCache.traversal` and `FrameCapCache.collectCap`.
Keep `support`, `nodeContribution`, `polySupport`, and `inBand` unchanged.

7. Append these nested classes next to `AncestorContainmentMemo`:

```java
    private static final class FrameCapCache {
        private final GraphProjection projection;
        private final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull;
        private final Map<EnclosureHullKey, HullGeometry> hulls;
        private final LayoutPositions positions;
        private final GeometryTextMetrics metrics;
        private final Set<ProjectedNodeKey> pinnedNodes;
        private final Map<SupportKey, Double> supports = new LinkedHashMap<SupportKey, Double>();
        private final Map<CapKey, Traversal> traversals = new LinkedHashMap<CapKey, Traversal>();
        private long traversalsComputed;
        private long traversalHits;

        private FrameCapCache(final GraphProjection projection,
                final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
                final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
                final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
            this.projection = projection;
            this.enclosuresByHull = enclosuresByHull;
            this.hulls = hulls;
            this.positions = positions;
            this.metrics = metrics;
            this.pinnedNodes = pinnedNodes;
        }

        private double support(final EnclosureHullKey hull, final LayoutPoint unit) {
            final SupportKey key = new SupportKey(hull, unit);
            final Double cached = supports.get(key);
            if (cached != null) {
                return cached.doubleValue();
            }
            final double value = BoundarySeparationCorrection.support(hull, unit, projection, enclosuresByHull,
                hulls, positions, metrics);
            supports.put(key, Double.valueOf(value));
            return value;
        }

        private Traversal traversal(final EnclosureHullKey hull, final LayoutPoint unit, final double band) {
            final CapKey key = new CapKey(hull, unit, band);
            final Traversal cached = traversals.get(key);
            if (cached != null) {
                traversalHits++;
                return cached;
            }
            traversalsComputed++;
            final Traversal computed = new Traversal();
            collectCap(hull, unit, band, computed);
            traversals.put(key, computed);
            return computed;
        }

        private void collectCap(final EnclosureHullKey hull, final LayoutPoint unit, final double band,
                final Traversal traversal) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            if (enclosure == null) {
                throw new BoundarySeparationException("Missing enclosure for hull "
                    + CanonicalLayoutKeys.hull(hull));
            }
            final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
            final double support = support(hull, unit);
            if (empty) {
                traversal.movedAnchors.add(hull);
                return;
            }
            for (final ProjectedNodeKey node : enclosure.directNodes()) {
                final double contribution = nodeContribution(node, unit, positions, projection);
                if (inBand(contribution, support, band)) {
                    if (pinnedNodes.contains(node)) {
                        traversal.pins.add(new PinDepth(node, support - contribution));
                    }
                    else {
                        traversal.movedNodes.add(node);
                    }
                }
            }
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                final double contribution = polySupport(child, unit, hulls) + HULL_CLEARANCE;
                if (inBand(contribution, support, band)) {
                    collectCap(child, unit, band, traversal);
                }
            }
        }
    }

    private static final class SupportKey {
        private final EnclosureHullKey hull;
        private final long unitX;
        private final long unitY;

        private SupportKey(final EnclosureHullKey hull, final LayoutPoint unit) {
            this.hull = hull;
            this.unitX = Double.doubleToLongBits(unit.x());
            this.unitY = Double.doubleToLongBits(unit.y());
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SupportKey)) {
                return false;
            }
            final SupportKey that = (SupportKey) other;
            return unitX == that.unitX && unitY == that.unitY && hull.equals(that.hull);
        }

        @Override
        public int hashCode() {
            int result = hull.hashCode();
            result = 31 * result + (int) (unitX ^ (unitX >>> 32));
            result = 31 * result + (int) (unitY ^ (unitY >>> 32));
            return result;
        }
    }

    private static final class CapKey {
        private final EnclosureHullKey hull;
        private final long unitX;
        private final long unitY;
        private final long band;

        private CapKey(final EnclosureHullKey hull, final LayoutPoint unit, final double band) {
            this.hull = hull;
            this.unitX = Double.doubleToLongBits(unit.x());
            this.unitY = Double.doubleToLongBits(unit.y());
            this.band = Double.doubleToLongBits(band);
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CapKey)) {
                return false;
            }
            final CapKey that = (CapKey) other;
            return unitX == that.unitX && unitY == that.unitY && band == that.band && hull.equals(that.hull);
        }

        @Override
        public int hashCode() {
            int result = hull.hashCode();
            result = 31 * result + (int) (unitX ^ (unitX >>> 32));
            result = 31 * result + (int) (unitY ^ (unitY >>> 32));
            result = 31 * result + (int) (band ^ (band >>> 32));
            return result;
        }
    }
```

The cache is scoped to one `plan` call, so every key is evaluated against one
projection, one hull map, one position map, one metrics instance, and one pinned
set; those are the values its constructor captures. `Traversal` instances are
fully built before they are cached and are only read afterwards (`addMoves`
iterates, `valid` and `complementaryDepth` iterate), so sharing them is safe.

- [ ] **Step 6: Confirm the correction suite still passes with the cache**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`
Expected: PASS with the same positions, displacements, and conflicts as before
the change.

- [ ] **Step 7: Use affected-only hull recomputation across correction rounds**

In `BoundarySeparationCorrection.java`:

1. In `separate`, immediately after

```java
        final Set<ProjectedNodeKey> pinnedNodes = pinnedNodes(pins);
```

add

```java
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey = nodeHulls(projection);
```

2. Immediately after

```java
        LayoutPositions current = positions;
        final Map<String, LayoutPoint> field = new LinkedHashMap<String, LayoutPoint>();
```

add

```java
        GraphGeometry geometry = null;
        LayoutPositions hullPositions = null;
```

3. Replace

```java
            final long planStart = System.nanoTime();
            final long hullStart = System.nanoTime();
            final Map<EnclosureHullKey, HullGeometry> hulls = geometryEngine
                .computeHulls(projection, current, metrics).hulls();
            hullNanos += System.nanoTime() - hullStart;
```

with

```java
            final long planStart = System.nanoTime();
            final long hullStart = System.nanoTime();
            if (geometry == null) {
                geometry = geometryEngine.computeHulls(projection, current, metrics);
            }
            else {
                geometry = geometryEngine.recomputeHulls(projection, current, metrics, geometry,
                    changedHulls(nodeHullsByKey, hullPositions, current));
            }
            final Map<EnclosureHullKey, HullGeometry> hulls = geometry.hulls();
            hullPositions = current;
            hullNanos += System.nanoTime() - hullStart;
```

4. Append these helper methods next to `enclosuresByHull` and `parents`:

```java
    private static Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHulls(final GraphProjection projection) {
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> result =
            new LinkedHashMap<ProjectedNodeKey, List<EnclosureHullKey>>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            for (final ProjectedNodeKey node : enclosure.directNodes()) {
                List<EnclosureHullKey> hulls = result.get(node);
                if (hulls == null) {
                    hulls = new ArrayList<EnclosureHullKey>(1);
                    result.put(node, hulls);
                }
                hulls.add(enclosure.hullKey());
            }
        }
        return result;
    }

    private static Set<EnclosureHullKey> changedHulls(
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey, final LayoutPositions before,
            final LayoutPositions after) {
        final Set<EnclosureHullKey> changed = new LinkedHashSet<EnclosureHullKey>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : after.nodes().entrySet()) {
            final LayoutPoint previous = before.nodes().get(entry.getKey());
            if (previous != null && !previous.equals(entry.getValue())) {
                final List<EnclosureHullKey> hulls = nodeHullsByKey.get(entry.getKey());
                if (hulls != null) {
                    changed.addAll(hulls);
                }
            }
        }
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : after.anchors().entrySet()) {
            final LayoutPoint previous = before.anchors().get(entry.getKey());
            if (previous != null && !previous.equals(entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }
```

`recomputeHulls` closes the directly affected hulls over their ancestors, so a
moved leaf node marks its own hull, its parent chain, and the map root; every
other hull instance is reused and its value stays byte-identical.

- [ ] **Step 8: Run the focused and then the full plugin suite**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould --tests org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould --tests org.freeplane.plugin.graph.geometry.GraphGeometryEngineShould`
Expected: PASS, 5 suites, zero failures.

Run: `gradle :freeplane_plugin_graph:test -PTestLoggingFull`
Expected: PASS, the whole plugin suite.

- [ ] **Step 9: Re-run the focused profile and record the improvement**

Run: `gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks -PTestLoggingFull`
Expected: the printed `hull` and `plan` lines (the `plan` stage fuses hull
recomputation, detection, and candidate planning) drop from the Task 1 reading.
Planner-captured remediated reading for the same workload (same host, from
this plan's code applied to a scratch copy): `hull` p50 about 1 ms, the fused
detection-plus-planning stage p50 about 5 ms and p95 about 7 ms, so
`directTotal` is dominated by the unchanged `separation` stage (p50 about
20 ms); `workerTotal` p50 about 30 ms and p95 about 47 ms. Record the new CSV
values in the task report next to the Task 1 numbers; do not edit the committed
Task 1 profile section yet.

- [ ] **Step 10: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java
git commit -m "perf(graph-layout): recompute affected hulls and memoize boundary verification"
```

## Task 3: Falsifiable regression tests for the optimized paths

**Implementer tier:** Advanced

**Lane:** integration

**Depends on:** 2

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngineShould.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.geometry.GraphGeometryEngineShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngineShould.java` (append the recompute test before the final closing brace; add `java.util.LinkedHashMap` and `java.util.Map` imports and a small helper)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` (append the four optimization tests before the final closing brace; no new imports are needed)

**Interfaces:**
- Consumes: `GraphGeometryEngine.recomputeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics, GraphGeometry, Set<EnclosureHullKey>)` from Task 2; `BoundarySeparationCorrection.ancestorExactContainmentChecks()`, `ancestorContainmentMemoHits()`, `capTraversals()`, `capCacheHits()` from Task 2; the existing private fixtures `frozenFixture()`, `siblingFixture(...)`, `positions(...)`, `nodeEntry(...)`, `anchorEntry(...)`, `pin(...)`, `key(...)`, `hull(...)`, `parent(...)`, `child(...)`, and the `FrozenFixture` fields.
- Produces: five tests that fail without the Task 2 optimizations and pass with them: `recomputeHullsReusesUnaffectedHullsAndMatchesTheFullComputation`, `ancestorContainmentIsMemoizedAcrossIdenticalFrames`, `ancestorContainmentMemoRechecksAHullWhoseGeometryChanged`, `capSetsAreReusedWithinAFrame`, `candidateSelectionStopsWhenNoSideHasAMovableContributor`.

Each test pins a mechanism, not a timing: a removed optimization makes the
assertion fail, and a changed verdict makes a correctness assertion fail.

- [ ] **Step 1: Add the engine reuse test**

Add `import java.util.LinkedHashMap;` and `import java.util.Map;` to
`GraphGeometryEngineShould.java`, then append this test and helper immediately
before the final closing brace of the class.

```java
    @Test
    public void recomputeHullsReusesUnaffectedHullsAndMatchesTheFullComputation() {
        EnclosureHullKey rootKey = hullKey("root");
        EnclosureHullKey firstKey = hullKey("first");
        EnclosureHullKey leafKey = hullKey("leaf");
        EnclosureHullKey otherKey = hullKey("other");
        ProjectedNodeKey leafNode = nodeKey("leaf-node");
        ProjectedNodeKey otherNode = nodeKey("other-node");
        ProjectedEnclosure root = enclosure(rootKey, "root", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(firstKey, otherKey), BoundaryTier.SUBTLE);
        ProjectedEnclosure first = enclosure(firstKey, "first", Optional.of(rootKey),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(leafKey), BoundaryTier.SUBTLE);
        ProjectedEnclosure leaf = enclosure(leafKey, "leaf", Optional.of(firstKey),
            Collections.singletonList(leafNode), Collections.<EnclosureHullKey>emptyList(), BoundaryTier.SUBTLE);
        ProjectedEnclosure other = enclosure(otherKey, "other", Optional.of(rootKey),
            Collections.singletonList(otherNode), Collections.<EnclosureHullKey>emptyList(), BoundaryTier.SUBTLE);
        GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(ProjectedNode.of(leafNode, SafeNodeLabel.of("leaf", "leaf"), "map", false),
                ProjectedNode.of(otherNode, SafeNodeLabel.of("other", "other"), "map", false)),
            Arrays.asList(root, first, leaf, other), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        GeometryTextMetrics metrics = new RecordingMetrics();
        GraphGeometryEngine engine = new GraphGeometryEngine();
        GraphGeometry full = engine.computeHulls(projection,
            geometryLayout(leafNode, 0.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics);

        GraphGeometry recomputed = engine.recomputeHulls(projection,
            geometryLayout(leafNode, 10.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics,
            full, Collections.singleton(leafKey));

        assertThat(recomputed).isEqualTo(new GraphGeometryEngine().computeHulls(projection,
            geometryLayout(leafNode, 10.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics));
        assertThat(recomputed.hulls().get(otherKey)).isSameAs(full.hulls().get(otherKey));
        assertThat(recomputed.hulls().get(leafKey)).isNotSameAs(full.hulls().get(leafKey));
        assertThat(recomputed.hulls().get(firstKey)).isNotSameAs(full.hulls().get(firstKey));
        assertThat(recomputed.hulls().get(rootKey)).isNotSameAs(full.hulls().get(rootKey));
        assertThat(new GraphGeometryEngine().recomputeHulls(projection,
            geometryLayout(leafNode, 0.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics, null,
            Collections.<EnclosureHullKey>emptySet())).isEqualTo(full);
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hull, final String label,
            final Optional<EnclosureHullKey> parent, final List<ProjectedNodeKey> nodes,
            final List<EnclosureHullKey> children, final BoundaryTier tier) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "map", parent, nodes, children, false,
            tier);
    }

    private static LayoutPositions geometryLayout(final ProjectedNodeKey leafNode, final double leafX,
            final ProjectedNodeKey otherNode, final double otherX, final EnclosureHullKey rootKey,
            final EnclosureHullKey firstKey, final EnclosureHullKey leafKey,
            final EnclosureHullKey otherKey) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(leafNode, LayoutPoint.of(leafX, 0.0));
        nodes.put(otherNode, LayoutPoint.of(otherX, 0.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(rootKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(firstKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(leafKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(otherKey, LayoutPoint.of(otherX, 0.0));
        return LayoutPositions.of(nodes, anchors);
    }
```

The `isSameAs`/`isNotSameAs` assertions are the falsifier: a fallback to a full
recomputation makes every hull a new instance and fails the `isSameAs` on
`otherKey`, while a still-too-eager recompute fails the `isNotSameAs` checks.

- [ ] **Step 2: Add the ancestor-containment memo tests**

Append both tests immediately before the final closing brace of
`BoundarySeparationCorrectionShould`.

```java
    @Test
    public void ancestorContainmentIsMemoizedAcrossIdenticalFrames() {
        final FrozenFixture fixture = frozenFixture();
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        final BoundarySeparationResult first = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
        final long checksAfterFirstFrame = correction.ancestorExactContainmentChecks();

        final BoundarySeparationResult second = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);

        assertThat(checksAfterFirstFrame).isGreaterThan(0);
        assertThat(correction.ancestorExactContainmentChecks()).isEqualTo(checksAfterFirstFrame);
        assertThat(correction.ancestorContainmentMemoHits()).isGreaterThan(0);
        assertThat(second.positions()).isEqualTo(first.positions());
        assertThat(second.diagnostics().rounds()).isEqualTo(first.diagnostics().rounds());
        assertThat(second.diagnostics().hullResidualViolations())
            .isEqualTo(first.diagnostics().hullResidualViolations());
        assertThat(second.diagnostics().conflicts().size()).isEqualTo(first.diagnostics().conflicts().size());
        assertThat(second.diagnostics().residualHullPairs())
            .isEqualTo(first.diagnostics().residualHullPairs());
    }

    @Test
    public void ancestorContainmentMemoRechecksAHullWhoseGeometryChanged() {
        final ProjectedNodeKey childNode = key("memo-child-node");
        final EnclosureHullKey rootHull = hull("memo-root");
        final EnclosureHullKey childHull = hull("memo-child");
        final GraphProjection projection = projection(Collections.singletonList(childNode),
            Arrays.asList(parent(rootHull, "root", Collections.singletonList(childHull)),
                child(childHull, "child", rootHull, Collections.singletonList(childNode))));
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        correction.apply(projection,
            positions(Collections.singletonList(nodeEntry(childNode, 0.0, 0.0)),
                Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(childHull, 0.0, 0.0))),
            METRICS, Collections.<PinProjection>emptyList());
        final long checksAfterFirstFrame = correction.ancestorExactContainmentChecks();

        correction.apply(projection,
            positions(Collections.singletonList(nodeEntry(childNode, 5.0, 0.0)),
                Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(childHull, 0.0, 0.0))),
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(checksAfterFirstFrame).isEqualTo(1);
        assertThat(correction.ancestorExactContainmentChecks()).isEqualTo(2);
        assertThat(correction.ancestorContainmentMemoHits()).isZero();
    }
```

The first test fails if the memo is removed (the exact-check counter grows on
the second frame). The second fails if the memo were keyed by hull identity or
by the pair alone instead of by both hull values, because the moved child and
its parent produce new geometries that must be checked again.

- [ ] **Step 3: Add the cap-set and early-exit tests**

Append both tests immediately after the memo tests.

```java
    @Test
    public void capSetsAreReusedWithinAFrame() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        final BoundarySeparationResult result = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
        final BoundarySeparationResult fresh = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(correction.capCacheHits()).isGreaterThan(0);
        assertThat(result.positions()).isEqualTo(fresh.positions());
        assertThat(result.appliedDisplacements()).isEqualTo(fresh.appliedDisplacements());
    }

    @Test
    public void candidateSelectionStopsWhenNoSideHasAMovableContributor() {
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Collections.singletonList(aPin),
            Collections.singletonList(bPin),
            Arrays.asList(nodeEntry(aPin, 0.0, 30.0), nodeEntry(bPin, 38.0, 30.0)),
            Arrays.asList(pin(aPin, 0.0, 30.0), pin(bPin, 38.0, 30.0)));
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        final BoundarySeparationResult result = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);

        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(correction.capTraversals()).isEqualTo(4);
        assertThat(correction.capCacheHits()).isZero();
    }
```

`capSetsAreReusedWithinAFrame` fails without the per-frame cache because the
mixed complementary split re-traverses the same full-band key and
`capCacheHits()` stays zero. `candidateSelectionStopsWhenNoSideHasAMovableContributor`
fails without the early exit: the cache-bearing but non-early-exit candidate
sequence traverses the half bands as well, doubling the traversal count from 4
to 8 across the in-loop and terminal `plan` calls.

- [ ] **Step 4: Run the tests and confirm green**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.geometry.GraphGeometryEngineShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould -PTestLoggingFull`
Expected: BUILD SUCCESSFUL; the two suites report their existing tests plus the
five new ones with zero failures.

- [ ] **Step 5: Commit the tests**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngineShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "test(graph-layout): pin memoization and cap reuse with falsifiable tests"
```

- [ ] **Step 6: Prove red-before against the pre-remediation production code**

Run this read-only probe; it extracts the current test tree, restores only the
two production files from the pre-remediation commit `HEAD~1`, and runs the same
two suites.

```bash
rm -rf /tmp/boundary-remediation-red
mkdir -p /tmp/boundary-remediation-red
git archive HEAD | tar -x -C /tmp/boundary-remediation-red
git show HEAD~1:freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java > /tmp/boundary-remediation-red/freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java
git show HEAD~1:freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java > /tmp/boundary-remediation-red/freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java
JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu gradle -p /tmp/boundary-remediation-red :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.geometry.GraphGeometryEngineShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould
rm -rf /tmp/boundary-remediation-red
```

Expected: the probe build fails in `compileTestJava` with errors naming
`recomputeHulls`, `ancestorExactContainmentChecks`, and the other Task 2
members. Record the first ten compiler errors in the task report as the
red-before evidence. Confirm afterwards that the active worktree is clean and
`git status --porcelain` prints nothing.

- [ ] **Step 7: Record the red-before evidence in the report**

In the implementer report, state for each of the five tests which optimization
it pins, the exact assertion that fails without it, and the red-before output
captured in Step 6. Do not create a second commit for this step.

## Task 4: Extend the process deadline, re-run the strict gate, and record evidence

**Implementer tier:** Standard

**Lane:** integration

**Depends on:** 3

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java, docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.performance.PerformanceTripwiresShould

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java` (line 83, the `PROCESS_DEADLINE_NANOS` constant only)
- Modify: `docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md` (append the strict-gate section created by Task 1; do not rewrite the profile section)

**Interfaces:**
- Consumes: the Task 1 profile section already present in the evidence document; the strict-gate command and the ledger written by `GraphWorkspacePerformanceDiagnostic` to `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`; the run archive baseline ledger at `/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20260912-211532-f0569035/archives/diagnostics/baseline-strict-gate/baseline-performance-ledger.csv`.
- Produces: a committed evidence document that records the four strict `reference-2000-5000` rows, the remediated ledger SHA-256, the baseline ledger SHA-256 `8bd8eb5fb440a7ced1e3586ad5658af8945a3824ba258a91b01d3c485516163a`, and the environment waivers for `force` and `edt-swap`.

### Gate expectations

The strict diagnostic exits non-zero whenever any row fails, and `force` and
`edt-swap` already fail on the untouched baseline commit `aa6ac48b02`. That
non-zero exit is therefore expected and is **not** a reason to report
`BLOCKED`; the pass criterion for this segment is the two remediated rows:

- `reference-2000-5000` / `full-worker` p95 at or below 51,985,400 ns (baseline) or at least below its 100,000,000 ns strict budget.
- `reference-2000-5000` / `accepted-batch-first-frame` p95 at or below 76,344,593 ns (baseline) or at least below its 150,000,000 ns strict budget.

`force` and `edt-swap` are environment waivers. No threshold, workload, sample
count, or stage mapping may change. The planner-captured remediated worker
profile for the same workload is p50 about 29.5 ms and p95 about 47.0 ms, at or
below the 51,985,400 ns baseline for `full-worker`; treat that as a sanity
check, not as the measurement.

- [ ] **Step 1: Extend the process deadline constant**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`, change exactly line 83 from

```java
    public static final long PROCESS_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(10L);
```

to

```java
    public static final long PROCESS_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(40L);
```

Change nothing else in the file. The abort message string still reads
`Ten-minute diagnostic process deadline exceeded`; it is outside the authorized
change and only appears if a future run aborts.

- [ ] **Step 2: Confirm the diagnostic support still compiles and passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.performance.PerformanceTripwiresShould`
Expected: PASS, zero failures.

- [ ] **Step 3: Commit the deadline change before measuring**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
git commit -m "test(graph-performance): extend strict-gate process deadline to 40 minutes"
```

- [ ] **Step 4: Run the strict gate and capture the evidence**

This run takes about 36 minutes on this host; use a tool timeout well above 40
minutes and do not interrupt it. The previous authoritative run at the same base
took 36m43s.

Run: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull`
Expected: the diagnostic completes and writes the ledger, then exits 1 with
`One or more performance ledger rows failed` because of the two waivers. The
output directory contains exactly `performance-ledger.csv`, `two-map.fpg`,
`three-map.fpg`, and `reference-2000-5000.fpg`.

- [ ] **Step 5: Extract the four strict rows and the ledger hash**

Run: `sha256sum freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`
Run: `grep -E "^reference-2000-5000,(full-worker|accepted-batch-first-frame|force|edt-swap)," freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`
Expected: four CSV lines with `warmupCount=400`, `measuredCount=300`,
`failureCount=0`, `discardCount=0`. `full-worker` and
`accepted-batch-first-frame` must satisfy the pass criterion; `force` and
`edt-swap` are the documented waivers. If a target row misses the criterion, do
not change a threshold and do not add a parallel path: stop and report
`DONE_WITH_CONCERNS` with the complete row and the Task 1 profile numbers.

- [ ] **Step 6: Append the strict-gate section to the evidence document**

Append this section to
`docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`
after the existing `## Profile (Task 1)` section. Keep the profile section
verbatim; fill every placeholder with the measured values and the verbatim CSV
lines from Step 5.

```markdown
## Strict gate (Task 4)

Command: gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull

Result: exit code 1 after a complete run; the non-zero exit comes from the two
pre-existing environment waivers below and is not a boundary-separation
regression.

Ledger: freeplane_plugin_graph/build/graph-performance/performance-ledger.csv
Ledger SHA-256: <measured>
Baseline ledger: archives/diagnostics/baseline-strict-gate/baseline-performance-ledger.csv
Baseline ledger SHA-256: 8bd8eb5fb440a7ced1e3586ad5658af8945a3824ba258a91b01d3c485516163a
Pre-remediation feature ledger SHA-256: 5d7a2aeec4891d30e30ede52a0c1f2023bacbd2d3cdd1c8b56a370de0e8c88f3

| scenario / stage | baseline p95 (ns) | pre-remediation p95 (ns) | remediated p50 (ns) | remediated p95 (ns) | remediated p99 (ns) | strict budget (ns) | verdict |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| reference-2000-5000 / full-worker | 51985400 | 137663516 | <measured> | <measured> | <measured> | 100000000 | <PASS or FAIL> |
| reference-2000-5000 / accepted-batch-first-frame | 76344593 | 160930981 | <measured> | <measured> | <measured> | 150000000 | <PASS or FAIL> |
| reference-2000-5000 / force | 268972130 | 178961459 | <measured> | <measured> | <measured> | 50000000 | waiver |
| reference-2000-5000 / edt-swap | 2700023 | 2398572 | <measured> | <measured> | <measured> | 2000000 | waiver |

Remediated ledger rows, verbatim from Step 5 (one line per row, no surrounding fence):

<paste the four CSV lines from Step 5 here>

Environment waivers: `force` and `edt-swap` fail on the untouched baseline
commit `aa6ac48b02` with the same or worse p95, on a machine where the boundary
correction is absent; they are environment conditions and not regressions of
this feature segment.

Pass criterion: `full-worker` and `accepted-batch-first-frame` are at or below
their baseline p95 values (51,985,400 ns and 76,344,593 ns) or at least under
their strict budgets (100,000,000 ns and 150,000,000 ns). No threshold,
workload, sample count, or measurement mapping changed; the only diagnostic
change in this segment is `PROCESS_DEADLINE_NANOS` from 10 to 40 minutes.
```

- [ ] **Step 7: Verify the evidence document is complete and consistent**

Run: `git diff --stat`
Run: `grep -n "Ledger SHA-256" docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`
Expected: the evidence document changed and contains both the `## Profile
(Task 1)` and `## Strict gate (Task 4)` sections, both ledger hashes, the four
remediated CSV rows, and the two waivers.

- [ ] **Step 8: Commit the evidence**

```bash
git add docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md
git commit -m "docs(graph-performance): record remediated boundary strict-gate evidence"
```
