# Graph Live Refresh on Node-Level Map Changes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make node-level map edits on a leased, active map refresh the Graph Workspace live
while the workspace is open, by adding one controller-level `INodeChangeListener` subscription
to `MapLeaseManager` and removing it on close.

**Architecture:** The production change is confined to
`org.freeplane.plugin.graph.adapter.MapLeaseManager`: one private inner `NodeChangeListener`,
one EDT-side identity lookup (`handleNodeChangedOnEdt`) that reuses the existing
`handleMapChanged` publication path, and the registration/removal lines in the constructor and
`close()`. Task 1 lands that change with its unit contract tests; Task 2 pins the end-to-end
behavior with headless `FreeplaneScope` integration tests and extends the resource baseline to
count node listeners; Task 3 runs the full module gate and proves the live test is falsifiable.
One lane (`graph-live-node-refresh`) is used because every task touches the same files or
depends on the previous task's committed state; there is no independent parallel work.

**Tech Stack:** Java 8 language level on runtime Java 21.0.8-zulu, Gradle multi-project build
(module `freeplane_plugin_graph`), JUnit 4.13.2 with AssertJ and Mockito, headless Freeplane
`FreeplaneScope` integration support.

**Implementation contract:** `docs/superpowers/specs/2026-10-11-graph-live-node-edit-refresh-spec.md`
(the contract implemented here; every value a task needs is restated in the task, so no task has
to open it), derived from the approved design
`docs/superpowers/specs/2026-10-11-graph-live-node-edit-refresh-design.md`.

## Global Constraints

- Java 8 source/target. Production excerpts use Java 8 syntax and mirror the anonymous-class
  style of `MapLeaseManager.java`; test code may use the lambdas already present in the plugin
  test sources.
- Toolchain is Zulu 21 at `~/.sdkman/candidates/java/21.0.8-zulu`: before every Gradle command
  run `export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` and
  `export PATH="$JAVA_HOME/bin:$PATH"`.
- Use `gradle` (never `gradlew`, never Maven) from the worktree root. The module test command is
  `gradle :freeplane_plugin_graph:test`; add `-PTestLoggingFull` whenever a run must show verbose
  failures.
- Plugin-only change with no Freeplane core edits: no file under `freeplane/` or
  `freeplane_api/` may be modified; the only production file touched is
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`.
- No new dependencies; no settings, build-script, XML-schema, workspace-format, or persistence
  changes.
- Sources are UTF-8; indentation is 4 spaces. No translation resource is touched, so
  `gradle format_translation` does not apply.
- Every commit message ends with the commit identifier
  `[2026-10-11-graph-live-node-edit-refresh]`.
- Lock discipline: the new code may acquire only `MapLeaseManager.monitor`; it never acquires
  `settlementLock` and never calls out while holding `monitor`.
- Exactly one controller-level `INodeChangeListener` registration per `MapLeaseManager` instance;
  no per-model node-listener registration is added.

## Task 1: Publish adapter events for node changes on leased maps

**Implementer tier:** Standard
**Lane:** graph-live-node-refresh

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java:30-36` (imports), `:95-103` (fields), `:233-242` (constructor registration), `:439-458` (close flag declaration and capture), `:496-508` (close EDT runnable), `:979-988` (insert `handleNodeChangedOnEdt` after `callbackRunnable`), `:1448-1489` (insert `NodeChangeListener` after `LifecycleListener`)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java:48-56` (imports), `:81-88` (insert the three tests after `closeManagers()`), `:2648-2725` (`TestEnvironment`)

**Interfaces:**

- Consumes: `MapController.addNodeChangeListener(INodeChangeListener)` and
  `MapController.removeNodeChangeListener(INodeChangeListener)`
  (`freeplane/src/main/java/org/freeplane/features/map/MapController.java:620-623` and `:1164-1166`);
  `INodeChangeListener.nodeChanged(NodeChangeEvent)`
  (`freeplane/src/main/java/org/freeplane/features/map/INodeChangeListener.java:20-22`);
  `NodeChangeEvent.getNode(): NodeModel`;
  `MapLeaseManager.handleMapChanged(Entry, MapModel, long)` (current `:949-977`), which owns the
  full revalidation (closed, entry identity, generation, model identity, `leaseCount > 0`,
  `!loading`) and `publish`; the existing `EdtExecutor edt`, `Object monitor`, and
  `LifecycleListener` EDT-dispatch pattern.
- Produces: `private final INodeChangeListener nodeChangeListener = new NodeChangeListener();`
  and `private boolean nodeListenerRegistered;` (guarded by `monitor`) on `MapLeaseManager`;
  `private final class NodeChangeListener implements INodeChangeListener` with
  `public void nodeChanged(NodeChangeEvent event)` that ignores `null` events and `null` nodes,
  dispatches to the EDT with `handleNodeChangedOnEdt(node.getMap())`, and catches
  `RuntimeException` around its whole body;
  `private void handleNodeChangedOnEdt(MapModel map)` that scans `entries` under `monitor` for
  `entry.model == map && entry.leaseCount > 0 && !entry.loading`, captures `entry.generation`,
  and calls `handleMapChanged(match, map, generation)`; `close()` removes the node listener in
  the same EDT runnable as the lifecycle listener, exactly once.

- [ ] **Step 1: Write the failing unit tests and the TestEnvironment capture**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`,
add these two imports. `INodeChangeListener` goes immediately after
`import org.freeplane.features.map.IMapLifeCycleListener;` and `NodeChangeEvent` goes between
`import org.freeplane.features.map.MapModel;` and `import org.freeplane.features.map.NodeModel;`:

