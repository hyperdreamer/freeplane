# Graph Workspace Group-Only Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change the Graph Workspace so only group-marked nodes appear in the graph (each rendered as a boundary, root frames kept), and guarantee that sibling boundaries never overlap.

**Architecture:** `ProjectionEngine` emits only group-marked nodes as enclosures plus each map's root frame; connectors, relationships, and pins resolve against these visible boundaries. The layout drops node particles entirely and seeds boundary anchors by recursive size-aware ring packing whose ring radii are computed from conservative label-derived extents, so same-ring siblings cannot intersect by construction; a boundary repulsion term preserves separation during settling. The canvas renders label-sized coral octagon boundaries (root frames keep their theme hulls), removes node-circle painting, and sizes empty hulls from real label metrics.

**Tech Stack:** Java 8 source/bytecode, Gradle, Knopflerfish OSGi, JUnit 4, AssertJ, GraphStream gs-core 1.3, AWT (EDT-only text metrics).

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries` on branch `graph-workspace-group-only-boundaries`, based on the local `main` HEAD that contains the committed design document (`2026-08-27-graph-workspace-group-only-boundaries-design.md`, commit `97c01c9a31`).
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle invocation; use `gradle`, never Maven or the Gradle wrapper.
- Commit subjects start `2026-08-27-graph-workspace-group-only-boundaries:` with an imperative subject.
- The group-only rule: a node appears in the graph iff it carries the Graph Group marker. Group-marked nodes are projected as enclosures (boundaries); their non-group descendants are never rendered, but group-marked descendants nest inside them. Each available map's root stays as a root frame (mapRoot) with the existing tiering. A map with zero group markers shows only its empty frame.
- Preserve: SHA-256 identity encoding, deterministic ordering and seeds, particle ordering, pin handling, availability semantics, hidden/summary exclusion, safe labels, boundary tiering, solver quality `0.10`, cross-map aggregate cap `0.005`, the `chooseNodePosition` no-op, and `LayoutCalibration` unchanged.
- **Retained-dormant policy:** `ProjectedNode`, `NodeProminence`, `GraphProjection.nodes()`, `LayoutPositions.nodes()`, and prominence maps stay in the public APIs with empty lists/maps; their removal is deferred to a future cleanup. Do not delete them in this plan.
- The hierarchy spring rest lengths stay 100 (depth 1) / 60 (deeper) (`GROUP_SPACING`/`SUB_GROUP_SPACING` moved to engine constants); they are the structural distances, not the seed radii.
- Pins are the one documented override: a pinned boundary keeps its forced position and may collide with siblings.
- TDD: each task's new regressions must fail against the pre-change code before the production change is applied.
- Staging rule per task: before `git add`, assert the index is empty; stage only paths listed in the task's Files block; run `git diff --cached --check`; compare `git diff --cached --name-only` against the Files list. Staged paths must be a subset of the listed Files. If a file OUTSIDE the list must change, stop, do not stage it, and report it to the controller.
- Full module suite must be `BUILD SUCCESSFUL` before each task's commit.

## Task 1: Project group-marked nodes only

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java:50-75,195-240,316-345,452-475`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:435-505,568-580`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1105-1185`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/GroupOnlyProjectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EndpointResolutionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EnclosureTierShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EdgeProjectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/DirectionCoverageShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibilityShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionDeterminismShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionPureReloadShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/testmodel/MutableProjectionScenario.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentationShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/ProjectionBatcherShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicyShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java`

**Interfaces:**

- Consumes: `NodeSnapshot.key()/label()/structuralLeaf()/graphGroup()/excluded()/children()`, `MapSnapshot.root()/mapName()/mapReferenceId()`, `ProjectedEnclosure.of(...)` and its accessors, `PinProjection.active(PinRecord, ProjectedNodeKey)` / `dormant(PinRecord)`, `ProjectedNodeKey.of(SourceNodeKey)`, `EnclosureKey.of(SourceNodeKey)` / `source()`, `GraphIntent.Pin/Unpin`, `GraphCommands.pin(NodeReference, double, double)` / `unpin(NodeReference)`, and the existing test builders `node(...)`, `map(...)`, `project(...)`, `workspace(...)`, `registration(...)` in `StructuralProjectionShould` and `EndpointResolutionShould`.
- Produces: `ProjectionEngine.project(ProjectionInput)` whose `GraphProjection` has an empty `nodes()` list, enclosures covering exactly the map roots plus group-marked nodes (nested group chains compressed), edges whose endpoints are always enclosure endpoints, pins active iff the pin's node is a map root or group-marked, and pin/unpin gestures that work on boundary selections.

- [ ] **Step 1: Add the failing group-only regressions**

Create `GroupOnlyProjectionShould.java` in the projection test package with these tests (reuse the `node`/`map`/`project`/`workspace`/`registration` helpers copied from `StructuralProjectionShould`, and a `projectedLabelTexts(GraphProjection)` helper that concatenates all `SafeNodeLabel` display texts of nodes and enclosures):

```java
    @Test
    public void projectOnlyGroupMarkedNodesAsBoundaries() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot plainBranch = node(MAP_ONE, "branch", "Branch", false, false, false, leaf);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false,
            node(MAP_ONE, "inner", "Inner", true, false, false));
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, plainBranch, group);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(0).endpointKeys())
            .containsExactly(EnclosureKey.of(root.key()));
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.enclosures().get(0).directNodes()).isEmpty();
        assertThat(projection.enclosures().get(0).directEnclosures())
            .containsExactly(EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(group.key()))));
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(group.key()));
        assertThat(projection.enclosures().get(1).directEnclosures()).isEmpty();
        assertThat(projectedLabelTexts(projection)).containsExactly("Root", "Group");
    }

    @Test
    public void hoistGroupMarkedDescendantsThroughPlainContainers() {
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false);
        NodeSnapshot plainBranch = node(MAP_ONE, "branch", "Branch", false, false, false, group);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, plainBranch);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(0).directEnclosures())
            .containsExactly(EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(group.key()))));
        assertThat(projection.enclosures().get(1).parentHull().get())
            .isEqualTo(projection.enclosures().get(0).hullKey());
    }

    @Test
    public void nestGroupMarkedDescendantsInsideTheirGroupBoundary() {
        NodeSnapshot innerGroup = node(MAP_ONE, "inner", "Inner", false, true, false);
        NodeSnapshot outerGroup = node(MAP_ONE, "outer", "Outer", false, true, false,
            node(MAP_ONE, "plain", "Plain", true, false, false), innerGroup);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, outerGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(3);
        assertThat(projection.enclosures().get(2).endpointKeys())
            .containsExactly(EnclosureKey.of(innerGroup.key()));
        assertThat(projection.enclosures().get(2).parentHull().get()).isEqualTo(
            EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(outerGroup.key()))));
    }

    @Test
    public void collapseUnaryGroupChainsIntoOneBoundary() {
        NodeSnapshot leafGroup = node(MAP_ONE, "leaf-group", "Leaf group", false, true, false);
        NodeSnapshot middleGroup = node(MAP_ONE, "middle", "Middle", false, true, false, leafGroup);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, middleGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(middleGroup.key()), EnclosureKey.of(leafGroup.key()));
    }

    @Test
    public void mapWithoutGroupsProjectsOnlyItsFrame() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, leaf);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.enclosures().get(0).directEnclosures()).isEmpty();
    }

    @Test
    public void excludedGroupsStayHidden() {
        NodeSnapshot hiddenGroup = node(MAP_ONE, "hidden-group", "SECRET_GROUP", false, true, true);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, hiddenGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projectedLabelTexts(projection)).doesNotContain("SECRET_GROUP");
    }

    @Test
    public void togglingTheMarkerAddsAndRemovesTheBoundary() {
        NodeSnapshot inner = node(MAP_ONE, "inner", "Inner", true, false, false);
        NodeSnapshot marked = node(MAP_ONE, "topic", "Topic", false, true, false, inner);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, marked);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));

        GraphProjection markedProjection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(markedProjection.enclosures()).hasSize(2);

        NodeSnapshot unmarked = node(MAP_ONE, "topic", "Topic", false, false, false, inner);
        NodeSnapshot unmarkedRoot = node(MAP_ONE, "root", "Root", false, false, false, unmarked);
        GraphProjection unmarkedProjection = project(workspace, map(MAP_ONE, 1, unmarkedRoot));

        assertThat(unmarkedProjection.enclosures()).hasSize(1);
        assertThat(unmarkedProjection.enclosures().get(0).mapRoot()).isTrue();
    }
