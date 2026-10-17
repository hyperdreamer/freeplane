# Graph View Initial and Preferred Spacing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking; leave this pinned plan unchanged during execution.

**Goal:** Widen fresh Graph View enclosure content-ring gaps to 24.0 layout units, preserve collision-based relationship targets, and verify the approved first-idle outcome without changing collision clearance or layout behavior beyond the approved amendment.

**Architecture:** Introduce one internal preferred-gap constant in `GraphStreamLayoutEngine` and confine it to the existing content-ring formula, whose consumers propagate the larger ring through seeds, containment targets and recursive packed footprints. Add only the approved `ClusterPacking.isClear` last-ulp tolerance; relationship rest lengths continue to use collision clearance. Inspect actual spring-box registrations and their consumer in test code, compare corrected first-idle frames against an archived unchanged implementation, then verify lifecycle commands and installed-application publications. A failed frozen outcome stops execution before the later evidence work.

**Tech Stack:** Java 8 source/bytecode, Java 21.0.8-zulu build/runtime, installed Gradle, JUnit 4, AssertJ, Mockito, GraphStream 1.3, Python 3, Swing/Java2D, Xvfb and Java Robot for actual application captures. No new runtime dependencies.

**Approved inputs:** `docs/superpowers/specs/2026-10-16-graph-initial-spacing-design.md`; `docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md`, SHA-256 `023bde826c9c6f351e2340d643ce262d9ce1d2a1c97ded0b4b250ae5f7ab80a1`. Task identifier: `graph-initial-spacing`. Old production baseline: `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`.

**Execution graph:** One lane, `spacing`, with Tasks 1 → 2 → 3 → 4 → 5, sequence numbers 1..5. Test support, the parameter, mutations and application evidence share interfaces and must not execute concurrently. There are no cross-lane writes. Each Files block is an exact write allowlist; read-only references are not permission to edit those files. External evidence below `/tmp/graph-initial-spacing-evidence/` is also explicitly allowlisted by task.

**File structure:**

| Owner | File | Responsibility |
| --- | --- | --- |
| Task 1 | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` | One preferred-gap declaration and one substitution in `BoundarySizes.contentRingRadius`; relationship term and `NodeSeparationProjection` import kept |
| Task 1 | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java` | Only production tolerance correction: accept a candidate at the existing 8.0 gutter when arithmetic is one ulp below it |
| Task 1 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java` | Only proven spacing-dependent 11/86 → 20/104 literals |
| Task 1 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java` | Focused exactly-on-the-8.0-gutter regression and unchanged-gutter assertion |
| Task 1 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/SpacingTestSupport.java` | Test fixtures, reflection snapshots, registered-target assertions |
| Task 1 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java` | Independent raw seeds, footprints, registration, consumption, topology, raw reset and pins |
| Task 1 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java` | Exact approved appendix A bytes, test only |
| Task 1 | `freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py` | Exact approved appendix B bytes, independent archived comparison |
| Task 2 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/InitialSpacingLifecycleShould.java` | Real coordinator/settle-loop/WorkerStepper/worker command tests |
| Task 3 | `freeplane_plugin_graph/src/test/scripts/mutate_initial_spacing.py` | Disposable single-mechanism negative controls |
| Task 3 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java` | Measured spacing-dependent first-idle golden and bounded hold envelope for the two-map regression only |
| Task 3 | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java` | Preferred-gap comment and fixture-D last-ulp residual-tolerant assertion only |
| Task 4 | `freeplane_plugin_graph/src/test/evidence/initial-spacing/SpacingUiAgent.java` | Test-only installed-application trace and synchronized screen captures |
| Task 4 | `freeplane_plugin_graph/src/test/evidence/initial-spacing/capture.py` | Frozen source files, isolated profiles, installed runtime launcher and receipt checks |
| Task 5 | `docs/superpowers/evidence/2026-10-16-graph-initial-spacing.md` | Requirement-to-receipt acceptance record with honest gate statuses |

## Global Constraints

- The production diff is confined to exactly these two files: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` and `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java`.
- Declare one package-private internal `static final double PREFERRED_NODE_GAP = 24.0` beside `NODE_RADIUS`.
- Substitute `PREFERRED_NODE_GAP` for `NodeSeparationProjection.MIN_GAP` in `BoundarySizes.contentRingRadius` only.
- Keep `NodeSeparationProjection.MIN_GAP` in the relationship `ForceLink` rest-length expression in `topology`; keep the `NodeSeparationProjection` import.
- In `ClusterPacking.isClear`, use `clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON`; this accepts the existing 8.0 gutter at a one-ulp arithmetic boundary without widening the gutter.
- There is one active preferred-gap parameter. Apart from the authorized `ClusterPacking.isClear` tolerance correction, no production algorithm, class interface, factory, command path, dependency, setting, persistence field, serialization format, migration, fallback, or public API changes. Do not restore obsolete `GROUP_SPACING` or `SUB_GROUP_SPACING` constants. Test-only observation and comparison code is permitted during implementation; it must not enter the plugin bundle.
- Relationship rest lengths are not widened. Collision clearance, `NodeSeparationProjection.MIN_GAP`: 6.0.
- Footprint packing gutter, `BoundarySizes.SIBLING_GAP`: 8.0.
- Aggregate cross-map displacement cap: 0.005 per affected particle per engine step.
- Production idle policy: 8 consecutive frames; RMS <= 0.05; maximum displacement <= 0.10.
- Rendered base node radius: 8.0, scaled by the existing prominence.
- Footprint node settle margin / frame clearance: 8.0 / 16.0.
- Existing boundary padding / hull clearance: 8.0 / 16.0.
- `LayoutCalibration.spikeDefaults()`: containment 1.0; hierarchy 0.30; relationship (`sameMap`) 1.0.
- Typed attraction / repulsion / boundary repulsion: 0.05 / 16.0 / 0.5.
- Pins, worker ownership, correction budgets, publication fencing: Existing mechanisms and limits.
- Zoom, node/label rendering, projection semantics: Existing mechanisms.
- Keep `GraphStreamLayoutEngine` package-private. Do not expose getters or mutation hooks in production, widen module exports, or make private topology/seed classes public.
- Required tests are JUnit 4/AssertJ, Java 8 source/bytecode target.
- Preserve the existing `<8.0` one-step movement checks, cross-map cap, 200-enclosure **<=3.0-second** submission assertion and its **5-second** future await. Keep existing safety/coverage/pin/determinism and performance thresholds. Only spacing-derived numeric goldens may change, supported by old/new measured receipts and a derivation explaining the spacing dependency.
- Geometry comparisons here use absolute tolerance `1e-9`; the outcome-improvement tolerance in section 7 is separately `1e-6`.
- For **each of the four qualifying enclosure results** (NR8 A, SM8 A, XM4x2 A, XM4x2 B), require `delta = candidateMedianAtItsOwnFirstIdle - baselineMedianAtItsOwnFirstIdle`; PASS iff `delta >= 6.0 - 1e-6`.
- Apply tolerance once to the difference. Do not average across enclosures or cases. Do not round before comparison.
- Submission is worker step count 0. At most **2000 calls to `step()`** follow it; a first idle returned by call 2000 is within the count limit.
- Use one monotonic 30-second deadline per fixture/version, starting immediately before construction of its fresh worker and submission. Request submission, engine initialization, queueing, corrections, future waits, intervening frame inspection/collection, and endpoint validation share that deadline.
- After freezing first idle, deliberately request **the next 100 sequential worker frames** from the same worker, without reset/restart/resubmission. Give this diagnostic window its own monotonic 30-second deadline; identify offsets 1..100 and absolute step counts.
- A failed worker frame can retain a previous frame's `idle` flag: always test `failed()` **before** interpreting idle. No failed, unverified, missing-coverage, or non-finite frame can establish acceptance.
- No random generator, search for favorable IDs, fixture-size reduction, edge removal, prominence override, pin, coefficient change, or per-version adjustment is permitted to make the comparison pass.
- If any required fixture fails to converge, improve or remain safe, keep its exact inputs and failed receipts and return the finding to design review. Do not weaken the frozen acceptance contract or expand production logic under this specification.
- Every headless command runs from the verified candidate root with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`, that `bin` prepended to inherited `PATH`, and `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`; use installed `gradle`, with repository-required escalation where supported. UI processes instead use a valid recorded X display and remove forced headless.
- Exact specification source is `docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md`, SHA-256 `023bde826c9c6f351e2340d643ce262d9ce1d2a1c97ded0b4b250ae5f7ab80a1`. Read its sections 1–11 and appendices before implementing any task. Baseline is `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`; never substitute a formula or the candidate for that old tree.
- Evidence root is `/tmp/graph-initial-spacing-evidence`; preserve failed receipts and use fresh, explicitly numbered attempt directories after an identified environmental correction. Do not select successful attempts or overwrite failures.
- Existing `reports/spec-materialization-2/` under PM run `pm-run-20261016-125451-a5048653` proves unchanged-layout negative controls only. It supplies no candidate feature pass. Do not repeat its six unchanged runs or its 31-second delay experiment as setup.
- The frozen outcome acceptance predicate is unchanged: for each of NR8 A, SM8 A, XM4x2 A and XM4x2 B, `candidateMedianAtItsOwnFirstIdle - baselineMedianAtItsOwnFirstIdle >= 6.0 - 1e-6`, with at most 2000 calls to `step()` after submission and one shared monotonic 30-second deadline per fixture/version.
- Gate failures stop dependent tasks. Commits below are execution instructions, not authorization to implement or commit during plan drafting. Every task commit includes `graph-initial-spacing`; do not push or edit PM state.

## Task 1: Pin independent geometry and live targets, widen the parameter, and run the frozen outcome gate

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 1

**Depends on:** none

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:23-25,40-42,232-245,500-513`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java:isClear`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java:70-86`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/SpacingTestSupport.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java`
- Create: `freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py`
- Create evidence only: `/tmp/graph-initial-spacing-evidence/task-1/` (including archived baseline, build/init script, classpaths, exact patch/source/dependency hashes, raw TSVs and `comparison.json` emitted by the approved comparator).

**Interfaces:**

- Consumes: existing `LayoutEngine.apply(LayoutRequest): LayoutFrame`, `step(): LayoutFrame`, `reset(): void`, `close(): void`; `GraphStreamLayoutFactory.create(LayoutCalibration): LayoutEngine`; `GraphProjection.projected(long,List<ProjectedNode>,List<ProjectedEnclosure>,List<ProjectedEdge>,List<RelationshipResolution>,List<PinProjection>): GraphProjection`; `ProjectionDiff.between(GraphProjection,GraphProjection): ProjectionDiff`.
- Consumes: approved appendix A `InitialSpacingProbe.main(String[]): void`, invoked as `[fixture, outputDirectory]`, and appendix B CLI `python3 compare_initial_spacing.py <candidateRoot> <observerJava> <outputDirectory>` (exit 0 only for a complete passing comparison).
- Produces: `SpacingTestSupport.ring(long,int,boolean): GraphProjection`, `targets(boolean): GraphProjection`, `request(WorkspaceId,GraphProjection,GraphProjection,List<PinProjection>): LayoutRequest`, `inspect(LayoutEngine): Snapshot`, `requireTargets(Snapshot,double,boolean,double...): void`; public test-only `Snapshot` contains detached node/anchor positions, particle radii and `List<Link>` with semantic endpoints, `kind`, `crossMap`, `restLength`. `Link` carries no live mutation handle. Detailed declarations are below.
- Produces: one internal production constant, no new production interface; complete archived baseline/candidate comparison at `/tmp/graph-initial-spacing-evidence/task-1/comparison/comparison.json`. Later tasks require its `status == "PASS"`; the four literal/target tests passing is a separate gate.

**Validation commands:** Resolve `PATH` by prepending the stated Java `bin` to inherited PATH, not by passing a literal `$PATH` string. All vectors use `cwd = candidateRoot` and the headless environment from Global Constraints.

```json
[
  ["gradle", ":freeplane_plugin_graph:test", "--tests", "*InitialSpacingShould", "--tests", "*BoundaryFootprintShould", "--tests", "*ClusterPackingShould", "--console=plain", "-PTestLoggingFull"],
  ["python3", "freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py", ".", "freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java", "/tmp/graph-initial-spacing-evidence/task-1/comparison"]
]
```

### Step 1: Materialize the frozen executable code without editing it

- [ ] Verify the spec digest and extract the two exact fenced sources. This is a deterministic source operation, not a new observer design. The same observer bytes must compile against both versions. Use this complete extraction program from the repository root:

```python
from pathlib import Path
import hashlib, re
spec = Path('docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md')
raw = spec.read_bytes()
assert hashlib.sha256(raw).hexdigest() == '023bde826c9c6f351e2340d643ce262d9ce1d2a1c97ded0b4b250ae5f7ab80a1'
for marker, lang, path in [
    ('spacing-probe-java', 'java', 'freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java'),
    ('spacing-compare-python', 'python', 'freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py')]:
    matches = re.findall(r'<!-- ' + marker + r' -->\s*```' + lang + r'\n(.*?)\n```', raw.decode(), re.S)
    assert len(matches) == 1, marker
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((matches[0] + '\n').encode())
    assert target.read_bytes() == (matches[0] + '\n').encode()
```

### Step 2: Add test fixtures and detached inspection of actual registrations

- [ ] Create `SpacingTestSupport.java` with the following complete code. Reflection stays in test sources; the returned snapshot is detached, keyed by the same semantic projected node/hull objects used in requests. Missing registrations fail assertions, rather than silently returning default rests.

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import java.lang.reflect.Field;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.*;
import org.freeplane.plugin.graph.workspace.model.*;

