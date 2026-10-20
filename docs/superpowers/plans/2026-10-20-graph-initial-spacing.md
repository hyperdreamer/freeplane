# Graph Initial Spacing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make fresh graph rings use a preferred 24.0 model-unit gap while preserving the 6.0 collision floor, the 8.0 sibling gutter, pin coordinates, existing forces, and corrected worker publication.

**Architecture:** Change only the ring's layout-owned constant and the existing packing feasibility predicate. Test-only independent geometry, a frozen-fixture probe, and isolated C0/candidate JVMs establish seed behavior, consumer propagation, first-idle bounds, and descriptive settled measurements. One sequential lane owns the graph plugin because the suite, frozen fixtures, and acceptance receipts are shared.

**Tech Stack:** Java 8 source/API target; Java 21.0.8-zulu runtime; Gradle; JUnit 4; AssertJ; GraphStream 1.3; Python 3 standard library; production AWT text metrics in headless mode.

**Normative inputs:** `docs/superpowers/specs/2026-10-20-graph-initial-spacing-spec.md` is the approved specification. `docs/superpowers/specs/2026-10-16-graph-initial-spacing-design.md`, including epoch 2, supplies its design context. When a current checkout or a receipt contradicts a frozen number, record the discrepancy and stop that gate; do not edit a fixture, threshold, force, or budget to obtain a pass.