```java
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.NodeChangeEvent;
```

Insert these three tests immediately after the closing brace of `closeManagers()` (current line
86) and before the `@Test` of `rebasesRelativeMapResolutionAfterTheWorkspaceMoves`:

```java
    @Test
    public void publishesAdapterEventForNodeChangesOnLeasedModels() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        int acquisitionEvents = events.size();
        assertThat(acquisitionEvents).isEqualTo(2);

        environment.fireNodeChange(environment.model.getRootNode());
        edt.runAll();

        assertThat(events).hasSize(acquisitionEvents + 1);
        assertThat(events.get(acquisitionEvents).mapReferenceId()).isEqualTo(environment.reference().id());
        assertThat(events.get(acquisitionEvents).state()).isEqualTo(MapOperationalState.AVAILABLE);
        lease.close();
    }

    @Test
    public void ignoresNodeChangesForUnleasedAndInactiveModels() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);

        FakeMapModel neverLeased = environment.newMap(
            temporaryFolder.newFile("unleased.mm").toPath().toUri().toURL());
        environment.fireNodeChange(neverLeased.getRootNode());
        edt.runAll();
        assertThat(events).isEmpty();

        MapLease lease = acquire(manager, environment, edt);
        lease.close();
        int afterClose = events.size();
        environment.fireNodeChange(environment.model.getRootNode());
        edt.runAll();
        assertThat(events).hasSize(afterClose);
    }

    @Test
    public void removesNodeChangeListenerOnClose() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        assertThat(environment.nodeChangeListeners).hasSize(1);

        manager.close();

        assertThat(environment.nodeChangeListeners).isEmpty();
    }
```

In the nested `TestEnvironment` class, add the capture field immediately after the existing
`private final List<IMapLifeCycleListener> lifecycleListeners = ...;` field (current line 2656):

```java
        private final List<INodeChangeListener> nodeChangeListeners = new ArrayList<INodeChangeListener>();
```

Add the Mockito capture in the constructor immediately after the existing
`removeMapLifeCycleListener` stub (current line 2679):

```java
            doAnswer(invocation -> {
                nodeChangeListeners.add(invocation.getArgument(0));
                return null;
            }).when(mapController).addNodeChangeListener(any(INodeChangeListener.class));
            doAnswer(invocation -> {
                nodeChangeListeners.remove(invocation.getArgument(0));
                return null;
            }).when(mapController).removeNodeChangeListener(any(INodeChangeListener.class));
```

Add the fire helper immediately after the existing `notifyRemoved(MapModel map)` method (current
line 2723), inside `TestEnvironment`:

