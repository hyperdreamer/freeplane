# Graph Workspace Edge-Crossing Avoidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove visible edge crossings in the Graph Workspace by adding a verified
node-relocation correction stage (`layout/EdgeConflictCorrection`, applied by
`LayoutWorker.accept` after `BoundarySeparationCorrection`) and a bounded publish-time
routing pass (`geometry/EdgeRouter`, invoked in `LayoutSettleLoop` between
`geometryEngine.computeHulls(...)` and `CanvasState.of(...)`), so that a crossing is
removed whenever the pinned candidate search finds an accepted relocation and an
accepted detour provably clears every obstacle, with every residual conflict counted and
published rather than hidden.

**Architecture:** Two new components share one extracted invariant/encoding foundation.
`projection/CanonicalKeys` becomes the single public canonical-encoding path (replacing
`layout/CanonicalLayoutKeys`); `layout/BoundaryInvariants` becomes the shared,
document/side-effect-free detection and terminal-classification home used by the boundary
stage and the edge stage. The relocation stage starts from `BoundarySeparationResult`
positions and its terminal `GraphGeometry` (new `geometry()` accessor), detects C1
edge-edge and C2 edge-over-node conflicts, indexes them in a subdividing uniform grid,
generates a fixed candidate ladder (adjacent swaps, mirrors, C2 translations), verifies
whole batches with A1 pair-set monotonicity, A2 disc separation, A3 hull-invariant subset
and A4 membership, retries rejected batches as singles, and rebuilds the boundary record
on the published positions. The router runs at publish over the final positions and hull
geometry, processing edges in canonical order against published paths, not-yet-processed
straight chords and unrelated discs, accepting the first straight/bow/dogleg rung whose
sampled path clears the pinned clearances and the `ROUTE_LENGTH_CAP`, else falling back
to straight and reporting the drawn-path residual. `CanvasState` carries the immutable
`EdgePaths`; `GraphPainter` and `GraphHitIndex` consume that one path list, so one active
edge-geometry path exists.

**Lane plan (file ownership; no file appears in two lanes):**

- `edge-foundation` (Tasks 1, 2, 3): `projection/CanonicalKeys.java`,
  `layout/CanonicalLayoutKeys.java` (deleted), `layout/BoundaryInvariants.java`,
  `layout/BoundaryConflict.java`, `layout/BoundarySeparationCorrection.java`,
  `layout/BoundarySeparationDiagnostics.java`, `layout/BoundarySeparationResult.java`,
  `layout/NodeSeparationProjection.java`, and the layout/projection test files named in
  those tasks. This lane produces every shared interface the other lanes consume.
- `edge-relocation` (Tasks 4, 10, 13, 14, 15): `layout/EdgeConflictCorrection.java`,
  `layout/EdgeConflictResult.java`, `layout/EdgeConflictDiagnostics.java`,
  `layout/EdgeConflictTimings.java`, `layout/EdgeConflictException.java`,
  `layout/EdgeConflictCorrectionShould.java`.
- `edge-routing` (Tasks 5, 6, 11, 16): `geometry/EdgePath.java`,
  `geometry/EdgePaths.java`, `geometry/EdgeRouter.java`,
  `geometry/EdgeRouteDiagnostics.java`, `geometry/EdgeRouteTimings.java`,
  `geometry/EdgePathShould.java`, `geometry/EdgeRouterShould.java`.
- `edge-publication` (Tasks 7, 8, 9, 12, 17, 18, 19): `layout/LayoutFrame.java`,
  `layout/LayoutWorker.java`, `control/CanvasState.java`, `control/LayoutSettleLoop.java`,
  `control/GraphUpdateCoordinator.java`, `canvas/GraphPainter.java`,
  `canvas/GraphHitIndex.java`, `performance/PerformanceMeasurements.java`,
  `performance/PerformanceTripwiresShould.java`,
  `performance/GraphWorkspacePerformanceDiagnostic.java`, and every remaining
  `CanvasState.of`/`withDiagnostics`/painter/hit-test fixture file named in Tasks 7-9 and
  17-19. The two performance tasks and the final acceptance task live in this lane, so no
  separate performance lane owns a file.
- `edge-foundation` also owns Task 20, the late composition assertion in
  `layout/BoundarySeparationShould.java` (the file is already owned by Task 1 of that
  lane); Task 20 depends on Tasks 8 and 15 and therefore appears after them.
- `edge-bounded` (Task 21, Revision 7): the bounded-work budgets and counted accessors in
  `layout/EdgeConflictCorrection.java`, `layout/EdgeConflictDiagnostics.java`,
  `geometry/EdgeRouter.java`, `geometry/EdgeRouteDiagnostics.java` and the
  `layout/EdgeConflictCorrectionShould.java` / `geometry/EdgeRouterShould.java` /
  `geometry/EdgeRouterFixture.java` fixtures. These files were written by the
  relocation/routing lanes in earlier tasks; Task 21 depends on Tasks 13-16, so the
  bounded lane runs after those lanes have finished and no two lanes write the same file
  concurrently.

Cross-lane dependencies are declared by each task's `Depends on` line and are the
scheduling authority: task numbers are authoring order, not dependency order. Tasks 4-6
depend on Tasks 1-3; Tasks 7-9 depend on Tasks 4-6; red-phase Tasks 10-12 add the failing
fixtures; implementation Tasks 13-16 turn them green; Task 17 completes the painter and
hit-test coverage; Task 21 bounds the relocation and router work (Revision 7); Task 18
adds the performance stages and depends on Task 21; Task 19 runs the module and core
gates and depends on Tasks 18 and 21; Task 20 adds the late foundation-lane composition
assertion. A task inside a lane always runs after every lower-numbered task in that lane.

**Tech Stack:** Java 8 source level on runtime Zulu 21.0.8-zulu, Gradle multi-project
build (`gradle :freeplane_plugin_graph:test`), JUnit 4.13.2 with AssertJ and Mockito.
All production types live in `org.freeplane.plugin.graph.{layout,geometry,canvas,control,
projection}` inside `freeplane_plugin_graph`.

**Implementation contract:** `docs/superpowers/specs/2026-10-12-graph-edge-crossing-avoidance-spec.md`
(the contract implemented here; every value a task needs is restated in the task, so no
task has to open it), derived from the approved design
`docs/superpowers/specs/2026-10-12-graph-edge-crossing-avoidance-design.md` (revision 7).

## Global Constraints

- Java 8 source/target on Zulu 21.0.8-zulu at `~/.sdkman/candidates/java/21.0.8-zulu`;
  before every Gradle command run `export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"`
  and `export PATH="$JAVA_HOME/bin:$PATH"`.
- Use `gradle` (never `gradlew`, never Maven) from the worktree root. The module test
  command is `gradle :freeplane_plugin_graph:test`; add `-PTestLoggingFull` whenever a run
  must show verbose failures.
- Plugin-only change: no file under `freeplane/` or `freeplane_api/` may be modified.
  Every touched path is under `freeplane_plugin_graph/` or `docs/superpowers/`.
- No new dependencies; no settings, build-script, OSGi export/import, XML-schema,
  workspace-format, or persistence change.
- Sources are UTF-8; indentation is 4 spaces. No translation resource is touched, so
  `gradle format_translation` does not apply.
- Every commit message ends with the commit identifier
  `[2026-10-12-graph-edge-crossing-avoidance]`.
- One active edge-geometry path per mechanism: no feature flag, no legacy straight-render
  branch, no parallel old/new implementation. The three-argument
  `LayoutFrame.withDiagnostics` and the five-argument `CanvasState.of` are removed in the
  same change that updates their call sites; no overload is retained.
- Determinism: no iteration over `HashMap` order participates in any decision. Every
  enumeration that can decide an outcome is ordered by the canonical keys of
  `projection/CanonicalKeys` (`node`, `edge`, `hull`, pairs) or by a pinned numeric
  tie-break. Identical `(projection, positions, metrics, pins, visibleEndpoints,
  geometry)` produce identical positions, paths and diagnostics across runs and across
  input-order permutations.
- All new production types are Java 8 `final` classes in the package named by their task,
  with `private` constructors and static factories unless the task states otherwise.
  Public value types validate constructor inputs like `BoundarySeparationTimings`
  (negative counts and non-finite numbers raise `IllegalArgumentException`;
  reference parameters use `Objects.requireNonNull`) and publish immutable
  `Collections.unmodifiableList`/`unmodifiableMap` values.
- No `System.nanoTime()` value enters any diagnostics value; timings live only in
  `EdgeConflictTimings` and `EdgeRouteTimings`.
- Design-review mandates N1-N8 are normative: N1 C2 identity is
  `CanonicalKeys.edgeNodePair`; N2 `BoundaryInvariants.describe` takes
  `baselineResidualKeys` (the boundary stage's terminal `residualHullPairs()`, exact
  `CanonicalKeys.hullPair` format); N3 `EdgeConflictResult.boundaryDiagnostics()` carries
  the rebuilt record; N4 the only exhaustion-flag names are
  `verificationBudgetExhausted`, `indexBudgetExhausted`, `detectionTruncated` and
  `routeBudgetExhausted` (Revision 7); N5 every `CanvasState` construction site uses the
  `EdgePaths`-taking factory; N6 every three-argument `withDiagnostics` call site uses
  the four-argument form; N7 the negative index/crossing fixtures exist; N8 enclosure
  anchors stay at pre-batch values.
- Revision 7 (bounded-effort stages, user-approved) is normative for Tasks 18, 19 and
  21: per-frame work budgets with the pinned counted units and check points, the four
  exhaustion flags and the both-flags rule, and steady-state measurement on the settled
  published worker frame with zero budget-exhaustion events required for A7.

## Task 1: Extract projection/CanonicalKeys as the one canonical encoding path

**Implementer tier:** Standard
**Lane:** edge-foundation
**Depends on:** none

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/CanonicalKeys.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/CanonicalKeysShould.java`
- Delete: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeys.java`
- Delete: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeysShould.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryConflict.java:39-40,88`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java:203,226,252,273,294,313,321,527,533,575,631,640,931,1101`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnostics.java:139`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertions.java:53,69`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryConflictShould.java:36`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java:650-652`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java:262,562-566,674-723,751,753,772,774,860-889,945-947,1072-1076,1108-1110`

**Interfaces:**

- Consumes: `SourceNodeKey.persistent()`, `.persistedReference().get().nodeId().value()`,
  `.structuralPath()`, `.mapReferenceId()`; `ProjectedNodeKey.source()`;
  `EnclosureHullKey.endpointKeys()`; `EnclosureKey.source()`;
  `ProjectedEndpointKey.isNode()`, `.node()`, `.enclosure()`;
  `ProjectedEdgeKey.first()`, `.second()`; `MapReferenceId.of(String)`.
- Produces: `public final class CanonicalKeys` in
  `org.freeplane.plugin.graph.projection` with exactly:
  `public static String escape(String)`;
  `public static String endpoint(SourceNodeKey)`;
  `public static String endpoint(ProjectedEndpointKey)`;
  `public static String hull(EnclosureHullKey)`;
  `public static String nodeField(ProjectedNodeKey)`;
  `public static String anchorField(EnclosureHullKey)`;
  `public static String pair(EnclosureHullKey, EnclosureHullKey)`;
  `public static MapReferenceId mapOfField(String)`;
  `public static String node(ProjectedNodeKey)` (== `nodeField`);
  `public static String nodePair(ProjectedNodeKey, ProjectedNodeKey)` (sorted `node|node`);
  `public static String edge(ProjectedEdgeKey)` (`endpoint(first)+"~"+endpoint(second)`);
  `public static String edgePair(ProjectedEdgeKey, ProjectedEdgeKey)` (sorted `edge|edge`, the C1 identity);
  `public static String edgeNodePair(ProjectedEdgeKey, ProjectedNodeKey)` (`edge(edge)+"|"+node(node)`, the N1 C2 identity);
  `public static String hullPair(EnclosureHullKey, EnclosureHullKey)` (== `pair`).

- [ ] **Step 1: Write the failing test**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/CanonicalKeysShould.java`
with exactly this content (it moves the six `CanonicalLayoutKeysShould` expectations to
the new class and adds the new-key expectations):

```java
package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class CanonicalKeysShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final MapReferenceId OTHER_MAP = MapReferenceId.of("76650fda-9b84-4f8b-858f-27b18d08c535");

    @Test
    public void escapeReservedCharactersInFixedOrder() {
        assertThat(CanonicalKeys.escape("a%b|c:d,e")).isEqualTo("a%25b%7Cc%3Ad%2Ce");
        assertThat(CanonicalKeys.escape("plain")).isEqualTo("plain");
    }

    @Test
    public void encodePersistedAndTransientEndpointsDistinctly() {
        SourceNodeKey persisted = source(MAP, "ID_1");
        SourceNodeKey transientKey = SourceNodeKey.transientPath(MAP,
            Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));

        assertThat(CanonicalKeys.endpoint(persisted)).isEqualTo("m:" + MAP.value() + "|p:ID_1");
        assertThat(CanonicalKeys.endpoint(transientKey)).isEqualTo("m:" + MAP.value() + "|t:1.2");
        assertThat(CanonicalKeys.endpoint(SourceNodeKey.transientPath(MAP,
            Collections.<Integer>emptyList()))).isEqualTo("m:" + MAP.value() + "|t:");
        assertThat(CanonicalKeys.endpoint(persisted))
            .isNotEqualTo(CanonicalKeys.endpoint(source(MAP, "p:ID_1")));
        assertThat(CanonicalKeys.endpoint(source(MAP, "x|t:1.2")))
            .isNotEqualTo(CanonicalKeys.endpoint(transientKey));
    }

    @Test
    public void buildHullKeysIndependentOfEndpointOrder() {
        EnclosureHullKey forward = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "a")), EnclosureKey.of(source(MAP, "b"))));
        EnclosureHullKey reversed = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "b")), EnclosureKey.of(source(MAP, "a"))));

        assertThat(CanonicalKeys.hull(forward)).isEqualTo(CanonicalKeys.hull(reversed));
        assertThat(CanonicalKeys.hull(forward)).isEqualTo(
            "m:" + MAP.value() + "|p:a,m:" + MAP.value() + "|p:b");
    }

    @Test
    public void orderPairsCanonicallyAndSymmetrically() {
        EnclosureHullKey first = hull("a");
        EnclosureHullKey second = hull("b");

        assertThat(CanonicalKeys.pair(first, second)).isEqualTo(CanonicalKeys.pair(second, first));
        assertThat(CanonicalKeys.pair(first, second))
            .isEqualTo(CanonicalKeys.hull(first) + "|" + CanonicalKeys.hull(second));
        assertThat(CanonicalKeys.hullPair(first, second)).isEqualTo(CanonicalKeys.pair(first, second));
    }

    @Test
    public void prefixNodeAndAnchorFields() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalKeys.nodeField(node)).isEqualTo("n:m:" + MAP.value() + "|p:node");
        assertThat(CanonicalKeys.node(node)).isEqualTo(CanonicalKeys.nodeField(node));
        assertThat(CanonicalKeys.anchorField(hull("hull"))).isEqualTo("a:m:" + MAP.value() + "|p:hull");
    }

    @Test
    public void recoverTheMapFromADisplacementField() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalKeys.mapOfField(CanonicalKeys.nodeField(node))).isEqualTo(MAP);
        assertThat(CanonicalKeys.mapOfField(CanonicalKeys.anchorField(hull("hull")))).isEqualTo(MAP);
        assertThat(CanonicalKeys.mapOfField(CanonicalKeys.nodeField(
            ProjectedNodeKey.of(source(OTHER_MAP, "node"))))).isEqualTo(OTHER_MAP);
        assertThatThrownBy(() -> CanonicalKeys.mapOfField("x:y"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalKeys.mapOfField("n:garbage"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void encodeEndpointOfEnclosureWithTheEnclosurePrefix() {
        EnclosureKey enclosure = EnclosureKey.of(source(MAP, "e"));

        assertThat(CanonicalKeys.endpoint(ProjectedEndpointKey.ofEnclosure(enclosure)))
            .isEqualTo("e:m:" + MAP.value() + "|p:e");
    }

    @Test
    public void orderNodePairsCanonicallyAndSymmetrically() {
        ProjectedNodeKey first = ProjectedNodeKey.of(source(MAP, "a"));
        ProjectedNodeKey second = ProjectedNodeKey.of(source(MAP, "b"));

        assertThat(CanonicalKeys.nodePair(first, second)).isEqualTo(CanonicalKeys.nodePair(second, first));
        assertThat(CanonicalKeys.nodePair(first, second))
            .isEqualTo(CanonicalKeys.node(first) + "|" + CanonicalKeys.node(second));
    }

    @Test
    public void encodeEdgesAndEdgePairsSymmetrically() {
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b"))));
        ProjectedEdgeKey other = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "c"))));

        assertThat(CanonicalKeys.edge(edge)).isEqualTo(CanonicalKeys.node(
            ProjectedNodeKey.of(source(MAP, "a"))) + "~" + CanonicalKeys.node(
                ProjectedNodeKey.of(source(MAP, "b"))));
        assertThat(CanonicalKeys.edgePair(edge, other)).isEqualTo(CanonicalKeys.edgePair(other, edge));
    }

    @Test
    public void distinguishTwoPassedOverNodesOnOneEdge() {
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b"))));
        ProjectedNodeKey first = ProjectedNodeKey.of(source(MAP, "n1"));
        ProjectedNodeKey second = ProjectedNodeKey.of(source(MAP, "n2"));

        assertThat(CanonicalKeys.edgeNodePair(edge, first))
            .isEqualTo(CanonicalKeys.edge(edge) + "|" + CanonicalKeys.node(first));
        assertThat(CanonicalKeys.edgeNodePair(edge, first))
            .isNotEqualTo(CanonicalKeys.edgeNodePair(edge, second));
    }

    @Test
    public void keepTildeContainingIdsInjectiveWithoutEscapingTilde() {
        assertThat(CanonicalKeys.endpoint(source(MAP, "a~b"))).isEqualTo("m:" + MAP.value() + "|p:a~b");
        ProjectedEdgeKey first = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a~b"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "c"))));
        ProjectedEdgeKey second = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b~c"))));

        assertThat(CanonicalKeys.edge(first)).isNotEqualTo(CanonicalKeys.edge(second));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(MAP, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.projection.CanonicalKeysShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava`, `cannot find symbol: class CanonicalKeys`.