public final class SpacingTestSupport {
    public static final MapReferenceId A = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    public static final MapReferenceId B = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    public static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-000000001604");
    private SpacingTestSupport() { }
    public static Object read(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }
    public static void write(Object object, String name, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }
    public static NodeReference ref(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }
    public static ProjectedNodeKey node(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(ref(map, id)));
    }
    public static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(ref(map, id)))));
    }
    public static ProjectedEnclosure enclosure(MapReferenceId map, String id,
            EnclosureHullKey parent, List<ProjectedNodeKey> nodes, List<EnclosureHullKey> children) {
        EnclosureHullKey key = hull(map, id);
        return ProjectedEnclosure.of(key, key.endpointKeys(), Collections.singletonList(SafeNodeLabel.of(id, id)),
            map.equals(A) ? "Map A" : "Map B", Optional.ofNullable(parent), nodes, children,
            parent == null, parent == null ? BoundaryTier.EMPHATIC : BoundaryTier.SUBTLE);
    }
    public static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> hulls, List<ProjectedEdge> edges, List<PinProjection> pins) {
        return GraphProjection.projected(generation, nodes, hulls, edges,
            Collections.<RelationshipResolution>emptyList(), pins);
    }
    public static GraphProjection empty(long generation) {
        return projection(generation, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<PinProjection>emptyList());
    }
    public static GraphProjection ring(long generation, int count, boolean mixed) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < count; i++) {
            String id = mixed ? "m" + i : "u" + i;
            ProjectedNodeKey key = node(A, id);
            direct.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(id, id), "Map A", true));
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        if (mixed) {
            edges.add(edge(ProjectedEndpointKey.ofNode(direct.get(0)), ProjectedEndpointKey.ofNode(direct.get(1)), true, 16041));
            edges.add(edge(ProjectedEndpointKey.ofNode(direct.get(0)), ProjectedEndpointKey.ofNode(direct.get(2)), true, 16042));
        }
        return projection(generation, nodes, Collections.singletonList(enclosure(A,
            mixed ? "mixed-root" : "ring-root", null, direct, Collections.<EnclosureHullKey>emptyList())),
            edges, Collections.<PinProjection>emptyList());
    }
    public static SourceNodeKey source(ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get().source() : endpoint.enclosure().get().source();
    }
    public static ProjectedEdge edge(ProjectedEndpointKey from, ProjectedEndpointKey to,
            boolean forward, int sequence) {
        EdgeContributor contributor;
        if (from.mapReferenceId().equals(to.mapReferenceId())) {
            ConnectorDescriptor descriptor = ConnectorDescriptor.of(source(from),
                source(to).persistedReference().get(), false, forward, "", "", "");
            contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor), from, to);
        } else {
            GraphRelationshipRecord record = GraphRelationshipRecord.of(RelationshipId.of(String.format(Locale.ROOT,
                "00000000-0000-0000-0000-%012d", sequence)), sequence - 16040L,
                source(from).persistedReference().get(), source(to).persistedReference().get(),
                forward ? RelationshipDirection.FORWARD : RelationshipDirection.UNDIRECTED,
                Collections.<UnknownXml>emptyList());
            contributor = EdgeContributor.graphRelationship(record, from, to);
        }
        return ProjectedEdge.of(ProjectedEdgeKey.of(from, to), Collections.singletonList(contributor));
    }
    public static GraphProjection targets(boolean crossMap) {
        MapReferenceId rightMap = crossMap ? B : A;
        EnclosureHullKey root = hull(A, "target-root"), left = hull(A, "left"), right = hull(rightMap, "right");
        List<ProjectedNodeKey> keys = Arrays.asList(node(A, "p0"), node(A, "p1"),
            node(rightMap, "q0"), node(rightMap, "q1"));
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (ProjectedNodeKey key : keys) {
            String id = key.source().persistedReference().get().nodeId().value();
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(id, id), key.mapReferenceId().equals(A) ? "Map A" : "Map B", true));
        }
        List<ProjectedEnclosure> hulls = new ArrayList<ProjectedEnclosure>();
        if (!crossMap) hulls.add(enclosure(A, "target-root", null, Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(left, right)));
        hulls.add(enclosure(A, "left", crossMap ? null : root, keys.subList(0, 2), Collections.<EnclosureHullKey>emptyList()));
        hulls.add(enclosure(rightMap, "right", crossMap ? null : root, keys.subList(2, 4), Collections.<EnclosureHullKey>emptyList()));
        List<ProjectedEdge> edges = Arrays.asList(
            edge(ProjectedEndpointKey.ofNode(keys.get(0)), ProjectedEndpointKey.ofNode(keys.get(2)), false, 16041),
            edge(ProjectedEndpointKey.ofNode(keys.get(1)), ProjectedEndpointKey.ofEnclosure(right.endpointKeys().get(0)), false, 16042),
            edge(ProjectedEndpointKey.ofEnclosure(left.endpointKeys().get(0)), ProjectedEndpointKey.ofEnclosure(right.endpointKeys().get(0)), false, 16043));
        return projection(1, nodes, hulls, edges, Collections.<PinProjection>emptyList());
    }
    public static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        return PinProjection.active(PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList()), node);
    }
    public static LayoutRequest request(WorkspaceId workspace, GraphProjection before,
            GraphProjection after, List<PinProjection> pins) {
        return LayoutRequest.of(workspace, after, ProjectionDiff.between(before, after), pins);
    }
    public static final class Link {
        public final Object first, second;
        public final String kind;
        public final boolean crossMap;
        public final double restLength;
        Link(Object first, Object second, String kind, boolean crossMap, double rest) {
            this.first = first; this.second = second; this.kind = kind;
            this.crossMap = crossMap; this.restLength = rest;
        }
    }
    public static final class Snapshot {
        public final LayoutPositions positions;
        public final Map<Object, Double> radii;
        public final List<Link> links;
        Snapshot(LayoutPositions positions, Map<Object, Double> radii, List<Link> links) {
            this.positions = positions;
            this.radii = Collections.unmodifiableMap(new LinkedHashMap<Object, Double>(radii));
            this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
        }
    }
    @SuppressWarnings("unchecked")
    public static Snapshot inspect(LayoutEngine engine) {
        Map<String, GraphStreamLayoutEngine.ParticleState> particles =
            (Map<String, GraphStreamLayoutEngine.ParticleState>) read(engine, "particles");
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        Map<Object, Double> radii = new LinkedHashMap<Object, Double>();
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        for (GraphStreamLayoutEngine.ParticleState particle : particles.values()) {
            Object key = particle.nodeKey != null ? particle.nodeKey : particle.anchorKey;
            assertThat(key).as("particle semantic key").isNotNull();
            keys.put(particle.id, key); radii.put(key, particle.radius);
            LayoutPoint position = LayoutPoint.of(particle.x, particle.y);
            if (particle.nodeKey != null) nodes.put(particle.nodeKey, position);
            else anchors.put(particle.anchorKey, position);
        }
        TypedSpringBox box = (TypedSpringBox) read(engine, "springBox");
        Map<String, GraphStreamLayoutEngine.ForceLink> registered =
            (Map<String, GraphStreamLayoutEngine.ForceLink>) read(box, "typedLinks");
        List<Link> links = new ArrayList<Link>();
        for (GraphStreamLayoutEngine.ForceLink link : registered.values()) {
            assertThat(keys).containsKeys(link.firstId, link.secondId);
            assertThat(link.firstId).isNotEqualTo(link.secondId);
            links.add(new Link(keys.get(link.firstId), keys.get(link.secondId), link.kind.name(), link.crossMap, link.restLength));
        }
        return new Snapshot(LayoutPositions.of(nodes, anchors), radii, links);
    }
    public static void requireTargets(Snapshot snapshot, double containment, boolean cross, double... relationship) {
        List<Double> actual = new ArrayList<Double>();
        int contained = 0;
        for (Link link : snapshot.links) {
            if (link.kind.equals("RELATIONSHIP")) { assertThat(link.crossMap).isEqualTo(cross); actual.add(link.restLength); }
            if (link.kind.equals("CONTAINMENT")) { assertThat(link.restLength).isCloseTo(containment, offset(1e-9)); contained++; }
        }
        Collections.sort(actual);
        double[] expected = relationship.clone(); Arrays.sort(expected);
        assertThat(actual).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) assertThat(actual.get(i)).isCloseTo(expected[i], offset(1e-9));
        assertThat(contained).isEqualTo(snapshot.positions.nodes().size());
    }
}
```

### Step 3: Write literal geometry, live-registration and force-consumer tests

- [ ] Create `InitialSpacingShould.java` with this code. `inspect()` reads the engine's actual registered links. The consumption experiment uses a real MultiGraph edge and a live TypedSpringBox particle before integration/clamping. The separate numeric outcome gate below cannot be replaced by these tests.

```java
package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.freeplane.plugin.graph.layout.graphstream.SpacingTestSupport.*;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.workspace.model.*;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.geom.Vector3;
import org.junit.Test;