```java
        private void fireNodeChange(final NodeModel node) {
            final NodeChangeEvent event =
                new NodeChangeEvent(node, NodeModel.NODE_TEXT, "before", "after", true, true);
            for (INodeChangeListener listener : new ArrayList<INodeChangeListener>(nodeChangeListeners)) {
                listener.nodeChanged(event);
            }
        }
```

- [ ] **Step 2: Run the registration-dependent tests and confirm they fail**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.adapter.MapLeaseManagerShould.publishesAdapterEventForNodeChangesOnLeasedModels" \
  --tests "org.freeplane.plugin.graph.adapter.MapLeaseManagerShould.removesNodeChangeListenerOnClose" \
  -PTestLoggingFull
```

Expected: FAIL, both tests, no compilation error.
`publishesAdapterEventForNodeChangesOnLeasedModels` fails at `assertThat(events).hasSize(acquisitionEvents + 1)`
with `expected size:<3> but was:<2>` because no listener is registered and
`fireNodeChange` iterates an empty capture list. `removesNodeChangeListenerOnClose` fails at
`assertThat(environment.nodeChangeListeners).hasSize(1)` with `expected size:<1> but was:<0>`.
`ignoresNodeChangesForUnleasedAndInactiveModels` is the negative control: it passes vacuously
here (nothing publishes when nothing is registered) and must stay green after the implementation.

- [ ] **Step 3: Write the minimal production implementation**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`,
make exactly these six edits. The class body already contains all consumed names; do not rename
or move anything else.

(a) Imports: add `INodeChangeListener` after `import org.freeplane.features.map.IMapLifeCycleListener;`
and `NodeChangeEvent` between `import org.freeplane.features.map.MapModel;` and
`import org.freeplane.features.map.NodeModel;`:

```java
import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
```

(b) Fields: add `nodeChangeListener` immediately after `lifecycleListener` (current line 96) and
`nodeListenerRegistered` immediately after `lifecycleListenerRegistered` (current line 100):

```java
    private final IMapLifeCycleListener lifecycleListener = new LifecycleListener();
    private final INodeChangeListener nodeChangeListener = new NodeChangeListener();
    private ScheduledFuture<?> externalCheck;
    private DeferredDrainRetry deferredDrainRetry;
    private ScheduledFuture<?> deferredDrainRetryFuture;
    private boolean lifecycleListenerRegistered;
    private boolean nodeListenerRegistered;
    private boolean deferredDrainScheduled;
```

(c) Constructor: replace the `runOnEdtAndWait` runnable body so both listeners register on the
EDT and both flags are set under `monitor` (current lines 234-241):

```java
            runOnEdtAndWait(new Runnable() {
                @Override
                public void run() {
                    mapController.addMapLifeCycleListener(lifecycleListener);
                    mapController.addNodeChangeListener(nodeChangeListener);
                    synchronized (monitor) {
                        lifecycleListenerRegistered = true;
                        nodeListenerRegistered = true;
                    }
                }
            });
```

(d) `close()`: declare the second capture flag next to `removeLifecycle` (current line 444):

```java
        boolean removeLifecycle;
        boolean removeNodeListener;
```

Capture and clear both flags inside the same `synchronized (monitor)` block, after the lifecycle
lines (current lines 457-458):

```java
                removeLifecycle = lifecycleListenerRegistered;
                lifecycleListenerRegistered = false;
                removeNodeListener = nodeListenerRegistered;
                nodeListenerRegistered = false;
```

Extend the outer EDT-runnable condition and remove the node listener inside it (current lines
496-508):

```java
            if (removeLifecycle || removeNodeListener || !detachments.isEmpty()) {
                runOnEdtAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (removeLifecycle) {
                            mapController.removeMapLifeCycleListener(lifecycleListener);
                        }
                        if (removeNodeListener) {
                            mapController.removeNodeChangeListener(nodeChangeListener);
                        }
                        for (Detachment detachment : detachments) {
                            detachOnEdt(detachment);
                        }
                    }
                });
            }
```

(e) Insert `handleNodeChangedOnEdt` immediately after `callbackRunnable` (which ends at current
line 986) and before `loadOnEdt` (current line 988):