- [ ] **Step 3: Write the minimal implementation**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/CanonicalKeys.java`
with exactly this content (the encodings move unchanged in literal format from
`CanonicalLayoutKeys`; the new pair/edge encodings follow the spec's injectivity rules:
`escape` replaces `%`, `|`, `:` and `,` as `%25`, `%7C`, `%3A`, `%2C`; `~` is not escaped;
every pair is emitted in ascending `String.compareTo` order of its two encoded elements):

```java
package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class CanonicalKeys {
    private CanonicalKeys() {
    }

    public static String escape(final String value) {
        Objects.requireNonNull(value, "value");
        return value.replace("%", "%25").replace("|", "%7C").replace(":", "%3A").replace(",", "%2C");
    }

    public static String endpoint(final SourceNodeKey source) {
        Objects.requireNonNull(source, "source");
        final StringBuilder builder = new StringBuilder();
        builder.append("m:").append(source.mapReferenceId().value()).append('|');
        if (source.persistent()) {
            builder.append("p:").append(escape(source.persistedReference().get().nodeId().value()));
        }
        else {
            builder.append("t:");
            final List<Integer> path = source.structuralPath();
            for (int index = 0; index < path.size(); index++) {
                if (index > 0) {
                    builder.append('.');
                }
                builder.append(path.get(index).intValue());
            }
        }
        return builder.toString();
    }

    public static String endpoint(final ProjectedEndpointKey endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.isNode()) {
            return "n:" + endpoint(endpoint.node().get().source());
        }
        return "e:" + endpoint(endpoint.enclosure().get().source());
    }

    public static String hull(final EnclosureHullKey hull) {
        Objects.requireNonNull(hull, "hull");
        final List<String> endpoints = new ArrayList<String>();
        for (final EnclosureKey endpoint : hull.endpointKeys()) {
            endpoints.add(endpoint(endpoint.source()));
        }
        Collections.sort(endpoints);
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < endpoints.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(endpoints.get(index));
        }
        return builder.toString();
    }

    public static String nodeField(final ProjectedNodeKey node) {
        Objects.requireNonNull(node, "node");
        return "n:" + endpoint(node.source());
    }

    public static String anchorField(final EnclosureHullKey hull) {
        return "a:" + hull(hull);
    }

    public static String pair(final EnclosureHullKey first, final EnclosureHullKey second) {
        return sortedJoin(hull(first), hull(second));
    }

    public static String node(final ProjectedNodeKey node) {
        return nodeField(node);
    }

    public static String nodePair(final ProjectedNodeKey first, final ProjectedNodeKey second) {
        return sortedJoin(node(first), node(second));
    }

    public static String hullPair(final EnclosureHullKey first, final EnclosureHullKey second) {
        return pair(first, second);
    }

    public static String edge(final ProjectedEdgeKey edge) {
        Objects.requireNonNull(edge, "edge");
        return endpoint(edge.first()) + "~" + endpoint(edge.second());
    }

    public static String edgePair(final ProjectedEdgeKey first, final ProjectedEdgeKey second) {
        return sortedJoin(edge(first), edge(second));
    }

    public static String edgeNodePair(final ProjectedEdgeKey edge, final ProjectedNodeKey node) {
        return edge(edge) + "|" + node(node);
    }

    public static MapReferenceId mapOfField(final String field) {
        Objects.requireNonNull(field, "field");
        if (field.length() < 5
                || (field.charAt(0) != 'n' && field.charAt(0) != 'a') || field.charAt(1) != ':') {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        final String endpoint = field.substring(2);
        if (!endpoint.startsWith("m:")) {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        final int separator = endpoint.indexOf('|');
        if (separator < 0) {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        return MapReferenceId.of(endpoint.substring(2, separator));
    }

    private static String sortedJoin(final String first, final String second) {
        if (first.compareTo(second) <= 0) {
            return first + "|" + second;
        }
        return second + "|" + first;
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.projection.CanonicalKeysShould" \
  -PTestLoggingFull
```

Expected: PASS, 11 tests.

- [ ] **Step 5: Delete the old encoding and repoint every caller (one active encoding path)**

Delete `layout/CanonicalLayoutKeys.java` and `layout/CanonicalLayoutKeysShould.java`.
In every file listed under **Files**, replace each `CanonicalLayoutKeys.` reference with
`CanonicalKeys.`; in the layout package add
`import org.freeplane.plugin.graph.projection.CanonicalKeys;` and remove any stale import.
No other edit: `CanonicalLayoutKeys` had no method that `CanonicalKeys` lacks, and the
literal encodings are identical, so every existing expectation is unchanged. Verify no
reference survives:

```bash
cd "$(git rev-parse --show-toplevel)"
grep -rn "CanonicalLayoutKeys" freeplane_plugin_graph/src || echo "single encoding path"
```

Expected: `single encoding path`.

- [ ] **Step 6: Run the affected suites and confirm they pass**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.projection.CanonicalKeysShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryConflictShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the moved expectations and every repointed caller stay green.

- [ ] **Step 7: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/CanonicalKeys.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/CanonicalKeysShould.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryConflict.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnostics.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertions.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryConflictShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git rm freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeys.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeysShould.java
git commit -m "refactor(graph): extract projection/CanonicalKeys as the one canonical encoding path [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 2: Extract layout/BoundaryInvariants with the preserved memo and counters

**Implementer tier:** Advanced
**Lane:** edge-foundation
**Depends on:** Task 1

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryInvariants.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java:29-177,212-888,916-1195`

**Interfaces:**

- Consumes: `CanonicalKeys.hull`, `.pair`, `.nodeField`, `.anchorField`, `.mapOfField`
  from Task 1; `BoundaryConflict` (constructor and `Kind`, `Reason`),
  `BoundarySeparationDiagnostics`, `BoundarySeparationException`,
  `BoundarySeparationTimings`; `GraphProjection.nodes()/enclosures()/pins()`,
  `ProjectedEnclosure.parentHull()/directNodes()/directEnclosures()/boundaryTier()`,
  `HullGeometry.exactPolygon()/contains()/minX()/minY()/maxX()/maxY()`,
  `HullIntersection.siblingOverlap/minimumSeparatingTranslation`,
  `GraphGeometryEngine` (consumed, not modified), `NodePromotion` scale rule
  (`NODE_RADIUS = 8.0`, `prominence.scale()`), `LayoutPositions.nodes()/anchors()`,
  `PinProjection.active()/projectedNode()/x()/y()`, `BoundaryTier.SUPPRESSED`.
- Produces: `public final class BoundaryInvariants` in
  `org.freeplane.plugin.graph.layout` with exactly:
  `public BoundaryInvariants()`;
  `public static final class Violation` with
  `public EnclosureHullKey first()`, `public EnclosureHullKey second()`,
  `public BoundaryConflict.Kind kind()`, `public double penetration()`,
  `public LayoutPoint translation()` (null for `ANCESTOR_ESCAPE`),
  `public String pairKey()` (`CanonicalKeys.hullPair(first, second)`);
  `public List<Violation> detect(GraphProjection, GraphGeometry)`;
  `public static Map<EnclosureHullKey, EnclosureHullKey> parents(GraphProjection)`;
  `public static Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull(GraphProjection)`;
  `public static Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey(GraphProjection)`;
  `public static Set<EnclosureHullKey> changedHulls(Map<ProjectedNodeKey, List<EnclosureHullKey>>,
  LayoutPositions, LayoutPositions)`;
  `public List<BoundaryConflict> terminalConflicts(GraphProjection, LayoutPositions,
  GeometryTextMetrics, List<PinProjection>, GraphGeometry, List<Violation>, boolean)`;
  package-private `PlanOutcome plan(GraphProjection, Map<EnclosureHullKey, ProjectedEnclosure>,
  Map<EnclosureHullKey, HullGeometry>, LayoutPositions, GeometryTextMetrics,
  List<PinProjection>, Set<ProjectedNodeKey>, List<Violation>)`;
  package-private `static List<String> ancestorEscapePairKeys(GraphProjection,
  Map<EnclosureHullKey, HullGeometry>)`;
  package-private `static LayoutPoint guardedMinimumSeparatingTranslation(HullGeometry, HullGeometry)`;
  package-private `long ancestorExactContainmentChecks()`, `long ancestorContainmentMemoHits()`,
  `long capTraversals()`, `long capCacheHits()`.

- [ ] **Step 1: Move the detection and planning members into the new class**

Create `BoundaryInvariants.java` in `org.freeplane.plugin.graph.layout`. Copy the
following members **verbatim** out of `BoundarySeparationCorrection` (same bodies, same
Java 8 style), replacing every `CanonicalLayoutKeys.` with `CanonicalKeys.`; the line
ranges are at HEAD `7c4ef8a2c0` and no body changes:

### [truncated]<｜end▁of▁thinking｜>

<｜｜DSML｜｜ calls>
<｜｜DSML｜｜ invoke name="bash">
<｜｜DSML｜｜ parameter name="command" string="true">cd "/data/home/henry-arch/.local/state/pi/project-manager/worktrees/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261012-021203-0d02a300/integration" && cat >> docs/superpowers/plans/2026-10-12-graph-edge-crossing-avoidance.md <<'PLAN_EOF'
- `Violation` (`916-934`), with the added `public String pairKey()` accessor returning
  `CanonicalKeys.hullPair(first, second)` and public accessors `first()`, `second()`,
  `kind()`, `penetration()`, `translation()`; keep the package-private fields accessed by
  the moved code.
- `PlanOutcome` (`935-949`), made a package-private static nested class of
  `BoundaryInvariants` with its fields unchanged.
- `Traversal` (`950-955`), `PinDepth` (`956-965`), `Candidate` (`966-985`).
- `AncestorContainmentMemo` (`986-1004`), `ContainmentCache` (`1005-1018`),
  `HullPair` (`1019-1045`).
- `FrameCapCache` (`1046-1095`) and its `collectCap` (`1096-1128`), `SupportKey`
  (`1129-1160`), `CapKey` (`1161-1195`).
- `detect` (`212-338`): this becomes
  `public List<Violation> detect(final GraphProjection projection, final GraphGeometry geometry)`,
  which calls `enclosuresByHull(projection)` and `parents(projection)` and then the moved
  sweep with `geometry.hulls()`. It uses `this.ancestorContainment`; it never resets the
  memo.
- `plan` (`339-366`), `planCrossMap` (`367-391`), `rigidMaps` (`392-401`),
  `addMapDelta` (`402-407`), `planSameMap` (`408-429`), `addMoves` (`430-441`),
  `selectCandidate` (`442-487`), `valid` (`488-501`), `complementaryDepth` (`502-513`),
  `inBand` (`514-519`), `support` (`520-554`), `nodeContribution` (`555-563`),
  `nodeRadius` (`564-570`), `polySupport` (`571-583`), `labelSize` (`584-603`),
  `ZERO_SIZE` (`604-620`). `plan` becomes package-private and returns `PlanOutcome`
  (the class is now a nested type of `BoundaryInvariants`).
- `terminalConflicts` (`709-730`): replace the old five-parameter form with exactly
  `public List<BoundaryConflict> terminalConflicts(final GraphProjection projection,
  final LayoutPositions positions, final GeometryTextMetrics metrics,
  final List<PinProjection> pins, final GraphGeometry geometry,
  final List<Violation> violations, final boolean boundExhausted)`. Its body builds
  `enclosuresByHull(projection)` and one `FrameCapCache(projection, enclosuresByHull,
  geometry.hulls(), positions, metrics, pinnedNodes(pins))`, runs the moved `plan`
  classification-only against `geometry.hulls()` (nothing is applied and
  `PlanOutcome.isEmpty()` is irrelevant), then maps each violation with the same
  three-branch reason mapping as today:
  `plan.conflicts.get(violation.pairKey)` if present; else
  `boundExhausted && plan.movedPairs.contains(pairKey)` → `ROUND_LIMIT`; else
  `ANCESTOR_ESCAPE` → `STRUCTURAL_ESCAPE`, otherwise `IMMOVABLE_SIDES`. It adds the
  cache's traversal counts to `this.capTraversals`/`this.capCacheHits` exactly as the
  old `plan` did. `boundExhausted = false` never produces `ROUND_LIMIT`.
- `blockingPins` (`731-760`), `enclosuresByHull` (`761-769`), `parents` (`770-780`),
  `nodeHulls` → rename to `public static Map<ProjectedNodeKey, List<EnclosureHullKey>>
  nodeHullsByKey` (`781-796`), `changedHulls` (`797-818`), `pinnedNodes` (`819-828`),
  `related` (`829-843`), `containsInclusive` (`844-852`), `subtreeNodes` (`853-859`),
  `collectSubtreeNodes` (`860-871`), `conflict` (`872-876`), `isZero` (`877-880`),
  `pairKeys` (`881-888`), and the vector helpers used only by the moved code:
  `add` (`697-700`), `negate` (`701-704`), `scale` (`705-708`).
- `guardedMinimumSeparatingTranslation` (`176-184`) and `ancestorEscapePairKeys`
  (`185-211`) move as package-private **static** methods, so the package-private static
  wrappers left on `BoundarySeparationCorrection` can delegate to them.

The class body starts with the instance state that the extraction must preserve:

```java
public final class BoundaryInvariants {
    private final AncestorContainmentMemo ancestorContainment = new AncestorContainmentMemo();
    private long capTraversals;
    private long capCacheHits;

    public BoundaryInvariants() {
    }

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

    // moved members follow in their current order
}
```

- [ ] **Step 2: Make BoundarySeparationCorrection delegate**

In `BoundarySeparationCorrection.java` remove every moved member and add exactly:

```java
    private final BoundaryInvariants boundaryInvariants = new BoundaryInvariants();
```

Replace the four package-private accessor bodies with delegation
(`ancestorExactContainmentChecks()`, `ancestorContainmentMemoHits()`, `capTraversals()`,
`capCacheHits()` return the same-named `boundaryInvariants` call), remove the
`ancestorContainment`, `capTraversals` and `capCacheHits` fields, and keep the two
package-private static wrappers unchanged in signature:

```java
    static LayoutPoint guardedMinimumSeparatingTranslation(final HullGeometry first, final HullGeometry second) {
        return BoundaryInvariants.guardedMinimumSeparatingTranslation(first, second);
    }

    static List<String> ancestorEscapePairKeys(final GraphProjection projection,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        return BoundaryInvariants.ancestorEscapePairKeys(projection, hulls);
    }
```

In `separate`, replace
`final List<Violation> violations = detect(projection, enclosuresByHull, parents, hulls);`
with `final List<Violation> violations = boundaryInvariants.detect(projection, geometry);`
(and delete the now-unused local `enclosuresByHull`/`parents` from `separate`), replace
`plan(projection, enclosuresByHull, hulls, current, metrics, pins, pinnedNodes,
violations)` with `boundaryInvariants.plan(projection, BoundaryInvariants
.enclosuresByHull(projection), geometry.hulls(), current, metrics, pins, pinnedNodes,
violations)`, and replace the terminal block

```java
        if (!terminalViolations.isEmpty()) {
            final PlanOutcome terminalPlan = plan(projection, enclosuresByHull, terminalHulls, current, metrics,
                pins, pinnedNodes, terminalViolations);
            final boolean boundExhausted = rounds >= maxDisplacementRounds;
            conflicts = terminalConflicts(terminalViolations, terminalPlan, boundExhausted, projection,
                enclosuresByHull, pins);
        }
```

with

```java
        if (!terminalViolations.isEmpty()) {
            final GraphGeometry terminalGeometry =
                GraphGeometry.of(current.nodes(), terminalHulls);
            conflicts = boundaryInvariants.terminalConflicts(projection, current, metrics, pins,
                terminalGeometry, terminalViolations, rounds >= maxDisplacementRounds);
        }
```

`applyRound`, `anchorDeltas`, `sortedNonZero`, `displacementMetrics`, `addVector`,
`addField`, `sum`, `translated` and the `apply` body stay in
`BoundarySeparationCorrection`. The class still compiles with `maxDisplacementRounds`,
the five pinned constants, and the no-argument and package-private constructors
unchanged.

- [ ] **Step 3: Write the characterization test**

Create `BoundaryInvariantsShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class BoundaryInvariantsShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(
        new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

    @Test
    public void detectMatchesTheBoundaryStageResidualPairSet() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey b = nodeKey("b");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a, b), Arrays.asList(
            enclosure(aHull, Collections.singletonList(a), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(b), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(a, 0.0, 0.0, b, 38.0, 0.0),
            anchorMap(aHull, 0.0, 0.0, bHull, 38.0, 0.0));
        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection,
            positions, METRICS, Collections.<PinProjection>emptyList());
        final BoundaryInvariants invariants = new BoundaryInvariants();

        final List<BoundaryInvariants.Violation> detected = invariants.detect(projection,
            new GraphGeometryEngine().computeHulls(projection, result.positions(), METRICS));

        final List<String> detectedKeys = new ArrayList<String>();
        for (final BoundaryInvariants.Violation violation : detected) {
            detectedKeys.add(violation.pairKey());
        }
        assertThat(detectedKeys).isEqualTo(result.diagnostics().residualHullPairs());
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
    }

    @Test
    public void ancestorMemoAndCountersPersistAcrossDetectCalls() {
        final ProjectedNodeKey childNode = nodeKey("child");
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey childHull = hull("child");
        final GraphProjection projection = projection(Collections.singletonList(childNode),
            Arrays.asList(parent(rootHull, "root", Collections.singletonList(childHull)),
                enclosure(childHull, Collections.singletonList(childNode), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(childNode, 0.0, 0.0),
            anchorMap(rootHull, 0.0, 0.0, childHull, 0.0, 0.0));
        final BoundaryInvariants invariants = new BoundaryInvariants();
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        invariants.detect(projection, geometry);
        final long checksAfterFirst = invariants.ancestorExactContainmentChecks();
        invariants.detect(projection, geometry);

        assertThat(checksAfterFirst).isGreaterThan(0);
        assertThat(invariants.ancestorExactContainmentChecks()).isEqualTo(checksAfterFirst);
        assertThat(invariants.ancestorContainmentMemoHits()).isGreaterThan(0);
    }

    @Test
    public void terminalClassificationAccumulatesTheFrameCapCounters() {
        final ProjectedNodeKey aPin = nodeKey("a-pin");
        final ProjectedNodeKey bPin = nodeKey("b-pin");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(aPin, bPin), Arrays.asList(
            enclosure(aHull, Collections.singletonList(aPin), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(bPin), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(aPin, 0.0, 30.0, bPin, 38.0, 30.0),
            anchorMap(aHull, 0.0, 30.0, bHull, 38.0, 30.0));
        final BoundaryInvariants invariants = new BoundaryInvariants();
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        final List<BoundaryConflict> conflicts = invariants.terminalConflicts(projection, positions,
            METRICS, Collections.<PinProjection>emptyList(), geometry,
            invariants.detect(projection, geometry), false);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).reason()).isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(invariants.capTraversals()).isPositive();
        assertThat(invariants.capCacheHits()).isZero();
    }

    private static GraphProjection projection(final List<ProjectedNodeKey> nodeKeys,
            final List<ProjectedEnclosure> enclosures) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : nodeKeys) {
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(key.source().toString(), key.source().toString()),
                "map", false));
        }
        return GraphProjection.projected(1L, nodes, enclosures, Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hull,
            final List<ProjectedNodeKey> directNodes, final BoundaryTier tier) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("e", "e")), "map", Optional.<EnclosureHullKey>empty(),
            directNodes, Collections.<EnclosureHullKey>emptyList(), false, tier);
    }

    private static ProjectedEnclosure parent(final EnclosureHullKey hull, final String label,
            final List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.EMPHATIC);
    }

    private static Map<ProjectedNodeKey, LayoutPoint> pointMap(final Object... values) {
        final Map<ProjectedNodeKey, LayoutPoint> result = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((ProjectedNodeKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }

    private static Map<EnclosureHullKey, LayoutPoint> anchorMap(final Object... values) {
        final Map<EnclosureHullKey, LayoutPoint> result = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((EnclosureHullKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(final String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }
}
```

- [ ] **Step 4: Run the characterization test and confirm it fails for the right reason first**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  -PTestLoggingFull
```

Before the extraction compiles this run must fail at `compileJava` with
`cannot find symbol: class BoundaryInvariants`; after Step 1-3 edits are complete it must
pass. Record which failure you observed.

- [ ] **Step 5: Run the extraction-preservation suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. The four pinned tests
`ancestorContainmentIsMemoizedAcrossIdenticalFrames` (1231-1244),
`ancestorContainmentMemoRechecksAHullWhoseGeometryChanged` (1262-1277),
`capSetsAreReusedWithinAFrame` (1281-1299) and
`candidateSelectionStopsWhenNoSideHasAMovableContributor` (1310-1325) must stay green
through the delegation, and `ancestorEscapePairKeysReportEscapingChildren` (245-262)
plus the `guardedMinimumSeparatingTranslation` test (239) must stay green through the
static wrappers.

- [ ] **Step 6: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryInvariants.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java
git commit -m "refactor(graph): extract BoundaryInvariants with preserved memo and counters [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 3: Add BoundaryInvariants.describe, BoundarySeparationResult.geometry() and NodeSeparationProjection.violatingPairs

**Implementer tier:** Advanced
**Lane:** edge-foundation
**Depends on:** Task 2

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryInvariants.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationResult.java:1-46`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java` (the `new BoundarySeparationResult(...)` construction in `separate`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationResultShould.java:25,36,43,50,57`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java`

**Interfaces:**

- Consumes: everything Task 2 produces; `NodeSeparationProjection.MIN_GAP = 6.0`, its
  private radius rule `NODE_RADIUS = 8.0` times `projection.prominence().get(key).scale()`
  (default `1.0` when absent); `CanonicalKeys.nodePair` from Task 1.
- Produces: `public Set<String> detectPairKeys(GraphProjection, GraphGeometry)` and
  `public BoundarySeparationDiagnostics describe(GraphProjection, LayoutPositions,
  GeometryTextMetrics, List<PinProjection>, GraphGeometry, List<String>,
  BoundarySeparationDiagnostics)` on `BoundaryInvariants`;
  `public GraphGeometry geometry()` on `BoundarySeparationResult`, stored at construction;
  `public static List<String> violatingPairs(GraphProjection, LayoutPositions)` on
  `NodeSeparationProjection`.

- [ ] **Step 1: Add the two new BoundaryInvariants entry points**

`detectPairKeys(projection, geometry)` calls `detect(projection, geometry)` and returns
the distinct identity keys in ascending `String.compareTo` order as an unmodifiable
`Set<String>` (a `LinkedHashSet` built from a sorted `TreeSet`), never resetting the
memo.

`describe(projection, positions, metrics, pins, geometry, baselineResidualKeys,
stageBaseline)` implements the N2 rebuild exactly:

1. `final Set<String> present = detectPairKeys(projection, geometry);`
   If `!baselineResidualKeys.containsAll(present)`, throw
   `new EdgeConflictException("Rebuilt boundary record contains a pair absent from the boundary baseline")`.
   (Task 4 creates `EdgeConflictException`; this task may first throw
   `BoundarySeparationException` with the same message, and Task 4 replaces the type. Task
   4 restates this signature, so either order compiles; prefer creating the guard with
   `BoundarySeparationException` now and changing the type in Task 4 in the same file
   edit that adds `EdgeConflictException`.)
2. `final List<BoundaryInvariants.Violation> violations = detect(projection, geometry);`
3. `final List<BoundaryConflict> conflicts = terminalConflicts(projection, positions,
   metrics, pins, geometry, violations, false);`
4. `final List<String> residualHullPairs = new ArrayList<String>(present);`
5. return `new BoundarySeparationDiagnostics(conflicts, residualHullPairs,
   stageBaseline.hullViolationsDetected(), violations.size(), stageBaseline.rounds(),
   stageBaseline.displacementRms(), stageBaseline.displacementMax(),
   stageBaseline.appliedDisplacements(), stageBaseline.deltaRms(),
   stageBaseline.deltaMax())`.

`present` must be an ascending list, so build it with
`new ArrayList<String>(new TreeSet<String>(presentSet))`. The method never resets the
memo or the counters, so `describe` shares both with every other call on the instance.

- [ ] **Step 2: Add BoundarySeparationResult.geometry() and pass the terminal geometry**

In `BoundarySeparationResult`, add a fourth constructor parameter
`final GraphGeometry terminalGeometry` (immediately after `positions`), validate it with
`Objects.requireNonNull(terminalGeometry, "geometry")`, store it, and add:

```java
    public GraphGeometry geometry() {
        return geometry;
    }
```

The constructor is package-private and its call site in `BoundarySeparationCorrection.separate`
becomes `new BoundarySeparationResult(current, GraphGeometry.of(current.nodes(), terminalHulls),
diagnostics, terminalNodeResidual, new BoundarySeparationTimings(...))`.

- [ ] **Step 3: Extract NodeSeparationProjection.violatingPairs and reuse it**

Add to `NodeSeparationProjection`:

```java
    /** The canonical node-pair keys of every unordered disc pair below the minimum gap. */
    public static List<String> violatingPairs(final GraphProjection projection,
            final LayoutPositions positions) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        Collections.sort(keys, new Comparator<ProjectedNodeKey>() {
            @Override
            public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                return CanonicalKeys.node(first).compareTo(CanonicalKeys.node(second));
            }
        });
        final List<String> result = new ArrayList<String>();
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                final ProjectedNodeKey firstKey = keys.get(first);
                final ProjectedNodeKey secondKey = keys.get(second);
                if (CanonicalKeys.node(firstKey).compareTo(CanonicalKeys.node(secondKey)) > 0) {
                    continue;
                }
                final LayoutPoint firstPoint = positions.nodes().get(firstKey);
                final LayoutPoint secondPoint = positions.nodes().get(secondKey);
                if (firstPoint == null || secondPoint == null) {
                    continue;
                }
                final double need = NODE_RADIUS * prominenceScale(projection, firstKey)
                    + NODE_RADIUS * prominenceScale(projection, secondKey) + MIN_GAP;
                if (Math.hypot(secondPoint.x() - firstPoint.x(), secondPoint.y() - firstPoint.y()) < need) {
                    result.add(CanonicalKeys.nodePair(firstKey, secondKey));
                }
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }
```

Rename the private `residualViolations(double[], double[], double[], int)` helper to
`countResidualViolations` and make `project` compute its `NodeSeparationResult` residual
as `violatingPairs(projection, result).size()` on the final `result`, so the predicate
has exactly one implementation and the returned count is unchanged. Add the needed
imports (`Comparator`, `CanonicalKeys`, `ProjectedNodeKey` already present,
`LayoutPoint` already present).

- [ ] **Step 4: Extend the tests**

In `BoundaryInvariantsShould` add this test (fixture helpers already exist in the file):

```java
    @Test
    public void describePreservesStageFieldsAndRecomputesResiduals() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey b = nodeKey("b");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a, b), Arrays.asList(
            enclosure(aHull, Collections.singletonList(a), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(b), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(a, 0.0, 0.0, b, 38.0, 0.0),
            anchorMap(aHull, 0.0, 0.0, bHull, 38.0, 0.0));
        final BoundarySeparationDiagnostics stageBaseline = new BoundarySeparationDiagnostics(
            Collections.<BoundaryConflict>emptyList(), Collections.<String>emptyList(), 7, 0, 3,
            1.5, 2.5, Collections.<String, LayoutPoint>emptyMap(), 0.5, 0.75);
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        final BoundarySeparationDiagnostics rebuilt = new BoundaryInvariants().describe(projection,
            positions, METRICS, Collections.<PinProjection>emptyList(), geometry,
            Collections.<String>emptyList(), stageBaseline);

        assertThat(rebuilt.hullViolationsDetected()).isEqualTo(7);
        assertThat(rebuilt.rounds()).isEqualTo(3);
        assertThat(rebuilt.displacementRms()).isEqualTo(1.5);
        assertThat(rebuilt.displacementMax()).isEqualTo(2.5);
        assertThat(rebuilt.deltaRms()).isEqualTo(0.5);
        assertThat(rebuilt.deltaMax()).isEqualTo(0.75);
        assertThat(rebuilt.appliedDisplacements()).isEmpty();
        assertThat(rebuilt.residualHullPairs()).isEqualTo(rebuilt.conflicts().isEmpty()
            ? Collections.<String>emptyList() : rebuilt.residualHullPairs());
        assertThat(rebuilt.residualHullPairs()).isSorted();
    }

    @Test
    public void describeRejectsAPairAbsentFromTheBaseline() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey b = nodeKey("b");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a, b), Arrays.asList(
            enclosure(aHull, Collections.singletonList(a), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(b), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(a, 0.0, 0.0, b, 38.0, 0.0),
            anchorMap(aHull, 0.0, 0.0, bHull, 38.0, 0.0));
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        assertThatThrownBy(() -> new BoundaryInvariants().describe(projection, positions, METRICS,
            Collections.<PinProjection>emptyList(), geometry, Collections.<String>emptyList(),
            BoundarySeparationDiagnostics.empty()))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("baseline");
    }
```

Add `import static org.assertj.core.api.Assertions.assertThatThrownBy;` to the file.

In `BoundarySeparationResultShould`, update the five constructor calls to pass a
terminal geometry: `GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
Collections.<EnclosureHullKey, HullGeometry>emptyMap())` and add a test:

```java
    @Test
    public void exposeTheTerminalGeometry() {
        final GraphGeometry geometry = GraphGeometry.of(
            Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());

        final BoundarySeparationResult result = new BoundarySeparationResult(positions(), geometry,
            diagnostics(), 0, timings());

        assertThat(result.geometry()).isSameAs(geometry);
    }
```

In `NodeSeparationProjectionShould`, add a test that two nodes at distance
`radiusA + radiusB + MIN_GAP - 0.001` produce one ascending
`CanonicalKeys.nodePair` key and that `project(...).residualViolations()` equals
`violatingPairs(...).size()` on the projected result; use the same
projection/positions helpers the class already uses.

- [ ] **Step 5: Run the two suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationResultShould" \
  --tests "org.freeplane.plugin.graph.layout.NodeSeparationProjectionShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryInvariants.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationResult.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationResultShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java
git commit -m "feat(graph): add rebuild describe, terminal geometry and the one gap predicate [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 4: Add the EdgeConflict value types and the behaviour-preserving correction scaffold

**Implementer tier:** Standard
**Lane:** edge-relocation
**Depends on:** Task 3

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictException.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictTimings.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictDiagnostics.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictResult.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`

**Interfaces:**

- Consumes: `BoundarySeparationResult.positions()`, `.diagnostics()`,
  `.nodeResidualViolations()`, `.appliedDisplacements()`, `.geometry()` (Task 3);
  `LayoutPositions`, `GraphProjection`, `GeometryTextMetrics`, `PinProjection`,
  `ProjectedEndpointKey`, `ProjectedNodeKey`, `LayoutPoint`, `CanonicalKeys.node`
  (Task 1).
- Produces: exactly the spec §2.3 API.
  `public final class EdgeConflictException extends RuntimeException` with
  `public EdgeConflictException(String)`, `(String, Throwable)`, `(Throwable)`.
  `public final class EdgeConflictTimings` with
  `public EdgeConflictTimings(long detectionNanos, long indexNanos, long verificationNanos,
  long rebuildNanos)`, the four accessors and `public long totalNanos()`; each value
  `>= 0` else `IllegalArgumentException("... must be nonnegative")`.
  `public final class EdgeConflictDiagnostics` with
  `public enum RepairKind { SWAP, MIRROR, TRANSLATION }`;
  `public static final class Repair` with `RepairKind kind()`,
  `List<ProjectedNodeKey> nodeKeys()` (ascending `CanonicalKeys.node` order, frozen copy),
  `Map<ProjectedNodeKey, LayoutPoint> fromCentres()`, `Map<ProjectedNodeKey, LayoutPoint> toCentres()`
  (immutable), `int batchIndex()`;
  `public static EdgeConflictDiagnostics empty()`;
  `int conflictsDetected()`, `crossingsDetected()`, `edgeOverNodeDetected()`,
  `crossingsResolved()`, `edgeOverNodeResolved()`, `List<String> conflictsResidual()`,
  `List<Repair> repairsApplied()`, `int rounds()`, `int verifications()`,
  `boolean verificationBudgetExhausted()`, `boolean indexBudgetExhausted()`,
  `long indexInsertedItems()`, `long indexPairChecks()`, `int indexSubdivisions()`;
  plus a full constructor taking all of those in that order (used by Task 14).
  `public final class EdgeConflictResult` with `LayoutPositions positions()`,
  `EdgeConflictDiagnostics diagnostics()`, `EdgeConflictTimings timings()`,
  `BoundarySeparationDiagnostics boundaryDiagnostics()`, `int nodeResidualViolations()`,
  `Map<String, LayoutPoint> appliedDisplacements()`.
  `public final class EdgeConflictCorrection` with `public EdgeConflictCorrection()` and
  `public EdgeConflictResult apply(GraphProjection, LayoutPositions, GeometryTextMetrics,
  List<PinProjection>, Set<ProjectedEndpointKey>, BoundarySeparationResult)`.

- [ ] **Step 1: Write the failing scaffold test**

Create `EdgeConflictCorrectionShould.java` with exactly this content; later tasks append
to this class and must not rename this test:

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.junit.Test;

public class EdgeConflictCorrectionShould {
    private static final GeometryTextMetrics METRICS =
        new AwtGeometryTextMetrics(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12),
            new java.awt.font.FontRenderContext(null, true, true));

    @Test
    public void returnsTheBoundaryResultUnchangedInTheScaffold() {
        final GraphProjection projection = GraphProjection.structure(1L,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedNode>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList());
        final LayoutPositions positions = LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        final GraphGeometry geometry = GraphGeometry.of(
            Collections.<ProjectedNodeKey, org.freeplane.plugin.graph.geometry.NodeGeometry>emptyMap(),
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));

        final EdgeConflictResult result = new EdgeConflictCorrection().apply(projection, positions, METRICS,
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEndpointKey>emptySet(), boundary);

        assertThat(result.positions()).isSameAs(positions);
        assertThat(result.diagnostics()).isEqualTo(EdgeConflictDiagnostics.empty());
        assertThat(result.boundaryDiagnostics()).isSameAs(boundary.diagnostics());
        assertThat(result.nodeResidualViolations()).isEqualTo(boundary.nodeResidualViolations());
        assertThat(result.appliedDisplacements()).isEmpty();
        assertThat(result.timings().totalNanos()).isZero();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava`, `cannot find symbol` for `EdgeConflictResult`,
`EdgeConflictCorrection` and `EdgeConflictDiagnostics`.

- [ ] **Step 3: Write the value types and the scaffold correction**

Create the five production files. `EdgeConflictException` is:

```java
package org.freeplane.plugin.graph.layout;

public final class EdgeConflictException extends RuntimeException {
    public EdgeConflictException(final String message) {
        super(message);
    }

    public EdgeConflictException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public EdgeConflictException(final Throwable cause) {
        super(cause);
    }
}
```

`EdgeConflictTimings` stores the four nonnegative longs and exposes the accessors plus
`totalNanos()` returning `detectionNanos + indexNanos + verificationNanos + rebuildNanos`.

`EdgeConflictDiagnostics` stores frozen copies of `conflictsResidual` (a `List<String>`),
`repairsApplied` (a `List<Repair>`), the eleven numeric/boolean members and the three
counted-work longs; every constructor input is validated (`null` entries rejected,
counts `>= 0`, `indexInsertedItems`/`indexPairChecks >= 0`, `indexSubdivisions >= 0`).
`empty()` returns zeros, empty lists and both flags `false`. `Repair` sorts `nodeKeys`
ascending by `CanonicalKeys.node`, validates that `fromCentres`/`toCentres` have exactly
those keys (same key set), freezes both maps in the ascending node order and validates
`batchIndex >= 0`; `kind` is required.

`EdgeConflictResult` and the scaffold correction are exactly:

```java
public final class EdgeConflictResult {
    private final LayoutPositions positions;
    private final EdgeConflictDiagnostics diagnostics;
    private final EdgeConflictTimings timings;
    private final BoundarySeparationDiagnostics boundaryDiagnostics;
    private final int nodeResidualViolations;
    private final Map<String, LayoutPoint> appliedDisplacements;

    public EdgeConflictResult(final LayoutPositions positions, final EdgeConflictDiagnostics diagnostics,
            final EdgeConflictTimings timings,
            final BoundarySeparationDiagnostics boundaryDiagnostics,
            final int nodeResidualViolations, final Map<String, LayoutPoint> appliedDisplacements) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.timings = Objects.requireNonNull(timings, "timings");
        this.boundaryDiagnostics = Objects.requireNonNull(boundaryDiagnostics, "boundaryDiagnostics");
        if (nodeResidualViolations < 0) {
            throw new IllegalArgumentException("Node residual violations must be nonnegative");
        }
        this.nodeResidualViolations = nodeResidualViolations;
        this.appliedDisplacements = Collections.unmodifiableMap(
            new LinkedHashMap<String, LayoutPoint>(Objects.requireNonNull(appliedDisplacements,
                "appliedDisplacements")));
    }

    public LayoutPositions positions() { return positions; }
    public EdgeConflictDiagnostics diagnostics() { return diagnostics; }
    public EdgeConflictTimings timings() { return timings; }
    public BoundarySeparationDiagnostics boundaryDiagnostics() { return boundaryDiagnostics; }
    public int nodeResidualViolations() { return nodeResidualViolations; }
    public Map<String, LayoutPoint> appliedDisplacements() { return appliedDisplacements; }
}
```

```java
public final class EdgeConflictCorrection {
    public EdgeConflictCorrection() {
    }

    public EdgeConflictResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedEndpointKey> visibleEndpoints,
            final BoundarySeparationResult boundaryResult) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(pins, "pins");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        Objects.requireNonNull(boundaryResult, "boundaryResult");
        return new EdgeConflictResult(positions, EdgeConflictDiagnostics.empty(),
            new EdgeConflictTimings(0L, 0L, 0L, 0L), boundaryResult.diagnostics(),
            boundaryResult.nodeResidualViolations(), Collections.<String, LayoutPoint>emptyMap());
    }
}
```

The scaffold is intentionally inert: it moves nothing, publishes the boundary record
unchanged (N3 path) and carries zero timings; Tasks 13-15 replace the body and keep this
signature.

- [ ] **Step 4: Run the test and confirm it passes**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: PASS, 1 test.

- [ ] **Step 5: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictException.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictTimings.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictDiagnostics.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictResult.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "feat(graph): add EdgeConflict value types and the inert correction scaffold [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 5: Add the EdgePath, EdgePaths, route diagnostics and route timings value types

**Implementer tier:** Advanced
**Lane:** edge-routing
**Depends on:** Task 1

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgePath.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgePaths.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouteDiagnostics.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouteTimings.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgePathShould.java`

**Interfaces:**

- Consumes: `LayoutPoint`, `ProjectedEdgeKey`.
- Produces: exactly the spec §2.4 API.
  `public final class EdgePath`:
  `public enum Shape { STRAIGHT, BOW, DOGLEG }`;
  `public static EdgePath straight(LayoutPoint, LayoutPoint)`;
  `public static EdgePath bow(LayoutPoint, LayoutPoint, LayoutPoint)`;
  `public static EdgePath dogleg(LayoutPoint, LayoutPoint, LayoutPoint, LayoutPoint)`;
  `Shape shape()`, `LayoutPoint start()`, `LayoutPoint end()`,
  `Optional<LayoutPoint> control()`, `List<LayoutPoint> definingPoints()`,
  `List<LayoutPoint> samples()`, `double length()`, `LayoutPoint arcLengthMidpoint()`,
  `LayoutPoint arrowAnchor(boolean atStart)`, `double terminalSegmentLength(boolean atStart)`,
  `equals`/`hashCode` (value equality over `definingPoints()` and `samples()`).
  `public final class EdgePaths`: `public static EdgePaths empty()`;
  `public static EdgePaths of(Map<ProjectedEdgeKey, EdgePath>, EdgeRouteDiagnostics,
  EdgeRouteTimings)`; `Map<ProjectedEdgeKey, EdgePath> byEdge()` (insertion order =
  canonical `CanonicalKeys.edge` order); `Optional<EdgePath> path(ProjectedEdgeKey)`;
  `EdgeRouteDiagnostics diagnostics()`; `EdgeRouteTimings timings()`.
  `public final class EdgeRouteDiagnostics` with `public static EdgeRouteDiagnostics empty()`,
  a full constructor `(int edgesStraight, int edgesBow, int edgesDogleg, int edgesFallback,
  int pathCrossingsDetected, List<String> pathCrossingsResidual, int pathHullPenetrations)`
  and those accessors.
  `public final class EdgeRouteTimings` with
  `(long indexNanos, long selectionNanos, long residualNanos)`, those accessors and
  `long totalNanos()`.

- [ ] **Step 1: Write the failing test**

Create `EdgePathShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.junit.Test;

public class EdgePathShould {
    @Test
    public void exposeStraightDefiningPointsSamplesAndTerminalValues() {
        final EdgePath path = EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(100.0, 0.0));

        assertThat(path.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(path.definingPoints()).containsExactly(LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(100.0, 0.0));
        assertThat(path.samples()).isEqualTo(path.definingPoints());
        assertThat(path.length()).isEqualTo(100.0);
        assertThat(path.arcLengthMidpoint()).isEqualTo(LayoutPoint.of(50.0, 0.0));
        assertThat(path.arrowAnchor(true)).isEqualTo(LayoutPoint.of(100.0, 0.0));
        assertThat(path.arrowAnchor(false)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(path.terminalSegmentLength(true)).isEqualTo(100.0);
        assertThat(path.terminalSegmentLength(false)).isEqualTo(100.0);
        assertThat(path.control()).isEmpty();
    }

    @Test
    public void sampleABowWithThePinnedCountFormula() {
        final EdgePath path = EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 90.0),
            LayoutPoint.of(180.0, 0.0));

        // d = 90, n = ceil(sqrt(90 / 1.5)) = ceil(sqrt(60)) = 8, so 9 samples.
        assertThat(path.samples()).hasSize(9);
        assertThat(path.samples().get(0)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(path.samples().get(8)).isEqualTo(LayoutPoint.of(180.0, 0.0));
        assertThat(path.samples().get(4).x()).isEqualTo(90.0);
        assertThat(path.samples().get(4).y()).isEqualTo(45.0);
        assertThat(path.control()).contains(LayoutPoint.of(0.0, 90.0));
        // The terminal segment is control -> arrival attachment.
        assertThat(path.arrowAnchor(false)).isEqualTo(LayoutPoint.of(0.0, 90.0));
        assertThat(path.terminalSegmentLength(false))
            .isEqualTo(Math.hypot(180.0, 90.0));
    }

    @Test
    public void exposeDoglegDefiningPointsAndTerminalValues() {
        final EdgePath path = EdgePath.dogleg(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(10.0, 20.0),
            LayoutPoint.of(20.0, 20.0), LayoutPoint.of(30.0, 0.0));

        assertThat(path.definingPoints()).containsExactly(LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(10.0, 20.0), LayoutPoint.of(20.0, 20.0), LayoutPoint.of(30.0, 0.0));
        assertThat(path.samples()).isEqualTo(path.definingPoints());
        assertThat(path.arrowAnchor(true)).isEqualTo(LayoutPoint.of(10.0, 20.0));
        assertThat(path.arrowAnchor(false)).isEqualTo(LayoutPoint.of(20.0, 20.0));
        assertThat(path.terminalSegmentLength(false)).isEqualTo(Math.hypot(10.0, 20.0));
        assertThat(path.length()).isEqualTo(Math.hypot(10.0, 20.0) + 10.0 + Math.hypot(10.0, 20.0));
    }

    @Test
    public void useValueEqualityAcrossEquivalentShapes() {
        assertThat(EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 2.0)))
            .isEqualTo(EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 2.0)))
            .isNotEqualTo(EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(2.0, 1.0)));
        assertThat(EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 1.0),
            LayoutPoint.of(1.0, 0.0)).hashCode())
            .isEqualTo(EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 1.0),
                LayoutPoint.of(1.0, 0.0)).hashCode());
    }

    @Test
    public void returnTheFirstSampleForADegeneratePath() {
        final EdgePath path = EdgePath.straight(LayoutPoint.of(1.0, 1.0), LayoutPoint.of(1.0, 1.0));

        assertThat(path.length()).isZero();
        assertThat(path.arcLengthMidpoint()).isEqualTo(LayoutPoint.of(1.0, 1.0));
    }

    @Test
    public void freezeEdgePathsInCanonicalInsertionOrderAndRejectNulls() {
        final ProjectedEdgeKey first = ProjectedEdgeKey.of(
            org.freeplane.plugin.graph.projection.ProjectedEndpointKey.ofNode(
                org.freeplane.plugin.graph.projection.ProjectedNodeKey.of(
                    org.freeplane.plugin.graph.projection.input.SourceNodeKey.persisted(
                        org.freeplane.plugin.graph.workspace.model.NodeReference.of(
                            org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(
                                "8055d8c8-d71e-40f3-a8ed-5bf2502f2cad"),
                            org.freeplane.plugin.graph.workspace.model.PersistedNodeId.of("a"))))),
            org.freeplane.plugin.graph.projection.ProjectedEndpointKey.ofNode(
                org.freeplane.plugin.graph.projection.ProjectedNodeKey.of(
                    org.freeplane.plugin.graph.projection.input.SourceNodeKey.persisted(
                        org.freeplane.plugin.graph.workspace.model.NodeReference.of(
                            org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(
                                "8055d8c8-d71e-40f3-a8ed-5bf2502f2cad"),
                            org.freeplane.plugin.graph.workspace.model.PersistedNodeId.of("b"))))));
        final Map<ProjectedEdgeKey, EdgePath> byEdge = new java.util.LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        byEdge.put(first, EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 1.0)));
        final EdgePaths paths = EdgePaths.of(byEdge, EdgeRouteDiagnostics.empty(),
            new EdgeRouteTimings(1L, 2L, 3L));

        assertThat(paths.byEdge()).containsExactlyEntriesOf(byEdge);
        assertThat(paths.path(first)).isPresent();
        assertThat(paths.timings().totalNanos()).isEqualTo(6L);
        assertThat(EdgePaths.empty().byEdge()).isEmpty();
        assertThat(EdgePaths.empty().path(first)).isEmpty();
        assertThatThrownBy(() -> EdgePaths.of(null, EdgeRouteDiagnostics.empty(),
            new EdgeRouteTimings(0L, 0L, 0L))).isInstanceOf(NullPointerException.class);
    }
}
```

