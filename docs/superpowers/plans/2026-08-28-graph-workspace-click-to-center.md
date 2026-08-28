# Graph Workspace Click-to-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Single-clicking a node or group-marked boundary in the Graph Workspace selects it in the graph and jumps the mindmap to that node — selecting and centering it — while double-click/Enter keep their existing open gesture (also centered). Group-marked boundaries are counted as nodes in every graph node count.

**Architecture:** Extend the existing intent/command path. A new `GraphIntent.RevealSourceNode` is emitted on single click; the window executes the existing `GraphCommands.openSource` silently (no status message, graph canvas keeps focus). `SourceNavigation.open` gains a `mapController.centerNode` call after `select`, which also upgrades double-click/Enter. Node accounting switches from the (now always empty) `ProjectedNode` list to counting non-map-root `ProjectedEnclosure`s.

**Tech Stack:** Java 8 target (JDK 21.0.8-zulu runtime), Gradle multi-project, JUnit 4 + AssertJ + Mockito, Swing. All changes in the `freeplane_plugin_graph` module.

## Global Constraints

- Use `gradle` (never `gradlew`); run module tests with `gradle :freeplane_plugin_graph:test --tests "<Fully.Qualified.Class>"`.
- Java runtime: `~/.sdkman/candidates/java/21.0.8-zulu` (export `JAVA_HOME` if `gradle` does not resolve it).
- Java 8 language target; 4-space indentation; UTF-8 source encoding; no new dependencies.
- All changes are inside `freeplane_plugin_graph`; do NOT modify `freeplane_api` or `freeplane` core sources.
- JUnit 4 tests, AssertJ assertions, Mockito mocks — match the style of the existing test classes you modify.
- Commit message prefix for every commit: `2026-08-28-graph-workspace-click-to-center: <summary>`.
- Never put a plain `##` heading inside a task body; use `###` for subheadings.

## Task 1: RevealSourceNode intent and single-click emission

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphIntent.java:65-67`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:284-286`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:363-374`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java` (append one new test after `translateSelectionConnectionZoomPanAndUninstallGestures`)

**Interfaces:**

- Consumes: `GraphInteractionListener.onGraphIntent(GraphIntent)` (unchanged); `GraphIntent.ChangeSelection(Optional<ProjectedEndpointKey>)` (unchanged); `GraphHitIndex.endpointAt(LayoutPoint)` (unchanged); the test helper `click(canvas, MouseEvent.MOUSE_CLICKED, worldX, worldY, clickCount, button)` and `clickAt(canvas, id, screenX, screenY, clickCount, button)` from `GraphInteractionControllerShould`.
- Produces: `GraphIntent.RevealSourceNode` — a value class mirroring `GraphIntent.OpenSourceNode`: constructor `RevealSourceNode(ProjectedEndpointKey)`, factory `of(ProjectedEndpointKey)`, accessors `endpoint()` and `key()`, `equals`/`hashCode` over the endpoint.

- [ ] **Step 1: Add the `RevealSourceNode` intent class**

In `GraphIntent.java`, insert this class between the closing brace of `OpenSourceNode` (line 65) and `public static final class Pin` (line 67):

```java
    public static final class RevealSourceNode extends GraphIntent {
        private final ProjectedEndpointKey endpoint;

        public RevealSourceNode(final ProjectedEndpointKey endpoint) {
            this.endpoint = require(endpoint, "endpoint");
        }

        public static RevealSourceNode of(final ProjectedEndpointKey endpoint) {
            return new RevealSourceNode(endpoint);
        }

        public ProjectedEndpointKey endpoint() {
            return endpoint;
        }

        public ProjectedEndpointKey key() {
            return endpoint;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof RevealSourceNode)) {
                return false;
            }
            final RevealSourceNode other = (RevealSourceNode) object;
            return endpoint.equals(other.endpoint);
        }

        @Override
        public int hashCode() {
            return endpoint.hashCode();
        }
    }
```

- [ ] **Step 2: Emit the intent on single click**

In `GraphInteractionController.java`, the single-click branch of `handleMouseClicked` currently ends:

```java
        setSelectionVisual(endpoint.orElse(null));
        emit(new GraphIntent.ChangeSelection(endpoint));
    }
```

Replace those three lines with:

```java
        setSelectionVisual(endpoint.orElse(null));
        emit(new GraphIntent.ChangeSelection(endpoint));
        if (endpoint.isPresent()) {
            emit(new GraphIntent.RevealSourceNode(endpoint.get()));
        }
    }
```

The double-click branch (which emits `GraphIntent.OpenSourceNode` and returns) stays untouched.

- [ ] **Step 3: Update the existing single-click assertions**

In `GraphInteractionControllerShould.java`, inside `translateSelectionConnectionZoomPanAndUninstallGestures`, the block after the first single click currently reads:

```java
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.last()).isInstanceOf(GraphIntent.ChangeSelection.class);
        assertThat(((GraphIntent.ChangeSelection) listener.last()).selection())
            .contains(fixture.firstHullEndpoint);
        assertThat(canvas.paintState().selection()).contains(fixture.firstHullEndpoint);
```

Replace the two `listener.last()` assertions with:

```java
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).contains(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstHullEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstHullEndpoint));
        assertThat(canvas.paintState().selection()).contains(fixture.firstHullEndpoint);
```

Leave every other assertion in that test method unchanged (the CONNECT-tool and uninstall sections still use `listener.last()`, and no click precedes them).

- [ ] **Step 4: Add the single-click lockstep test**

Append this test method to `GraphInteractionControllerShould` (right after `translateSelectionConnectionZoomPanAndUninstallGestures`):

```java
    @Test
    public void revealHitEndpointSourceOnSingleClickWhileEmptySpaceOnlyClearsSelection() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).containsExactly(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstHullEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstHullEndpoint));

        dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, 390, 290, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).containsExactly(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstHullEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstHullEndpoint),
            new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        controller.uninstall();
    }
```

The `Optional` and `ProjectedEndpointKey` imports already exist in this test class.

- [ ] **Step 5: Run the controller tests and confirm they pass**

```bash
cd /data/home/guest/Development/freeplane
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould"
```

Expected: BUILD SUCCESSFUL, all tests in `GraphInteractionControllerShould` pass, including the two updated/added ones.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphIntent.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
git commit -m "2026-08-28-graph-workspace-click-to-center: Emit RevealSourceNode on single click"
```

## Task 2: Silent openSource execution for RevealSourceNode

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:647-659`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1147-1150`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (imports + one new test)

**Interfaces:**

- Consumes: `GraphIntent.RevealSourceNode` from Task 1, with `endpoint(): ProjectedEndpointKey` and `key()`; `GraphCommands.openSource(SourceNodeKey)` (exists); `GraphIntent.OpenSourceNode` (exists); the private helper `source(ProjectedEndpointKey): SourceNodeKey` in `GraphWorkspaceWindowModel` (exists, handles node and enclosure endpoints); the test fixture `fixture(Viewport, CanvasState, List<MapRegistration>, boolean)` and `fixture.model(Consumer<String>)` in `GraphWorkspaceWindowModelShould` (exist).
- Produces: `executeCommand(GraphCommand, boolean silent)` — silent executions skip the status-message sink but still run `refreshPresentation()`, `applyInitialViewport(currentState)`, and `graphFocus.run()` when `editorViewActivated()`. `handleIntent` routes `RevealSourceNode` to the silent execution.

- [ ] **Step 1: Refactor `executeCommand` with a silent flag**

In `GraphWorkspaceWindow.java`, replace the whole current `executeCommand` method (lines 647-659):

```java
    private GraphCommandResult executeCommand(final GraphCommand command) {
        final GraphCommandResult result = handle.execute(Objects.requireNonNull(command, "command"));
        refreshPresentation();
        applyInitialViewport(currentState);
        if (result != null && result.editorViewActivated()) {
            graphFocus.run();
        }
        if (result != null && (result.status() == GraphCommandResult.Status.REJECTED
                || result.status() == GraphCommandResult.Status.NO_OP)) {
            commandMessageSink.accept(TextUtils.format(result.messageKey(), result.messageArguments().toArray()));
        }
        return result;
    }
