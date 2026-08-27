# Graph Workspace Layout Calibration Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recalibrate the Graph Workspace force layout so real maps settle into a readable, separated layout: count-scaled seed spread, structure-aware deterministic seeds, per-link rest lengths, and convergent force constants.

**Architecture:** `GraphStreamLayoutEngine` gains a `Seeds` class that places map-root anchors at identity-derived positions in a count-scaled spread, non-root enclosure anchors on deterministic rings around their parent (100 units at depth 1, 60 deeper), and nodes on 24-unit rings around their innermost containing enclosure. `ForceLink` carries a per-link `restLength` (24 for relationship/containment, 100/60 for hierarchy by child depth) and `TypedSpringBox` raises `ATTRACTION_FACTOR` to 0.05 and the native repulsion `K2` to 16 so the settle loop actually converges. `TypedForcesShould` gains two seed regressions, one hierarchy-rest regression, and updates two movement thresholds.

**Tech Stack:** Java 8 source target, JUnit 4, AssertJ, Gradle, GraphStream gs-core 1.3.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-layout-calibration-remediation` on branch `graph-layout-calibration-remediation`, based on the local `main` HEAD that contains the committed design document and plan.
- The deliverable code diff may modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`.
- Do not modify `TypedNodeParticle`, `LayoutCalibration`, the cross-map aggregate cap (0.005), the `chooseNodePosition` no-op, public layout interfaces, projection code, canvas code, or persistence code.
- Preserve the SHA-256 identity encoding, deterministic seed derivation, particle ordering, pin handling, solver quality 0.10, and reset behavior.
- Use public `LayoutEngine`, `LayoutRequest`, `LayoutFrame`, and `PinProjection` in tests; do not access GraphStream particle internals and do not add a test-only production seam.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle invocation; use `gradle`, never Maven or the Gradle wrapper.
- Follow TDD: the three new regressions must fail against the pre-calibration implementation (the current `main` layout code) before the production changes are added.
- Commit subject prefix `2026-08-27-graph-workspace-layout-calibration-remediation:` with an imperative subject.
- Before the commit: assert the index is empty, stage exactly the three allowlisted paths, run `git diff --cached --check`, compare `git diff --cached --name-only` to the exact allowlist, and abort on any extra or missing path.

## Task 1: Recalibrate the Graph Workspace layout

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:38-41,127-150,211-292,319-328,452-536`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:16-32,94-104`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:54-110,160-200,245-300`

**Interfaces:**

- Consumes: the existing `LayoutEngine.apply(LayoutRequest): LayoutFrame` / `step(): LayoutFrame`, the existing `TypedForcesShould` helpers `baseline(long)`, `expanded(long)`, `hierarchyProjection(long, boolean)`, `projection(long, List, List, List)`, `request(WorkspaceId, GraphProjection, GraphProjection, List<PinProjection>)`, `pin(ProjectedNodeKey, double, double)`, `key(MapReferenceId, String)`, `hull(MapReferenceId, String)`, `distance(LayoutPoint, LayoutPoint)`, `greatestDistanceBetweenDistinctPositions(LayoutFrame)`, and constants `WORKSPACE_ONE`, `MAP_ONE`, plus the new `within` AssertJ import.
- Produces: the recalibrated layout described in the design document, and 17 `TypedForcesShould` tests (13 existing, 2 threshold updates, 2 new seed regressions, 1 new hierarchy regression replacing one existing test).

- [ ] **Step 1: Add the failing regressions and update the test suite**

In `TypedForcesShould.java`, make exactly these changes:

1. Add `import static org.assertj.core.api.Assertions.within;` to the static imports.

2. In the two movement regressions `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` and `aTopologyChangeDoesNotTeleportRetainedParticles`, change the bound from `1.0` to `8.0` (the stronger springs make a legitimate one-step movement reach ~1.15 units; the teleport defect measures 36.7-42.1, so 8.0 still discriminates).

3. Add `largerWorkspacesSeedWiderThanTheMinimumSpread` directly after `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin`:

```java
    @Test
    public void largerWorkspacesSeedWiderThanTheMinimumSpread() {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (int index = 0; index < 200; index++) {
            nodes.add(node(MAP_ONE, "wide-" + index));
        }
        GraphProjection projection = projection(1, nodes, Collections.<ProjectedEnclosure>emptyList(),
            Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            assertThat(greatestDistanceBetweenDistinctPositions(frame)).isGreaterThan(150.0);
        }
    }