Add `import java.util.Map;` to the test file.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgePathShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava`, `cannot find symbol: class EdgePath`.

- [ ] **Step 3: Write the four value types**

`EdgePath` rules (spec §6.5-§6.7): `BOW_SAMPLE_TOLERANCE = 0.75` is
`public static final`; `samples()` is computed once in the constructor; a bow's control
point is stored; `arrowAnchor(atStart)` returns the opposite end for `STRAIGHT`, the
control for `BOW`, `bend1` for the start and `bend2` for the end of a `DOGLEG`;
`terminalSegmentLength(atStart)` is the straight start-to-end distance, the
control-to-arrival distance (or departure-to-control for `atStart`), or the final/initial
dogleg leg. `arcLengthMidpoint()` walks `samples()` and returns the point at half
`length()`; if `length() == 0.0` it returns `samples().get(0)`. Bow sampling is:

```java
    private static List<LayoutPoint> sampleBow(final LayoutPoint start, final LayoutPoint control,
            final LayoutPoint end) {
        final double chordLength = distance(start, end);
        final double sagitta = chordLength == 0.0 ? distance(control, start)
            : distanceToLine(control, start, end);
        final int segments = Math.max(1, (int) Math.ceil(
            Math.sqrt(sagitta / (2.0 * BOW_SAMPLE_TOLERANCE))));
        final List<LayoutPoint> samples = new ArrayList<LayoutPoint>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            final double t = (double) index / (double) segments;
            final double oneMinusT = 1.0 - t;
            final double x = oneMinusT * oneMinusT * start.x()
                + 2.0 * oneMinusT * t * control.x() + t * t * end.x();
            final double y = oneMinusT * oneMinusT * start.y()
                + 2.0 * oneMinusT * t * control.y() + t * t * end.y();
            samples.add(LayoutPoint.of(x, y));
        }
        return Collections.unmodifiableList(samples);
    }
```

where `distanceToLine` is the exact point-to-line distance of the chord through
`start`/`end` (the line, not the segment; `d` is the perpendicular distance of the
control point from the attachment chord). Endpoint duplicates are not removed. All lists
are `Collections.unmodifiableList`; constructors validate that every point is non-null
and that the shape's point count matches.

`EdgePaths.of` copies `byEdge` into an unmodifiable `LinkedHashMap`, rejecting null keys
and values; `byEdge()` returns that map; `path(key)` returns `Optional.ofNullable`; `empty()`
delegates to `of(Collections.<ProjectedEdgeKey, EdgePath>emptyMap(), EdgeRouteDiagnostics.empty(),
new EdgeRouteTimings(0L, 0L, 0L))`. `EdgeRouteDiagnostics` freezes `pathCrossingsResidual`
as an unmodifiable list and validates nonnegative counts and non-null entries; `empty()`
returns zeros and an empty list. `EdgeRouteTimings` validates nonnegative longs and
`totalNanos()` sums the three.

- [ ] **Step 4: Run the test and confirm it passes**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgePathShould" \
  -PTestLoggingFull
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgePath.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgePaths.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouteDiagnostics.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouteTimings.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgePathShould.java
git commit -m "feat(graph): add EdgePath, EdgePaths and route diagnostics value types [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 6: Add the straight-only EdgeRouter scaffold and the crossing predicate

**Implementer tier:** Standard
**Lane:** edge-routing
**Depends on:** Task 5

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java`

**Interfaces:**

- Consumes: `EdgePath`, `EdgePaths`, `EdgeRouteDiagnostics`, `EdgeRouteTimings` from
  Task 5; `ProjectedEndpointVisibility.visibleEndpoints(List<ProjectedNode>,
  List<ProjectedEnclosure>)`; `GraphProjection.nodes()/enclosures()/edges()`;
  `GraphGeometry.nodes()/hulls()/edgeAttachment(ProjectedEndpointKey, LayoutPoint)`;
  `ProjectedEdge.first()/second()/key()`; `ProjectedNodeKey`, `EnclosureHullKey`,
  `HullGeometry.labelAnchor()`, `NodeGeometry.center()`; `CanonicalKeys.edge` (Task 1).
- Produces: `public final class EdgeRouter` with
  `public EdgePaths route(GraphProjection, LayoutPositions, GraphGeometry,
  Set<ProjectedEndpointKey>)` and
  `public static boolean properlyCross(EdgePath, EdgePath)`. This task implements the
  canonical straight-only route; Task 16 replaces the candidate selection with the
  pinned ladder while keeping both signatures.

- [ ] **Step 1: Write the failing scaffold tests**

Create `EdgeRouterShould.java` with exactly this content; later tasks append to this class
and must not rename these tests:

```java
package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.junit.Test;

public class EdgeRouterShould {
    @Test
    public void exposeEdgePathsInCanonicalEdgeOrder() {
        final ProjectedEdgeKey first = EdgeRouterFixture.exitEdge("a", "b");
        final ProjectedEdgeKey second = EdgeRouterFixture.exitEdge("a", "c");

        assertThat(org.freeplane.plugin.graph.projection.CanonicalKeys.edge(first)
                .compareTo(org.freeplane.plugin.graph.projection.CanonicalKeys.edge(second)) <= 0).isTrue();
    }

    @Test
    public void exposeStraightFallbackForAStraightOnlyScaffold() {
        // The reported fixture's four nodes and two crossing chords: the scaffold returns
        // two STRAIGHT paths and reports the crossing pair as a residual.
        final EdgeRouterFixture fixture = EdgeRouterFixture.reportedConvexQuad();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).hasSize(2);
        final List<EdgePath> values = new java.util.ArrayList<EdgePath>(paths.byEdge().values());
        assertThat(values.get(0).shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(values.get(1).shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(2);
        assertThat(paths.diagnostics().edgesBow()).isZero();
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
        assertThat(paths.diagnostics().edgesFallback()).isZero();
        assertThat(paths.diagnostics().pathCrossingsDetected()).isEqualTo(1);
        assertThat(paths.diagnostics().pathCrossingsResidual()).hasSize(1);
    }

    @Test
    public void omitAnEdgeWhoseGeometryIsNotResolvableAndDoNotUseItAsAnObstacle() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.oneResolvableEdgeAndOneBrokenEdge();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).hasSize(1);
        assertThat(paths.diagnostics().pathCrossingsDetected()).isZero();
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void detectAProperCrossingWithTheExponentNormalisedPredicate() {
        final EdgePath first = EdgePath.straight(LayoutPoint.of(-1e300, -1e300), LayoutPoint.of(1e300, 1e300));
        final EdgePath second = EdgePath.straight(LayoutPoint.of(1e300, -1e300), LayoutPoint.of(-1e300, 1e300));
        final EdgePath parallel = EdgePath.straight(LayoutPoint.of(-1e300, 0.0), LayoutPoint.of(1e300, 0.0));

        assertThat(EdgeRouter.properlyCross(first, second)).isTrue();
        assertThat(EdgeRouter.properlyCross(first, parallel)).isFalse();
        assertThat(EdgeRouter.properlyCross(EdgePath.straight(LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(1.0, 1.0)), EdgePath.straight(LayoutPoint.of(1.0, 1.0),
                LayoutPoint.of(2.0, 2.0)))).isFalse();
    }

    @Test
    public void rejectNullInputsAndEmptyEdgeSets() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.empty();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).isEmpty();
        assertThat(paths.diagnostics()).isEqualTo(EdgeRouteDiagnostics.empty());
        assertThat(EdgeRouterFixture.exitEdge("a", "b")).isNotNull();
        assertThat(Arrays.asList(fixture.projection, fixture.positions, fixture.geometry)).doesNotContainNull();
    }
}
```

Also create the shared test fixture
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java`
exactly as follows; Tasks 11 and 16 reuse `reportedConvexQuad()`, `laterChordBow()`,
`fanCrossing()` and `empty()`:

```java
package org.freeplane.plugin.graph.geometry;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

final class EdgeRouterFixture {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(
        new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

    final GraphProjection projection;
    final LayoutPositions positions;
    final GraphGeometry geometry;
    final Set<ProjectedEndpointKey> visibleEndpoints;

    private EdgeRouterFixture(final GraphProjection projection, final LayoutPositions positions) {
        this.projection = projection;
        this.positions = positions;
        this.geometry = new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        this.visibleEndpoints = ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(),
            projection.enclosures());
    }