```

with:

```java
    private GraphCommandResult executeCommand(final GraphCommand command) {
        return executeCommand(command, false);
    }

    private GraphCommandResult executeCommand(final GraphCommand command, final boolean silent) {
        final GraphCommandResult result = handle.execute(Objects.requireNonNull(command, "command"));
        refreshPresentation();
        applyInitialViewport(currentState);
        if (result != null && result.editorViewActivated()) {
            graphFocus.run();
        }
        if (!silent && result != null && (result.status() == GraphCommandResult.Status.REJECTED
                || result.status() == GraphCommandResult.Status.NO_OP)) {
            commandMessageSink.accept(TextUtils.format(result.messageKey(), result.messageArguments().toArray()));
        }
        return result;
    }
```

All existing callers keep compiling through the one-argument overload (unchanged behavior).

- [ ] **Step 2: Route `RevealSourceNode` to the silent execution**

In `GraphWorkspaceWindow.java`, `handleIntent` currently has the `OpenSourceNode` branch:

```java
        else if (intent instanceof GraphIntent.OpenSourceNode) {
            final ProjectedEndpointKey endpoint = ((GraphIntent.OpenSourceNode) intent).endpoint();
            executeCommand(GraphCommands.openSource(source(endpoint)));
        }
```

Insert the new branch immediately BEFORE it:

```java
        else if (intent instanceof GraphIntent.RevealSourceNode) {
            final ProjectedEndpointKey endpoint = ((GraphIntent.RevealSourceNode) intent).endpoint();
            executeCommand(GraphCommands.openSource(source(endpoint)), true);
        }
```

- [ ] **Step 3: Add the window-level test**

In `GraphWorkspaceWindowModelShould.java`, add these imports (alphabetical position among the existing `org.freeplane.plugin.graph.*` imports):

```java
import org.freeplane.plugin.graph.canvas.GraphIntent;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
```

Then append this test method (place it after `doesNotPublishMessageForAppliedCommand`):

```java
    @Test
    public void revealsSourceNodeSilentlyOnSingleClickIntentWhileDoubleClickOpenStillReportsFailures() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            enclosureState(ACTIVE_ID), Collections.singletonList(registration(ACTIVE_ID, "Active",
                MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.rejected(emptyDocument(), "graph_workspace.test.rejected")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);
        final SourceNodeKey source = SourceNodeKey.transientPath(ACTIVE_ID, Collections.emptyList());
        final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source));

        model.acceptIntent(new GraphIntent.RevealSourceNode(endpoint));

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.OpenSource.class);
        assertThat(((GraphCommands.OpenSource) commands.getValue()).source()).isEqualTo(source);
        assertThat(messages).isEmpty();

        model.acceptIntent(new GraphIntent.OpenSourceNode(endpoint));

        assertThat(messages).containsExactly("graph_workspace.test.rejected[]");
        model.close();
    }
```

Notes: `enclosureState(ACTIVE_ID)` already exists in this test class and builds a single-enclosure state whose enclosure source is `SourceNodeKey.transientPath(ACTIVE_ID, Collections.emptyList())`; `commandResult(...)`, `emptyDocument()`, `ArgumentCaptor`, `WorkspaceTransition`, `SourceNodeKey`, `EnclosureKey`, `GraphCommands`, and `GraphCommand` are already imported/used in this file.

- [ ] **Step 4: Run the window tests and confirm they pass**

```bash
cd /data/home/guest/Development/freeplane
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould"
```

Expected: BUILD SUCCESSFUL, all tests pass, including `revealsSourceNodeSilentlyOnSingleClickIntentWhileDoubleClickOpenStillReportsFailures`.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "2026-08-28-graph-workspace-click-to-center: Execute single-click reveal silently"
```

## Task 3: Center the resolved node in SourceNavigation.open

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/SourceNavigation.java:65`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/SourceNavigationShould.java`