```java
    private void handleNodeChangedOnEdt(final MapModel map) {
        if (map == null) {
            return;
        }
        Entry match = null;
        long generation = 0L;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            for (Entry entry : entries.values()) {
                if (entry.model == map && entry.leaseCount > 0 && !entry.loading) {
                    match = entry;
                    generation = entry.generation;
                    break;
                }
            }
        }
        if (match != null) {
            handleMapChanged(match, map, generation);
        }
    }
```

(f) Insert the inner class immediately after `LifecycleListener` (which ends at current line
1488) and before `handleMapCreatedOnEdt`:

```java
    private final class NodeChangeListener implements INodeChangeListener {
        @Override
        public void nodeChanged(final NodeChangeEvent event) {
            try {
                if (event == null) {
                    return;
                }
                final NodeModel node = event.getNode();
                if (node == null) {
                    return;
                }
                if (edt.isEdt()) {
                    handleNodeChangedOnEdt(node.getMap());
                }
                else {
                    try {
                        edt.execute(new Runnable() {
                            @Override
                            public void run() {
                                handleNodeChangedOnEdt(node.getMap());
                            }
                        });
                    }
                    catch (RuntimeException ignored) {
                        // Manager close invalidates node change callbacks.
                    }
                }
            }
            catch (RuntimeException ignored) {
                // Node change processing must never fail an editor operation.
            }
        }
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.adapter.MapLeaseManagerShould.publishesAdapterEventForNodeChangesOnLeasedModels" \
  --tests "org.freeplane.plugin.graph.adapter.MapLeaseManagerShould.ignoresNodeChangesForUnleasedAndInactiveModels" \
  --tests "org.freeplane.plugin.graph.adapter.MapLeaseManagerShould.removesNodeChangeListenerOnClose" \
  -PTestLoggingFull
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.adapter.MapLeaseManagerShould" \
  -PTestLoggingFull
```

Expected: the three named tests pass and the whole `MapLeaseManagerShould` class passes. If the
class run surfaces a regression caused by the new constructor registration or the new `close()`
branch, fix it inside this task before committing.

- [ ] **Step 5: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java
git commit -m "fix(graph): publish adapter events for node changes on leased maps [2026-10-11-graph-live-node-edit-refresh]"
```

## Task 2: Pin live refresh with headless integration tests

**Implementer tier:** Advanced
**Lane:** graph-live-node-refresh

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLiveNodeRefreshShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:658-666` (insert `hasNodeLabel`, `nodeKeyByLabel`, `awaitIdle` after `awaitCondition`), `:694-697` (`baseline`), `:699-712` (`awaitBaseline`), `:885-919` (`ResourceBaseline`), `:1190-1192` (insert `mapNodeChangeListenerCount` after `mapLifecycleListenerCount`)

**Interfaces:**

- Consumes: the committed subscription and close behavior of Task 1 in `MapLeaseManager`;
  `GraphWorkspaceIntegrationSupport.FreeplaneScope.installGraphGroups()`,
  `loadWithView(Path): MapModel`, `drainAsyncQueues()`, `modeController(): ModeController`,
  and `RecordingViewFactory.latestBinding(): GraphWorkspaceViewBinding`;
  `GraphWorkspaceHandle.currentProjection(): GraphProjection`, `execute(GraphCommand): GraphCommandResult`,
  and `close()`; `DefaultGraphWorkspaceController(ModeController, GraphWorkspaceViewFactory)` with
  `open(Path): GraphWorkspaceHandle` and `shutdown()`;
  `GraphCommands.addMap(MapReferenceId, java.net.URI): GraphCommand`;
  `MapReferenceId.of(UUID): MapReferenceId`; `CanvasState.generation()`, `.projection()`,
  `.layout()`, `.status()`; `LayoutFrame.positions()` returning `LayoutPositions` whose
  `nodes()` is `Map<ProjectedNodeKey, LayoutPoint>`; `ProjectedNodeKey` identity equality;
  `ProjectedNode.label()` with `displayText()`/`fullText()`; `IUndoHandler.undo()`;
  `IActor.act()`, `.undo()`, `.getDescription()`.