    static EdgeRouterFixture empty() {
        return new EdgeRouterFixture(GraphProjection.structure(1L,
            Collections.<ProjectedNode>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList()),
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture reportedConvexQuad() {
        final ProjectedNodeKey a = node("ID_A");
        final ProjectedNodeKey b = node("ID_B");
        final ProjectedNodeKey c = node("ID_C");
        final ProjectedNodeKey d = node("ID_D");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, c, d), Arrays.asList(
            edge(a, c), edge(b, d))), LayoutPositions.of(
            points(a, 272.0, 170.0, b, 307.0, 202.0, c, 275.0, 240.0, d, 240.0, 208.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture laterChordBow() {
        final ProjectedNodeKey one = node("ID_1");
        final ProjectedNodeKey two = node("ID_2");
        final ProjectedNodeKey three = node("ID_3");
        final ProjectedNodeKey four = node("ID_4");
        return new EdgeRouterFixture(projection(Arrays.asList(one, two, three, four), Arrays.asList(
            edge(one, two), edge(three, four))), LayoutPositions.of(
            points(one, -90.0, 0.0, two, 90.0, 0.0, three, 0.0, -25.0, four, 0.0, 25.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture fanCrossing() {
        final ProjectedNodeKey x = node("X");
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey o = node("O");
        return new EdgeRouterFixture(projection(Arrays.asList(x, a, b, o), Arrays.asList(
            edge(x, a), edge(x, b))), LayoutPositions.of(
            points(x, 0.0, 0.0, a, 12.997, 48.089, b, 38.39, 23.412, o, 4.808, 23.714),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture oneResolvableEdgeAndOneBrokenEdge() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey missing = node("MISSING");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b), Arrays.asList(
            edge(a, b), edge(a, missing))), LayoutPositions.of(
            points(a, 0.0, 0.0, b, 100.0, 0.0), Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static ProjectedEdgeKey exitEdge(final String first, final String second) {
        return ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(node(first)),
            ProjectedEndpointKey.ofNode(node(second)));
    }

    static ProjectedNodeKey node(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static GraphProjection projection(final List<ProjectedNodeKey> keys,
            final List<ProjectedEdgeKey> edges) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : keys) {
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(key.source().toString(),
                key.source().toString()), "map", false));
        }
        final List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        for (final ProjectedEdgeKey edge : edges) {
            final SourceNodeKey sourceKey = edge.first().node().get().source();
            final NodeReference targetReference = NodeReference.of(
                edge.second().node().get().source().mapReferenceId(),
                edge.second().node().get().source().persistedReference().get().nodeId());
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(sourceKey, targetReference,
                false, false, "source", "middle", "target");
            projectedEdges.add(ProjectedEdge.of(edge, Collections.singletonList(
                EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor), edge.first(),
                    edge.second()))));
        }
        return GraphProjection.projected(1L, nodes,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList(),
            projectedEdges, Collections.<org.freeplane.plugin.graph.projection.RelationshipResolution>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList());
    }

    private static ProjectedEdgeKey edge(final ProjectedNodeKey first, final ProjectedNodeKey second) {
        return ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(first), ProjectedEndpointKey.ofNode(second));
    }

    private static Map<ProjectedNodeKey, LayoutPoint> points(final Object... values) {
        final Map<ProjectedNodeKey, LayoutPoint> result = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((ProjectedNodeKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }
}
```

The fixture uses `ConnectorDescriptor.of(SourceNodeKey, NodeReference, boolean, boolean,
String, String, String)`, `ConnectorSnapshot.of(int, ConnectorDescriptor)`, and
`EdgeContributor.nativeConnector(ConnectorSnapshot, ProjectedEndpointKey,
ProjectedEndpointKey)` exactly as `GraphCanvasPaintShould.directedEdge` does; add the
`ConnectorDescriptor`, `ConnectorSnapshot`, `EdgeContributor`, and `NodeReference` imports.

- [ ] **Step 2: Run the tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava`, `cannot find symbol: class EdgeRouter`.

- [ ] **Step 3: Write the scaffold router and the predicate**

`properlyCross(first, second)` is already final and lives here; implement the spec §3.2
predicate exactly:

```java
    private static int orientationSign(final int exponent, final LayoutPoint p, final LayoutPoint q,
            final LayoutPoint r) {
        final double px = Math.scalb(p.x(), -exponent);
        final double py = Math.scalb(p.y(), -exponent);
        final double qx = Math.scalb(q.x(), -exponent);
        final double qy = Math.scalb(q.y(), -exponent);
        final double rx = Math.scalb(r.x(), -exponent);
        final double ry = Math.scalb(r.y(), -exponent);
        final double cross = (qx - px) * (ry - py) - (qy - py) * (rx - px);
        return cross == 0.0 ? 0 : Double.compare(cross, 0.0);
    }

    private static int commonExponent(final LayoutPoint... points) {
        int exponent = Integer.MIN_VALUE;
        for (final LayoutPoint point : points) {
            exponent = Math.max(exponent, Math.max(Math.getExponent(point.x()),
                Math.getExponent(point.y())));
        }
        return exponent == Integer.MIN_VALUE ? 0 : exponent;
    }

    public static boolean properlyCross(final EdgePath first, final EdgePath second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        final List<LayoutPoint> firstSamples = first.samples();
        final List<LayoutPoint> secondSamples = second.samples();
        for (int a = 0; a + 1 < firstSamples.size(); a++) {
            for (int b = 0; b + 1 < secondSamples.size(); b++) {
                if (properlyCrossSegments(firstSamples.get(a), firstSamples.get(a + 1),
                        secondSamples.get(b), secondSamples.get(b + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean properlyCrossSegments(final LayoutPoint a, final LayoutPoint b,
            final LayoutPoint c, final LayoutPoint d) {
        final int exponent = commonExponent(a, b, c, d);
        return orientationSign(exponent, a, b, c) * orientationSign(exponent, a, b, d) < 0
            && orientationSign(exponent, c, d, a) * orientationSign(exponent, c, d, b) < 0;
    }
```

`route` in this task: build the projected edges in ascending `CanonicalKeys.edge` order,
skip edges with an invisible endpoint or an unresolvable anchor/attachment (an anchor for
a node endpoint is `geometry.nodes().get(key).center()`; for an enclosure endpoint it is
`positions.anchors().get(hullKey)` falling back to `hull.labelAnchor()` only when the
anchor is non-finite, exactly as `GraphHitIndex.enclosureAnchor` does), compute the
attachment with `geometry.edgeAttachment(endpoint, otherAnchor)`, and emit
`EdgePath.straight(first, second)`. `pathCrossingsDetected` counts distinct
`CanonicalKeys.edgePair` keys over the straight paths with `properlyCross` (unordered
pairs, ascending); `pathCrossingsResidual` is the same set after routing (identical here);
`edgesStraight` is the emitted count; `pathHullPenetrations` is `0` until Task 16; the
timings are zero until Task 16. Use a `TreeMap` keyed by `CanonicalKeys.edge` for the
incoming edges and a `LinkedHashMap` for the result so insertion order is canonical.

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  -PTestLoggingFull
```

Expected: PASS, 5 tests. If `properlyCross` fails the extreme-span case, check the
exponent extraction: it must use one common exponent over all four points and
`Math.scalb(value, -exponent)`.

- [ ] **Step 5: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java
git commit -m "feat(graph): add the straight-only EdgeRouter scaffold and crossing predicate [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 7: Plumb EdgeConflict diagnostics through LayoutFrame and LayoutWorker

**Implementer tier:** Advanced
**Lane:** edge-publication
**Depends on:** Task 4

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java:1-98`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java:35,281-303,368-378`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:761`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java:1194,1604,1646`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java:1203,1410`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`

**Interfaces:**

- Consumes: `EdgeConflictCorrection.apply(GraphProjection, LayoutPositions, GeometryTextMetrics,
  List<PinProjection>, Set<ProjectedEndpointKey>, BoundarySeparationResult)` and
  `EdgeConflictDiagnostics.empty()` from Task 4; `BoundarySeparationResult.positions()`,
  `.diagnostics()`, `.appliedDisplacements()`, `.nodeResidualViolations()`;
  `ProjectedEndpointVisibility.visibleEndpoints(...)`;
  `PerceptualIdlePolicy.observe(LayoutPositions, LayoutPositions)`;
  `BoundarySeparationDiagnostics.withDeltas(double, double)`.
- Produces: `public static LayoutFrame withDiagnostics(LayoutFrame frame, int residualViolations,
  BoundarySeparationDiagnostics boundaryDiagnostics, EdgeConflictDiagnostics
  edgeConflictDiagnostics, PerceptualIdlePolicy.IdleMeasurement idle)` (the only
  `withDiagnostics`; the three-argument form is removed) and
  `public EdgeConflictDiagnostics edgeConflictDiagnostics()` on `LayoutFrame`;
  `LayoutWorker.accept` composes the edge stage exactly as spec §5.6, with
  `mergeDisplacements(Map<String, LayoutPoint>, Map<String, LayoutPoint>)` returning a
  `LinkedHashMap` ascending by field key and summing duplicate keys componentwise;
  `LayoutWorker.EMPTY_FAILED_FRAME` and `LayoutWorker.failedFrame` and
  `LayoutSettleLoop.failedFrame` carry `EdgeConflictDiagnostics.empty()` or the retained
  `retained.edgeConflictDiagnostics()` respectively.

- [ ] **Step 1: Write the failing composition test**

Append this test to `LayoutWorkerShould` (keep every existing test and helper; add the
imports `org.freeplane.plugin.graph.geometry.*` only where absent):

```java
    @Test
    public void decoratePublishedFramesWithEmptyEdgeConflictDiagnosticsInTheScaffold() {
        LayoutWorker worker = new LayoutWorker(fixedEngine(), PerceptualIdlePolicy.spikeDefaults());
        try {
            LayoutFrame frame = worker.submit(request()).toCompletableFuture().get();

            assertThat(frame.edgeConflictDiagnostics()).isEqualTo(EdgeConflictDiagnostics.empty());
            assertThat(frame.boundaryDiagnostics().rounds()).isEqualTo(0);
            assertThat(frame.verified()).isTrue();
        }
        catch (Exception failure) {
            throw new AssertionError(failure);
        }
        finally {
            worker.close();
        }
    }
```

If `LayoutWorkerShould` already has an equivalent engine/request helper, reuse it
verbatim; otherwise add these helpers the way the class's existing tests construct them
(a deterministic `LayoutEngine` whose `apply` returns the frame's positions, and a
`LayoutRequest` built from a two-node projection). Do not change the assertion: the
scaffold edge stage returns `EdgeConflictDiagnostics.empty()`.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava`, `cannot find symbol: method
edgeConflictDiagnostics()` (and, until the call sites move, the removed three-argument
`withDiagnostics`).

- [ ] **Step 3: Change LayoutFrame**

In `LayoutFrame`: add the field `private final EdgeConflictDiagnostics
edgeConflictDiagnostics;`, take it in the private constructor after
`boundaryDiagnostics`, `Objects.requireNonNull` it, initialise it to
`EdgeConflictDiagnostics.empty()` in both `of(...)` factories, replace the
three-argument `withDiagnostics` with exactly:

```java
    public static LayoutFrame withDiagnostics(final LayoutFrame raw, final int residualViolations,
            final BoundarySeparationDiagnostics boundaryDiagnostics,
            final EdgeConflictDiagnostics edgeConflictDiagnostics,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        final LayoutFrame value = Objects.requireNonNull(raw, "raw");
        return new LayoutFrame(value.stepIndex, value.positions, value.failed, residualViolations,
            boundaryDiagnostics, edgeConflictDiagnostics, idle);
    }
```

and add `public EdgeConflictDiagnostics edgeConflictDiagnostics() { return
edgeConflictDiagnostics; }`. Import `EdgeConflictDiagnostics` (same package, no import
needed).

- [ ] **Step 4: Compose the edge stage in LayoutWorker.accept**

Replace the body of `accept` from the boundary call onward with the spec §5.6
composition, scaffold version (the edge stage is inert, so `corrected` equals the
boundary positions and `merged` equals the boundary displacements):

```java
        final BoundarySeparationResult boundary = boundaryCorrection.apply(request.projection(),
            raw.positions(), defaultMetrics(), request.pins());
        if (!boundary.diagnostics().boundaryVerified() && !boundary.diagnostics().boundaryCovered()) {
            throw new BoundarySeparationException("Boundary separation did not cover every residual pair");
        }
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            request.projection().nodes(), request.projection().enclosures());
        final EdgeConflictResult edge = edgeConflictCorrection.apply(request.projection(),
            boundary.positions(), defaultMetrics(), request.pins(), visible, boundary);
        final LayoutPositions corrected = edge.positions();
        final Map<String, LayoutPoint> merged = mergeDisplacements(boundary.appliedDisplacements(),
            edge.appliedDisplacements());
        final LayoutPositions before = previousCorrectedPositions == null ? corrected
            : previousCorrectedPositions;
        final PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);
        final double[] deltas = displacementDeltas(previousAppliedDisplacements, merged);
        final LayoutFrame decorated = LayoutFrame.withDiagnostics(
            LayoutFrame.of(raw.stepIndex(), corrected, false, edge.nodeResidualViolations()),
            edge.nodeResidualViolations(),
            edge.boundaryDiagnostics().withDeltas(deltas[0], deltas[1]), edge.diagnostics(), idle);
        previousAppliedDisplacements = merged;
```

Add the field `private final EdgeConflictCorrection edgeConflictCorrection = new
EdgeConflictCorrection();` next to `boundaryCorrection`, the
`Set`/`ProjectedEndpointKey` imports, and the helper:

```java
    private static Map<String, LayoutPoint> mergeDisplacements(final Map<String, LayoutPoint> boundary,
            final Map<String, LayoutPoint> edge) {
        final Map<String, LayoutPoint> result = new java.util.TreeMap<String, LayoutPoint>();
        for (final Map.Entry<String, LayoutPoint> entry : boundary.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, LayoutPoint> entry : edge.entrySet()) {
            final LayoutPoint previous = result.get(entry.getKey());
            result.put(entry.getKey(), previous == null ? entry.getValue()
                : LayoutPoint.of(previous.x() + entry.getValue().x(),
                    previous.y() + entry.getValue().y()));
        }
        return new java.util.LinkedHashMap<String, LayoutPoint>(result);
    }
```

`EMPTY_FAILED_FRAME` becomes
`LayoutFrame.withDiagnostics(LayoutFrame.of(0L, LayoutPositions.of(...), true, 0), 0,
BoundarySeparationDiagnostics.empty(), EdgeConflictDiagnostics.empty(),
PerceptualIdlePolicy.IdleMeasurement.initial())` and `failedFrame` passes
`retained.residualViolations()`, `retained.boundaryDiagnostics()`,
`retained.edgeConflictDiagnostics()` and `retained.idle()`. In
`LayoutSettleLoop.failedFrame` (761) pass the same four values from `retained`.

- [ ] **Step 5: Run the composition and settle-loop suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould" \
  --tests "org.freeplane.plugin.graph.control.LayoutSettleLoopShould" \
  --tests "org.freeplane.plugin.graph.control.GraphUpdateCoordinatorShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; `LayoutWorkerShould.decorateConsecutiveFramesWithDisplacementDeltas`
stays green on the merged map.

- [ ] **Step 6: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java
git commit -m "feat(graph): compose the edge stage and carry edge diagnostics on the frame [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 8: Move CanvasState to the EdgePaths-taking factory and route at publish

**Implementer tier:** Advanced
**Lane:** edge-publication
**Depends on:** Tasks 5, 6, 7

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/CanvasState.java:1-56`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:545-552,720-728`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java:134-138,558-564`
- Modify every `CanvasState.of` call site in the plugin test tree:
  `control/LayoutSettleLoopShould.java:1374`, `command/ContributorDeletionPlanShould.java:856`,
  `integration/GraphWorkspaceCommandAcceptanceShould.java:785,1230`,
  `smoke/GraphWorkspaceUiEvidence.java:281`,
  `window/GraphWorkspaceWindowModelShould.java:2618,2910,2928,2938,2953,2982,2995,3024,3048`,
  `window/WorkspaceDialogsShould.java:438,481,510`,
  `canvas/AccessibleGraphCanvasShould.java:660,667,791`,
  `canvas/GraphInteractionControllerShould.java:341,617,761,831,889,1030`,
  `canvas/GraphCanvasPaintShould.java:121,159,180,237,440,458,727,734,814,1149,1233,1300,1334,1349,1363,1613,1642,1677,1764`,
  `canvas/GraphSearchModelShould.java:185`,
  `performance/GraphWorkspacePerformanceDiagnostic.java:843`.

**Interfaces:**

- Consumes: `EdgePaths.empty()`/`of(...)` from Task 5; `EdgeRouter.route(...)` from Task 6;
  `LayoutSettleLoop`'s `geometryEngine.computeHulls(run.projection, frame.positions(),
  metrics)` result and `ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(),
  projection.enclosures())`.
- Produces: exactly the spec §2.5 API: the five-argument `CanvasState.of` is removed and
  the single factory is
  `public static CanvasState of(long generation, GraphProjection projection, LayoutFrame
  layout, GraphGeometry geometry, EdgePaths edgePaths, OperationalStatus status)`;
  `public EdgePaths edgePaths()`; `CanvasState withStatus(OperationalStatus nextStatus)`
  preserving `edgePaths`.

- [ ] **Step 1: Write the failing publish test**

Append to `LayoutSettleLoopShould` a test that publishes a two-node/two-edge projection
through the existing harness and asserts the canvas state carries routed paths:

```java
    @Test
    public void publishTheFramesOwnRoutedEdgePaths() throws Exception {
        CanvasState state = publishTwoEdgeFrame();

        assertThat(state.edgePaths().byEdge()).isNotEmpty();
        assertThat(state.edgePaths().diagnostics().edgesStraight()
            + state.edgePaths().diagnostics().edgesBow()
            + state.edgePaths().diagnostics().edgesDogleg()
            + state.edgePaths().diagnostics().edgesFallback())
            .isEqualTo(state.edgePaths().byEdge().size());
        assertThat(state.edgePaths()).isSameAs(state.withStatus(OperationalStatus.FAILED).edgePaths());
    }
```

`publishTwoEdgeFrame()` reuses the class's existing publish harness with a projection
carrying two visible edges, exactly as the existing `CanvasState.of` fixture at
`LayoutSettleLoopShould.java:1374` is built. The failure-publish path must be exercised
too: assert that the failed state's `edgePaths()` is non-null and that
`CanvasState.of(...)` no longer compiles with five arguments (the compile error in Step 2
proves the removal).

- [ ] **Step 2: Run the test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.control.LayoutSettleLoopShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava` (`edgePaths()` missing) and, before the call sites
move, a compile error for every five-argument `CanvasState.of`.

- [ ] **Step 3: Change CanvasState**

Add the `EdgePaths edgePaths` field after `geometry`, `Objects.requireNonNull` it, add
the accessor, change `withStatus` to `new CanvasState(generation, projection, layout,
geometry, edgePaths, nextStatus)` (package-private visibility unchanged), and replace
`of` with the six-argument factory. Import
`org.freeplane.plugin.graph.geometry.EdgePaths`.

- [ ] **Step 4: Route at both publish sites**

In `LayoutSettleLoop.handleFrame`, after `geometryEngine.computeHulls(...)`:

```java
            final Set<ProjectedEndpointKey> visibleEndpoints =
                ProjectedEndpointVisibility.visibleEndpoints(run.projection.nodes(),
                    run.projection.enclosures());
            final EdgePaths edgePaths = new EdgeRouter().route(run.projection, frame.positions(),
                geometry, visibleEndpoints);
            final CanvasState state = CanvasState.of(run.batch.generation(), run.projection, frame,
                geometry, edgePaths, status);
```

In `fail(...)`, route `failed.positions()` through the same call and pass the resulting
`EdgePaths` to `CanvasState.of(...)` with `OperationalStatus.FAILED`. Add the imports
for `EdgePaths`, `EdgeRouter`, `ProjectedEndpointKey` and `ProjectedEndpointVisibility`;
keep one `EdgeRouter` instance per `LayoutSettleLoop` is **not** required (it is
stateless), but do not cache a per-frame result outside the published state.

In `GraphUpdateCoordinator.initializeState`, pass `EdgePaths.empty()`; in
`publishFailure`, pass `state.edgePaths()`.

- [ ] **Step 5: Move every remaining call site**

At each file/line in the **Files** list add the `EdgePaths` argument:
fixtures with drawn edges call `new EdgeRouter().route(projection, positions, geometry,
ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures()))`
so the painted geometry is the routed geometry; fixtures without edges pass
`EdgePaths.empty()`. For the three `GraphWorkspaceWindowModelShould` and three
`WorkspaceDialogsShould` sites, and the `command/ContributorDeletionPlanShould` and
`smoke/GraphWorkspaceUiEvidence` sites, `EdgePaths.empty()` is correct because they assert
generation/status/window behaviour, not painted geometry. For
`GraphCanvasPaintShould`, `GraphInteractionControllerShould`, `AccessibleGraphCanvasShould`
and `performance/GraphWorkspacePerformanceDiagnostic` use the router call for every
fixture that draws edges. Verify the exhaustive list with:

```bash
cd "$(git rev-parse --show-toplevel)"
grep -rn "CanvasState\.of(" freeplane_plugin_graph/src | grep -v "edgePaths" || echo "all call sites carry EdgePaths"
```

Expected: `all call sites carry EdgePaths`.

- [ ] **Step 6: Run the affected suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.control.LayoutSettleLoopShould" \
  --tests "org.freeplane.plugin.graph.control.GraphUpdateCoordinatorShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" \
  --tests "org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`, with one routed-path assertion listening at
`state.edgePaths()`.

- [ ] **Step 7: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/CanvasState.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
git commit -m "feat(graph): publish routed EdgePaths on CanvasState at both publish sites [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 9: Draw and hit-test the published EdgePaths

**Implementer tier:** Advanced
**Lane:** edge-publication
**Depends on:** Task 8

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:122-219`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:95-180,380-393`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1019-1039,228,248`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:66-152,345-355`

**Interfaces:**

- Consumes: `CanvasState.edgePaths()` from Task 8; `EdgePath.samples()`,
  `.arrowAnchor(boolean)`, `.terminalSegmentLength(boolean)`, `.arcLengthMidpoint()` from
  Task 5; `ProjectedEdge.key()`, `.arrowAtFirst()`, `.arrowAtSecond()`,
  `.hasMultiplicityCue()`, `.contributorCount()`.
- Produces: `GraphPainter.paintEdges` draws exactly the published
  `EdgePath.samples()` polyline per visible edge and draws each arrowhead from
  `arrowAnchor(...)` to the attachment with
  `availableArrowHeadWorldLength(path.terminalSegmentLength(atStart), arrowAtFirst,
  arrowAtSecond)` and the unchanged `effectiveArrowHeadLengthPx` floor/growth rule;
  multiplicity is drawn at `arcLengthMidpoint()`; `GraphHitIndex.EdgeEntry` stores the
  `EdgePath` and `edgeAt(point, tolerance)` returns the canonical edge key with the
  minimum exact segment distance over consecutive `samples()` pairs.

- [ ] **Step 1: Write the failing painter and hit-test tests**

Add to `GraphCanvasPaintShould`, near the arrowhead tests at 1019-1039:

```java
    @Test
    public void drawThePublishedSamplesForABowAndKeepArrowheadsOnTheTerminalSegment() {
        GraphCanvasPaintShould.PaintFixture fixture = bowFixture();
        assertThat(paintedPathSampleCount(fixture)).isEqualTo(
            fixture.state.edgePaths().path(fixture.edgeKey).get().samples().size());
        assertThat(paintedArrowFrom(fixture, false))
            .isEqualTo(fixture.state.edgePaths().path(fixture.edgeKey).get().arrowAnchor(false));
    }
```

where `bowFixture()` builds a state whose router result contains one bow (for example
the §8.2 `ID_1–ID_2` geometry; reuse `EdgeRouterFixture` through the test-source package
boundary, or inline the exact coordinates), `paintedPathSampleCount` records the number
of `Path2D` segments the edge painter emits through the existing recording
`Graphics2D`, and `paintedArrowFrom` records the arrow's `moveTo` point. If the class
has no recording graphics, capture with the same technique the existing
`GraphCanvasPaintShould` arrow tests use. Keep the assertion on
`arrowAnchor(false)` (the control point) with no tolerance.

In `GraphInteractionControllerShould`, extend the edge hit tests at 66-152 with:

```java
    @Test
    public void hitTestARoutedBowAtItsSampledNearestPoint() {
        CanvasState state = bowState();
        EdgePath path = state.edgePaths().path(state.projection().edges().get(0).key()).get();
        LayoutPoint midpointSample = path.samples().get(path.samples().size() / 2);

        assertThat(index(state).edgeAt(midpointSample, 0.5)).contains(
            state.projection().edges().get(0).key());
        assertThat(index(state).edgeAt(LayoutPoint.of(midpointSample.x() + 50.0,
            midpointSample.y() + 50.0), 0.5)).isEmpty();
    }
```

Reuse the class's existing `edgeAt`/index harness and the same tolerance unit
(world units) as the existing straight-edge assertions at 345-355.

- [ ] **Step 2: Run the tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" \
  -PTestLoggingFull
```

Expected: FAIL: the painter still draws `Line2D(first, second)` from re-derived
attachments (no samples path) and the edge hit index still stores two points.

- [ ] **Step 3: Draw the published paths**

In `paintEdges`, replace the attachment/`Line2D` block with:

```java
            final EdgePath path = state.edgePaths().path(edge.key()).orElse(null);
            if (path == null) {
                continue;
            }
            final boolean related = isRelated(edge.first(), paintState) || isRelated(edge.second(), paintState);
            final AlphaComposite oldComposite = setOpacity(graphics, dimUnrelated && !related);
            graphics.setColor(theme.edgeColor());
            graphics.setStroke(theme.edgeStroke());
            graphics.draw(polyline(path.samples()));
            if (showArrowheads && edge.arrowAtFirst()) {
                paintArrow(graphics, path.start(), path.arrowAnchor(true), theme.edgeColor(), viewport.zoom(),
                    edge.arrowAtFirst(), edge.arrowAtSecond(), path.terminalSegmentLength(true));
            }
            if (showArrowheads && edge.arrowAtSecond()) {
                paintArrow(graphics, path.end(), path.arrowAnchor(false), theme.edgeColor(), viewport.zoom(),
                    edge.arrowAtFirst(), edge.arrowAtSecond(), path.terminalSegmentLength(false));
            }
            if (edge.hasMultiplicityCue()) {
                paintMultiplicity(graphics, path.arcLengthMidpoint(), edge.contributorCount(), theme,
                    viewport.zoom());
            }
            graphics.setComposite(oldComposite);
```

`polyline(List<LayoutPoint>)` builds a `Path2D.Double` through every sample (two points
for straight); `paintArrow` takes the terminal length instead of recomputing
`distance(tip, from)` and keeps the current direction math, floor and halving:

```java
    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip,
            final LayoutPoint from, final Color color, final double zoom, final boolean arrowAtFirst,
            final boolean arrowAtSecond, final double terminalSegmentLength) {
        final double dx = tip.x() - from.x();
        final double dy = tip.y() - from.y();
        final double length = Math.sqrt(dx * dx + dy * dy);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return;
        }
        final double availableWorldLength = availableArrowHeadWorldLength(terminalSegmentLength,
            arrowAtFirst, arrowAtSecond);
        // unchanged: unit vector from `from` to `tip`, effectiveArrowHeadLengthPx(zoom,
        // availableWorldLength), arrowHeadHalfBasePx, triangle fill
    }
```

`paintMultiplicity` takes the already-computed point:

```java
    private static void paintMultiplicity(final Graphics2D graphics, final LayoutPoint midpoint,
            final int count, final GraphTheme theme, final double zoom) {
        final Font font = theme.denseLabelFont().deriveFont(Math.max(1.0f, 9.0f / (float) zoom));
        graphics.setFont(font);
        graphics.setColor(theme.labelColor());
        graphics.drawString("x" + count, (float) (midpoint.x() + 4.0 / zoom),
            (float) (midpoint.y() - 4.0 / zoom));
    }
```

`GraphPainter.availableArrowHeadWorldLength`, `arrowHeadLengthPx`,
`effectiveArrowHeadLengthPx` and `arrowHeadHalfBasePx` keep their current signatures and
semantics because the existing helper test at 1019-1039 must stay green.

- [ ] **Step 4: Hit-test the published paths**

In `GraphHitIndex.from`, replace the attachment computation for each visible edge with
`EdgePaths paths = state.edgePaths(); EdgePath path = paths.path(edge.key()).orElse(null);
if (path == null) continue; edgeEntries.add(new EdgeEntry(edge.key(), path));` (keep the
visibility filter and the canonical projection order). In `edgeAt`, replace the
point-to-segment call with the minimum over consecutive `samples()` pairs:

```java
            DistanceValue bestPathDistance = null;
            for (int index = 0; index + 1 < edge.path.samples().size(); index++) {
                final DistanceValue distance = pointToSegmentDistance(point,
                    edge.path.samples().get(index), edge.path.samples().get(index + 1));
                if (distance.isFinite() && (bestPathDistance == null
                        || distance.compareTo(bestPathDistance) < 0)) {
                    bestPathDistance = distance;
                }
            }
            if (bestPathDistance == null) {
                continue;
            }
            if (bestPathDistance.compareToSquared(toleranceSquared) > 0) {
                continue;
            }
```

then keep the existing `(distance, canonical key)` tie-break (`edge.key.compareTo(best)`).
`EdgeEntry` keeps `key` and stores `EdgePath path`; a zero-length sample pair falls back
to endpoint distance through the existing `lengthSquared.signum() == 0` branch.

- [ ] **Step 5: Run the painter and hit-test suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the straight-path hit tests at 66-152/345-355 are
unchanged and the new sampled-path assertions pass.

- [ ] **Step 6: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
git commit -m "feat(graph): paint and hit-test the published EdgePaths sample list [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 10: Add the red-phase relocation fixtures (spec §8.1 and §8.5)

**Implementer tier:** Standard
**Lane:** edge-relocation
**Depends on:** Tasks 4, 7

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`

**Interfaces:**

- Consumes: `EdgeConflictCorrection.apply(...)`, `EdgeConflictResult.diagnostics()`,
  `EdgeConflictDiagnostics.Repair`, `.RepairKind.SWAP`, `.rounds()`, `.verifications()`,
  `.crossingsDetected()`, `.edgeOverNodeDetected()`, `.crossingsResolved()`,
  `.edgeOverNodeResolved()`, `.conflictsResidual()`, `.repairsApplied()`,
  `.indexBudgetExhausted()`, `.indexSubdivisions()`, `.indexPairChecks()`
  (Task 4); `BoundarySeparationResult` (Task 3);
  `ProjectedEndpointVisibility.visibleEndpoints(...)`; `ConnectorDescriptor.of(SourceNodeKey,
  NodeReference, boolean, boolean, String, String, String)` and
  `EdgeContributor.nativeConnector(ConnectorSnapshot.of(int, ConnectorDescriptor),
  ProjectedEndpointKey, ProjectedEndpointKey)` (see
  `GraphCanvasPaintShould.directedEdge`).
- Produces: the reported-fixture tests
  `swapsTheReportedConvexQuadAndRemovesTheCrossing`,
  `rejectsTheTwoAdjacentSwapBatchAndRetriesSingles`,
  `retriesASingleCandidateAfterARejectedBatch`, the extreme-span test
  `detectsACrossingAtExtremeSpans`, and the negative test
  `enumeratesEveryPairWhenSubdivisionExhausts`. On the Task 4 scaffold every one of them
  fails; Tasks 13 and 14 turn them green.

- [ ] **Step 1: Add the fixture builder and the five tests**

Append to `EdgeConflictCorrectionShould` a private `Fixture` type and these five tests.
`Fixture` builds a hand-made `GraphGeometry` with drawn radius `14.0` per node (so the
C2 threshold is exactly `radius + EDGE_NODE_CLEARANCE = 17.0`), an empty hull map, and a
`BoundarySeparationResult` whose positions are the supplied centres:

```java
    @Test
    public void swapsTheReportedConvexQuadAndRemovesTheCrossing() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().edgeOverNodeDetected()).isZero();
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = result.diagnostics().repairsApplied().get(0);
        assertThat(repair.kind()).isEqualTo(EdgeConflictDiagnostics.RepairKind.SWAP);
        assertThat(repair.nodeKeys()).containsExactly(fixture.key(0), fixture.key(1));
        assertThat(repair.fromCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(repair.toCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(repair.fromCentres().get(fixture.key(1))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(repair.toCentres().get(fixture.key(1))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(result.positions().nodes().get(fixture.key(2))).isEqualTo(LayoutPoint.of(275.0, 240.0));
        assertThat(result.positions().nodes().get(fixture.key(3))).isEqualTo(LayoutPoint.of(240.0, 208.0));
        assertThat(result.diagnostics().crossingsResolved()).isEqualTo(1);
        assertThat(result.diagnostics().edgeOverNodeResolved()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void rejectsTheTwoAdjacentSwapBatchAndRetriesSingles() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        // Greedy batch = {swap01, swap23}; applying both leaves the two diagonals, so A1's
        // strict decrease fails, and the single swap01 is accepted.
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verifications()).isEqualTo(2);
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void retriesASingleCandidateAfterARejectedBatch() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        // A whole-batch discard (probe 10) would leave rounds == 0 and a residual crossing.
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        assertThat(result.diagnostics().repairsApplied().get(0).kind())
            .isEqualTo(EdgeConflictDiagnostics.RepairKind.SWAP);
        assertThat(result.diagnostics().crossingsResolved()).isEqualTo(1);
    }

    @Test
    public void detectsACrossingAtExtremeSpans() {
        final Fixture fixture = fixture(1L, new String[] {"A", "B", "C", "D"},
            new double[] {-1e300, -1e300, 1e300, 1e300, -1e300, 1e300, 1e300, -1e300},
            new int[][] {{0, 1}, {2, 3}});
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().indexBudgetExhausted()).isTrue();
    }

    @Test
    public void enumeratesEveryPairWhenSubdivisionExhausts() {
        final int chords = 65;
        final double[] x = new double[chords * 2];
        final double[] y = new double[chords * 2];
        for (int index = 0; index < chords; index++) {
            final double theta = 2.0 * Math.PI * index / (double) chords;
            x[index * 2] = 100.5 * Math.cos(theta);
            y[index * 2] = 100.5 * Math.sin(theta);
            x[index * 2 + 1] = -100.5 * Math.cos(theta);
            y[index * 2 + 1] = -100.5 * Math.sin(theta);
        }
        final int[][] edges = new int[chords][];
        for (int index = 0; index < chords; index++) {
            edges[index] = new int[] {index * 2, index * 2 + 1};
        }
        final String[] ids = new String[chords * 2];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = "C" + index;
        }
        final Fixture fixture = fixture(1L, ids, x, y, edges, 14.0);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(2080);
        assertThat(result.diagnostics().indexSubdivisions()).isEqualTo(4);
        assertThat(result.diagnostics().indexBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().indexPairChecks()).isGreaterThanOrEqualTo(2080L);
    }
```

The fixture helper (private in the test class) is exactly:

```java
    private static final class Fixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final List<ProjectedNodeKey> keys;

        private Fixture(GraphProjection projection, BoundarySeparationResult boundary,
                Set<ProjectedEndpointKey> visible, List<ProjectedNodeKey> keys) { ... }

        private ProjectedNodeKey key(int index) { return keys.get(index); }
    }

    private static EdgeConflictResult apply(final Fixture fixture) {
        return new EdgeConflictCorrection().apply(fixture.projection, fixture.boundary.positions(),
            METRICS, Collections.<PinProjection>emptyList(), fixture.visible, fixture.boundary);
    }

    private static Fixture reportedQuad() {
        return fixture(1L, new String[] {"ID_A", "ID_B", "ID_C", "ID_D"},
            new double[] {272.0, 170.0, 307.0, 202.0, 275.0, 240.0, 240.0, 208.0},
            new int[][] {{0, 2}, {1, 3}});
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] coordinates,
            final int[][] edges) {
        return fixture(generation, ids, coordinates, edges, 14.0);
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] coordinates,
            final int[][] edges, final double radius) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (int index = 0; index < ids.length; index++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
                NodeReference.of(MAP, PersistedNodeId.of(ids[index]))));
            final LayoutPoint point = LayoutPoint.of(coordinates[index * 2], coordinates[index * 2 + 1]);
            keys.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(ids[index], ids[index]), "map", false));
            points.put(key, point);
            nodeGeometry.put(key, NodeGeometry.of(point, radius));
        }
        final List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        for (final int[] edge : edges) {
            final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(keys.get(edge[0]));
            final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(keys.get(edge[1]));
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(
                first.node().get().source(), NodeReference.of(second.mapReferenceId(),
                    PersistedNodeId.of(ids[edge[1]])), false, false, "s", "m", "t");
            projectedEdges.add(ProjectedEdge.of(ProjectedEdgeKey.of(first, second),
                Collections.singletonList(EdgeContributor.nativeConnector(
                    ConnectorSnapshot.of(0, descriptor), first, second))));
        }
        final GraphProjection projection = GraphProjection.projected(generation, nodes,
            Collections.<ProjectedEnclosure>emptyList(), projectedEdges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        final LayoutPositions positions = LayoutPositions.of(points,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        final GraphGeometry geometry = GraphGeometry.of(nodeGeometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        return new Fixture(projection, boundary, visible, keys);
    }
```

Add the missing imports (`GraphProjection`, `ProjectedEdge`, `ProjectedEdgeKey`,
`ProjectedEndpointKey`, `ProjectedEndpointVisibility`, `ProjectedEnclosure`,
`ProjectedNode`, `RelationshipResolution`, `ConnectorDescriptor`, `ConnectorSnapshot`,
`EdgeContributor`, `SafeNodeLabel`, `SourceNodeKey`, `NodeReference`, `PersistedNodeId`,
`MapReferenceId`, `NodeGeometry`, `GraphGeometryEngine` is not needed because the
geometry is hand-made, `PinProjection`, `Set`, `List`, `Map`, `ArrayList`,
`LinkedHashMap`, `Collections`).

- [ ] **Step 2: Run the five tests against the scaffold and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: FAIL, and the failures are exactly the scaffold behaviour:
`swapsTheReportedConvexQuadAndRemovesTheCrossing` fails at
`assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1)` with `expected: 1 but
was: 0`; `rejectsTheTwoAdjacentSwapBatchAndRetriesSingles` and
`retriesASingleCandidateAfterARejectedBatch` fail on `rounds` / `repairsApplied`;
`detectsACrossingAtExtremeSpans` fails on `crossingsDetected`;
`enumeratesEveryPairWhenSubdivisionExhausts` fails on `crossingsDetected`. Record the
observed failures; this is the mandated red phase of spec §8.6 and the tests are
committed in this failing state on purpose.

- [ ] **Step 3: Confirm the rest of the class stays green**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.returnsTheBoundaryResultUnchangedInTheScaffold" \
  -PTestLoggingFull
```

Expected: PASS — the only scaffold test stays green while the new fixtures are red.

- [ ] **Step 4: Commit the red phase**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "test(graph): add the failing reported-fixture and index fixtures [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 11: Add the red-phase router fixtures (spec §8.2 and §8.3)

**Implementer tier:** Standard
**Lane:** edge-routing
**Depends on:** Tasks 6, 9

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java`

**Interfaces:**

- Consumes: `EdgeRouter.route(...)`, `EdgePaths.byEdge()/diagnostics()`,
  `EdgePath.Shape`, `EdgeRouteDiagnostics.edgesBow()/edgesStraight()/edgesFallback()/
  pathCrossingsDetected()/pathCrossingsResidual()`, `EdgeRouterFixture.laterChordBow()`
  and `.fanCrossing()` from Task 6; `ROUTE_LENGTH_CAP = 1.25` and `BOW_SAMPLE_TOLERANCE =
  0.75` from Tasks 5, 6 and 16.
- Produces: the §8.2 test `routesTheFirstEdgeAroundALaterChordsCrossing`, the §8.3 tests
  `waivesTheDistanceRequirementInsideTheFanRegion` and
  `rejectsAProperCrossingInsideTheFanRegion`, and the rung-cap test
  `rejectsTheDeadDoglegRungAndFallsBack`. All four fail on the straight-only scaffold and
  are turned green by Task 16.

- [ ] **Step 1: Add the four tests with their exact expected values**

Append to `EdgeRouterShould`:

```java
    @Test
    public void routesTheFirstEdgeAroundALaterChordsCrossing() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath first = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_1", "ID_2"));
        final EdgePath second = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_3", "ID_4"));

        assertThat(first.shape()).isEqualTo(EdgePath.Shape.BOW);
        assertThat(first.control()).contains(LayoutPoint.of(0.0, 90.0));
        assertThat(first.samples()).hasSize(9);
        assertThat(first.samples().get(0).x()).isEqualTo(-80.100505, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.samples().get(0).y()).isEqualTo(9.899495, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.samples().get(4).y()).isEqualTo(49.949747, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.length()).isLessThanOrEqualTo(1.25 * 180.0);
        assertThat(first.length()).isCloseTo(183.58, org.assertj.core.data.Offset.offset(0.05));
        assertThat(second.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().edgesBow()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesFallback()).isZero();
        assertThat(paths.diagnostics().pathCrossingsDetected()).isEqualTo(1);
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void waivesTheDistanceRequirementInsideTheFanRegion() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.diagnostics().edgesFallback()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(1);
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
        assertThat(paths.diagnostics().pathHullPenetrations()).isZero();
    }

    @Test
    public void rejectsAProperCrossingInsideTheFanRegion() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath first = paths.byEdge().get(EdgeRouterFixture.exitEdge("X", "A"));

        // The widest negative bow would cross X-B's chord near (12.55, 7.65) inside the fan
        // region; a mutation that waives the crossing ban there accepts it.
        assertThat(first.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void rejectsTheDeadDoglegRungAndFallsBack() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        // The same-side dogleg 0.50 has analytic ratio 1.535184 > ROUTE_LENGTH_CAP = 1.25 and
        // must never be selected as a live rung.
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
    }
```

- [ ] **Step 2: Run the four tests against the scaffold and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  -PTestLoggingFull
```

Expected: FAIL on the straight-only scaffold:
`routesTheFirstEdgeAroundALaterChordsCrossing` fails at
`assertThat(first.shape()).isEqualTo(EdgePath.Shape.BOW)` with `expected: BOW but was:
STRAIGHT`; `waivesTheDistanceRequirementInsideTheFanRegion` fails at
`assertThat(paths.diagnostics().edgesFallback()).isEqualTo(1)` with `expected: 1 but was:
0`; the crossing-ban and dead-rung assertions fail for the same reason. Record the
observed failures.

- [ ] **Step 3: Commit the red phase**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java
git commit -m "test(graph): add the failing later-chord and fan-crossing router fixtures [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 12: Add the red-phase painter and rebuild fixtures

**Implementer tier:** Standard
**Lane:** edge-publication
**Depends on:** Tasks 9, 10

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`

**Interfaces:**

- Consumes: the Task 10 reported fixture (`ID_A` 272/170, `ID_B` 307/202, `ID_C` 275/240,
  `ID_D` 240/208; edges `ID_A–ID_C` and `ID_B–ID_D`); `CanvasState.edgePaths()`;
  `GraphPainter`'s routed path draw from Task 9; `LayoutWorker.accept` composition from
  Task 7; `BoundarySeparationDiagnostics` stage fields `hullViolationsDetected()`,
  `rounds()`, `displacementRms()`, `displacementMax()`, `deltaRms()`, `deltaMax()`,
  `appliedDisplacements()`.
- Produces: `GraphCanvasPaintShould.paintsNoCrossingForTheReportedFixture` and
  `LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild`. Both fail on the
  scaffold and are turned green by Tasks 13-15.

- [ ] **Step 1: Add the two failing tests**

`GraphCanvasPaintShould.paintsNoCrossingForTheReportedFixture` builds the reported
fixture's `CanvasState` through `new EdgeRouter().route(...)` over the four measured
centres and asserts that no two painted edge polylines properly cross. Reuse the
class's image-recording helper if one exists; otherwise assert on the published paths
plus the paint call count:

```java
    @Test
    public void paintsNoCrossingForTheReportedFixture() {
        CanvasState state = reportedQuadState();
        EdgePaths paths = state.edgePaths();

        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
        int crossings = 0;
        List<EdgePath> values = new ArrayList<EdgePath>(paths.byEdge().values());
        for (int first = 0; first < values.size(); first++) {
            for (int second = first + 1; second < values.size(); second++) {
                if (EdgeRouter.properlyCross(values.get(first), values.get(second))) {
                    crossings++;
                }
            }
        }
        assertThat(crossings).isZero();
        assertThat(paintEdgeCount(state)).isEqualTo(2);
    }
```

On the scaffold (straight router, no relocation) the crossing is still present, so the
first assertion fails with one residual key.

`LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild` runs the worker on a
two-node fixture whose boundary stage changes a position (the existing
`decoration`/`deltas` fixture in that class may be reused), then asserts on the published
`LayoutFrame`:

```java
    @Test
    public void preservesBoundaryStageFieldsAcrossTheRebuild() {
        LayoutFrame frame = publishRelocationFixture();

        assertThat(frame.edgeConflictDiagnostics().rounds()).isGreaterThan(0);
        assertThat(frame.boundaryDiagnostics().hullViolationsDetected())
            .isEqualTo(boundaryBaselineHullViolationsDetected());
        assertThat(frame.boundaryDiagnostics().rounds())
            .isEqualTo(boundaryBaselineRounds());
        assertThat(frame.boundaryDiagnostics().displacementRms())
            .isEqualTo(boundaryBaselineDisplacementRms());
        assertThat(frame.boundaryDiagnostics().displacementMax())
            .isEqualTo(boundaryBaselineDisplacementMax());
        assertThat(frame.boundaryDiagnostics().deltaRms())
            .isEqualTo(boundaryBaselineDeltaRms());
        assertThat(frame.boundaryDiagnostics().deltaMax())
            .isEqualTo(boundaryBaselineDeltaMax());
        assertThat(frame.boundaryDiagnostics().appliedDisplacements())
            .isEqualTo(boundaryBaselineAppliedDisplacements());
    }
```

where the `boundaryBaseline*()` helpers capture the boundary stage's own values from the
same input run (the class's existing fixture already computes a boundary result; expose
it or recompute with `new BoundarySeparationCorrection().apply(...)`). On the scaffold
`edgeConflictDiagnostics().rounds()` is `0` for every frame, so the first assertion fails.

Add two more failing publication tests to `LayoutWorkerShould` for the same relocation
fixture:

```java
    @Test
    public void retainsEdgeDiagnosticsOnFailureFrames() {
        LayoutFrame valid = publishRelocationFixture();
        assertThat(valid.edgeConflictDiagnostics().rounds()).isGreaterThan(0);

        LayoutFrame failed = failAfter(valid);

        assertThat(failed.failed()).isTrue();
        assertThat(failed.edgeConflictDiagnostics()).isEqualTo(valid.edgeConflictDiagnostics());
        assertThat(failed.boundaryDiagnostics()).isEqualTo(valid.boundaryDiagnostics());
    }

    @Test
    public void mergedRelocationDisplacementsEnterTheDeltaAccounting() {
        LayoutFrame first = publishRelocationFixture();
        LayoutFrame second = publishRelocationFixture();

        assertThat(first.boundaryDiagnostics().deltaMax()).isGreaterThan(0.0);
        assertThat(second.boundaryDiagnostics().deltaRms())
            .isEqualTo(second.boundaryDiagnostics().deltaRms());
        assertThat(lastAppliedDisplacements()).containsKeysInAnyOrder(
            lastBoundaryAppliedDisplacements().keySet().toArray(new String[0]));
    }
```

`failAfter(LayoutFrame)` reuses the class's failure harness (an engine that throws on the
next step) so the retained `lastValidFrame()` is the relocation frame; both tests fail on
the scaffold because `rounds()` is `0`.

- [ ] **Step 2: Run the two tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould.paintsNoCrossingForTheReportedFixture" \
  --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild" \
  -PTestLoggingFull
```

Expected: FAIL, `expected: 0 but was: 1` on the crossing count and
`expected: greater than 0 but was: 0` on the relocation rounds. Record the observed
failures.

- [ ] **Step 3: Commit the red phase**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java
git commit -m "test(graph): add the failing painted-crossing and rebuilt-record fixtures [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 13: Implement C1/C2 conflict detection and the subdividing conflict index

**Implementer tier:** Advanced
**Lane:** edge-relocation
**Depends on:** Tasks 1, 3, 10

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`

**Interfaces:**

- Consumes: `CanonicalKeys.edge`, `.edgePair`, `.edgeNodePair`, `.node`, `.nodePair`
  (Task 1); `BoundarySeparationResult.geometry()` (Task 3);
  `GraphGeometry.nodes()/hulls()/edgeAttachment(...)`,
  `NodeGeometry.center()/radius()`, `HullGeometry.labelAnchor()`,
  `ProjectedEndpointVisibility.visibleEndpoints(...)`, `ProjectedEdge.first()/second()/key()`,
  `ProjectedNode.key()`, `ProjectedEndpointKey.isNode()/node()`;
  `BoundaryInvariants.nodeHullsByKey(projection)`,
  `BoundaryInvariants.changedHulls(...)` (Task 2).
- Produces (all on `EdgeConflictCorrection`, package-private so the layout test can call
  them): `static final double EDGE_NODE_CLEARANCE = 3.0`,
  `static final double DRAWN_BASE_RADIUS = 8.0`,
  `static final double CONFLICT_CELL_SIZE = 48.0`,
  `static final double CONFLICT_MIN_CELL_SIZE = 3.0`,
  `static final int CONFLICT_CELL_CAP = 64`,
  `static final int CONFLICT_MAX_SUBDIVISION_DEPTH = 4`,
  `static final int OUTERMOST_CELL_INDEX = Integer.MAX_VALUE`,
  `static final double ROUTE_EPSILON = 1e-9`;
  `static Detection detectConflicts(GraphProjection, LayoutPositions, GraphGeometry,
  Set<ProjectedEndpointKey>)`, where `Detection` is a package-private static nested type
  with `List<String> keys()` (ascending, deduplicated), `int crossings()`,
  `int edgeOverNodes()`; a package-private `ConflictIndex` type with the counted-work
  accessors `insertedItems()`, `pairChecks()`, `subdivisions()`,
  `budgetExhausted()` and constructor `ConflictIndex()`; a package-private instance
  method `IndexedDetection detect(projection, positions, geometry, visibleEndpoints)` that
  runs `detectConflicts` through the index and accumulates the counts on the correction
  instance. `EdgeConflictDiagnostics`/`EdgeConflictResult` from Task 4 stay unchanged.

- [ ] **Step 1: Extend the failing test expectations into detection tests**

The Task 10 tests `detectsACrossingAtExtremeSpans` and
`enumeratesEveryPairWhenSubdivisionExhausts` drive this task. Add three more tests to
`EdgeConflictCorrectionShould`:

```java
    @Test
    public void reportsTheReportedFixtureCountsBeforeRepair() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictCorrection.Detection detection = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(detection.keys()).hasSize(1);
        assertThat(detection.crossings()).isEqualTo(1);
        assertThat(detection.edgeOverNodes()).isZero();
        assertThat(detection.keys().get(0)).isEqualTo(org.freeplane.plugin.graph.projection.CanonicalKeys
            .edgePair(fixture.projection.edges().get(0).key(), fixture.projection.edges().get(1).key()));
    }

    @Test
    public void ignoresAnInvisibleEdgeForC2() {
        final Fixture fixture = fixture(1L, new String[] {"A", "B"},
            new double[] {0.0, 0.0, 100.0, 0.0}, new int[][] {{0, 1}});
        final Set<org.freeplane.plugin.graph.projection.ProjectedEndpointKey> visible =
            Collections.emptySet();

        final EdgeConflictCorrection.Detection detection = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(), visible);

        assertThat(detection.keys()).isEmpty();
    }

    @Test
    public void detectConflictsIsAscendingDeduplicatedAndDeterministic() {
        final Fixture fixture = reportedQuad();
        final List<String> first = EdgeConflictCorrection.detectConflicts(fixture.projection,
            fixture.boundary.positions(), fixture.boundary.geometry(), fixture.visible).keys();
        final List<String> second = EdgeConflictCorrection.detectConflicts(fixture.projection,
            fixture.boundary.positions(), fixture.boundary.geometry(), fixture.visible).keys();

        assertThat(first).isSorted();
        assertThat(first).isEqualTo(second);
    }