```

In `EndpointResolutionShould.java`, add these three tests with its existing builders:

```java
    @Test
    public void connectorsToPlainNodesDoNotResolve() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, leaf, group);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        MapSnapshot map = map(MAP_ONE, 1, root, connector(MAP_ONE, "leaf", "group"));

        GraphProjection projection = project(workspace, map);

        assertThat(projection.edges()).isEmpty();
        assertThat(projection.relationshipResolutions()).isEmpty();
    }

    @Test
    public void connectorsToGroupDescendantsFoldToTheBoundary() {
        NodeSnapshot inner = node(MAP_ONE, "inner", "Inner", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false, inner);
        NodeSnapshot otherGroup = node(MAP_ONE, "other-group", "Other group", false, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group, otherGroup);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        MapSnapshot map = map(MAP_ONE, 1, root, connector(MAP_ONE, "inner", "other-group"));

        GraphProjection projection = project(workspace, map);

        assertThat(projection.edges()).hasSize(1);
        ProjectedEdge edge = projection.edges().get(0);
        assertThat(edge.first().isEnclosure()).isTrue();
        assertThat(edge.second().isEnclosure()).isTrue();
        assertThat(edge.first().enclosure().get().source()).isEqualTo(group.key());
        assertThat(edge.second().enclosure().get().source()).isEqualTo(otherGroup.key());
    }

    @Test
    public void connectorsInsideAGroupDoNotCreateSelfLoops() {
        NodeSnapshot inner = node(MAP_ONE, "inner", "Inner", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false, inner);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        MapSnapshot map = map(MAP_ONE, 1, root, connector(MAP_ONE, "inner", "group"));

        GraphProjection projection = project(workspace, map);

        assertThat(projection.edges()).isEmpty();
    }
```

Also add to `GroupOnlyProjectionShould` (using `PinRecord` and the `PinProjection` accessors; `key(MAP_ONE, "group")` builds the persisted `SourceNodeKey` like `EndpointResolutionShould` does):

```java
    @Test
    public void pinsActivateOnlyForRootsAndGroupMarkedNodes() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, leaf, group);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true),
            pin(key(MAP_ONE, "leaf"), 1.0, 2.0), pin(key(MAP_ONE, "group"), 3.0, 4.0),
            pin(key(MAP_ONE, "root"), 5.0, 6.0));

        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(projection.pins()).hasSize(3);
        assertThat(projection.pins().get(0).active()).isFalse();
        assertThat(projection.pins().get(1).active()).isTrue();
        assertThat(projection.pins().get(2).active()).isTrue();
    }
```

If `workspace(...)`/`pin(...)` builder signatures differ, adapt the calls to the existing helper signatures in `StructuralProjectionShould`/`EndpointResolutionShould` without changing the assertions.

- [ ] **Step 2: Run the projection tests and confirm the new regressions fail**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*GroupOnlyProjectionShould' --tests '*EndpointResolutionShould' -PTestLoggingFull
```

Expected: the new tests fail against the pre-change code (plain nodes are still projected; `projection.nodes()` is not empty; group nodes are still `ProjectedNode`s, not enclosures; connectors to plain nodes still resolve).

- [ ] **Step 3: Apply the group-only projection**

In `ProjectionEngine.java`:

1. In `project(...)`, replace the per-map loop body so each map contributes only its root frame and hoisted group boundaries:

```java
        for (final MapSnapshot map : selectedMaps) {
            validateSafeIdentityTraversal(map);
            final NodeSnapshot root = map.root();
            if (root.excluded()) {
                continue;
            }
            final ExactEnclosure rootEnclosure = projectRoot(root, map.mapName());
            collectEnclosures(compress(rootEnclosure, Optional.<EnclosureHullKey>empty(),
                activeRegistrationCount, 0), projectedEnclosures);
        }
```

### 2. Chain compression rule (load-bearing fix from Task 1 preflight)

The chain-compression rule for the group-only hull tree is: **the map root never chains** (the frame is always its own hull), and a **group chains with its single projected enclosure child only when the group has exactly one source child** (one non-excluded snapshot child, which is necessarily that group). A group with zero or multiple source children ends the chain; its projected group children become child hulls. A leaf group (no projected children) is a valid chain member. Under this rule `root->outer(children=[plain, inner])->inner` produces `[root] [outer] [inner]` (three hulls: `outer` has two source children), while `root->middle->leafGroup` produces `[root] [middle+leafGroup]` (two hulls: `middle` has one source child).

2. Replace the `ExactEnclosure` inner class with:

```java
    private static final class ExactEnclosure implements StructuralElement {
        private final EnclosureKey key;
        private final SafeNodeLabel label;
        private final String mapName;
        private final int sourceChildCount;
        private final List<StructuralElement> children = new ArrayList<StructuralElement>();

        private ExactEnclosure(final EnclosureKey key, final SafeNodeLabel label, final String mapName,
                final int sourceChildCount) {
            this.key = key;
            this.label = label;
            this.mapName = mapName;
            this.sourceChildCount = sourceChildCount;
        }
    }
```

`projectRoot` and `projectGroup` pass the snapshot's non-excluded child count as `sourceChildCount`:

```java
    private static ExactEnclosure projectRoot(final NodeSnapshot root, final String mapName) {
        final ExactEnclosure enclosure = new ExactEnclosure(EnclosureKey.of(root.key()), root.label(), mapName,
            visibleChildCount(root));
        final List<StructuralElement> groups = new ArrayList<StructuralElement>();
        for (final NodeSnapshot child : root.children()) {
            projectGroups(child, mapName, groups);
        }
        enclosure.children.addAll(groups);
        return enclosure;
    }

    private static void projectGroups(final NodeSnapshot snapshot, final String mapName,
            final List<StructuralElement> groups) {
        if (snapshot.excluded()) {
            return;
        }
        if (snapshot.graphGroup()) {
            groups.add(projectGroup(snapshot, mapName));
            return;
        }
        for (final NodeSnapshot child : snapshot.children()) {
            projectGroups(child, mapName, groups);
        }
    }

    private static ExactEnclosure projectGroup(final NodeSnapshot snapshot, final String mapName) {
        final ExactEnclosure enclosure = new ExactEnclosure(EnclosureKey.of(snapshot.key()), snapshot.label(),
            mapName, visibleChildCount(snapshot));
        final List<StructuralElement> nested = new ArrayList<StructuralElement>();
        for (final NodeSnapshot child : snapshot.children()) {
            projectGroups(child, mapName, nested);
        }
        enclosure.children.addAll(nested);
        return enclosure;
    }

    private static int visibleChildCount(final NodeSnapshot snapshot) {
        int count = 0;
        for (final NodeSnapshot child : snapshot.children()) {
            if (!child.excluded()) {
                count++;
            }
        }
        return count;
    }
```

3. In `compress(...)`, replace the chain-building loop (the map root never chains; a non-root group chains only with a single source child):

```java
        final List<ExactEnclosure> chain = new ArrayList<ExactEnclosure>();
        chain.add(start);
        ExactEnclosure deepest = start;
        final boolean mapRoot = !parentHull.isPresent();
        if (!mapRoot) {
            while (deepest.children.size() == 1 && deepest.sourceChildCount == 1
                    && deepest.children.get(0) instanceof ExactEnclosure) {
                deepest = (ExactEnclosure) deepest.children.get(0);
                chain.add(deepest);
            }
        }
```

Keep the rest of `compress` unchanged (endpointKeys/labels from the chain, `directNodes` always empty, child hulls from `deepest.children`).

4. In `project(...)`, replace the `indexExactEndpoints(projectedNodes, projectedEnclosures)` call with `indexExactEndpoints(projectedEnclosures)` and pass `Collections.<ProjectedNode>emptyList()` as the nodes argument to `GraphProjection.projected(...)`.

5. Replace `indexExactEndpoints` with:

```java
    private static Map<SourceNodeKey, ProjectedEndpointKey> indexExactEndpoints(
            final List<ProjectedEnclosure> enclosures) {
        final Map<SourceNodeKey, ProjectedEndpointKey> endpoints =
            new HashMap<SourceNodeKey, ProjectedEndpointKey>();
        for (final ProjectedEnclosure enclosure : enclosures) {
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                addExactEndpoint(endpoints, endpoint.source(), ProjectedEndpointKey.ofEnclosure(endpoint));
            }
        }
        return endpoints;
    }
```

6. Replace `traverseEndpoints` with:

```java
    private static void traverseEndpoints(final NodeSnapshot node,
            final Map<SourceNodeKey, ProjectedEndpointKey> exactEndpoints, final EndpointTraversal traversal,
            final ProjectedEndpointKey outerGroup, final boolean rootNode) {
        if (node.excluded()) {
            recordExcludedSubtree(node, traversal);
            return;
        }
        if (node.graphGroup()) {
            final ProjectedEndpointKey exactEndpoint = exactEndpoints.get(node.key());
            if (exactEndpoint == null || !exactEndpoint.isEnclosure()) {
                throw new IllegalArgumentException("Active graph groups must have an exact projected enclosure");
            }
            traversal.recordEndpoint(node.key(), exactEndpoint);
            for (final NodeSnapshot child : node.children()) {
                traverseEndpoints(child, exactEndpoints, traversal, exactEndpoint, false);
            }
            return;
        }
        if (outerGroup != null) {
            traversal.recordEndpoint(node.key(), outerGroup);
            for (final NodeSnapshot child : node.children()) {
                traverseEndpoints(child, exactEndpoints, traversal, outerGroup, false);
            }
            return;
        }
        if (rootNode) {
            final ProjectedEndpointKey rootEndpoint = exactEndpoints.get(node.key());
            if (rootEndpoint == null || !rootEndpoint.isEnclosure()) {
                throw new IllegalArgumentException("Map roots must have an exact projected enclosure");
            }
            traversal.recordEndpoint(node.key(), rootEndpoint);
        }
        for (final NodeSnapshot child : node.children()) {
            traverseEndpoints(child, exactEndpoints, traversal, null, false);
        }
    }
```

and update the top-level call to `traverseEndpoints(map.root(), exactEndpoints, traversal, null, true)`.

7. Replace `projectPins` with:

```java
    private static List<PinProjection> projectPins(final WorkspaceDocument workspace,
            final List<ProjectedEnclosure> enclosures) {
        final Map<SourceNodeKey, EnclosureKey> exactBoundaries = new HashMap<SourceNodeKey, EnclosureKey>();
        for (final ProjectedEnclosure enclosure : enclosures) {
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                if (exactBoundaries.put(endpoint.source(), endpoint) != null) {
                    throw new IllegalArgumentException("Projected enclosure endpoints must be exact and unique");
                }
            }
        }
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        for (final PinRecord pin : workspace.pins()) {
            final EnclosureKey boundary = exactBoundaries.get(SourceNodeKey.persisted(pin.node()));
            pins.add(boundary == null ? PinProjection.dormant(pin)
                : PinProjection.active(pin, ProjectedNodeKey.of(SourceNodeKey.persisted(pin.node()))));
        }
        return pins;
    }
```

and update the `projectPins(value.workspace(), projectedNodes)` call to pass `projectedEnclosures`.

In `GraphInteractionController.java`, make pins work on boundary selections. Add a helper and update the three sites:

```java
    private static ProjectedNodeKey pinKey(final ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get()
            : ProjectedNodeKey.of(endpoint.enclosure().get().source());
    }
```

- `beginSelect`: change `final boolean pinCandidate = value != null && value.isNode() && !isPinned(state, value.node().get());` to `final boolean pinCandidate = value != null && !isPinned(state, pinKey(value));`
- `finishSelect`: change the guard `if (!moved || !current.endpoint.isNode())` to `if (!moved || current.endpoint == null)` and the emit to `emit(new GraphIntent.Pin(pinKey(current.endpoint), world.x(), world.y()));`
- `handleContext`: replace the `endpoint.isPresent() && endpoint.get().isNode() && isPinned(state, endpoint.get().node().get())` condition and emit with `endpoint.isPresent() && isPinned(state, pinKey(endpoint.get()))` and `emit(new GraphIntent.Unpin(pinKey(endpoint.get())));`

In `GraphWorkspaceWindow.java`, rework the selection pin paths (the `selectedNode` field stays for compatibility but is never set now). Add:

```java
    private EnclosureHullKey hullKeyOf(final ProjectedEndpointKey endpoint) {
        if (endpoint == null || endpoint.isNode() || currentState == null) {
            return null;
        }
        for (final ProjectedEnclosure enclosure : currentState.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(endpoint.enclosure().get())) {
                return enclosure.hullKey();
            }
        }
        return null;
    }
```

and rework `pinSelectedNode` / `unpinSelectedNode`:

```java
    private void pinSelectedNode() {
        if (selectedEndpoint == null || currentState == null) {
            return;
        }
        final EnclosureHullKey hull = hullKeyOf(selectedEndpoint);
        if (hull == null) {
            return;
        }
        final LayoutPoint anchor = currentState.geometry().hulls().get(hull).labelAnchor();
        executePin(ProjectedNodeKey.of(source(selectedEndpoint)), anchor.x(), anchor.y());
    }

    private void unpinSelectedNode() {
        if (selectedEndpoint != null) {
            executeUnpin(ProjectedNodeKey.of(source(selectedEndpoint)));
        }
    }
```

Do not change `PinProjection`, `MapTierCorrection`, `LayoutConflict`, `GraphProjection`, `ProjectedNode`, or the prominence machinery.

- [ ] **Step 4: Run the projection package tests and confirm green**