- Produces: `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection()`,
  `.nodeTextEditPreservesUnaffectedNodePositions()`,
  `.nodeTextEditRefreshesWhenADistinctEditorModelAppears()`, and
  `.undoOfNodeTextEditRefreshesTheLiveProjection()`;
  `GraphWorkspaceIntegrationSupport.hasNodeLabel(GraphProjection, String): boolean`,
  `.nodeKeyByLabel(GraphProjection, String): ProjectedNodeKey` (throwing
  `AssertionError("Missing projected node with label " + label)` when absent), and
  `.awaitIdle(GraphWorkspaceViewBinding, long): void`;
  `FreeplaneScope.mapNodeChangeListenerCount(): int`; the `ResourceBaseline.nodeChangeListeners`
  field asserted by `awaitBaseline`.

- [ ] **Step 1: Write the failing integration test class**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLiveNodeRefreshShould.java`
with exactly this content:

```java
package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphWorkspaceLiveNodeRefreshShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String LIVE_REFRESH_FIXTURE = "<map version=\"freeplane 1.12.15\">\n"
        + "  <node TEXT=\"live refresh root\" ID=\"ID_ROOT\">\n"
        + "    <node TEXT=\"alpha\" ID=\"ID_ALPHA\">\n"
        + "      <graph_group version=\"1\"/>\n"
        + "    </node>\n"
        + "    <node TEXT=\"bravo\" ID=\"ID_BRAVO\">\n"
        + "      <graph_group version=\"1\"/>\n"
        + "    </node>\n"
        + "  </node>\n"
        + "</map>\n";

    @Test
    public void nodeTextEditRefreshesTheLiveProjection() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("live-refresh.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            freeplane.installGraphGroups();
            final MapModel sourceMap = freeplane.loadWithView(sourceMapFile);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("live-refresh.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "initial live label did not appear");

            final NodeModel alpha = sourceMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);

            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "edited label did not reach the live projection");
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void nodeTextEditPreservesUnaffectedNodePositions() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-positions.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            freeplane.installGraphGroups();
            final MapModel sourceMap = freeplane.loadWithView(sourceMapFile);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-positions.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha")
                    && GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "bravo"),
                15000L, "initial live labels did not appear");

            GraphWorkspaceIntegrationSupport.awaitIdle(views.latestBinding(), 15000L);
            final CanvasState baseline = views.latestBinding().currentCanvasState();
            final long baselineGeneration = baseline.generation();
            final ProjectedNodeKey alphaKey =
                GraphWorkspaceIntegrationSupport.nodeKeyByLabel(baseline.projection(), "alpha");
            final ProjectedNodeKey bravoKey =
                GraphWorkspaceIntegrationSupport.nodeKeyByLabel(baseline.projection(), "bravo");
            final LayoutPoint alphaBefore = baseline.layout().positions().nodes().get(alphaKey);
            final LayoutPoint bravoBefore = baseline.layout().positions().nodes().get(bravoKey);
            assertThat(alphaBefore).isNotNull();
            assertThat(bravoBefore).isNotNull();

            final NodeModel alpha = sourceMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);

            GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
                    final CanvasState state = views.latestBinding().currentCanvasState();
                    return state.generation() > baselineGeneration
                        && GraphWorkspaceIntegrationSupport.hasNodeLabel(state.projection(), "omega")
                        && !GraphWorkspaceIntegrationSupport.hasNodeLabel(state.projection(), "alpha");
                }, 15000L, "same-length text edit did not reach the live canvas state");
            GraphWorkspaceIntegrationSupport.awaitIdle(views.latestBinding(), 15000L);

            final CanvasState edited = views.latestBinding().currentCanvasState();
            assertThat(edited.layout().positions().nodes().get(alphaKey)).isEqualTo(alphaBefore);
            assertThat(edited.layout().positions().nodes().get(bravoKey)).isEqualTo(bravoBefore);
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void nodeTextEditRefreshesWhenADistinctEditorModelAppears() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path realDirectory = temporaryFolder.newFolder("real").toPath();
            final Path sourceMapFile = realDirectory.resolve("symlinked.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            final Path aliasDirectory = temporaryFolder.newFolder("alias").toPath();
            final Path aliasMapFile = aliasDirectory.resolve("symlinked.mm");
            Files.createSymbolicLink(aliasMapFile, sourceMapFile);
            freeplane.installGraphGroups();
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("symlinked.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            // The stored URI uses the alias path; the lease canonicalizes it to the real path.
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, aliasMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "initial live label did not appear");

            final MapModel editorMap = freeplane.loadWithView(aliasMapFile);
            freeplane.drainAsyncQueues();
            final NodeModel alpha = editorMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), editorMap);

            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "edited label did not reach the live projection after the editor model appeared");
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void undoOfNodeTextEditRefreshesTheLiveProjection() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-undo.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            freeplane.installGraphGroups();
            final MapModel sourceMap = freeplane.loadWithView(sourceMapFile);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-undo.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "initial live label did not appear");

            final NodeModel alpha = sourceMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "edited label did not reach the live projection");

            sourceMap.getExtension(IUndoHandler.class).undo();

            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega"),
                15000L, "undone label did not reach the live projection");
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    private static IActor textEdit(final org.freeplane.features.mode.ModeController modeController,
            final NodeModel node, final String oldText, final String newText) {
        return new IActor() {
            @Override
            public void act() {
                node.setText(newText);
                modeController.getMapController().nodeChanged(node, NodeModel.NODE_TEXT, oldText, newText);
            }

            @Override
            public String getDescription() {
                return "graph-live-refresh-text-edit";
            }

            @Override
            public void undo() {
                node.setText(oldText);
                modeController.getMapController().nodeChanged(node, NodeModel.NODE_TEXT, newText, oldText);
            }
        };
    }
}
```

- [ ] **Step 2: Run the new test class and confirm the compilation failure**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould" \
  -PTestLoggingFull
```