```

- [ ] **Step 2: Run the detection tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava`, `cannot find symbol` for
`EdgeConflictCorrection.Detection` / `detectConflicts`.

- [ ] **Step 3: Implement the detection**

`detectConflicts` builds the drawn chords and the inflated discs, inserts them through
`ConflictIndex`, enumerates the candidate pairs and returns the canonical keys.

### 3a. The straight drawn chord

For a projected edge with both endpoints in `visibleEndpoints`:
`firstAnchor = anchor(first)` and `secondAnchor = anchor(second)` where `anchor(endpoint)`
is `geometry.nodes().get(nodeKey).center()` for a node endpoint and
`positions.anchors().get(hullKey)` falling back to `hull.labelAnchor()` when the anchor is
missing or non-finite for an enclosure endpoint. `attachmentFirst =
geometry.edgeAttachment(first, secondAnchor)` and `attachmentSecond =
geometry.edgeAttachment(second, firstAnchor)`. If any of the four values is unavailable,
the edge is not drawn and takes part in no conflict. Chords are open: zero-length and
touch-only contacts are not conflicts.

### 3b. C1 and C2

C1: two drawn chords properly cross. Implement the exact sign-only predicate of spec
§3.2 (there is no epsilon): one common exponent `e` over all eight coordinates of the two
chords, scale every coordinate with `Math.scalb(value, -e)` (so every scaled coordinate
has magnitude `< 2` and every difference `< 4`), compute
`cross(a, b, c) = (bx-ax)*(cy-ay) - (by-ay)*(cx-ax)` with `a, b` the chord and `c` the
opposite chord's endpoint, map a zero cross to sign `0` and otherwise to
`Double.compare(cross, 0.0)`, and require both endpoint-sign pairs to have opposite
nonzero signs. Two chords sharing an endpoint never satisfy the strict predicate because
their shared point is collinear; do not special-case them. The identity key is
`CanonicalKeys.edgePair(first.key(), second.key())`.

C2: for a drawn chord and a projected node whose disc exists in `geometry.nodes()`, whose
key is not one of the edge's own node endpoints, and whose disc is not attached to the
edge: `(e, n)` is a conflict iff
`segmentDistance(chord, center(n)) - radius(n) < EDGE_NODE_CLEARANCE - ROUTE_EPSILON`.
`segmentDistance` is the exact point-to-closed-segment distance (a zero-length chord is a
point distance). The identity key is `CanonicalKeys.edgeNodePair(edge.key(), node)` (N1),
which distinguishes two passed-over nodes on one edge. The visibility filter is part of
the predicate. A node endpoint that is an enclosure source is never a C2 target unless it
also appears in `projection.nodes()`.

### 3c. The conflict index

`ConflictIndex` is a uniform grid over chords and inflated discs, built once per detection
pass:

1. **Origin and normalisation.** `originX` = the minimum bounding-box `x` over every
   inserted primitive (chord endpoints; inflated-disc `center.x - radius(n) -
   EDGE_NODE_CLEARANCE`), `originY` likewise. For level `L` in
   `[0, CONFLICT_MAX_SUBDIVISION_DEPTH]`, `cellSize = CONFLICT_CELL_SIZE / 2^L` and
   `cellX(x) = clamp(floor((x - originX) / cellSize))`, `clamp(v) = min(v,
   OUTERMOST_CELL_INDEX)` for finite `v >= 0`; `floor(-0.0) = 0`. The 64-bit key is
   `((long) cellX << 32) | (cellY & 0xffffffffL)`; both indices are in `[0, 2^31-1]`, so
   the key is injective. Build the level by starting at `0`; while any occupied cell's
   inserted-item count exceeds `CONFLICT_CELL_CAP` and `L <
   CONFLICT_MAX_SUBDIVISION_DEPTH`, increment `L` and recount from scratch. A cell that
   subdivides is replaced by its four children and every primitive inserted in it is
   re-inserted in every child it traverses/overlaps; subdivision never truncates. If a
   cell at the maximum level still exceeds the cap, enumerate its pairs in full and set
   `budgetExhausted`.
2. **Insertion.** A chord is inserted into every cell its segment actually traverses (a
   segment-cell traversal, not its bounding box); an inflated disc into every cell it
   overlaps. A primitive whose `cellX` or `cellY` clamps to `OUTERMOST_CELL_INDEX` is
   recorded in the level's `outermost` set and is **not inserted into any cell**: if
   either chord endpoint clamps, or either bounding-box corner of an inflated disc
   clamps, cell insertion for that primitive is skipped entirely. After the level's
   enumeration, every primitive in `outermost` is paired directly against all other
   primitives it can conflict with, and the pass sets `budgetExhausted`.
3. **Enumeration and counted work.** C1 pairs are enumerated only from chords that share
   a cell; C2 pairs only from a chord and an inflated disc that share a cell. Accumulate
   keys in a `TreeSet<String>` and emit once in ascending order; a pair found in several
   cells is emitted once. `indexInsertedItems` increments once per primitive per cell
   accepted at the final level plus once per direct outermost check; `indexPairChecks`
   increments once per examined primitive pair (chord-chord and chord-disc, after
   canonical-key deduplication); `indexSubdivisions` increments once per over-cap
   `(cell, level)` that was split. Counts accumulate over all detection passes of the
   frame; no full hull computation is performed by the index.

- [ ] **Step 4: Run the relocation index tests and confirm they pass**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: `reportsTheReportedFixtureCountsBeforeRepair`,
`ignoresAnInvisibleEdgeForC2`, `detectsACrossingAtExtremeSpans` and
`enumeratesEveryPairWhenSubdivisionExhausts` pass. The reported-fixture repair tests
(`swapsTheReportedConvexQuadAndRemovesTheCrossing`,
`rejectsTheTwoAdjacentSwapBatchAndRetriesSingles`,
`retriesASingleCandidateAfterARejectedBatch`) still fail because candidates are Task 14.

- [ ] **Step 5: Prove the outermost-ring fallback (probe 15)**

Mutate the index so a clamped primitive is not added to the `outermost` direct pass
(insert it nowhere and skip the direct pairing). Run
`detectsACrossingAtExtremeSpans` with `-PTestLoggingFull`. Expected: the test fails
because the two 1e300 diagonals are no longer paired (`crossingsDetected` is `0`).
Restore the file with `git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
and confirm `git diff` is empty for that file. Record the probe outcome. This is probe 15
of spec §8.7.

- [ ] **Step 6: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "feat(graph): detect C1/C2 conflicts through a subdividing conflict index [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 14: Implement the candidate ladder, A1-A4 and rejection-retry

**Implementer tier:** Advanced
**Lane:** edge-relocation
**Depends on:** Tasks 3, 13

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`

**Interfaces:**

- Consumes: `BoundaryInvariants.detectPairKeys(GraphProjection, GraphGeometry)` and
  `.detect(projection, geometry)` (Task 3); `GraphGeometryEngine.recomputeHulls(projection,
  candidatePositions, metrics, currentGeometry, BoundaryInvariants.changedHulls(
  BoundaryInvariants.nodeHullsByKey(projection), currentPositions, candidatePositions))`;
  `NodeSeparationProjection.violatingPairs(GraphProjection, LayoutPositions)` (Task 3);
  `projection.enclosures()` with `directNodes()`, `parentHull()`, `boundaryTier()` for the
  A4 membership path; `BoundarySeparationCorrection.pinnedNodes` filtering rule
  (`pin.active()` and a present projected node).
- Produces: on `EdgeConflictCorrection`, the constants
  `static final double CROSSING_CLEARANCE = 16.0`,
  `static final int MAX_RELOCATION_ROUNDS = 4`,
  `static final int MAX_VERIFICATIONS_PER_FRAME = 64`; the candidate ladder of spec §5.2;
  A1-A4 acceptance; the rejection-retry rule; the round loop of spec §5.3; and a populated
  `EdgeConflictDiagnostics` with `conflictsDetected`, `crossingsDetected`,
  `edgeOverNodeDetected`, `crossingsResolved`, `edgeOverNodeResolved`,
  `conflictsResidual`, `repairsApplied`, `rounds`, `verifications`,
  `verificationBudgetExhausted`, `indexBudgetExhausted` and the counted index work.

- [ ] **Step 1: Add the A3 and determinism tests**

Append to `EdgeConflictCorrectionShould`:

```java
    @Test
    public void vetoesACandidateThatAddsASiblingHullViolation() {
        final SiblingFixture fixture = siblingOverlapFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(result.diagnostics().conflictsResidual())
            .containsExactly(org.freeplane.plugin.graph.projection.CanonicalKeys.edgeNodePair(
                fixture.edgeKey, fixture.passedOverNode));
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.positions().nodes().get(fixture.passedOverNode))
            .isEqualTo(fixture.initialPassedOverCentre);
    }

    @Test
    public void keepsAPreExistingHullResidualWhenTheCandidateDoesNotAddOne() {
        final SiblingFixture fixture = siblingOverlapFixture();
        final BoundarySeparationResult boundary = fixture.boundaryWithResidual(
            org.freeplane.plugin.graph.projection.CanonicalKeys.hullPair(
                fixture.siblingFirst, fixture.siblingSecond));
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            boundary.positions(), METRICS, Collections.<PinProjection>emptyList(), fixture.visible, boundary);

        // A3 compares pair-key subsets, so a legal pre-existing residual never vetoes the repair.
        assertThat(result.diagnostics().rounds()).isGreaterThanOrEqualTo(0);
        assertThat(result.diagnostics().conflictsResidual()).doesNotContain(
            org.freeplane.plugin.graph.projection.CanonicalKeys.hullPair(
                fixture.siblingFirst, fixture.siblingSecond));
    }

    @Test
    public void appliesCandidatesInCanonicalOrder() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        final List<EdgeConflictDiagnostics.Repair> repairs = result.diagnostics().repairsApplied();
        for (int index = 1; index < repairs.size(); index++) {
            assertThat(repairs.get(index - 1).batchIndex()).isLessThan(repairs.get(index).batchIndex());
        }
        assertThat(result.diagnostics().repairsApplied().get(0).nodeKeys().get(0))
            .isEqualTo(fixture.key(0));
    }

    @Test
    public void vetoesACandidateThatWouldBreakMinGap() {
        final MinGapFixture fixture = minGapFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(org.freeplane.plugin.graph.layout.NodeSeparationProjection.violatingPairs(
            fixture.projection, result.positions())).contains(
                org.freeplane.plugin.graph.projection.CanonicalKeys.nodePair(
                    fixture.passedOverNode, fixture.bystanderNode));
    }

    @Test
    public void movesThePassedOverNodeOffTheChordWithTheLexicographicallySmallerNormal() {
        final C2Fixture fixture = c2FixtureWithPositiveProjection();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = result.diagnostics().repairsApplied().get(0);
        assertThat(repair.kind()).isEqualTo(EdgeConflictDiagnostics.RepairKind.TRANSLATION);
        assertThat(repair.nodeKeys()).containsExactly(fixture.passedOverNode);
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
        // Flipping the normal direction (probe 16) would leave |2s - (R + 3)| < R + 3.
        assertThat(result.positions().nodes().get(fixture.passedOverNode))
            .isNotEqualTo(fixture.initialPassedOverCentre);
    }

    @Test
    public void isDeterministicAcrossRunsAndPermutedInputs() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult first = apply(fixture);

        final EdgeConflictResult second = new EdgeConflictCorrection().apply(fixture.permutedProjection(),
            fixture.permutedPositions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.permutedBoundary());

        assertThat(second.positions()).isEqualTo(first.positions());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }
```

Add these private fixtures: `siblingOverlapFixture()` implementing spec §8.4 exactly
(root enclosure `R`, sibling children `A`/`C`, nodes `a = (10, 0)`, `c = (-58, 0)`,
`p = (0, 100)`, `q = (0, -100)`, edge `p–q`, anchors zero); `minGapFixture()` placing the
C2 candidate's landing position within `MIN_GAP` of a bystander disc; `c2FixtureWithPositiveProjection()`
placing the passed-over node so `s = (centre - chordPoint)·n*` is positive. These fixtures
use the same hand-built geometry and boundary-result helpers as Task 10; `Fixture` gains
`permutedProjection()/permutedPositions()/permutedBoundary()` that rebuild the same
content with the nodes/edges lists reversed (the geometry keyed by the same keys).

- [ ] **Step 2: Run the new tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: FAIL on the scaffold for every new test (no candidates, `rounds == 0`).

- [ ] **Step 3: Implement the candidate ladder (spec §5.2)**

A repair moves node centres only; enclosure anchors (`positions.anchors()`) are never
modified (N8).

**All-node C1.** For a C1 whose four endpoints are all projected nodes, order the four
endpoint keys `K0 < K1 < K2 < K3` ascending by `CanonicalKeys.node`. Compute the centroid
`O` of the four centres; sort the four keys by `atan2(centre.y - O.y, centre.x - O.x)`
ascending, breaking exact angle ties by `CanonicalKeys.node`; rotate the cycle to start at
`K0`, giving `P0..P3`. Candidates in this fixed order:

1. **Adjacent swap `P0<->P1`**: exchange the two nodes' centre positions; the moved set is
   exactly those two.
2. **Adjacent swap `P2<->P3`**: the other adjacent pair.
3. **Mirror `M(P0)`, `M(P1)`, `M(P2)`, `M(P3)`** in ascending index order. For `M(Pi)`:
   `L` is the supporting line of `Pi`'s opposite chord through the two centres of that
   chord's endpoints; `n` is either unit normal; `s = (centre(Pi) - a)·n` for any `a` on
   `L`; `c' = centre(Pi) - 2s·n`; `n_out = s < 0 ? n : -n`; the final centre is
   `c' + CROSSING_CLEARANCE·n_out`; the moved set is exactly `{Pi}`. The postcondition is
   distance `|s| + 16.0 >= 16.0 > 14.0` from `L`.

**Mixed C1** (at least one enclosure endpoint): no candidates; any candidate that would
move an enclosure endpoint is discarded before evaluation. **C2:** translate the
passed-over node `n` along the lexicographically smaller unit normal `n*` of `chord(e)`
(the `(-dy, dx)/len` or its exact negation, whichever `(nx, ny)` sorts first by
`Double.compare(nx)` then `Double.compare(ny)`); with `R = radius(n)`, `a` on the chord
line and `s = (centre(n) - a)·n*`, set
`centre(n) = centre(n) + (R + EDGE_NODE_CLEARANCE - s)·n*`. Zero-length chords produce no
C2 candidate. A pinned passed-over node yields no candidate.

**Discards before evaluation:** a candidate is discarded iff it moves a pinned node
(active pins filtered exactly as `BoundarySeparationCorrection.pinnedNodes`), a swap's two
nodes have different projection enclosure path membership, or it would move any enclosure
endpoint. Discarded candidates are not counted as verifications. Batch assembly: scan
candidates in the order above and greedily add each whose moved-node set does not
intersect the accumulated set.

- [ ] **Step 4: Implement A1-A4 and the pinned round loop**

Verification of an attempt:

- **A1**: `C_out ⊆ C_in` and `|C_out| < |C_in|`, where both sets are the union of C1
  (`edgePair`) and C2 (`edgeNodePair`) identity keys; the formats are disjoint (N1).
- **A2**: `NodeSeparationProjection.violatingPairs(projection, candidatePositions)`
  is a subset of `violatingPairs(projection, currentPositions)` compared as key sets; disc
  radii are the same rule as the boundary stage.
- **A3**: `V_in = boundaryInvariants.detectPairKeys(projection, currentGeometry)`, re-derived
  from the current positions every round, and
  `V_out = detectPairKeys(projection, recomputeHulls(projection, candidatePositions, metrics,
  currentGeometry, changedHulls(nodeHullsByKey, currentPositions, candidatePositions)))`;
  accept only if `V_out ⊆ V_in` compared by pair keys.
- **A4**: every moved node keeps its projection enclosure path (the ascending
  `List<EnclosureHullKey>` before and after is equal and `candidateGeometry.hulls()`
  contains every key). A4 is an assertion that can fail only on an implementation defect.

The pinned control flow (spec §5.3) is:

```text
round = 0; acceptedAny = false; verificationBudgetExhausted = false
while (round < MAX_RELOCATION_ROUNDS && verificationCount < MAX_VERIFICATIONS_PER_FRAME) {
    conflicts = detectConflicts(currentPositions, currentGeometry)
    if (conflicts.isEmpty()) break
    pool = generateCandidates(conflicts)
    acceptedThisRound = false
    while (pool not empty && verificationCount < MAX_VERIFICATIONS_PER_FRAME) {
        batch = greedyDisjoint(pool); pool -= batch
        if (batch empty) break
        attempts = [batch] + batch.singlesInCanonicalOrder()
        for attempt in attempts {
            verificationCount++
            if (acceptsA1A2A3A4(attempt)) {
                apply(attempt); currentPositions = result; currentGeometry = recomputedGeometry
                rounds++; acceptedAny = true; acceptedThisRound = true
                recordRepair(attempt); break
            }
            if (verificationCount == MAX_VERIFICATIONS_PER_FRAME) {
                verificationBudgetExhausted = true
                break
            }
        }
        if (acceptedThisRound) break
    }
    if (!acceptedThisRound) break
}
residual = rounds > 0 ? detectConflicts(currentPositions, currentGeometry) : conflicts
if (verificationCount == MAX_VERIFICATIONS_PER_FRAME && !residual.isEmpty()) {
    verificationBudgetExhausted = true
}
```

A one-candidate batch has no separate retry (its batch evaluation is the candidate's
individual evaluation). Every batch or single attempt increments `verifications`; the
total per frame is bounded by `MAX_VERIFICATIONS_PER_FRAME`. `conflictsResidual`,
`crossingsResolved` and `edgeOverNodeResolved` are measured on the published positions.
Timings: `detectionNanos` covers every `detectConflicts` pass, `indexNanos` the index
build/query, `verificationNanos` A1-A4 except hull recompute; `rebuildNanos` is Task 15.

- [ ] **Step 5: Run the relocation suite and confirm every test passes**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the reported fixture yields `rounds == 1`,
`verifications == 2` and the `SWAP {ID_A, ID_B}` repair; the A3 sibling fixture yields
`rounds == 0` and one residual `edgeNodePair` key; the min-gap and canonical-order tests
pass; determinism holds across permuted inputs.

Add the pin and mixed-conflict coverage spec §9.1 requires for this suite:

```java
    @Test
    public void discardsCandidatesThatMoveAPinnedNode() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, fixture.pinsForAllNodes(), fixture.visible,
            fixture.boundary);

        // Every candidate moves a pinned node and is discarded before evaluation, so no
        // verification is counted and every published position equals the input (I1).
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).hasSize(1);
        for (final ProjectedNodeKey key : fixture.keys()) {
            assertThat(result.positions().nodes().get(key))
                .isEqualTo(fixture.boundary.positions().nodes().get(key));
        }
    }

    @Test
    public void leavesAMixedEnclosureConflictToRoutingWithoutMovingAnchors() {
        final MixedFixture fixture = mixedEnclosureConflictFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.diagnostics().conflictsResidual()).isNotEmpty();
        assertThat(result.positions().anchors()).isEqualTo(fixture.boundary.positions().anchors());
    }
```

`Fixture.pinsForAllNodes()` builds one active `PinProjection` per node at its input
centre. `mixedEnclosureConflictFixture()` adds a root `ProjectedEnclosure` whose direct
node is `e`, the enclosure-endpoint edge `e–x`, and the two nodes `y`/`z` with the chord
`y–z` that properly crosses the `e–x` chord; non-zero anchors are supplied so the
anchor-stability assertion is meaningful. The mixed C1 generates no candidate and no
anchor movement (N8).

- [ ] **Step 5b: Run the pin and mixed-conflict coverage**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; both tests pass with the pinned and mixed semantics above.

- [ ] **Step 6: Run the A1-A4, ordering, visibility and C2 mutation probes (probes 1, 2, 3, 4, 7, 10, 11, 16)**

For each probe: make the named single-line mutation, run the named test with
`-PTestLoggingFull`, confirm the expected failure, restore the file with
`git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`,
and confirm `git diff` is empty before the next probe.

### Probe 1: drop A1's strict decrease

Mutation: accept when `C_out` equals `C_in` as well as when it is a proper subset (drop
the `|C_out| < |C_in|` clause). Run
`rejectsTheTwoAdjacentSwapBatchAndRetriesSingles`. Expected: FAIL, because the two-swap
batch is accepted and `rounds` becomes `1` with the crossing still present
(`conflictsResidual` is non-empty and `verifications` is no longer `2`).

### Probe 2: drop A1's pair-set inclusion

Mutation: replace `C_out ⊆ C_in` with `|C_out| ≤ |C_in|`. Run
`rejectsACandidateThatIntroducesANewCrossing` (add that test in this task: a fixture
where a candidate removes one crossing and creates another equal-count crossing;
assert `rounds == 0` and that the new `edgePair` key is in `conflictsResidual`). Expected:
the new pair is accepted.

### Probe 3: drop the A2 disc-gap veto

Mutation: make A2 always pass. Run `vetoesACandidateThatWouldBreakMinGap`. Expected:
FAIL because the candidate is accepted and the min-gap pair appears in the published
positions.

### Probe 4: drop the A3 hull veto

Mutation: make A3 always pass. Run `vetoesACandidateThatAddsASiblingHullViolation`.
Expected: FAIL because the C2 translation is accepted and hull `A`/`C` overlap in the
published positions.

### Probe 7: invert the canonical candidate order

Mutation: reverse the candidate list before batch assembly (swaps before mirrors, `P3`
before `P0`). Run `appliesCandidatesInCanonicalOrder` and
`rejectsTheTwoAdjacentSwapBatchAndRetriesSingles`. Expected: FAIL on the applied repair's
node keys and on `verifications`.

### Probe 10: replace rejection-retry with whole-batch discard

Mutation: on batch rejection discard the whole batch and continue with the remaining
pool without evaluating singles. Run `retriesASingleCandidateAfterARejectedBatch` and
`swapsTheReportedConvexQuadAndRemovesTheCrossing`. Expected: FAIL with `rounds == 0` and
a residual crossing on the reported fixture.

### Probe 11: drop the C2 visibility filter

Mutation: detect C2 conflicts for edges even when an endpoint is invisible. Run
`ignoresAnInvisibleEdgeForC2`. Expected: FAIL because the invisible edge produces a C2
key.

### Probe 16: flip the C2 normal direction

Mutation: translate along `-n*` instead of `n*`. Run
`movesThePassedOverNodeOffTheChordWithTheLexicographicallySmallerNormal`. Expected: FAIL
because the disc does not clear the chord (`|2s - (R + 3)| < R + 3`).

Record each probe's mutation, the exact failing assertion and the restore result in the
task report.

- [ ] **Step 7: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "feat(graph): apply verified swaps, mirrors and translations with retry [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 15: Rebuild the boundary record on the published positions

**Implementer tier:** Advanced
**Lane:** edge-relocation
**Depends on:** Task 14

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`

**Interfaces:**

- Consumes: `BoundaryInvariants.describe(GraphProjection, LayoutPositions,
  GeometryTextMetrics, List<PinProjection>, GraphGeometry, List<String>,
  BoundarySeparationDiagnostics)` (Task 3);
  `BoundarySeparationResult.diagnostics().residualHullPairs()`; `EdgeConflictException`
  (Task 4); `NodeSeparationProjection.violatingPairs` (Task 3);
  `CanonicalKeys.node` (Task 1).
- Produces: when `rounds > 0`, `EdgeConflictResult.boundaryDiagnostics()` is the rebuilt
  record with the exact spec §5.4 field policy and `nodeResidualViolations()` is
  `new NodeSeparationProjection().project(projection, canonicallySorted(publishedPositions),
  pinnedNodes).residualViolations()`; `EdgeConflictResult.appliedDisplacements()` is the
  edge stage's own canonical field map; `EdgeConflictTimings.rebuildNanos()` covers
  `describe` plus the node residual. When `rounds == 0`, the result copies the boundary
  record unchanged and performs no rebuild.

- [ ] **Step 1: Add the rebuild tests**

Append to `EdgeConflictCorrectionShould`:

```java
    @Test
    public void rebuildsTheBoundaryRecordForThePublishedPositions() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.boundaryDiagnostics()).isNotSameAs(fixture.boundary.diagnostics());
        assertThat(result.boundaryDiagnostics().hullViolationsDetected())
            .isEqualTo(fixture.boundary.diagnostics().hullViolationsDetected());
        assertThat(result.boundaryDiagnostics().rounds())
            .isEqualTo(fixture.boundary.diagnostics().rounds());
        assertThat(result.boundaryDiagnostics().appliedDisplacements())
            .isEqualTo(fixture.boundary.diagnostics().appliedDisplacements());
        assertThat(result.boundaryDiagnostics().residualHullPairs()).isSorted();
        assertThat(result.nodeResidualViolations()).isZero();
        assertThat(result.appliedDisplacements()).isNotEmpty();
        assertThat(result.timings().rebuildNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    public void reusesTheBoundaryTerminalGeometry() {
        final Fixture fixture = reportedQuad();
        final BoundarySeparationResult poisoned = fixture.boundaryWithNodeRadius(20.0);
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            poisoned.positions(), METRICS, Collections.<PinProjection>emptyList(), fixture.visible, poisoned);

        // A fresh computeHulls would rebuild radius 8.0/14.0 discs from prominence; the
        // stage must detect against the supplied terminal geometry, whose radius is 20.0.
        assertThat(result.diagnostics().edgeOverNodeDetected()).isGreaterThanOrEqualTo(1);
    }
```

`Fixture.boundaryWithNodeRadius(double)` rebuilds the same positions with a hand-made
`GraphGeometry` whose node radii are that value.

- [ ] **Step 2: Run the two tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: `rebuildsTheBoundaryRecordForThePublishedPositions` fails at
`isNotSameAs` (the scaffold returns the boundary record) and `reusesTheBoundaryTerminalGeometry`
fails because the stage has not yet run detection against the supplied geometry.

- [ ] **Step 3: Implement the rebuild**

When `rounds > 0`:

1. `rebuilt = boundaryInvariants.describe(projection, publishedPositions, metrics, pins,
   boundaryResult.geometry(), boundaryResult.diagnostics().residualHullPairs(),
   boundaryResult.diagnostics())`. `describe`'s inclusion guard throws
   `EdgeConflictException` if `detectPairKeys` contains a pair absent from the baseline;
   that exception propagates after being wrapped (Step 4).
2. `nodeResidualViolations = new NodeSeparationProjection().project(projection,
   canonicallySorted(publishedPositions), pinnedNodes).residualViolations()`, where
   `canonicallySorted` rebuilds the node map in ascending `CanonicalKeys.node` order and
   keeps `anchors()` unchanged.
3. `appliedDisplacements` is the edge stage's own canonical field map (field key →
   componentwise sum of the accepted repairs' deltas, non-zero entries only, ascending by
   field key, exactly like `BoundarySeparationDiagnostics.appliedDisplacements`).
4. `buildNanos` accumulations wrap the describe call in `System.nanoTime()` deltas
   recorded into `rebuildNanos`; no timing value enters a diagnostics object.

When `rounds == 0`, `boundaryDiagnostics` is `boundaryResult.diagnostics()`,
`nodeResidualViolations` is `boundaryResult.nodeResidualViolations()`, and no rebuild
runs beyond the detection passes.

- [ ] **Step 4: Wrap failures as EdgeConflictException**

Wrap the whole `apply` body so any `RuntimeException` (including the describe guard)
becomes a propagating `EdgeConflictException`, exactly as `BoundarySeparationException`
does from the boundary stage; `LayoutWorker.runSubmit`/`runStep` already catch it, set
`failedEngine` and return `failedFrame(...)`. An unsatisfiable conflict is a residual,
never an error.

- [ ] **Step 5: Run the relocation and composition suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; `LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild`
from Task 12 is green.

- [ ] **Step 6: Run the rebuilt-record and terminal-geometry mutation probes (probes 13 and 14)**

### Probe 13: drop the rebuilt-record field preservation

Mutation: rebuild `conflicts`, `residualHullPairs` and `hullResidualViolations` but copy
`hullViolationsDetected = 0`, `rounds = 0` and empty displacement statistics into the
rebuilt record. Run `LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild`.
Expected: FAIL on the preserved stage-level field assertions. Restore the file and confirm
`git diff` is empty.

### Probe 14: replace terminal-geometry reuse with a fresh full computeHulls

Mutation: replace the `recomputeHulls(..., boundaryResult.geometry(), changedHulls(...))`
path with `new GraphGeometryEngine().computeHulls(projection, candidatePositions,
metrics)`. Run `EdgeConflictCorrectionShould.reusesTheBoundaryTerminalGeometry`. Expected:
FAIL because the poisoned radius-20 geometry is discarded and
`edgeOverNodeDetected` is `0`; the `edge-conflict` budget tripwire added in Task 18 is the
second half of this probe. Restore the file and confirm `git diff` is empty.

Record both probe outcomes.

- [ ] **Step 7: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "feat(graph): rebuild the boundary record for the published frame [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 16: Implement the routing ladder, clearance rules and route diagnostics

**Implementer tier:** Advanced
**Lane:** edge-routing
**Depends on:** Tasks 11, 14

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java`

**Interfaces:**

- Consumes: `EdgePath.straight/bow/dogleg`, `.samples()`, `.length()`,
  `.control()`, `.arcLengthMidpoint()` (Task 5); `EdgePaths`, `EdgeRouteDiagnostics`,
  `EdgeRouteTimings` (Task 5); `GraphGeometry.edgeAttachment(...)`,
  `NodeGeometry.radius()/center()`, `HullGeometry.exactPolygon()/contains()`,
  `ProjectedEndpointVisibility.visibleEndpoints(...)`,
  `ProjectedEdge.first()/second()/key()`, `CanonicalKeys.edge`, `.edgePair`,
  `BoundaryInvariants.parents(projection)` (N2/§6.9 clause (c)).
- Produces: the router constants
  `public static final double EDGE_NODE_CLEARANCE = 3.0`,
  `public static final double EDGE_CLEARANCE = 3.0`,
  `public static final double MAX_BEND = 96.0`,
  `public static final double ROUTE_LENGTH_CAP = 1.25`,
  `public static final double FAN_HULL_RADIUS = 16.0`,
  `public static final double ROUTE_EPSILON = 1e-9`;
  the live rung list, the clearance rules, `pathCrossingsDetected`,
  `pathCrossingsResidual` and `pathHullPenetrations` populated in
  `EdgeRouteDiagnostics`, and non-zero `EdgeRouteTimings`.