Run the Step 2 command plus the whole projection package:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*projection*' -PTestLoggingFull
```

Expected: PASS. Then rework the pre-existing projection tests to the group-only model where they still assert node behavior:

- `StructuralProjectionShould`: scenario nodes that must appear become group-marked (`node(..., graphGroup=true, ...)`); assertions on `projection.nodes()` become assertions on `projection.enclosures()` (endpointKeys/directEnclosures/directNodes empty); the excluded/visibility tests keep their confidentiality assertions on `projectedLabels`.
- `EndpointResolutionShould`: scenario nodes that must be endpoints become group-marked; assertions on node endpoints become enclosure endpoints; plain-node endpoints assert unresolved.
- `EnclosureTierShould`, `EdgeProjectionShould`, `DirectionCoverageShould`, `ProjectedEndpointVisibilityShould`, `ProjectionDeterminismShould`, `ProjectionPureReloadShould`, `MutableProjectionScenario`: same rule (group-mark the nodes that must appear; node assertions become enclosure assertions). Tiering itself is unchanged, but tests that assert root unary-chain merging are reworked to the new chain rule: the map root never chains (its hull is always the root alone), and a group chains only with a single source child. `ProminenceCalculatorShould`: keep as-is; it tests the calculator directly and stays valid.
- `LayoutWorkerShould`, `MapTierCorrectionShould`, `PerceptualIdlePolicyShould`: their fixture projections must use group-marked nodes only; pin fixtures must target group-marked nodes or roots; node-key pin expectations become boundary expectations (the pin binding rule: active iff root or group-marked).
- `GraphUpdateCoordinatorShould`, `GraphWorkspacePresentationShould`, `LayoutSettleLoopShould`, `ProjectionBatcherShould`, `WorkspaceMapCoordinatorShould`, `DefaultGraphWorkspaceControllerShould`, the four `integration/*` files, `GraphWorkspaceWindowModelShould`, `GraphPluginIntegrationShould`, `WorkspaceDialogsShould`, `GraphWorkspaceUiEvidence`, `GeneratedWorkspace`, `GraphWorkspacePerformanceDiagnostic`, `PerformanceTripwiresShould`: their map fixtures must mark the nodes that must appear in the graph as group nodes (via the same `GraphGroupModel` extension the acceptance fixtures already use for groups); assertions on projected node counts/labels/positions become enclosure assertions. The performance diagnostic must keep exercising the 2,000-node projection traversal gate (the adapter still walks the whole map) and may keep non-group nodes in its source map, asserting the projected boundary count instead.

- [ ] **Step 5: Run the full module suite and fix remaining fallout**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`, zero failures. Every test file that still asserts node-circle behavior gets the group-only model per Step 4's rule. If any file OUTSIDE this task's Files list needs a change, do not stage it; report it to the controller.

- [ ] **Step 6: Falsifiability probes (disposable, no worktree residue)**

For each probe: record the SHA-256 of `ProjectionEngine.java`, apply the mutation, run the Step 2 command, confirm the named tests fail, restore the exact bytes, verify the SHA-256 matches, rerun green:

1. Re-emit plain nodes: restore the pre-change `projectNode`/`collectNodes`/`ExactNode` behavior (emit every non-excluded node as `ExactNode`) -> `projectOnlyGroupMarkedNodesAsBoundaries`, `mapWithoutGroupsProjectsOnlyItsFrame`, `excludedGroupsStayHidden` fail.
2. Pin binding without boundary check: in `projectPins`, activate pins for every persisted node reference (drop the `exactBoundaries` lookup) -> `pinsActivateOnlyForRootsAndGroupMarkedNodes` fails.

- [ ] **Step 7: Verify scope and commit**

```bash
test -z "$(git diff --cached --name-only)"
git add <every changed path from this task's Files list>
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-group-only-boundaries: Project group-marked nodes only"
```

Expected staged names: a nonempty subset of this task's Files list, nothing else.

## Task 2: Separate sibling boundaries by construction

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicyShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java`

**Interfaces:**

- Consumes: `GraphProjection.enclosures()/edges()`, `ProjectedEnclosure.hullKey()/endpointKeys()/labels()/directEnclosures()/parentHull()/mapRoot()`, `SafeNodeLabel.fullText()`, `PinProjection`, `LayoutEngine.apply(LayoutRequest)/step()`, the `TypedForcesShould` builders `baseline(long)`/`projection(...)`/`request(...)`/`pin(...)`/`key(...)`/`distance(...)`, and `PerceptualIdlePolicy` constants.
- Produces: a layout where every anchor particle is seeded by recursive size-aware ring packing (no randomness), every anchor carries a boundary radius (half diagonal of its conservative extent), sibling boundaries cannot overlap at seed time (ring radii from `hypot(maxW + GAP, maxH + GAP) / (2 sin(pi/N))`), a boundary repulsion term keeps them apart during settling, and idle thresholds re-derived for anchor-only dynamics.

- [ ] **Step 1: Add the failing separation regressions**

Create `BoundarySeparationShould.java` in the layout test package with `WorkspaceId`/`MapReferenceId` constants, the `source(MapReferenceId, String)` helper (persisted `SourceNodeKey`), and the enclosure-only projection builders in the `TypedForcesShould` style, plus helpers. The size formulas use test-local constants that must equal the production `BoundarySizes` constants (reviewed by the task reviewer):

```java
    private static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
    private static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
    private static final double BOUNDARY_PADDING = 8.0;
    private static final double SIBLING_GAP = 8.0;

    private static double widthOf(final SafeNodeLabel label) {
        return label.displayText().length() * CHAR_WIDTH_UPPER_BOUND
            + 2.0 * BOUNDARY_PADDING;
    }

    private static double heightOf(final SafeNodeLabel label) {
        return CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
    }

    private static boolean boxesOverlap(final LayoutPoint first, final double firstWidth,
            final double firstHeight, final LayoutPoint second, final double secondWidth,
            final double secondHeight) {
        return Math.abs(first.x() - second.x()) < (firstWidth + secondWidth) * 0.5
            && Math.abs(first.y() - second.y()) < (firstHeight + secondHeight) * 0.5;
    }
```

Tests:

```java
    @Test
    public void siblingBoundariesSeedWithoutOverlap() {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<ProjectedNodeKey> noNodes = Collections.emptyList();
        List<EnclosureHullKey> children = new ArrayList<EnclosureHullKey>();
        List<EnclosureHullKey> siblingHulls = new ArrayList<EnclosureHullKey>();
        List<SafeNodeLabel> labels = new ArrayList<SafeNodeLabel>();
        for (int index = 0; index < 5; index++) {
            EnclosureKey key = EnclosureKey.of(source(MAP_ONE, "wide-" + index));
            EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(key));
            SafeNodeLabel label = SafeNodeLabel.of("A very wide boundary label number " + index,
                "A very wide boundary label number " + index);
            enclosures.add(ProjectedEnclosure.of(hull, Collections.singletonList(key),
                Collections.singletonList(label), "map", Optional.<EnclosureHullKey>empty(), noNodes,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC));
            children.add(hull);
            siblingHulls.add(hull);
            labels.add(label);
        }
        List<EnclosureKey> rootKeys = Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "root")));
        EnclosureHullKey rootHull = EnclosureHullKey.of(rootKeys);
        enclosures.add(ProjectedEnclosure.of(rootHull, rootKeys,
            Collections.singletonList(SafeNodeLabel.of("Root", "Root")), "map",
            Optional.<EnclosureHullKey>empty(), noNodes, children, true, BoundaryTier.EMPHATIC));
        GraphProjection projection = projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            for (int first = 0; first < 5; first++) {
                for (int second = first + 1; second < 5; second++) {
                    LayoutPoint firstAnchor = frame.positions().anchors().get(siblingHulls.get(first));
                    LayoutPoint secondAnchor = frame.positions().anchors().get(siblingHulls.get(second));
                    assertThat(boxesOverlap(firstAnchor, widthOf(labels.get(first)), heightOf(labels.get(first)),
                        secondAnchor, widthOf(labels.get(second)), heightOf(labels.get(second)))).isFalse();
                }
            }
        }
    }
```

(`projection(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>)` and `request(WorkspaceId, GraphProjection, GraphProjection, List<PinProjection>)` are the real `TypedForcesShould` helper signatures; `SafeNodeLabel.of(String, String)` takes the full and display texts.)

```java
    @Test
    public void settledSiblingBoundariesRemainSeparated() {
        // Same fixture as siblingBoundariesSeedWithoutOverlap; run 1500 steps.
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            for (int step = 0; step < 1500; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            // Assert no sibling pair boxes overlap, as above.
        }
    }

    @Test
    public void nestedGroupRingsDoNotInterleave() {
        // One root, two sibling groups, each with three subgroup boundaries.
        // Assert: for each sibling pair (first group, second group),
        //   center distance >= span(first) + span(second) + SIBLING_GAP
        // where span(hull) = ringRadius(hull) + max member reach, computed with the
        // BoundarySizes formulas; the test recomputes ringRadius from the fixture labels.
    }

    @Test
    public void rootFramesSeparateOnTheTopRing() {
        // Two maps, each with one group. Assert the root anchors' distance is
        // >= (rootWidth(first) + rootWidth(second)) * 0.5, rootWidth = 2 * (reach + FRAME_CLEARANCE).
    }

    @Test
    public void pinnedBoundariesKeepTheirForcedPositions() {
        // Pin two sibling groups at the same point; after apply + 300 steps both anchors
        // stay at the pin positions (the documented pin override).
    }

    @Test
    public void repeatedSettlesAreDeterministic() {
        // Two identical apply+step sequences produce identical final LayoutPositions.
    }
```

- [ ] **Step 2: Run the layout tests and confirm the new regressions fail**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*BoundarySeparationShould' -PTestLoggingFull
```

Expected: the new tests fail (seeds place wide siblings at the flat 100/60 ring radii so wide boxes overlap; `BoundarySizes` does not exist yet, so the file must compile after Step 3's class is added — if the compile fails first, the tests are still red for the right reason: the feature is absent).

- [ ] **Step 3: Apply the packing layout**

In `GraphStreamLayoutEngine.java`:

1. Remove node-particle machinery: `NODE_RADIUS`, the node loop and `nodeIds` in `topology()`, `DesiredParticle.node`, `encodeNode`, the `directNodes()` containment links, the `containingEnclosureOfNode` map, `nodeSeed`, and the node branch of `positionFor`. `topology()` becomes: anchors for every enclosure (radius from `BoundarySizes`), edges via `endpointId` (enclosure path only), hierarchy links via `addHierarchyLink` with `hierarchyRestLength(depths.get(child))`. Keep `enclosureDepths`, `depthOf`, `hierarchyRestLength`, `addHierarchyLink`, and the `ForceLink`/`ForceKind` types unchanged. `frame()` keeps `LayoutPositions.of(nodes, anchors)` with an always-empty nodes map.

2. Remove seed randomness: delete `MIN_INITIAL_POSITION_SPREAD`, `INITIAL_POSITION_SPREAD_PER_NODE`, `initialPositionSpread`, `initialPosition`, and `seedBytes` (the per-particle identity hashing). KEEP `workspaceBytes` — `initializeGraph` still derives the solver `Random` seed from it. Keep the `Random` passed to `TypedSpringBox` and keep `initializeGraph` otherwise unchanged.

3. Add the `BoundarySizes` class (package-private, next to `Seeds`):

```java
    static final class BoundarySizes {
        static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
        static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
        static final double BOUNDARY_PADDING = 8.0;
        static final double SIBLING_GAP = 8.0;
        static final double FRAME_CLEARANCE = 16.0;

        private final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        private final Map<EnclosureHullKey, Size> sizes = new LinkedHashMap<EnclosureHullKey, Size>();

        BoundarySizes(final GraphProjection projection) {
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                enclosuresByHull.put(enclosure.hullKey(), enclosure);
            }
        }

        double boundaryRadius(final EnclosureHullKey key) {
            final Size size = sizeOf(key);
            return 0.5 * Math.hypot(size.width, size.height);
        }

        double ringRadius(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<EnclosureHullKey> children = enclosure.directEnclosures();
            final int count = children.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (final EnclosureHullKey child : children) {
                final Size size = sizeOf(child);
                maxWidth = Math.max(maxWidth, size.width);
                maxHeight = Math.max(maxHeight, size.height);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        private Size sizeOf(final EnclosureHullKey key) {
            final Size cached = sizes.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final Size size;
            if (enclosure.directEnclosures().isEmpty()) {
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (final SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                size = new Size(width, height);
            }
            else {
                double reach = 0.0;
                final double radius = ringRadius(key);
                for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                    reach = Math.max(reach, radius + reachOf(child));
                }
                size = new Size(2.0 * (reach + FRAME_CLEARANCE), 2.0 * (reach + FRAME_CLEARANCE));
            }
            sizes.put(key, size);
            return size;
        }

        private double reachOf(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            if (enclosure.directEnclosures().isEmpty()) {
                return 0.5 * Math.hypot(sizeOf(key).width, sizeOf(key).height);
            }
            final double radius = ringRadius(key);
            double reach = 0.0;
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                reach = Math.max(reach, radius + reachOf(child));
            }
            return reach;
        }

        private static final class Size {
            private final double width;
            private final double height;

            Size(final double width, final double height) {
                this.width = width;
                this.height = height;
            }
        }
    }
```

Note: `sizeOf` computes the frame size from `ringRadius(key)` + `reachOf(child)`; `reachOf` recurses into children. The memoization order is safe because `sizeOf` recurses depth-first before storing the parent.

4. Replace the `Seeds` class with a packing seed:

```java
    private static final class Seeds {
        private final BoundarySizes sizes;
        private final Map<EnclosureHullKey, Position> centers = new LinkedHashMap<EnclosureHullKey, Position>();

        Seeds(final BoundarySizes sizes) {
            this.sizes = sizes;
        }

        Position positionFor(final DesiredParticle particle) {
            return center(particle.anchorKey);
        }

        private Position center(final EnclosureHullKey key) {
            final Position cached = centers.get(key);
            if (cached != null) {
                return cached;
            }
            final Position center;
            if (isTopLevel(key)) {
                center = topRingPosition(key);
            }
            else {
                final ProjectedEnclosure enclosure = sizes.enclosure(key);
                final EnclosureHullKey parentKey = enclosure.parentHull().get();
                final Position parentCenter = center(parentKey);
                final int index = sizes.enclosure(parentKey).directEnclosures().indexOf(key);
                final int count = sizes.enclosure(parentKey).directEnclosures().size();
                final double radius = sizes.ringRadius(parentKey);
                final double angle = 2.0 * Math.PI * Math.max(0, index) / Math.max(1, count);
                center = new Position(parentCenter.x + radius * Math.cos(angle),
                    parentCenter.y + radius * Math.sin(angle));
            }
            centers.put(key, center);
            return center;
        }

        private boolean isTopLevel(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = sizes.enclosure(key);
            return enclosure == null || enclosure.mapRoot() || !enclosure.parentHull().isPresent();
        }

        private Position topRingPosition(final EnclosureHullKey key) {
            final List<EnclosureHullKey> roots = new ArrayList<EnclosureHullKey>();
            for (final ProjectedEnclosure enclosure : sizes.enclosures()) {
                if (enclosure.mapRoot()) {
                    roots.add(enclosure.hullKey());
                }
            }
            final int count = roots.size();
            if (count <= 1) {
                return new Position(0.0, 0.0);
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (final EnclosureHullKey root : roots) {
                maxWidth = Math.max(maxWidth, sizes.sizeOf(root).width);
                maxHeight = Math.max(maxHeight, sizes.sizeOf(root).height);
            }
            final double radius = Math.hypot(maxWidth + BoundarySizes.SIBLING_GAP,
                maxHeight + BoundarySizes.SIBLING_GAP) / (2.0 * Math.sin(Math.PI / count));
            final int index = Math.max(0, roots.indexOf(key));
            final double angle = 2.0 * Math.PI * index / count;
            return new Position(radius * Math.cos(angle), radius * Math.sin(angle));
        }
    }
```

Adjust the private accessors as needed so `Seeds` can read `BoundarySizes` state (`enclosure(EnclosureHullKey)`, `enclosures()`, `sizeOf(...)`, `ringRadius(...)` — make them package-private in `BoundarySizes`).

5. Wire the sizes into particles and pins:

- In `synchronize(...)`, build `final BoundarySizes sizes = new BoundarySizes(request.projection());` and `final Seeds seeds = new Seeds(sizes);`.
- `topology(...)` gains the `BoundarySizes sizes` parameter; anchor particles use `DesiredParticle.anchor(enclosure.hullKey(), sizes.boundaryRadius(enclosure.hullKey()))`.
- Replace `activePins` with a source-keyed map and pin anchors by their endpoint keys:

```java
    private static Map<SourceNodeKey, PinProjection> pinsBySource(final List<PinProjection> pins) {
        final Map<SourceNodeKey, PinProjection> result = new LinkedHashMap<SourceNodeKey, PinProjection>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.put(SourceNodeKey.persisted(pin.source()), pin);
            }
        }
        return result;
    }
```

and in `synchronize(...)` replace the node-key pin lookup with:

```java
            final PinProjection pin = pinFor(desired, pinsBySource);
```

with:

```java
    private static PinProjection pinFor(final DesiredParticle desired,
            final Map<SourceNodeKey, PinProjection> pinsBySource) {
        for (final EnclosureKey endpoint : desired.anchorKey.endpointKeys()) {
            final PinProjection pin = pinsBySource.get(endpoint.source());
            if (pin != null) {
                return pin;
            }
        }
        return null;
    }
```

`PinProjection.source()` returns the persisted `NodeReference`; `SourceNodeKey.persisted(NodeReference)` constructs the matching key.

In `TypedSpringBox.java`:

1. Make `baseSeparationRadius()` return `8.0` as before (it stays the native repulsion baseline) and add the boundary repulsion:

```java
    void addBoundaryRepulsion(final TypedNodeParticle particle, final Vector3 displacement) {
        for (final Map.Entry<String, TypedNodeParticle> entry : typedParticles.entrySet()) {
            final TypedNodeParticle other = entry.getValue();
            if (other == particle) {
                continue;
            }
            final Point3 own = particle.getPosition();
            final Point3 position = other.getPosition();
            double dx = own.x - position.x;
            double dy = own.y - position.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance == 0.0) {
                dx = particle.getId().toString().compareTo(other.getId().toString()) < 0 ? 1.0 : -1.0;
                dy = 0.0;
                distance = 1.0;
            }
            final double extent = particle.boundaryRadius() + other.boundaryRadius()
                + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP;
            final double penetration = extent - distance;
            if (penetration <= 0.0) {
                continue;
            }
            final double force = penetration * BOUNDARY_REPULSION_FACTOR;
            displacement.set(0, displacement.at(0) + dx / distance * force);
            displacement.set(1, displacement.at(1) + dy / distance * force);
        }
    }
```

with `private static final double BOUNDARY_REPULSION_FACTOR = 0.5;` and `boundaryRadius()` added to `TypedNodeParticle` (returning `separationRadius`; `configure(radius, pinned)` already stores it).

2. In `TypedNodeParticle.java`, call the boundary repulsion from both repulsion overrides after `scaleRepulsion`:

```java
    @Override
    protected void repulsionN2(final Vector3 displacement) {
        final Vector3 before = new Vector3(disp);
        super.repulsionN2(displacement);
        scaleRepulsion(before);
        typedBox.addBoundaryRepulsion(this, displacement);
    }
```

(same for `repulsionNLogN`). Note: the boundary repulsion is intentionally NOT added to `rawBudgetedRepulsion` (it is not cross-map attraction; the cross-map budget remains the native repulsion + cross-map springs as before).

In `PerceptualIdlePolicy.java`, re-derive the spike thresholds for anchor-only dynamics. Keep the structure; change the constants to:

```java
    private static final int SPIKE_CONSECUTIVE = 8;
    private static final double SPIKE_RMS = 0.05;
    private static final double SPIKE_MAX = 0.10;
```

Then validate: the separation regressions in `BoundarySeparationShould` and the reworked `TypedForcesShould` must pass; `PerceptualIdlePolicyShould` may need its constant expectations updated to the new values (it pins the constants; update the assertions to the new values).

- [ ] **Step 4: Rework `TypedForcesShould` to anchor-only semantics**

The current suite builds projections with nodes; rework to enclosures only:

- `produceIdenticalFramesForEqualRequests`, `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin`, `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours`, `aTopologyChangeDoesNotTeleportRetainedParticles`: keep, with the fixture projections built from enclosures only (baseline root + one group child). The movement bounds stay 8.0.
- `largerWorkspacesSeedWiderThanTheMinimumSpread`: build 200 sibling group boundaries under one root (each a one-label hull) and keep the assertion `greatestDistanceBetweenDistinctPositions(frame) > 150.0` (the packing ring for 200 siblings has radius ~917).
- `hierarchyAnchorsSeedOnTheGroupRing`: replace the fixed 100-unit expectation with the packing expectation: three sibling groups under the root seed on a ring whose radius is `BoundarySizes.ringRadius(rootHull)` and whose adjacent chord is `>= maxWidth + SIBLING_GAP` (recompute from the fixture labels with the `BoundarySizes` formulas); keep `isCloseTo` only if the ring radius matches the formula.
- `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`: keep as-is (anchors only; the fixture already uses enclosures) — verify the rest-100/60 springs still produce `nestedDistance > 100` and `< peerDistance` with the new dynamics; if the measured values change, keep the assertions (they pin the hierarchy-rest behavior) and report the new measured values.
- Delete the node-specific helpers and tests: `node(...)`, `hierarchyProjection` node children, `greatestMovementBetween` node assertions, and any test whose only subject is a node particle; move the retained enclosure fixtures into shared helpers.

- [ ] **Step 5: Run the layout suite and the full module suite**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*layout*' -PTestLoggingFull
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Fix any layout-package fallout (files listed in this task) to the anchor-only model. If the settle-time separation test does not hold within 1500 steps (boundaries drift together), raise `BOUNDARY_REPULSION_FACTOR` in steps of 0.25 up to 2.0 and record the final value and measured equilibrium penetration in the report; do not change the packing formulas. If idle fires before separation is reached (the settle loop stops early in the UI), lower `SPIKE_MAX`/`SPIKE_RMS` no further than `0.05`/`0.02` and record the measured settle steps in the report.

- [ ] **Step 6: Falsifiability probes (disposable, no worktree residue)**

For each probe: record SHA-256, mutate, run the Step 2 command plus `--tests '*TypedForcesShould'`, confirm the named tests fail, restore byte-exact, verify SHA-256, rerun green:

1. Ring radius regression: in `BoundarySizes.ringRadius`, replace `Math.max` over child widths with the mean width -> `siblingBoundariesSeedWithoutOverlap` (and the wide-sibling cases of `TypedForcesShould`) fail.
2. Repulsion removal: delete the `typedBox.addBoundaryRepulsion(this, displacement)` calls -> `settledSiblingBoundariesRemainSeparated` fails.
3. Top-ring regression: place all root frames at the origin (topRingPosition returns `(0.0, 0.0)` for every root) -> `rootFramesSeparateOnTheTopRing` fails.

- [ ] **Step 7: Verify scope and commit**

```bash
test -z "$(git diff --cached --name-only)"
git add <every changed path from this task's Files list>
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-group-only-boundaries: Separate sibling boundaries by construction"
```

Expected staged names: a nonempty subset of this task's Files list, nothing else.

## Task 3: Render group boundaries from labels

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/LabelPlacementShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:**

- Consumes: `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions)`, `GeometryTextMetrics.measure(String, BoundaryTier)`, `AwtGeometryTextMetrics(Font, FontRenderContext)`, `LayoutSettleLoop.defaultMetrics()`, `HullGeometry.of(...)`, `HullIntersection`, `LabelPlacementEngine`, `GraphTheme`, `GraphPaintState`, `CanvasState`, `ProjectedEnclosure.mapRoot()`.
- Produces: `computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)` where empty enclosures (no direct nodes, no child hulls) get a label-sized octagon centered on the anchor (measured label bounds + `BOUNDARY_PADDING`, octagonalized by the existing 8-normal clip), root frames keep the convex-closure path; the painter draws non-root hulls in coral (`#DF625D`) and stops painting node circles/labels/highlights; the hit index, search model, and accessibility surface expose boundaries only; `HullIntersection` gains a sibling-overlap predicate for tests.

- [ ] **Step 1: Add the failing boundary-rendering regressions**

In `HullGeometryShould.java` (or a new `EmptyHullGeometryShould.java` in the geometry package) add:

```java
    @Test
    public void emptyEnclosuresSizeTheirOctagonFromTheLabel() {
        // Build an enclosure with one label "Wide group label" and no children;
        // compute hulls via GraphGeometryEngine.computeHulls(projection, positions, metrics)
        // with a fixed AwtGeometryTextMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12),
        // new FontRenderContext(null, true, true));
        // Assert: hull.exactPolygon()'s bounding box width is within
        // [measuredWidth + 2*BOUNDARY_PADDING - 1, measuredWidth + 2*BOUNDARY_PADDING + 1]
        // (measure the label with the same metrics), and the polygon is centered on the anchor.
    }
```

In `HullIntersectionShould.java` add:

```java
    @Test
    public void siblingOverlapPredicateRejectsNestedContainment() {
        // Two octagons: one inside the other -> siblingOverlap(outer, inner) is false
        // (containment is not sibling overlap); two intersecting octagons -> true.
    }
```

In `GraphCanvasPaintShould.java` add a test that a projection containing only enclosures (no nodes) paints hull shapes and never paints node circles (assert via the painted image that no node-fill-colored circle exists, or via the existing image-assertion helper of that suite).

- [ ] **Step 2: Run the geometry/canvas tests and confirm the new regressions fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*HullGeometryShould' --tests '*HullIntersectionShould' --tests '*GraphCanvasPaintShould' -PTestLoggingFull
```

Expected: the new tests fail (empty hulls are the fixed 16-unit octagons; no sibling predicate exists; node circles are still painted).

- [ ] **Step 3: Apply the boundary rendering**

In `GraphGeometryEngine.java`:

1. Change `computeHulls(GraphProjection, LayoutPositions)` to `computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)` (add the parameter; keep the cache key unchanged).
2. In `computeHull`, replace the `empty` branch so an empty enclosure's supports come from its measured label bounds instead of the anchor point:

```java
            final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
            for (int index = 0; index < 8; index++) {
                final double nx = NORMALS[index][0];
                final double ny = NORMALS[index][1];
                double maxSupport = Double.NEGATIVE_INFINITY;
                if (empty) {
                    final LayoutPoint anchor = positions.anchors().get(hullKey);
                    final Dimension2D label = labelSize(enclosure, metrics);
                    final double halfWidth = label.getWidth() * 0.5 + BOUNDARY_PADDING;
                    final double halfHeight = label.getHeight() * 0.5 + BOUNDARY_PADDING;
                    maxSupport = nx * anchor.x() + ny * anchor.y()
                        + Math.max(Math.abs(nx) * halfWidth, Math.abs(ny) * halfHeight);
                }
                else {
                    // unchanged child/nodes path
                }
                supports[index] = maxSupport + HULL_CLEARANCE;
            }
```

with `private static final double BOUNDARY_PADDING = 8.0;` and:

```java
    private static Dimension2D labelSize(final ProjectedEnclosure enclosure, final GeometryTextMetrics metrics) {
        Dimension2D largest = null;
        for (final SafeNodeLabel label : enclosure.labels()) {
            final Dimension2D measured = metrics.measure(label.displayText(), enclosure.boundaryTier());
            if (largest == null || measured.getWidth() * measured.getHeight() > largest.getWidth()
                    * largest.getHeight()) {
                largest = measured;
            }
        }
        if (largest == null) {
            throw new IllegalArgumentException("Enclosures must carry at least one label");
        }
        return largest;
    }
```

3. Thread `metrics` through `computeHull` (parameter). Keep the empty label anchor at the anchor position (the octagon is centered on the anchor). `BOUNDARY_PADDING` must equal `GraphStreamLayoutEngine.BoundarySizes.BOUNDARY_PADDING`; add a comment noting the cross-package invariant.

In `GraphPainter.java`:

1. Remove `paintNodes(...)` and its call; remove the node label loop in `paintLabels(...)`; remove the node loop in `paintHighlights(...)`; remove `paintPins`' node-geometry skip condition and use the hull lookup instead:

```java
    private static void paintPins(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme, final boolean dimUnrelated,
            final Set<ProjectedEndpointKey> visibleEndpoints) {
        for (final PinProjection pin : state.projection().pins()) {
            if (!pin.active() || !pin.projectedNode().isPresent()) {
                continue;
            }
            final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofNode(pin.projectedNode().get());
            if (!visibleEndpoints.contains(endpoint) || hullOf(state, endpoint) == null) {
                continue;
            }
            // unchanged cross drawing at pin.x()/pin.y()
        }
    }

    private static HullGeometry hullOf(final CanvasState state, final ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            return null;
        }
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(endpoint.enclosure().get())) {
                return state.geometry().hulls().get(enclosure.hullKey());
            }
        }
        return null;
    }
```

2. Paint non-root boundaries in coral: in `paintHulls`, replace the fill/stroke colors for non-root enclosures:

```java
            if (enclosure.mapRoot()) {
                graphics.setColor(theme.hullFill(enclosure.mapReferenceId(), enclosure.boundaryTier()));
                graphics.setColor(theme.hullStroke(enclosure.mapReferenceId(), enclosure.boundaryTier()));
            }
            else {
                graphics.setColor(GROUP_BOUNDARY_COLOR);
                graphics.setColor(GROUP_BOUNDARY_COLOR);
            }
```

with `private static final Color GROUP_BOUNDARY_COLOR = new Color(0xDF, 0x62, 0x5D);` (fixed coral, matching the map-view marker).

3. Remove now-unused imports (ProjectedNode, NodeGeometry, Ellipse2D where unused).

In `GraphHitIndex.java`: remove the `EndpointEntry.forNode(...)` node entries and the `geometry.nodes()` loop; hull entries only (the class already builds hull entries; the node loop and its imports go).

In `GraphSearchModel.java`: search enclosure labels instead of node labels — the node loop becomes an enclosure loop over `state.projection().enclosures()` using each `endpointKeys()` entry and `labels()` text; keep the `SafeText`/`SearchMatch` shape and the existing API.

In `AccessibleGraphCanvas.java`: the node accessibility branch (lines around 470, reading `projection.prominence()`) is replaced by enclosure endpoints: each visible boundary contributes its label text and hull position; keep the same accessibility API (names/roles may stay "boundary" via the existing enclosure path if present, otherwise "node" for compatibility).

In `HullIntersection.java`: add

```java
    public static boolean siblingOverlap(final HullGeometry first, final HullGeometry second) {
        // Returns true iff the two convex hull polygons intersect in more than a
        // shared boundary point (strict interior intersection). Uses the existing
        // polygon intersection logic of this class; containment is NOT overlap.
    }
```

Implement it with the class's existing polygon-separation machinery (edge-normal separation): two convex polygons are disjoint iff a separating axis exists; containment is detected when one polygon lies entirely inside the other (no separating axis but no edge intersection) and returns false.

In `LayoutSettleLoop.java` and `LayoutWorker.java`: pass the metrics into `computeHulls(...)` at the call sites (`geometryEngine.computeHulls(projection, positions, metrics)` — `LayoutSettleLoop` already holds `metrics`; `LayoutWorker` uses the same `defaultMetrics()` as `LayoutSettleLoop` — add the identical private helper or pass the loop's metrics through).

- [ ] **Step 4: Run the geometry/canvas suites and the full module suite**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*geometry*' --tests '*canvas*' --tests '*LayoutSettleLoopShould' --tests '*LayoutWorkerShould' -PTestLoggingFull
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Rework the affected suites: hull tests that assert the fixed 16-unit empty octagon now assert the label-sized octagon; paint tests that draw node circles now assert boundary-only painting; search/accessibility tests assert enclosure search results; `LabelPlacementShould` keeps its interior placement expectations (labels now fit inside the label-sized octagons). If any file OUTSIDE this task's Files list needs a change, do not stage it; report it to the controller.

- [ ] **Step 5: Falsifiability probe (disposable, no worktree residue)**

Record SHA-256 of `GraphGeometryEngine.java`; mutate the empty branch to keep the old anchor-point supports (drop the label sizing); confirm `emptyEnclosuresSizeTheirOctagonFromTheLabel` fails; restore byte-exact; verify SHA-256; rerun green.

- [ ] **Step 6: Verify scope and commit**

```bash
test -z "$(git diff --cached --name-only)"
git add <every changed path from this task's Files list>
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-group-only-boundaries: Render group boundaries from labels"
```

Expected staged names: a nonempty subset of this task's Files list, nothing else.