Expected: FAIL at `compileTestJava` with `cannot find symbol` for
`GraphWorkspaceIntegrationSupport.hasNodeLabel(...)`, `GraphWorkspaceIntegrationSupport.nodeKeyByLabel(...)`,
and `GraphWorkspaceIntegrationSupport.awaitIdle(...)`. The test class is the failing test; the
support helpers written in Step 3 make it compile and run. The behavioural red for the mechanism
itself (the live test failing without the Task 1 subscription) is executed and recorded in Task 3.

- [ ] **Step 3: Write the minimal implementation: the GraphWorkspaceIntegrationSupport additions**

All edits are inside `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`.

(a) Insert the three helpers immediately after `awaitCondition` (which ends at current line 665)
and before `deleteRecursively`:

```java
    static boolean hasNodeLabel(final GraphProjection projection, final String label) {
        for (final ProjectedNode node : projection.nodes()) {
            if (label.equals(node.label().displayText()) || label.equals(node.label().fullText())) {
                return true;
            }
        }
        return false;
    }

    static ProjectedNodeKey nodeKeyByLabel(final GraphProjection projection, final String label) {
        for (final ProjectedNode node : projection.nodes()) {
            if (label.equals(node.label().displayText()) || label.equals(node.label().fullText())) {
                return node.key();
            }
        }
        throw new AssertionError("Missing projected node with label " + label);
    }

    static void awaitIdle(final GraphWorkspaceViewBinding binding, final long timeoutMillis)
            throws Exception {
        awaitCondition(() -> binding.currentCanvasState().status() == OperationalStatus.IDLE,
            timeoutMillis, "graph workspace did not reach OperationalStatus.IDLE");
    }
```

(b) Replace `baseline` (current lines 694-697) so it passes the new count:

```java
    static ResourceBaseline baseline(final FreeplaneScope freeplane, final Path root) throws Exception {
        return new ResourceBaseline(freeplane.mapLifecycleListenerCount(), freeplane.mapChangeListenerCount(),
            freeplane.mapNodeChangeListenerCount(), freeplane.viewCount(), freeplane.timerCount(),
            graphThreadProfile(), temporaryArtifacts(root));
    }
```

(c) Add the node-listener assertion to `awaitBaseline` immediately after the `mapChangeListeners`
assertion (current line 705):