- [ ] **Step 1: Add the remaining router tests**

Append to `EdgeRouterShould`:

```java
    @Test
    public void rejectsAnAlternatingBendDogleg() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        // Both bends of every candidate lie on the same signed side; an alternating-bend
        // dogleg (ratio ~2.256) is not part of the ladder.
        for (final EdgePath path : paths.byEdge().values()) {
            if (path.shape() == EdgePath.Shape.DOGLEG) {
                final List<LayoutPoint> points = path.definingPoints();
                final double firstSide = side(points.get(0), points.get(3), points.get(1));
                final double secondSide = side(points.get(0), points.get(3), points.get(2));
                assertThat(firstSide * secondSide).isGreaterThan(0.0);
            }
        }
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void attachBowsAndDoglegsAtTheRimFacingTheRung() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath bow = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_1", "ID_2"));

        assertThat(bow.shape()).isEqualTo(EdgePath.Shape.BOW);
        final LayoutPoint control = bow.control().get();
        assertThat(distance(fixture.geometry.nodes().get(EdgeRouterFixture.node("ID_1")).center(),
            bow.start())).isEqualTo(14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(distance(fixture.geometry.nodes().get(EdgeRouterFixture.node("ID_2")).center(),
            bow.end())).isEqualTo(14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(bow.start().x()).isLessThan(control.x());
    }

    @Test
    public void keepEverySampleWithinTheSagittaTolerance() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        for (final EdgePath path : paths.byEdge().values()) {
            if (path.shape() != EdgePath.Shape.BOW) {
                continue;
            }
            final double worst = maxSagitta(path);
            assertThat(worst).isLessThanOrEqualTo(0.75);
        }
    }

    @Test
    public void isDeterministicAcrossRunsAndPermutedEdgeOrder() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths first = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePaths second = new EdgeRouter().route(fixture.permutedProjection(),
            fixture.positions, fixture.geometry, fixture.visibleEndpoints);

        assertThat(second.byEdge()).isEqualTo(first.byEdge());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }
```

`EdgeRouterFixture` gains `permutedProjection()` returning the same content with the
edges list reversed, plus package-private `side(...)`, `distance(...)` and
`maxSagitta(...)` static helpers.

- [ ] **Step 2: Run the tests and confirm the red fixtures still fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  -PTestLoggingFull
```

Expected: the Task 11 red fixtures still fail; the new tests either fail or pass
vacuously on the straight-only router.

- [ ] **Step 3: Implement the candidate ladder (spec §6.3)**

Process edges in ascending `CanonicalKeys.edge(edge.key())` order. For edge `e` the
obstacle set is every other projected edge whose two endpoints are visible (already
processed edges contribute their published `EdgePath`, including straight fallbacks;
not-yet-processed edges contribute their straight chord) plus every projected node disc
except `e`'s own endpoint discs. An edge whose endpoints are visible but whose geometry
cannot be resolved is omitted from `EdgePaths` and excluded from every other edge's
obstacle set. The ladder, in order:

1. **Straight chord** — accepted whenever it clears §6.4.
2. **Single bow** — offsets `+0.15, -0.15, +0.30, -0.30, +0.50, -0.50` times
   `chordLength` (anchor-to-anchor distance); the control point is the chord midpoint plus
   the chord normal times the offset.
3. **Two-bend dogleg** — same offsets and sign order, both bends on the same signed side:
   `bend1` at `(1/3, +s·a)` and `bend2` at `(2/3, +s·a)` in chord-normal coordinates.

A rung is skipped when its analytic ratio exceeds `ROUTE_LENGTH_CAP` (the live list is
exactly: bow 0.15/0.30/0.50 = 1.014804/1.057116/1.147794 and dogleg 0.15/0.30 =
1.064390/1.230242 live; dogleg 0.50 = 1.535184 excluded) or when `|offset| > MAX_BEND`.
Acceptance additionally requires `pathLength <= ROUTE_LENGTH_CAP * chordLength`, measured
on the sampled published path per candidate. If no rung clears, fall back to the straight
chord (`edgesFallback`, not a candidate; it may pass over discs and paths).

- [ ] **Step 4: Implement the clearance rules (spec §6.4) and path construction**

- **Discs:** every sample point and every sample segment keeps `EDGE_NODE_CLEARANCE`
  from every projected node disc it is not attached to; the rule fails iff a measured
  distance is `< radius(n) + EDGE_NODE_CLEARANCE - ROUTE_EPSILON`. Attached discs are the
  edge's own node endpoints only.
- **Other paths:** `EDGE_CLEARANCE` from every other path with the fan exemption. For two
  paths that share an endpoint `X`, each path has its own attachment at `X`; the exemption
  region `R` is the closed union of two discs, centred at the two paths' own attachments
  at `X`, each with radius `endpointClearanceRadius(X) + EDGE_CLEARANCE`, where
  `endpointClearanceRadius` is the node's drawn radius for a node endpoint and
  `FAN_HULL_RADIUS` for an enclosure endpoint. The distance requirement is waived for
  point pairs both inside `R`: a path is clear of another iff
  `minDistance(P \ R, Q) >= EDGE_CLEARANCE - ROUTE_EPSILON` **and**
  `minDistance(P, Q \ R) >= EDGE_CLEARANCE - ROUTE_EPSILON`. A **proper crossing of the
  two sample polylines is rejected everywhere**, inside or outside `R`, using the sign-only
  exponent-normalised predicate of spec §3.2 on every sample segment pair; the fan
  exemption never waives the crossing ban.
- **Hulls:** not obstacles; `pathHullPenetrations` counts `(edge, hull)` pairs where the
  published path properly crosses at least one edge of the hull's `exactPolygon()` and the
  hull contains neither endpoint. A hull contains an endpoint iff (a) the endpoint key is
  one of the hull's `endpointKeys`, (b) the endpoint's source node's projection enclosure
  path contains the hull key, or (c) the hull is an ancestor via `BoundaryInvariants.parents`
  of a hull key on that path.
- **Path construction:** compute the candidate shape between the endpoint anchors; the
  departure attachment is `geometry.edgeAttachment(first, firstWaypoint)` and the arrival
  attachment `geometry.edgeAttachment(second, lastWaypoint)`; the published path starts and
  ends at those attachments (for a bow the single waypoint is the control point, for a
  dogleg `bend1`/`bend2`). Use the single `EdgePath` sample list for clearance checking,
  hit-testing and painting.

- [ ] **Step 5: Populate the route diagnostics and timings**

`edgesStraight`, `edgesBow`, `edgesDogleg` and `edgesFallback` count the accepted shape of
each routed edge; their sum equals the number of routed edges.
`pathCrossingsDetected` counts the distinct visible edge pairs whose straight chords
properly intersect before routing; `pathCrossingsResidual` lists every proper intersection
of the published drawn paths with no region exclusion, deduplicated as ascending
`CanonicalKeys.edgePair` keys. `EdgeRouteTimings` records `indexNanos` (disc/path query
index), `selectionNanos` (ladder evaluation) and `residualNanos` (residual computation);
no timing value enters `EdgeRouteDiagnostics`.

- [ ] **Step 6: Run the router suite and confirm every test passes**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  --tests "org.freeplane.plugin.graph.geometry.EdgePathShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; §8.2 yields `BOW (0, 90)` with 9 samples, length
`183.58 <= 225`, `edgesBow == 1`, `pathCrossingsResidual` empty; §8.3 yields
`edgesFallback == 1`, `pathCrossingsResidual` empty.

- [ ] **Step 7: Run the router mutation probes (probes 5, 6, 8, 12)**

### Probe 5: drop the fan exemption

Mutation: delete the `R`-region waiver from the path-distance rule. Run
`waivesTheDistanceRequirementInsideTheFanRegion`. Expected: FAIL because `X–A` can no
longer accept its non-straight rung and `edgesFallback`/`edgesStraight` change.

### Probe 6: drop not-yet-processed-chord obstacles

Mutation: build the obstacle set only from already-published paths, skipping the straight
chords of not-yet-processed edges. Run `routesTheFirstEdgeAroundALaterChordsCrossing`.
Expected: FAIL because `ID_1–ID_2` stays straight and `pathCrossingsResidual` becomes the
crossing pair.

### Probe 8: remove the ROUTE_LENGTH_CAP fallback

Mutation: accept a rung without the `pathLength <= 1.25 * chordLength` check so the dead
dogleg 0.50 rung can be selected. Run `rejectsTheDeadDoglegRungAndFallsBack`. Expected:
FAIL if `edgesDogleg` becomes non-zero for the fan fixture; if the fixture still falls
back because of clearance, instead run the §8.2 fixture and assert the accepted path is
still the bow, then use a fixture whose only clearing rung is the dead dogleg. The probe
must show the cap clause is load-bearing.

### Probe 12: allow an alternating-bend dogleg

Mutation: place `bend2` at `(2/3, -s·a)`. Run `rejectsAnAlternatingBendDogleg`. Expected:
FAIL on the same-side product assertion.

Restore the file with `git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java`
after each probe and confirm `git diff` is empty.

- [ ] **Step 8: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java
git commit -m "feat(graph): route edges with the bounded bow/dogleg ladder [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 17: Complete the painter, arrowhead, multiplicity and hit-test coverage

**Implementer tier:** Advanced
**Lane:** edge-publication
**Depends on:** Tasks 9, 12, 16

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (only if the Task 9 draw needs the terminal-segment source factored out)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1019-1039,228,248`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:66-152,345-355`

**Interfaces:**

- Consumes: `GraphPainter.availableArrowHeadWorldLength(double, boolean, boolean)`,
  `arrowHeadLengthPx(double)`, `effectiveArrowHeadLengthPx(double, double)`,
  `arrowHeadHalfBasePx(double)` (unchanged signatures);
  `EdgePath.arrowAnchor(boolean)`, `.terminalSegmentLength(boolean)`,
  `.arcLengthMidpoint()`, `.samples()`.
- Produces: the §9.2 painter assertions: `bow` terminal source `control -> attachment`,
  `dogleg` terminal source `bend2 -> attachment`, `straight` attachment-to-attachment,
  the both-heads halving on a routed path, multiplicity at `arcLengthMidpoint()`, and
  nearest-point hit tests on routed bows and doglegs.

- [ ] **Step 1: Add the arrowhead, multiplicity and hit-test tests**

`GraphCanvasPaintShould.arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers`
stays unchanged. Add:

```java
    @Test
    public void capsTheArrowheadAtTheTerminalSegmentOfARoutedPath() {
        EdgePath bow = EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 90.0),
            LayoutPoint.of(180.0, 0.0));
        EdgePath dogleg = EdgePath.dogleg(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(10.0, 20.0),
            LayoutPoint.of(20.0, 20.0), LayoutPoint.of(30.0, 0.0));

        assertThat(bow.terminalSegmentLength(false)).isEqualTo(Math.hypot(180.0, 90.0));
        assertThat(bow.arrowAnchor(false)).isEqualTo(LayoutPoint.of(0.0, 90.0));
        assertThat(dogleg.terminalSegmentLength(false)).isEqualTo(Math.hypot(10.0, 20.0));
        assertThat(dogleg.arrowAnchor(false)).isEqualTo(LayoutPoint.of(20.0, 20.0));
        assertThat(GraphPainter.availableArrowHeadWorldLength(
            EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(40.0, 0.0))
                .terminalSegmentLength(true), true, true)).isEqualTo(20.0);
    }

    @Test
    public void drawMultiplicityAtTheArcLengthMidpointOfABow() {
        EdgePath bow = EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 90.0),
            LayoutPoint.of(180.0, 0.0));

        assertThat(bow.arcLengthMidpoint()).isNotEqualTo(
            LayoutPoint.of((bow.start().x() + bow.end().x()) * 0.5,
                (bow.start().y() + bow.end().y()) * 0.5));
    }
```

In `GraphInteractionControllerShould`, add nearest-point assertions through the existing
index harness for a dogleg (three defining points) and a bow (its sample list), asserting
that a point `0.5` world units from a middle sample hits and a point 50 units away does
not. Keep the straight behaviour at 66-152/345-355 unchanged.

- [ ] **Step 2: Run the painter and hit-test suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. If a test needs the painter to expose the arrow source,
factor the Task 9 `paintArrow` inputs into a package-private static
`arrowSource(EdgePath, boolean)` returning `arrowAnchor(atStart)` and keep the drawing
behaviour identical.

- [ ] **Step 3: Prove the terminal-segment cap (probe 9)**

Mutation: re-introduce the endpoint-to-endpoint cap by passing
`distance(tip, oppositeEnd)` instead of `path.terminalSegmentLength(atStart)` to
`availableArrowHeadWorldLength`. Run
`GraphCanvasPaintShould.capsTheArrowheadAtTheTerminalSegmentOfARoutedPath` and the Task
12 `paintsNoCrossingForTheReportedFixture`. Expected: FAIL because the bow's terminal
length is `hypot(180, 90)` while the attachment-to-attachment distance is shorter (the
mutation shrinks or grows the drawn head relative to the pinned source). Restore the file
and confirm `git diff` is empty.

- [ ] **Step 4: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
git commit -m "test(graph): pin routed arrowheads, multiplicity and sampled hit-testing [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 18: Add the edge-conflict and edge-routing performance stages and thresholds

**Implementer tier:** Advanced
**Lane:** edge-publication
**Depends on:** Tasks 14, 15, 16, 21

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java:13-70,297-338`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java:38-77,225-290,350-377`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java:90-91,253-420,840-910`
- Modify (visibility of the existing engine-supplier constructor only; no behaviour change):
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java:78-82`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixture.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixtureCompiler.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixtureShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv`
- Modify (only to tighten a calibrated ceiling below a pinned Task 21 constant when this task's calibration requires it): `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
- Modify (only to tighten a calibrated ceiling below a pinned Task 21 constant when this task's calibration requires it): `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java`

**Interfaces:**

- Consumes: `EdgeConflictTimings.detectionNanos()/indexNanos()/verificationNanos()/rebuildNanos()`
  (Tasks 4, 13-15); `EdgeRouteTimings.indexNanos()/selectionNanos()/residualNanos()`
  (Tasks 5, 16); `BoundarySeparationCorrection.apply(...)`,
  `EdgeConflictCorrection.apply(...)`, `EdgeRouter.route(...)`;
  `EdgeConflictDiagnostics.detectionTruncated()/verificationBudgetExhausted()/indexBudgetExhausted()/indexInsertedItems()/indexPairChecks()/verificationWork()/verifications()/crossingsDetected()/conflictsDetected()`
  and `EdgeRouteDiagnostics.routeBudgetExhausted()/detectionTruncated()/routeIndexItems()/routeObstacleChecks()/routePairChecks()/pathCrossingsDetected()/edgesBow()/edgesDogleg()`
  (Tasks 4, 5, 21); `GeneratedWorkspace.forScenario("reference-2000-5000")` and
  `GeneratedWorkspace.Scenario.REFERENCE_2000_5000.warmupCount()/measuredCount()`;
  `PerformanceMeasurements.Stage` wire names and threshold methods;
  `LayoutWorker(Supplier<LayoutEngine>, PerceptualIdlePolicy)` (the existing
  package-private constructor, made public in Step 5);
  `LayoutFrame.of(...)`, `PerceptualIdlePolicy.spikeDefaults()`,
  `BoundaryCorrectionProfile` as the pattern for the direct-probe timings.
- Produces: `PerformanceMeasurements.Stage.EDGE_CONFLICT("edge-conflict")` and
  `EDGE_ROUTING("edge-routing")` inserted immediately after `HULL` and before
  `PLACEMENT`; `strictThresholdNanos("reference-2000-5000", EDGE_CONFLICT) ==
  25_000_000L` and `strictThresholdNanos("reference-2000-5000", EDGE_ROUTING) ==
  10_000_000L`, every other scenario and every `normalThresholdNanos` value
  `DIAGNOSTIC_ONLY_THRESHOLD`; the pinned wire list
  `"snapshot", "projection", "diff", "mutation", "force", "correction", "separation",
  "hull", "edge-conflict", "edge-routing", "placement", "full-worker", "edt-swap",
  "repaint", "accepted-batch-first-frame"`; the named tests
  `exposeTheCanonicalStageOrderAndThresholds`,
  `referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates` and
  `referenceFixtureStaysBoundedAndFlagVisible`; `SettledReferenceFixture.load()` /
  `.newReplayWorker()` / `.request()` / `.projection()` / `.positions()` / `.pins()`;
  `SettledReferenceFixtureCompiler.compile()` / `.main(String[])`;
  `GraphWorkspacePerformanceDiagnostic.runSettledReferenceFixture()`,
  `.runRawReferenceFixture()`, `.settledReferenceSamples()`, `.rawReferenceFrames()`,
  `.rawReferenceMaxFrameNanos()` and the public record types
  `SettledReferenceSample` / `RawReferenceFrame`; the measurement-window constant
  `RAW_REFERENCE_MAX_FRAME_NANOS` (recorded and tightened in Step 7); the
  zero-exhaustion discards for the two stages; the deadline message
  `"Forty-minute diagnostic process deadline exceeded"`.

**Revision 8 measurement model (binding).** A7 is split into two inputs.
**A7a** asserts the pinned `edge-conflict` (25 ms) and `edge-routing` (10 ms) strict
thresholds, the `FULL_WORKER` p95/p99 (100 ms) and first-frame p95/p99 (150/300 ms)
`out` gates on the deterministic `settled-reference` fixture (committed
recorded-ledger positions, never produced by stepping the divergent engine), with zero
budget-exhaustion events on every measured sample and a non-trivial workload (at least
one detected crossing and at least one accepted bow or dogleg rung). **A7b** asserts
bounded, flag-visible behaviour on the raw/divergent `reference-2000-5000` frames: no
OOM, every measured frame completes within the pinned per-frame bound, every exceeded
Revision 7 budget sets its corresponding flag and the published counted work stays
within the pinned budgets; no millisecond threshold applies to those frames. The
attempt-8/9 direct-probe benchmark scaffolding is **superseded**: the seed-frame
`EDGE_CONFLICT`/`EDGE_ROUTING` recordings and the claim that the strict gates are
measured on the fresh-engine/direct-probe seed frame must not be reintroduced, and A7b
must never be read as a strict millisecond gate. Every Revision 7 budget, the four
flags, the stop semantics and the threshold table remain exactly as pinned; only the
measurement input of A7 is split.

- [ ] **Step 1: Extend the pinned tripwire test first**

In `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds`, replace the
wire-list expectation with exactly:

```java
        assertThat(PerformanceMeasurements.Stage.names()).containsExactly(
            "snapshot", "projection", "diff", "mutation", "force", "correction", "separation",
            "hull", "edge-conflict", "edge-routing", "placement", "full-worker", "edt-swap",
            "repaint", "accepted-batch-first-frame");
        assertThat(PerformanceMeasurements.strictThresholdNanos("reference-2000-5000",
            PerformanceMeasurements.Stage.EDGE_CONFLICT)).isEqualTo(25_000_000L);
        assertThat(PerformanceMeasurements.strictThresholdNanos("reference-2000-5000",
            PerformanceMeasurements.Stage.EDGE_ROUTING)).isEqualTo(10_000_000L);
        assertThat(PerformanceMeasurements.normalThresholdNanos("reference-2000-5000",
            PerformanceMeasurements.Stage.EDGE_CONFLICT))
            .isEqualTo(PerformanceMeasurements.DIAGNOSTIC_ONLY_THRESHOLD);
        assertThat(PerformanceMeasurements.normalThresholdNanos("two-map",
            PerformanceMeasurements.Stage.EDGE_ROUTING))
            .isEqualTo(PerformanceMeasurements.DIAGNOSTIC_ONLY_THRESHOLD);
```

Add the Revision 7 measurement-window pins directly after them:

```java
        assertThat(GeneratedWorkspace.Scenario.REFERENCE_2000_5000.warmupCount()).isEqualTo(400);
        assertThat(GeneratedWorkspace.Scenario.REFERENCE_2000_5000.measuredCount()).isEqualTo(300);
        assertThat(GraphWorkspacePerformanceDiagnostic.WARMUP_SAMPLES).isEqualTo(20);
        assertThat(GraphWorkspacePerformanceDiagnostic.TIMED_SAMPLES).isEqualTo(100);
```

`REFERENCE_2000_5000` is the only scenario that pins 400 warmups / 300 measured (700
samples); `WARMUP_SAMPLES = 20` and `TIMED_SAMPLES = 100` are the placement-trigger
sub-measurement counts and are unchanged. The window values remain re-pinnable with
measured costs recorded (Revision 7 §13.4); no strict threshold changes.
Make the two constants package-private in Step 5 so this compiles.

Add the two reference-fixture tests. Both read the two shared runs started once per
test class by `@BeforeClass`; A7a uses the settled fixture and a **real** clock so the
strict gates are wall-clock measurements, A7b uses the raw frames and a real clock:

```java
    @ClassRule
    public static final TemporaryFolder classFixtureFolder = new TemporaryFolder();

    private static GraphWorkspacePerformanceDiagnostic referenceDiagnostic;
    private static PerformanceMeasurements referenceMeasurements;

    @BeforeClass
    public static void runReferenceFixturesOnce() throws Exception {
        referenceDiagnostic = new GraphWorkspacePerformanceDiagnostic(
            classFixtureFolder.newFolder("reference-fixture").toPath(), false,
            new GraphWorkspacePerformanceDiagnostic.RelativeNanoClock());
        referenceMeasurements = referenceDiagnostic.runSettledReferenceFixture();
        referenceDiagnostic.runRawReferenceFixture();
    }

    @Test
    public void referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates() {
        PerformanceMeasurements.Summary worker =
            referenceMeasurements.summary(PerformanceMeasurements.Stage.FULL_WORKER);
        PerformanceMeasurements.Summary firstFrame =
            referenceMeasurements.summary(PerformanceMeasurements.Stage.ACCEPTED_BATCH_FIRST_FRAME);
        assertThat(worker.p95Nanos()).isLessThanOrEqualTo(PerformanceMeasurements.STRICT_FULL_WORKER_NANOS);
        assertThat(worker.p99Nanos()).isLessThanOrEqualTo(PerformanceMeasurements.STRICT_FULL_WORKER_NANOS);
        assertThat(firstFrame.p95Nanos())
            .isLessThanOrEqualTo(PerformanceMeasurements.STRICT_FIRST_FRAME_P95_NANOS);
        assertThat(firstFrame.p99Nanos())
            .isLessThanOrEqualTo(PerformanceMeasurements.STRICT_FIRST_FRAME_P99_NANOS);
        assertThat(referenceMeasurements.summary(PerformanceMeasurements.Stage.EDGE_CONFLICT).p95Nanos())
            .isLessThanOrEqualTo(25_000_000L);
        assertThat(referenceMeasurements.summary(PerformanceMeasurements.Stage.EDGE_ROUTING).p95Nanos())
            .isLessThanOrEqualTo(10_000_000L);
        assertThat(referenceMeasurements.measuredCount(PerformanceMeasurements.Stage.EDGE_CONFLICT))
            .isEqualTo(300);
        assertThat(referenceMeasurements.measuredCount(PerformanceMeasurements.Stage.EDGE_ROUTING))
            .isEqualTo(300);
        assertThat(referenceMeasurements.discardCount(PerformanceMeasurements.Stage.EDGE_CONFLICT)).isZero();
        assertThat(referenceMeasurements.discardCount(PerformanceMeasurements.Stage.EDGE_ROUTING)).isZero();
        assertThat(referenceDiagnostic.settledReferenceSamples()).isNotEmpty();
        for (GraphWorkspacePerformanceDiagnostic.SettledReferenceSample sample
                : referenceDiagnostic.settledReferenceSamples()) {
            assertThat(sample.edgeDiagnostics().detectionTruncated()).isFalse();
            assertThat(sample.edgeDiagnostics().verificationBudgetExhausted()).isFalse();
            assertThat(sample.edgeDiagnostics().indexBudgetExhausted()).isFalse();
            assertThat(sample.routeDiagnostics().routeBudgetExhausted()).isFalse();
            assertThat(sample.routeDiagnostics().detectionTruncated()).isFalse();
        }
        assertThat(referenceDiagnostic.settledReferenceSamples()).anySatisfy(sample ->
            assertThat(sample.edgeDiagnostics().crossingsDetected()).isGreaterThan(0));
        assertThat(referenceDiagnostic.settledReferenceSamples()).anySatisfy(sample ->
            assertThat(sample.routeDiagnostics().edgesBow()
                + sample.routeDiagnostics().edgesDogleg()).isGreaterThan(0));
    }

    @Test
    public void referenceFixtureStaysBoundedAndFlagVisible() {
        assertThat(referenceDiagnostic.rawReferenceFrames()).isNotEmpty();
        assertThat(referenceDiagnostic.rawReferenceMaxFrameNanos())
            .isLessThanOrEqualTo(GraphWorkspacePerformanceDiagnostic.RAW_REFERENCE_MAX_FRAME_NANOS);
        for (GraphWorkspacePerformanceDiagnostic.RawReferenceFrame frame
                : referenceDiagnostic.rawReferenceFrames()) {
            EdgeConflictDiagnostics edge = frame.edgeDiagnostics();
            EdgeRouteDiagnostics route = frame.routeDiagnostics();
            // The pinned Revision 7 caps of spec 13.1 (the constants are package-private in
            // the production packages; these literals are the specification pins).
            assertThat(edge.indexInsertedItems()).isLessThanOrEqualTo(262_144L);
            assertThat(edge.indexPairChecks()).isLessThanOrEqualTo(131_072L);
            assertThat(edge.verificationWork()).isLessThanOrEqualTo(65_536L);
            assertThat(route.routeIndexItems()).isLessThanOrEqualTo(262_144L);
            assertThat(route.routeObstacleChecks()).isLessThanOrEqualTo(131_072L);
            assertThat(route.routePairChecks()).isLessThanOrEqualTo(131_072L);
            // Whenever a budget is exhausted the corresponding flag is set.
            if (edge.indexInsertedItems() == 262_144L) {
                assertThat(edge.detectionTruncated() || edge.indexBudgetExhausted()).isTrue();
            }
            if (edge.indexPairChecks() == 131_072L) {
                assertThat(edge.detectionTruncated()).isTrue();
            }
            if (edge.verificationWork() == 65_536L) {
                assertThat(edge.verificationBudgetExhausted()).isTrue();
            }
            if (route.routeIndexItems() == 262_144L || route.routeObstacleChecks() == 131_072L) {
                assertThat(route.routeBudgetExhausted()).isTrue();
            }
            if (route.routePairChecks() == 131_072L) {
                assertThat(route.detectionTruncated()).isTrue();
            }
        }
    }
```

Add the imports `org.junit.BeforeClass`, `org.junit.ClassRule`,
`org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics` and
`org.freeplane.plugin.graph.geometry.EdgeRouteDiagnostics`. If
`GraphWorkspacePerformanceDiagnostic` has no settled/raw entry points yet, Steps 4-5
add them below; do not weaken the assertions. The shared runs happen once per test
class, not once per test.

The §8.5/§13.5 bounded-policy fixtures — item-budget and pair-budget truncation, the
volume fixture, the verification-detection stop, the router obstacle-budget fallback and
the residual-pass lower bound, with their counted-work bounds — are Task 21's and must
not be duplicated here. `PerformanceTripwiresShould` keeps the suite-level grid-encoding
coverage spec §9.2 names (the hub/star frame and the geometry-suite extreme coordinates)
by asserting the same values as Task 13's
`EdgeConflictCorrectionShould.detectsACrossingAtExtremeSpans` and
`.enumeratesEveryPairWhenSubdivisionExhausts`.

- [ ] **Step 2: Run the tripwire test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.performance.PerformanceTripwiresShould" \
  -PTestLoggingFull
```

Expected: FAIL at `exposeTheCanonicalStageOrderAndThresholds` with the missing
`edge-conflict`/`edge-routing` names and the unknown enum constants in the new tests.

- [ ] **Step 3: Add the stages and thresholds**

In `PerformanceMeasurements.Stage`, insert:

```java
        EDGE_CONFLICT("edge-conflict"),
        EDGE_ROUTING("edge-routing"),
```

immediately after `HULL("hull")` and before `PLACEMENT("placement")`. In
`strictThresholdNanos`, inside the `reference-2000-5000` branch, add:

```java
        case EDGE_CONFLICT:
            return 25_000_000L;
        case EDGE_ROUTING:
            return 10_000_000L;
```

`normalThresholdNanos` keeps returning `DIAGNOSTIC_ONLY_THRESHOLD` for both new stages
(the `default` branch). Do not change any existing threshold constant, and do not add a
feature flag: a first measurement above a pinned strict threshold is a hard failure that
forces a design revision, and the only permitted constant change is a tightening applied
to the specification and to this test in the same change.

- [ ] **Step 4: Build the deterministic `settled-reference` fixture (A7a)**

Create `SettledReferenceFixtureCompiler` as a test-only, **offline** generator. It is
never called by any test at measurement time: it computes the recorded ledger once, the
ledger is committed as a resource, and the tests only parse it and verify its digest.
The construction recipe is fixed and deterministic.

**Size calibration (binding).** The fixture must be the largest deterministic layout
that stays inside every Revision 7 budget with zero exhaustion while carrying a
non-trivial workload (at least one detected crossing and at least one accepted bow or
dogleg rung). A full-scale 2,000/5,000 ledger is **not** required and is measured to be
infeasible in-budget (its conflict volume exceeds the detected-key, index-item and
router caps). Start from the full reference projection and deterministically reduce it -
drop the highest-index nodes and their incident edges in ascending `CanonicalKeys.node`
order - until the counted work of a complete detection and a complete routing pass stays
under every cap with margin. Record the chosen node/edge counts in the ledger header and
in the task report. The fixture's resource name records provenance, not a hard scale.

The construction recipe is fixed and deterministic:

1. Build `GeneratedWorkspace.forScenario("reference-2000-5000").projection()` and order
   the projected nodes ascending by `CanonicalKeys.node`.
2. Seed node `i` with the lattice position `((i % 50) * 100, (i / 50) * 100)`.
3. Run exactly `SETTLED_RELAXATION_ITERATIONS = 400` relaxation passes with the pinned
   Fruchterman-Reingold routine below: repulsion `k^2 / d` between every node pair
   within `2k` through a deterministic bucket grid, attraction `d^2 / k` along every
   node-node edge (the reference graph has no enclosure endpoints), per-node displacement
   capped at a linearly cooling temperature `8 * 100 * (1 - pass / iterations)`.
   The pass count, the edge order and the arithmetic are fixed and there is no RNG and
   no engine, so the compiler always terminates in a fixed time.
4. Scale node coordinates by `SETTLED_SCALE` (initial `4.1`; see the Step 6 tuning
   rule) and derive each enclosure anchor as the arithmetic mean of its
   `directNodes()` positions (zero when it has none).
5. Emit rows in ascending key order as `kind,key,x,y`, where `kind` is `node` or
   `anchor`, the key is `CanonicalKeys.node(key)` or `CanonicalKeys.hull(hullKey)`, and
   the coordinates use `%.17g` so the ledger is byte-stable. Every projected node is
   recorded and every node is pinned by the fixture request (Step 5), so the frame is an
   immovable fixed point: a "settled" frame by construction.
6. Write the bytes to
   `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv`
   and print its SHA-256.

**Why it cannot diverge.** The committed ledger is static data; the test
(`SettledReferenceFixture.load()`) only reads bytes, verifies the SHA-256 pin and maps
each row to a `ProjectedNodeKey`/`EnclosureHullKey`; no `LayoutEngine`, no `step()` and
no iteration run at test time. Even the compiler is bounded: it performs a fixed 400
passes with clamped displacement, never an open-ended convergence loop, and it is a
dedicated test-only relaxation, not the production `GraphStreamLayoutEngine`. The
recorded frame is bounded (`Double.isFinite` on every coordinate) because the attract
and repel updates are finite and clamped.

The exact routine (fill in the enclosing class and constants):

```java
    static final int SETTLED_RELAXATION_ITERATIONS = 400;
    static final double SETTLED_SCALE = 4.1;
    static final int LATTICE_COLUMNS = 50;
    static final double LATTICE_SPACING = 100.0;

    static byte[] compile(final GraphProjection projection) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(projection.nodes());
        Collections.sort(nodes, new Comparator<ProjectedNode>() {
            @Override
            public int compare(final ProjectedNode first, final ProjectedNode second) {
                return CanonicalKeys.node(first.key()).compareTo(CanonicalKeys.node(second.key()));
            }
        });
        final int count = nodes.size();
        final Map<ProjectedNodeKey, Integer> indexByKey = new LinkedHashMap<ProjectedNodeKey, Integer>();
        for (int index = 0; index < count; index++) {
            indexByKey.put(nodes.get(index).key(), Integer.valueOf(index));
        }
        final double[] x = new double[count];
        final double[] y = new double[count];
        for (int index = 0; index < count; index++) {
            x[index] = (index % LATTICE_COLUMNS) * LATTICE_SPACING;
            y[index] = (index / LATTICE_COLUMNS) * LATTICE_SPACING;
        }
        final List<int[]> edges = new ArrayList<int[]>();
        for (final ProjectedEdge edge : projection.edges()) {
            if (!edge.first().isNode() || !edge.second().isNode()) {
                continue;
            }
            final Integer first = indexByKey.get(edge.first().node().get());
            final Integer second = indexByKey.get(edge.second().node().get());
            if (first != null && second != null && first.intValue() != second.intValue()) {
                edges.add(new int[] { first.intValue(), second.intValue() });
            }
        }
        final double area = (LATTICE_COLUMNS * LATTICE_SPACING)
            * ((count / LATTICE_COLUMNS + 1) * LATTICE_SPACING);
        final double k = Math.sqrt(area / count);
        final double[] displacementX = new double[count];
        final double[] displacementY = new double[count];
        for (int pass = 0; pass < SETTLED_RELAXATION_ITERATIONS; pass++) {
            final double temperature = 8.0 * LATTICE_SPACING
                * (1.0 - (double) pass / SETTLED_RELAXATION_ITERATIONS);
            Arrays.fill(displacementX, 0.0);
            Arrays.fill(displacementY, 0.0);
            final double bucketSize = 2.0 * k;
            final Map<Long, List<Integer>> buckets = new HashMap<Long, List<Integer>>();
            for (int index = 0; index < count; index++) {
                final long bucketKey = (((long) Math.floor(x[index] / bucketSize)) << 32)
                    | ((long) Math.floor(y[index] / bucketSize) & 0xffffffffL);
                List<Integer> bucket = buckets.get(Long.valueOf(bucketKey));
                if (bucket == null) {
                    bucket = new ArrayList<Integer>();
                    buckets.put(Long.valueOf(bucketKey), bucket);
                }
                bucket.add(Integer.valueOf(index));
            }
            for (int index = 0; index < count; index++) {
                final int cellX = (int) Math.floor(x[index] / bucketSize);
                final int cellY = (int) Math.floor(y[index] / bucketSize);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        final List<Integer> bucket = buckets.get(Long.valueOf(
                            (((long) (cellX + dx)) << 32) | ((cellY + dy) & 0xffffffffL)));
                        if (bucket == null) {
                            continue;
                        }
                        for (final Integer other : bucket) {
                            final int peer = other.intValue();
                            if (peer == index) {
                                continue;
                            }
                            double deltaX = x[index] - x[peer];
                            double deltaY = y[index] - y[peer];
                            double distance = Math.hypot(deltaX, deltaY);
                            if (distance < 1e-9) {
                                deltaX = ((index & 1) == 0 ? 1.0 : -1.0) * 0.01;
                                deltaY = 0.01;
                                distance = Math.hypot(deltaX, deltaY);
                            }
                            final double force = (k * k) / distance / 2.0;
                            displacementX[index] += deltaX / distance * force;
                            displacementY[index] += deltaY / distance * force;
                        }
                    }
                }
            }
            for (final int[] edge : edges) {
                final int first = edge[0];
                final int second = edge[1];
                final double deltaX = x[first] - x[second];
                final double deltaY = y[first] - y[second];
                final double distance = Math.max(1e-9, Math.hypot(deltaX, deltaY));
                final double force = (distance * distance) / k / 2.0;
                displacementX[first] -= deltaX / distance * force;
                displacementY[first] -= deltaY / distance * force;
                displacementX[second] += deltaX / distance * force;
                displacementY[second] += deltaY / distance * force;
            }
            for (int index = 0; index < count; index++) {
                final double length = Math.hypot(displacementX[index], displacementY[index]);
                if (length < 1e-12) {
                    continue;
                }
                final double limited = Math.min(length, temperature) / length;
                x[index] += displacementX[index] * limited;
                y[index] += displacementY[index] * limited;
            }
        }
        final StringBuilder csv = new StringBuilder("kind,key,x,y\n");
        for (int index = 0; index < count; index++) {
            csv.append("node,").append(CanonicalKeys.node(nodes.get(index).key())).append(',')
                .append(String.format(Locale.ROOT, "%.17g", x[index] * SETTLED_SCALE)).append(',')
                .append(String.format(Locale.ROOT, "%.17g", y[index] * SETTLED_SCALE)).append('\n');
        }
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>(projection.enclosures());
        Collections.sort(enclosures, new Comparator<ProjectedEnclosure>() {
            @Override
            public int compare(final ProjectedEnclosure first, final ProjectedEnclosure second) {
                return CanonicalKeys.hull(first.hullKey()).compareTo(CanonicalKeys.hull(second.hullKey()));
            }
        });
        for (final ProjectedEnclosure enclosure : enclosures) {
            double sumX = 0.0;
            double sumY = 0.0;
            int direct = 0;
            for (final ProjectedNodeKey key : enclosure.directNodes()) {
                final Integer index = indexByKey.get(key);
                if (index != null) {
                    sumX += x[index.intValue()] * SETTLED_SCALE;
                    sumY += y[index.intValue()] * SETTLED_SCALE;
                    direct++;
                }
            }
            csv.append("anchor,").append(CanonicalKeys.hull(enclosure.hullKey())).append(',')
                .append(String.format(Locale.ROOT, "%.17g", direct == 0 ? 0.0 : sumX / direct)).append(',')
                .append(String.format(Locale.ROOT, "%.17g", direct == 0 ? 0.0 : sumY / direct)).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
```

`main(String[] args)` writes `compile(new GeneratedWorkspace(...).projection())` to the
resource path above. Run it once in the task worktree:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:testClasses
GRAPH_REGENERATE_SETTLED_FIXTURE=1 gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould" \
  -PTestLoggingFull
```

`SettledReferenceFixtureShould` (created in this step) regenerates the committed ledger
**byte-for-byte** when `GRAPH_REGENERATE_SETTLED_FIXTURE=1` and otherwise asserts that
`compile(...)` equals the committed resource bytes and that the resource SHA-256 equals
the pinned constant:

```java
    @Test
    public void theCommittedLedgerIsTheDeterministicCompilationOfTheReferenceProjection() throws Exception {
        GeneratedWorkspace workspace = GeneratedWorkspace.forScenario("reference-2000-5000");
        byte[] expected = SettledReferenceFixtureCompiler.compile(workspace.projection());
        if ("1".equals(System.getenv("GRAPH_REGENERATE_SETTLED_FIXTURE"))) {
            Path resource = repositoryFile(
                "freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv");
            Files.createDirectories(resource.getParent());
            Files.write(resource, expected);
        }
        byte[] committed = SettledReferenceFixture.readCommittedLedger();
        assertThat(committed).isEqualTo(expected);
        assertThat(SettledReferenceFixture.sha256(committed))
            .isEqualTo(SettledReferenceFixture.SETTLED_FIXTURE_SHA256);
    }

    @Test
    public void theCommittedLedgerCoversTheReferenceProjectionExactly() {
        SettledReferenceFixture fixture = SettledReferenceFixture.load();
        assertThat(fixture.positions().nodes()).hasSize(2000);
        assertThat(fixture.positions().anchors()).hasSize(1200);
        assertThat(fixture.positions().nodes().keySet()).containsExactlyInAnyOrderElementsOf(
            nodeKeys(fixture.projection()));
        assertThat(fixture.pins()).hasSize(2000);
    }
```

Define the two test helpers in the same class: `repositoryFile(String relativePath)`
walks up from the working directory until the file exists (the
`GraphWorkspaceModelAcceptanceShould.repositoryFile` pattern), and
`nodeKeys(GraphProjection)` returns the ascending `CanonicalKeys.node` key list of the
projection's nodes.

`SettledReferenceFixture` (created in this step) loads the resource through
`SettledReferenceFixture.class.getResourceAsStream("/performance/settled-reference.csv")`,
verifies the pinned SHA-256, parses the header `kind,key,x,y` and builds the
`LayoutPositions`/`PinProjection` lists against the projection's exact key sets (a
missing or extra row raises `IllegalStateException`). Its public surface is:

```java
public final class SettledReferenceFixture {
    public static final String SETTLED_FIXTURE_SHA256 = "<recorded in Step 4>";
    static byte[] readCommittedLedger();
    static String sha256(byte[] bytes);
    public static SettledReferenceFixture load();
    public GraphProjection projection();
    public LayoutPositions positions();
    public List<PinProjection> pins();
    public LayoutRequest request();
    public LayoutFrame replayFrame();       // LayoutFrame.of(0L, positions(), false, 0)
}
```

`request()` builds the `LayoutRequest` from the reference workspace id, the loaded
projection, an empty `ProjectionDiff.between(projection, projection)` and the ledged
pins.

Those two surface requirements are the whole recorded-ledger contract: static committed
bytes, a digest pin, and no engine step anywhere in the test path.

- [ ] **Step 5: Record the two stages on the settled fixture and the raw frames**

Make the `LayoutWorker` engine-supplier constructor public (visibility only):

```java
    public LayoutWorker(final Supplier<LayoutEngine> engineFactory,
            final PerceptualIdlePolicy idlePolicy) {
```

This is the seam `SettledReferenceFixture` needs to drive the production worker
composition with a deterministic replay engine:

```java
    private static final class LedgerReplayEngine implements LayoutEngine {
        private final LayoutFrame frame;

        private LedgerReplayEngine(final LayoutFrame frame) {
            this.frame = frame;
        }

        @Override
        public LayoutFrame apply(final LayoutRequest request) {
            return frame;
        }

        @Override
        public LayoutFrame step() {
            return frame;
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }
    }
```

In `GraphWorkspacePerformanceDiagnostic`, add the two record types and the two entry
points (package-private; `WARMUP_SAMPLES`/`TIMED_SAMPLES` become package-private), and
remove every `EDGE_CONFLICT`/`EDGE_ROUTING` recording from `runDirectProbe` (the
attempt-8/9 direct-probe benchmark scaffolding is superseded; if no such recordings
exist yet, add none there):

```java
    static final int RAW_REFERENCE_FRAMES = 8;
    static final long RAW_REFERENCE_MAX_FRAME_NANOS = 30_000_000_000L;   // Step 7 tightens

    public static final class SettledReferenceSample {
        private final EdgeConflictDiagnostics edgeDiagnostics;
        private final EdgeRouteDiagnostics routeDiagnostics;
        public EdgeConflictDiagnostics edgeDiagnostics() { return edgeDiagnostics; }
        public EdgeRouteDiagnostics routeDiagnostics() { return routeDiagnostics; }
    }

    public static final class RawReferenceFrame {
        private final long durationNanos;
        private final EdgeConflictDiagnostics edgeDiagnostics;
        private final EdgeRouteDiagnostics routeDiagnostics;
        public long durationNanos() { return durationNanos; }
        public EdgeConflictDiagnostics edgeDiagnostics() { return edgeDiagnostics; }
        public EdgeRouteDiagnostics routeDiagnostics() { return routeDiagnostics; }
    }

    private final List<SettledReferenceSample> settledReferenceSamples =
        new ArrayList<SettledReferenceSample>();
    private final List<RawReferenceFrame> rawReferenceFrames =
        new ArrayList<RawReferenceFrame>();

    List<SettledReferenceSample> settledReferenceSamples() {
        return Collections.unmodifiableList(settledReferenceSamples);
    }

    List<RawReferenceFrame> rawReferenceFrames() {
        return Collections.unmodifiableList(rawReferenceFrames);
    }

    long rawReferenceMaxFrameNanos() {
        long maximum = 0L;
        for (final RawReferenceFrame frame : rawReferenceFrames) {
            maximum = Math.max(maximum, frame.durationNanos());
        }
        return maximum;
    }
```

`runSettledReferenceFixture()` runs `REFERENCE_2000_5000.warmupCount()` +
`measuredCount()` samples with one replay worker and records every stage on the settled
fixture's own frame. `visible` is computed once from the fixture projection; the
`edge-conflict` stage is timed on `frame.positions()` after a fresh
`BoundarySeparationCorrection.apply`; the `edge-routing` stage is timed on the same
positions and `workerHull`; the first-frame sample runs from the sample start through
the `CanvasState` swap, mirroring `runSample`:

```java
        final long workerStart = clock.nanoTime();
        final LayoutFrame workerFrame = await(worker.submit(fixture.request()),
            "settled reference worker submit");
        final long workerEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.FULL_WORKER,
            workerStart, workerEnd, warmup);

        final GraphGeometry workerHull = geometryEngine.computeHulls(fixture.projection(),
            workerFrame.positions(), textMetrics);
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            fixture.projection().nodes(), fixture.projection().enclosures());

        final BoundarySeparationResult settledBoundary = correctionEngine.apply(fixture.projection(),
            workerFrame.positions(), textMetrics, fixture.pins());
        final long edgeConflictStart = clock.nanoTime();
        final EdgeConflictResult settledEdge = new EdgeConflictCorrection().apply(fixture.projection(),
            settledBoundary.positions(), textMetrics, fixture.pins(), visible, settledBoundary);
        final long edgeConflictEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.EDGE_CONFLICT,
            edgeConflictStart, edgeConflictEnd, warmup);
        if (settledEdge.diagnostics().detectionTruncated()
                || settledEdge.diagnostics().verificationBudgetExhausted()
                || settledEdge.diagnostics().indexBudgetExhausted()) {
            measurements.recordDiscard(PerformanceMeasurements.Stage.EDGE_CONFLICT);
        }

        final long edgeRoutingStart = clock.nanoTime();
        final EdgePaths settledPaths = new EdgeRouter().route(fixture.projection(),
            workerFrame.positions(), workerHull, visible);
        final long edgeRoutingEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.EDGE_ROUTING,
            edgeRoutingStart, edgeRoutingEnd, warmup);
        if (settledPaths.diagnostics().routeBudgetExhausted()
                || settledPaths.diagnostics().detectionTruncated()) {
            measurements.recordDiscard(PerformanceMeasurements.Stage.EDGE_ROUTING);
        }
        if (!warmup) {
            settledReferenceSamples.add(new SettledReferenceSample(settledEdge.diagnostics(),
                settledPaths.diagnostics()));
        }
```

The settled run is the **only** place the two stages are recorded; the raw run records
no `EDGE_CONFLICT`/`EDGE_ROUTING` samples at all. `runRawReferenceFixture()` submits
exactly `RAW_REFERENCE_FRAMES = 8` production worker frames on the generated reference
workspace (no `worker.step()`, matching the published raw/divergent frames), times each
`worker.submit` completion and records a `RawReferenceFrame` with the frame's own
`edgeConflictDiagnostics()` and the `EdgeRouter.route(...)` diagnostics for that frame's
positions. Its frames must be non-failed (`LayoutFrame.of` already rejects non-finite
coordinates), so an OOM or a failed frame fails the test rather than passing silently.