**Interfaces:**

- Consumes: `MapController.centerNode(NodeModel)` (exists in core, no change); `MapController.select(NodeModel)` (exists); `FreeplaneMapCommandExecutor.TraversalResolver.resolve(MapLease, SourceNodeKey): Optional<NodeModel>` (exists); the `Fixture` inner class of `SourceNavigationShould` with its mocked `mapController` (Mockito mock).
- Produces: `SourceNavigation.open(SourceNodeKey)` now selects the resolved node AND centers it (select first, then center), on the EDT, before returning `APPLIED`.

Note: per the design spec's known limits, centering is best-effort in the rare case where the source map has no displayed view yet (`MapController.select` defers via an `IMapViewChangeListener` and re-selects after display, ending in scroll-into-view). Do not add extra listener machinery; the fallback is accepted.

- [ ] **Step 1: Add the centering call**

In `SourceNavigation.java`, the `open` method currently contains:

```java
                mapController.select(resolved.get());
                return GraphCommandResult.from(WorkspaceTransition.applied(results.currentDocument(), SOURCE_OPENED))
                    .withEditorViewActivated(true);
```

Replace with:

```java
                mapController.select(resolved.get());
                mapController.centerNode(resolved.get());
                return GraphCommandResult.from(WorkspaceTransition.applied(results.currentDocument(), SOURCE_OPENED))
                    .withEditorViewActivated(true);
```

- [ ] **Step 2: Record centering in the test fixture**

In `SourceNavigationShould.java`, inside the `Fixture` constructor, after the existing `doAnswer(...).when(mapController).select(any(NodeModel.class));` block, add:

```java
            doAnswer(invocation -> {
                edt.requireOnEdt("source centering");
                centered = invocation.getArgument(0);
                return null;
            }).when(mapController).centerNode(any(NodeModel.class));
```

And add the field next to `private NodeModel selected;`:

```java
        private NodeModel centered;
```

- [ ] **Step 3: Assert centering in the three navigation tests**

In `selectsAReachableSourceThroughTheTraversalResolver`, after the existing line `assertThat(fixture.selected).isSameAs(nodes.node);`, add:

```java
        assertThat(fixture.centered).isSameAs(nodes.node);
        final org.mockito.InOrder order = org.mockito.Mockito.inOrder(fixture.mapController);
        order.verify(fixture.mapController).select(nodes.node);
        order.verify(fixture.mapController).centerNode(nodes.node);
```

In `rejectsUnavailableOrUnreachableSourcesWithoutSelectingAnything`, after `assertThat(fixture.selected).isNull();` (which appears twice, once per rejection), add `assertThat(fixture.centered).isNull();` next to each.

In `navigatesToAnIdlessSourceWithoutAssigningAnId`, after `assertThat(fixture.selected).isSameAs(nodes.node);`, add `assertThat(fixture.centered).isSameAs(nodes.node);`.

- [ ] **Step 4: Run the navigation tests and confirm they pass**

```bash
cd /data/home/guest/Development/freeplane
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.command.SourceNavigationShould"
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/SourceNavigation.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/SourceNavigationShould.java
git commit -m "2026-08-28-graph-workspace-click-to-center: Center the source node after selecting it"
```

## Task 4: Count group-marked boundaries as nodes

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java:84-86`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1049-1073`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java:129-131`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:362`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java:55` (+ one new test)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (one call-site line + one new helper)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java` (imports + `stateWithCounts` rewrite)

**Interfaces:**

- Consumes: `ProjectedEnclosure.mapRoot(): boolean` (exists); `ProjectedEnclosure.mapReferenceId()` and `mapName()` (exist); `ProjectedEnclosure.of(...)` (exists, signature used by `GraphWorkspaceWindowModelShould.enclosureState`); `HullGeometry.of(List<LayoutPoint>, LayoutPoint)` and `GraphGeometry.of(Map, Map, Map)` (exist, used by `enclosureState`).
- Produces: `GraphProjection.projectedNodeCount()` counts non-map-root enclosures (group-marked boundaries), never map root frames. `updateMapRows` and `GraphStatusBar.Status.from` count the same per map. `GraphCanvas.paintComponent` uses `projectedNodeCount()`.