public class InitialSpacingShould {
    private static final double EPS = 1e-9;
    private static LayoutEngine engine() { return GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults()); }
    private static double distance(LayoutPoint a, LayoutPoint b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
    private static WorkspaceId workspace(boolean cross) { return WorkspaceId.of(cross
        ? "00000000-0000-0000-0000-000000001606" : "00000000-0000-0000-0000-000000001605"); }
    @Test public void pinLiteralFreshRingsAndFootprints() {
        int[] counts = {0, 1, 2, 4, 8};
        double[] rings = {0, 0, 20, 28.284271247461902, 52.26251859505506};
        double[] widths = {160, 64, 104, 120.5685424949238, 168.52503719011014};
        for (int c = 0; c < counts.length; c++) {
            GraphProjection p = ring(1, counts[c], false);
            ProjectedEnclosure h = p.enclosures().get(0);
            GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
            assertThat(sizes.contentRingRadius(h.hullKey())).isCloseTo(rings[c], offset(EPS));
            assertThat(sizes.footprint(h.hullKey()).width).isCloseTo(widths[c], offset(EPS));
            assertThat(sizes.footprint(h.hullKey()).height).isCloseTo(counts[c] == 0 ? 40 : widths[c], offset(EPS));
            try (LayoutEngine e = engine()) {
                LayoutFrame f = e.apply(request(WORKSPACE, empty(0), p, p.pins()));
                assertThat(f.positions().nodes()).hasSize(counts[c]);
                LayoutPoint anchor = f.positions().anchors().get(h.hullKey());
                for (int i = 0; i < counts[c]; i++) {
                    LayoutPoint v = f.positions().nodes().get(h.directNodes().get(i));
                    double angle = 2 * Math.PI * i / counts[c];
                    assertThat(v.x()).isCloseTo(anchor.x() + rings[c] * Math.cos(angle), offset(EPS));
                    assertThat(v.y()).isCloseTo(anchor.y() + rings[c] * Math.sin(angle), offset(EPS));
                    if (counts[c] > 1) assertThat(distance(v, f.positions().nodes().get(
                        h.directNodes().get((i + 1) % counts[c]))) - 16).isCloseTo(24, offset(EPS));
                }
                requireTargets(inspect(e), rings[c], false);
            }
        }
        try (LayoutEngine e = engine()) {
            LayoutFrame f = e.apply(request(WORKSPACE, empty(0), empty(1), Collections.<PinProjection>emptyList()));
            assertThat(f.positions().nodes()).isEmpty(); assertThat(f.positions().anchors()).isEmpty();
            assertThat(inspect(e).links).isEmpty();
        }
    }
    @Test public void computeMixedProminenceAndConservativeAdjacentGaps() {
        GraphProjection p = ring(1, 3, true);
        double[] radii = {9.6, 8, 8}, gaps = {25.6, 27.2, 25.6};
        try (LayoutEngine e = engine()) {
            LayoutFrame f = e.apply(request(WORKSPACE, empty(0), p, p.pins()));
            for (int i = 0; i < 3; i++) {
                ProjectedNodeKey key = p.nodes().get(i).key();
                assertThat(p.prominence().get(key).visibleOutgoingTargets()).isEqualTo(i == 0 ? 2 : 0);
                assertThat(inspect(e).radii.get(key)).isCloseTo(radii[i], offset(EPS));
                assertThat(distance(f.positions().nodes().get(key), f.positions().anchors().get(p.enclosures().get(0).hullKey())))
                    .isCloseTo(24.941531628991836, offset(EPS));
                assertThat(distance(f.positions().nodes().get(key), f.positions().nodes().get(p.nodes().get((i + 1) % 3).key()))
                    - radii[i] - radii[(i + 1) % 3]).isCloseTo(gaps[i], offset(EPS));
            }
            requireTargets(inspect(e), 24.941531628991836, false, 23.6, 23.6);
        }
    }
    @Test public void registerAllEndpointAndMapVariantsAndConsumeTheirActualRests() {
        for (boolean cross : new boolean[] {false, true}) {
            GraphProjection p = targets(cross);
            try (LayoutEngine e = engine()) {
                e.apply(request(workspace(cross), empty(0), p, p.pins()));
                requireTargets(inspect(e), 20, cross, 22, 34, 46);
                if (!cross) {
                    assertThat(attraction(registered(e, "RELATIONSHIP", 22), 30)).isCloseTo(0.4, offset(EPS));
                    assertThat(attraction(new GraphStreamLayoutEngine.ForceLink("x", "y",
                        GraphStreamLayoutEngine.ForceKind.RELATIONSHIP, false, 40), 30)).isCloseTo(-0.5, offset(EPS));
                    assertThat(attraction(new GraphStreamLayoutEngine.ForceLink("x", "y",
                        GraphStreamLayoutEngine.ForceKind.RELATIONSHIP, false, 40), 30) - attraction(
                        registered(e, "RELATIONSHIP", 22), 30)).isCloseTo(-0.9, offset(EPS));
                    assertThat(attraction(registered(e, "CONTAINMENT", 20), 15)).isCloseTo(-0.25, offset(EPS));
                    assertThat(attraction(new GraphStreamLayoutEngine.ForceLink("x", "y",
                        GraphStreamLayoutEngine.ForceKind.CONTAINMENT, false, 11), 15)).isCloseTo(0.20, offset(EPS));
                }
                e.step(); requireTargets(inspect(e), 20, cross, 22, 34, 46);
                e.reset();
                LayoutFrame reset = e.apply(request(workspace(cross), p, p, p.pins()));
                requireTargets(inspect(e), 20, cross, 22, 34, 46);
                try (LayoutEngine fresh = engine()) {
                    assertThat(reset.positions()).isEqualTo(fresh.apply(request(workspace(cross), empty(0), p, p.pins())).positions());
                }
            }
        }
    }
    @SuppressWarnings("unchecked") private static GraphStreamLayoutEngine.ForceLink registered(LayoutEngine e, String kind, double rest) {
        TypedSpringBox box = (TypedSpringBox) read(e, "springBox");
        for (GraphStreamLayoutEngine.ForceLink link : ((Map<String, GraphStreamLayoutEngine.ForceLink>) read(box, "typedLinks")).values())
            if (link.kind.name().equals(kind) && Math.abs(link.restLength - rest) < EPS) return link;
        throw new AssertionError("missing registered " + kind + " rest " + rest);
    }
    @SuppressWarnings("unchecked") private static double attraction(GraphStreamLayoutEngine.ForceLink link, double distance) {
        MultiGraph graph = new MultiGraph("consumption");
        TypedSpringBox box = new TypedSpringBox(LayoutCalibration.spikeDefaults(), new Random(1));
        graph.addSink(box);
        graph.addNode(link.firstId); graph.addNode(link.secondId);
        box.setParticlePosition(link.firstId, 0, 0); box.setParticlePosition(link.secondId, distance, 0);
        graph.addEdge("test", link.firstId, link.secondId, false); box.registerLink("test", link);
        TypedNodeParticle particle = ((Map<String, TypedNodeParticle>) read(box, "typedParticles")).get(link.firstId);
        assertThat(particle).isNotNull();
        Vector3 displacement = new Vector3(), cross = new Vector3();
        box.addTypedAttraction(particle, displacement, cross);
        graph.removeSink(box);
        assertThat(displacement.at(1)).isCloseTo(0, offset(EPS)); assertThat(cross.at(0)).isCloseTo(0, offset(EPS));
        return displacement.at(0);
    }
    @Test public void retainAllSurvivorsAcrossRealTopologyAndProminenceChanges() {
        GraphProjection before = ring(1, 2, false);
        GraphProjection mixed = ring(2, 3, true);
        // Keep the same persisted u0/u1 identities: mixed edges must change real prominence, not the node IDs.
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (int i = 0; i < 3; i++) nodes.add(ProjectedNode.of(node(A, "u" + i),
            org.freeplane.plugin.graph.projection.input.SafeNodeLabel.of("u" + i, "u" + i), "Map A", true));
        List<ProjectedEdge> edges = Arrays.asList(
            edge(ProjectedEndpointKey.ofNode(node(A,"u0")), ProjectedEndpointKey.ofNode(node(A,"u1")), true,16041),
            edge(ProjectedEndpointKey.ofNode(node(A,"u0")), ProjectedEndpointKey.ofNode(node(A,"u2")), true,16042));
        GraphProjection after = projection(2, nodes, ring(2,3,false).enclosures(), edges, mixed.pins());
        GraphProjection removed = projection(3, nodes.subList(0,2), before.enclosures(), edges.subList(0,1), before.pins());
        assertThat(ProjectionDiff.between(before, after).isEmpty()).isFalse();
        assertThat(ProjectionDiff.between(after, removed).isEmpty()).isFalse();
        try (LayoutEngine e = engine(); LayoutEngine repeat = engine()) {
            e.apply(request(WORKSPACE, empty(0), before, before.pins()));
            repeat.apply(request(WORKSPACE, empty(0), before, before.pins()));
            LayoutFrame retained = e.step(); repeat.step();
            LayoutFrame updated = e.apply(request(WORKSPACE, before, after, after.pins()));
            LayoutFrame repeated = repeat.apply(request(WORKSPACE, before, after, after.pins()));
            assertThat(updated.positions()).isEqualTo(repeated.positions());
            for (ProjectedNodeKey key : retained.positions().nodes().keySet())
                assertThat(updated.positions().nodes().get(key)).isEqualTo(retained.positions().nodes().get(key));
            assertThat(updated.positions().anchors()).isEqualTo(retained.positions().anchors());
            requireTargets(inspect(e), 24.941531628991836, false, 23.6, 23.6);
            assertThat(inspect(e).radii.get(node(A,"u0"))).isCloseTo(9.6,offset(EPS));
            e.apply(request(WORKSPACE, after, removed, removed.pins()));
            assertThat(inspect(e).positions.nodes()).doesNotContainKey(node(A,"u2"));
            assertThat(inspect(e).radii.get(node(A,"u0"))).isEqualTo(8.0);
            requireTargets(inspect(e), 20, false, 22);
        }
    }
    @Test public void honorConflictingPinsWithoutHidingResiduals() throws Exception {
        GraphProjection p = ring(1,2,false);
        List<PinProjection> pins = Arrays.asList(pin(node(A,"u0"),0,0),pin(node(A,"u1"),20,0));
        LayoutRequest r = request(WORKSPACE,empty(0),p,pins);
        try (LayoutEngine e = engine()) {
            checkPins(e.apply(r)); checkPins(e.step());
            e.reset(); checkPins(e.apply(r));
            checkPins(e.apply(request(WORKSPACE,p,ring(2,3,false),pins)));
        }
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            LayoutFrame f = worker.submit(r).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
            checkPins(f); assertThat(f.failed()).isFalse(); assertThat(f.verified()).isTrue();
            assertThat(f.residualViolations()).isGreaterThan(0);
            assertThat(NodeSeparationProjection.violatingPairs(p,f.positions())).isNotEmpty();
            checkPins(worker.step().toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    private static void checkPins(LayoutFrame f) {
        assertThat(f.positions().nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
        assertThat(f.positions().nodes().get(node(A,"u1"))).isEqualTo(LayoutPoint.of(20,0));
    }
    @Test public void preserveZeroEndpointRadiusAndPackedHierarchyRules() {
        ProjectedNodeKey a=node(A,"a"), b=node(A,"b");
        EnclosureHullKey root=hull(A,"parent"), child=hull(A,"child");
        GraphProjection p=projection(1,Arrays.asList(
            ProjectedNode.of(a,org.freeplane.plugin.graph.projection.input.SafeNodeLabel.of("a","a"),"Map A",true),
            ProjectedNode.of(b,org.freeplane.plugin.graph.projection.input.SafeNodeLabel.of("b","b"),"Map A",true)),
            Arrays.asList(enclosure(A,"parent",null,Collections.<ProjectedNodeKey>emptyList(),Collections.singletonList(child)),
                enclosure(A,"child",root,Collections.singletonList(a),Collections.<EnclosureHullKey>emptyList()),
                enclosure(A,"other",null,Collections.singletonList(b),Collections.<EnclosureHullKey>emptyList())),
            Arrays.asList(edge(ProjectedEndpointKey.ofNode(b),ProjectedEndpointKey.ofEnclosure(child.endpointKeys().get(0)),false,16041),
                edge(ProjectedEndpointKey.ofEnclosure(root.endpointKeys().get(0)),ProjectedEndpointKey.ofEnclosure(child.endpointKeys().get(0)),false,16042)),
            Collections.<PinProjection>emptyList());
        try(LayoutEngine e=engine()) {
            e.apply(request(WORKSPACE,empty(0),p,p.pins())); requireTargets(inspect(e),0,false,6,14);
            List<Link> hierarchy=new ArrayList<Link>();
            for(Link l:inspect(e).links) if(l.kind.equals("HIERARCHY")) hierarchy.add(l);
            assertThat(hierarchy).hasSize(1); assertThat(hierarchy.get(0).restLength).isEqualTo(0);
        }
    }
    @Test public void expandRecursiveContentWithoutChangingTheEightUnitGutter() {
        GraphProjection base=targets(true);
        EnclosureHullKey root=hull(A,"outer"),left=hull(A,"left");
        GraphProjection p=projection(1,base.nodes(),Arrays.asList(
            enclosure(A,"outer",null,Arrays.asList(node(A,"p0"),node(A,"p1")),Collections.singletonList(left)),
            enclosure(A,"left",root,Collections.<ProjectedNodeKey>emptyList(),Collections.<EnclosureHullKey>emptyList()),
            base.enclosures().get(1)),base.edges(),base.pins());
        GraphStreamLayoutEngine.BoundarySizes sizes=new GraphStreamLayoutEngine.BoundarySizes(p);
        assertThat(sizes.contentRingRadius(root)).isEqualTo(20);
        assertThat(sizes.footprint(hull(B,"right")).width).isEqualTo(104);
        checkPacking(sizes.placements(root),sizes.footprint(root));
        checkPacking(sizes.topLevelPlacements(),null);
        try(LayoutEngine e=engine()) {
            LayoutFrame f=e.apply(request(WORKSPACE,empty(0),p,p.pins()));
            assertThat(f.positions().anchors()).hasSize(3);
            for(Link link:inspect(e).links) if(link.kind.equals("HIERARCHY"))
                assertThat(link.restLength).isCloseTo(distance(f.positions().anchors().get((EnclosureHullKey)link.first),
                    f.positions().anchors().get((EnclosureHullKey)link.second)),offset(EPS));
        }
    }
    private static void checkPacking(List<ClusterPacking.ClusterPlacement> placed,GraphStreamLayoutEngine.BoundarySizes.Footprint footprint) {
        assertThat(placed).hasSize(2);
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
        for(ClusterPacking.ClusterPlacement p:placed) {
            minX=Math.min(minX,p.x-p.item.width/2);maxX=Math.max(maxX,p.x+p.item.width/2);
            minY=Math.min(minY,p.y-p.item.height/2);maxY=Math.max(maxY,p.y+p.item.height/2);
        }
        ClusterPacking.ClusterPlacement a=placed.get(0),b=placed.get(1);
        double gapX=Math.abs(a.x-b.x)-(a.item.width+b.item.width)/2;
        double gapY=Math.abs(a.y-b.y)-(a.item.height+b.item.height)/2;
        assertThat(Math.max(gapX,gapY)).isGreaterThanOrEqualTo(8-EPS);
        if(footprint!=null) {
            assertThat(footprint.width).isCloseTo(maxX-minX+32,offset(EPS));
            assertThat(footprint.height).isCloseTo(maxY-minY+32,offset(EPS));
            assertThat(footprint.anchorX).isCloseTo(-(minX+maxX)/2,offset(EPS));
            assertThat(footprint.anchorY).isCloseTo(-(minY+maxY)/2,offset(EPS));
            boolean content=false;
            for(ClusterPacking.ClusterPlacement p:placed) if(p.item.width==72&&p.item.height==72) content=true;
            assertThat(content).as("literal radius-20 direct-content square").isTrue();
        }
    }
}
```

### Step 4: Add the focused packing-boundary regression and establish red before the production edit

- [ ] Add `java.lang.reflect.Method` to `ClusterPackingShould` and append this focused test. It calls only the private predicate through reflection so the test can pin the exact one-ulp boundary without adding a production API. The mathematical candidate is exactly at the existing 8.0 gutter; `Math.ulp` makes the computed clearance one ulp below 8.0, as occurs in the real candidate enumeration. The assertion accepts that value only because of `CANDIDATE_EPSILON`, and the existing `twoItemsAreSeparatedByExactlyTheGap` assertion continues to prove that the configured gutter is still 8.0 rather than widened:

```java
    @Test
    public void acceptsAnEightUnitGutterAtTheFloatingPointBoundaryWithoutWideningIt() throws Exception {
        final double gutter = GAP;
        assertThat(gutter).isEqualTo(8.0);
        final ClusterPacking.ClusterItem placedItem = content(10.0, 10.0, 0);
        final ClusterPacking.ClusterItem candidate = content(10.0, 10.0, 1);
        final ClusterPacking.ClusterPlacement placed =
            new ClusterPacking.ClusterPlacement(placedItem, 0.0, 0.0);
        final double halfExtent = (placedItem.width + candidate.width) * 0.5;
        final double candidateX = halfExtent + gutter - Math.ulp(gutter);
        final double measuredGap = Math.abs(candidateX - placed.x) - halfExtent;
        assertThat(measuredGap).isLessThan(gutter);
        assertThat(measuredGap).isGreaterThan(gutter - ClusterPacking.CANDIDATE_EPSILON);

        final Method isClear = ClusterPacking.class.getDeclaredMethod("isClear",
            double.class, double.class, ClusterPacking.ClusterItem.class, List.class, double.class);
        isClear.setAccessible(true);
        final boolean accepted = ((Boolean) isClear.invoke(null, candidateX, 0.0, candidate,
            Collections.singletonList(placed), gutter)).booleanValue();
        assertThat(accepted).isTrue();
    }
```

- [ ] Run the first validation argv, retain stdout/stderr and JUnit XML in `task-1/red/`. Expected red evidence is ring 20 versus old 11, radius-derived footprints, and the focused one-ulp candidate being rejected by the old `isClear`; the relationship assertions must remain 22/34/46 and 23.6, and the attraction controls must already remain `+0.4` for rest 22, `-0.5` for rest 40, and `-0.9` for their difference. A compiler error is not red evidence; correct test API/type mistakes without touching production behavior. Do not run the six-case comparator against unchanged code again.

### Step 5: Apply exactly the approved production change and footprint literals

- [ ] Make this entire production diff, using the original files' exact whitespace. The relationship expression and its import are intentionally unchanged:

```diff
 GraphStreamLayoutEngine.java
@@
     static final double NODE_RADIUS = 8.0;
+    static final double PREFERRED_NODE_GAP = 24.0;
@@
-            return (2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)
+            return (2.0 * maxRadius + PREFERRED_NODE_GAP)
+
ClusterPacking.java
@@
-            if (clearanceX < gap && clearanceY < gap) {
+            if (clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON) {
```

- [ ] Verify in the source diff that `NodeSeparationProjection.MIN_GAP` remains in the relationship `ForceLink` rest-length expression and that its import remains present. No relationship-rest substitution or import removal is allowed.

- [ ] In `BoundaryFootprintShould.leafFootprintIsSquareAroundTheAnchor`, change its ring literal `11.0` to `20.0` and both footprint literals `86.0` to `104.0`. Keep the independent singleton 64, empty-label boxes and no-content nesting literals. The derivation is `R=(16+24)/2=20`, then `2*(20+8+8+16)=104`. No other test golden changes in this task.

### Step 6: Verify literal/target green, then independently require the frozen outcome

- [ ] Rerun the first validation argv. Expected: `InitialSpacingShould`, `BoundaryFootprintShould` and `ClusterPackingShould` pass. Capture test counts, source diff and Java/Gradle versions under `task-1/green/`.
- [ ] Run the second validation argv once against the actual candidate. The unchanged appendix handles `git archive` of the old revision, Java-8 observer compilation against each version, exact input-manifest comparison, corrected own-first-idle measurement, all-pairs independent Python recomputation, four separate six-unit predicates, safety checks, separate complete 100-frame diagnostics, 2000-call/shared-30-second bounds and the 65-second process watchdog. Keep every emitted file, including failed receipts. `comparison.json` must show all four improvements passing. Confirm the ring-only candidate receipts against the measured finding: NR8/A delta `17.529239771648` with first idle step `7`; SM8/A delta `11.456894` with first idle step `39`; XM4x2/A delta `17.043923865606` with first idle step `7`; XM4x2/B delta `17.042998283483` with first idle step `7`; record the observed full-precision values in evidence, but do not hard-code them into production or replace the comparator predicate with these numbers.
- [ ] If either gate fails, stop Task 1 and all dependent tasks with the exact failure/receipt path. For an outcome failure, report a design-review blocker; do not change inputs, force multipliers, idle policy, performance limits or production scope to rescue it. Do not execute UI evidence tasks for a rejected numeric outcome.

### Step 7: Commit the passing task

- [ ] Only after both distinct gates pass, review `git diff` and commit exactly the Task 1 allowlisted repository files:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/SpacingTestSupport.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py
git commit -m "graph-initial-spacing: widen preferred node gap and verify frozen outcome"
```

## Task 2: Exercise real worker lifecycle commands and exact pin retention

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 2

**Depends on:** Task 1

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/InitialSpacingLifecycleShould.java`
- Create evidence only: `/tmp/graph-initial-spacing-evidence/task-2/` (JUnit XML, command output and lifecycle identity/target receipts).

**Interfaces:**

- Consumes: Task 1 `SpacingTestSupport.ring(long,int,boolean): GraphProjection`, `projection(long,List<ProjectedNode>,List<ProjectedEnclosure>,List<ProjectedEdge>,List<PinProjection>): GraphProjection`, `pin(ProjectedNodeKey,double,double): PinProjection`, `edge(ProjectedEndpointKey,ProjectedEndpointKey,boolean,int): ProjectedEdge`, `read(Object,String): Object`, `write(Object,String,Object): void`, `inspect(LayoutEngine): Snapshot`, `requireTargets(Snapshot,double,boolean,double...): void`; `Snapshot.positions: LayoutPositions`, `Snapshot.links: List<Link>` with `Link.first/second: Object`, `kind: String`, `crossMap: boolean`, `restLength: double`.
- Consumes: Task 1 frozen `comparison.json` with `status: "PASS"`; existing `GraphUpdateCoordinator.pauseLayout(): void`, `restartLayout(): void`, `resetLayout(): void`, `acceptBatch(AcceptedBatch): void`, `currentProjection(): GraphProjection`; real private `LayoutSettleLoop.WorkerStepper(LayoutCalibration)` through test-local reflection. Delegate all stepper operations to that real object.
- Produces: JUnit tests proving healthy engine continuity across a barriered pause/Restart resume boundary before the next force integration, reset worker/engine replacement, failed engine replacement/current-projection resubmission, actual wider registered targets and active-pin coordinates through every transition. There is no production interface change.

**Validation commands:** Candidate root, headless environment from Global Constraints:

```json
[
  ["gradle", ":freeplane_plugin_graph:test", "--tests", "*InitialSpacingLifecycleShould", "--tests", "*LayoutWorkerShould", "--tests", "*LayoutSettleLoopShould", "--tests", "*GraphUpdateCoordinatorShould", "--console=plain", "-PTestLoggingFull"]
]
```

### Step 1: Require the early gate and add the real-stepper test

- [ ] Read `/tmp/graph-initial-spacing-evidence/task-1/comparison/comparison.json` and require `status == "PASS"`. Do not repeat that comparison when no source changed.
- [ ] Create the following complete test. Its only injected failure is a one-shot failed raw frame from a wrapper around a real GraphStream engine. The actual worker handles failure and replacement; no substitute idle result, substitute reset, production observer or altered idle policy is used. Healthy Restart is exercised on a live, paused run: a completion-stage barrier holds the in-flight real step result, then a second barrier observes raw engine coordinates before the resumed run can integrate its next step. Continuity compares raw engine positions, because corrected worker positions are a different contract.

```java
package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.freeplane.plugin.graph.layout.graphstream.SpacingTestSupport.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.freeplane.plugin.graph.adapter.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.layout.graphstream.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.workspace.*;
import org.junit.Test;

public class InitialSpacingLifecycleShould {
    private static final class TestEdt implements EdtExecutor {
        public <T> T call(Callable<T> action) { try { return action.call(); } catch(Exception e) { throw new AssertionError(e); } }
        public void execute(Runnable action) { action.run(); }
        public boolean isEdt() { return true; }
    }
    private static final class RealEngine implements LayoutEngine {
        final LayoutEngine delegate=GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults());
        final AtomicBoolean fail;
        volatile Snapshot last;
        volatile boolean closed;
        RealEngine(AtomicBoolean fail) { this.fail=fail; }
        public LayoutFrame apply(LayoutRequest r) {
            LayoutFrame f=delegate.apply(r); last=inspect(delegate); return f;
        }
        public LayoutFrame step() {
            if(fail.compareAndSet(true,false)) return LayoutFrame.of(0,last.positions,true);
            LayoutFrame f=delegate.step(); last=inspect(delegate); return f;
        }
        public void reset() { delegate.reset(); last=inspect(delegate); }
        public void close() { delegate.close(); closed=true; }
    }
    private static final class Submission {
        final LayoutRequest request;
        final LayoutWorker worker;
        final RealEngine engine;
        final Snapshot raw;
        final LayoutFrame corrected;
        Submission(LayoutRequest r,LayoutWorker w,RealEngine e,LayoutFrame f) {
            request=r;worker=w;engine=e;raw=e.last;corrected=f;
        }
    }
    private static final class Gate {
        final CompletableFuture<Submission> entered=new CompletableFuture<Submission>();
        final CompletableFuture<Void> release=new CompletableFuture<Void>();
    }
    private static final class ObservingStepper implements LayoutSettleLoop.FrameStepper {
        final LayoutSettleLoop.FrameStepper delegate;
        final Set<LayoutWorker> instrumented=Collections.newSetFromMap(new IdentityHashMap<LayoutWorker,Boolean>());
        final AtomicBoolean failNext=new AtomicBoolean();
        final AtomicReference<Gate> nextSubmit=new AtomicReference<Gate>();
        final AtomicReference<Gate> nextStepResult=new AtomicReference<Gate>();
        final AtomicReference<Gate> nextStepBeforeIntegration=new AtomicReference<Gate>();
        volatile LayoutRequest lastRequest;
        volatile RealEngine current;
        ObservingStepper() throws Exception {
            Class<?> type=Class.forName("org.freeplane.plugin.graph.control.LayoutSettleLoop$WorkerStepper");
            Constructor<?> constructor=type.getDeclaredConstructor(LayoutCalibration.class);
            constructor.setAccessible(true);
            delegate=(LayoutSettleLoop.FrameStepper)constructor.newInstance(LayoutCalibration.spikeDefaults());
        }
        LayoutWorker worker() { return (LayoutWorker)read(delegate,"worker"); }
        Gate holdNextSubmit() {
            Gate gate=new Gate(); assertThat(nextSubmit.compareAndSet(null,gate)).isTrue(); return gate;
        }
        Gate holdNextStepResult() {
            Gate gate=new Gate(); assertThat(nextStepResult.compareAndSet(null,gate)).isTrue(); return gate;
        }
        Gate holdNextStepBeforeIntegration() {
            Gate gate=new Gate(); assertThat(nextStepBeforeIntegration.compareAndSet(null,gate)).isTrue(); return gate;
        }
        private Submission observed(LayoutWorker w, LayoutFrame corrected) {
            return new Submission(lastRequest,w,current,corrected);
        }
        public CompletionStage<LayoutFrame> submit(LayoutRequest r) {
            lastRequest=r;
            final LayoutWorker w=worker();
            if(instrumented.add(w)) write(w,"engineFactory",(Supplier<LayoutEngine>)()->{
                RealEngine created=new RealEngine(failNext);current=created;return created;
            });
            Gate gate=nextSubmit.getAndSet(null);
            return delegate.submit(r).thenCompose(f->{
                requireTargets(current.last,20,false,22);
                if(gate==null) return CompletableFuture.completedFuture(f);
                gate.entered.complete(new Submission(r,w,current,f));
                return gate.release.thenApply(ignored->f);
            });
        }
        public CompletionStage<LayoutFrame> step() {
            Gate before=nextStepBeforeIntegration.getAndSet(null);
            if(before!=null) {
                before.entered.complete(observed(worker(),delegate.lastValidFrame()));
                return before.release.thenCompose(ignored->delegate.step());
            }
            Gate after=nextStepResult.getAndSet(null);
            CompletionStage<LayoutFrame> stepping=delegate.step();
            if(after==null) return stepping;
            return stepping.thenCompose(frame->{
                after.entered.complete(observed(worker(),frame));
                return after.release.thenApply(ignored->frame);
            });
        }
        public void pause() { delegate.pause(); }
        public void restart() { delegate.restart(); }
        public void reset() { delegate.reset(); }
        public LayoutFrame lastValidFrame() { return delegate.lastValidFrame(); }
        public void close() {
            for(AtomicReference<Gate> gates:Arrays.asList(nextSubmit,nextStepResult,nextStepBeforeIntegration)) {
                Gate g=gates.getAndSet(null);if(g!=null)g.release.complete(null);
            }
            delegate.close();
        }
    }
    private static final class Fixture implements AutoCloseable {
        final TestEdt edt=new TestEdt();
        final ObservingStepper stepper=new ObservingStepper();
        final BlockingQueue<CanvasState> states=new LinkedBlockingQueue<CanvasState>();
        final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
        final GraphUpdateCoordinator coordinator;
        Fixture() throws Exception {
            GraphProjection basic=ring(1,2,false);
            List<PinProjection> pins=Collections.singletonList(pin(node(A,"u0"),0,0));
            GraphProjection p=projection(1,basic.nodes(),basic.enclosures(),Collections.singletonList(
                edge(ProjectedEndpointKey.ofNode(node(A,"u0")),ProjectedEndpointKey.ofNode(node(A,"u1")),false,16041)),pins);
            GraphUpdateCoordinator.RebuildPipeline pipeline=(batch,previous)->p;
            ProjectionBatcher batcher=new ProjectionBatcher(edt,scheduler,()->0L,batch->{});
            GraphWorkspaceStore store=mock(GraphWorkspaceStore.class);
            MapLeaseManager leases=mock(MapLeaseManager.class);
            when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(()->{});
            when(leases.addListener(any(MapAdapterListener.class))).thenReturn(()->{});
            coordinator=new GraphUpdateCoordinator(pipeline,batcher,
                new LayoutSettleLoop(WORKSPACE,stepper,new GraphGeometryEngine(),edt),edt,store,leases);
            coordinator.addCanvasStateListener(state->{
                if(state.layout().positions().nodes().containsKey(node(A,"u0"))) {
                    assertThat(state.layout().positions().nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
                }
                states.add(state);
            });
        }
        void start() { coordinator.acceptBatch(new AcceptedBatch(1,1,EnumSet.of(ChangeKind.STRUCTURE))); }
        CanvasState until(OperationalStatus status) throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            for(;;) {
                long left=end-System.nanoTime(); assertThat(left).as("command publication deadline").isPositive();
                CanvasState state=states.poll(left,TimeUnit.NANOSECONDS);
                assertThat(state).as("command publication").isNotNull();
                if(state.status()==OperationalStatus.FAILED&&status!=OperationalStatus.FAILED)
                    throw new AssertionError("failed frame cannot establish idle");
                if(state.status()==status) {
                    if(status==OperationalStatus.IDLE) {
                        assertThat(state.layout().failed()).isFalse();assertThat(state.layout().verified()).isTrue();
                        assertThat(state.layout().idle().idle()).isTrue();
                    }
                    return state;
                }
            }
        }
        public void close() { coordinator.close();scheduler.shutdownNow(); }
    }
    private static Submission entered(Gate gate) throws Exception { return gate.entered.get(5,TimeUnit.SECONDS); }
    private static void verifyPin(Submission s) {
        assertThat(s.raw.positions.nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
        assertThat(s.corrected.positions().nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
        requireTargets(s.raw,20,false,22);
        System.out.println("spacing-lifecycle worker="+System.identityHashCode(s.worker)+" engine="+
            System.identityHashCode(s.engine)+" generation="+s.request.projection().generation()+" targets=20/22 pin=0,0");
    }
    @Test public void healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker() throws Exception {
        try(Fixture f=new Fixture()) {
            Gate inFlight=f.stepper.holdNextStepResult();
            f.start();Submission beforeRestart=entered(inFlight);verifyPin(beforeRestart);
            RealEngine retained=beforeRestart.engine;LayoutWorker worker=beforeRestart.worker;
            Snapshot rawBefore=beforeRestart.raw;
            Gate restartBoundary=f.stepper.holdNextStepBeforeIntegration();
            try {
                f.coordinator.pauseLayout();f.coordinator.restartLayout();
                inFlight.release.complete(null);
                Submission resumed=entered(restartBoundary);verifyPin(resumed);
                assertThat(resumed.worker).isSameAs(worker);assertThat(resumed.engine).isSameAs(retained);
                assertThat(retained.closed).isFalse();
                assertThat(resumed.raw.positions).isEqualTo(rawBefore.positions);
                assertThat(resumed.request.projection()).isSameAs(f.coordinator.currentProjection());
            } finally { inFlight.release.complete(null);restartBoundary.release.complete(null); }
            f.until(OperationalStatus.IDLE);f.states.clear();
            Gate reset=f.stepper.holdNextSubmit();
            try {
                f.coordinator.resetLayout();Submission rebuilt=entered(reset);verifyPin(rebuilt);
                assertThat(rebuilt.worker).isNotSameAs(worker);assertThat(rebuilt.engine).isNotSameAs(retained);
                assertThat(retained.closed).isTrue();
                assertThat(rebuilt.request.projection()).isSameAs(f.coordinator.currentProjection());
                try(LayoutEngine fresh=GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
                    assertThat(rebuilt.raw.positions).isEqualTo(fresh.apply(rebuilt.request).positions());
                }
            } finally { reset.release.complete(null); }
            f.until(OperationalStatus.IDLE);
        }
    }
    @Test public void failedRestartReplacesEngineResubmitsCurrentProjectionAndRetainsPin() throws Exception {
        try(Fixture f=new Fixture()) {
            f.stepper.failNext.set(true);f.start();CanvasState failed=f.until(OperationalStatus.FAILED);
            assertThat(failed.layout().failed()).isTrue();
            RealEngine old=f.stepper.current;LayoutWorker worker=f.stepper.worker();f.states.clear();
            Gate recover=f.stepper.holdNextSubmit();
            try {
                f.coordinator.restartLayout();Submission recovered=entered(recover);verifyPin(recovered);
                assertThat(recovered.worker).isSameAs(worker);assertThat(recovered.engine).isNotSameAs(old);
                assertThat(old.closed).isTrue();
                assertThat(recovered.request.projection()).isSameAs(f.coordinator.currentProjection());
                assertThat(recovered.corrected.failed()).isFalse();assertThat(recovered.corrected.verified()).isTrue();
            } finally { recover.release.complete(null); }
            f.until(OperationalStatus.IDLE);
        }
    }
}
```

### Step 2: Run the focused verification and check the barriers

- [ ] Run the validation argv. Expected: the two new command-path tests and existing worker/settle-loop/coordinator suites pass, with the real raw containment target 20 and node-to-node relationship target 22 on initial, healthy restart, Reset and failed recovery. The exact two-pin residual fixture remains in Task 1; it is intentionally not an idle-zero-residual fixture.
- [ ] Retain the new lifecycle output and XML under `task-2/`. If an existing production behavior contradicts the approved lifecycle contract, report it; do not widen the production allowlist. Task 3 will demonstrate that engine-recreating healthy Restart and engine-preserving Reset break these exact assertions. A gate or failure injection that times out is not a successful negative control.

### Step 3: Commit the passing task

- [ ] Review the test-only diff and commit:

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/InitialSpacingLifecycleShould.java
git commit -m "graph-initial-spacing: verify real reset restart recovery and pins"
```

## Task 3: Falsify every load-bearing mechanism and reconcile only derived regression expectations

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 3

**Depends on:** Task 2

**Files:**

- Create: `freeplane_plugin_graph/src/test/scripts/mutate_initial_spacing.py`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:45-51,391-421` (the measured two-map regression only)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java:506-513` (preferred-gap wording and fixture-D residual-tolerant comparison only)
- Create evidence only: `/tmp/graph-initial-spacing-evidence/task-3/` (disposable source/classes, classpath init script, compiler/JUnit outputs, commands and mutation/golden receipts).

**Interfaces:**

- Consumes: Task 1 `InitialSpacingShould` test methods named in the mutation table below; Task 2 `InitialSpacingLifecycleShould.healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker()`; actual `sourceSets.test.runtimeClasspath.asPath` obtained through a temporary Gradle init task.
- Consumes: Task 1 `comparison.json` status PASS and immutable raw comparison receipts. No frozen input/observer or outcome predicate change is allowed here.
- Produces: CLI `python3 freeplane_plugin_graph/src/test/scripts/mutate_initial_spacing.py <candidateRoot> <evidenceDirectory>` returning 0 iff a clean JUnit control passes and all seven isolated compiled mutants fail the specified assertions; `mutations.json` holds commands, source hashes, return codes and expected assertion names.
- Produces: unchanged motion/safety/performance assertions; `TWO_MAP_FIRST_IDLE_STEP = 3455`, an observation bound of 6000 calls for this one two-map test, and its scoped 100-frame hold envelope of RMS <= 0.30 / maximum <= 1.00. Other mismatches require review, not a blanket test rewrite.

**Validation commands:** Candidate root and headless environment:

```json
[
  ["gradle", ":freeplane_plugin_graph:test", "--tests", "*InitialSpacingShould", "--tests", "*InitialSpacingLifecycleShould", "--tests", "*BoundaryFootprintShould", "--tests", "*ClusterPackingShould", "--tests", "*TypedForcesShould", "--tests", "*ClusterProximityShould", "--tests", "*AnchorSeparationShould", "--tests", "*LayoutWorkerShould", "--tests", "*LayoutSettleLoopShould", "--tests", "*GraphUpdateCoordinatorShould", "--console=plain", "-PTestLoggingFull"],
  ["python3", "freeplane_plugin_graph/src/test/scripts/mutate_initial_spacing.py", ".", "/tmp/graph-initial-spacing-evidence/task-3/mutations"]
]
```

### Step 1: Preserve invariant assertions and obtain measured golden receipts

- [ ] Run the first argv and retain JUnit XML/output in `task-3/regression-before/`. The amended candidate receipt must show the two-map fixture first reaches idle at step 3455; preserve the exact pre-amendment failure and the convergence report rather than treating the old 756 value as a valid candidate expectation. Confirm the old tree only as an unchanged-baseline receipt, not by reconstructing a formula. Derivation: the approved amendment widens the content ring only, keeps relationship rests collision-based, and therefore removes the prior widened-rest/2000-step mismatch; the observed cross-map ring-only trajectory needs 3455 steps. Change only `TWO_MAP_FIRST_IDLE_STEP`, the observation bound to 6000 for this one test, and the scoped post-idle envelope to RMS <= 0.30 / maximum <= 1.00 over 100 frames. A non-converged fixture or a failed motion/performance/safety assertion is a blocker, not permission to change another threshold.
- [ ] Replace only `MIN_GAP tie produced by contentRingRadius` in the obsolete comment in `ClusterProximityShould` with `preferred-gap tie produced by contentRingRadius`. In `initialFrameThroughWorkerKeepsSeedsWithoutPins`, keep the engine and anchor equality assertions exact for every unpinned fixture. For fixture `D` only, compare each worker node coordinate with the corresponding `NodeSeparationProjection` coordinate using an explicitly named one-ulp helper whose offset is `max(Math.ulp(actual), Math.ulp(expected))`; all other fixtures keep exact node-map equality. Do not relax the worker frame gate: assert `failed()` false, `verified()` true, `residualViolations()` zero, `boundaryDiagnostics().hullResidualViolations()` zero, `boundaryVerified()` true, `boundaryCovered()` true, and `NodeSeparationProjection.violatingPairs(projection, worker.positions())` empty for every fixture, then retain `assertPackedNonOverlap` and `assertContactGutters`. The only changed premise is fixture D's last-ulp node equality.
- [ ] In `TypedForcesShould.twoMapWorkspaceSettlesToIdle`, set `TWO_MAP_FIRST_IDLE_STEP` to `3455`; change the loop bound from `2000` to `6000` and retain an explicit `assertThat(firstIdleStep).isLessThanOrEqualTo(6000)` before `isEqualTo(TWO_MAP_FIRST_IDLE_STEP)`. Replace the old comment with: `Epoch-2 ring-only amendment receipt: content-ring seeds changed, relationship rests remain collision-based via MIN_GAP; the measured first idle is 3455. The next 100 frames are a bounded observation for this regression only, with measured RMS <= 0.30 and maximum displacement <= 1.00; this is not a production idle-policy change.` Assert those two limits with `0.30` and `1.00`; do not alter any other test or production threshold.

### Step 2: Add isolated mutation verification

- [ ] Create the following complete script. It compiles mutated copies into the external directory and places that directory first on the child JVM classpath. It never edits active production sources or build output. A successful compile and the intended JUnit assertion failure are both required; timeouts/linkage errors are not mutation kills.

```python
#!/usr/bin/env python3
from pathlib import Path
import hashlib, json, os, re, subprocess, sys
root=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]).resolve()
out.mkdir(parents=True,exist_ok=False)
java=Path('/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin')
env=dict(os.environ,JAVA_HOME=str(java.parent),JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
env['PATH']=str(java)+os.pathsep+env['PATH']
records=[]
def run(argv,name,timeout=180):
    p=subprocess.run([str(x) for x in argv],cwd=root,env=env,text=True,capture_output=True,timeout=timeout)
    (out/(name+'.stdout')).write_text(p.stdout);(out/(name+'.stderr')).write_text(p.stderr)
    records.append(dict(name=name,argv=[str(x) for x in argv],cwd=str(root),exit=p.returncode))
    return p
init=out/'classpath.init.gradle'
init.write_text('''allprojects { p ->
  afterEvaluate {
    if(p.path == ':freeplane_plugin_graph') {
      tasks.register('printSpacingTestClasspath') {
        doLast { println('SPACING_TEST_CP=' + sourceSets.test.runtimeClasspath.asPath) }
      }
    }
  }
}
''')
built=run(['gradle',':freeplane_plugin_graph:testClasses',':freeplane_plugin_graph:printSpacingTestClasspath','-I',init,'--console=plain'],'classpath')
assert built.returncode==0,'classpath build failed'
lines=[x.removeprefix('SPACING_TEST_CP=') for x in built.stdout.splitlines() if x.startswith('SPACING_TEST_CP=')]
assert len(lines)==1,'missing/duplicate classpath'
cp=lines[0]
tests=['org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould',
       'org.freeplane.plugin.graph.control.InitialSpacingLifecycleShould']
control=run([java/'java','-cp',cp,'org.junit.runner.JUnitCore',*tests],'control',120)
assert control.returncode==0 and re.search(r'OK \(\d+ tests?\)',control.stdout),'clean JUnit control failed'
base='freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/'
engine=base+'layout/graphstream/GraphStreamLayoutEngine.java'
box=base+'layout/graphstream/TypedSpringBox.java'
loop=base+'control/LayoutSettleLoop.java'
def replace_once(text,before,after):
    assert text.count(before)==1,repr(before)
    return text.replace(before,after,1)
def old_containment(text):
    text=replace_once(text,'sizes.contentRingRadius(enclosure.hullKey())));',
                      'oldContainmentRing(projection, enclosure)));')
    helper='''    private static double oldContainmentRing(GraphProjection projection, ProjectedEnclosure enclosure) {
        int n = enclosure.directNodes().size();
        if (n <= 1) return 0.0;
        double maximum = 0.0;
        for (ProjectedNodeKey node : enclosure.directNodes())
            maximum = Math.max(maximum, 8.0 * projection.prominence().get(node).scale());
        return (2.0 * maximum + 6.0) / (2.0 * Math.sin(Math.PI / n));
    }

'''
    return replace_once(text,'    private static double radiusOf(',helper+'    private static double radiusOf(')
mutants=[
 ('gap-six',engine,lambda t:replace_once(t,'PREFERRED_NODE_GAP = 24.0','PREFERRED_NODE_GAP = 6.0'),
  'pinLiteralFreshRingsAndFootprints'),
 ('old-containment',engine,old_containment,'registerAllEndpointAndMapVariantsAndConsumeTheirActualRests'),
 # Control (c): the forbidden widened relationship term must be rejected by the
 # frozen 22/34/46 registrations and the 23.6 mixed-prominence registrations.
 ('relationship-preferred',engine,lambda t:replace_once(t,
  '+ NodeSeparationProjection.MIN_GAP));','+ PREFERRED_NODE_GAP));'),
  'registerAllEndpointAndMapVariantsAndConsumeTheirActualRests'),
 ('ignore-rest',box,lambda t:replace_once(t,'(distance - link.restLength) * ATTRACTION_FACTOR','distance * ATTRACTION_FACTOR'),
  'registerAllEndpointAndMapVariantsAndConsumeTheirActualRests'),
 ('reseed-survivors',engine,lambda t:replace_once(t,'            state.radius = desired.radius;',
  '''            if (lastRequest != null) {
                Position replacement = seeds.positionFor(desired);
                state.x = replacement.x;
                state.y = replacement.y;
                springBox.setParticlePosition(desired.id, state.x, state.y);
            }
            state.radius = desired.radius;'''), 'retainAllSurvivorsAcrossRealTopologyAndProminenceChanges'),
 ('restart-recreates',loop,lambda t:replace_once(t,
  'public synchronized void restart() {\n            worker.restart();',
  'public synchronized void restart() {\n            reset();\n            worker.restart();'),
  'healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker'),
 ('reset-preserves',loop,lambda t:replace_once(t,'            worker.close();\n            worker = new LayoutWorker(calibration);',
  '            worker.restart();'),'healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker')]
source_hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in [engine,box,loop]}
try:
    for name,path,mutate,assertion in mutants:
        d=out/name; source=d/'source'/Path(path).name; classes=d/'classes'
        source.parent.mkdir(parents=True);classes.mkdir()
        source.write_text(mutate((root/path).read_text()))
        compiled=run([java/'javac','--release','8','-cp',cp,'-d',classes,source],name+'-compile')
        assert compiled.returncode==0,name+': compile failure is not a killed mutant'
        result=run([java/'java','-cp',str(classes)+os.pathsep+cp,'org.junit.runner.JUnitCore',*tests],name+'-test',120)
        text=result.stdout+result.stderr
        assert result.returncode!=0 and 'FAILURES!!!' in text and assertion in text,name+': expected assertion did not fail'
        assert 'AssertionError' in text or 'ComparisonFailure' in text,name+': failure was not an assertion'
        for bad in ['NoSuchMethodError','NoClassDefFoundError','ClassNotFoundException','VerifyError','UnsupportedClassVersionError']:
            assert bad not in text,name+': '+bad
        records[-1].update(expectedAssertion=assertion,mutantSha256=hashlib.sha256(source.read_bytes()).hexdigest(),killed=True)
finally:
    assert source_hashes=={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in source_hashes},'active source changed'
    (out/'mutations.json').write_text(json.dumps(dict(commands=records,activeSourceHashes=source_hashes),indent=2)+'\n')
print('PASS: clean control and all seven isolated mutation assertions, including control (c)')
```

### Step 3: Require actual failures and retain controls

- [ ] Run the second validation argv. Expected: the clean control passes and each mutant fails its named assertion. Record the broken mechanism, exact failing test/assertion and classpath/source hashes in `mutations.json`. No mutation may survive, be accepted for timing out, or rely only on a compiler error. Preserve the already-established appendix coverage/deadline controls by linking the immutable `spec-materialization-2/coverage-mutants/` and `deadline-mutant/` receipts; do not rerun their 31-second delay experiment.
- [ ] If a new test cannot detect its assigned mutant, repair the test only within a separately reviewed allowlist adjustment; do not mark the mechanism verified or proceed to UI work with a surviving control. Run the focused regression vector after any permitted numeric-golden change, once.

### Step 4: Commit the passing task

- [ ] Review `git diff` to confirm this task changed no production source, and commit the script and the specified test/comment files that actually changed:

```bash
git add freeplane_plugin_graph/src/test/scripts/mutate_initial_spacing.py freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java
git commit -m "graph-initial-spacing: falsify target lifecycle and retention assertions"
```

## Task 4: Capture first-idle publications in the installed application at a fixed viewport

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 4

**Depends on:** Task 3

**Files:**

- Create: `freeplane_plugin_graph/src/test/evidence/initial-spacing/SpacingUiAgent.java`
- Create: `freeplane_plugin_graph/src/test/evidence/initial-spacing/capture.py`
- Create evidence only: `/tmp/graph-initial-spacing-evidence/task-4/` (agent classes/jar, unchanged old installation, frozen maps/workspaces/profiles, Xvfb output, launcher output, publication traces, screenshot PNGs and `ui-receipt.json`).

**Interfaces:**

- Consumes: Task 1 frozen `comparison.json` status PASS, old revision `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`, Task 3 `mutations.json` with seven `killed: true` results. Do not repeat numerical comparison or delay controls.
- Consumes: existing installed launcher `BIN/freeplane.sh` produced by `gradle :freeplane_plugin_graph:graphSmokeInstallation`; actual `OpenGraphWorkspaceAction.actionPerformed(ActionEvent): void`; `GraphWorkspaceViewFactory.create(GraphWorkspaceHandle,GraphWorkspaceViewBinding,WorkspaceCloseController): GraphWorkspaceView`; `GraphUpdateCoordinator.addCanvasStateListener(CanvasStateListener): ListenerRegistration`; `CanvasStateListener.onCanvasState(CanvasState): void`; `GraphWorkspaceViewBinding.currentCanvasState(): CanvasState`; `GraphCanvas.runWhenViewportSettled(Runnable): void`, `visibleViewport(): GraphViewport`; `GraphCommands.restartLayout()/resetLayout(): RestartLayout/ResetLayout` routed by `GraphWorkspaceHandle.execute(GraphCommand): GraphCommandResult`.
- Produces: test-only `SpacingUiAgent.premain(String,Instrumentation): void`, argument `outputDirectory,workspacePath,caseName` (`nr8`, `sm8`, `nested`, `pins`, or `pin-conflict`); it locates installed classes through their real OSGi classloader, wraps only view construction to attach a passive listener, and delegates every production operation. No class redefinition, worker replacement, synthesized `CanvasState`, setter for positions, forced idle, extra step, Fit Graph or settle-policy modification is allowed.
- Produces: `python3 capture.py <candidateRoot> <evidenceDirectory>` exit 0 only when old/new direct-node, connected, nested multi-map and required one-pin lifecycle captures complete, source/projection/viewport/radius identities match, exact pin coordinates survive Restart/Reset, and normal first-IDLE screenshots identify the first qualified publication. A supplemental two-pin conflict diagnostic is excluded from spacing acceptance. `frames.tsv` row types and receipt calculations are fully specified by the code below.

**Validation commands:** The Python process can inherit the headless build environment; it must remove it from each UI child. All paths are resolved from candidate root. Xvfb allocates a new display; it never borrows the user's desktop.

```json
[
  ["python3", "freeplane_plugin_graph/src/test/evidence/initial-spacing/capture.py", ".", "/tmp/graph-initial-spacing-evidence/task-4/ui"]
]
```

### Step 1: Add a passive installed-runtime publication observer

- [ ] Create the following complete JDK-only agent. It uses reflection to retain the installed OSGi class identity, rather than loading a second copy of the plugin on a test classpath. `failed` is checked before `idle`. The observer is attached before the real view exists and before queued EDT publications can execute. It logs every subsequent publication. A screenshot queued for the first qualified idle fails if a different state reaches the real canvas before capture. The required one-pin fixture follows initial publication, healthy Restart, exact first IDLE and Reset; the supplemental pinned-infeasible fixture is captured as a corrected nonzero-residual diagnostic, never relabeled as a spacing outcome.

```java
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class SpacingUiAgent {
    static Instrumentation instrumentation;
    static Path output, workspace;
    static boolean pinned, pinConflict, installed, pending, restartPending, restartObserved, resetPending, done;
    static String caseName;
    static long sequence, end, restartAfterSequence, initialRunToken=-1L;
    static int phase, initialWorkerIdentity, resetWorkerIdentity;
    static final String[] phases={"initial-publication","first-idle"};
    static Object handle, updates, binding, canvas;
    static JFrame window;
    static PrintWriter trace;
    static ClassLoader graphLoader;
    static javax.swing.Timer timer;
    static Object call(Object target,String name,Object... args) throws Exception {
        Class<?> type=target instanceof Class ? (Class<?>)target : target.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) for(Method method:c.getDeclaredMethods()) {
            if(!method.getName().equals(name)||method.getParameterTypes().length!=args.length) continue;
            Class<?>[] types=method.getParameterTypes();boolean match=true;
            for(int i=0;i<args.length;i++) {
                Class<?> t=types[i];if(t==double.class)t=Double.class;if(t==boolean.class)t=Boolean.class;
                if(t==long.class)t=Long.class;if(t==int.class)t=Integer.class;
                if(args[i]!=null&&!t.isInstance(args[i]))match=false;
            }
            if(match) { method.setAccessible(true);return method.invoke(target instanceof Class?null:target,args); }
        }
        throw new NoSuchMethodException(type+"."+name);
    }
    static Object field(Object target,String name,Object... replacement) throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass()) try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);Object old=f.get(target);
            if(replacement.length==1)f.set(target,replacement[0]);return old;
        } catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static Class<?> loaded(String name) {
        for(Class<?> c:instrumentation.getAllLoadedClasses())if(c.getName().equals(name))return c;
        return null;
    }
    static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void fail(Throwable failure) {
        try { if(trace!=null){trace.println("ERROR\t"+failure);trace.flush();}
            PrintWriter errors=new PrintWriter(output.resolve("error.txt").toFile());failure.printStackTrace(errors);errors.close();
        } catch(Exception ignored) { }
        failure.printStackTrace();System.exit(2);
    }
    static String text(Object value) {
        return Base64.getEncoder().encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }
    static void row(Object... values) {
        for(int i=0;i<values.length;i++){if(i>0)trace.print('\t');trace.print(values[i]);}trace.println();trace.flush();
    }
    static String[] identity(Object key) throws Exception {
        Object ref=call(call(call(key,"source"),"persistedReference"),"get");
        return new String[]{String.valueOf(call(call(ref,"mapReferenceId"),"value")),String.valueOf(call(call(ref,"nodeId"),"value"))};
    }
    public static void premain(String arguments,Instrumentation instance) throws Exception {
        instrumentation=instance;String[] parts=arguments.split(",",-1);
        require(parts.length==3,"agent arguments");output=Paths.get(parts[0]);workspace=Paths.get(parts[1]);caseName=parts[2];pinned=caseName.equals("pins")||caseName.equals("pin-conflict");pinConflict=caseName.equals("pin-conflict");
        Files.createDirectories(output);trace=new PrintWriter(output.resolve("frames.tsv").toFile(),"UTF-8");
        end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(240);
        SwingUtilities.invokeLater(()->{
            timer=new javax.swing.Timer(100,event->{try {
                require(System.nanoTime()<end,"application capture deadline");
                if(!installed)install();
            }catch(Throwable failure){fail(failure);}});timer.start();
        });
    }
    static void install() throws Exception {
        Class<?> controller=loaded("org.freeplane.features.mode.Controller");if(controller==null)return;
        Object mode=call(controller,"getCurrentModeController");if(mode==null)return;
        Object action=call(mode,"getAction","OpenGraphWorkspaceAction");if(action==null)return;
        Object forwarding=field(action,"applicationController");Object application=field(forwarding,"delegate");
        if(application==null)return;
        Object original=field(application,"viewFactory");graphLoader=original.getClass().getClassLoader();
        Class<?> factory=graphLoader.loadClass("org.freeplane.plugin.graph.control.GraphWorkspaceViewFactory");
        Object observing=Proxy.newProxyInstance(graphLoader,new Class<?>[]{factory},(proxy,method,args)->{
            if(method.getName().equals("create")) {
                handle=args[0];binding=args[1];updates=field(handle,"updates");
                Class<?> listener=graphLoader.loadClass("org.freeplane.plugin.graph.control.CanvasStateListener");
                Object observer=Proxy.newProxyInstance(graphLoader,new Class<?>[]{listener},(p,m,a)->{
                    if(m.getName().equals("onCanvasState"))try{state(a[0]);}catch(Throwable failure){fail(failure);}
                    return null;
                });
                call(updates,"addCanvasStateListener",observer);
                Object view=method.invoke(original,args);require(view instanceof JFrame,"actual workspace JFrame");
                window=(JFrame)view;window.setSize(1280,800);window.setLocation(20,20);
                canvas=call(window,"canvas");
                row("INSTALL",System.nanoTime(),graphLoader.getClass().getName(),view.getClass().getName(),
                    graphLoader.loadClass("org.freeplane.plugin.graph.layout.LayoutWorker").getProtectionDomain().getCodeSource());
                return view;
            }
            return method.invoke(original,args);
        });
        field(application,"viewFactory",observing);installed=true;
        // This is the existing public controller reached by the installed Open action.
        // RecentWorkspaceList uses the exact isolated profile path, so no file chooser is involved.
        call(action,"actionPerformed",new ActionEvent(action,ActionEvent.ACTION_PERFORMED,"spacing-evidence"));
        require(handle!=null,"OpenGraphWorkspaceAction must open configured recent workspace");
    }
    static int workerIdentity() throws Exception {
        Object loop=field(updates,"settleLoop"),stepper=field(loop,"worker"),worker=field(stepper,"worker");
        return System.identityHashCode(worker);
    }
    static long runToken() throws Exception {
        Object loop=field(updates,"settleLoop"),run=field(loop,"currentRun");
        return run==null?-1L:((Number)field(run,"token")).longValue();
    }
    static void execute(String commandName) throws Exception {
        Object commands=graphLoader.loadClass("org.freeplane.plugin.graph.command.GraphCommands");
        Object result=call(handle,"execute",call(commands,commandName));
        require("APPLIED".equals(String.valueOf(call(result,"status"))),commandName+" command was not applied: "+call(result,"status"));
    }
    static void scheduleCapture(Object state,long seq,String name,String run) {
        SwingUtilities.invokeLater(()->{try {
            call(canvas,"runWhenViewportSettled",(Runnable)()->{try{capture(state,seq,name,run);}catch(Throwable failure){fail(failure);}});
        }catch(Throwable failure){fail(failure);}});
    }
    @SuppressWarnings("unchecked") static void state(Object state) throws Exception {
        if(done)return;
        long seq=++sequence;Object frame=call(state,"layout"),projection=call(state,"projection"),idle=call(frame,"idle");
        Map<Object,Object> nodes=(Map<Object,Object>)call(call(frame,"positions"),"nodes");
        Map<Object,Object> anchors=(Map<Object,Object>)call(call(frame,"positions"),"anchors");
        boolean failed=(Boolean)call(frame,"failed"),verified=(Boolean)call(frame,"verified"),isIdle=(Boolean)call(idle,"idle");
        String status=String.valueOf(call(state,"status"));int worker=workerIdentity();long token=runToken();
        int stepCalls=((Number)call(frame,"stepIndex")).intValue();
        row("STATE",seq,phase<2?phases[phase]:"reset-publication",call(state,"generation"),call(frame,"stepIndex"),status,failed,verified,isIdle,
            call(frame,"residualViolations"),nodes.size(),anchors.size(),call(idle,"rms"),call(idle,"max"),call(idle,"consecutiveStableFrames"),
            System.nanoTime(),worker,token,stepCalls,System.identityHashCode(frame));
        Object prominence=call(projection,"prominence");
        row("PROJECTION",seq,call(projection,"generation"),((List<?>)call(projection,"nodes")).size(),
            ((List<?>)call(projection,"enclosures")).size(),((List<?>)call(projection,"edges")).size(),((List<?>)call(projection,"pins")).size());
        for(Object projected:(List<?>)call(projection,"nodes")) {
            Object key=call(projected,"key"),label=call(projected,"label"),position=nodes.get(key);
            double scale=((Number)call(call(prominence,"get",key),"scale")).doubleValue();String[] id=identity(key);
            row("NODE_META",seq,id[0],id[1],text(call(label,"fullText")),text(call(label,"displayText")),
                call(projected,"graphGroup"),scale);
            if(position!=null)row("NODE",seq,id[0],id[1],call(position,"x"),call(position,"y"),8.0*scale);
        }
        for(Object pin:(List<?>)call(projection,"pins")) {
            Object source=call(pin,"source"),projected=call(call(pin,"projectedNode"),"get"),position=nodes.get(projected),record=call(pin,"record");
            require(position!=null&&((Boolean)call(pin,"active")),"active persisted pin is missing from the projected frame");
            row("PIN_SPEC",seq,call(call(source,"mapReferenceId"),"value"),call(call(source,"nodeId"),"value"),call(pin,"active"),call(record,"x"),call(record,"y"));
            row("PIN",seq,call(call(source,"mapReferenceId"),"value"),call(call(source,"nodeId"),"value"),call(position,"x"),call(position,"y"));
        }
        for(Object enclosure:(List<?>)call(projection,"enclosures")) {
            Object hull=call(enclosure,"hullKey"),endpoint=((List<?>)call(hull,"endpointKeys")).get(0);
            String[] hullId=identity(endpoint);List<?> labels=(List<?>)call(enclosure,"labels");
            row("ENCLOSURE",seq,hullId[0],hullId[1],text(call(labels.get(0),"fullText")),
                call(enclosure,"mapRoot"),call(enclosure,"boundaryTier"),text(call(enclosure,"parentHull")));
            for(Object key:(List<?>)call(enclosure,"directNodes")) {
                String[] id=identity(key);row("DIRECT",seq,hullId[0],hullId[1],id[0],id[1]);
            }
        }
        for(Object edge:(List<?>)call(projection,"edges")) for(Object contributor:(List<?>)call(edge,"contributors")) {
            Object sourceOptional=call(contributor,"sourceReference"),source=call(sourceOptional,"get"),target=call(contributor,"target");
            boolean nativeConnector=((java.util.Optional<?>)call(contributor,"connectorDescriptor")).isPresent();
            row("EDGE",seq,nativeConnector?"NATIVE":"WORKSPACE",call(call(source,"mapReferenceId"),"value"),
                call(call(source,"nodeId"),"value"),call(call(target,"mapReferenceId"),"value"),call(call(target,"nodeId"),"value"),
                call(contributor,"arrowAtSource"),call(contributor,"arrowAtTarget"));
        }
        if(failed) {
            if(token>=0L&&nodes.size()==8)throw new AssertionError("failed full-coverage publication cannot establish acceptance");
            return;
        }
        boolean complete=token>=0L&&verified&&nodes.size()==8&&window!=null&&window.isShowing()
            &&!(Boolean)call(canvas,"isViewportPositioningActive");
        if(resetPending&&complete&&worker!=initialWorkerIdentity&&token!=initialRunToken) {
            resetWorkerIdentity=worker;resetPending=false;pending=true;
            require(resetWorkerIdentity!=initialWorkerIdentity,"Reset retained the old LayoutWorker");
            row("RESET_STATE",seq,worker,token,stepCalls);
            execute("pauseLayout");scheduleCapture(state,seq,"initial-publication","reset");return;
        }
        if(restartPending&&complete&&seq>restartAfterSequence&&worker==initialWorkerIdentity&&token==initialRunToken) {
            restartPending=false;restartObserved=true;
            row("RESTART_STATE",seq,worker,token,stepCalls);
        }
        if(!pending&&phase==0&&complete) {
            pending=true;initialWorkerIdentity=worker;initialRunToken=token;
            row("SELECT",seq,phases[0],worker,token,stepCalls);
            execute("pauseLayout");scheduleCapture(state,seq,phases[0],"initial");return;
        }
        if(!pending&&phase==1&&complete&&restartObserved&&worker==initialWorkerIdentity&&token==initialRunToken
                &&(pinConflict?((Number)call(frame,"residualViolations")).intValue()>0:(isIdle&&status.equals("IDLE")))) {
            pending=true;String selected=pinConflict?"pinned-diagnostic":phases[1];
            row("SELECT",seq,selected,worker,token,stepCalls);
            scheduleCapture(state,seq,selected,"initial");
        }
    }
    static void capture(Object state,long seq,String name,String run) throws Exception {
        Object frame=call(state,"layout"),projection=call(state,"projection"),displayed=call(binding,"currentCanvasState");
        require(call(displayed,"layout")==frame&&call(displayed,"projection")==projection,
            "the selected worker publication must remain displayed through screenshot capture");
        Object canvasState=field(canvas,"canvasState");
        require(call(canvasState,"layout")==frame&&call(canvasState,"projection")==projection,"canvas differs from selected publication");
        Object viewport=call(canvas,"visibleViewport");
        require(((Number)call(viewport,"zoom")).doubleValue()==1.0,"zoom must be 1.0");
        JComponent component=(JComponent)canvas;Rectangle visible=component.getVisibleRect();
        require(visible.width>0&&visible.height>0,"visible canvas crop must be nonempty");
        component.paintImmediately(visible);window.getRootPane().paintImmediately(window.getRootPane().getVisibleRect());Toolkit.getDefaultToolkit().sync();
        Point at=component.getLocationOnScreen();Rectangle screen=new Rectangle(at.x+visible.x,at.y+visible.y,visible.width,visible.height);
        AffineTransform scale=window.getGraphicsConfiguration().getDefaultTransform();
        Robot robot=new Robot();robot.mouseMove(2,2);
        BufferedImage full=robot.createScreenCapture(window.getBounds()),crop=robot.createScreenCapture(screen);
        String prefix=run.equals("reset")?"reset-":"";String filename=prefix+name;
        ImageIO.write(full,"png",output.resolve(filename+"-window.png").toFile());
        ImageIO.write(crop,"png",output.resolve(filename+"-canvas.png").toFile());
        displayed=call(binding,"currentCanvasState");
        require(call(displayed,"layout")==frame&&call(displayed,"projection")==projection,"displayed frame changed during screenshot");
        Object idle=call(frame,"idle");
        row("CAPTURE",seq,name,run,call(state,"generation"),call(frame,"stepIndex"),workerIdentity(),runToken(),call(frame,"stepIndex"),
            call(idle,"idle"),call(idle,"rms"),call(idle,"max"),call(idle,"consecutiveStableFrames"),
            call(viewport,"centerX"),call(viewport,"centerY"),call(viewport,"zoom"),visible.width,visible.height,
            scale.getScaleX(),scale.getScaleY(),screen.x,screen.y,System.identityHashCode(frame),System.nanoTime());
        if(run.equals("reset")){finish();return;}
        phase++;pending=false;
        if(name.equals("initial-publication")) {
            require(workerIdentity()==initialWorkerIdentity&&runToken()==initialRunToken,"Restart must retain worker and run identity");
            execute("restartLayout");
            require(workerIdentity()==initialWorkerIdentity&&runToken()==initialRunToken,"healthy Restart replaced worker or run");
            restartAfterSequence=sequence;restartPending=true;
            row("RESTART_COMMAND",sequence,initialWorkerIdentity,initialRunToken,System.nanoTime());
            return;
        }
        require(restartObserved&&((!pinConflict&&name.equals("first-idle"))||(pinConflict&&name.equals("pinned-diagnostic"))),
            "post-Restart capture phase must match the fixture and witnessed healthy Restart");
        if(pinned) {
            resetPending=true;pending=true;row("RESET_COMMAND",sequence,initialWorkerIdentity,initialRunToken,System.nanoTime());
            execute("resetLayout");return;
        }
        finish();
    }
    static void finish() throws Exception {
        if(done)return;done=true;timer.stop();row("DONE",sequence);trace.close();
        Files.write(output.resolve("done.txt"),"PASS\n".getBytes(StandardCharsets.UTF_8));call(handle,"close");
        Object mode=call(loaded("org.freeplane.features.mode.Controller"),"getCurrentModeController");
        Object quit=call(mode,"getAction","QuitAction");call(quit,"actionPerformed",new ActionEvent(quit,1001,"spacing-evidence"));
    }
}
```

### Step 2: Add deterministic fixtures, isolated launch and independent trace calculations

- [ ] Create this complete Python launcher. Every direct-node, connected, nested multi-map and pinned pair uses identical source/workspace bytes, IDs and profile settings at both versions. `nr8`/`sm8` register Map B as active but deliberately unavailable: this preserves the approved one-projected-map manifests while making the live `ProjectionEngine` root boundary emphatic (it derives root tier from active registrations). The actual projection trace must match the frozen node/enclosure/edge manifest exactly. The required nested `pins` fixture has one exact persisted pin `L0=(120,-40)`; a separate two-pin `pin-conflict` fixture is diagnostic-only and excluded from the six-unit outcome. Source maps are never opened through a stand-in renderer.

```python
#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, json, math, os, select, signal, statistics, subprocess, sys, tarfile
from xml.sax.saxutils import quoteattr
root=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]).resolve();out.mkdir(parents=True,exist_ok=False)
java=Path('/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin')
base='0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a'
env=dict(os.environ,JAVA_HOME=str(java.parent),JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
env['PATH']=str(java)+os.pathsep+env['PATH']
comparison=json.loads(Path('/tmp/graph-initial-spacing-evidence/task-1/comparison/comparison.json').read_text())
assert comparison['status']=='PASS','frozen numerical gate must pass before UI work'
mutations=json.loads(Path('/tmp/graph-initial-spacing-evidence/task-3/mutations/mutations.json').read_text())
assert sum(c.get('killed',False) for c in mutations['commands'])==7,'mutation gate'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def command(argv,cwd,log,environment=env,timeout=240):
    with log.open('w') as stream:
        stream.write(json.dumps(dict(argv=[str(x) for x in argv],cwd=str(cwd)))+'\n');stream.flush()
        subprocess.run([str(x) for x in argv],cwd=cwd,env=environment,stdout=stream,stderr=subprocess.STDOUT,check=True,timeout=timeout)
old=out/'baseline';old.mkdir()
archive=out/'baseline.tar'
with archive.open('wb') as f:subprocess.run(['git','-C',str(root),'archive',base],stdout=f,check=True)
with tarfile.open(archive) as f:f.extractall(old,filter='data')
classes=out/'agent-classes';classes.mkdir();source=root/'freeplane_plugin_graph/src/test/evidence/initial-spacing/SpacingUiAgent.java'
command([java/'javac','--release','8','-d',classes,source],root,out/'agent-compile.log')
manifest=out/'agent.mf';manifest.write_text('Manifest-Version: 1.0\nPremain-Class: SpacingUiAgent\n\n')
agent=out/'spacing-ui-agent.jar';command([java/'jar','cfm',agent,manifest,'-C',classes,'.'],root,out/'agent-jar.log')
fixtures=out/'fixtures';fixtures.mkdir();inputs={};MAP_A='00000000-0000-0000-0000-000000000001';MAP_B='00000000-0000-0000-0000-000000000002'
def group_node(node_id):return '<node ID='+quoteattr(node_id)+' TEXT='+quoteattr(node_id)+'><graph_group version="1"/></node>'
def linked_group(node_id,target):return '<node ID='+quoteattr(node_id)+' TEXT='+quoteattr(node_id)+'><graph_group version="1"/><arrowlink DESTINATION='+quoteattr(target)+' STARTARROW="None" ENDARROW="Default"/></node>'
def map_xml(root_id,children):return '<map version="freeplane 1.12.1"><node ID='+quoteattr(root_id)+' TEXT='+quoteattr(root_id)+'>'+children+'</node></map>'
def write_workspace(case,workspace_id,map_a,map_b,relationship=False,pins=()):
    rel=('<relationship id="00000000-0000-0000-0000-000000016071" sequence="1" source-map="'+MAP_A+'" source-node="L0" target-map="'+MAP_B+'" target-node="B0" direction="FORWARD"/>') if relationship else ''
    pin_xml=''.join('<pin map="'+m+'" node="'+n+'" x="'+str(x)+'" y="'+str(y)+'"/>' for n,m,x,y in pins)
    maps='<map id="'+MAP_A+'" sequence="1" uri="'+map_a.as_uri()+'" active="true" color="#4E79A7"/><map id="'+MAP_B+'" sequence="2" uri="'+map_b.as_uri()+'" active="true" color="#F28E2B"/>'
    fpg=fixtures/(case+'.fpg');fpg.write_text('<graph-workspace format-version="1" id="'+workspace_id+'"><maps>'+maps+'</maps><relationships>'+rel+'</relationships><pins>'+pin_xml+'</pins><viewport center-x="0.0" center-y="0.0" zoom="1.0"/><display-settings show-arrowheads="false" canvas-theme="LIGHT" remember-viewport="true" dim-unrelated-nodes="false"/></graph-workspace>')
    return fpg
cases={}
for case,workspace_id in [('nr8','00000000-0000-0000-0000-000000001601'),('sm8','00000000-0000-0000-0000-000000001602')]:
    a=fixtures/(case+'-A.mm');b=fixtures/(case+'-B-unavailable.mm')
    if case=='nr8':children=''.join(group_node('A%d'%i) for i in range(8))
    else:children=''.join(linked_group('A%d'%i,'A%d'%((i+1)%8)) for i in range(8))
    a.write_text(map_xml('A_root',children));fpg=write_workspace(case,workspace_id,a,b,relationship=False)
    inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBUnavailablePath=str(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A])
case='nested';workspace_id='00000000-0000-0000-0000-000000001607';a=fixtures/'nested-A.mm';b=fixtures/'nested-B.mm'
left='<node ID="A_left" TEXT="A_left">'+group_node('L0')+group_node('L1')+'</node>'
right='<node ID="A_right" TEXT="A_right">'+group_node('R0')+group_node('R1')+'</node>'
a.write_text(map_xml('A_root',left+right));b.write_text(map_xml('B_root',''.join(group_node('B%d'%i) for i in range(4))))
fpg=write_workspace(case,workspace_id,a,b,relationship=True)
inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBPath=str(b),mapBSha256=sha(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A,MAP_B],relationship=['L0',MAP_A,'B0',MAP_B],pins=[])
case='pins';workspace_id='00000000-0000-0000-0000-000000001608';a=fixtures/'pins-A.mm';b=fixtures/'pins-B.mm'
a.write_text(map_xml('A_root',left+right));b.write_text(map_xml('B_root',''.join(group_node('B%d'%i) for i in range(4))))
pins=[('L0',MAP_A,120.0,-40.0)];fpg=write_workspace(case,workspace_id,a,b,relationship=True,pins=pins)
inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBPath=str(b),mapBSha256=sha(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A,MAP_B],relationship=['L0',MAP_A,'B0',MAP_B],pins=[['L0',MAP_A,120.0,-40.0]])
case='pin-conflict';workspace_id='00000000-0000-0000-0000-000000001609';a=fixtures/'pin-conflict-A.mm';b=fixtures/'pin-conflict-B-unavailable.mm'
a.write_text(map_xml('A_root',''.join(linked_group('n%d'%i,'n%d'%((i+1)%8)) for i in range(8))))
pins=[('n0',MAP_A,0.0,0.0),('n1',MAP_A,20.0,0.0)];fpg=write_workspace(case,workspace_id,a,b,pins=pins)
inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBUnavailablePath=str(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A],pins=[['n0',MAP_A,0.0,0.0],['n1',MAP_A,20.0,0.0]])
manifest_path=fixtures/'input-manifest.json';manifest_path.write_text(json.dumps(inputs,sort_keys=True,indent=2)+'\n')
expected={
 'nr8':dict(nodes=[(MAP_A,'A%d'%i) for i in range(8)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[(MAP_A,'A%d'%i) for i in range(8)]},edges=[]),
 'sm8':dict(nodes=[(MAP_A,'A%d'%i) for i in range(8)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[(MAP_A,'A%d'%i) for i in range(8)]},edges=[('NATIVE',MAP_A,'A%d'%i,MAP_A,'A%d'%((i+1)%8),'false','true') for i in range(8)]),
 'nested':dict(nodes=[(MAP_A,x) for x in ['L0','L1','R0','R1']]+[(MAP_B,'B%d'%i) for i in range(4)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC'),(MAP_A,'A_left','A_left','false','SUBTLE'),(MAP_A,'A_right','A_right','false','SUBTLE'),(MAP_B,'B_root','B_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[],(MAP_A,'A_left'):[(MAP_A,'L0'),(MAP_A,'L1')],(MAP_A,'A_right'):[(MAP_A,'R0'),(MAP_A,'R1')],(MAP_B,'B_root'):[(MAP_B,'B%d'%i) for i in range(4)]},edges=[('WORKSPACE',MAP_A,'L0',MAP_B,'B0','false','true')]),
 'pins':dict(nodes=[(MAP_A,x) for x in ['L0','L1','R0','R1']]+[(MAP_B,'B%d'%i) for i in range(4)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC'),(MAP_A,'A_left','A_left','false','SUBTLE'),(MAP_A,'A_right','A_right','false','SUBTLE'),(MAP_B,'B_root','B_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[],(MAP_A,'A_left'):[(MAP_A,'L0'),(MAP_A,'L1')],(MAP_A,'A_right'):[(MAP_A,'R0'),(MAP_A,'R1')],(MAP_B,'B_root'):[(MAP_B,'B%d'%i) for i in range(4)]},edges=[('WORKSPACE',MAP_A,'L0',MAP_B,'B0','false','true')]),
 'pin-conflict':dict(nodes=[(MAP_A,'n%d'%i) for i in range(8)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[(MAP_A,'n%d'%i) for i in range(8)]},edges=[('NATIVE',MAP_A,'n%d'%i,MAP_A,'n%d'%((i+1)%8),'false','true') for i in range(8)])}
receipt=dict(status='RUNNING',baseline=base,candidateHead=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),candidatePatchSha256=hashlib.sha256(subprocess.check_output(['git','diff',base,'--binary'],cwd=root)+source.read_bytes()+Path(__file__).read_bytes()).hexdigest(),agentSha256=sha(agent),inputManifestSha256=sha(manifest_path),inputs=inputs,runs={})
rfd,wfd=os.pipe();xlog=(out/'xvfb.log').open('w');xvfb=subprocess.Popen(['Xvfb','-displayfd',str(wfd),'-screen','0','1440x1000x24','-nolisten','tcp','-ac'],pass_fds=(wfd,),stdout=xlog,stderr=subprocess.STDOUT);os.close(wfd)
def projection_rows(rows,seq):
    selected=[]
    for r in rows:
        if r[0] in ('PROJECTION','NODE_META','ENCLOSURE','DIRECT','EDGE','PIN_SPEC') and int(r[1])==seq:
            selected.append(['PROJECTION',*r[3:]] if r[0]=='PROJECTION' else [r[0],*r[2:]])
    return selected
def verify_manifest(rows,seq,case):
    spec=expected[case];node_rows=[r for r in rows if r[0]=='NODE_META' and int(r[1])==seq]
    actual_nodes=[(r[2],r[3]) for r in node_rows]
    assert actual_nodes==spec['nodes'],(case,'node order/identity',actual_nodes)
    for r in node_rows:
        assert base64.b64decode(r[4]).decode()==r[3] and base64.b64decode(r[5]).decode()==r[3]
        assert r[6]=='true' and float(r[7])==1.0,(case,'label/group/prominence',r)
    enc_rows=[r for r in rows if r[0]=='ENCLOSURE' and int(r[1])==seq]
    actual_enc=[(r[2],r[3],base64.b64decode(r[4]).decode(),r[5],r[6]) for r in enc_rows]
    assert actual_enc==spec['enclosures'],(case,'enclosures',actual_enc)
    direct={key:[] for key in spec['direct']}
    for r in rows:
        if r[0]=='DIRECT' and int(r[1])==seq:direct.setdefault((r[2],r[3]),[]).append((r[4],r[5]))
    assert direct==spec['direct'],(case,'direct membership',direct)
    edge_rows=[r for r in rows if r[0]=='EDGE' and int(r[1])==seq]
    actual_edges=sorted(tuple(r[2:9]) for r in edge_rows);assert actual_edges==sorted(spec['edges']),(case,'edges',actual_edges)
    expected_pins=inputs[case].get('pins',[])
    actual_pins=[(r[2],r[3],r[4],float(r[5]),float(r[6])) for r in rows if r[0]=='PIN_SPEC' and int(r[1])==seq]
    assert actual_pins==[(m,n,'true',float(x),float(y)) for n,m,x,y in expected_pins],(case,'pin projection',actual_pins)
    p=next(r for r in rows if r[0]=='PROJECTION' and int(r[1])==seq)
    assert [int(x) for x in p[3:7]]==[8,len(spec['enclosures']),len(spec['edges']),len(expected_pins)]
    canonical=json.dumps(projection_rows(rows,seq),separators=(',',':'),ensure_ascii=False).encode()
    return hashlib.sha256(canonical).hexdigest()
def verify_pin_positions(rows,seq,case):
    expected_pins=inputs[case].get('pins',[])
    actual=[(r[2],r[3],float(r[4]),float(r[5])) for r in rows if r[0]=='PIN' and int(r[1])==seq]
    expected_values=[(m,n,float(x),float(y)) for n,m,x,y in expected_pins]
    assert actual==expected_values,(case,'pin coordinates changed',seq,actual,expected_values)
def nearest_gaps(rows,seq,case):
    positions={(r[2],r[3]):(float(r[4]),float(r[5]),float(r[6])) for r in rows if r[0]=='NODE' and int(r[1])==seq}
    assert len(positions)==8 and all(math.isfinite(v) for p in positions.values() for v in p)
    assert all(p[2]==8.0 for p in positions.values()),'rendered node radius changed'
    groups={key:list(value) for key,value in expected[case]['direct'].items()}
    gaps={}
    for group,keys in groups.items():
        if len(keys)<2:continue
        nearest=[]
        for key in keys:
            a=positions[key]
            vals=[math.hypot(a[0]-positions[k][0],a[1]-positions[k][1])-a[2]-positions[k][2] for k in keys if k!=key]
            assert vals and all(math.isfinite(v) for v in vals)
            nearest.append(min(vals))
        gaps[group[0]+'/'+group[1]]=dict(minimum=min(nearest),median=statistics.median(nearest),perNode=nearest)
    return positions,gaps
try:
    assert select.select([rfd],[],[],10)[0],'Xvfb display allocation deadline';display=':'+os.read(rfd,64).decode().strip();receipt['display']=display
    receipt['xvfbScreen']='1440x1000x24'
    for version,tree in [('baseline',old),('candidate',root)]:
        command(['gradle',':freeplane_plugin_graph:graphSmokeInstallation','--console=plain'],tree,out/(version+'-install.log'))
        plugin=tree/'BIN/plugins/org.freeplane.plugin.graph';receipt[version+'BundleHashes']={str(p.relative_to(tree)):sha(p) for p in sorted(plugin.rglob('*')) if p.is_file()};assert receipt[version+'BundleHashes']
        for case in ['nr8','sm8','nested','pins','pin-conflict']:
            d=out/(version+'-'+case);d.mkdir();config=d/'config/freeplane/1.12.x';config.mkdir(parents=True);work=d/(case+'.fpg');work.write_bytes((fixtures/(case+'.fpg')).read_bytes())
            assert sha(work)==inputs[case]['workspaceSha256'];(d/'home').mkdir()
            props='create_new_map_if_no_maps_are_loaded=false\nalways_load_last_maps=false\nload_last_maps=false\nload_last_map=false\nui_elements_follow_selected_map=false\nselected_map_follows_active_window=false\ngraph_workspace_recent_workspaces='+str(work)+'\n'
            (config/'auto.properties').write_text(props)
            ui=dict(env,DISPLAY=display,HOME=str(d/'home'),XDG_CONFIG_HOME=str(d/'config'));ui.pop('JAVA_TOOL_OPTIONS',None);ui.pop('JDK_JAVA_OPTIONS',None)
            ui['JAVA_OPTS']='-Dorg.freeplane.nosplash=true -Duser.language=en -Duser.country=US -Duser.timezone=UTC -Dsun.java2d.uiScale=1.0 -javaagent:'+str(agent)+'='+str(d)+','+str(work)+','+case
            log=(d/'launch.log').open('w');p=subprocess.Popen([str(tree/'BIN/freeplane.sh')],cwd=tree/'BIN',env=ui,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            try:code=p.wait(timeout=260);assert code==0,(version,case,code);assert (d/'done.txt').read_text()=='PASS\n',(version,case,'incomplete agent trace')
            finally:
                if p.poll() is None:
                    os.killpg(p.pid,signal.SIGTERM)
                    try:p.wait(timeout=5)
                    except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait(timeout=5)
                log.close()
            rows=[x.split('\t') for x in (d/'frames.tsv').read_text().splitlines()]
            state_by_seq={int(r[1]):r for r in rows if r[0]=='STATE'};caps=[r for r in rows if r[0]=='CAPTURE']
            diagnostic_name='pinned-diagnostic' if case=='pin-conflict' else 'first-idle'
            pinned=case in ('pins','pin-conflict')
            expected_caps=[('initial-publication','initial'),(diagnostic_name,'initial')]+([('initial-publication','reset')] if pinned else [])
            assert [(r[2],r[3]) for r in caps]==expected_caps,(version,case,'capture phases',[(r[2],r[3]) for r in caps])
            selected=[r for r in rows if r[0]=='SELECT'];assert [r[2] for r in selected[:2]]==['initial-publication',diagnostic_name]
            initial_seq=int(next(r for r in selected if r[2]=='initial-publication')[1]);diagnostic_seq=int(next(r for r in selected if r[2]==diagnostic_name)[1])
            initial_state=state_by_seq[initial_seq];diagnostic_state=state_by_seq[diagnostic_seq]
            verify_pin_positions(rows,initial_seq,case);verify_pin_positions(rows,diagnostic_seq,case)
            assert initial_state[6:8]==['false','true'] and int(initial_state[10])==8 and int(initial_state[18])==0
            assert diagnostic_state[6:8]==['false','true'] and int(diagnostic_state[10])==8
            if case=='pin-conflict':assert int(diagnostic_state[9])>0,'conflicting persisted pins must retain their measured nonzero residual'
            else:
                assert diagnostic_state[5]=='IDLE' and diagnostic_state[6:9]==['false','true','true']
                restart_floor=int(next(r for r in rows if r[0]=='RESTART_STATE')[1])
                same_run=[r for r in rows if r[0]=='STATE' and int(r[1])>=restart_floor and int(r[16])==int(initial_state[16]) and int(r[17])==int(initial_state[17]) and r[5]=='IDLE' and r[6:9]==['false','true','true'] and int(r[10])==8]
                assert same_run and int(same_run[0][1])==diagnostic_seq,'selected state is not exact first IDLE after witnessed healthy Restart'
            restart=next(r for r in rows if r[0]=='RESTART_STATE');assert int(restart[2])==int(initial_state[16]) and int(restart[3])==int(initial_state[17])
            initial_projection=verify_manifest(rows,initial_seq,case);diagnostic_projection=verify_manifest(rows,diagnostic_seq,case)
            assert initial_projection==diagnostic_projection
            assert int(next(r for r in caps if int(r[1])==initial_seq)[6])==int(initial_state[16])
            assert int(next(r for r in caps if int(r[1])==diagnostic_seq)[6])==int(initial_state[16])
            assert int(next(r for r in caps if int(r[1])==initial_seq)[7])==int(initial_state[17])
            assert int(next(r for r in caps if int(r[1])==diagnostic_seq)[7])==int(initial_state[17])
            restart_seq=int(restart[1]);verify_pin_positions(rows,restart_seq,case)
            if case in ('pins','pin-conflict'):
                reset_seq=int(caps[2][1]);reset_state=state_by_seq[reset_seq];reset_row=next(r for r in rows if r[0]=='RESET_STATE')
                assert int(reset_state[18])==0 and int(reset_state[16])!=int(initial_state[16]) and int(reset_state[17])!=int(initial_state[17])
                assert int(caps[2][6])==int(reset_state[16]) and int(caps[2][7])==int(reset_state[17])
                assert int(reset_row[2])==int(reset_state[16]) and int(reset_row[3])==int(reset_state[17]) and int(reset_row[4])==0
                verify_pin_positions(rows,reset_seq,case)
                reset_projection=verify_manifest(rows,reset_seq,case);assert reset_projection==initial_projection
            for state_row in (r for r in rows if r[0]=='STATE'):
                verify_pin_positions(rows,int(state_row[1]),case)
            capture_results=[]
            for cap in caps:
                seq=int(cap[1]);state=state_by_seq[seq];positions,gaps=nearest_gaps(rows,seq,case)
                assert float(cap[13])==0.0 and float(cap[14])==0.0 and float(cap[15])==1.0
                assert float(cap[18])==1.0 and float(cap[19])==1.0
                if cap[2]=='first-idle':assert cap[9]=='true' and state[5]=='IDLE'
                prefix='reset-' if cap[3]=='reset' else '';base_name=prefix+cap[2]
                capture_results.append(dict(phase=cap[2],run=cap[3],sequence=seq,generation=int(cap[4]),rawFrameIndex=int(cap[5]),workerIdentity=int(cap[6]),runToken=int(cap[7]),explicitStepCalls=int(cap[8]),idle=(cap[9]=='true'),rms=float(cap[10]),maximumMotion=float(cap[11]),stableFrames=int(cap[12]),viewport=[float(cap[i]) for i in [13,14,15]],canvasSize=[int(cap[16]),int(cap[17])],displayScale=[float(cap[18]),float(cap[19])],screenBounds=[int(cap[20]),int(cap[21])],frameIdentity=int(cap[22]),captureNanos=int(cap[23]),projectionManifestSha256=verify_manifest(rows,seq,case),gaps=gaps,screenshotSha256={kind:sha(d/(base_name+'-'+kind+'.png')) for kind in ['window','canvas']}))
            receipt['runs'][version+'-'+case]=dict(captures=capture_results,inputManifestSha256=sha(manifest_path),projectionManifestSha256=initial_projection,sourceRevision=base if version=='baseline' else receipt['candidateHead'],patchSha256=hashlib.sha256(b'' if version=='baseline' else subprocess.check_output(['git','diff',base,'--binary'],cwd=root)+source.read_bytes()+Path(__file__).read_bytes()).hexdigest())
    for case in ['nr8','sm8','nested','pins','pin-conflict']:
        before=receipt['runs']['baseline-'+case]['captures'];after=receipt['runs']['candidate-'+case]['captures'];assert [(x['phase'],x['run']) for x in before]==[(x['phase'],x['run']) for x in after]
        for a,b in zip(before,after):assert a['viewport']==b['viewport'] and a['canvasSize']==b['canvasSize'] and a['displayScale']==b['displayScale'] and a['viewport'][2]==1.0
        assert receipt['runs']['baseline-'+case]['projectionManifestSha256']==receipt['runs']['candidate-'+case]['projectionManifestSha256']
    receipt['status']='PASS'
finally:
    os.close(rfd);xvfb.terminate()
    try:xvfb.wait(timeout=5)
    except subprocess.TimeoutExpired:xvfb.kill();xvfb.wait(timeout=5)
    xlog.close();(out/'ui-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
assert receipt['status']=='PASS'
print('PASS: installed baseline/candidate applications; direct, connected, nested multi-map, required one-pin lifecycle and supplemental two-pin conflict captures match frozen live-projection manifests')
```

### Step 3: Run and inspect the actual application evidence

- [ ] Run the validation argv. Expected: `nr8`, `sm8`, `nested`, and the required `pins` fixture produce old/new initial-publication and exact first-IDLE screenshots after a witnessed healthy Restart. The nested pins fixture retains `L0=(120,-40)` exactly through initial publication, healthy Restart, first IDLE, and Reset; trace rows assert persisted targets equal corrected positions on every publication. The supplemental `pin-conflict` fixture produces initial-publication, a clearly labeled nonzero-residual diagnostic regardless of its idle flag, and post-Reset initial-publication captures; retain `n0=(0,0)` and `n1=(20,0)` exactly and exclude the diagnostic from spacing outcomes. Failed recovery is verified by Task 2 and is not represented as a UI claim here.
- [ ] Inspect every `*-window.png` and `*-canvas.png` at native resolution with the image reader. Confirm the ordinary/nested initial and first-IDLE comparisons visibly widen discs without increasing their radii, changing zoom, stretching labels, clipping the intended population, or substituting a synthetic panel. Check hulls, labels and status text; the pinned images are lifecycle diagnostics, not spacing acceptance. Retain any failure and return a real contradiction to the design; screenshots cannot override a failed numeric gate.
- [ ] Write `visual-inspection.json` in the UI evidence directory only after that review. Required top-level fields: `status: PASS`, `uiReceiptSha256`, `reviewer`, `tool`, `inspectedAt`, and `images`. Each image row has `path`, `sha256`, and `observations` with booleans `realApplicationCapture`, `phaseMatchesTrace`, `intendedNodesVisible`, `labelsAndHullsReadable`, `radiiUnchanged`, `viewportAndZoomUnchanged`, and `noClipping`; each `*-window.png` row additionally has `windowChromeVisible`, each `pins` or `pin-conflict` row also has `pinsExact`, and each `pinned-diagnostic` row additionally has `nonzeroResidualDiagnostic` and `excludedFromOutcomeGate`. The canvas crops must be verified as genuine live `GraphCanvas` captures, not synthetic renders. Bind every PNG's relative path and digest to the exact `ui-receipt.json`; the acceptance writer rejects missing, duplicate, hash-mismatched, or failed image rows.
- [ ] Record old/new nearest-neighbor minima/medians from `ui-receipt.json` as supporting diagnostics in the final acceptance record, with the pinned exclusion and selected publication identities explicit. UI fixture statistics do not replace or weaken the four frozen six-unit predicates. The trace, exact projection-manifest digest and screenshot hashes tie each image to its actual worker publication.

### Step 4: Commit the passing test support

- [ ] Review that the agent and launcher exist only under test/evidence, then commit:

```bash
git add freeplane_plugin_graph/src/test/evidence/initial-spacing/SpacingUiAgent.java freeplane_plugin_graph/src/test/evidence/initial-spacing/capture.py
git commit -m "graph-initial-spacing: capture installed first idle spacing evidence"
```

## Task 5: Run the required integration gates and publish a receipt-backed acceptance record

**Implementer tier:** Standard

**Lane:** spacing

**Sequence:** 5

**Depends on:** Task 4

**Files:**

- Create: `docs/superpowers/evidence/2026-10-16-graph-initial-spacing.md`
- Create evidence only: `/tmp/graph-initial-spacing-evidence/task-5/` (four command logs, exact environment/argv/exit receipts, production diff and copied test/performance/smoke result artifacts).

**Interfaces:**

- Consumes: frozen Task 1 `comparison.json` (`status`, `fixtures[case].baseline/candidate.firstIdle`, `.enclosures`, `.postIdle`, `.firstIdleComparisons[hull].delta/pass`); Task 3 CLI receipt `mutations.json` (`commands[].killed`, `.expectedAssertion`); Task 4 `ui-receipt.json` (`status`, `runs[version-case].captures[]` with `phase`, `run`, `sequence`, `generation`, `rawFrameIndex`, `workerIdentity`, `runToken`, `explicitStepCalls`, `idle`, `viewport`, `canvasSize`, `displayScale`, `projectionManifestSha256`, `gaps`, `screenshotSha256`) and `visual-inspection.json` binding every image digest to a PASS observation. All are read-only.
- Consumes: exact required Gradle tasks listed below. Successful prior focused tests do not imply these broader integration/performance/runtime gates pass.
- Produces: `task-5/validation.json` with actual argv/cwd/environment/exit results, `task-5/production.diff`, copied current build receipts, and the acceptance markdown linking requirement-specific evidence with actual measurements. No new runtime/test interface or production code.

**Validation commands:** The first, second and fourth vectors use the headless environment. The third runs inside its newly allocated valid X display and removes `JAVA_TOOL_OPTIONS`/`JDK_JAVA_OPTIONS`; keep Java HOME/PATH as specified.

```json
[
  ["gradle", ":freeplane_plugin_graph:check", "--console=plain"],
  ["gradle", ":freeplane_plugin_graph:graphPerformanceDiagnostic", "-PgraphStrictPerformance", "--console=plain"],
  ["xvfb-run", "-a", "-s", "-screen 0 1440x1000x24 -nolisten tcp", "gradle", ":freeplane_plugin_graph:graphOsgiSmoke", ":freeplaneLaunchSmoke", "--console=plain"],
  ["gradle", ":freeplane:compileJava", "test", "--console=plain"]
]
```

### Step 1: Run the four distinct gates with retained outputs

- [ ] Execute this complete runner from candidate root. It writes failure receipts before exiting and does not retry or waive an assertion. Command logging includes the headless/real-display distinction. For a known environmental problem, retain this attempt, correct only the environment, and use a new numbered attempt directory with the same vectors and unchanged assertions; document both attempts before any PASS claim.

```python
from pathlib import Path
import json, os, shutil, subprocess
root=Path.cwd().resolve();out=Path('/tmp/graph-initial-spacing-evidence/task-5/attempt-1');out.mkdir(parents=True,exist_ok=False)
java='/home/henry/.sdkman/candidates/java/21.0.8-zulu'
env=dict(os.environ,JAVA_HOME=java,JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
env['PATH']=java+'/bin'+os.pathsep+env['PATH']
commands=[
 ['gradle',':freeplane_plugin_graph:check','--console=plain'],
 ['gradle',':freeplane_plugin_graph:graphPerformanceDiagnostic','-PgraphStrictPerformance','--console=plain'],
 ['xvfb-run','-a','-s','-screen 0 1440x1000x24 -nolisten tcp','gradle',':freeplane_plugin_graph:graphOsgiSmoke',':freeplaneLaunchSmoke','--console=plain'],
 ['gradle',':freeplane:compileJava','test','--console=plain']]
records=[]
try:
    for index,argv in enumerate(commands,1):
        active=dict(env)
        if index==3:
            active.pop('JAVA_TOOL_OPTIONS',None);active.pop('JDK_JAVA_OPTIONS',None)
        record=dict(argv=argv,cwd=str(root),JAVA_HOME=active['JAVA_HOME'],JAVA_TOOL_OPTIONS=active.get('JAVA_TOOL_OPTIONS'),display='allocated by xvfb-run' if index==3 else None)
        records.append(record)
        with (out/('command-%d.log'%index)).open('w') as log:
            log.write(json.dumps(record)+'\n');log.flush()
            result=subprocess.run(argv,cwd=root,env=active,stdout=log,stderr=subprocess.STDOUT)
        record['exit']=result.returncode
        assert result.returncode==0,'required gate %d failed; retain log and stop'%index
finally:
    (out/'validation.json').write_text(json.dumps(records,indent=2)+'\n')
    for relative in ['freeplane_plugin_graph/build/test-results/test','freeplane_plugin_graph/build/reports/tests/test',
                     'freeplane_plugin_graph/build/graph-performance','freeplane_plugin_graph/build/graph-osgi-smoke',
                     'build/freeplane-launch-smoke.json']:
        source=root/relative
        if source.exists():
            target=out/'artifacts'/relative;target.parent.mkdir(parents=True,exist_ok=True)
            if source.is_dir():shutil.copytree(source,target)
            else:shutil.copy2(source,target)
assert len(records)==4 and all(r.get('exit')==0 for r in records)
```

### Step 2: Enforce the exact production diff and write the acceptance record

- [ ] Confirm the bundle verification remains in `check`, the strict performance gate retains every old threshold, OSGi activates the installed graph bundle, and the real launch smoke reaches production QuitAction and exits without surviving processes. Locate the actual smoke result files from the command logs if their generated output paths differ from the optional copies above; add those exact artifacts to the external receipt directory. Do not change build files to make this collection work.
- [ ] Run this complete source/receipt verifier and acceptance writer. It compares both candidate production files to their old sources with only the approved ring substitution, preferred-gap declaration and `ClusterPacking.isClear` epsilon correction; the relationship expression/import must remain byte-identical. A production refactor or unrelated change fails. Numerical outcomes are extracted from receipts, not invented in documentation.

```python
from pathlib import Path
import hashlib, json, subprocess
root=Path.cwd();e=Path('/tmp/graph-initial-spacing-evidence');base='0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a'
p='freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java'
packing='freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java'
old=subprocess.check_output(['git','show',base+':'+p],text=True)
old_packing=subprocess.check_output(['git','show',base+':'+packing],text=True)
assert old.count('import org.freeplane.plugin.graph.layout.NodeSeparationProjection;\n')==1
assert old_packing.count('if (clearanceX < gap && clearanceY < gap) {')==1
expected=old.replace('    static final double NODE_RADIUS = 8.0;\n','    static final double NODE_RADIUS = 8.0;\n    static final double PREFERRED_NODE_GAP = 24.0;\n')
old_ring='            return (2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)\n                / (2.0 * Math.sin(Math.PI / count));'
new_ring='            return (2.0 * maxRadius + PREFERRED_NODE_GAP)\n                / (2.0 * Math.sin(Math.PI / count));'
assert expected.count(old_ring)==1
expected=expected.replace(old_ring,new_ring)
assert expected.count('NodeSeparationProjection.MIN_GAP')==1
assert (root/p).read_text()==expected,'GraphStreamLayoutEngine production source exceeds the approved ring-only edits'
expected_packing=old_packing.replace(
    '            if (clearanceX < gap && clearanceY < gap) {',
    '            if (clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON) {')
assert (root/packing).read_text()==expected_packing,'ClusterPacking production source exceeds the approved epsilon edit'
changed=sorted(subprocess.check_output(['git','diff','--name-only',base,'--','*/src/main/**'],text=True).splitlines())
assert changed==sorted([p,packing]),changed
(e/'task-5/production.diff').write_bytes(subprocess.check_output(['git','diff',base,'--',p,packing]))
spec=root/'docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md'
assert hashlib.sha256(spec.read_bytes()).hexdigest()=='023bde826c9c6f351e2340d643ce262d9ce1d2a1c97ded0b4b250ae5f7ab80a1'
comparison=json.loads((e/'task-1/comparison/comparison.json').read_text());assert comparison['status']=='PASS'
mutations=json.loads((e/'task-3/mutations/mutations.json').read_text());assert sum(x.get('killed',False) for x in mutations['commands'])==7
ui=json.loads((e/'task-4/ui/ui-receipt.json').read_text());assert ui['status']=='PASS'
visual=json.loads((e/'task-4/ui/visual-inspection.json').read_text());assert visual['status']=='PASS'
expected_images=[]
for run,data in ui['runs'].items():
    for sample in data['captures']:
        prefix='reset-' if sample['run']=='reset' else '';stem=prefix+sample['phase']
        for kind,digest in sample['screenshotSha256'].items():expected_images.append((run+'/'+stem+'-'+kind+'.png',digest))
actual_images=[(item['path'],item['sha256']) for item in visual['images']]
assert sorted(actual_images)==sorted(expected_images) and len(actual_images)==len(set(actual_images))
assert all(item['observations'].get(key) is True for item in visual['images'] for key in ['realApplicationCapture','phaseMatchesTrace','intendedNodesVisible','labelsAndHullsReadable','radiiUnchanged','viewportAndZoomUnchanged','noClipping'])
assert all(item['observations'].get('windowChromeVisible') is True for item in visual['images'] if item['path'].endswith('-window.png'))
assert all(item['observations'].get('pinsExact') is True for item in visual['images'] if '-pins/' in item['path'] or '-pin-conflict/' in item['path'])
assert all(item['observations'].get('nonzeroResidualDiagnostic') is True and item['observations'].get('excludedFromOutcomeGate') is True for item in visual['images'] if 'pinned-diagnostic' in item['path'])
assert visual['uiReceiptSha256']==hashlib.sha256((e/'task-4/ui/ui-receipt.json').read_bytes()).hexdigest()
validation=json.loads((e/'task-5/attempt-1/validation.json').read_text());assert len(validation)==4 and all(x.get('exit')==0 for x in validation)
convergence=Path('/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/task-1-convergence-finding.md')
assert convergence.exists() and 'twoMapWorkspaceSettlesToIdle' in convergence.read_text()
lines=['# Graph initial spacing acceptance','',
 'Task: `graph-initial-spacing`. Approved spec SHA-256: `023bde826c9c6f351e2340d643ce262d9ce1d2a1c97ded0b4b250ae5f7ab80a1`.',
 '', 'Production scope: one preferred-gap constant at 24.0 and the content-ring substitution, plus the approved `ClusterPacking.isClear` one-ulp epsilon correction. The relationship `ForceLink` term remains `NodeSeparationProjection.MIN_GAP` and its import remains present; no relationship rest was widened. Exact whole-source comparison PASS. Collision 6.0, sibling gutter 8.0, radii, force coefficients, idle policy, lifecycle, persistence and zoom retain their existing values.',
 '', '## Epoch 2 convergence finding and approved resolution','',
 'The pre-amendment Task 1 run recorded `TypedForcesShould.twoMapWorkspaceSettlesToIdle` failing to reach idle within 2000 calls; the convergence report records the 12/16/20/24 gap sweep failures, the widened-relationship-only non-convergence, and the widened-ring-only trajectory reaching idle at step 3455. This finding is retained at `/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/task-1-convergence-finding.md`.',
 'The approved Epoch 2 amendment resolves that finding by widening only `BoundarySizes.contentRingRadius`, applying the `ClusterPacking.isClear` last-ulp tolerance, and keeping relationship rests collision-based: 22/34/46 for the frozen target fixture and 23.6 for the mixed-prominence synchronization fixture. The two-map regression records first idle 3455 within its scoped 6000-call observation bound and uses only its scoped 100-frame RMS <= 0.30 / maximum <= 1.00 envelope. The four-fixture outcome predicate, 2000-call bound, one 30-second deadline per fixture/version, and production idle policy remain unchanged.',
 '', '## Corrected first-idle outcome','',
 'Each version is measured at its own first corrected idle. Submission is step 0; first idle is bounded by 2000 subsequent calls and one shared 30-second monotonic deadline. Full-precision predicates use `delta >= 6.0 - 1e-6`. These are independent of the raw geometry/registration/consumption tests.',
 '', '| Fixture / enclosure | Old median | New median | Delta | Old/new first-idle calls | PASS |',
 '| --- | ---: | ---: | ---: | --- | --- |']
count=0
for case,data in comparison['fixtures'].items():
    for hull,predicate in data['firstIdleComparisons'].items():
        assert predicate['pass'];count+=1
        a=data['baseline'];b=data['candidate'];assert len(a['postIdle'])==len(b['postIdle'])==100
        lines.append('| %s / %s | %s | %s | %s | %s / %s | yes |'%(case,hull,a['enclosures'][hull]['median'],b['enclosures'][hull]['median'],predicate['delta'],a['firstIdle']['steps'],b['firstIdle']['steps']))
assert count==4
lines += ['', 'The next 100 sequential corrected frames for each version/case are complete under their separate 30-second diagnostic deadline. Their frame-by-frame minima, medians, safety flags and actual absolute step counts remain in the linked raw receipt. This window was not searched for a favorable endpoint.',
 '', '## Verification and falsifiability','',
 '- Raw zero/singleton/2/4/8 and mixed-prominence seeds, independent footprints, registered containment/relationship targets across node/enclosure and same/cross-map variants, and live force consumption: JUnit receipts in Tasks 1 and 3.',
 '- Actual topology changes retain all surviving raw coordinates; raw reset, healthy coordinator Restart continuity, real WorkerStepper Reset replacement and failed-engine recovery resubmit current projection and wider targets: Tasks 1 and 2.',
 '- Active pins remain exact through apply/step/topology/reset/restart/recovery; the infeasible gap retains nonzero corrected residuals: Tasks 1, 2 and the separate pinned application trace.',
 '- Seven single-mechanism compiled negative controls fail their intended assertions; the clean control passes. Frozen appendix coverage/deadline controls are historical tooling receipts, not feature acceptance.',
 '', '## Installed application evidence','',
 'Full application windows and canvas screen crops were captured from the installed old/new bundles. The `nr8`, `sm8`, `nested` and required one-pin `pins` first-IDLE endpoints are the exact first qualified IDLE publications after a witnessed healthy Restart; trace sequence/generation/raw-frame index/worker/run token and canvas-state identity match each screenshot. The nested `pins` fixture retains `L0=(120,-40)` exactly through initial publication, healthy Restart, first IDLE and Reset. The supplemental `pin-conflict` diagnostic retains both exact coordinates and records nonzero residual without claiming a spacing outcome. Failed recovery is verified deterministically in Task 2 and is not claimed as a UI capture.',
 '', 'All before/after pairs use identical source/workspace bytes, source IDs, projection manifests, fixed viewport and zoom 1.0; rendered radii stay 8.0. Every window/canvas image is bound by SHA-256 to the required native-resolution visual-inspection receipt. No Fit Graph or synthetic panel was used.',
 '', '| Version / fixture / phase / run | Endpoint | Trace sequence / generation / frame / step calls | Minimum / median gaps | Window / canvas SHA-256 |',
 '| --- | --- | --- | --- | --- |']
for run,data in ui['runs'].items():
    for sample in data['captures']:
        endpoint='exact first IDLE' if sample['phase']=='first-idle' else ('two-pin corrected residual diagnostic (excluded)' if sample['phase']=='pinned-diagnostic' else 'verified initial publication')
        gaps='; '.join('%s / %s'%(g['minimum'],g['median']) for g in sample['gaps'].values())
        images='%s / %s'%(sample['screenshotSha256']['window'],sample['screenshotSha256']['canvas'])
        lines.append('| %s / %s / %s | %s | %s / %s / %s / %s | %s | `%s` |'%(run,sample['phase'],sample['run'],endpoint,sample['sequence'],sample['generation'],sample['rawFrameIndex'],sample['explicitStepCalls'],gaps,images))
lines += ['', 'Visual inspection: `%s` (%d images), reviewer `%s`, tool `%s`, inspected `%s`.'%(visual['uiReceiptSha256'],len(actual_images),visual['reviewer'],visual['tool'],visual['inspectedAt'])]
lines += ['', '## Required integration gates','', '| Command argv | Exit |','| --- | ---: |']
for record in validation:lines.append('| `%s` | %s |'%(' '.join(record['argv']),record['exit']))
lines += ['', '## Evidence and provenance','',
 '- [Frozen comparison and complete raw traces](/tmp/graph-initial-spacing-evidence/task-1/comparison/comparison.json)',
 '- [Lifecycle command receipts](/tmp/graph-initial-spacing-evidence/task-2/)',
 '- [Mutation commands and source hashes](/tmp/graph-initial-spacing-evidence/task-3/mutations/mutations.json)',
 '- [Application screenshots, viewport/source hashes and gap measurements](/tmp/graph-initial-spacing-evidence/task-4/ui/ui-receipt.json)',
 '- [Exact integration argv, environment and exit receipts](/tmp/graph-initial-spacing-evidence/task-5/attempt-1/validation.json)',
 '- [Exact production diff](/tmp/graph-initial-spacing-evidence/task-5/production.diff)',
 '', 'The spec-materialization-2 and spec-review-2 receipts predate this implementation and establish tooling/spec readiness only. The original baseline 25/26 headless run failed during unauthorized X11/font initialization; its retained rerun passed 26/26 with forced headless and the 200-enclosure submission measured 0.565 s. That historical assertion remains <=3.0 s with a separate 5 s await; neither receipt substitutes for current feature gates.'
 '', 'No design blocker remains only when every linked current gate is PASS. Failed attempts remain beside successful reruns with an environmental explanation; no performance assertion or acceptance threshold was weakened.', '']
target=root/'docs/superpowers/evidence/2026-10-16-graph-initial-spacing.md';target.parent.mkdir(parents=True,exist_ok=True)
target.write_text('\n'.join(lines))
print('PASS: exact production scope and complete receipt-backed acceptance')
```

### Step 3: Inspect the record and commit the completed task

- [ ] Check the record against actual logs, especially all four improvement rows, six full post-idle windows, seven intended mutant assertion failures, installed screenshot identities, current strict performance results and the 200-enclosure assertion/await distinction. Add the exact retained environmental-failure/rerun links if applicable; use actual passing attempt paths consistently in the writer and report. Do not claim native-resolution inspection until it has been performed in Task 4.
- [ ] Run `git diff --check`, inspect `git diff`/`git diff --cached`, and confirm every production edit is still the approved parameter-only change. No whole-repository rerun is needed after this documentation-only write. Commit the acceptance record:

```bash
git diff --check
git add docs/superpowers/evidence/2026-10-16-graph-initial-spacing.md
git commit -m "graph-initial-spacing: record verified spacing acceptance and runtime gates"
```