```

4. Add `hierarchyAnchorsSeedOnTheGroupRing` next to it:

```java
    @Test
    public void hierarchyAnchorsSeedOnTheGroupRing() {
        GraphProjection projection = baseline(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            assertThat(distance(frame.positions().anchors().get(rootHull()),
                frame.positions().anchors().get(childHull()))).isCloseTo(100.0, within(10.0));
        }
    }
```

5. Delete `reduceAnchorDistanceChangeWhenEnclosuresHaveAHierarchyLink` and its helper `anchorDistanceChangeAfterPositioningAndOneStep`, and add this replacement test and helper:

```java
    @Test
    public void hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength() {
        GraphProjection nested = hierarchyProjection(1, true);
        GraphProjection peers = hierarchyProjection(1, false);
        List<PinProjection> pins = Arrays.asList(pin(key(MAP_ONE, "hierarchy-parent-node"), 0.0, 0.0),
            pin(key(MAP_ONE, "hierarchy-child-node"), 400.0, 0.0));
        double nestedDistance = anchorDistanceAfterSteps(WORKSPACE_ONE, nested, pins, 300);
        double peerDistance = anchorDistanceAfterSteps(WORKSPACE_ONE, peers, pins, 300);

        assertThat(nestedDistance).isGreaterThan(100.0);
        assertThat(nestedDistance).isLessThan(peerDistance);
    }

    private static double anchorDistanceAfterSteps(WorkspaceId workspace, GraphProjection projection,
            List<PinProjection> pins, int steps) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(workspace, projection, projection, pins));
            for (int step = 0; step < steps; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(request(workspace, projection, projection, pins));
            return distance(frame.positions().anchors().get(hierarchyParentHull()),
                frame.positions().anchors().get(hierarchyChildHull()));
        }
    }
```

- [ ] **Step 2: Run the focused suite and confirm exactly the three new regressions fail**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' -PTestLoggingFull
```

Expected: 14 pass, 3 fail, each for its intended mechanism against the pre-calibration code:
- `largerWorkspacesSeedWiderThanTheMinimumSpread`: greatest pairwise ~100, assertion `> 150.0` fails (flat 50-unit spread).
- `hierarchyAnchorsSeedOnTheGroupRing`: independent random anchor seeds keep the root-child anchor distance below ~71, so `isCloseTo(100.0, within(10.0))` fails.
- `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`: with the 24-unit hierarchy rest the nested anchors settle near ~70, so `isGreaterThan(100.0)` fails.

Do not change assertions or thresholds to make the pre-calibration implementation pass.

- [ ] **Step 3: Apply the production calibration**

In `TypedSpringBox.java`, change the constants and the constructor and the attraction magnitude exactly:

```java
    static final double CROSS_MAP_DISPLACEMENT_LIMIT = 0.005;
    static final double REST_LENGTH = 24.0;
    private static final double ATTRACTION_FACTOR = 0.05;
    private static final double REPULSION_FACTOR = 16.0;
    private static final double BASE_SEPARATION_RADIUS = 8.0;
```

and in the constructor after `setQuality(0.10);` add `K2 = REPULSION_FACTOR;` (the `K2` field is protected in `SpringBox`), and change the attraction magnitude to:

```java
            final double magnitude = (distance - link.restLength) * ATTRACTION_FACTOR * multiplier(link.kind);
```

In `GraphStreamLayoutEngine.java`:

1. Replace the constants block:

```java
    private static final double MIN_INITIAL_POSITION_SPREAD = 50.0;
    private static final double INITIAL_POSITION_SPREAD_PER_NODE = 20.0;
```

2. In `synchronize`, after `activePins(...)`, add `final Seeds seeds = new Seeds(request.workspace(), request.projection(), topology.particles);` and replace the new-particle seed with `state = new ParticleState(desired, seeds.positionFor(desired));`.

3. Add the spread helper before `initialPosition`:

```java
    private static double initialPositionSpread(final GraphProjection projection) {
        return Math.max(MIN_INITIAL_POSITION_SPREAD,
            INITIAL_POSITION_SPREAD_PER_NODE * Math.sqrt(Math.max(1, projection.projectedNodeCount())));
    }
```

4. Add the `Seeds` class before `ParticleState` (exact code):