- [ ] **Step 1: Fix `GraphProjection.projectedNodeCount()`**

In `GraphProjection.java`, replace:

```java
    public int projectedNodeCount() {
        return nodes.size();
    }
```

with:

```java
    public int projectedNodeCount() {
        int count = 0;
        for (final ProjectedEnclosure enclosure : enclosures) {
            if (!enclosure.mapRoot()) {
                count++;
            }
        }
        return count;
    }
```

- [ ] **Step 2: Fix per-map row counts in `updateMapRows`**

In `GraphWorkspaceWindow.java`, replace the `ProjectedNode` loop plus the `ProjectedEnclosure` loop in `updateMapRows` (lines 1049-1073):

```java
            for (final ProjectedNode node : projection.nodes()) {
                final MapReferenceId mapId = node.mapReferenceId();
                RowAccumulator accumulator = accumulators.get(mapId);
                if (accumulator == null) {
                    accumulator = new RowAccumulator(mapId, node.mapName(), MapAvailability.AVAILABLE);
                    accumulators.put(mapId, accumulator);
                }
                else {
                    accumulator.displayName = node.mapName();
                }
                accumulator.projectedNodeCount++;
            }
            for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
                final MapReferenceId mapId = enclosure.mapReferenceId();
                RowAccumulator accumulator = accumulators.get(mapId);
                if (accumulator == null) {
                    accumulators.put(mapId, new RowAccumulator(mapId, enclosure.mapName(),
                        MapAvailability.AVAILABLE));
                }
                else if (accumulator.projectedNodeCount == 0) {
                    accumulator.displayName = enclosure.mapName();
                }
            }
```

with a single enclosure loop that counts non-map-root enclosures:

```java
            for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
                final MapReferenceId mapId = enclosure.mapReferenceId();
                RowAccumulator accumulator = accumulators.get(mapId);
                if (accumulator == null) {
                    accumulator = new RowAccumulator(mapId, enclosure.mapName(), MapAvailability.AVAILABLE);
                    accumulators.put(mapId, accumulator);
                }
                else if (accumulator.projectedNodeCount == 0) {
                    accumulator.displayName = enclosure.mapName();
                }
                if (!enclosure.mapRoot()) {
                    accumulator.projectedNodeCount++;
                }
            }
```

- [ ] **Step 3: Fix per-map counts in `GraphStatusBar.Status.from`**

In `GraphStatusBar.java`, replace:

```java
                for (final org.freeplane.plugin.graph.projection.ProjectedNode node : value.projection().nodes()) {
                    final Integer count = nodeCounts.get(node.mapReferenceId());
                    nodeCounts.put(node.mapReferenceId(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
```

with:

```java
                for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure
                        : value.projection().enclosures()) {
                    if (enclosure.mapRoot()) {
                        continue;
                    }
                    final Integer count = nodeCounts.get(enclosure.mapReferenceId());
                    nodeCounts.put(enclosure.mapReferenceId(),
                        Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
```

The status-bar total already flows through `projection.projectedNodeCount()` (fixed in Step 1); no change needed there.

- [ ] **Step 4: Fix the canvas rendering-policy count**

In `GraphCanvas.java`, replace:

```java
        final int nodeCount = state == null ? 0 : state.projection().nodes().size();
```

with:

```java
        final int nodeCount = state == null ? 0 : state.projection().projectedNodeCount();
```

- [ ] **Step 5: Update and extend the projection count tests**

In `StructuralProjectionShould.java`, inside `projectGroupMarkedLeavesAsBoundariesBeforePlainLeavesVanish`, replace:

```java
        assertThat(projection.projectedNodeCount()).isZero();
```

with:

```java
        assertThat(projection.projectedNodeCount()).isEqualTo(1);
```

Append this new test to `StructuralProjectionShould`:

```java
    @Test
    public void countGroupMarkedBoundariesAsNodesButNeverMapRootFrames() {
        NodeSnapshot group = node(MAP_ONE, "group", "Group", true, true, false);
        NodeSnapshot plain = node(MAP_ONE, "plain", "Plain", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group, plain);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.projectedNodeCount()).isEqualTo(1);
        assertThat(projection.enclosures().get(1).mapRoot()).isFalse();

        NodeSnapshot rootOnly = node(MAP_ONE, "root", "Root", false, false, false, plain);
        GraphProjection noGroups = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, rootOnly));
        assertThat(noGroups.projectedNodeCount()).isZero();
    }
```

- [ ] **Step 6: Update the map-row count test and add a group-state helper**

In `GraphWorkspaceWindowModelShould.java`, inside `exposesAllMapRowStatesWithProjectedCountsAndEmitsSessionCommandsOnCanvasUpdates`, replace:

```java
        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)));
```

with:

```java
        model.acceptCanvasState(groupState(ACTIVE_ID));
```

(The row-count assertions that follow — `get(0)` equals 1, `get(3)` equals 0 — stay unchanged.)

Add this helper right after the existing `enclosureState(MapReferenceId mapId)` method:

```java
    private static CanvasState groupState(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.transientPath(mapId, Collections.emptyList());
        EnclosureKey endpoint = EnclosureKey.of(source);
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of("Group", "Group")), "Map",
            java.util.Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC);
        LayoutPoint anchor = LayoutPoint.of(0.0, 0.0);
        HullGeometry hull = HullGeometry.of(Arrays.asList(LayoutPoint.of(-30.0, -20.0),
            LayoutPoint.of(30.0, -20.0), LayoutPoint.of(30.0, 20.0), LayoutPoint.of(-30.0, 20.0)), anchor);
        GraphProjection projection = GraphProjection.projected(0L,
            Collections.<ProjectedNode>emptyList(), Collections.singletonList(enclosure),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
            Collections.singletonMap(hullKey, hull), Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(),
            Collections.singletonMap(hullKey, anchor)), false);
        return CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE);
    }
```

All imports this helper needs (`SourceNodeKey`, `EnclosureKey`, `EnclosureHullKey`, `ProjectedEnclosure`, `SafeNodeLabel`, `ProjectedNodeKey`, `ProjectedNode`, `BoundaryTier`, `HullGeometry`, `GraphGeometry`, `NodeGeometry`, `LayoutPoint`, `LayoutPositions`, `LayoutFrame`, `CanvasState`, `OperationalStatus`) are already imported in this test class.

- [ ] **Step 7: Rewrite `stateWithCounts` to build group boundaries**

In `WorkspaceDialogsShould.java`, add these imports (insert after the existing `org.freeplane.plugin.graph.*` imports):

```java
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
```

Replace the whole current `stateWithCounts(long generation, int nodes, int edges, List<RelationshipResolution> resolutions)` method (including its one-argument overload above it, which stays as-is):

```java
    private static CanvasState stateWithCounts(long generation, int nodes, int edges,
            List<RelationshipResolution> resolutions) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        Map<EnclosureHullKey, HullGeometry> hullGeometry = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (int index = 0; index < nodes; index++) {
            MapReferenceId map = index % 2 == 0 ? MAP_ONE : MAP_TWO;
            SourceNodeKey source = SourceNodeKey.transientPath(map,
                Collections.singletonList(Integer.valueOf(index)));
            EnclosureKey endpoint = EnclosureKey.of(source);
            EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
            enclosures.add(ProjectedEnclosure.of(hullKey, Collections.singletonList(endpoint),
                Collections.singletonList(SafeNodeLabel.of("full-" + index, "Node " + index)),
                map.equals(MAP_ONE) ? "Map one" : "Map two",
                java.util.Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(),
                false, BoundaryTier.EMPHATIC));
            LayoutPoint point = LayoutPoint.of(0.0, 0.0);
            hullGeometry.put(hullKey, HullGeometry.of(Arrays.asList(LayoutPoint.of(-30.0, -20.0),
                LayoutPoint.of(30.0, -20.0), LayoutPoint.of(30.0, 20.0), LayoutPoint.of(-30.0, 20.0)), point));
            anchors.put(hullKey, point);
        }
        List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        if (edges > 0 && nodes >= 2) {
            ProjectedEndpointKey first = ProjectedEndpointKey.ofEnclosure(enclosures.get(0).endpointKeys().get(0));
            ProjectedEndpointKey second = ProjectedEndpointKey.ofEnclosure(enclosures.get(1).endpointKeys().get(0));
            EdgeContributor contributor = EdgeContributor.graphRelationship(relationship(3L), first, second);
            ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(first, second),
                Collections.singletonList(contributor));
            for (int index = 0; index < edges; index++) {
                projectedEdges.add(edge);
            }
        }
        GraphProjection projection = GraphProjection.projected(generation,
            Collections.<ProjectedNode>emptyList(), enclosures, projectedEdges, resolutions,
            Collections.emptyList());
        return CanvasState.of(generation, projection, LayoutFrame.of(generation,
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), anchors), false),
            GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hullGeometry,
                Collections.emptyMap()), OperationalStatus.IDLE);
    }
```