```java
        assertThat(freeplane.mapNodeChangeListenerCount()).isEqualTo(expected.nodeChangeListeners);
```

(d) Replace `ResourceBaseline` (current lines 885-919) with:

```java
    static final class ResourceBaseline {
        final int mapLifecycleListeners;
        private final int mapChangeListeners;
        private final int nodeChangeListeners;
        private final int views;
        private final int timers;
        private final Map<String, Integer> threads;
        private final Set<String> temporaryArtifacts;

        private ResourceBaseline(final int mapLifecycleListeners, final int mapChangeListeners,
                final int nodeChangeListeners, final int views, final int timers,
                final Map<String, Integer> threads, final Set<String> temporaryArtifacts) {
            this.mapLifecycleListeners = mapLifecycleListeners;
            this.mapChangeListeners = mapChangeListeners;
            this.nodeChangeListeners = nodeChangeListeners;
            this.views = views;
            this.timers = timers;
            this.threads = threads;
            this.temporaryArtifacts = temporaryArtifacts;
        }

        private boolean matches(final FreeplaneScope freeplane, final Path root) {
            try {
                return mapLifecycleListeners == freeplane.mapLifecycleListenerCount()
                    && mapChangeListeners == freeplane.mapChangeListenerCount()
                    && nodeChangeListeners == freeplane.mapNodeChangeListenerCount()
                    && views == freeplane.viewCount() && timers == freeplane.timerCount()
                    && threads.equals(graphThreadProfile())
                    && temporaryArtifacts.equals(temporaryArtifacts(root));
            }
            catch (Exception failure) {
                return false;
            }
        }
    }
```

(e) Insert `mapNodeChangeListenerCount` inside `FreeplaneScope` immediately after
`mapLifecycleListenerCount` (current lines 1190-1192):

```java
        int mapNodeChangeListenerCount() {
            return mapController.getNodeChangeListeners().size();
        }
```

- [ ] **Step 4: Run the integration tests and confirm they pass**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould" \
  -PTestLoggingFull
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLifecycleShould" \
  -PTestLoggingFull
```

Expected: all four `GraphWorkspaceLiveNodeRefreshShould` tests pass, and the resource-baseline
lifecycle suite stays green with the new `nodeChangeListeners` assertion (proving the Task 1
`close()` removal returns the listener count to baseline). If
`nodeTextEditPreservesUnaffectedNodePositions` fails, report `BLOCKED` with the observed
positions and do not relax the equality assertion: a moved position is a finding per the spec.

- [ ] **Step 5: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLiveNodeRefreshShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java
git commit -m "test(graph): pin live node-edit refresh end to end [2026-10-11-graph-live-node-edit-refresh]"
```

## Task 3: Run the module gate and prove the live refresh test is falsifiable

**Implementer tier:** Standard
**Lane:** graph-live-node-refresh

**Files:**

- Modify (transient falsifiability probe only; restored byte-identical before any commit): `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java:233-242` (the `mapController.addNodeChangeListener(nodeChangeListener);` registration line)
- Create: `docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh-evidence.md`

**Interfaces:**

- Consumes: the committed subscription line
  `mapController.addNodeChangeListener(nodeChangeListener);` inside the constructor's
  `runOnEdtAndWait` runnable from Task 1;
  `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection()` from Task 2; the
  module test task `:freeplane_plugin_graph:test`.
- Produces: `docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh-evidence.md` recording
  the observed gate, probe, and core-gate results; the committed source tree is unchanged by the
  probe.

- [ ] **Step 1: Run the full module gate on the committed state**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; no test failure. This is AC5 and the regression gate for AC3
(structural edits, marker toggles, and style/icon changes keep their existing coverage).

- [ ] **Step 2: Confirm the core gate and a clean tree before probing**

```bash
cd "$(git rev-parse --show-toplevel)"
git status --porcelain
git diff --stat
git log --oneline -2
```

Expected: `git status --porcelain` and `git diff --stat` print nothing; the top commit is the
Task 2 commit. Then confirm that no commit carrying this plan's identifier touches a core path,
regardless of the lane's base revision (design, spec, and plan commits also carry the identifier
and are expected to appear in the listing):