```java
    private static final class Seeds {
        static final double GROUP_SPACING = 100.0;
        static final double SUB_GROUP_SPACING = 60.0;
        private static final double NODE_RING_RADIUS = 24.0;
        private static final double NODE_RING_JITTER = 4.0;

        private final WorkspaceId workspace;
        private final double spread;
        private final Map<ProjectedNodeKey, Position> nodes = new LinkedHashMap<ProjectedNodeKey, Position>();
        private final Map<EnclosureHullKey, Position> anchors = new LinkedHashMap<EnclosureHullKey, Position>();
        private final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        private final Map<ProjectedNodeKey, ProjectedEnclosure> containingEnclosureOfNode =
            new LinkedHashMap<ProjectedNodeKey, ProjectedEnclosure>();
        private final Map<EnclosureHullKey, Integer> depth = new LinkedHashMap<EnclosureHullKey, Integer>();
        private final Map<EnclosureHullKey, Position> centers = new LinkedHashMap<EnclosureHullKey, Position>();
        private final Map<ProjectedNodeKey, DesiredParticle> desiredNodes =
            new LinkedHashMap<ProjectedNodeKey, DesiredParticle>();
        private final Map<EnclosureHullKey, DesiredParticle> desiredAnchors =
            new LinkedHashMap<EnclosureHullKey, DesiredParticle>();

        Seeds(final WorkspaceId workspace, final GraphProjection projection,
                final LinkedHashMap<String, DesiredParticle> desired) {
            this.workspace = workspace;
            this.spread = initialPositionSpread(projection);
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                enclosuresByHull.put(enclosure.hullKey(), enclosure);
            }
            for (final DesiredParticle particle : desired.values()) {
                if (particle.nodeKey != null) {
                    desiredNodes.put(particle.nodeKey, particle);
                }
                else {
                    desiredAnchors.put(particle.anchorKey, particle);
                }
            }
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                depth.put(enclosure.hullKey(), depth(enclosure));
            }
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                for (final ProjectedNodeKey child : enclosure.directNodes()) {
                    final ProjectedEnclosure previous = containingEnclosureOfNode.get(child);
                    if (previous == null || depth(previous) > depth(enclosure)) {
                        containingEnclosureOfNode.put(child, enclosure);
                    }
                }
            }
        }

        Position positionFor(final DesiredParticle particle) {
            if (particle.nodeKey != null) {
                return nodeSeed(particle);
            }
            return anchorSeed(particle);
        }

        private Position nodeSeed(final DesiredParticle particle) {
            final Position cached = nodes.get(particle.nodeKey);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure containing = containingEnclosureOfNode.get(particle.nodeKey);
            final Position seed;
            if (containing == null) {
                seed = initialPosition(workspace, particle, spread);
            }
            else {
                final Position center = center(containing.hullKey());
                final Random random = new Random(lower64(sha256(seedBytes(workspace, particle.identity))));
                final double angle = random.nextDouble() * 2.0 * Math.PI;
                final double radius = NODE_RING_RADIUS + (random.nextDouble() - 0.5) * NODE_RING_JITTER;
                seed = new Position(center.x + radius * Math.cos(angle),
                    center.y + radius * Math.sin(angle));
            }
            nodes.put(particle.nodeKey, seed);
            return seed;
        }

        private Position anchorSeed(final DesiredParticle particle) {
            return center(particle.anchorKey);
        }

        private Position center(final EnclosureHullKey key) {
            final Position cached = centers.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final Position center;
            if (enclosure == null || enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
                final DesiredParticle anchor = desiredAnchors.get(key);
                center = anchor == null ? new Position(0.0, 0.0)
                    : initialPosition(workspace, anchor, spread);
            }
            else {
                final ProjectedEnclosure parent = enclosuresByHull.get(enclosure.parentHull().get());
                final Position parentCenter = center(parent.hullKey());
                final int index = parent.directEnclosures().indexOf(key);
                final int count = parent.directEnclosures().size();
                final double angle = 2.0 * Math.PI * Math.max(0, index) / Math.max(1, count);
                final double spacing = depth(enclosure) <= 1 ? GROUP_SPACING : SUB_GROUP_SPACING;
                center = new Position(parentCenter.x + spacing * Math.cos(angle),
                    parentCenter.y + spacing * Math.sin(angle));
            }
            centers.put(key, center);
            return center;
        }

        private int depth(final ProjectedEnclosure enclosure) {
            final Integer cached = depth.get(enclosure.hullKey());
            if (cached != null) {
                return cached.intValue();
            }
            final int value = enclosure.parentHull().isPresent()
                ? depth(enclosuresByHull.get(enclosure.parentHull().get())) + 1 : 0;
            depth.put(enclosure.hullKey(), Integer.valueOf(value));
            return value;
        }
    }
```