The existing assertions that use this helper keep their expected values: `stateWithCounts(2, 1, ...)` yields `projectedNodeCount()` 2 and `projectedEdgeCount()` 1; `stateWithCounts(2000, 1, ...)` yields 2000 (node warning visible); `stateWithCounts(2, 5000, ...)` yields 5000 edges (edge warning visible).

- [ ] **Step 8: Run the affected test classes and confirm they pass**

```bash
cd /data/home/guest/Development/freeplane
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.projection.StructuralProjectionShould" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" --tests "org.freeplane.plugin.graph.window.WorkspaceDialogsShould"
```

Expected: BUILD SUCCESSFUL, all three classes pass.

- [ ] **Step 9: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java
git commit -m "2026-08-28-graph-workspace-click-to-center: Count group-marked boundaries as nodes"
```

## Task 5: Acceptance coverage and full module suite

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:322-330`

**Interfaces:**

- Consumes: `GraphIntent.RevealSourceNode` from Task 1; the `CanvasFixture` of `GraphWorkspaceCommandAcceptanceShould` with `firstEndpoint: ProjectedEndpointKey` and `edge.key()`; the existing single-click + double-click dispatch sequence in `scenario14SupportsPanZoomFitResetSearchHoverSelectOpenAndInspect`.
- Produces: acceptance coverage that a real single click followed by a double click emits `ChangeSelection`, `RevealSourceNode`, `OpenSourceNode`, and `InspectEdge` for the same endpoint.

- [ ] **Step 1: Pin the single-click intent in the acceptance scenario**

In `GraphWorkspaceCommandAcceptanceShould.java`, inside `scenario14SupportsPanZoomFitResetSearchHoverSelectOpenAndInspect`, replace:

```java
            assertThat(listener.intents).contains(new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.OpenSourceNode(fixture.firstEndpoint), new GraphIntent.InspectEdge(fixture.edge.key()));
```

with:

```java
            assertThat(listener.intents).contains(new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.RevealSourceNode(fixture.firstEndpoint),
                new GraphIntent.OpenSourceNode(fixture.firstEndpoint),
                new GraphIntent.InspectEdge(fixture.edge.key()));
```

- [ ] **Step 2: Run the acceptance test**

```bash
cd /data/home/guest/Development/freeplane
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full module suite**

```bash
cd /data/home/guest/Development/freeplane
gradle :freeplane_plugin_graph:test
```

Expected: BUILD SUCCESSFUL — every test in `freeplane_plugin_graph` passes. If a test fails, investigate whether it is a consequence of Tasks 1-4 (all are plugin-only behavioral changes); fix the production code or the test's stale expectation accordingly, and re-run the module suite until green. If the failure looks unrelated to this feature, do not fix it — report it in CONCERNS and finish with `DONE_WITH_CONCERNS`.

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java
git commit -m "2026-08-28-graph-workspace-click-to-center: Pin single-click reveal in acceptance scenario"
```