**File map:** The only production files are `layout/graphstream/GraphStreamLayoutEngine.java` and `layout/graphstream/ClusterPacking.java` under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`. Under the corresponding test tree, `InitialSpacingFixtures` owns immutable inputs; `InitialSpacingMetrics` owns independent measurements; `InitialSpacingFrameAssertions` owns final-frame invariants; `InitialSpacingConsumers` observes actual consumer state. `InitialSpacingMetricsShould`, `InitialSpacingSeedShould`, and `InitialSpacingLifecycleShould` run through the `InitialSpacingShould` suite. `InitialSpacingProbe` is a standalone package-local-observation JVM entrypoint, and `InitialSpacingAcceptanceShould` invokes its Python coordinator. The coordinator lives in `freeplane_plugin_graph/src/test/resources/initial-spacing/`; generated receipts start under `freeplane_plugin_graph/build/initial-spacing-*` and the selected complete run is copied to `freeplane_plugin_graph/doc/initial-spacing/`. UI captures are supplied outside this lane.

**Lane:** `spacing` → tasks 1, 2, 3, 4, 5, 6, in that order. Every task owns `freeplane_plugin_graph/**`; there are no concurrent lanes. Line references below name the current planning checkout (`8c36da1b7a0f0794c75bccbe29dabec43c770a3f`); use the named symbol as the edit anchor after earlier tasks move lines. A new file's later modification range is the lines created by its preceding task.

## Global Constraints

- All implementation, tests, fixtures, scripts, and committed evidence must remain under `freeplane_plugin_graph/`; this plan is the sole planning deliverable outside that plugin.
- Only the two production edits in spec A are authorized. No production helper seam, force-model edit, idle-policy edit, persistence edit, core-module edit, fallback, or budget increase is authorized.
- Add `static final double PREFERRED_NODE_GAP = 24.0` to `GraphStreamLayoutEngine`. Its package-private scope lets layout tests name the preferred gap without promoting it to a public model/API constant.
- `R(n,maxDisc) = 0.0` for `n <= 1`; otherwise `R = (2*maxDisc + PREFERRED_NODE_GAP) / (2*sin(pi/n))`.
- Change each strict packing-clearance comparison from `< gap` to `< gap - CANDIDATE_EPSILON` in `ClusterPacking.isClear`; keep `CANDIDATE_EPSILON = 1e-9`, `SIBLING_GAP = 8.0`, and `CONTACT_GAP_TOLERANCE = 0.75`.
- NodeSeparationProjection.MIN_GAP remains `6.0`; relationship additive clearance remains `6.0`; the preferred gap is used only by content-ring geometry.
- Use absolute `1e-6` for finite spacing/oracle comparisons; use exact comparisons for unchanged coordinates and pins. Reject NaN/infinity before comparing or reducing values.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never `gradlew` or Maven; every automated Java/Gradle command must have `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`.
- For the scoped first-idle probes, raise only the **test/probe** search bound to **6000 worker steps** and give **each fixture/configuration first-idle search** a **600-second wall-clock limit**. A search exceeding either bound fails; do not edit the force model or production idle predicate to hide it.
- Enforce each 600-second limit with one shared monotonic deadline. For first idle, start it before construction/initial submission of the fresh worker and stop it only when the first `idle=true` frame has been received; pass only the remaining time to every future wait. The post-100 window gets a second shared deadline covering all 100 calls. Do not multiply an unchanged per-call timeout by thousands of steps, and do not reset the first-idle deadline after submit, warmup, or an intermediate step. The existing generic five-second helper may remain for unrelated tests; these scoped probes must use their deadline-aware helper.
- `TypedForcesShould.TWO_MAP_FIRST_IDLE_STEP` must be re-pinned exactly to `3455`; add `INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000`; retain the `rms <= 9.0`, `max <= 36.0` smoke envelopes.
- Each C0/candidate fixture experiment runs alone in a fresh JVM with fresh worker/engine state, no warmup, no parallel experiment, and no fastest-attempt selection. Keep every attempt and every error in the evidence.
- Passing primary worker measurements require nonfailed verified frames, exact coverage, finite geometry and diagnostics, exact pins, independently recomputed node residuals and hull invariants, pair-key bijection, unchanged counter limits, and false exhaustion/truncation flags. Primary unpinned seeds and fixture D require zero node residual; pinned impossibility must be reported rather than repaired by moving pins.
- For fixture D, require exactly two repairs, one relocation round, one verification, no residual edge conflicts, inserted items `112`, pair checks `40`, verification work `98`; keep all budgets unchanged.
- Do not impose zero residual edge conflicts on every graph: RD's primary cross-map seed legitimately has four repairs over four rounds/eight verifications and **eight residual edge-conflict keys**, with zero node/hull residual and no exhausted flags (index items `1173`, pair checks `546`, work `2553`). Preserve/report that bounded result.
- Headless model gaps cannot close the real-application delivery gate. Until the three paired same-build application captures and complete provenance arrive outside this lane, keep `NOT_MEASURED` and the delivery gate open. Do not build a UI automation tool in this plan.
- Every task ends with a commit using `graph-initial-spacing: <imperative summary> [graph-initial-spacing]`; keep every checklist box unticked in this plan.

## Task 1: Freeze fixtures and implement independent spacing metrics

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingFixtures.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetrics.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingFrameAssertions.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetricsShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetricsShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`

**Interfaces:**

Consumes:

- `GraphProjection ReferenceRepulsionFixture.referenceProjection(long generation)`
- `GraphProjection ReferenceRepulsionFixture.reparentedProjection(long generation)`
- `private static GraphProjection TypedForcesShould.twoMapProjection(long generation)`
- `static void BoundaryInvariantAssertions.assertBijection(BoundarySeparationDiagnostics diagnostics)`
- `static void BoundaryInvariantAssertions.assertVerifiedFrame(GraphProjection projection, LayoutFrame frame, BoundarySeparationDiagnostics diagnostics, GeometryTextMetrics metrics, List<PinProjection> pins)`

Produces:

- `static final WorkspaceId InitialSpacingFixtures.WORKSPACE`
- `static final MapReferenceId InitialSpacingFixtures.A`
- `static final MapReferenceId InitialSpacingFixtures.B`
- `static GraphProjection InitialSpacingFixtures.reference(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.reparented(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.cross(long generation) throws Exception`
- `static SourceNodeKey InitialSpacingFixtures.source(MapReferenceId map, String id)`
- `static ProjectedNodeKey InitialSpacingFixtures.key(MapReferenceId map, String id)`
- `static EnclosureHullKey InitialSpacingFixtures.hull(MapReferenceId map, String id)`
- `static ProjectedEnclosure InitialSpacingFixtures.enclosure(MapReferenceId map, String id, String label, Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> direct, List<EnclosureHullKey> children, boolean root, BoundaryTier tier)`
- `static GraphProjection InitialSpacingFixtures.projection(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.single(int count, long generation, boolean mixed, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.root(int count, long generation)`
- `static GraphProjection InitialSpacingFixtures.named(String name) throws Exception`
- `static ProjectedEndpointKey InitialSpacingFixtures.endpoint(ProjectedNodeKey key)`
- `static SourceNodeKey InitialSpacingFixtures.source(ProjectedEndpointKey endpoint)`
- `static ProjectedEdge InitialSpacingFixtures.nativeEdge(int occurrence, ProjectedEndpointKey first, ProjectedEndpointKey second, boolean targetArrow)`
- `static GraphProjection InitialSpacingFixtures.matrix(boolean crossMap, String pair, boolean reverse)`
- `static PinProjection InitialSpacingFixtures.pin(ProjectedNodeKey key, double x, double y)`
- `static LayoutRequest InitialSpacingFixtures.request(GraphProjection before, GraphProjection after)`
- `static final double InitialSpacingMetrics.TOLERANCE = 1e-6`
- `static double InitialSpacingMetrics.radius(GraphProjection projection, ProjectedNodeKey key)`
- `static void InitialSpacingMetrics.finite(double value)`
- `static void InitialSpacingMetrics.close(double actual, double expected)`
- `static double InitialSpacingMetrics.distance(LayoutPoint a, LayoutPoint b)`
- `static Map<EnclosureHullKey, InitialSpacingMetrics.Metric> InitialSpacingMetrics.measure(GraphProjection projection, LayoutPositions positions)`
- `static double InitialSpacingMetrics.analyticRadius(GraphProjection projection, ProjectedEnclosure enclosure, double gap)`
- `InitialSpacingMetrics.Metric(List<Double> values)`
- `final double InitialSpacingMetrics.Metric.minimum`
- `final double InitialSpacingMetrics.Metric.median`
- `final List<Double> InitialSpacingMetrics.Metric.nearest`
- `public static GeometryTextMetrics InitialSpacingFrameAssertions.metrics()`
- `public static LayoutFrame InitialSpacingFrameAssertions.awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline) throws Exception`
- `public static List<String> InitialSpacingFrameAssertions.residualPairs(GraphProjection projection, LayoutPositions positions)`
- `public static void InitialSpacingFrameAssertions.check(GraphProjection projection, LayoutFrame frame, List<PinProjection> pins, boolean requireZero)`
- `public class InitialSpacingShould (JUnit 4 suite entrypoint)`

**Implementer tier:** Economy
**Lane:** spacing
**Owns:** freeplane_plugin_graph/**
**Depends on:** none

**Purpose / acceptance:** Spec B/C/J. Complete code is supplied; this task is transcription plus the specified test run. Freeze the reference fixture and cross-map factory by reflective delegation without editing either source. Freeze S1 as four nodes plus exactly two native connectors; the forbidden 24-node same-map shape never appears. Uniform radius 8, generation 1, workspace/map IDs, labels, direct-node order, parent hulls, tiers, and empty pin lists are explicit in the fixture code. The mixed fixture has radii `[8.0,8.0,9.6,8.0]` from two distinct outgoing targets on n2. A root with two direct nodes is an acceptance fixture rather than an empty candidate metric map.

- [ ] **Step 1: Add the test and its suite entrypoint.** Before adding the helpers, create the following test and suite. Missing helper symbols are the expected red result; no production file changes in this task.

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetricsShould.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.util.*;
import org.junit.Test;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

public class InitialSpacingMetricsShould {
    @Test public void countOneNearestGapPerDirectNodeAndUseAnEvenMedian() {
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        double[] x = {0.0, 30.0, 70.0, 120.0};
        for (int i = 0; i < 4; i++) nodes.put(p.nodes().get(i).key(), LayoutPoint.of(x[i], 0.0));
        Map<EnclosureHullKey, Metric> result = measure(p, LayoutPositions.of(nodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
        assertThat(result).hasSize(1);
        Metric m = result.values().iterator().next();
        assertThat(m.nearest).containsExactly(14.0, 14.0, 24.0, 34.0);
        close(m.minimum, 14.0); close(m.median, 19.0);
        close(new Metric(Arrays.asList(9.0, 1.0, 5.0)).median, 5.0);
        Map<ProjectedNodeKey, LayoutPoint> shifted = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (Map.Entry<ProjectedNodeKey, LayoutPoint> entry : nodes.entrySet())
            shifted.put(entry.getKey(), LayoutPoint.of(entry.getValue().x() + 10000.0, entry.getValue().y() - 100000.0));
        assertThat(measure(p, LayoutPositions.of(shifted, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))
            .values().iterator().next().nearest).isEqualTo(m.nearest);
    }

    @Test public void rejectNonfiniteMetricMissingNodesAndDuplicateMembership() {
        assertThatThrownBy(() -> new Metric(Arrays.asList(1.0, Double.NaN))).isInstanceOf(AssertionError.class);
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        assertThatThrownBy(() -> measure(p, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isInstanceOf(AssertionError.class);
        GraphProjection duplicate = InitialSpacingFixtures.projection(1L, p.nodes(), Arrays.asList(
            p.enclosures().get(0), InitialSpacingFixtures.enclosure(InitialSpacingFixtures.A, "other", "Other",
                Optional.<EnclosureHullKey>empty(), p.enclosures().get(0).directNodes(),
                Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE)),
            p.edges(), p.pins());
        Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (ProjectedNode n : p.nodes()) points.put(n.key(), LayoutPoint.of(0.0, 0.0));
        assertThatThrownBy(() -> measure(duplicate, LayoutPositions.of(points,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isInstanceOf(AssertionError.class);
    }

    @Test public void excludePinnedAndSingletonScopesAndFreezeFixtureIdentity() throws Exception {
        PinProjection pin = InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0);
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(pin));
        Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (ProjectedNode n : p.nodes()) points.put(n.key(), LayoutPoint.of(25.0, 35.0));
        assertThat(measure(p, LayoutPositions.of(points, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isEmpty();
        GraphProjection s1 = InitialSpacingFixtures.named("S1");
        assertThat(s1.nodes()).hasSize(4); assertThat(s1.enclosures()).hasSize(2); assertThat(s1.edges()).hasSize(2);
        for (ProjectedEdge e : s1.edges()) assertThat(e.contributors().get(0).connectorDescriptor()).isPresent();
        assertThat(InitialSpacingFixtures.named("cross").nodes()).hasSize(24);
        assertThat(InitialSpacingFixtures.named("mixed").prominence().values())
            .extracting(value -> value.scale()).contains(1.0).anyMatch(value -> value > 1.0);
        assertThat(InitialSpacingFixtures.single(0, 1L, false, Collections.<PinProjection>emptyList()).nodes()).isEmpty();
        for (int n : new int[] {0, 1}) {
            GraphProjection small = InitialSpacingFixtures.single(n, 1L, false, Collections.<PinProjection>emptyList());
            Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (ProjectedNode node : small.nodes()) positions.put(node.key(), LayoutPoint.of(0.0, 0.0));
            assertThat(measure(small, LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isEmpty();
        }
    }
}
```

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({InitialSpacingMetricsShould.class})
public class InitialSpacingShould { }
```

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
```

Expected red: compilation reports missing `InitialSpacingFixtures` / `InitialSpacingMetrics`. If the test fails for another reason, resolve the environment discrepancy before changing an assertion.

- [ ] **Step 2: Add the exact frozen factories and metric implementation.** All factories and helpers below are test-only. The metric enumerates all direct-node pairs, takes one nearest surface gap per direct node, takes the ordinary sorted median (including the even-count average), skips only explicitly unsupported pinned direct-node metrics, validates membership and finite values before reduction, and rejects undersized/invalid samples. Analytic expectations never call the production ring helper.

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingFixtures.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import java.lang.reflect.Method;
import java.util.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.*;
import org.freeplane.plugin.graph.workspace.model.*;

final class InitialSpacingFixtures {
    static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    static final MapReferenceId A = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final MapReferenceId B = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    static GraphProjection reference(long generation) throws Exception {
        return invoke("org.freeplane.plugin.graph.layout.ReferenceRepulsionFixture", "referenceProjection", generation);
    }

    static GraphProjection reparented(long generation) throws Exception {
        return invoke("org.freeplane.plugin.graph.layout.ReferenceRepulsionFixture", "reparentedProjection", generation);
    }

    static GraphProjection cross(long generation) throws Exception {
        return invoke("org.freeplane.plugin.graph.layout.TypedForcesShould", "twoMapProjection", generation);
    }

    private static GraphProjection invoke(String owner, String name, long generation) throws Exception {
        Method method = Class.forName(owner).getDeclaredMethod(name, long.class);
        method.setAccessible(true);
        return (GraphProjection) method.invoke(null, generation);
    }

    static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(source(map, id));
    }

    static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    static ProjectedEnclosure enclosure(MapReferenceId map, String id, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> direct,
            List<EnclosureHullKey> children, boolean root, BoundaryTier tier) {
        return ProjectedEnclosure.of(hull(map, id), Collections.singletonList(EnclosureKey.of(source(map, id))),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "Map", parent, direct, children, root, tier);
    }

    static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<PinProjection> pins) {
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), pins);
    }

    static GraphProjection single(int count, long generation, boolean mixed, List<PinProjection> pins) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < count; i++) {
            ProjectedNodeKey key = key(A, "n" + i);
            keys.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node " + i, "Node " + i), "Map", true));
        }
        List<ProjectedEnclosure> enclosures = Arrays.asList(
            enclosure(A, "sub", "Sub", Optional.of(hull(A, "root")), keys,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE),
            enclosure(A, "root", "Root", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(hull(A, "sub")),
                true, BoundaryTier.EMPHATIC));
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        if (count >= 4) {
            if (mixed) {
                edges.add(nativeEdge(0, endpoint(keys.get(2)), endpoint(keys.get(0)), true));
                edges.add(nativeEdge(1, endpoint(keys.get(2)), endpoint(keys.get(1)), true));
            }
            else {
                edges.add(nativeEdge(0, endpoint(keys.get(0)), endpoint(keys.get(1)), false));
                edges.add(nativeEdge(0, endpoint(keys.get(2)), endpoint(keys.get(3)), false));
            }
        }
        return projection(generation, nodes, enclosures, edges, pins);
    }

    static GraphProjection root(int count, long generation) {
        GraphProjection p = single(count, generation, false, Collections.<PinProjection>emptyList());
        return projection(generation, p.nodes(), Collections.singletonList(enclosure(A, "root", "Root",
            Optional.<EnclosureHullKey>empty(), p.enclosures().get(0).directNodes(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC)), p.edges(), p.pins());
    }

    static GraphProjection named(String name) throws Exception {
        if (name.equals("two")) return root(2, 1L);
        if (name.equals("reference")) return reference(1L);
        if (name.equals("S1")) return single(4, 1L, false, Collections.<PinProjection>emptyList());
        if (name.equals("cross")) return cross(1L);
        if (name.equals("mixed")) return single(4, 1L, true, Collections.<PinProjection>emptyList());
        String[] parts = name.split("-");
        if (parts.length == 3 && (parts[0].equals("matrix") || parts[0].equals("endpoints"))) {
            return matrix(parts[1].equals("cross"), parts[2], false);
        }
        throw new IllegalArgumentException("Unknown fixture " + name);
    }

    static ProjectedEndpointKey endpoint(ProjectedNodeKey key) {
        return ProjectedEndpointKey.ofNode(key);
    }

    static SourceNodeKey source(ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get().source() : endpoint.enclosure().get().source();
    }

    static ProjectedEdge nativeEdge(int occurrence, ProjectedEndpointKey first,
            ProjectedEndpointKey second, boolean targetArrow) {
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source(first),
            source(second).persistedReference().get(), false, targetArrow, "source", "middle", "target");
        return ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(
            EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor), first, second)));
    }

    static GraphProjection matrix(boolean crossMap, String pair, boolean reverse) {
        MapReferenceId secondMap = crossMap ? B : A;
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        for (int side = 0; side < 2; side++) {
            MapReferenceId map = side == 0 ? A : secondMap;
            String prefix = side == 0 ? "left" : "right";
            List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < 4; i++) {
                ProjectedNodeKey key = key(map, prefix + i);
                direct.add(key);
                nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node " + i, "Node " + i), "Map", true));
            }
            enclosures.add(enclosure(map, prefix, prefix, Optional.<EnclosureHullKey>empty(), direct,
                Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE));
        }
        ProjectedEndpointKey first = pair.equals("ee")
            ? ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source(A, "left"))) : endpoint(key(A, "left0"));
        ProjectedEndpointKey second = pair.equals("nn") ? endpoint(key(secondMap, "right0"))
            : ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source(secondMap, "right")));
        if (reverse) {
            ProjectedEndpointKey saved = first;
            first = second;
            second = saved;
        }
        ProjectedEdge edge;
        if (crossMap) {
            GraphRelationshipRecord record = GraphRelationshipRecord.of(
                RelationshipId.of("30000000-0000-0000-0000-000000000001"), 1L,
                source(first).persistedReference().get(), source(second).persistedReference().get(),
                RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
            edge = ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(
                EdgeContributor.graphRelationship(record, first, second)));
        }
        else edge = nativeEdge(0, first, second, false);
        return projection(1L, nodes, enclosures, Collections.singletonList(edge), Collections.<PinProjection>emptyList());
    }

    static PinProjection pin(ProjectedNodeKey key, double x, double y) {
        return PinProjection.active(PinRecord.of(key.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList()), key);
    }

    static LayoutRequest request(GraphProjection before, GraphProjection after) {
        return LayoutRequest.of(WORKSPACE, after, ProjectionDiff.between(before, after), after.pins());
    }
}
```

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetrics.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

final class InitialSpacingMetrics {
    static final double TOLERANCE = 1e-6;

    static final class Metric {
        final double minimum;
        final double median;
        final List<Double> nearest;
        Metric(List<Double> values) {
            if (values.size() < 2) throw new IllegalArgumentException("Metric needs two direct nodes");
            nearest = Collections.unmodifiableList(new ArrayList<Double>(values));
            List<Double> sorted = new ArrayList<Double>(values);
            for (double value : sorted) finite(value);
            Collections.sort(sorted);
            minimum = sorted.get(0);
            int n = sorted.size();
            median = n % 2 == 1 ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            finite(median);
        }
    }

    static double radius(GraphProjection projection, ProjectedNodeKey key) {
        double result = 8.0 * (projection.prominence().containsKey(key)
            ? projection.prominence().get(key).scale() : 1.0);
        finite(result);
        assertThat(result).isGreaterThan(0.0);
        return result;
    }

    static void finite(double value) {
        assertThat(Double.isFinite(value)).as("finite value %s", value).isTrue();
    }

    static void close(double actual, double expected) {
        finite(actual);
        finite(expected);
        assertThat(actual).isCloseTo(expected, within(TOLERANCE));
    }

    static double distance(LayoutPoint a, LayoutPoint b) {
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    static Map<EnclosureHullKey, Metric> measure(GraphProjection projection, LayoutPositions positions) {
        Set<ProjectedNodeKey> requested = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) assertThat(requested.add(node.key())).isTrue();
        assertThat(positions.nodes().keySet()).isEqualTo(requested);
        Set<ProjectedNodeKey> memberships = new LinkedHashSet<ProjectedNodeKey>();
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (PinProjection pin : projection.pins()) if (pin.active()) pinned.add(pin.projectedNode().get());
        Map<EnclosureHullKey, Metric> result = new LinkedHashMap<EnclosureHullKey, Metric>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            List<ProjectedNodeKey> direct = enclosure.directNodes();
            boolean hasPin = false;
            for (ProjectedNodeKey key : direct) {
                assertThat(requested).contains(key);
                assertThat(memberships.add(key)).as("unique direct membership %s", key).isTrue();
                LayoutPoint point = positions.nodes().get(key);
                assertThat(point).isNotNull();
                finite(point.x());
                finite(point.y());
                radius(projection, key);
                hasPin |= pinned.contains(key);
            }
            if (direct.size() < 2 || hasPin) continue;
            List<Double> values = new ArrayList<Double>();
            for (ProjectedNodeKey first : direct) {
                double nearest = Double.POSITIVE_INFINITY;
                for (ProjectedNodeKey second : direct) {
                    if (!first.equals(second)) nearest = Math.min(nearest,
                        distance(positions.nodes().get(first), positions.nodes().get(second))
                        - radius(projection, first) - radius(projection, second));
                }
                finite(nearest);
                values.add(nearest);
            }
            assertThat(result.put(enclosure.hullKey(), new Metric(values))).isNull();
        }
        return result;
    }

    static double analyticRadius(GraphProjection projection, ProjectedEnclosure enclosure, double gap) {
        if (enclosure.directNodes().size() <= 1) return 0.0;
        double maximum = 0.0;
        for (ProjectedNodeKey key : enclosure.directNodes()) maximum = Math.max(maximum, radius(projection, key));
        return (2.0 * maximum + gap) / (2.0 * Math.sin(Math.PI / enclosure.directNodes().size()));
    }
}
```

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingFrameAssertions.java` with exactly:

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.*;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.*;
import java.util.concurrent.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.projection.*;

public final class InitialSpacingFrameAssertions {
    private InitialSpacingFrameAssertions() { }

    public static GeometryTextMetrics metrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
    }

    public static LayoutFrame awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline)
            throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) throw new TimeoutException("Initial spacing shared deadline expired");
        return stage.toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
    }

    public static List<String> residualPairs(GraphProjection projection, LayoutPositions positions) {
        List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        Collections.sort(nodes, (a, b) -> CanonicalKeys.node(a).compareTo(CanonicalKeys.node(b)));
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ProjectedNodeKey a = nodes.get(i), b = nodes.get(j);
                LayoutPoint x = positions.nodes().get(a), y = positions.nodes().get(b);
                double need = 8.0 * scale(projection, a) + 8.0 * scale(projection, b) + 6.0;
                if (Math.hypot(y.x() - x.x(), y.y() - x.y()) < need) {
                    result.add(CanonicalKeys.nodePair(a, b));
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    private static double scale(GraphProjection p, ProjectedNodeKey key) {
        return p.prominence().containsKey(key) ? p.prominence().get(key).scale() : 1.0;
    }

    public static void check(GraphProjection projection, LayoutFrame frame,
            List<PinProjection> pins, boolean requireZero) {
        assertThat(frame.failed()).isFalse();
        assertThat(frame.verified()).isTrue();
        Set<ProjectedNodeKey> nodes = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) assertThat(nodes.add(node.key())).isTrue();
        Set<EnclosureHullKey> anchors = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : projection.enclosures()) assertThat(anchors.add(e.hullKey())).isTrue();
        assertThat(frame.positions().nodes().keySet()).isEqualTo(nodes);
        assertThat(frame.positions().anchors().keySet()).isEqualTo(anchors);
        for (LayoutPoint point : frame.positions().nodes().values()) point(point);
        for (LayoutPoint point : frame.positions().anchors().values()) point(point);
        List<String> residuals = residualPairs(projection, frame.positions());
        assertThat(residuals).doesNotHaveDuplicates();
        assertThat(frame.residualViolations()).isEqualTo(residuals.size());
        if (requireZero) assertThat(residuals).isEmpty();
        BoundarySeparationDiagnostics boundary = frame.boundaryDiagnostics();
        BoundaryInvariantAssertions.assertBijection(boundary);
        assertThat(boundary.boundaryCovered()).isTrue();
        if (pins.isEmpty() || boundary.boundaryVerified()) {
            assertThat(boundary.boundaryVerified()).isTrue();
            assertThat(boundary.hullResidualViolations()).isZero();
            BoundaryInvariantAssertions.assertVerifiedFrame(projection, frame, boundary, metrics(), pins);
        }
        for (PinProjection pin : pins) if (pin.active()) {
            assertThat(frame.positions().nodes().get(pin.projectedNode().get()))
                .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
        }
        assertThat(boundary.rounds()).isBetween(0, 4);
        finiteNonnegative(boundary.displacementRms());
        finiteNonnegative(boundary.displacementMax());
        finiteNonnegative(boundary.deltaRms());
        finiteNonnegative(boundary.deltaMax());
        for (LayoutPoint point : boundary.appliedDisplacements().values()) point(point);
        EdgeConflictDiagnostics edge = frame.edgeConflictDiagnostics();
        assertThat(edge.rounds()).isBetween(0, 4);
        assertThat(edge.verifications()).isBetween(0, 64);
        assertThat(edge.indexInsertedItems()).isBetween(0L, 262144L);
        assertThat(edge.indexPairChecks()).isBetween(0L, 131072L);
        assertThat(edge.verificationWork()).isBetween(0L, 65536L);
        assertThat(edge.conflictsDetected()).isBetween(0, 4096);
        assertThat(edge.conflictsResidual()).doesNotHaveDuplicates();
        assertThat(edge.indexBudgetExhausted()).isFalse();
        assertThat(edge.verificationBudgetExhausted()).isFalse();
        assertThat(edge.detectionTruncated()).isFalse();
        finiteNonnegative(frame.idle().rms());
        finiteNonnegative(frame.idle().max());
    }

    private static void point(LayoutPoint point) {
        assertThat(point).isNotNull();
        assertThat(Double.isFinite(point.x())).isTrue();
        assertThat(Double.isFinite(point.y())).isTrue();
    }

    private static void finiteNonnegative(double value) {
        assertThat(Double.isFinite(value)).isTrue();
        assertThat(value).isGreaterThanOrEqualTo(0.0);
    }
}
```

`InitialSpacingFrameAssertions` is shared test infrastructure, compiled now and exercised by tasks 3–5. It independently recomputes published node residuals on final positions without applying another correction, delegates hull reconstruction to the existing independent test helper with pinned production metrics, and checks identity, uniqueness, finite diagnostics and unchanged budgets. Suppressed hull semantics remain those of `BoundaryInvariantAssertions`.

- [ ] **Step 3: Verify the metric contract.**

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
```

Expected green: `InitialSpacingShould` succeeds against unchanged C0 production. The tests cover known even/odd nearest-gap medians, finite rejection, duplicate membership rejection, no positive-infinity reduction for zero/one-node content, and translation invariance. Keep production unchanged here. Fixture-D/current-force regressions are handled after the production edit in task 3; do not alter those tests in task 1.

- [ ] **Commit: Inspect and record this task.** Run `git diff --check`, inspect `git diff` and `git diff --cached`, then stage only the listed task files.

```sh
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingFixtures.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetrics.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingFrameAssertions.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingMetricsShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java
git diff --cached --check
git diff --cached
git commit -m "graph-initial-spacing: Add frozen spacing fixtures and independent metrics [graph-initial-spacing]"
```

## Task 2: Apply the preferred ring gap and verify every geometry consumer

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingConsumers.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingSeedShould.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:40-55`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:500-513`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java:140-157`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java:1-8`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingSeedShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`

**Interfaces:**

Consumes:

- `static final WorkspaceId InitialSpacingFixtures.WORKSPACE`
- `static final MapReferenceId InitialSpacingFixtures.A`
- `static final MapReferenceId InitialSpacingFixtures.B`
- `static GraphProjection InitialSpacingFixtures.reference(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.reparented(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.cross(long generation) throws Exception`
- `static SourceNodeKey InitialSpacingFixtures.source(MapReferenceId map, String id)`
- `static ProjectedNodeKey InitialSpacingFixtures.key(MapReferenceId map, String id)`
- `static EnclosureHullKey InitialSpacingFixtures.hull(MapReferenceId map, String id)`
- `static ProjectedEnclosure InitialSpacingFixtures.enclosure(MapReferenceId map, String id, String label, Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> direct, List<EnclosureHullKey> children, boolean root, BoundaryTier tier)`
- `static GraphProjection InitialSpacingFixtures.projection(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.single(int count, long generation, boolean mixed, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.root(int count, long generation)`
- `static GraphProjection InitialSpacingFixtures.named(String name) throws Exception`
- `static ProjectedEndpointKey InitialSpacingFixtures.endpoint(ProjectedNodeKey key)`
- `static SourceNodeKey InitialSpacingFixtures.source(ProjectedEndpointKey endpoint)`
- `static ProjectedEdge InitialSpacingFixtures.nativeEdge(int occurrence, ProjectedEndpointKey first, ProjectedEndpointKey second, boolean targetArrow)`
- `static GraphProjection InitialSpacingFixtures.matrix(boolean crossMap, String pair, boolean reverse)`
- `static PinProjection InitialSpacingFixtures.pin(ProjectedNodeKey key, double x, double y)`
- `static LayoutRequest InitialSpacingFixtures.request(GraphProjection before, GraphProjection after)`
- `static final double InitialSpacingMetrics.TOLERANCE = 1e-6`
- `static double InitialSpacingMetrics.radius(GraphProjection projection, ProjectedNodeKey key)`
- `static void InitialSpacingMetrics.finite(double value)`
- `static void InitialSpacingMetrics.close(double actual, double expected)`
- `static double InitialSpacingMetrics.distance(LayoutPoint a, LayoutPoint b)`
- `static Map<EnclosureHullKey, InitialSpacingMetrics.Metric> InitialSpacingMetrics.measure(GraphProjection projection, LayoutPositions positions)`
- `static double InitialSpacingMetrics.analyticRadius(GraphProjection projection, ProjectedEnclosure enclosure, double gap)`
- `InitialSpacingMetrics.Metric(List<Double> values)`
- `final double InitialSpacingMetrics.Metric.minimum`
- `final double InitialSpacingMetrics.Metric.median`
- `final List<Double> InitialSpacingMetrics.Metric.nearest`
- `public static GeometryTextMetrics InitialSpacingFrameAssertions.metrics()`
- `public static LayoutFrame InitialSpacingFrameAssertions.awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline) throws Exception`
- `public static List<String> InitialSpacingFrameAssertions.residualPairs(GraphProjection projection, LayoutPositions positions)`
- `public static void InitialSpacingFrameAssertions.check(GraphProjection projection, LayoutFrame frame, List<PinProjection> pins, boolean requireZero)`
- `public class InitialSpacingShould (JUnit 4 suite entrypoint)`

Produces:

- `static Object InitialSpacingConsumers.field(Object object, String name) throws Exception`
- `static LayoutPoint InitialSpacingConsumers.composed(GraphStreamLayoutEngine.BoundarySizes sizes, EnclosureHullKey key)`
- `static void InitialSpacingConsumers.raw(GraphProjection p, LayoutFrame raw, double gap)`
- `static void InitialSpacingConsumers.topology(GraphProjection p, GraphStreamLayoutEngine engine, double gap) throws Exception`
- `static EnclosureHullKey InitialSpacingConsumers.endpointHull(GraphProjection p, ProjectedEndpointKey endpoint)`
- `static double InitialSpacingConsumers.endpointRadius(GraphProjection p, ProjectedEndpointKey endpoint, double gap)`
- `static void InitialSpacingConsumers.repulsion() throws Exception`
- `static final double GraphStreamLayoutEngine.PREFERRED_NODE_GAP = 24.0`
- `double GraphStreamLayoutEngine.BoundarySizes.contentRingRadius(EnclosureHullKey key)`
- `private static boolean ClusterPacking.isClear(double x, double y, ClusterItem item, List<ClusterPlacement> placed, double gap)`

**Implementer tier:** Economy
**Lane:** spacing
**Owns:** freeplane_plugin_graph/**
**Depends on:** 1

**Purpose / acceptance:** Spec A/D/H/H.1/I/J. Only the two production edits authorized in A are made here. Literal consumer tests inspect actual raw engine output, configured particles, force links and packing. Test calculations use frozen inputs and independent formulae. The six endpoint cases also reverse input node/enclosure/edge lists and compare consumer results. Only containment rings use the preferred gap; the relationship additive floor remains 6.0. The uniform larger-disc four-node ring has radius-9.6 nodes and exact raw adjacent surface gaps 24.0; mixed rings use `24 + 2*maxDisc - r_i - r_j` and never assume uniform exactness.

- [ ] **Step 1: Write the red consumer tests.** Add the following helper and test, then change only the suite annotation as shown. The reflection test invokes the existing private packing predicate; do not expose it in production.

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingConsumers.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.lang.reflect.Field;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.graphstream.ui.geom.Vector3;

final class InitialSpacingConsumers {
    static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static LayoutPoint composed(GraphStreamLayoutEngine.BoundarySizes sizes, EnclosureHullKey key) {
        ProjectedEnclosure enclosure = sizes.enclosure(key);
        if (enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
            ClusterPacking.ClusterPlacement p = sizes.topLevelPlacementOf(key);
            assertThat(p).isNotNull();
            return LayoutPoint.of(p.x + p.item.anchorX, p.y + p.item.anchorY);
        }
        LayoutPoint parent = composed(sizes, enclosure.parentHull().get());
        ClusterPacking.ClusterPlacement p = sizes.placementOf(enclosure.parentHull().get(), key);
        assertThat(p).isNotNull();
        return LayoutPoint.of(parent.x() + p.x + p.item.anchorX, parent.y() + p.y + p.item.anchorY);
    }

    static void raw(GraphProjection p, LayoutFrame raw, double gap) {
        assertThat(raw.failed()).isFalse();
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        Set<EnclosureHullKey> top = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : p.enclosures()) {
            if (e.mapRoot() || !e.parentHull().isPresent()) top.add(e.hullKey());
            double r = analyticRadius(p, e, gap);
            close(sizes.contentRingRadius(e.hullKey()), r);
            LayoutPoint anchor = composed(sizes, e.hullKey());
            close(distance(raw.positions().anchors().get(e.hullKey()), anchor), 0.0);
            List<ClusterPacking.ClusterPlacement> placed = sizes.placements(e.hullKey());
            if (!e.directNodes().isEmpty()) {
                ClusterPacking.ClusterPlacement content = placed.get(0);
                assertThat(content.item.anchorKey).isNull();
                double maxRadius = 0.0;
                for (ProjectedNodeKey key : e.directNodes()) maxRadius = Math.max(maxRadius, radius(p, key));
                close(content.item.width, 2.0 * (r + maxRadius + 8.0));
                close(content.item.height, 2.0 * (r + maxRadius + 8.0));
            }
            int n = e.directNodes().size();
            for (int i = 0; i < n; i++) {
                ProjectedNodeKey key = e.directNodes().get(i);
                PinProjection pin = null;
                for (PinProjection candidate : p.pins()) if (candidate.active()
                        && candidate.projectedNode().get().equals(key)) pin = candidate;
                double angle = 2.0 * Math.PI * i / n;
                LayoutPoint expected = pin == null
                    ? LayoutPoint.of(anchor.x() + r * Math.cos(angle), anchor.y() + r * Math.sin(angle))
                    : LayoutPoint.of(pin.x(), pin.y());
                close(distance(raw.positions().nodes().get(key), expected), 0.0);
                if (n > 1 && pin == null) {
                    ProjectedNodeKey next = e.directNodes().get((i + 1) % n);
                    double maximum = 0.0;
                    for (ProjectedNodeKey k : e.directNodes()) maximum = Math.max(maximum, radius(p, k));
                    boolean anyPin = false;
                    for (PinProjection q : p.pins()) if (q.active()
                            && e.directNodes().contains(q.projectedNode().get())) anyPin = true;
                    if (!anyPin) close(distance(raw.positions().nodes().get(key), raw.positions().nodes().get(next))
                        - radius(p, key) - radius(p, next), gap + 2.0 * maximum - radius(p, key) - radius(p, next));
                }
            }
            footprint(sizes, e);
            packing(placed);
        }
        Set<EnclosureHullKey> actualTop = new LinkedHashSet<EnclosureHullKey>();
        for (ClusterPacking.ClusterPlacement placement : sizes.topLevelPlacements()) {
            assertThat(actualTop.add(placement.item.anchorKey)).isTrue();
        }
        assertThat(actualTop).isEqualTo(top);
        assertThat(raw.positions().anchors().keySet()).hasSize(p.enclosures().size());
        assertThat(raw.positions().nodes().keySet()).hasSize(p.nodes().size());
        packing(sizes.topLevelPlacements());
    }

    private static void footprint(GraphStreamLayoutEngine.BoundarySizes sizes, ProjectedEnclosure e) {
        GraphStreamLayoutEngine.BoundarySizes.Footprint f = sizes.footprint(e.hullKey());
        finite(f.width); finite(f.height); finite(f.anchorX); finite(f.anchorY);
        if (e.directNodes().isEmpty() && e.directEnclosures().isEmpty()) {
            double width = 16.0;
            for (org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : e.labels())
                width = Math.max(width, label.displayText().length() * 16.0 + 16.0);
            close(f.width, width); close(f.height, 40.0);
            close(f.anchorX, 0.0); close(f.anchorY, 0.0);
            return;
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (ClusterPacking.ClusterPlacement item : sizes.placements(e.hullKey())) {
            minX = Math.min(minX, item.x - item.item.width / 2.0);
            maxX = Math.max(maxX, item.x + item.item.width / 2.0);
            minY = Math.min(minY, item.y - item.item.height / 2.0);
            maxY = Math.max(maxY, item.y + item.item.height / 2.0);
        }
        close(f.width, maxX - minX + 32.0); close(f.height, maxY - minY + 32.0);
        close(f.anchorX, -(minX + maxX) / 2.0); close(f.anchorY, -(minY + maxY) / 2.0);
    }

    private static void packing(List<ClusterPacking.ClusterPlacement> placements) {
        for (int i = 0; i < placements.size(); i++) {
            ClusterPacking.ClusterPlacement a = placements.get(i);
            for (int j = i + 1; j < placements.size(); j++) {
                ClusterPacking.ClusterPlacement b = placements.get(j);
                double x = Math.abs(a.x - b.x) - (a.item.width + b.item.width) / 2.0;
                double y = Math.abs(a.y - b.y) - (a.item.height + b.item.height) / 2.0;
                assertThat(Math.max(x, y)).isGreaterThanOrEqualTo(8.0 - 1e-9);
            }
            for (ClusterPacking.ClusterItem contact : a.contacts) {
                ClusterPacking.ClusterPlacement b = null;
                for (ClusterPacking.ClusterPlacement candidate : placements) if (candidate.item == contact) b = candidate;
                assertThat(b).isNotNull();
                double x = Math.abs(a.x - b.x) - (a.item.width + b.item.width) / 2.0;
                double y = Math.abs(a.y - b.y) - (a.item.height + b.item.height) / 2.0;
                assertThat(Math.max(x, y)).isBetween(8.0 - 1e-9, 8.75);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void topology(GraphProjection p, GraphStreamLayoutEngine engine, double gap) throws Exception {
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        Map<String, GraphStreamLayoutEngine.ParticleState> particles =
            (Map<String, GraphStreamLayoutEngine.ParticleState>) field(engine, "particles");
        TypedSpringBox box = (TypedSpringBox) field(engine, "springBox");
        Map<String, TypedNodeParticle> configured = (Map<String, TypedNodeParticle>) field(box, "typedParticles");
        Map<String, GraphStreamLayoutEngine.ForceLink> links =
            (Map<String, GraphStreamLayoutEngine.ForceLink>) field(box, "typedLinks");
        Map<ProjectedNodeKey, String> nodeIds = new LinkedHashMap<ProjectedNodeKey, String>();
        Map<EnclosureHullKey, String> anchorIds = new LinkedHashMap<EnclosureHullKey, String>();
        for (GraphStreamLayoutEngine.ParticleState state : particles.values()) {
            if (state.nodeKey != null) {
                nodeIds.put(state.nodeKey, state.id);
                close(state.radius, radius(p, state.nodeKey));
            }
            else {
                anchorIds.put(state.anchorKey, state.id);
                close(state.radius, 0.0);
                TypedNodeParticle actual = configured.get(state.id);
                GraphStreamLayoutEngine.BoundarySizes.Footprint f = sizes.footprint(state.anchorKey);
                close(actual.separationHalfWidth(), f.width / 2.0);
                close(actual.separationHalfHeight(), f.height / 2.0);
                close(actual.separationOffsetX(), -f.anchorX);
                close(actual.separationOffsetY(), -f.anchorY);
                close(actual.separationRadius(), 0.0);
            }
        }
        assertThat(nodeIds).hasSize(p.nodes().size()); assertThat(anchorIds).hasSize(p.enclosures().size());
        Set<String> expected = new LinkedHashSet<String>();
        for (ProjectedEnclosure e : p.enclosures()) {
            for (ProjectedNodeKey node : e.directNodes())
                expected.add(signature(GraphStreamLayoutEngine.ForceKind.CONTAINMENT, anchorIds.get(e.hullKey()), nodeIds.get(node)));
            if (e.parentHull().isPresent())
                expected.add(signature(GraphStreamLayoutEngine.ForceKind.HIERARCHY, anchorIds.get(e.parentHull().get()), anchorIds.get(e.hullKey())));
        }
        Set<String> actual = new LinkedHashSet<String>();
        int relationships = 0;
        for (GraphStreamLayoutEngine.ForceLink link : links.values()) {
            finite(link.restLength);
            if (link.kind == GraphStreamLayoutEngine.ForceKind.RELATIONSHIP) {
                relationships++;
                List<ProjectedEdge> matched = new ArrayList<ProjectedEdge>();
                for (ProjectedEdge e : p.edges()) {
                    String a = id(p, e.first(), nodeIds, anchorIds), b = id(p, e.second(), nodeIds, anchorIds);
                    if (signature(link.kind, a, b).equals(signature(link.kind, link.firstId, link.secondId))) matched.add(e);
                }
                assertThat(matched).hasSize(1);
                ProjectedEdge edge = matched.get(0);
                double first = endpointRadius(p, edge.first(), gap), second = endpointRadius(p, edge.second(), gap);
                close(link.restLength, first + second + 6.0);
                close(link.restLength - first - second, 6.0);
                assertThat(link.crossMap).isEqualTo(!edge.first().mapReferenceId().equals(edge.second().mapReferenceId()));
                System.out.printf(java.util.Locale.ROOT, "REST\t%s\t%s\t%s\t%.17g\t%.17g\t%.17g%n",
                    CanonicalKeys.endpoint(edge.first()), CanonicalKeys.endpoint(edge.second()), link.crossMap, first, second, link.restLength);
                continue;
            }
            assertThat(actual.add(signature(link.kind, link.firstId, link.secondId))).isTrue();
            if (link.kind == GraphStreamLayoutEngine.ForceKind.CONTAINMENT) {
                ProjectedEnclosure enclosure = sizes.enclosure(particles.get(link.firstId).anchorKey);
                assertThat(link.secondId).isEqualTo(nodeIds.get(particles.get(link.secondId).nodeKey));
                assertThat(enclosure.directNodes()).contains(particles.get(link.secondId).nodeKey);
                close(link.restLength, analyticRadius(p, enclosure, gap));
            }
            else {
                EnclosureHullKey parent = particles.get(link.firstId).anchorKey;
                EnclosureHullKey child = particles.get(link.secondId).anchorKey;
                assertThat(sizes.enclosure(child).parentHull().get()).isEqualTo(parent);
                ClusterPacking.ClusterPlacement placement = sizes.placementOf(parent, child);
                close(link.restLength, Math.hypot(placement.x + placement.item.anchorX, placement.y + placement.item.anchorY));
            }
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(relationships).isEqualTo(p.edges().size());
    }

    private static String signature(GraphStreamLayoutEngine.ForceKind kind, String a, String b) {
        assertThat(a).isNotNull(); assertThat(b).isNotNull();
        return kind + ":" + (a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a);
    }

    private static String id(GraphProjection p, ProjectedEndpointKey endpoint,
            Map<ProjectedNodeKey, String> nodes, Map<EnclosureHullKey, String> anchors) {
        return endpoint.isNode() ? nodes.get(endpoint.node().get()) : anchors.get(endpointHull(p, endpoint));
    }

    static EnclosureHullKey endpointHull(GraphProjection p, ProjectedEndpointKey endpoint) {
        List<EnclosureHullKey> found = new ArrayList<EnclosureHullKey>();
        for (ProjectedEnclosure enclosure : p.enclosures())
            if (enclosure.endpointKeys().contains(endpoint.enclosure().get())) found.add(enclosure.hullKey());
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    static double endpointRadius(GraphProjection p, ProjectedEndpointKey endpoint, double gap) {
        if (endpoint.isNode()) return radius(p, endpoint.node().get());
        EnclosureHullKey key = endpointHull(p, endpoint);
        for (ProjectedEnclosure e : p.enclosures()) if (e.hullKey().equals(key)) return analyticRadius(p, e, gap);
        throw new AssertionError("Unresolved enclosure endpoint " + endpoint);
    }

    static void repulsion() throws Exception {
        for (boolean ancestor : new boolean[] {false, true}) {
            try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                GraphProjection p = InitialSpacingFixtures.matrix(false, "ee", false);
                engine.apply(InitialSpacingFixtures.request(p, p));
                TypedSpringBox box = (TypedSpringBox) field(engine, "springBox");
                @SuppressWarnings("unchecked") Map<String, TypedNodeParticle> particles =
                    (Map<String, TypedNodeParticle>) field(box, "typedParticles");
                @SuppressWarnings("unchecked") Map<String, GraphStreamLayoutEngine.ParticleState> states =
                    (Map<String, GraphStreamLayoutEngine.ParticleState>) field(engine, "particles");
                List<String> anchors = new ArrayList<String>();
                for (GraphStreamLayoutEngine.ParticleState state : states.values()) if (state.anchorKey != null) anchors.add(state.id);
                assertThat(anchors).hasSize(2);
                String a = anchors.get(0), b = anchors.get(1);
                if (ancestor) box.configureParticle(b, 0.0, false, true, a);
                box.configureAnchorExtents(a, 20.0, 10.0, 3.0, -2.0);
                box.configureAnchorExtents(b, 30.0, 40.0, -7.0, 6.0);
                // Box centres coincide: both overlap magnitudes are 58; x wins the tie.
                box.setParticlePosition(a, -3.0, 2.0);
                box.setParticlePosition(b, 7.0, -6.0);
                Vector3 displacement = new Vector3();
                box.addBoundaryRepulsion(particles.get(a), displacement);
                close(displacement.at(0), ancestor ? 0.0 : 29.0);
                close(displacement.at(1), 0.0);
                // Make x overlap 58 and y overlap 18: the independently smaller y push is 9.
                box.setParticlePosition(a, -3.0, 42.0);
                displacement = new Vector3();
                box.addBoundaryRepulsion(particles.get(a), displacement);
                close(displacement.at(0), 0.0);
                close(displacement.at(1), ancestor ? 0.0 : 9.0);
            }
        }
    }
}
```

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingSeedShould.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

public class InitialSpacingSeedShould {
    @Test public void preferTwentyFourWhileKeepingTheSafetyAndSiblingGaps() {
        close(GraphStreamLayoutEngine.PREFERRED_NODE_GAP, 24.0);
        close(NodeSeparationProjection.MIN_GAP, 6.0);
        close(GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP, 8.0);
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        double old = analyticRadius(p, p.enclosures().get(0), 6.0);
        double now = analyticRadius(p, p.enclosures().get(0), 24.0);
        close(old, 15.556349186104047); close(now, 28.284271247461902);
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        close(sizes.contentRingRadius(p.enclosures().get(0).hullKey()), now);
    }

    @Test public void preserveTheAnalyticSeedConsumersAndFreshDeterminism() throws Exception {
        for (String name : Arrays.asList("reference", "S1", "cross", "mixed")) {
            GraphProjection p = InitialSpacingFixtures.named(name);
            LayoutPositions previous = null;
            for (int run = 0; run < 2; run++) {
                try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                    LayoutFrame raw = engine.apply(InitialSpacingFixtures.request(p, p));
                    InitialSpacingConsumers.raw(p, raw, 24.0);
                    InitialSpacingConsumers.topology(p, engine, 24.0);
                    if (previous != null) assertThat(raw.positions()).isEqualTo(previous);
                    previous = raw.positions();
                    for (Metric metric : measure(p, raw.positions()).values()) {
                        assertThat(metric.minimum).isGreaterThanOrEqualTo(24.0 - TOLERANCE);
                        assertThat(metric.median).isGreaterThanOrEqualTo(24.0 - TOLERANCE);
                    }
                }
            }
        }
    }

    @Test public void coverEmptySingletonTwoNodeLongLabelAndPinnedSeeds() throws Exception {
        for (int count : new int[] {0, 1, 2}) {
            GraphProjection p = InitialSpacingFixtures.single(count, 1L, false, Collections.<PinProjection>emptyList());
            check(p);
            if (count == 2) close(new GraphStreamLayoutEngine.BoundarySizes(p)
                .contentRingRadius(p.enclosures().get(0).hullKey()), 20.0);
        }
        GraphProjection base = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        char[] chars = new char[500]; Arrays.fill(chars, 'W');
        ProjectedEnclosure original = base.enclosures().get(0);
        ProjectedEnclosure longLabel = InitialSpacingFixtures.enclosure(InitialSpacingFixtures.A, "sub",
            new String(chars), original.parentHull(), original.directNodes(), original.directEnclosures(), false, BoundaryTier.SUBTLE);
        GraphProjection longP = InitialSpacingFixtures.projection(1L, base.nodes(),
            Arrays.asList(longLabel, base.enclosures().get(1)), base.edges(), base.pins());
        check(longP);
        close(new GraphStreamLayoutEngine.BoundarySizes(longP).contentRingRadius(longLabel.hullKey()), 28.284271247461902);
        PinProjection pin = InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0);
        GraphProjection pinned = InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(pin));
        check(pinned);
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(600);
            LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(
                InitialSpacingFixtures.request(pinned, pinned)), deadline);
            InitialSpacingFrameAssertions.check(pinned, frame, pinned.pins(), false);
            for (int i = 0; i < 10; i++) {
                frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
                InitialSpacingFrameAssertions.check(pinned, frame, pinned.pins(), false);
            }
        }
    }

    private static void check(GraphProjection p) throws Exception {
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(InitialSpacingFixtures.request(p, p));
            InitialSpacingConsumers.raw(p, frame, 24.0);
            InitialSpacingConsumers.topology(p, engine, 24.0);
        }
    }

    @Test public void sameMapNodeNodeRest() throws Exception { matrix(false, "nn"); }
    @Test public void sameMapNodeEnclosureRest() throws Exception { matrix(false, "ne"); }
    @Test public void sameMapEnclosureEnclosureRest() throws Exception { matrix(false, "ee"); }
    @Test public void crossMapNodeNodeRest() throws Exception { matrix(true, "nn"); }
    @Test public void crossMapNodeEnclosureRest() throws Exception { matrix(true, "ne"); }
    @Test public void crossMapEnclosureEnclosureRest() throws Exception { matrix(true, "ee"); }

    private static void matrix(boolean cross, String pair) throws Exception {
        for (boolean reverse : new boolean[] {false, true}) check(InitialSpacingFixtures.matrix(cross, pair, reverse));
    }

    @Test public void uniformLargerDiscsStillUseAnExactTwentyFourRawGap() throws Exception {
        GraphProjection base = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        Map<ProjectedEdgeKey, List<EdgeContributor>> contributors = new LinkedHashMap<ProjectedEdgeKey, List<EdgeContributor>>();
        for (int i = 0; i < 4; i++) for (int j = 1; j <= 2; j++) {
            ProjectedEdge edge = InitialSpacingFixtures.nativeEdge(j - 1,
                InitialSpacingFixtures.endpoint(base.nodes().get(i).key()),
                InitialSpacingFixtures.endpoint(base.nodes().get((i + j) % 4).key()), true);
            if (!contributors.containsKey(edge.key())) contributors.put(edge.key(), new ArrayList<EdgeContributor>());
            contributors.get(edge.key()).addAll(edge.contributors());
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (Map.Entry<ProjectedEdgeKey, List<EdgeContributor>> entry : contributors.entrySet())
            edges.add(ProjectedEdge.of(entry.getKey(), entry.getValue()));
        GraphProjection p = InitialSpacingFixtures.projection(1L, base.nodes(), base.enclosures(), edges, base.pins());
        for (ProjectedNode node : p.nodes()) close(radius(p, node.key()), 9.6);
        check(p);
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            LayoutFrame raw = engine.apply(InitialSpacingFixtures.request(p, p));
            for (Metric metric : measure(p, raw.positions()).values()) close(metric.minimum, 24.0);
        }
    }

    @Test public void packEveryParentlessEnclosureExactlyOnce() throws Exception {
        GraphProjection base = InitialSpacingFixtures.matrix(false, "nn", false);
        List<ProjectedEnclosure> roots = new ArrayList<ProjectedEnclosure>();
        for (ProjectedEnclosure e : base.enclosures()) if (!e.directNodes().isEmpty())
            roots.add(ProjectedEnclosure.of(e.hullKey(), e.endpointKeys(), e.labels(), e.mapName(),
                Optional.<EnclosureHullKey>empty(), e.directNodes(), e.directEnclosures(), false, e.boundaryTier()));
        GraphProjection p = InitialSpacingFixtures.projection(1L, base.nodes(), roots,
            Collections.<ProjectedEdge>emptyList(), Collections.<PinProjection>emptyList());
        assertThat(roots).hasSize(2);
        check(p);
        assertThat(new GraphStreamLayoutEngine.BoundarySizes(p).topLevelPlacements()).hasSize(2);
    }

    @Test public void nestedFixturesExposeAnOffsetAndStillUseIndependentConsumers() throws Exception {
        for (GraphProjection p : Arrays.asList(InitialSpacingFixtures.reference(1L), InitialSpacingFixtures.reparented(2L))) {
            check(p);
            boolean nonzero = false;
            GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
            for (ProjectedEnclosure e : p.enclosures()) {
                GraphStreamLayoutEngine.BoundarySizes.Footprint f = sizes.footprint(e.hullKey());
                nonzero |= f.anchorX != 0.0 || f.anchorY != 0.0;
            }
            assertThat(nonzero).isTrue();
        }
    }

    @Test public void useConfiguredRectanglesForBoundaryRepulsion() throws Exception {
        InitialSpacingConsumers.repulsion();
    }

    @Test public void distinguishOneUlpsContactFromRealSiblingUnderGap() throws Exception {
        Method clear = ClusterPacking.class.getDeclaredMethod("isClear", double.class, double.class,
            ClusterPacking.ClusterItem.class, List.class, double.class);
        clear.setAccessible(true);
        ClusterPacking.ClusterItem item = new ClusterPacking.ClusterItem(null, 10.0, 10.0, 0.0, 0.0, 0);
        List<ClusterPacking.ClusterPlacement> placed = Collections.singletonList(new ClusterPacking.ClusterPlacement(item, 0.0, 0.0));
        assertThat((Boolean) clear.invoke(null, Math.nextDown(18.0), 0.0, item, placed, 8.0)).isTrue();
        assertThat((Boolean) clear.invoke(null, 18.0 - 2e-9, 0.0, item, placed, 8.0)).isFalse();
        assertThat((Boolean) clear.invoke(null, 18.0 - 5e-10, 18.0 - 2e-9, item, placed, 8.0)).isTrue();
        assertThat((Boolean) clear.invoke(null, 18.0 - 2e-9, 18.0 - 2e-9, item, placed, 8.0)).isFalse();
        System.out.println("EPSILON_NECESSITY = NOT_ESTABLISHED (predicate sensitivity only; no C0 slot-failure witness)");
    }
}
```

Set `InitialSpacingShould.java` to:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({InitialSpacingMetricsShould.class, InitialSpacingSeedShould.class})
public class InitialSpacingShould { }
```

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
```

Expected red: `PREFERRED_NODE_GAP` is absent, or ring-coordinate/containment-rest assertions observe the old 6.0 ring, and the one-ULP feasibility case rejects the target contact. Keep the test inputs and expected 24.0 values frozen.

- [ ] **Step 2: Make the complete production diff.** In `GraphStreamLayoutEngine`, immediately after `static final double NODE_RADIUS = 8.0;`, insert:

```java
    static final double PREFERRED_NODE_GAP = 24.0;
```

In `BoundarySizes.contentRingRadius`, replace exactly:

```java
            return (2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)
                / (2.0 * Math.sin(Math.PI / count));
```

with:

```java
            return (2.0 * maxRadius + PREFERRED_NODE_GAP)
                / (2.0 * Math.sin(Math.PI / count));
```

In `ClusterPacking.isClear`, replace exactly:

```java
            if (clearanceX < gap && clearanceY < gap) {
```

with:

```java
            if (clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON) {
```

Do not change `CANDIDATE_EPSILON`, `NodeSeparationProjection`, `TypedSpringBox`, `TypedNodeParticle`, worker code, or any other production source. The unchanged `count <= 1` branch continues to return 0.0.

- [ ] **Step 3: Verify the complete consumer coverage.**

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
```

Expected green: all metric and seed tests pass. Actual observations cover raw seed coordinates/order and composed anchors; content-item dimensions; sibling gutters/contact slack; independent box union and offsets; parentless enclosure top-level membership exactly once; containment rests; all relationship endpoint radii and actual rests; hierarchy identity/deduplication/rest; configured anchor half-extents/negative offsets; and rectangular boundary repulsion along both axes. Nested reference/reparented fixtures must actually expose a nonzero anchor offset. Empty content retains the label-sized box, and empty/singleton/two-node/long-label/pin cases remain finite.

The private-predicate test demonstrates ULP sensitivity and rejects a real `gap - 1e-6` under-gap; it does **not** establish a larger-item C0 slot failure. Record `EPSILON_NECESSITY = NOT_ESTABLISHED` with that limitation. Do not invent a necessity witness or credit the predicate change for fixture D's substantive worker repairs. Task 5 carries the explicit result into evidence, satisfying H.1's permitted alternative.

Analytic four-node radius-8 checks: C0 `R=15.556349186104047`, candidate `R=28.284271247461902`. Actual relationship rests, in same-map and cross-map variants: node–node `22.0 → 22.0`, node–enclosure `29.556349186104047 → 42.28427124746190`, enclosure–enclosure `37.112698372208094 → 62.568542494923804`. The consumer code also compares endpoint radii separately and checks cross-map classification.

- [ ] **Commit: Inspect and record this task.** Run `git diff --check`, inspect `git diff` and `git diff --cached`, then stage only the listed task files.

```sh
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingConsumers.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingSeedShould.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java
git diff --cached --check
git diff --cached
git commit -m "graph-initial-spacing: Prefer 24-unit fresh ring spacing [graph-initial-spacing]"
```

## Task 3: Pin corrected worker geometry and the measured first-idle regression

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:45-52`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:364-434`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java:494-537`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java`

**Interfaces:**

Consumes:

- `public static GeometryTextMetrics InitialSpacingFrameAssertions.metrics()`
- `public static LayoutFrame InitialSpacingFrameAssertions.awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline) throws Exception`
- `public static List<String> InitialSpacingFrameAssertions.residualPairs(GraphProjection projection, LayoutPositions positions)`
- `public static void InitialSpacingFrameAssertions.check(GraphProjection projection, LayoutFrame frame, List<PinProjection> pins, boolean requireZero)`
- `static Object InitialSpacingConsumers.field(Object object, String name) throws Exception`
- `static LayoutPoint InitialSpacingConsumers.composed(GraphStreamLayoutEngine.BoundarySizes sizes, EnclosureHullKey key)`
- `static void InitialSpacingConsumers.raw(GraphProjection p, LayoutFrame raw, double gap)`
- `static void InitialSpacingConsumers.topology(GraphProjection p, GraphStreamLayoutEngine engine, double gap) throws Exception`
- `static EnclosureHullKey InitialSpacingConsumers.endpointHull(GraphProjection p, ProjectedEndpointKey endpoint)`
- `static double InitialSpacingConsumers.endpointRadius(GraphProjection p, ProjectedEndpointKey endpoint, double gap)`
- `static void InitialSpacingConsumers.repulsion() throws Exception`
- `static GraphProjection ClusterProximityShould.fixture(char name)`
- `static List<PinProjection> ClusterProximityShould.fixturePins(char name)`
- `public static GraphProjection ClusterProximityShould.wideProjection(long generation)`
- `public static Map<EnclosureHullKey, LayoutPoint> ClusterProximityShould.composeAnchors(GraphProjection projection)`
- `private static Map<ProjectedNodeKey, LayoutPoint> ClusterProximityShould.composeNodes(GraphProjection projection, Map<EnclosureHullKey, LayoutPoint> anchors, GraphStreamLayoutEngine.BoundarySizes sizes)`
- `static LayoutFrame ClusterProximityShould.workerFrame(GraphProjection projection, List<PinProjection> pins) throws Exception`
- `static LayoutFrame ClusterProximityShould.engineFrame(GraphProjection projection, List<PinProjection> pins)`
- `private static void ClusterProximityShould.assertContactGutters(GraphStreamLayoutEngine.BoundarySizes sizes)`
- `private static GraphProjection TypedForcesShould.twoMapProjection(long generation)`

Produces:

- `private static final int TypedForcesShould.TWO_MAP_FIRST_IDLE_STEP = 3455`
- `private static final int TypedForcesShould.INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000`
- `private static void TypedForcesShould.observeInitialSpacing(GraphProjection projection, boolean crossMap) throws Exception`
- `public void ClusterProximityShould.initialFrameThroughWorkerPublishesTheCompleteCorrectionPipeline() throws Exception`
- `public void ClusterProximityShould.initialFrameThroughWorkerPreservesPinsAndChecksCorrectedGeometry() throws Exception`

**Implementer tier:** Economy
**Lane:** spacing
**Owns:** freeplane_plugin_graph/**
**Depends on:** 2

**Purpose / acceptance:** Spec F/G. Existing raw ring expectations remain appropriate for the raw engine; worker publication is independently reconstructed through boundary separation and edge conflict correction. Replace only the two obsolete worker tests and the scoped first-idle methods/constants; preserve every other assertion, the generated-projection regression, and the existing impossible-pin/failed-frame tests.

- [ ] **Step 1: Add the corrected-frame regressions before relaxing any old assumptions.** In `ClusterProximityShould`, replace the complete methods `initialFrameThroughWorkerKeepsSeedsWithoutPins` and `initialFrameThroughWorkerWithPinOnlyChecksNonOverlap` (including their `@Test` annotations) with the following methods/helper. Keep every other existing raw-engine/pin test, `settledFixturesReachIdleWithinBudgetAndDoNotCross`, the pin-hop limit, all fixture factories, and `fixturePins`. No imports are needed: new cross-package types below are fully qualified.
```java
    @Test
    public void initialFrameThroughWorkerPublishesTheCompleteCorrectionPipeline() throws Exception {
        final List<PinProjection> pins = Collections.<PinProjection>emptyList();
        for (final char name : UNPINNED_FIXTURE_NAMES) {
            final GraphProjection projection = fixture(name);
            final GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(projection);
            final LayoutFrame raw = engineFrame(projection, pins);
            InitialSpacingConsumers.raw(projection, raw, 24.0);
            final org.freeplane.plugin.graph.layout.BoundarySeparationResult boundary =
                new org.freeplane.plugin.graph.layout.BoundarySeparationCorrection().apply(projection,
                    raw.positions(), org.freeplane.plugin.graph.layout.InitialSpacingFrameAssertions.metrics(), pins);
            final org.freeplane.plugin.graph.layout.EdgeConflictResult edge =
                new org.freeplane.plugin.graph.layout.EdgeConflictCorrection().apply(projection,
                    boundary.positions(), org.freeplane.plugin.graph.layout.InitialSpacingFrameAssertions.metrics(), pins,
                    org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility.visibleEndpoints(
                        projection.nodes(), projection.enclosures()), boundary);
            final LayoutFrame actual = workerFrame(projection, pins);
            assertThat(actual.positions()).as("fixture %s complete correction", name).isEqualTo(edge.positions());
            org.freeplane.plugin.graph.layout.InitialSpacingFrameAssertions.check(projection, actual, pins, name == 'D');
            assertContactGutters(sizes);
            System.out.println("STAGES " + name + " raw=" + raw.positions() + " boundary=" + boundary.positions()
                + " edge=" + edge.positions() + " diagnostics=" + actual.edgeConflictDiagnostics());
            if (name == 'D') assertFixtureDRepairs(projection, raw, actual);
        }
    }

    private static void assertFixtureDRepairs(final GraphProjection projection, final LayoutFrame raw,
            final LayoutFrame actual) {
        final ProjectedNodeKey a = nodeKey(MAP_ONE, "a-n2"), b = nodeKey(MAP_TWO, "b-n0");
        assertThat(raw.positions().nodes().get(a).x()).isCloseTo(-28.284271247461902, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(raw.positions().nodes().get(a).y()).isCloseTo(3.4638242249419733e-15, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(raw.positions().nodes().get(b).x()).isCloseTo(-132.28427124746190, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(raw.positions().nodes().get(b).y()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(actual.positions().nodes().get(a).x()).isCloseTo(-28.284271247461902, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(actual.positions().nodes().get(a).y()).isCloseTo(11.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(actual.positions().nodes().get(b).x()).isCloseTo(-132.28427124746190, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(actual.positions().nodes().get(b).y()).isCloseTo(-10.999999999999996, org.assertj.core.data.Offset.offset(1e-6));
        for (final ProjectedNode node : projection.nodes()) if (!node.key().equals(a) && !node.key().equals(b)) {
            assertThat(actual.positions().nodes().get(node.key())).isEqualTo(raw.positions().nodes().get(node.key()));
        }
        assertThat(actual.positions().anchors()).isEqualTo(raw.positions().anchors());
        final Map<EnclosureHullKey, LayoutPoint> composed = composeAnchors(projection);
        assertThat(actual.positions().anchors()).isEqualTo(composed);
        assertThat(actual.positions().anchors().get(hull(MAP_ONE, "a-sub"))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(actual.positions().anchors().get(hull(MAP_TWO, "b-sub")).x())
            .isCloseTo(-160.56854249492380, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(actual.positions().anchors().get(hull(MAP_TWO, "b-sub")).y()).isEqualTo(0.0);
        final org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics diagnostic = actual.edgeConflictDiagnostics();
        assertThat(diagnostic.repairsApplied()).hasSize(2);
        final java.util.Set<ProjectedNodeKey> affected = new java.util.LinkedHashSet<ProjectedNodeKey>();
        for (final org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics.Repair repair : diagnostic.repairsApplied()) {
            for (int i = 0; i < repair.nodeKeys().size(); i++) {
                final ProjectedNodeKey key = repair.nodeKeys().get(i);
                assertThat(affected.add(key)).isTrue();
                assertThat(key).isIn(a, b);
                assertThat(repair.toCentres().get(key).x()).isEqualTo(repair.fromCentres().get(key).x());
                assertThat(repair.toCentres().get(key).y() - repair.fromCentres().get(key).y())
                    .isCloseTo(key.equals(a) ? 11.0 : -11.0, org.assertj.core.data.Offset.offset(1e-6));
                assertThat(repair.toCentres().get(key)).isNotEqualTo(repair.fromCentres().get(key));
            }
        }
        assertThat(affected).containsExactlyInAnyOrder(a, b);
        assertThat(diagnostic.rounds()).isEqualTo(1); assertThat(diagnostic.verifications()).isEqualTo(1);
        assertThat(diagnostic.conflictsResidual()).isEmpty();
        assertThat(diagnostic.indexInsertedItems()).isEqualTo(112L);
        assertThat(diagnostic.indexPairChecks()).isEqualTo(40L); assertThat(diagnostic.verificationWork()).isEqualTo(98L);
    }

    @Test
    public void initialFrameThroughWorkerPreservesPinsAndChecksCorrectedGeometry() throws Exception {
        final GraphProjection projection = fixture('C');
        final List<PinProjection> pins = fixturePins('C');
        final GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(projection);
        final LayoutFrame raw = engineFrame(projection, pins);
        final org.freeplane.plugin.graph.layout.BoundarySeparationResult boundary =
            new org.freeplane.plugin.graph.layout.BoundarySeparationCorrection().apply(projection, raw.positions(),
                org.freeplane.plugin.graph.layout.InitialSpacingFrameAssertions.metrics(), pins);
        final org.freeplane.plugin.graph.layout.EdgeConflictResult edge =
            new org.freeplane.plugin.graph.layout.EdgeConflictCorrection().apply(projection, boundary.positions(),
                org.freeplane.plugin.graph.layout.InitialSpacingFrameAssertions.metrics(), pins,
                org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility.visibleEndpoints(
                    projection.nodes(), projection.enclosures()), boundary);
        final LayoutFrame frame = workerFrame(projection, pins);
        assertThat(frame.positions()).isEqualTo(edge.positions());
        org.freeplane.plugin.graph.layout.InitialSpacingFrameAssertions.check(projection, frame, pins, false);
        assertContactGutters(sizes);
        assertThat(frame.positions().nodes().get(nodeKey(MAP_TWO, "b-n0"))).isEqualTo(LayoutPoint.of(60.0, 20.0));
    }
```

- [ ] **Step 2: Run the existing first-idle regression to observe the old-bound failure.**

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould' --tests 'org.freeplane.plugin.graph.layout.BoundarySeparationShould' --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould'
```

Expected red after the production ring change: `twoMapWorkspaceSettlesToIdle` cannot satisfy the old `2000` search cap / old exact `756` pin. The new worker-stage tests must pass; if they fail, do not use an epsilon or remove a correction to force a match.

- [ ] **Step 3: Apply the exact scoped bound and measured pin.** In `TypedForcesShould`, replace `private static final int TWO_MAP_FIRST_IDLE_STEP = 756;` with:

```java
    private static final int TWO_MAP_FIRST_IDLE_STEP = 3455;
    private static final int INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000;
```

Replace both methods `settleRelationshipFreeNestedBoundaryProjectionToIdle` and `twoMapWorkspaceSettlesToIdle`, and insert the scoped helper immediately before the existing generic `await` helper, using exactly:
```java
    @Test
    public void settleRelationshipFreeNestedBoundaryProjectionToIdle() throws Exception {
        observeInitialSpacing(ReferenceRepulsionFixture.referenceProjection(1L), false);
    }

    @Test
    public void twoMapWorkspaceSettlesToIdle() throws Exception {
        observeInitialSpacing(twoMapProjection(1L), true);
    }

    private static void observeInitialSpacing(final GraphProjection projection, final boolean crossMap) throws Exception {
        final long start = System.nanoTime();
        final long firstIdleDeadlineNanos = start + TimeUnit.SECONDS.toNanos(600L);
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            final LayoutFrame seed = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(
                LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                    ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())), firstIdleDeadlineNanos);
            InitialSpacingFrameAssertions.check(projection, seed, Collections.<PinProjection>emptyList(), true);
            LayoutFrame firstIdle = null;
            int firstIdleStep = 0;
            long firstIdleReceived = 0L;
            for (int step = 1; step <= INITIAL_SPACING_FIRST_IDLE_LIMIT; step++) {
                final LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), firstIdleDeadlineNanos);
                final long received = System.nanoTime();
                assertThat(frame.failed()).isFalse();
                InitialSpacingFrameAssertions.check(projection, frame, Collections.<PinProjection>emptyList(), false);
                if (frame.idle().idle()) {
                    firstIdle = frame;
                    firstIdleStep = step;
                    firstIdleReceived = received;
                    break;
                }
            }
            assertThat(firstIdle).as("first idle within 6000 steps and 600 seconds").isNotNull();
            assertThat(firstIdleReceived - start).isBetween(0L, TimeUnit.SECONDS.toNanos(600L));
            if (crossMap) assertThat(firstIdleStep).as("measured ring-24 first-idle step").isEqualTo(TWO_MAP_FIRST_IDLE_STEP);
            else assertThat(firstIdleStep).isLessThanOrEqualTo(INITIAL_SPACING_FIRST_IDLE_LIMIT);
            System.out.println("FIRST_IDLE_REGRESSION " + crossMap + " " + firstIdleStep + " " + (firstIdleReceived - start));
            if (crossMap) {
                // C0: 6.911901186336649 / 29.693160379681416; candidate: 0.2910542126686765 / 0.9902335696885642.
                // These maxima cover 100 following frames; the unchanged 9/36 smoke envelope does not promise persistent idle.
                final long followStart = System.nanoTime();
                final long followDeadline = followStart + TimeUnit.SECONDS.toNanos(600L);
                for (int step = 0; step < 100; step++) {
                    final LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), followDeadline);
                    InitialSpacingFrameAssertions.check(projection, frame, Collections.<PinProjection>emptyList(), false);
                    assertThat(frame.idle().rms()).isLessThanOrEqualTo(9.0);
                    assertThat(frame.idle().max()).isLessThanOrEqualTo(36.0);
                }
                assertThat(System.nanoTime() - followStart).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(600L));
            }
        }
        finally { worker.close(); }
    }
```

Keep the existing generic five-second `await` method for unrelated tests. Every wait in the two scoped methods above receives the remaining shared deadline. The first-idle clock starts before worker construction/submission; the following 100 calls get a separate aggregate deadline. The stopped-clock receipt occurs at frame reception. These in-suite regressions are not substituted for task 5's isolated fresh-JVM acceptance timings.

- [ ] **Step 4: Verify the scoped regressions and worker invariants.**

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould' --tests 'org.freeplane.plugin.graph.layout.BoundarySeparationShould' --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould'
```

Expected green. Fixture D must have raw a-n2 `(-28.284271247461902, approximately 0)` and b-n0 `(-132.28427124746190,0)`, final y values `11.0` / `-10.999999999999996`, only those two changed nodes, exact unchanged full anchor map, two repairs and exact `1/1/112/40/98` rounds/verifications/inserted/pairs/work. Node residual is independently zero; final hull geometry is nonoverlapping/nested; repair keys and signed displacements are asserted; no exhausted flag is allowed. Pinned C keeps b-n0 exactly `(60.0,20.0)` and still passes coverage/hull/residual-accounting checks. `twoMapWorkspaceSettlesToIdle` asserts `isEqualTo(TWO_MAP_FIRST_IDLE_STEP)`; never replace this with a range or a loose cap. The `9.0/36.0` post-idle smoke bounds stay unchanged, and the comment reports both C0 and ring-24 measurements without promising sustained idle.

- [ ] **Commit: Inspect and record this task.** Run `git diff --check`, inspect `git diff` and `git diff --cached`, then stage only the listed task files.

```sh
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java
git diff --cached --check
git diff --cached
git commit -m "graph-initial-spacing: Verify corrected seeds and pin first idle at 3455 [graph-initial-spacing]"
```

## Task 4: Verify lifecycle retention pins and edge-case behavior

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingLifecycleShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java:1-8`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingLifecycleShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`

**Interfaces:**

Consumes:

- `static final WorkspaceId InitialSpacingFixtures.WORKSPACE`
- `static final MapReferenceId InitialSpacingFixtures.A`
- `static final MapReferenceId InitialSpacingFixtures.B`
- `static GraphProjection InitialSpacingFixtures.reference(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.reparented(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.cross(long generation) throws Exception`
- `static SourceNodeKey InitialSpacingFixtures.source(MapReferenceId map, String id)`
- `static ProjectedNodeKey InitialSpacingFixtures.key(MapReferenceId map, String id)`
- `static EnclosureHullKey InitialSpacingFixtures.hull(MapReferenceId map, String id)`
- `static ProjectedEnclosure InitialSpacingFixtures.enclosure(MapReferenceId map, String id, String label, Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> direct, List<EnclosureHullKey> children, boolean root, BoundaryTier tier)`
- `static GraphProjection InitialSpacingFixtures.projection(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.single(int count, long generation, boolean mixed, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.root(int count, long generation)`
- `static GraphProjection InitialSpacingFixtures.named(String name) throws Exception`
- `static ProjectedEndpointKey InitialSpacingFixtures.endpoint(ProjectedNodeKey key)`
- `static SourceNodeKey InitialSpacingFixtures.source(ProjectedEndpointKey endpoint)`
- `static ProjectedEdge InitialSpacingFixtures.nativeEdge(int occurrence, ProjectedEndpointKey first, ProjectedEndpointKey second, boolean targetArrow)`
- `static GraphProjection InitialSpacingFixtures.matrix(boolean crossMap, String pair, boolean reverse)`
- `static PinProjection InitialSpacingFixtures.pin(ProjectedNodeKey key, double x, double y)`
- `static LayoutRequest InitialSpacingFixtures.request(GraphProjection before, GraphProjection after)`
- `static final double InitialSpacingMetrics.TOLERANCE = 1e-6`
- `static double InitialSpacingMetrics.radius(GraphProjection projection, ProjectedNodeKey key)`
- `static void InitialSpacingMetrics.finite(double value)`
- `static void InitialSpacingMetrics.close(double actual, double expected)`
- `static double InitialSpacingMetrics.distance(LayoutPoint a, LayoutPoint b)`
- `static Map<EnclosureHullKey, InitialSpacingMetrics.Metric> InitialSpacingMetrics.measure(GraphProjection projection, LayoutPositions positions)`
- `static double InitialSpacingMetrics.analyticRadius(GraphProjection projection, ProjectedEnclosure enclosure, double gap)`
- `InitialSpacingMetrics.Metric(List<Double> values)`
- `final double InitialSpacingMetrics.Metric.minimum`
- `final double InitialSpacingMetrics.Metric.median`
- `final List<Double> InitialSpacingMetrics.Metric.nearest`
- `public static GeometryTextMetrics InitialSpacingFrameAssertions.metrics()`
- `public static LayoutFrame InitialSpacingFrameAssertions.awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline) throws Exception`
- `public static List<String> InitialSpacingFrameAssertions.residualPairs(GraphProjection projection, LayoutPositions positions)`
- `public static void InitialSpacingFrameAssertions.check(GraphProjection projection, LayoutFrame frame, List<PinProjection> pins, boolean requireZero)`
- `static Object InitialSpacingConsumers.field(Object object, String name) throws Exception`
- `static LayoutPoint InitialSpacingConsumers.composed(GraphStreamLayoutEngine.BoundarySizes sizes, EnclosureHullKey key)`
- `static void InitialSpacingConsumers.raw(GraphProjection p, LayoutFrame raw, double gap)`
- `static void InitialSpacingConsumers.topology(GraphProjection p, GraphStreamLayoutEngine engine, double gap) throws Exception`
- `static EnclosureHullKey InitialSpacingConsumers.endpointHull(GraphProjection p, ProjectedEndpointKey endpoint)`
- `static double InitialSpacingConsumers.endpointRadius(GraphProjection p, ProjectedEndpointKey endpoint, double gap)`
- `static void InitialSpacingConsumers.repulsion() throws Exception`
- `public class InitialSpacingShould (JUnit 4 suite entrypoint)`

Produces:

- `public class InitialSpacingLifecycleShould (JUnit 4 lifecycle/edge-case tests)`

**Implementer tier:** Economy
**Lane:** spacing
**Owns:** freeplane_plugin_graph/**
**Depends on:** 3

**Purpose / acceptance:** Spec I/J and design epoch-2 lifecycle semantics. No production lifecycle code changes. Reparent existing particles and add exactly one new direct node; existing raw positions and anchors must remain bitwise equal, while the new particle uses the candidate fresh seed. Explicit engine reset restores fresh deterministic seeds. A healthy worker restart preserves its current trajectory, including exact pins, rather than reseeding. A new workspace gets fresh state.

- [ ] **Step 1: Add the exact lifecycle regressions and register them.**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingLifecycleShould.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public class InitialSpacingLifecycleShould {
    @Test public void retainExistingParticlesOnReparentingAndSeedOnlyAnAddedParticle() throws Exception {
        GraphProjection before = InitialSpacingFixtures.reference(1L), after = InitialSpacingFixtures.reparented(2L);
        retained(before, after, null);
        before = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        after = InitialSpacingFixtures.single(5, 2L, false, Collections.<PinProjection>emptyList());
        retained(before, after, InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n4"));
    }

    private static void retained(GraphProjection before, GraphProjection after, ProjectedNodeKey added) throws Exception {
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            engine.apply(InitialSpacingFixtures.request(before, before));
            LayoutFrame previous = engine.step(); assertThat(previous.failed()).isFalse();
            LayoutFrame updated = engine.apply(InitialSpacingFixtures.request(before, after));
            assertThat(updated.failed()).isFalse();
            for (Map.Entry<ProjectedNodeKey, LayoutPoint> entry : previous.positions().nodes().entrySet())
                assertThat(updated.positions().nodes().get(entry.getKey())).isEqualTo(entry.getValue());
            for (Map.Entry<EnclosureHullKey, LayoutPoint> entry : previous.positions().anchors().entrySet())
                assertThat(updated.positions().anchors().get(entry.getKey())).isEqualTo(entry.getValue());
            InitialSpacingConsumers.topology(after, engine, 24.0);
            if (added != null) {
                ProjectedEnclosure e = after.enclosures().get(0);
                assertThat(e.directNodes()).hasSize(5); assertThat(e.directNodes().get(4)).isEqualTo(added);
                LayoutPoint centre = InitialSpacingConsumers.composed(new GraphStreamLayoutEngine.BoundarySizes(after), e.hullKey());
                double r = (16.0 + 24.0) / (2.0 * Math.sin(Math.PI / 5.0)), angle = 8.0 * Math.PI / 5.0;
                close(updated.positions().nodes().get(added).x(), centre.x() + r * Math.cos(angle));
                close(updated.positions().nodes().get(added).y(), centre.y() + r * Math.sin(angle));
            }
        }
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(before, before)), deadline);
            InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
            LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(before, after)), deadline);
            InitialSpacingFrameAssertions.check(after, frame, after.pins(), false);
        }
    }

    @Test public void explicitRawResetRestoresFreshSeedsAndKeepsPinsExactly() throws Exception {
        for (List<PinProjection> pins : Arrays.asList(Collections.<PinProjection>emptyList(), Collections.singletonList(
                InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0)))) {
            GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, pins);
            LayoutRequest request = InitialSpacingFixtures.request(p, p);
            try (GraphStreamLayoutEngine changed = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults());
                    GraphStreamLayoutEngine fresh = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                changed.apply(request); LayoutFrame stepped = changed.step();
                assertThat(stepped.failed()).isFalse();
                for (PinProjection pin : pins) assertThat(stepped.positions().nodes().get(pin.projectedNode().get()))
                    .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
                changed.reset(); LayoutFrame reset = changed.apply(request), independent = fresh.apply(request);
                assertThat(reset.positions()).isEqualTo(independent.positions()); InitialSpacingConsumers.raw(p, reset, 24.0);
            }
        }
    }

    @Test public void healthyRestartPreservesTheTrajectoryInsteadOfReseeding() throws Exception {
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(
            InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0)));
        try (LayoutWorker restarted = new LayoutWorker(LayoutCalibration.spikeDefaults());
                LayoutWorker control = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            InitialSpacingFrameAssertions.awaitWithinDeadline(restarted.submit(InitialSpacingFixtures.request(p, p)), deadline);
            InitialSpacingFrameAssertions.awaitWithinDeadline(control.submit(InitialSpacingFixtures.request(p, p)), deadline);
            LayoutFrame before = InitialSpacingFrameAssertions.awaitWithinDeadline(restarted.step(), deadline);
            LayoutFrame controlBefore = InitialSpacingFrameAssertions.awaitWithinDeadline(control.step(), deadline);
            equalFrames(before, controlBefore);
            restarted.restart();
            assertThat(restarted.lastValidFrame().positions()).isEqualTo(before.positions());
            LayoutFrame actual = InitialSpacingFrameAssertions.awaitWithinDeadline(restarted.step(), deadline);
            LayoutFrame expected = InitialSpacingFrameAssertions.awaitWithinDeadline(control.step(), deadline);
            equalFrames(actual, expected); InitialSpacingFrameAssertions.check(p, actual, p.pins(), false);
        }
    }

    private static void equalFrames(LayoutFrame a, LayoutFrame b) {
        assertThat(a.positions()).isEqualTo(b.positions()); assertThat(a.failed()).isEqualTo(b.failed());
        assertThat(a.stepIndex()).isEqualTo(b.stepIndex()); assertThat(a.residualViolations()).isEqualTo(b.residualViolations());
        assertThat(a.boundaryDiagnostics()).usingRecursiveComparison().isEqualTo(b.boundaryDiagnostics());
        assertThat(a.edgeConflictDiagnostics()).isEqualTo(b.edgeConflictDiagnostics());
        assertThat(a.idle().idle()).isEqualTo(b.idle().idle());
        assertThat(a.idle().rms()).isEqualTo(b.idle().rms()); assertThat(a.idle().max()).isEqualTo(b.idle().max());
    }

    @Test public void initialiseANewWorkspaceAsAFreshEngine() throws Exception {
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        LayoutRequest other = LayoutRequest.of(WorkspaceId.of("00000000-0000-0000-0000-0000000000ab"), p,
            ProjectionDiff.between(p, p), p.pins());
        try (GraphStreamLayoutEngine changed = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults());
                GraphStreamLayoutEngine fresh = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            changed.apply(InitialSpacingFixtures.request(p, p)); changed.step();
            assertThat(changed.apply(other).positions()).isEqualTo(fresh.apply(other).positions());
        }
    }

    @Test public void handleEmptyProjectionEmptyEnclosureSingletonTwoNodesAndFiniteExtremes() throws Exception {
        List<GraphProjection> fixtures = new ArrayList<GraphProjection>();
        fixtures.add(InitialSpacingFixtures.projection(1L, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(), Collections.<PinProjection>emptyList()));
        for (int n : new int[] {0, 1, 2}) fixtures.add(InitialSpacingFixtures.root(n, 1L));
        fixtures.add(InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(
            InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), Double.MAX_VALUE, -Double.MAX_VALUE))));
        for (int i = 0; i < fixtures.size(); i++) {
            GraphProjection p = fixtures.get(i); boolean extreme = i == fixtures.size() - 1;
            try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
                LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(p, p)),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(600));
                if (extreme) {
                    if (frame.failed()) assertThat(frame.positions()).isEqualTo(worker.lastValidFrame().positions());
                    else {
                        for (LayoutPoint point : frame.positions().nodes().values()) {
                            finite(point.x()); finite(point.y());
                        }
                        for (LayoutPoint point : frame.positions().anchors().values()) {
                            finite(point.x()); finite(point.y());
                        }
                        assertThat(frame.positions().nodes().get(p.pins().get(0).projectedNode().get()))
                            .isEqualTo(LayoutPoint.of(Double.MAX_VALUE, -Double.MAX_VALUE));
                        assertThat(frame.residualViolations()).isEqualTo(InitialSpacingFrameAssertions.residualPairs(p, frame.positions()).size());
                        assertThat(frame.edgeConflictDiagnostics().indexBudgetExhausted()
                            || frame.edgeConflictDiagnostics().verificationBudgetExhausted()
                            || frame.edgeConflictDiagnostics().detectionTruncated()).isTrue();
                    }
                }
                else InitialSpacingFrameAssertions.check(p, frame, p.pins(), true);
                if (!extreme && i <= 2) assertThat(measure(p, frame.positions())).isEmpty();
                if (i == 3) close(measure(p, frame.positions()).values().iterator().next().median, 24.0);
            }
        }
    }
}
```

Replace the suite with:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({InitialSpacingMetricsShould.class, InitialSpacingSeedShould.class, InitialSpacingLifecycleShould.class})
public class InitialSpacingShould { }
```

The finite extreme-pin case exercises production error handling: accept either the existing failed-frame/last-valid-position fallback or a finite saturated frame retaining its exact pin and reporting exhaustion/truncation with independently recomputed residuals. It must never turn a nonfinite point into a passing model observation. Preserve `LayoutWorkerShould.publishesTheRecomputedResidualForATwoPinnedSandwich` and `carriesTheRetainedResidualIntoAFailedFrame`; the final gate runs them without modification.

- [ ] **Step 2: Run the scoped suite.**

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
```

Expected green. Empty projection, empty enclosure, singleton, two-node root, very long labels, pin-determined positions, fresh-input repeatability, stable-input-list permutations, reparent/add/reset/restart/new-workspace behavior, and finite/error paths now have executable checks. Impossible pins remain exact and may report a residual; no assertion may hide it by reprojecting final geometry.

- [ ] **Commit: Inspect and record this task.** Run `git diff --check`, inspect `git diff` and `git diff --cached`, then stage only the listed task files.

```sh
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingLifecycleShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java
git diff --cached --check
git diff --cached
git commit -m "graph-initial-spacing: Cover initial-spacing lifecycle and edge cases [graph-initial-spacing]"
```

## Task 5: Implement isolated C0 and candidate acceptance receipts

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingProbe.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingAcceptanceShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java:1-8`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingAcceptanceShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`

**Interfaces:**

Consumes:

- `static final WorkspaceId InitialSpacingFixtures.WORKSPACE`
- `static final MapReferenceId InitialSpacingFixtures.A`
- `static final MapReferenceId InitialSpacingFixtures.B`
- `static GraphProjection InitialSpacingFixtures.reference(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.reparented(long generation) throws Exception`
- `static GraphProjection InitialSpacingFixtures.cross(long generation) throws Exception`
- `static SourceNodeKey InitialSpacingFixtures.source(MapReferenceId map, String id)`
- `static ProjectedNodeKey InitialSpacingFixtures.key(MapReferenceId map, String id)`
- `static EnclosureHullKey InitialSpacingFixtures.hull(MapReferenceId map, String id)`
- `static ProjectedEnclosure InitialSpacingFixtures.enclosure(MapReferenceId map, String id, String label, Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> direct, List<EnclosureHullKey> children, boolean root, BoundaryTier tier)`
- `static GraphProjection InitialSpacingFixtures.projection(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.single(int count, long generation, boolean mixed, List<PinProjection> pins)`
- `static GraphProjection InitialSpacingFixtures.root(int count, long generation)`
- `static GraphProjection InitialSpacingFixtures.named(String name) throws Exception`
- `static ProjectedEndpointKey InitialSpacingFixtures.endpoint(ProjectedNodeKey key)`
- `static SourceNodeKey InitialSpacingFixtures.source(ProjectedEndpointKey endpoint)`
- `static ProjectedEdge InitialSpacingFixtures.nativeEdge(int occurrence, ProjectedEndpointKey first, ProjectedEndpointKey second, boolean targetArrow)`
- `static GraphProjection InitialSpacingFixtures.matrix(boolean crossMap, String pair, boolean reverse)`
- `static PinProjection InitialSpacingFixtures.pin(ProjectedNodeKey key, double x, double y)`
- `static LayoutRequest InitialSpacingFixtures.request(GraphProjection before, GraphProjection after)`
- `static final double InitialSpacingMetrics.TOLERANCE = 1e-6`
- `static double InitialSpacingMetrics.radius(GraphProjection projection, ProjectedNodeKey key)`
- `static void InitialSpacingMetrics.finite(double value)`
- `static void InitialSpacingMetrics.close(double actual, double expected)`
- `static double InitialSpacingMetrics.distance(LayoutPoint a, LayoutPoint b)`
- `static Map<EnclosureHullKey, InitialSpacingMetrics.Metric> InitialSpacingMetrics.measure(GraphProjection projection, LayoutPositions positions)`
- `static double InitialSpacingMetrics.analyticRadius(GraphProjection projection, ProjectedEnclosure enclosure, double gap)`
- `InitialSpacingMetrics.Metric(List<Double> values)`
- `final double InitialSpacingMetrics.Metric.minimum`
- `final double InitialSpacingMetrics.Metric.median`
- `final List<Double> InitialSpacingMetrics.Metric.nearest`
- `public static GeometryTextMetrics InitialSpacingFrameAssertions.metrics()`
- `public static LayoutFrame InitialSpacingFrameAssertions.awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline) throws Exception`
- `public static List<String> InitialSpacingFrameAssertions.residualPairs(GraphProjection projection, LayoutPositions positions)`
- `public static void InitialSpacingFrameAssertions.check(GraphProjection projection, LayoutFrame frame, List<PinProjection> pins, boolean requireZero)`
- `static Object InitialSpacingConsumers.field(Object object, String name) throws Exception`
- `static LayoutPoint InitialSpacingConsumers.composed(GraphStreamLayoutEngine.BoundarySizes sizes, EnclosureHullKey key)`
- `static void InitialSpacingConsumers.raw(GraphProjection p, LayoutFrame raw, double gap)`
- `static void InitialSpacingConsumers.topology(GraphProjection p, GraphStreamLayoutEngine engine, double gap) throws Exception`
- `static EnclosureHullKey InitialSpacingConsumers.endpointHull(GraphProjection p, ProjectedEndpointKey endpoint)`
- `static double InitialSpacingConsumers.endpointRadius(GraphProjection p, ProjectedEndpointKey endpoint, double gap)`
- `static void InitialSpacingConsumers.repulsion() throws Exception`
- `public class InitialSpacingShould (JUnit 4 suite entrypoint)`

Produces:

- `public static void InitialSpacingProbe.main(String[] args) throws Exception`
- `CLI InitialSpacingProbe <C0|candidate> <fixture-name> <absolute-C0-log-path>`
- `def run_acceptance(repo, candidate_cp, output)`
- `def compare(reports, output)`
- `def write_report(reports, output)`
- `CLI run-initial-spacing.py --repo <absolute-repo> --classpath <candidate-classpath> --output <new-output-directory>`
- `public class InitialSpacingAcceptanceShould (JUnit 4 acceptance driver)`
- `freeplane_plugin_graph/build/initial-spacing-current.txt: absolute directory of the complete latest attempt`

**Implementer tier:** Standard
**Lane:** spacing
**Owns:** freeplane_plugin_graph/**
**Depends on:** 4

**Purpose / acceptance:** Spec B–F/H.1/J/K. This integration task requires checking the evidence/provenance receipts against the actual checkout; use Standard even though the code is literal. The coordinator builds a source-controlled C0 from delivery base `cb8782fb3742f378b2907198fd2343292415da95`, rejects any candidate main-source change beyond the exact A overlay, and compiles C0 production plus frozen fixture classes into a temporary baseline-first classpath. It checks loaded code-source locations, runtime configuration, Java/Gradle versions, actual candidate head, per-source/jar hashes and exact input rows; this prevents accidentally measuring the candidate twice. The R5 probe head is corroboration, not a substitute for the named C0 source.

The six `matrix-*` cases run raw/seed consumer checks only and emit `CONSUMER_ONLY`; they make no first-idle claim. Supplemental `endpoints-cross-ne` and `endpoints-cross-ee` use frozen cross-map endpoint inputs for timed endpoint observations. The planning-only trials of same-map matrix nn/ne hit `boundaryCovered=false` during settling in both C0 and candidate; do not silently weaken G to promote those configurations into passing settled fixtures. This division preserves all six H consumer rows and supplies actual enclosure-endpoint gaps through qualified supplemental cases.

Only acceptance subprocesses own authoritative experiment timings. They run one fixture/configuration per JVM, serially, without warmup or fastest-attempt selection. Each creates a fresh worker; the first-idle deadline begins before construction, and the follow window is exactly 100 additional calls under a new 600-second aggregate deadline. Class loading/JVM launch is outside the monotonic worker clock and is separately recorded by the coordinator. Every attempt has a separate log/JSON receipt, including failure receipts.

- [ ] **Step 1: Add the exact probe.** It obtains the corrected seed before any step, classifies every measured enclosure from mixed radii, packing/composed offsets and actual correction-stage movement, emits all eligibility receipts first, then applies eligible uniform 24.0 oracles. It preserves raw geometry separately. Measurements use the production prominence radii and production label metrics; raw consumer expectations use independent formulae.

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingProbe.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.awt.geom.Dimension2D;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

public final class InitialSpacingProbe {
    private static String fixture;
    private static String configuration;
    private static double gap;

    public static void main(String[] args) throws Exception {
        assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
        configuration = args[0]; fixture = args[1];
        assertThat(configuration).isIn("C0", "candidate");
        gap = configuration.equals("C0") ? 6.0 : 24.0;
        GraphProjection p = InitialSpacingFixtures.named(fixture);
        try {
            java.lang.reflect.Field field = GraphStreamLayoutEngine.class.getDeclaredField("PREFERRED_NODE_GAP");
            field.setAccessible(true);
            assertThat(configuration).isEqualTo("candidate"); close(field.getDouble(null), 24.0);
        }
        catch (NoSuchFieldException absent) { assertThat(configuration).isEqualTo("C0"); }
        inputs(p);
        assertThat(p.generation()).isEqualTo(1L); assertThat(p.pins()).isEmpty();
        if (!fixture.equals("mixed")) for (ProjectedNode node : p.nodes()) close(radius(p, node.key()), 8.0);
        if (fixture.equals("mixed")) {
            double[] scales = {1.0, 1.0, 1.2, 1.0};
            for (int i = 0; i < 4; i++) {
                close(radius(p, p.nodes().get(i).key()), 8.0 * scales[i]);
                assertThat(p.prominence().get(p.nodes().get(i).key()).visibleOutgoingTargets()).isEqualTo(i == 2 ? 2 : 0);
            }
        }
        row("CONFIG", configuration, fixture, gap, NodeSeparationProjection.MIN_GAP,
            GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP, InitialSpacingFixtures.WORKSPACE,
            System.getProperty("java.version"), System.getProperty("java.home"), "Dialog/PLAIN/12;FRC(null,true,true)");
        for (Class<?> type : Arrays.asList(GraphStreamLayoutEngine.class, ClusterPacking.class,
                LayoutWorker.class, InitialSpacingFixtures.class)) row("CODE_SOURCE", type.getName(),
                    type.getProtectionDomain().getCodeSource().getLocation());
        LayoutFrame seed, raw, first = null, post = null;
        int firstStep = -1, stillIdle = 0;
        double maxRms = 0.0, maxDisplacement = 0.0;
        long start = System.nanoTime();
        long deadline = start + TimeUnit.SECONDS.toNanos(600);
        long firstReceived = -1L, followStart;
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            seed = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(p, p)), deadline);
            InitialSpacingFrameAssertions.check(p, seed, p.pins(), true);
            try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                raw = engine.apply(InitialSpacingFixtures.request(p, p));
                InitialSpacingConsumers.raw(p, raw, gap);
                InitialSpacingConsumers.topology(p, engine, gap);
            }
            Set<EnclosureHullKey> eligible = classify(p, raw, seed);
            print(p, "raw", raw); print(p, "seed", seed);
            seedGate(p, seed, args[2], eligible);
            if (fixture.startsWith("matrix-")) {
                labels(p);
                row("CONSUMER_ONLY", fixture, "Raw/seed topology matrix; no settling claim");
                return;
            }
            // submit is sample 0; the first raw step can also have frame.stepIndex()==0.
            for (int step = 1; step <= 6000; step++) {
                LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
                long received = System.nanoTime();
                InitialSpacingFrameAssertions.check(p, frame, p.pins(), false);
                row("SAMPLE", fixture, step, frame.stepIndex(), frame.idle().idle(), frame.idle().rms(), frame.idle().max());
                if (frame.idle().idle()) {
                    first = frame; firstStep = step; firstReceived = received; break;
                }
            }
            assertThat(first).as("first idle within 6000 calls and 600 seconds").isNotNull();
            assertThat(firstReceived - start).isBetween(0L, TimeUnit.SECONDS.toNanos(600));
            row("FIRST_IDLE", fixture, firstStep, firstReceived - start);
            followStart = System.nanoTime();
            long followDeadline = followStart + TimeUnit.SECONDS.toNanos(600);
            for (int i = 1; i <= 100; i++) {
                post = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), followDeadline);
                InitialSpacingFrameAssertions.check(p, post, p.pins(), false);
                maxRms = Math.max(maxRms, post.idle().rms()); maxDisplacement = Math.max(maxDisplacement, post.idle().max());
                if (post.idle().idle()) stillIdle++;
                row("FOLLOW", fixture, i, firstStep + i, post.idle().idle(), post.idle().rms(), post.idle().max());
            }
            long followElapsed = System.nanoTime() - followStart;
            assertThat(followElapsed).isBetween(0L, TimeUnit.SECONDS.toNanos(600));
            row("POST", fixture, firstStep + 100, stillIdle, maxRms, maxDisplacement, followElapsed);
        }
        labels(p);
        LayoutFrame repeatSeed;
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            repeatSeed = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(p, p)),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(600));
            InitialSpacingFrameAssertions.check(p, repeatSeed, p.pins(), true);
        }
        assertThat(repeatSeed.positions()).isEqualTo(seed.positions());
        assertThat(repeatSeed.boundaryDiagnostics()).usingRecursiveComparison().isEqualTo(seed.boundaryDiagnostics());
        assertThat(repeatSeed.edgeConflictDiagnostics()).isEqualTo(seed.edgeConflictDiagnostics());
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            assertThat(engine.apply(InitialSpacingFixtures.request(p, p)).positions()).isEqualTo(raw.positions());
        }
        print(p, "idle", first); print(p, "post100", post);
        endpoints(p, "idle", first); endpoints(p, "post100", post);
        row("NOTE", "Descriptive endpoint gaps; additive MIN_GAP remains 6.0; total rests with enclosure endpoints change.");
    }

    private static void seedGate(GraphProjection p, LayoutFrame seed, String baselineLog, Set<EnclosureHullKey> eligible) throws Exception {
        Map<String, double[]> expected = new LinkedHashMap<String, double[]>();
        if (fixture.equals("reference")) expected.put("Axioms", new double[] {6.0, 24.0, 24.0});
        if (fixture.equals("S1")) expected.put("Sub", new double[] {6.0, 24.0, 24.0});
        if (fixture.equals("two")) expected.put("Root", new double[] {6.0, 24.0, 24.0});
        if (fixture.equals("mixed")) expected.put("Sub", new double[] {7.6, 25.6, 25.6});
        if (fixture.equals("cross")) {
            expected.put("A-sub 0", new double[] {6.0, 17.14733824239645, 20.573669121198225});
            expected.put("A-sub 1", new double[] {6.0, 17.14733824239645, 17.14733824239645});
            expected.put("A-sub 2", new double[] {6.0, 24.0, 24.0});
            expected.put("B-sub 0", new double[] {6.000000000000009, 24.0, 24.0});
            expected.put("B-sub 1", new double[] {6.000000000000009, 24.0, 24.0});
            expected.put("B-sub 2", new double[] {6.000000000000011, 17.14733824239645, 20.573669121198225});
        }
        if (expected.isEmpty()) return;
        Map<EnclosureHullKey, Metric> actual = measure(p, seed.positions());
        assertThat(actual).hasSize(expected.size());
        Map<String, String[]> control = new LinkedHashMap<String, String[]>();
        if (configuration.equals("candidate")) {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(baselineLog), StandardCharsets.UTF_8)) {
                String[] row = line.split("\t");
                if (row.length > 6 && row[0].equals("METRIC") && row[2].equals("seed")) {
                    assertThat(control.put(row[3], row)).isNull();
                    close(Double.parseDouble(row[6]), expected.get(row[4])[0]);
                }
            }
            assertThat(control).hasSize(expected.size());
        }
        for (ProjectedEnclosure e : p.enclosures()) if (actual.containsKey(e.hullKey())) {
            Metric m = actual.get(e.hullKey()); double[] target = expected.get(e.labels().get(0).displayText());
            assertThat(target).isNotNull();
            if (configuration.equals("C0")) close(m.median, target[0]);
            else {
                String[] baseline = control.get(CanonicalKeys.hull(e.hullKey())); assertThat(baseline).isNotNull();
                assertThat(m.median - Double.parseDouble(baseline[6])).isGreaterThanOrEqualTo(6.0 - 1e-6);
                close(m.minimum, target[1]); close(m.median, target[2]);
                if (eligible.contains(e.hullKey())) {
                    for (int i = 0; i < e.directNodes().size(); i++) {
                        ProjectedNodeKey a = e.directNodes().get(i), b = e.directNodes().get((i + 1) % e.directNodes().size());
                        close(distance(seed.positions().nodes().get(a), seed.positions().nodes().get(b)) - radius(p, a) - radius(p, b), 24.0);
                        if (e.directNodes().size() == 2) break;
                    }
                }
            }
        }
    }

    private static void inputs(GraphProjection p) {
        for (ProjectedNode node : p.nodes()) row("INPUT_NODE", CanonicalKeys.node(node.key()), node.label().displayText(),
            node.mapName(), node.graphGroup(), radius(p, node.key()));
        for (ProjectedEnclosure e : p.enclosures()) {
            List<String> direct = new ArrayList<String>(), children = new ArrayList<String>(), labels = new ArrayList<String>();
            for (ProjectedNodeKey key : e.directNodes()) direct.add(CanonicalKeys.node(key));
            for (EnclosureHullKey key : e.directEnclosures()) children.add(CanonicalKeys.hull(key));
            for (org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : e.labels()) labels.add(label.displayText());
            row("INPUT_HULL", CanonicalKeys.hull(e.hullKey()), labels, e.mapName(), e.mapRoot(), e.boundaryTier(),
                e.parentHull().isPresent() ? CanonicalKeys.hull(e.parentHull().get()) : "absent", direct, children);
        }
        for (ProjectedEdge edge : p.edges()) for (EdgeContributor c : edge.contributors()) row("INPUT_EDGE",
            CanonicalKeys.endpoint(edge.first()), CanonicalKeys.endpoint(edge.second()), c.connectorDescriptor().isPresent(),
            CanonicalKeys.endpoint(c.projectedSource()), CanonicalKeys.endpoint(c.projectedTarget()), c.arrowAtSource(), c.arrowAtTarget(),
            c.sourceLabel(), c.middleLabel(), c.targetLabel());
    }

    private static void labels(GraphProjection p) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (ProjectedEnclosure e : p.enclosures()) if (e.boundaryTier() != BoundaryTier.SUPPRESSED) {
            for (org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : e.labels()) {
                Dimension2D dimension = InitialSpacingFrameAssertions.metrics().measure(label.displayText(), e.boundaryTier());
                finite(dimension.getWidth()); finite(dimension.getHeight());
                assertThat(dimension.getWidth()).isPositive(); assertThat(dimension.getHeight()).isPositive();
                String value = CanonicalKeys.hull(e.hullKey()) + "\t" + label.displayText() + "\t"
                    + e.boundaryTier() + "\t" + dimension.getWidth() + "\t" + dimension.getHeight();
                sha.update((value + "\n").getBytes(StandardCharsets.UTF_8)); row("LABEL", value);
            }
        }
        StringBuilder hex = new StringBuilder(); for (byte b : sha.digest()) hex.append(String.format("%02x", b & 255));
        row("LABEL_SHA256", hex);
    }

    private static Set<EnclosureHullKey> classify(GraphProjection p, LayoutFrame raw, LayoutFrame seed) {
        Set<EnclosureHullKey> eligibleHulls = new LinkedHashSet<EnclosureHullKey>();
        GeometryTextMetrics metrics = InitialSpacingFrameAssertions.metrics();
        BoundarySeparationResult boundary = new BoundarySeparationCorrection().apply(p, raw.positions(), metrics, p.pins());
        EdgeConflictResult edge = new EdgeConflictCorrection().apply(p, boundary.positions(), metrics, p.pins(),
            ProjectedEndpointVisibility.visibleEndpoints(p.nodes(), p.enclosures()), boundary);
        assertThat(edge.positions()).isEqualTo(seed.positions());
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        for (ProjectedEnclosure e : p.enclosures()) if (e.directNodes().size() >= 2) {
            double firstRadius = radius(p, e.directNodes().get(0)), maximumShift = 0.0;
            boolean mixed = false, repair = false;
            ClusterPacking.ClusterPlacement content = sizes.placements(e.hullKey()).get(0);
            assertThat(content.item.anchorKey).isNull();
            LayoutPoint composed = InitialSpacingConsumers.composed(sizes, e.hullKey());
            boolean translated = content.x != 0.0 || content.y != 0.0 || composed.x() != 0.0 || composed.y() != 0.0;
            for (int i = 0; i < e.directNodes().size(); i++) {
                ProjectedNodeKey key = e.directNodes().get(i);
                mixed |= radius(p, key) != firstRadius;
                LayoutPoint a = raw.positions().nodes().get(key), b = boundary.positions().nodes().get(key), c = seed.positions().nodes().get(key);
                double shift = distance(a, c); maximumShift = Math.max(maximumShift, shift);
                boolean record = boundary.appliedDisplacements().containsKey(CanonicalKeys.node(key));
                for (EdgeConflictDiagnostics.Repair r : edge.diagnostics().repairsApplied()) record |= r.nodeKeys().contains(key);
                boolean stageMoved = distance(a, b) > 1e-9 || distance(b, c) > 1e-9;
                repair |= record || stageMoved || shift > 1e-9;
                row("ELIGIBILITY_NODE", fixture, CanonicalKeys.hull(e.hullKey()), CanonicalKeys.node(key), radius(p, key),
                    analyticRadius(p, e, gap), 2.0 * Math.PI * i / e.directNodes().size(), a.x(), a.y(), b.x(), b.y(), c.x(), c.y(), record);
            }
            boolean eligible = !mixed && !translated && !repair;
            row("ELIGIBILITY", fixture, CanonicalKeys.hull(e.hullKey()), e.labels().get(0).displayText(), content.x, content.y,
                composed.x(), composed.y(), maximumShift, mixed, translated, repair, eligible);
            if (eligible) eligibleHulls.add(e.hullKey());
        }
        return eligibleHulls;
    }

    private static void print(GraphProjection p, String stage, LayoutFrame frame) {
        for (ProjectedNode node : p.nodes()) {
            LayoutPoint v = frame.positions().nodes().get(node.key());
            row("POINT", fixture, stage, "node", CanonicalKeys.node(node.key()), v.x(), v.y(), radius(p, node.key()));
        }
        for (ProjectedEnclosure e : p.enclosures()) {
            LayoutPoint v = frame.positions().anchors().get(e.hullKey());
            row("POINT", fixture, stage, "enclosure", CanonicalKeys.hull(e.hullKey()), v.x(), v.y(), analyticRadius(p, e, gap));
        }
        for (Map.Entry<EnclosureHullKey, Metric> entry : measure(p, frame.positions()).entrySet()) {
            ProjectedEnclosure enclosure = null;
            for (ProjectedEnclosure e : p.enclosures()) if (e.hullKey().equals(entry.getKey())) enclosure = e;
            int nativeCount = 0, relationshipCount = 0;
            for (ProjectedEdge edge : p.edges()) for (EdgeContributor c : edge.contributors()) {
                for (ProjectedEndpointKey endpoint : Arrays.asList(edge.first(), edge.second())) if (endpoint.isNode()
                        && enclosure.directNodes().contains(endpoint.node().get())) {
                    if (c.connectorDescriptor().isPresent()) nativeCount++; else relationshipCount++;
                }
            }
            Metric m = entry.getValue();
            row("METRIC", fixture, stage, CanonicalKeys.hull(entry.getKey()), enclosure.labels().get(0).displayText(),
                m.minimum, m.median, m.nearest, Math.abs(m.median - 6.0) <= 1e-9, nativeCount, relationshipCount);
        }
        BoundarySeparationDiagnostics b = frame.boundaryDiagnostics(); EdgeConflictDiagnostics e = frame.edgeConflictDiagnostics();
        row("DIAGNOSTICS", fixture, stage, frame.failed(), frame.residualViolations(), b.boundaryVerified(), b.boundaryCovered(),
            b.hullResidualViolations(), b.rounds(), e.repairsApplied().size(), e.rounds(), e.verifications(), e.conflictsResidual(),
            e.indexInsertedItems(), e.indexPairChecks(), e.verificationWork(), e.indexBudgetExhausted(), e.verificationBudgetExhausted(), e.detectionTruncated());
        for (EdgeConflictDiagnostics.Repair r : e.repairsApplied()) row("REPAIR", fixture, stage, r.kind(), r.nodeKeys(), r.fromCentres(), r.toCentres(), r.batchIndex());
    }

    private static void endpoints(GraphProjection p, String stage, LayoutFrame frame) {
        Map<String, List<Double>> byType = new TreeMap<String, List<Double>>();
        for (ProjectedEdge edge : p.edges()) {
            double value = distance(endpointPosition(p, frame, edge.first()), endpointPosition(p, frame, edge.second()))
                - InitialSpacingConsumers.endpointRadius(p, edge.first(), gap) - InitialSpacingConsumers.endpointRadius(p, edge.second(), gap);
            finite(value);
            String first = edge.first().isNode() ? "node" : "enclosure", second = edge.second().isNode() ? "node" : "enclosure";
            String pair = first.equals(second) ? first + "-" + second : "node-enclosure";
            if (!byType.containsKey(pair)) byType.put(pair, new ArrayList<Double>());
            byType.get(pair).add(value);
            for (EdgeContributor contributor : edge.contributors()) row("ENDPOINT", fixture, stage, CanonicalKeys.endpoint(edge.first()), CanonicalKeys.endpoint(edge.second()),
                contributor.connectorDescriptor().isPresent() ? "nativeConnector" : "graphRelationship",
                !edge.first().mapReferenceId().equals(edge.second().mapReferenceId()), first, second, value, pair);
        }
        for (String pair : Arrays.asList("node-node", "node-enclosure", "enclosure-enclosure")) {
            List<Double> values = byType.get(pair);
            if (values == null) { row("ENDPOINT_AGGREGATE", fixture, stage, pair, "NOT_APPLICABLE", "NOT_APPLICABLE"); continue; }
            Collections.sort(values); int n = values.size();
            double median = n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
            row("ENDPOINT_AGGREGATE", fixture, stage, pair, values.get(0), median);
        }
    }

    private static LayoutPoint endpointPosition(GraphProjection p, LayoutFrame frame, ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? frame.positions().nodes().get(endpoint.node().get())
            : frame.positions().anchors().get(InitialSpacingConsumers.endpointHull(p, endpoint));
    }

    private static void row(String tag, Object... values) {
        StringBuilder line = new StringBuilder(tag);
        for (Object value : values) line.append('\t').append(value);
        System.out.println(line);
    }
}
```

The `seedGate` table freezes the seed C0-control and candidate minima/medians from spec D. Noneligible roots are still checked by their general independent nearest-gap metric and the candidate median increase of at least `6.0 - 1e-6`. An exact 24.0 assertion is attached only to eligible uniform corrected seeds and the analytic raw ring where appropriate. The two-node root is included in the experiment vector so the metric cannot silently be empty.

- [ ] **Step 2: Add the exact serial coordinator.** Python's standard library is sufficient; no dependency/configuration/build-file edit is needed. It reconstructs POINT-based nearest-gap metrics independently, freezes C0 S1 post-100 qualification at 184 calls/100 idle/`0.03903402780948504` RMS/`0.06011499052181499` max, asserts primary first-idle steps `S1:84→58`, `reference:42→37`, `cross:756→3455`, records elapsed values, and preserves descriptive first-idle/post-100 endpoint gaps. The uniform larger-disc and permutation tests remain in the focused Java suite rather than being substituted for those primary frozen fixtures.

Create `freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py` with exactly:

```python
import argparse
import hashlib
import json
import math
import os
import pathlib
import shutil
import statistics
import subprocess
import tempfile
import time

BASE = 'cb8782fb3742f378b2907198fd2343292415da95'
JAVA = '/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java'
PREFIX = 'freeplane_plugin_graph/src/main/java/'
ENGINE = PREFIX + 'org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java'
PACKING = PREFIX + 'org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java'
FROZEN = ['freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java',
          'freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java',
          'freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java']
FIXTURES = ['S1', 'reference', 'cross', 'mixed', 'two', 'endpoints-cross-ne', 'endpoints-cross-ee'] + [
    'matrix-' + scope + '-' + pair for scope in ('same', 'cross') for pair in ('nn', 'ne', 'ee')]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run_acceptance(repo, candidate_cp, output):
    output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
    manifest = dict(base=BASE, head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip(),
                    environment={k: env.get(k) for k in ('JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'LANG', 'LC_ALL')},
                    commands=[], source_sha256={}, classpath=candidate_cp, jar_sha256={})
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')

    def command(label, argv, timeout):
        start = time.monotonic()
        receipt = dict(label=label, argv=argv, utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
        manifest['commands'].append(receipt)
        try:
            with (output / (label + '.log')).open('w') as log:
                result = subprocess.run(argv, cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
            receipt.update(returncode=result.returncode, elapsed_s=time.monotonic() - start)
            assert result.returncode == 0, (label, output)
        except BaseException as error:
            receipt.update(error=repr(error), elapsed_s=time.monotonic() - start)
            raise
        finally:
            (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')

    command('java-version', [JAVA, '-version'], 60)
    command('gradle-version', ['gradle', '--version'], 60)
    for item in candidate_cp.split(os.pathsep):
        path = pathlib.Path(item)
        if path.suffix == '.jar' and path.is_file(): manifest['jar_sha256'][str(path)] = digest(path.read_bytes())
    sources = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE, '--', PREFIX], cwd=repo, text=True).splitlines()
    baseline_files = []
    expected_overlay = {}
    for relative in sources + FROZEN:
        if not relative.endswith('.java'): continue
        data = subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=repo)
        manifest['source_sha256'][relative] = dict(C0=digest(data))
        if relative.startswith(PREFIX):
            current = (repo / relative).read_bytes()
            manifest['source_sha256'][relative]['candidate'] = digest(current)
            expected = data.decode('utf-8')
            if relative == ENGINE:
                old = 'static final double NODE_RADIUS = 8.0;'
                assert expected.count(old) == 1
                expected = expected.replace(old, old + '\n    static final double PREFERRED_NODE_GAP = 24.0;')
                old = '(2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)'
                assert expected.count(old) == 1
                expected = expected.replace(old, '(2.0 * maxRadius + PREFERRED_NODE_GAP)')
            if relative == PACKING:
                old = 'clearanceX < gap && clearanceY < gap'
                assert expected.count(old) == 1
                expected = expected.replace(old, 'clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON')
            assert current == expected.encode('utf-8'), 'Unexpected production difference: ' + relative
            expected_overlay[relative] = digest(current)
        target = output / 'C0-src' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        baseline_files.append(str(target))
    current_sources = {str(p.relative_to(repo)) for p in (repo / PREFIX).rglob('*.java')}
    assert current_sources == {x for x in sources if x.endswith('.java')}, 'Production source added or removed'
    baseline = output / 'C0-classes'
    baseline.mkdir()
    empty = output / 'empty-sourcepath'
    empty.mkdir()
    command('compile-C0', [JAVA + 'c', '--release', '8', '-encoding', 'UTF-8', '-implicit:none', '-sourcepath',
            str(empty), '-classpath', candidate_cp, '-d', str(baseline)] + baseline_files, 600)
    reports = {}
    # Serial JVMs; exactly one fixture/configuration in each JVM; C0 S1 qualifies first.
    for configuration in ('C0', 'candidate'):
        for fixture in FIXTURES:
            cp = str(baseline) + os.pathsep + candidate_cp if configuration == 'C0' else candidate_cp
            label = configuration + '-' + fixture
            command(label, [JAVA, '-ea', '-cp', cp,
                    'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingProbe', configuration, fixture,
                    str(output / ('C0-' + fixture + '.log'))], 1900)
            rows = [line.split('\t') for line in (output / (label + '.log')).read_text().splitlines() if '\t' in line]
            reports[(configuration, fixture)] = rows
            assert [r for r in rows if r[0] == 'CONFIG'][0][3:6] == [
                '6.0' if configuration == 'C0' else '24.0', '6.0', '8.0']
            locations = [r for r in rows if r[0] == 'CODE_SOURCE']
            assert len(locations) == 4
            for r in locations[:3]:
                assert (str(baseline) in r[2]) == (configuration == 'C0'), r
            label_sha = [r for r in rows if r[0] == 'LABEL_SHA256']
            assert len(label_sha) == 1
            if configuration == 'candidate':
                assert label_sha[0][1] == [r for r in reports[('C0', fixture)] if r[0] == 'LABEL_SHA256'][0][1]
                assert [r for r in rows if r[0].startswith('INPUT_')] == [
                    r for r in reports[('C0', fixture)] if r[0].startswith('INPUT_')]
                frozen = {'reference': {'Axioms': (36.0, 0.0, 0.0, False, True, False, False)},
                    'S1': {'Sub': (0.0, 0.0, 0.0, False, False, False, True)},
                    'cross': {'A-sub 0': (0.0, 0.0, 11.0, False, False, True, False),
                        'A-sub 1': (-128.56854249492380, 0.0, 11.0, False, True, True, False),
                        'A-sub 2': (128.56854249492380, 0.0, 0.0, False, True, False, False),
                        'B-sub 0': (-417.70562748477140, 0.0, 0.0, False, True, False, False),
                        'B-sub 1': (-546.27416997969520, 0.0, 0.0, False, True, False, False),
                        'B-sub 2': (-289.13708498984760, 0.0, 11.0, False, True, True, False)}}
                if fixture in frozen:
                    eligibility = [r for r in rows if r[0] == 'ELIGIBILITY']
                    assert len(eligibility) == len(frozen[fixture])
                    for r in eligibility:
                        target = frozen[fixture][r[3]]
                        assert all(abs(float(v)) <= 1e-9 for v in r[4:6])
                        assert all(abs(float(v) - expected) <= 1e-6 for v, expected in zip(r[6:9], target[:3]))
                        assert [v == 'true' for v in r[9:13]] == list(target[3:])
            first = [r for r in rows if r[0] == 'FIRST_IDLE']
            post = [r for r in rows if r[0] == 'POST']
            follow = [r for r in rows if r[0] == 'FOLLOW']
            if fixture.startswith('matrix-'):
                assert not first and not post and not follow
                assert len([r for r in rows if r[0] == 'CONSUMER_ONLY']) == 1
            else:
                assert len(first) == len(post) == 1 and len(follow) == 100
                assert int(first[0][3]) <= 600_000_000_000
                assert int(post[0][6]) <= 600_000_000_000
            if fixture in ('S1', 'reference', 'cross'):
                expected_step = {'S1': (84, 58), 'reference': (42, 37), 'cross': (756, 3455)}[fixture][configuration == 'candidate']
                assert int(first[0][2]) == expected_step
            if configuration == 'C0' and fixture == 'S1':
                assert int(post[0][2]) == 184 and int(post[0][3]) == 100
                assert math.isclose(float(post[0][4]), 0.03903402780948504, abs_tol=1e-6)
                assert math.isclose(float(post[0][5]), 0.06011499052181499, abs_tol=1e-6)
            if fixture == 'S1': assert int(post[0][3]) == 100
            # Independent enumeration from printed coordinates catches metric implementation mutations.
            for stage in (('raw', 'seed') if fixture.startswith('matrix-') else ('raw', 'seed', 'idle', 'post100')):
                points = {r[4]: tuple(map(float, r[5:8])) for r in rows if r[0] == 'POINT' and r[2] == stage and r[3] == 'node'}
                members = {}
                for r in rows:
                    if r[0] == 'ELIGIBILITY_NODE': members.setdefault(r[2], []).append(r[3])
                for r in [r for r in rows if r[0] == 'METRIC' and r[2] == stage]:
                    keys = members[r[3]]
                    gaps = [min(math.hypot(points[a][0] - points[b][0], points[a][1] - points[b][1])
                        - points[a][2] - points[b][2] for b in keys if b != a) for a in keys]
                    assert abs(min(gaps) - float(r[5])) <= 1e-6
                    assert abs(statistics.median(gaps) - float(r[6])) <= 1e-6
            # Persist a machine-readable full record set even before the final comparison.
            (output / (label + '.json')).write_text(json.dumps(rows, indent=2) + '\n')
            print('PASS', label, 'first-idle', first[0][2], flush=True)
    compare(reports, output)
    write_report(reports, output)
    manifest['epsilon_necessity'] = 'NOT_ESTABLISHED'
    manifest['epsilon_reason'] = 'Predicate ULP sensitivity does not reproduce a larger-item C0 packItems slot failure.'
    manifest['status'] = 'MODEL_GATES_PASSED; UI_NOT_MEASURED_DELIVERY_GATE_OPEN'
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


def compare(reports, output):
    expected = {'reference': {'Axioms': (6.0, 24.0, 24.0)}, 'S1': {'Sub': (6.0, 24.0, 24.0)},
        'cross': {'A-sub 0': (6.0, 17.14733824239645, 20.573669121198225),
                  'A-sub 1': (6.0, 17.14733824239645, 17.14733824239645), 'A-sub 2': (6.0, 24.0, 24.0),
                  'B-sub 0': (6.000000000000009, 24.0, 24.0), 'B-sub 1': (6.000000000000009, 24.0, 24.0),
                  'B-sub 2': (6.000000000000011, 17.14733824239645, 20.573669121198225)},
        'mixed': {'Sub': (7.6, 25.6, 25.6)}, 'two': {'Root': (6.0, 24.0, 24.0)}}
    comparisons = []
    endpoint_rows = []
    for fixture, hulls in expected.items():
        old = {r[3]: r for r in reports[('C0', fixture)] if r[0] == 'METRIC' and r[2] == 'seed'}
        new = {r[3]: r for r in reports[('candidate', fixture)] if r[0] == 'METRIC' and r[2] == 'seed'}
        assert set(old) == set(new) and len(old) == len(hulls)
        assert {r[4] for r in old.values()} == set(hulls)
        # Control drift is checked for all rows before any candidate increase comparison.
        for key, row in old.items(): assert abs(float(row[6]) - hulls[row[4]][0]) <= 1e-6
        for key, row in new.items():
            baseline, minimum, median = hulls[row[4]]
            increase = float(row[6]) - float(old[key][6])
            assert increase >= 6.0 - 1e-6, (fixture, row[4], increase)
            assert abs(float(row[5]) - minimum) <= 1e-6 and abs(float(row[6]) - median) <= 1e-6
            comparisons.append(dict(fixture=fixture, hull=key, label=row[4], C0_median=float(old[key][6]),
                                    candidate_minimum=float(row[5]), candidate_median=float(row[6]), increase=increase))
    for fixture in FIXTURES:
        for stage in ('idle', 'post100'):
            old = [r for r in reports[('C0', fixture)] if r[0] == 'ENDPOINT' and r[2] == stage]
            new = [r for r in reports[('candidate', fixture)] if r[0] == 'ENDPOINT' and r[2] == stage]
            assert len(old) == len(new)
            for a, b in zip(old, new):
                assert a[:9] == b[:9] and a[10] == b[10]
                endpoint_rows.append(dict(fixture=fixture, stage=stage, first=a[3], second=a[4], contributor=a[5],
                    crossMap=a[6] == 'true', first_type=a[7], second_type=a[8], pair=a[10], C0=float(a[9]), candidate=float(b[9])))
    for stage in ('idle', 'post100'):
        assert any(r['stage'] == stage and not r['crossMap'] and r['pair'] == 'node-node' for r in endpoint_rows)
        assert any(r['stage'] == stage and r['crossMap'] and r['pair'] == 'node-node' for r in endpoint_rows)
        for pair in ('node-enclosure', 'enclosure-enclosure'): assert any(r['stage'] == stage and r['pair'] == pair for r in endpoint_rows)
    aggregates = []
    for stage in ('idle', 'post100'):
        for pair in ('node-node', 'node-enclosure', 'enclosure-enclosure'):
            group = [r for r in endpoint_rows if r['stage'] == stage and r['pair'] == pair]
            for config in ('C0', 'candidate'):
                values = [r[config] for r in group]
                assert values and all(math.isfinite(x) for x in values)
                aggregates.append(dict(stage=stage, pair=pair, configuration=config, minimum=min(values), median=statistics.median(values)))
    rests = []
    for config in ('C0', 'candidate'):
        for fixture in FIXTURES:
            if not fixture.startswith('matrix-'): continue
            rows = [r for r in reports[(config, fixture)] if r[0] == 'REST']
            assert len(rows) == 1
            row = rows[0]
            pair = fixture.rsplit('-', 1)[1]
            target = {'nn': (22.0, 22.0), 'ne': (29.556349186104047, 42.28427124746190),
                      'ee': (37.112698372208094, 62.568542494923804)}[pair][config == 'candidate']
            assert abs(float(row[6]) - target) <= 1e-6
            assert row[3] == ('true' if '-cross-' in fixture else 'false')
            rests.append(dict(configuration=config, fixture=fixture, first=row[1], second=row[2], crossMap=row[3],
                              first_radius=float(row[4]), second_radius=float(row[5]), rest=float(row[6])))
    result = dict(seed=comparisons, endpoints=endpoint_rows, endpoint_aggregates=aggregates, rest_matrix=rests,
        note='Endpoint gaps are descriptive. Additive MIN_GAP remains 6.0; total rests involving enclosures change.')
    (output / 'comparison.json').write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')


def write_report(reports, output):
    result = json.loads((output / 'comparison.json').read_text())
    lines = ['# Initial-spacing acceptance receipts', '',
        'EPSILON_NECESSITY = NOT_ESTABLISHED: predicate ULP sensitivity does not reproduce a larger-item C0 packItems slot failure.', '',
        'UI evidence: NOT_MEASURED; mandatory same-build application capture gate remains open.', '',
        'Seed median increases apply to every qualifying primary enclosure. Settled and realized endpoint gaps are descriptive.', '',
        '| Fixture | Hull | C0 median | Candidate min | Candidate median | Increase |',
        '| --- | --- | ---: | ---: | ---: | ---: |']
    for r in result['seed']:
        lines.append('| {fixture} | {label} | {C0_median:.17g} | {candidate_minimum:.17g} | {candidate_median:.17g} | {increase:.17g} |'.format(**r))
    lines += ['', '| Config / fixture | First-idle calls | First-idle ns | Post-100 idle count | Max RMS | Max displacement | Follow-up ns |',
              '| --- | ---: | ---: | ---: | ---: | ---: | ---: |']
    for (config, fixture), rows in reports.items():
        first = next((r for r in rows if r[0] == 'FIRST_IDLE'), None)
        post = next((r for r in rows if r[0] == 'POST'), None)
        if first is None:
            lines.append('| ' + config + '/' + fixture + ' | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |')
        else:
            lines.append('| ' + config + '/' + fixture + ' | ' + ' | '.join(first[2:4] + post[3:7]) + ' |')
    lines += ['', '| Config / fixture | Stage | Hull | Minimum | Median | Median at collision floor | Native endpoints | Relationship endpoints |',
              '| --- | --- | --- | ---: | ---: | --- | ---: | ---: |']
    for (config, fixture), rows in reports.items():
        for r in rows:
            if r[0] == 'METRIC' and r[2] in ('idle', 'post100'):
                lines.append('| ' + config + '/' + fixture + ' | ' + ' | '.join([r[2], r[4], r[5], r[6], r[8], r[9], r[10]]) + ' |')
    lines += ['', result['note'], '',
        '| Fixture | Stage | First endpoint | Second endpoint | Contributor | Cross map | Pair | C0 realized gap | Candidate realized gap |',
        '| --- | --- | --- | --- | --- | --- | --- | ---: | ---: |']
    for r in result['endpoints']:
        lines.append('| ' + ' | '.join(str(r[k]).replace('|', '&#124;') for k in (
            'fixture', 'stage', 'first', 'second', 'contributor', 'crossMap', 'pair', 'C0', 'candidate')) + ' |')
    lines += ['', '| Stage | Pair | Config | Minimum | Median |', '| --- | --- | --- | ---: | ---: |']
    for r in result['endpoint_aggregates']:
        lines.append('| ' + ' | '.join(str(r[k]) for k in ('stage', 'pair', 'configuration', 'minimum', 'median')) + ' |')
    lines += ['', '| Config | Fixture | First radius | Second radius | Observed rest |',
        '| --- | --- | ---: | ---: | ---: |']
    for r in result['rest_matrix']:
        lines.append('| ' + ' | '.join(str(r[k]) for k in ('configuration', 'fixture', 'first_radius', 'second_radius', 'rest')) + ' |')
    lines += ['', 'Matrix cases are raw/seed consumer checks only; endpoints-cross-ne/ee are qualified settled supplemental cases.',
        'Full precision coordinates, input order, classification, repairs, clocks and all 100 follow-up samples are in the adjacent per-run .log/.json files.',
        'C0 S1 qualification is measured before candidate runs. Missing/failed/timeout/truncated runs fail once and cannot be promoted as settled.',
        'A floor label describes a median; topology incidence counts do not identify its binding cause.', '']
    (output / 'acceptance.md').write_text('\n'.join(lines))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=pathlib.Path, required=True)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    run_acceptance(args.repo.resolve(), args.classpath, args.output.resolve())
```

- [ ] **Step 3: Add the JUnit entrypoint and final suite.** The acceptance test resolves the repository root rather than assuming the Gradle worker's cwd. It records a unique output directory before launching the coordinator, inherits its output, waits up to the full serialized experiment vector's aggregate bound, and preserves the directory on every failure. Each internal per-fixture deadline remains 600 seconds; the outer guard of fourteen hours never relaxes one.

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingAcceptanceShould.java` with exactly:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class InitialSpacingAcceptanceShould {
    @Test public void compareFrozenC0AndCandidateInSerialFreshJvms() throws Exception {
        assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
        Path repo = Paths.get("").toAbsolutePath();
        while (repo != null && !Files.exists(repo.resolve(".git"))) repo = repo.getParent();
        assertThat(repo).as("repository root").isNotNull();
        Set<String> classpath = new LinkedHashSet<String>();
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) if (url.getProtocol().equals("file"))
                    classpath.add(Paths.get(url.toURI()).toString());
            }
        }
        classpath.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        Path build = repo.resolve("freeplane_plugin_graph/build"); Files.createDirectories(build);
        Path output = Files.createTempDirectory(build, "initial-spacing-");
        Files.write(build.resolve("initial-spacing-current.txt"), (output.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        Process process = new ProcessBuilder("python3", repo.resolve(
            "freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py").toString(),
            "--repo", repo.toString(), "--classpath", String.join(java.io.File.pathSeparator, classpath),
            "--output", output.toString()).inheritIO().start();
        try {
            assertThat(process.waitFor(14L, TimeUnit.HOURS)).as("acceptance driver bounded completion; receipts %s", output).isTrue();
            assertThat(process.exitValue()).as("acceptance driver exit; receipts %s", output).isZero();
        }
        finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        assertThat(Files.isRegularFile(output.resolve("comparison.json"))).isTrue();
        assertThat(Files.isRegularFile(output.resolve("acceptance.md"))).isTrue();
    }
}
```

Set the suite to:

```java
package org.freeplane.plugin.graph.layout.graphstream;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({InitialSpacingMetricsShould.class, InitialSpacingSeedShould.class, InitialSpacingLifecycleShould.class, InitialSpacingAcceptanceShould.class})
public class InitialSpacingShould { }
```

- [ ] **Step 4: Run the scoped acceptance entrypoint.**

Run from the repository root:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
```

Expected green with `comparison.json`, `acceptance.md`, `manifest.json`, per-configuration/per-fixture logs and parsed JSON in the directory named by `build/initial-spacing-current.txt`. Check every failure receipt before considering another run; never choose a faster replicate. Missing source history, C0 drift, stale code source, unexpected production changes, missing sample, failed frame, nonfinite metric, exhausted budget or a deadline overrun is a failed gate, with its actual error retained.

**Required receipt interpretation:**

- Seed table: spec D per-enclosure minimum/median targets; C0 controls within `1e-6`; candidate median gain at least `6.0-1e-6`; eligible corrected/raw uniform gaps 24.0; mixed raw/corrected oracle as classified. Full eligibility tables carry radii, raw ring radius/order, parent/packing/composed offsets, correction movement/repair identities, and flags, including the unchanged noneligible Axioms packing offset `36.0` and all six cross-map sub-enclosures.
- First idle: print every sample index separately from `LayoutFrame.stepIndex()`, first-idle step/elapsed nanoseconds and diagnostics; print full first-idle and first-idle+100 minimum/median per enclosure, retained node/hull/conflict residual keys/counters and all 100 follow diagnostics. The S1 first-idle median is `7.085863751184222`; `6.552` is a post-100 value and must not be used as the first-idle observation. The reference `13.018` and cross-map `6.217–12.094` values likewise describe post-100, not first idle.
- Settled observations are descriptive. Candidate medians may improve, overlap or decrease and may approach 6.0. There is no settled +6 threshold, universal monotone improvement, exact mixed-ring 24 promise, persistent-idle guarantee, or wider relationship additive clearance.
- Endpoint records: at first idle and post-100, keep contributor kind (`nativeConnector` / `graphRelationship`), canonical endpoint keys/types, crossMap flag and actual realized gap; compare C0/candidate, preserve negative/decreased values, aggregate finite min/median by endpoint pair. Require at least one same-map and one cross-map node–node record in each configuration/stage. Node–enclosure/enclosure–enclosure supplemental fixtures provide actual measurements; absent categories have explicit `NOT_APPLICABLE`, never an inferred improvement.
- Matrix records: keep actual node/enclosure radii and actual REST values for all six scope×type combinations in `comparison.json`/`acceptance.md`; the focused Java suite covers reversed inputs and independent configured-extents/topology/repulsion consumers.
- Epsilon: `EPSILON_NECESSITY = NOT_ESTABLISHED` is explicit. The receipt states that predicate ULP sensitivity did not reproduce the larger-item C0 `packItems` slot failure. Do not label that test a proven real-layout necessity witness.
- UI status: `MODEL_GATES_PASSED; UI_NOT_MEASURED_DELIVERY_GATE_OPEN`. Preserve `NOT_MEASURED` for the application pixel claim; model-space success cannot change this field.

- [ ] **Commit: Inspect and record this task.** Run `git diff --check`, inspect `git diff` and `git diff --cached`, then stage only the listed task files.

```sh
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingProbe.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingAcceptanceShould.java freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java
git diff --cached --check
git diff --cached
git commit -m "graph-initial-spacing: Add isolated spacing acceptance measurements [graph-initial-spacing]"
```

## Task 6: Run the delivery gates and publish the evidence handoff

**Files:**

- Create: `freeplane_plugin_graph/doc/initial-spacing/collect-receipt.py`
- Create: `freeplane_plugin_graph/doc/initial-spacing/UI-CAPTURE-RECIPE.md`
- Create: `freeplane_plugin_graph/doc/initial-spacing/validation.log`
- Create: `freeplane_plugin_graph/doc/initial-spacing/collection.json`
- Create: `freeplane_plugin_graph/doc/initial-spacing/implementation.sha256`
- Create: `freeplane_plugin_graph/doc/initial-spacing/runs/**`
- Create: `freeplane_plugin_graph/doc/initial-spacing/replay/**`
- Create: `freeplane_plugin_graph/doc/initial-spacing/test-results/**`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`

**Interfaces:**

Consumes:

- `CLI InitialSpacingProbe <C0|candidate> <fixture-name> <absolute-C0-log-path>`
- `CLI run-initial-spacing.py --repo <absolute-repo> --classpath <candidate-classpath> --output <new-output-directory>`
- `freeplane_plugin_graph/build/initial-spacing-current.txt: absolute directory of the complete latest attempt`
- `manifest.json + comparison.json + acceptance.md + per-fixture .json/.log receipts from task 5`

Produces:

- `CLI collect-receipt.py (run from repository root; no arguments)`
- `freeplane_plugin_graph/doc/initial-spacing/collection.json: source/artifact/receipt provenance and open UI delivery status`
- `freeplane_plugin_graph/doc/initial-spacing/UI-CAPTURE-RECIPE.md: external application-capture recipe and receipt schema`

**Implementer tier:** Standard
**Lane:** spacing
**Owns:** freeplane_plugin_graph/**
**Depends on:** 5

**Purpose / acceptance:** Spec K plus the full A–K ledger. This task runs the prescribed validation vector and collects the actual results into a reviewable commit. It supplies the collection recipe for the external three-pair application capture gate; it never tries to close that gate from headless data or builds a UI tool. Do not edit a production/test file to resolve a discrepancy without a task-level finding and its evidence.

- [ ] **Step 1: Create the exact collection script.** It preserves every completed/failed `build/initial-spacing-*` attempt, requires the latest run's model gates to pass, hashes the implementation before the evidence commit and records its actual HEAD, and inventories built artifacts. This avoids a circular claim that a report commit knows its own OID. UI evidence must match the recorded implementation revision and artifact set; after any further source/build change, regenerate the evidence and application captures.

Create `freeplane_plugin_graph/doc/initial-spacing/collect-receipt.py` with exactly:

```python
#!/usr/bin/env python3
import hashlib
import json
import pathlib
import re
import shutil
import subprocess

repo = pathlib.Path.cwd().resolve()
assert (repo / '.git').exists()
assert (repo / 'freeplane_plugin_graph').is_dir()
output = repo / 'freeplane_plugin_graph/doc/initial-spacing'
output.mkdir(parents=True, exist_ok=True)
latest = pathlib.Path((repo / 'freeplane_plugin_graph/build/initial-spacing-current.txt').read_text().strip()).resolve()
assert latest.parent == (repo / 'freeplane_plugin_graph/build').resolve()
manifest = json.loads((latest / 'manifest.json').read_text())
assert manifest['status'] == 'MODEL_GATES_PASSED; UI_NOT_MEASURED_DELIVERY_GATE_OPEN'
assert (latest / 'acceptance.md').is_file() and (latest / 'comparison.json').is_file()
runs = output / 'runs'
runs.mkdir(exist_ok=True)
for attempt in sorted((repo / 'freeplane_plugin_graph/build').glob('initial-spacing-*')):
    if attempt.is_dir():
        shutil.copytree(str(attempt), str(runs / attempt.name), dirs_exist_ok=True)
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=str(repo), text=True).strip()
replays = set(re.findall(r'/tmp/initial-spacing-replay-[A-Za-z0-9_-]+', (output / 'validation.log').read_text(errors='replace')))
assert len(replays) == 1, 'expected one exact spec K replay directory in validation.log'
replay = pathlib.Path(next(iter(replays)))
assert (replay / 'commands.json').is_file()
shutil.copytree(str(replay), str(output / 'replay'), dirs_exist_ok=True)
results = repo / 'freeplane_plugin_graph/build/test-results'
assert list(results.rglob('*.xml')), 'missing Gradle XML test receipts'
shutil.copytree(str(results), str(output / 'test-results'), dirs_exist_ok=True)
assert manifest['head'] == head, 'latest model receipt was measured at a different implementation HEAD'
planning_head = '8c36da1b7a0f0794c75bccbe29dabec43c770a3f'
allowed_outside = {'docs/superpowers/plans/2026-10-20-graph-initial-spacing.md'}
for args in [['git', 'diff', '--name-only', planning_head, 'HEAD', '--', '.', ':!freeplane_plugin_graph/**'],
             ['git', 'diff', '--name-only', 'HEAD', '--', '.', ':!freeplane_plugin_graph/**']]:
    outside = subprocess.check_output(args, cwd=str(repo), text=True).splitlines()
    assert set(outside) <= allowed_outside, 'out-of-scope tracked edits: ' + repr(outside)
changed_main = subprocess.check_output(['git', 'diff', '--name-only', manifest['base'], 'HEAD', '--',
    'freeplane_plugin_graph/src/main/java'], cwd=str(repo), text=True).splitlines()
assert set(changed_main) == {
    'freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java',
    'freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java'}
files = sorted(list((repo / 'freeplane_plugin_graph/src/main').rglob('*')) +
               list((repo / 'freeplane_plugin_graph/src/test').rglob('*')))
hashes = {p.relative_to(repo).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()}
(output / 'implementation.sha256').write_text(''.join(v + '  ' + k + '\n' for k, v in hashes.items()))
artifacts = {}
for base in [repo / 'freeplane_plugin_graph/build/libs', repo / 'BIN']:
    if base.is_dir():
        for p in sorted(base.rglob('*')):
            if p.is_file() and (p.suffix.lower() in {'.jar', '.zip'} or p.name.startswith('freeplane.')):
                artifacts[p.relative_to(repo).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
assert artifacts, 'no built artifacts found for same-build capture provenance'
receipt = dict(implementation_head=head, delivery_base=manifest['base'], latest_run=latest.name,
    latest_run_manifest_sha256=hashlib.sha256((latest / 'manifest.json').read_bytes()).hexdigest(),
    source_sha256=hashes, built_artifact_sha256=artifacts, fixture_and_dependency_manifest='runs/' + latest.name + '/manifest.json',
    validation_log='validation.log', application_capture_status='NOT_MEASURED',
    delivery_gate='OPEN: three paired same-build real-application captures remain required outside the SDD lane')
(output / 'collection.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
```

- [ ] **Step 2: Create the external application-capture handoff.** Create `freeplane_plugin_graph/doc/initial-spacing/UI-CAPTURE-RECIPE.md` with the exact content below. The capture itself is a delivery gate owned outside the SDD lane; a completed model lane leaves that gate visibly open until the receipt arrives.
```markdown
# Initial spacing application capture receipt

Status: NOT_MEASURED. Delivery gate: OPEN.

Use the actual application built from the implementation revision and artifact set in collection.json. Record that revision (git rev-parse HEAD at build), every runnable launcher/bundle artifact path and SHA-256, JDK/runtime, OS, display server, font/look-and-feel, application version, fixture input/projection identity and generation. A pre-evidence-commit implementation OID is valid only while its recorded source hashes and built artifacts remain unchanged.

Collect three paired same-build application captures outside the SDD lane:

1. Fresh map/new workspace: an enclosure with multiple direct nodes and a visibly wider candidate initial ring.
2. Nested/multi-map graph: wider content footprints, sibling/top-level packing, and a relationship between actual endpoints.
3. At least two active pins: exact pinned coordinates before and after stepping/restart, with the finite corrected frame visible.

Use zoom exactly 1.0. For every case pair the first displayed published worker frame with the actual first idle=true frame from the same unchanged build/input sequence. Keep original PNGs and their SHA-256; pair names must identify the same build and fixture. Record canvas logical-pixel coordinates and rendered-disc radii for the closest relevant same-enclosure unpinned pair, its measured boundary-to-boundary gap, and the corresponding model-node keys/frame sample. Record observed label boxes and pin markers where they affect interpretation. Keep raw device-pixel coordinates, OS display scale and Graphics2D device transform separately; do not call a scaled device-pixel measurement a logical-pixel measurement.

Receipt JSON must contain:

{
  "status": "NOT_MEASURED",
  "implementation_head": null,
  "artifact_sha256": {},
  "jdk_runtime": null,
  "os_display_server": null,
  "font_look_and_feel": null,
  "zoom": 1.0,
  "display_scale": null,
  "device_transform": null,
  "cases": [],
  "gap_claim": "24 model units at an eligible raw/corrected initial ring; logical-pixel evidence pending",
  "gate": "OPEN"
}

For each received case append an object with case ID, fixture and input hashes, workspace/generation, active pin node keys and exact coordinates, frame sample/stage and idle flag, screenshot paths/hashes, selected node keys, logical-pixel centres/radii/measured surface gap, device coordinates/transform, notes on label boxes and worker repairs, and model-frame receipt link. Keep settled gaps descriptive and preserve every decrease/negative endpoint gap.

Only after all three paired captures match collection.json and all required fields and measurements are present may the external delivery reviewer replace status with MEASURED and gate with PASSED. Any source/build mismatch, missing pair, nonfinite measurement, missing transform or unknown frame provenance keeps the gate OPEN. No capture may be inferred from the headless acceptance log.
```

- [ ] **Step 3: Run the exact spec K commands, preserving one validation log.** Run from the repository root. Before running, start a plain shell transcript targeting `freeplane_plugin_graph/doc/initial-spacing/validation.log`; execute the following text verbatim within it and end the transcript afterward. A normal Unix collector is `script -q -e -c 'bash --noprofile --norc' freeplane_plugin_graph/doc/initial-spacing/validation.log`; enter the commands below into that shell, then enter `exit`. Do not pipe away the exit status or select only successful attempts. Any nonzero command fails the gate and its output stays in the log. The prescribed unfiltered graph test necessarily runs the JUnit helper classes and suite according to Gradle discovery; preserve all resulting acceptance directories.
```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould' --tests 'org.freeplane.plugin.graph.layout.BoundarySeparationShould' --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould'
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test -PTestLoggingFull
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:verifyGraphBundle
git diff --check
git diff -- freeplane_plugin_graph/src/main/java
git diff -- freeplane_plugin_graph/src/test/java
```

Run the exact archived-receipt replay from spec K in the same environment, retaining its printed temporary directory and commands/logs with the delivery evidence. This diagnostic replay complements the new tests and does not replace their explicit deadline and budget checks.

```bash
python3 - <<'PY'
import hashlib, json, os, pathlib, subprocess, tempfile, time
repo = pathlib.Path.cwd()
reports = pathlib.Path('/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261020-041612-a868d395/reports')
r5 = reports / 'frontier-review-5'
base = 'cb8782fb3742f378b2907198fd2343292415da95'
prov = json.loads((r5 / 'provenance.json').read_text())
cmds = {r['label']: r['command'] for r in json.loads((r5 / 'commands.json').read_text())}
root = pathlib.Path(tempfile.mkdtemp(prefix='initial-spacing-replay-'))
for d in ('src', 'empty', 'C0', 'candidate', 'probes'):
    (root / d).mkdir()
sources = []
for relative, expected in prov['source_sha256'].items():
    data = subprocess.check_output(['git', 'show', base + ':' + relative], cwd=repo)
    assert hashlib.sha256(data).hexdigest() == expected, relative
    p = root / 'src' / relative
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)
    sources.append(str(p))
java = '/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java'
javac = java + 'c'
recorded = cmds['compile-C0']
jars = recorded[recorded.index('-classpath') + 1]
assert all(pathlib.Path(p).is_file() for p in jars.split(':')), 'resolve pinned jars first'
env = dict(os.environ, JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
receipts = []
def run(label, argv, timeout=600):
    start = time.monotonic()
    with (root / (label + '.log')).open('w') as out:
        result = subprocess.run(argv, env=env, stdout=out, stderr=subprocess.STDOUT, timeout=timeout)
    receipts.append(dict(label=label, command=argv, returncode=result.returncode,
                         elapsed_s=time.monotonic()-start))
    (root / 'commands.json').write_text(json.dumps(receipts, indent=2) + '\n')
    assert result.returncode == 0, (label, root)
def compile_to(label, out, cp, files):
    run(label, [javac, '--release', '8', '-encoding', 'UTF-8', '-implicit:none',
        '-sourcepath', str(root/'empty'), '-classpath', cp, '-d', str(out)] + files)
compile_to('compile-C0', root/'C0', jars, sources)
engine = root/'src/freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java'
packing = engine.with_name('ClusterPacking.java')
# Edit only scratch copies, with unique old-text checks.
e = engine.read_text()
old = 'static final double NODE_RADIUS = 8.0;'
assert e.count(old) == 1
e = e.replace(old, old + '\n    static final double PREFERRED_NODE_GAP = 24.0;')
old = '(2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)'
assert e.count(old) == 1
e = e.replace(old, '(2.0 * maxRadius + PREFERRED_NODE_GAP)')
engine.write_text(e)
s = packing.read_text()
old = 'clearanceX < gap && clearanceY < gap'
assert s.count(old) == 1
packing.write_text(s.replace(old, 'clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON'))
compile_to('compile-candidate', root/'candidate', str(root/'C0')+':'+jars,
           [str(engine), str(packing)])
compile_to('compile-probes', root/'probes', str(root/'C0')+':'+jars,
           [str(r5/'SpacingProbe.java'), str(r5/'ReviewSeedProbe.java')])
for config in ('C0', 'candidate'):
    cp = ':'.join([str(root/config), str(root/'probes'), str(root/'C0'), jars])
    for fixture in ('reference', 'S1', 'cross', 'rest'):
        args = [java, '-cp', cp, 'org.freeplane.plugin.graph.layout.graphstream.ReviewSeedProbe', fixture]
        if fixture != 'rest':
            args.append('6000')
        run(config+'-'+fixture, args)
print(root)
PY
```

Expected: all five Gradle commands exit zero; the focused suite, existing force/proximity/footprint/packing/idle regressions, full graph-plugin tests, and `verifyGraphBundle` pass headlessly. No translation is edited, so the translation formatter is unnecessary. Any unrelated generated output must be reported and must not enter the implementation diff. Do not delete preexisting user changes. Bundle verification is not evidence of application capture.

- [ ] **Step 4: Collect and inspect the reviewable receipts.** Run after the exact commands have succeeded:

```sh
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH JAVA_TOOL_OPTIONS=-Djava.awt.headless=true python3 freeplane_plugin_graph/doc/initial-spacing/collect-receipt.py
```

Verify `collection.json` names the actual implementation HEAD/artifact hashes, the latest successful model run and all earlier attempts. Inspect `runs/*/acceptance.md` and the validation log, including C0 eligibility/qualification, all primary and supplemental samples, endpoint/rest tables, label-metric digests and unchanged production-diff proof. Keep `EPSILON_NECESSITY = NOT_ESTABLISHED`, and keep the application gate open. Do not label the feature delivered while the capture receipt remains incomplete. This automated plan can finish its implementation/evidence lane while explicitly handing off the unconditional delivery gate.

**Requirement ledger (all requirements placed):**

| Spec requirement | Task and executable evidence |
| --- | --- |
| A two production edits, protected constants/paths | 2 exact edits; 5 exact-overlay source guard; 6 diff/hash inventory |
| B nearest surface gap/eligibility | 1 independent metric tests; 5 per-node/classification/POINT reconstruction |
| C frozen C0-qualified fixtures, S1 only, labels/mixed prominence | 1 factories; 5 C0 controls, input/label/provenance receipts |
| D seed table and analytic formula | 2 raw consumer oracles; 5 frozen seed targets and median-gain checks, including n=2 |
| E first idle/post-100 and descriptive settled reporting | 3 scoped bound/pin; 5 serial fresh-JVM SAMPLE/FIRST_IDLE/FOLLOW/POST/metric reports |
| E.4 realized relationship endpoint gaps | 5 per-contributor endpoint rows, min/median aggregates and same/cross node-node coverage |
| F 6000/600 and exact 3455 re-pin | 3 exact constants/deadline helper; 5 isolated first-idle/follow timing receipts |
| G corrected worker/fixture D, pins, independent residual/hulls/budgets | 1 shared final-frame assertions; 3 independent pipeline and D oracle; 4 error/pin paths; 5 every sampled frame |
| H every consumer and six endpoint cases | 2 actual consumers/raw/configured state/rests, reversed inputs, root/orphan membership; 5 actual REST table |
| H.1 epsilon necessity | 2 ULP predicate check and explicit NOT_ESTABLISHED; 5 same explicit evidence result |
| I empty/singleton/two-node/mixed/large-disc/labels/pins/restart | 2 analytic/seed consumers; 4 lifecycle/finite/error cases; 5 two-node acceptance fixture |
| J finite rejection/errors/determinism/provenance | 1 metric guards; 2 permutations/repeats; 4 lifecycle/fallback; 5 source/code-source/hash/log failure guards |
| K exact commands and unconditional real-application capture gate | 6 full headless vector, evidence collection, external capture recipe/schema with NOT_MEASURED gate open |
| L traceability and M non-promises/delivery boundary | All tasks use the normative spec, preserve protected production sources, and report acceptance without adding non-promised settled/UI claims |

**No requirement is unplaced.** Actual real-application capture collection remains explicitly outside the SDD lane and must be completed against the recorded build before delivery approval. The lane must not fabricate that receipt or claim that headless success closes it.

- [ ] **Commit: Inspect and record this task.** Run `git diff --check`, inspect `git diff` and `git diff --cached`, then stage only the listed task files.

```sh
git add freeplane_plugin_graph/doc/initial-spacing/
git diff --cached --check
git diff --cached
git commit -m "graph-initial-spacing: Record spacing gates and application capture handoff [graph-initial-spacing]"
```