5. Add the `restLength` field to `ForceLink` and the constructor parameter:

```java
        final boolean crossMap;
        final double restLength;

        ForceLink(final String firstId, final String secondId, final ForceKind kind, final boolean crossMap,
                final double restLength) {
            this.firstId = firstId;
            this.secondId = secondId;
            this.kind = kind;
            this.crossMap = crossMap;
            this.restLength = restLength;
        }
```

6. In `topology()`, pass rest lengths at every `ForceLink` construction: relationship and containment links use `TypedSpringBox.REST_LENGTH` as the trailing argument; hierarchy links use `hierarchyRestLength(depths.get(<childHull>).intValue())` where `<childHull>` is the child enclosure hull key (`enclosure.hullKey()` for the parentHull case, `child` in the directEnclosures loop). Add `final Map<EnclosureHullKey, Integer> depths = enclosureDepths(projection);` at the top of the link-building section, and add these helpers next to `addHierarchyLink` (which also gains the `double restLength` parameter passed through to the `ForceLink`):

```java
    private static Map<EnclosureHullKey, Integer> enclosureDepths(final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> byHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            byHull.put(enclosure.hullKey(), enclosure);
        }
        final Map<EnclosureHullKey, Integer> depths = new LinkedHashMap<EnclosureHullKey, Integer>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            depthOf(enclosure, byHull, depths);
        }
        return depths;
    }

    private static int depthOf(final ProjectedEnclosure enclosure,
            final Map<EnclosureHullKey, ProjectedEnclosure> byHull,
            final Map<EnclosureHullKey, Integer> depths) {
        final Integer cached = depths.get(enclosure.hullKey());
        if (cached != null) {
            return cached.intValue();
        }
        final int value = enclosure.parentHull().isPresent()
            ? depthOf(byHull.get(enclosure.parentHull().get()), byHull, depths) + 1 : 0;
        depths.put(enclosure.hullKey(), Integer.valueOf(value));
        return value;
    }

    private static double hierarchyRestLength(final int childDepth) {
        return childDepth <= 1 ? Seeds.GROUP_SPACING : Seeds.SUB_GROUP_SPACING;
    }
```

Do not change `TypedNodeParticle`, `LayoutCalibration`, the cross-map cap, the `chooseNodePosition` no-op, or any other production code.

- [ ] **Step 4: Run the focused suite and confirm all 17 tests pass**

Run the Step 2 command again.

Expected: PASS, 17 tests, 0 failures.

- [ ] **Step 5: Falsifiability probes (disposable, no worktree residue)**

For each probe: record the SHA-256 of the affected production file, apply the mutation, run the Step 2 command, confirm the named test(s) fail, restore the exact bytes, verify the SHA-256 matches, and rerun green. Probes:

1. Teleport: delete the `chooseNodePosition` override from `TypedSpringBox.java` -> `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` and `aTopologyChangeDoesNotTeleportRetainedParticles` fail (movement ~36.7 / ~42.1).
2. Hierarchy rest: change `hierarchyRestLength` to return `TypedSpringBox.REST_LENGTH` -> `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength` fails.
3. Spread formula: change `initialPositionSpread` to return `MIN_INITIAL_POSITION_SPREAD` -> `largerWorkspacesSeedWiderThanTheMinimumSpread` fails.
4. Anchor seeding: in `Seeds.center`, replace the non-root ring branch with `initialPosition(workspace, desiredAnchors.get(key), spread)` -> `hierarchyAnchorsSeedOnTheGroupRing` fails.

After all probes, confirm `git status --short` shows exactly the three allowlisted paths modified and nothing else, and delete any `/tmp` copies.

- [ ] **Step 6: Run the full graph-plugin suite and verify scope**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
git diff --check
git diff --name-only
```

Expected: `BUILD SUCCESSFUL` with zero failed tests; `git diff --check` produces no output; the modified-file list contains exactly the three allowlisted paths.

- [ ] **Step 7: Commit exactly the three allowlisted paths**

```bash
test -z "$(git diff --cached --name-only)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-layout-calibration-remediation: Recalibrate the graph workspace layout"
```

Expected staged names: exactly the three allowlisted paths.