```bash
cd "$(git rev-parse --show-toplevel)"
git log --format='%H' --grep='\[2026-10-11-graph-live-node-edit-refresh\]' \
  | xargs -r git show --name-only --format= \
  | sort -u
```

Expected: the listing contains the four source/test paths
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`,
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`,
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`,
and
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLiveNodeRefreshShould.java`,
plus any `docs/superpowers/` design/spec/plan paths. Then assert no core path is present:

```bash
cd "$(git rev-parse --show-toplevel)"
git log --format='%H' --grep='\[2026-10-11-graph-live-node-edit-refresh\]' \
  | xargs -r git show --name-only --format= \
  | grep -E '^(freeplane|freeplane_api)/' || echo "core gate clean"
```

Expected: `core gate clean`. If any path starting with `freeplane/` or `freeplane_api/` is
listed, stop and report `BLOCKED`: the plugin-only constraint is violated.

- [ ] **Step 3: Introduce the falsifiability probe**

Replace the registration line with a comment naming the probe:

```bash
cd "$(git rev-parse --show-toplevel)"
sed -i 's|^\([[:space:]]*\)mapController\.addNodeChangeListener(nodeChangeListener);$|\1// FALSIFIABILITY PROBE: mapController.addNodeChangeListener(nodeChangeListener);|' \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java
git diff -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java
```

Expected: `git diff` shows exactly one changed line, the comment
`// FALSIFIABILITY PROBE: mapController.addNodeChangeListener(nodeChangeListener);`.
`git diff --stat` shows `1 file changed, 1 insertion(+), 1 deletion(-)`.

- [ ] **Step 4: Run the named live test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection" \
  -PTestLoggingFull
```

Expected: FAIL. The test fails at the final await with the AssertJ description
`edited label did not reach the live projection` after the 15000 ms timeout. This proves the test
pins the subscription mechanism rather than some other refresh path. If the test passes or fails
with a different message, stop and report `BLOCKED` with the observed output.

- [ ] **Step 5: Restore the registration line and confirm no residual diff**

```bash
cd "$(git rev-parse --show-toplevel)"
git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java
git status --porcelain
git diff
```

Expected: `git status --porcelain` prints nothing and `git diff` prints nothing. The worktree has
no residual diff from the probe.

- [ ] **Step 6: Re-run the named live test to green**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the restored subscription makes the same test pass.

- [ ] **Step 7: Write the evidence note and commit it**

Write the note only if Steps 1-6 produced exactly the expected results above; otherwise report
`BLOCKED` with the observed output instead of writing it.

```bash
cd "$(git rev-parse --show-toplevel)"
REV=$(git rev-parse HEAD)
cat > docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh-evidence.md <<EOF
# Graph Live Node Edit Refresh Verification Evidence

- Date: 2026-10-11
- Topic slug: graph-live-node-edit-refresh
- Source revision: $REV
- Plan: docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh.md
- Contract: docs/superpowers/specs/2026-10-11-graph-live-node-edit-refresh-spec.md

**Full module gate**

gradle :freeplane_plugin_graph:test -PTestLoggingFull: BUILD SUCCESSFUL.

**Falsifiability probe**

With mapController.addNodeChangeListener(nodeChangeListener) replaced by a
"// FALSIFIABILITY PROBE:" comment in the MapLeaseManager constructor, the command
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection" -PTestLoggingFull
fails at the awaiting assertion with the message "edited label did not reach the live projection".
The registration line was restored with git checkout -- and both git status --porcelain and
git diff were empty; the same test then passed.

**Core gate**

Every commit carrying the plan identifier touches only freeplane_plugin_graph test and adapter
paths; no path under freeplane/ or freeplane_api/ was modified.
EOF
cat docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh-evidence.md
git add docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh-evidence.md
git commit -m "docs: record live node-edit refresh verification evidence [2026-10-11-graph-live-node-edit-refresh]"
git status --porcelain
```

Expected: the note prints with the Task 2 commit sha in the `Source revision` line; the commit
contains exactly the evidence file; the final `git status --porcelain` prints nothing.