Also validate `frame.edgeConflictDiagnostics()` in `requireUsableFrame` exactly as
`validateBoundaryDiagnostics` validates the boundary record: the diagnostics must be
present on every usable worker frame (the Task 8 `CanvasState` factory already routes the
frame's own positions).

- [ ] **Step 6: Run the performance suites and tune only the fixture compiler**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould" \
  --tests "org.freeplane.plugin.graph.performance.PerformanceTripwiresShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the wire list and both thresholds pass, the window pins
hold, every measured settled sample is exhaustion-free and non-trivial (at least one
detected crossing and at least one accepted bow or dogleg), the settled fixture stays
inside the worker/first-frame gates, and the raw frames stay bounded and flag-visible.

If Step 6 fails, the only permitted adjustments are the deterministic fixture compiler's
parameters (`SETTLED_RELAXATION_ITERATIONS`, `SETTLED_SCALE` and the relaxation
constants) and its deterministic projection subset size (per the Step 4 size-calibration
rule): record the observed counted work, regenerate the ledger, and re-run. **Do not change any
assertion, threshold or Revision 7 budget.** If no safe parameter change removes a
budget stop, stop and report `BLOCKED` with the observed diagnostics
(`indexInsertedItems`, `indexPairChecks`, `detectionTruncated`, `indexBudgetExhausted`,
`verificationBudgetExhausted`, `routeIndexItems`, `routeObstacleChecks`,
`routePairChecks`, `routeBudgetExhausted`, and the measured stage p50/p95/p99): the plan
does not authorize weakening A7a or raising a budget.

The full wall-clock ledger remains the existing
`gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance`
JavaExec (all six scenarios, the process deadline and the strict ledger). Run it once on
the reference machine for the calibration record below.

- [ ] **Step 7: Record the calibration and the A7b per-frame bound (tighten-only)**

On the saturated in-budget frame (all eight Task 21 budgets at their caps, including the
final detection and the §5.4 rebuild), measure for each budget `B` the per-unit cost
`c_B` and fixed overhead `o` using the Task 21 counted accessors, and the stage's
measured worst-in-budget time. Verify `cap(B) × c_B + o ≤ f × threshold` with `f = 0.5`
(the `edge-conflict` allowance is 12.5 ms and the `edge-routing` allowance is 5 ms) and
record the measured values and the worst-in-budget stage time in the task report. If the
calibrated cap is below a pinned ceiling, replace that ceiling with the calibrated value
in `EdgeConflictCorrection.java`/`EdgeRouter.java` in this same change; never raise a
pinned budget or threshold. The spec §13.1 obligation ("The implementation change records
the measured values and replaces any ceiling above its calibrated cap with the calibrated
value in the same change ... Only tightening is allowed") is satisfied by this record,
and any measured stage time above the pinned threshold blocks the commit.

Record the max raw/divergent frame time from `runRawReferenceFixture()`; replace
`RAW_REFERENCE_MAX_FRAME_NANOS` with `ceil(max(measuredMaxNanos, 1 s) * 1.25)` rounded up
to the next second when that is smaller than the current value, and record both the
measured maximum and the final pin in the task report. This is a measurement-window
value (Revision 8 §15.3); no strict millisecond threshold applies to the raw frames.
The Revision 7 window values (400/300, `WARMUP_SAMPLES = 20`, `TIMED_SAMPLES = 100`, the
40-minute `PROCESS_DEADLINE_NANOS`) remain re-pinnable with the measured cost recorded;
no strict threshold changes.

- [ ] **Step 8: Complete probe 14's budget half**

With the `edge-conflict` stage recorded on the settled fixture, apply the Task 15 probe
14 mutation again (fresh `computeHulls` instead of the terminal-geometry reuse) and run
`referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates`. Expected: the mutation
fails `reusesTheBoundaryTerminalGeometry` (the fresh `computeHulls` rebuilds radius
8.0/14.0 discs instead of the poisoned terminal geometry), and it increases the
settled-frame `edge-conflict` cost, so if that increase pushes the p95 past 25 ms the
strict gate also fails. Restore the file and confirm `git diff` is empty.

- [ ] **Step 9: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixture.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixtureCompiler.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixtureShould.java \
  freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv
git commit -m "perf(graph): split A7 measurement with a committed settled-position fixture [2026-10-12-graph-edge-crossing-avoidance]"
```

If Step 5 tightened a calibrated ceiling or Step 7 tightened
`RAW_REFERENCE_MAX_FRAME_NANOS`, include the modified source file(s) in the same
`git add` and the same commit.

## Task 19: Pin the acceptance surface and run the module and core gates

**Implementer tier:** Standard
**Lane:** edge-publication
**Depends on:** Tasks 12, 16, 17, 18, 21

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java` (only if a §9.2 case is still uncovered)

**Interfaces:**

- Consumes: the Task 12 `paintsNoCrossingForTheReportedFixture` fixture;
  `EdgePaths.diagnostics().pathCrossingsResidual()`;
  `EdgeConflictDiagnostics.detectionTruncated()/verificationBudgetExhausted()/indexBudgetExhausted()`
  and `EdgeRouteDiagnostics.routeBudgetExhausted()/detectionTruncated()` (Task 21);
  the Task 16 routed paths; the Task 18 A7a/A7b acceptance tests
  (`PerformanceTripwiresShould.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates`
  on the committed `settled-reference` fixture, and
  `.referenceFixtureStaysBoundedAndFlagVisible` on the raw/divergent reference frames);
  every acceptance test named in spec §10.
- Produces: `GraphCanvasPaintShould.reportsNoPaintedCrossingResidualForTheReportedFixture`
  with the Revision 7 non-truncation scope, and the full acceptance gate evidence in the
  task report (the module suite result and the core-gate listing).

- [ ] **Step 1: Add the residual acceptance test**

Append to `GraphCanvasPaintShould`:

```java
    @Test
    public void reportsNoPaintedCrossingResidualForTheReportedFixture() {
        CanvasState state = reportedQuadState();

        assertThat(state.edgePaths().diagnostics().pathCrossingsDetected()).isEqualTo(1);
        assertThat(state.edgePaths().diagnostics().pathCrossingsResidual()).isEmpty();
        assertThat(state.edgePaths().diagnostics().edgesFallback()).isZero();
        assertThat(state.layout().edgeConflictDiagnostics().crossingsResolved()).isEqualTo(1);
        assertThat(state.layout().edgeConflictDiagnostics().conflictsResidual()).isEmpty();
        assertThat(state.layout().edgeConflictDiagnostics().verificationBudgetExhausted()).isFalse();
        assertThat(state.layout().edgeConflictDiagnostics().detectionTruncated()).isFalse();
        assertThat(state.layout().edgeConflictDiagnostics().indexBudgetExhausted()).isFalse();
        assertThat(state.edgePaths().diagnostics().routeBudgetExhausted()).isFalse();
        assertThat(state.edgePaths().diagnostics().detectionTruncated()).isFalse();
    }
```

The reported fixture is a non-truncated frame for both stages: no detection-side
truncation, no verification stop, no direct full-enumeration fallback and no router
budget stop. A truncated frame publishes a lower-bound residual, so the acceptance
surface must assert the flag scope, not only the empty residual.

- [ ] **Step 2: Run the full module gate**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`, no test failure. This is acceptance criterion A10 and the
regression gate for the unchanged interaction contract (selection, hover, dimming,
search, pins, viewport, hit-testing, the 2,000-node/5,000-edge commitment).

- [ ] **Step 3: Confirm the core gate and a clean tree**

```bash
cd "$(git rev-parse --show-toplevel)"
git status --porcelain
git diff --stat
git log --format='%H' --grep='\[2026-10-12-graph-edge-crossing-avoidance\]' \
  | xargs -r git show --name-only --format= \
  | sort -u
git log --format='%H' --grep='\[2026-10-12-graph-edge-crossing-avoidance\]' \
  | xargs -r git show --name-only --format= \
  | grep -E '^(freeplane|freeplane_api)/' || echo "core gate clean"
```

Expected: `git status --porcelain` and `git diff --stat` print nothing; the file listing
contains only `freeplane_plugin_graph/` paths and `docs/superpowers/` design/spec/plan
paths; the final command prints `core gate clean`. If any path starts with `freeplane/`
or `freeplane_api/`, stop and report `BLOCKED`: the plugin-only constraint is violated.

- [ ] **Step 4: Verify every named acceptance test is present and green**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  --tests "org.freeplane.plugin.graph.geometry.EdgePathShould" \
  --tests "org.freeplane.plugin.graph.projection.CanonicalKeysShould" \
  --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" \
  --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" \
  --tests "org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould" \
  --tests "org.freeplane.plugin.graph.performance.PerformanceTripwiresShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Map the spec §10 acceptance criteria to the observed
results in the task report: A1
`EdgeConflictCorrectionShould.swapsTheReportedConvexQuadAndRemovesTheCrossing` +
`GraphCanvasPaintShould.paintsNoCrossingForTheReportedFixture`; A2
`EdgeRouterShould.routesTheFirstEdgeAroundALaterChordsCrossing`; A3
`EdgeRouterShould.waivesTheDistanceRequirementInsideTheFanRegion` +
`.rejectsAProperCrossingInsideTheFanRegion`; A4
`EdgeConflictCorrectionShould.keepsAPreExistingHullResidualWhenTheCandidateDoesNotAddOne`;
A5 `LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild` +
`BoundaryInvariantsShould.describePreservesStageFieldsAndRecomputesResiduals`; A6 **both
scopes of the Revision 7 truncation contract**: the non-truncated scope by
`EdgeConflictCorrectionShould.detectsACrossingAtExtremeSpans` +
`.enumeratesEveryPairWhenSubdivisionExhausts` (every pair detected, counted work bounded
by the Task 21 pins) and the truncated scope by
`EdgeConflictCorrectionShould.truncatesDetectionAtTheItemBudget` +
`.truncatesDetectionAtThePairBudget` (the item/pair stop publishes the ascending
accumulated set as a lower-bound residual with `detectionTruncated == true`); A7 the
Revision 8 split — **A7a** by `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds` +
`.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates` on the committed
`settled-reference` ledger (`SettledReferenceFixtureShould` proves the ledger
is the deterministic compilation of the reference projection): the pinned strict gates
hold and every measured sample reports `detectionTruncated == false`,
`verificationBudgetExhausted == false`, `indexBudgetExhausted == false`,
`routeBudgetExhausted == false` with at least one detected crossing and at least one
accepted bow or dogleg; **A7b** by
`.referenceFixtureStaysBoundedAndFlagVisible` on the raw/divergent reference frames: no
OOM, every frame completes within `RAW_REFERENCE_MAX_FRAME_NANOS`, every exhausted
Revision 7 budget has its flag set and the published counted work stays within the pins
(no millisecond threshold applies to those frames). The attempt-8/9 direct-probe
benchmark scaffolding is superseded where it conflicts with this measurement model; A8
**all twenty probes** recorded in Tasks 13-18 and 21 (probes 1-16 in Tasks 13-18, probes
17-20 in Task 21); A9
`EdgeConflictCorrectionShould.isDeterministicAcrossRunsAndPermutedInputs` +
`EdgeRouterShould.isDeterministicAcrossRunsAndPermutedEdgeOrder`; A10 this gate.

- [ ] **Step 5: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "test(graph): pin the no-painted-crossing acceptance residual [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 20: Pin the rebuilt-record composition in BoundarySeparationShould

**Implementer tier:** Standard
**Lane:** edge-foundation
**Depends on:** Tasks 8, 15

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`

**Interfaces:**

- Consumes: the class's existing pipeline fixture (`PIPELINE_REGULARITY`,
  `PIPELINE_CHOICE`, `PIPELINE_PINNED_THEOREM`, `pipelineKey(...)` and the request/worker
  harness used at `BoundarySeparationShould.java:650-652`); `LayoutWorker.submit(...)`;
  `LayoutFrame.edgeConflictDiagnostics()`, `.boundaryDiagnostics()`, `.positions()`;
  `BoundarySeparationCorrection.apply(...)` for the stage baseline;
  `BoundaryInvariants.detectPairKeys(...)` (Task 3); `EdgeConflictDiagnostics.rounds()`;
  `NodeSeparationProjection.violatingPairs(...)` (Task 3).
- Produces: `BoundarySeparationShould.rebuildsThePublishedBoundaryRecordAfterARelocation`
  and `.rebuiltNodeResidualIsOrderIndependent`, the composition coverage spec §9.2 names
  for this suite.

- [ ] **Step 1: Add the composition tests**

Append to `BoundarySeparationShould`:

```java
    @Test
    public void rebuildsThePublishedBoundaryRecordAfterARelocation() throws Exception {
        final RebuildPipeline pipeline = rebuildPipeline();
        final LayoutFrame published = pipeline.worker.submit(pipeline.request).toCompletableFuture().get();

        assertThat(published.edgeConflictDiagnostics().rounds()).isGreaterThan(0);
        assertThat(published.boundaryDiagnostics().rounds())
            .isEqualTo(pipeline.boundaryBaseline.rounds());
        assertThat(published.boundaryDiagnostics().hullViolationsDetected())
            .isEqualTo(pipeline.boundaryBaseline.hullViolationsDetected());
        assertThat(published.boundaryDiagnostics().displacementRms())
            .isEqualTo(pipeline.boundaryBaseline.displacementRms());
        assertThat(published.boundaryDiagnostics().displacementMax())
            .isEqualTo(pipeline.boundaryBaseline.displacementMax());
        assertThat(published.boundaryDiagnostics().appliedDisplacements())
            .isEqualTo(pipeline.boundaryBaseline.appliedDisplacements());
        assertThat(published.boundaryDiagnostics().residualHullPairs()).isSorted();
        assertThat(published.boundaryDiagnostics().residualHullPairs())
            .isEqualTo(new ArrayList<String>(new TreeSet<String>(new BoundaryInvariants().detectPairKeys(
                pipeline.request.projection(), pipeline.publishedGeometry))));
    }

    @Test
    public void rebuiltNodeResidualIsOrderIndependent() {
        final RebuildPipeline pipeline = rebuildPipeline();
        final LayoutPositions published = pipeline.publishedPositions;

        final int first = new NodeSeparationProjection().project(pipeline.request.projection(),
            published, Collections.<ProjectedNodeKey>emptySet()).residualViolations();
        final int second = new NodeSeparationProjection().project(pipeline.request.projection(),
            reversedNodeOrder(published), Collections.<ProjectedNodeKey>emptySet()).residualViolations();

        assertThat(first).isEqualTo(second);
    }
```

`rebuildPipeline()` reuses the class's existing pipeline projection and builds a
`LayoutRequest`, runs `new BoundarySeparationCorrection().apply(...)` on the raw engine
frame to capture `boundaryBaseline` and `publishedGeometry` (via
`new GraphGeometryEngine().computeHulls(projection, publishedFrame.positions(), metrics)`),
and exposes the worker, the request, the published frame's positions and the published
geometry. `reversedNodeOrder(LayoutPositions)` returns a `LinkedHashMap` with the node
entries in reverse insertion order and the same anchors. The fixture must actually
relocate (the class's pipeline fixture does, at lines 650-652).

- [ ] **Step 2: Run the composition suite**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the published frame's boundary record is the rebuilt one
with stage fields preserved, its residual pairs equal an independent
`BoundaryInvariants.detectPairKeys` call, and the node residual is identical under the
reversed node order.

- [ ] **Step 3: Re-run the whole relocation and foundation surface**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "test(graph): pin the rebuilt-record and node-residual composition [2026-10-12-graph-edge-crossing-avoidance]"
```

## Task 21: Bound the relocation and router work for reference-scale frames

**Implementer tier:** Advanced
**Lane:** edge-bounded
**Depends on:** Tasks 13, 14, 15, 16

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictDiagnostics.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryInvariants.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CountedViolatingPairs.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouteDiagnostics.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java`

**Interfaces:**

- Consumes: `EdgeConflictCorrection`'s Task 13-15 detection, candidate ladder, A1-A4,
  round loop, diagnostics and rebuild; `NodeSeparationProjection.violatingPairs`,
  `.MIN_GAP`, `NODE_RADIUS × prominenceScale`; `BoundaryInvariants.detectPairKeys`,
  `.detect`; `GraphGeometryEngine.recomputeHulls(...)`;
  `EdgeRouter.route(...)`, its `QueryGrid`, `clears(...)`, `crossingPairs(...)` and
  `hullPenetrations(...)` (Task 16); `EdgeConflictDiagnostics` and
  `EdgeRouteDiagnostics` (Tasks 4, 5); the Task 13-16 fixtures
  (`reportedQuad()`, `enumeratesEveryPairWhenSubdivisionExhausts`,
  `EdgeRouterFixture.laterChordBow()` and the counted `Detection` seam).
- Produces (spec §13.1-§13.3, §2.3/§2.4/§2.6; design §14.2-§14.3): the eight per-frame
  budgets, their counted units and check points exactly as pinned below; the four flags
  and the both-flags rule; the counted conflict-stage accessors
  `EdgeConflictDiagnostics.detectionTruncated()` and `.verificationWork()`; the counted
  router accessors `EdgeRouteDiagnostics.routeBudgetExhausted()`,
  `.detectionTruncated()`, `.routeIndexItems()`, `.routeObstacleChecks()`,
  `.routePairChecks()`; and the budget-aware predicate (spec §2.6, verbatim):

```java
public static List<String> violatingPairs(GraphProjection projection, LayoutPositions positions);
public static CountedViolatingPairs violatingPairsCounted(GraphProjection projection, LayoutPositions positions,
        int pairBudget);
```

`CountedViolatingPairs` is a new immutable value type with `pairs()` (the ascending
`CanonicalKeys.nodePair(a, b)` list), `long pairEvaluations()` (the number of node pairs
examined, counted as `verificationWork`) and `boolean truncated()`. `violatingPairs(...)`
delegates to the counted variant with `Integer.MAX_VALUE`, so the predicate has exactly
one implementation and unbudgeted callers keep today's behaviour.

The conflict-stage budget constants on `EdgeConflictCorrection` are package-private like
the existing constants:

```java
    static final long EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME = 262_144L;
    static final long EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME = 131_072L;
    static final int EDGE_CONFLICT_MAX_DETECTED_PER_FRAME = 4_096;
    static final long EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65_536L;
```

`MAX_VERIFICATIONS_PER_FRAME = 64` is Task 14's constant and stays. The router budget
constants on `EdgeRouter` are package-private:

```java
    static final long EDGE_ROUTING_MAX_INDEX_ITEMS_PER_FRAME = 262_144L;
    static final long EDGE_ROUTING_MAX_OBSTACLE_CHECKS_PER_FRAME = 131_072L;
    static final long EDGE_ROUTING_MAX_PAIR_CHECKS_PER_FRAME = 131_072L;
```

The eight pins, with counted work as the unit and the exact check points (spec §13.1):

| pin | value | counted unit | check point | stop |
| --- | --- | --- | --- | --- |
| `EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME` | `262_144` | one inserted index item (one primitive-cell write) or one direct outermost examination | before every cell write and inside every subdivision re-collect | `detectionTruncated`; ladder does not start; lower-bound residual |
| `EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME` | `131_072` | one `properlyCross` / `segmentDistance` test | before every test, including the outermost direct pass | `detectionTruncated`; lower-bound residual |
| `EDGE_CONFLICT_MAX_DETECTED_PER_FRAME` | `4_096` | one distinct conflict identity key | when a key is added to the accumulating set | `detectionTruncated`; lower-bound residual |
| `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME` | `65_536` | one verification-phase predicate evaluation: one A2 node-pair test, one A3 hull-pair comparison or one recomputed hull | before every counted verification sub-step | attempt rejected; `verificationBudgetExhausted`; detection complete |
| `MAX_VERIFICATIONS_PER_FRAME` | `64` (§1.3) | one batch or single A1-A4 evaluation | before each attempt | `verificationBudgetExhausted`; detection complete |
| `EDGE_ROUTING_MAX_INDEX_ITEMS_PER_FRAME` | `262_144` | one query-index primitive-cell insertion (path sample segment, disc box, hull segment) | before every insertion | remaining edges fallback; `routeBudgetExhausted` |
| `EDGE_ROUTING_MAX_OBSTACLE_CHECKS_PER_FRAME` | `131_072` | one `(candidate rung or straight chord, obstacle)` clearance evaluation | before every evaluation | remaining edges fallback; `routeBudgetExhausted`; fallbacks consume no obstacle budget |
| `EDGE_ROUTING_MAX_PAIR_CHECKS_PER_FRAME` | `131_072` | one sample-segment pair test in the detected-crossing, residual or hull-penetration pass | before every test | running pass stops; lower-bound diagnostics; router `detectionTruncated` |

All counters are **per-frame totals** and are never reset between passes: the conflict
counters accumulate over the initial detection, every verification detection, A1-A4
(including the counted A2 predicate, every A3 hull-pair comparison and every recomputed
hull), the final detection and the §5.4 rebuild; the router counters accumulate over the
index build, every obstacle evaluation and every detected/residual/hull pass. At every
stop point all counters are evaluated and every flag whose counter is at or beyond its
cap is set (the both-flags rule). The final publication detection and the §5.4 rebuild
always run to completion and their counted work is part of the per-frame total; because
A1 strictly shrinks the conflict set, the final detection's volume is no larger than the
initial detection's.

**Four flags, distinct.** `indexBudgetExhausted` records that the index fell back to
direct full enumeration (subdivision floor/depth reached, or the outermost direct pass
ran) with every pair examined and none dropped; it is never a truncation signal, it
stays `false` on a truncated frame unless the direct fallback ran at the same stop
point, and a stop inside that fallback may therefore carry both `indexBudgetExhausted`
and `detectionTruncated`. `verificationBudgetExhausted` is a verification-phase stop;
detection is complete and the residual exact. `detectionTruncated` is a detection-side
stop on the initial (published-position) detection; the published
`conflictsResidual`/`pathCrossingsResidual` is a lower bound and the flag is the only
signal of incompleteness. `routeBudgetExhausted` is the router's index/obstacle stop and
the remaining edges are fallbacks (§13.3).

**Initial-detection stop (spec §13.2).** When an index-item, pair-check or detected-key
budget trips during the initial detection, `apply` performs no relocation attempts and
no §5.4 rebuild for that frame. The published set is the ascending set of distinct
conflict keys accumulated up to the counted stop ("detected so far"), never a set derived
from full enumeration: its size is `conflictsDetected`, its C1 subset count is
`crossingsDetected`, its C2 subset count is `edgeOverNodeDetected`, and the same keys are
`conflictsResidual`; `rounds = 0`, `verifications = 0`, `repairsApplied` empty and
`crossingsResolved = edgeOverNodeResolved = 0`. The frame's
boundary diagnostics are the boundary result's own diagnostics for this frame (no
rebuild; positions are unchanged) and no stale previous-frame diagnostics are retained.

**Verification stop (spec §13.2).** A detection-side stop inside a verification
detection rejects that attempt; a verification-work stop likewise rejects the in-flight
attempt. Both stop the ladder with `verificationBudgetExhausted = true` and leave
`detectionTruncated = false` (the stop is reported as a verification-phase stop): the
last complete detection still describes the unchanged published positions, so the
residual is exact and `C_out` is never accepted partially. The attempt cap keeps its
§5.3 semantics: the flag is set on the transition to the cap when conflicts remain after
the rejected cap attempt and after the final detection when an accepted cap attempt
still leaves conflicts; an accepted cap attempt that clears every conflict leaves the
flag `false`.

**Router stop semantics (spec §13.3).** On index-item or obstacle-budget exhaustion the
router stops the corresponding work, publishes every remaining canonical-order edge as a
straight fallback and sets `routeBudgetExhausted = true`; the classification identity
`edgesStraight + edgesBow + edgesDogleg + edgesFallback == routed visible edges` still
holds and **fallback edges consume no obstacle budget**. On pair-check exhaustion the
running detected-crossing, residual or hull-penetration pass stops and sets router
`detectionTruncated = true`; `pathCrossingsDetected`, `pathCrossingsResidual` and
`pathHullPenetrations` are lower bounds over the published paths, and routing selection
itself is unaffected (it is bounded by the obstacle budget). No partially drawn geometry
is ever published; router index-item/obstacle stops and the pair-check stop may coexist
as flags.

- [ ] **Step 1: Add the budget-aware A2 predicate and its value type**

Create `CountedViolatingPairs.java` with the spec §2.6 value type:

```java
public final class CountedViolatingPairs {
    public static CountedViolatingPairs of(List<String> pairs, long pairEvaluations, boolean truncated);
    public List<String> pairs();
    public long pairEvaluations();
    public boolean truncated();
}
```

Then add to `NodeSeparationProjection`:

```java
    public static List<String> violatingPairs(final GraphProjection projection,
            final LayoutPositions positions) {
        return violatingPairsCounted(projection, positions, Integer.MAX_VALUE).pairs();
    }

    /** The canonical node-pair keys of every pair below the minimum gap, or the prefix examined
     *  before the pair budget reaches zero; the loop decrements the allowance before each test. */
    public static CountedViolatingPairs violatingPairsCounted(final GraphProjection projection,
            final LayoutPositions positions, final int pairBudget) {
        // Same ascending node order and same MIN_GAP / NODE_RADIUS * prominenceScale rule as
        // violatingPairs today. remaining = pairBudget; before every test: if (remaining == 0)
        // return CountedViolatingPairs.of(result, evaluations, true); remaining--; evaluations++;
        // predicate; add CanonicalKeys.nodePair(first, second) when below need.
        // A scan that ends with remaining == 0 is still truncated (the budget was consumed).
    }
```

`project(...)` keeps computing `residualViolations()` as
`violatingPairs(projection, result).size()` on the final relaxed positions. Do **not**
change the public `violatingPairs` signature or the `project` signature.

- [ ] **Step 2: Run the predicate suite and confirm the transition**

Add to `NodeSeparationProjectionShould` a test that a small fixture's
`violatingPairsCounted(projection, positions, 7)` returns exactly
`pairEvaluations() == 7`, `truncated() == true`, and an ascending `pairs()` list that is
a prefix of the unbounded `violatingPairs(...)` list, plus a test that the unbounded
delegation returns `truncated() == false` and the same list as `violatingPairs(...)`.

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.NodeSeparationProjectionShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava` before the value type and method exist;
`BUILD SUCCESSFUL` after Step 1.

- [ ] **Step 3: Extend the diagnostics with the two flags and the counted accessors**

`EdgeConflictDiagnostics` gains `boolean detectionTruncated()` and
`long verificationWork()`; its full constructor becomes (spec §2.3):

```java
    public EdgeConflictDiagnostics(int conflictsDetected, int crossingsDetected, int edgeOverNodeDetected,
            int crossingsResolved, int edgeOverNodeResolved, List<String> conflictsResidual,
            List<Repair> repairsApplied, int rounds, int verifications,
            boolean verificationBudgetExhausted, boolean indexBudgetExhausted, boolean detectionTruncated,
            long indexInsertedItems, long indexPairChecks, long verificationWork, int indexSubdivisions);
```

`empty()` zeroes/falses the two new members; `equals`/`hashCode` include them.
`EdgeRouteDiagnostics` gains `boolean routeBudgetExhausted()`, `boolean detectionTruncated()`,
`long routeIndexItems()`, `long routeObstacleChecks()` and `long routePairChecks()`; its
full constructor becomes (spec §2.4):

```java
    public EdgeRouteDiagnostics(int edgesStraight, int edgesBow, int edgesDogleg, int edgesFallback,
            int pathCrossingsDetected, List<String> pathCrossingsResidual, int pathHullPenetrations,
            boolean routeBudgetExhausted, boolean detectionTruncated,
            long routeIndexItems, long routeObstacleChecks, long routePairChecks);
```

`empty()` zeroes/falses the new members; `equals`/`hashCode` include all of them. The
only production construction sites, `EdgeConflictCorrection.relocate` and
`EdgeRouter.route`, are updated in Steps 6 and 10; Task 5's `EdgePaths.empty()` keeps
delegating to `EdgeRouteDiagnostics.empty()`.

- [ ] **Step 4: Add the relocation truncation fixtures first**

Add these fixtures and tests to `EdgeConflictCorrectionShould`. All fixtures are
generated programmatically by helpers on the class (`referenceScaleFan(int)`,
`sharedLeafFan(int)`, `concurrentDiameters(int)`, `wideNodeFieldWithOneCrossing(int)`,
`verificationDetectionStopFixture()`), all using the class's existing hand-built
`Fixture` harness, and all assertions are counted work — never wall clock.

```java
    @Test
    public void boundsTheCountedRelocationWorkOnADenseWideSpanFrame() {
        final Fixture fixture = referenceScaleFan(500);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().indexInsertedItems())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(result.diagnostics().indexPairChecks())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(result.diagnostics().verificationWork())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME);
        assertThat(result.diagnostics().verifications())
            .isLessThanOrEqualTo(EdgeConflictCorrection.MAX_VERIFICATIONS_PER_FRAME);
    }

    @Test
    public void truncatesDetectionAtTheItemBudget() {
        final Fixture fixture = referenceScaleFan(500);
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection bruteForce = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().indexBudgetExhausted()).isFalse();
        assertThat(result.diagnostics().indexInsertedItems())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(result.diagnostics().indexPairChecks())
            .isLessThan(EdgeConflictCorrection.EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(result.diagnostics().crossingsDetected()).isLessThan(bruteForce.crossings());
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(new java.util.TreeSet<String>(bruteForce.keys()))
            .containsAll(result.diagnostics().conflictsResidual());
        assertThat(result.diagnostics().conflictsResidual()).isSorted();
        assertThat(new java.util.LinkedHashSet<String>(result.diagnostics().conflictsResidual()))
            .hasSize(result.diagnostics().conflictsResidual().size());
    }

    @Test
    public void truncatesDetectionAtThePairBudget() {
        final Fixture fixture = sharedLeafFan(513);
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection bruteForce = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().indexPairChecks())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(result.diagnostics().indexInsertedItems())
            .isLessThan(EdgeConflictCorrection.EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(result.diagnostics().conflictsDetected())
            .isLessThan(EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(new java.util.TreeSet<String>(bruteForce.keys()))
            .containsAll(result.diagnostics().conflictsResidual());
        assertThat(result.diagnostics().conflictsResidual()).isSorted();
    }

    @Test
    public void truncatesDetectionAtTheVolumeBudget() {
        final Fixture fixture = concurrentDiameters(92);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().conflictsDetected())
            .isEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().crossingsDetected())
            .isEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().conflictsResidual()).hasSize(
            EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.diagnostics().conflictsResidual()).isSorted();
    }

    @Test
    public void rejectsTheAttemptWhenVerificationWorkExhausts() {
        final Fixture fixture = wideNodeFieldWithOneCrossing(512);
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection expected = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().detectionTruncated()).isFalse();
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verificationWork())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME);
        assertThat(result.diagnostics().conflictsResidual()).isEqualTo(expected.keys());
    }

    @Test
    public void rejectsTheAttemptWhenAVerificationDetectionStops() {
        final Fixture fixture = verificationDetectionStopFixture();
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection expected = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().detectionTruncated()).isFalse();
        assertThat(result.diagnostics().conflictsResidual()).isEqualTo(expected.keys());
    }
```

The fixture shapes (pick the counts by reading the counted accessors; the assertions
above are the contract, the counts are tunable):

- `referenceScaleFan(500)`: 500 diameters of a radius-`600_000` circle (the superseded
  `1b5e22c460` shape). The shared origin cell re-collects at every subdivision level and
  the item writes alone reach the cap before pair enumeration starts. This fixture
  replaces the archived test's all-124,750-crossing assertion: below the budgets every
  crossing is still detected, above them the accumulated truncated set plus
  `detectionTruncated` is the pinned behaviour.
- `sharedLeafFan(513)`: 513 chords that share a common endpoint inside one leaf cell plus
  a small fixed number of genuine crossing pairs. Pair checks ≈ 513·512/2 > the pair cap,
  the item writes and the detected-key volume stay below their caps, and the published
  residual is the accumulated crossing keys (a non-empty lower bound).
- `concurrentDiameters(92)`: 92 diameters that all pass through one interior point; all
  4,186 pairs properly cross, so the detected-key volume trips at 4,096 with the pair and
  item counters below their caps.
- `wideNodeFieldWithOneCrossing(512)`: ~512 well-separated discs plus one properly
  crossing pair. The initial detection is cheap, one conflict reaches A2, and the first
  `violatingPairsCounted` call consumes the 65,536-unit verification-work cap
  mid-predicate (512·511/2 = 130,816 pairs), so the in-flight attempt is rejected with
  the flag set and the residual equals the complete initial detection.
- `verificationDetectionStopFixture()`: the verification-detection stop. Tune a
  wide-span fan so the initial detection's `indexInsertedItems` completes between
  one-half and one times the cap (observable through the counted accessors), keep at
  least one crossing pair so a candidate exists, and let the `C_out` detection re-collect
  and trip the item budget; if the item cap cannot be reached this way, tune the fixture
  so the candidate's positions pile the chords into shared cells and trip the pair-check
  budget. The essential assertions are `verificationBudgetExhausted == true`,
  `detectionTruncated == false` and the exact-residual equality; tune the counts, never
  the semantics.

- [ ] **Step 5: Run the new relocation fixtures and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava` for `detectionTruncated()`/`verificationWork()`
before Step 3, then FAIL on the new assertions (the unbounded Task 13-15 code detects
everything and never sets the flags) before Step 6.

- [ ] **Step 6: Implement the bounded relocation detection and verification**

1. **Per-frame counters and reset.** Keep the existing instance counters
   (`indexInsertedItems`, `indexPairChecks`, `indexSubdivisions`,
   `indexBudgetExhausted`) and add `detectedKeys`, `verificationWork`,
   `detectionTruncated`, `verificationBudgetExhausted`. `resetIndexCounters()` (rename
   to `resetFrameCounters()`) resets every one of them at the top of `apply`.
2. **Budget-aware detection.** `detect(projection, positions, geometry, visibleEndpoints)`
   becomes the counted per-frame detection. `collect(...)`, `insertChord(...)`,
   `insertDisc(...)` and `addCell(...)` call the item-budget check **before every cell
   write**, so every subdivision re-collect is checked too (the re-collect is another
   `collect` pass). `DetectionAccumulator.examine...` and the outermost direct pass call
   the pair-check check **before every `properlyCross`/`segmentDistance` test**. The
   accumulation checks the detected-key budget when a distinct key is added. A stop
   abandons the rest of the pass and returns the ascending distinct keys accumulated so
   far, with the accumulated C1/C2 counts; the caller records which side stopped. The
   direct full-enumeration fallback still sets `indexBudgetExhausted` (subdivision
   floor/depth reached, or the outermost direct pass ran); it is set independently of the
   truncation and a stop inside the fallback may carry both flags.
3. **Counted verification.** Give `acceptsA1A2A3A4` a three-way outcome
   (`ACCEPT`, `REJECT`, `DETECTION_STOP`). A2 calls `violatingPairsCounted` for the
   current and the candidate positions with the frame's remaining verification-work
   allowance, adds both `pairEvaluations()` to `verificationWork`, and returns
   `DETECTION_STOP` when either result is `truncated()`. A3 counts every hull-pair
   comparison and every `recomputeHulls(...)` under the same counter (add a
   package-private counted overload on `BoundaryInvariants` used by `EdgeConflictCorrection`
   and keep the public `detectPairKeys(projection, geometry)` unbudgeted for `describe`);
   a candidate that exhausts the counter is a `DETECTION_STOP`. The §5.4 rebuild uses the
   unbounded variants and adds its `detect`/`project` predicate work to `verificationWork`;
   it always runs to completion.
4. **Round loop.** Implement the spec §5.3 pinned control flow verbatim, including the
   guard check before each attempt:

```text
detectionTruncated = false; verificationStopped = false
round = 0; acceptedAny = false; verificationBudgetExhausted = false
while (round < MAX_RELOCATION_ROUNDS && !detectionTruncated && !verificationStopped
        && verificationCount < MAX_VERIFICATIONS_PER_FRAME) {
    conflicts = detectConflicts(currentPositions, currentGeometry)     // counted; a detection-side
                                                                       // stop sets detectionTruncated
    if (detectionTruncated || conflicts.isEmpty()) break
    pool = generateCandidates(conflicts)                                // canonical order
    acceptedThisRound = false
    while (pool not empty && !verificationStopped
            && verificationCount < MAX_VERIFICATIONS_PER_FRAME) {
        batch = greedyDisjoint(pool); pool -= batch
        if (batch empty) break
        attempts = [batch] + batch.singlesInCanonicalOrder()
        for attempt in attempts {
            verificationCount++                                         // counted before evaluation
            outcome = acceptsA1A2A3A4(attempt)                          // counted; DETECTION_STOP
                                                                        // rejects the attempt
            if (outcome == ACCEPT) {
                apply(attempt); currentPositions = result; currentGeometry = recomputedGeometry
                rounds++; acceptedAny = true; acceptedThisRound = true
                recordRepair(attempt)
                if (verificationCount >= MAX_VERIFICATIONS_PER_FRAME) {
                    verificationStopped = true      // accepted cap attempt; the final detection
                                                    // below sets the flag if conflicts remain
                }
                break
            }
            if (outcome == DETECTION_STOP || verificationWorkAtCap) {
                verificationStopped = true; verificationBudgetExhausted = true  // rejected attempt;
                                                                                // C_out never truncated
                break
            }
        }
        if (acceptedThisRound || verificationStopped) break
    }
    if (!acceptedThisRound) break
}
residual = detectionTruncated ? conflicts                                  // accumulated lower bound
         : rounds > 0 ? detectConflicts(currentPositions, currentGeometry) // complete final detection
         : conflicts
if (verificationStopped && !residual.isEmpty() && !detectionTruncated) {
    verificationBudgetExhausted = true
}
```

The top-of-loop detection is the initial detection on the first iteration and a
verification detection on later iterations; on a verification-detection stop
(`detectionStopped` during a non-initial detection) set `verificationStopped` and
`verificationBudgetExhausted` and keep `detectionTruncated` false. When
`detectionTruncated` is set, skip the attempts, the final detection and the §5.4 rebuild,
and publish the accumulated set as both the detected and the residual set. When
`rounds > 0`, run the unbudgeted final detection and the `describe` rebuild exactly as
Task 15 does; otherwise copy the last complete detection and the boundary result.
5. **Diagnostics composition.** `EdgeConflictDiagnostics` is built with the new flags
   and counters: on an initial truncation `crossingsResolved = edgeOverNodeResolved = 0`
   and `verificationWork` still reports the counted work consumed so far.

- [ ] **Step 7: Run the relocation suite and the counted-work bounds**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.NodeSeparationProjectionShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the six new tests pass with the pinned flags, and the
Task 13-15 suites stay green (the 65-chord fixture keeps `indexSubdivisions = 4`,
`indexBudgetExhausted = true`, `crossingsDetected = 2080`, and its ≈ 39,440 item writes
and 2,080 pair checks stay inside the pins).

- [ ] **Step 8: Add the router truncation fixtures first**

Add these tests to `EdgeRouterShould` (fixtures generated programmatically on
`EdgeRouterFixture`; all assertions counted work):

```java
    @Test
    public void boundsTheRouterCountedWorkOnADenseFrame() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.referenceScaleFan(500);
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.diagnostics().routeIndexItems())
            .isLessThanOrEqualTo(EdgeRouter.EDGE_ROUTING_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(paths.diagnostics().routeObstacleChecks())
            .isLessThanOrEqualTo(EdgeRouter.EDGE_ROUTING_MAX_OBSTACLE_CHECKS_PER_FRAME);
        assertThat(paths.diagnostics().routePairChecks())
            .isLessThanOrEqualTo(EdgeRouter.EDGE_ROUTING_MAX_PAIR_CHECKS_PER_FRAME);
    }

    @Test
    public void fallsBackWhenTheObstacleBudgetExhausts() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.denseObstacleFan(400);
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.diagnostics().routeBudgetExhausted()).isTrue();
        assertThat(paths.diagnostics().edgesStraight() + paths.diagnostics().edgesBow()
            + paths.diagnostics().edgesDogleg() + paths.diagnostics().edgesFallback())
            .isEqualTo(paths.byEdge().size());
        assertThat(paths.diagnostics().routeObstacleChecks())
            .isLessThanOrEqualTo(EdgeRouter.EDGE_ROUTING_MAX_OBSTACLE_CHECKS_PER_FRAME);
    }

    @Test
    public void truncatesTheResidualPassAndPublishesALowerBound() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.referenceScaleFan(513);
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePaths bruteForce = EdgeRouter.routeUnbounded(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.diagnostics().detectionTruncated()).isTrue();
        assertThat(paths.diagnostics().routePairChecks())
            .isLessThanOrEqualTo(EdgeRouter.EDGE_ROUTING_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(new java.util.TreeSet<String>(bruteForce.diagnostics().pathCrossingsResidual()))
            .containsAll(paths.diagnostics().pathCrossingsResidual());
        assertThat(paths.byEdge()).isEqualTo(bruteForce.byEdge());
    }
```

Fixture shapes: `referenceScaleFan(500)`/`(513)` extend the Task 16 fixtures with the
radius-`600_000` diameters so the query grid's pair tests reach the cap while the index
and obstacle counters stay below theirs; `denseObstacleFan(400)` places ≈ 400 visible
chords and ≈ 400 projected discs so the straight-chord clearance checks alone exceed
131,072 before any rung is selected. `routeUnbounded` is a package-private test seam:
the same `route` code path with the budget caps set to `Long.MAX_VALUE`.

- [ ] **Step 9: Run the new router fixtures and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava` for the new diagnostics accessors before Step 3,
then FAIL on the flags (the Task 16 router is unbounded) before Step 10.

- [ ] **Step 10: Implement the bounded router**

1. **Counters and reset.** Add `routeIndexItems`, `routeObstacleChecks`,
   `routePairChecks`, `routeBudgetExhausted` and `detectionTruncated` locals/state to
   the `route(...)` frame; they reset at the start of every `route` call.
2. **Index items.** `QueryGrid` checks the index-item budget **before every
   primitive-cell insertion** (disc boxes, hull segments and every path sample
   segment/straight chord), during the build and during every selection-time
   `publish(...)`. On exhaustion no further items are inserted; the remaining edges are
   fallbacks, the residual passes use the partial index, and `routeBudgetExhausted` is
   set.
3. **Obstacle checks.** Every `(candidate rung or straight chord, obstacle)` clearance
   evaluation in `clears(...)`/`clearsDiscs(...)`/`clearsPath(...)` consumes one unit,
   checked **before the evaluation**. On exhaustion the current edge and every remaining
   canonical-order edge are published as straight fallbacks **without further obstacle
   checks** and `routeBudgetExhausted` is set; the classification identity still holds.
4. **Pair checks.** Every sample-segment pair test in `crossingPairs(false)`,
   `crossingPairs(true)` and `hullPenetrations(...)` consumes one unit, checked **before
   the test**. On exhaustion the running pass stops, the partial detected/residual/hull
   results are published as lower bounds, and `detectionTruncated` is set; routing
   selection itself keeps its obstacle-only bound. The three passes share the one
   per-frame pair-check counter.
5. **Diagnostics.** `EdgeRouter` builds `EdgeRouteDiagnostics` with the new flags and
   counters; `EdgePaths` is unchanged.

- [ ] **Step 11: Run the router suite and the counted-work bounds**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  --tests "org.freeplane.plugin.graph.geometry.EdgePathShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the three new tests pass and every Task 6/11/16 router
fixture keeps its pinned behaviour (the bounded budgets admit them all).

- [ ] **Step 12: Run mutation probes 17-20 (spec §8.7)**

For each probe: make the named single-line mutation, run the named test with
`-PTestLoggingFull`, confirm the expected failure, restore the file with
`git checkout -- <file>` and confirm `git diff` is empty before the next probe.

### Probe 17: check-after-collect

Mutation: move the index-item cap check after a whole cell/collect write instead of
before every write. Run `truncatesDetectionAtTheItemBudget`. Expected: FAIL because the
item counter overshoots the cap by a whole collect and the fixture no longer truncates
at the counted stop.

### Probe 18: check-after-predicate

Mutation: move the pair-check cap check after the predicate instead of before it. Run
`truncatesDetectionAtThePairBudget`. Expected: FAIL because the pair counter overshoots
the cap by the whole pass and the fixture no longer truncates at the counted stop.

### Probe 19: unbounded router residual pass

Mutation: drop the router residual pass's pair-check budget (leave it unbounded). Run
`truncatesTheResidualPassAndPublishesALowerBound`. Expected: FAIL because
`detectionTruncated` is false and the residual equals the brute-force residual instead
of a lower bound.

### Probe 20: truncate `C_out`

Mutation: truncate `C_out` instead of rejecting the attempt when the verification-work
cap is reached. Run `rejectsTheAttemptWhenVerificationWorkExhausts`. Expected: FAIL
because the truncated candidate detection is accepted (or sets `detectionTruncated`)
instead of rejecting the in-flight attempt with `verificationBudgetExhausted` and the
exact published-position residual.

Record each probe's mutation, the exact failing assertion and the restore result in the
task report.

- [ ] **Step 13: Run the full bounded-policy suite**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould" \
  --tests "org.freeplane.plugin.graph.layout.NodeSeparationProjectionShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundaryInvariantsShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  --tests "org.freeplane.plugin.graph.geometry.EdgeRouterShould" \
  --tests "org.freeplane.plugin.graph.geometry.EdgePathShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Every bounded claim in this task is asserted by counted
work, never wall clock, and the new diagnostics equality over the flags means two
identical runs produce identical truncated sets.

- [ ] **Step 14: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictDiagnostics.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryInvariants.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CountedViolatingPairs.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouteDiagnostics.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java
git commit -m "perf(graph): bound relocation and router work with counted budgets [2026-10-12-graph-edge-crossing-avoidance]"
```
