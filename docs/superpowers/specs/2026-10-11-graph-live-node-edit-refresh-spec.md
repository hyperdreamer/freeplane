# Graph Live Refresh on Node-Level Map Changes — Technical Specification

- Date: 2026-10-11
- Topic slug: `graph-live-node-edit-refresh`
- Approved design (immutable input): `docs/superpowers/specs/2026-10-11-graph-live-node-edit-refresh-design.md`
- Module: `freeplane_plugin_graph` only
- Code baseline: this worktree at commit `146b013b43`; every `file:line` reference below is to that commit
- Status: implementation-ready

## 1. Scope, Non-Goals, and Constraints

### 1.1 Scope

1. Add one controller-level `INodeChangeListener` subscription to
   `org.freeplane.plugin.graph.adapter.MapLeaseManager` so that node-level map changes
   (`MapController.nodeChanged` / `nodeRefresh`) on leased, active maps publish a
   `MapAdapterEvent` and therefore trigger the existing coalesced projection rebuild.
2. Remove that subscription in `MapLeaseManager.close()` together with the existing
   lifecycle listener removal, in the same EDT runnable.
3. Reuse the existing publication, generation, debounce, snapshot, projection-diff, and
   layout machinery without change.
4. Add unit and headless integration tests that pin: live refresh on text edits, position
   preservation for unaffected nodes, refresh when a distinct editor model appears, undo
   refresh, suppression for unleased/inactive entries, and listener removal on close.

### 1.2 Non-Goals

- Any change to Freeplane core (`freeplane/`, `freeplane_api/`, `MapController`, `MapModel`,
  `NodeModel` notification semantics).
- Property-level filtering of node-change events; every accepted node change schedules one
  coalesced rebuild.
- New UI, commands, persistence, or workspace-format changes.
- Changes to projection, snapshot, layout, canvas, or hit-testing behavior.
- New per-model node-listener registration; the new subscription is one
  controller-level registration per `MapLeaseManager` instance (the same shape as the
  existing `IMapLifeCycleListener` subscription).

### 1.3 Constraints

- Java 8 source/target; production excerpts use Java 8 syntax and mirror the anonymous-class
  style of `MapLeaseManager.java`. Test code may use the lambdas already present in the
  plugin test sources.
- No core-module source change is permitted. The production diff is limited to
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`.
- Lock discipline: the new code may acquire only `MapLeaseManager.monitor` and must not
  acquire `settlementLock` or call out while holding `monitor`. The existing global lock
  order (settlement lock, then monitor) is unchanged.
- All new behavior must be exercised by tests named in Section 6; no requirement in this
  document is satisfied by inspection alone.

### 1.4 File Inventory

| Action | File |
| --- | --- |
| Modify (production) | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java` |
| Modify (unit tests) | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java` |
| Add (integration tests) | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLiveNodeRefreshShould.java` |
| Modify (integration support) | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java` |

`GraphWorkspaceIntegrationSupport` is not a standalone file. It is the package-private class
declared inside `GraphWorkspaceColdReloadShould.java` at line 605. Additions described in
Section 6.4 therefore edit `GraphWorkspaceColdReloadShould.java`.

## 2. Production Changes in `MapLeaseManager.java`

### 2.0 Current code sites being modified

All line ranges are from `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`
at commit `146b013b43`.

**(a) Fields, lines 95–103:**

```java
    private final List<DeferredOperation> deferredOperations = new ArrayList<DeferredOperation>();
    private final IMapLifeCycleListener lifecycleListener = new LifecycleListener();
    private ScheduledFuture<?> externalCheck;
    private DeferredDrainRetry deferredDrainRetry;
    private ScheduledFuture<?> deferredDrainRetryFuture;
    private boolean lifecycleListenerRegistered;
    private boolean deferredDrainScheduled;
    private boolean deferredDrainRunning;
    private boolean closed;
```

**(b) Constructor registration, lines 233–242:**

```java
        try {
            runOnEdtAndWait(new Runnable() {
                @Override
                public void run() {
                    mapController.addMapLifeCycleListener(lifecycleListener);
                    synchronized (monitor) {
                        lifecycleListenerRegistered = true;
                    }
                }
            });
```

**(c) `close()` declaration and flag capture, lines 439–458:**

```java
    public void close() {
        final List<Detachment> detachments = new ArrayList<Detachment>();
        final List<PendingAcquire> pending = new ArrayList<PendingAcquire>();
        ScheduledFuture<?> scheduledCheck;
        ScheduledFuture<?> scheduledDeferredDrainRetry;
        boolean removeLifecycle;
        settlementLock.lock();
        try {
            synchronized (monitor) {
                if (closed) {
                    return;
                }
                closed = true;
                scheduledCheck = externalCheck;
                externalCheck = null;
                scheduledDeferredDrainRetry = deferredDrainRetryFuture;
                deferredDrainRetryFuture = null;
                deferredDrainRetry = null;
                removeLifecycle = lifecycleListenerRegistered;
                lifecycleListenerRegistered = false;
```

**(d) `close()` EDT runnable, lines 496–508:**

```java
            if (removeLifecycle || !detachments.isEmpty()) {
                runOnEdtAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (removeLifecycle) {
                            mapController.removeMapLifeCycleListener(lifecycleListener);
                        }
                        for (Detachment detachment : detachments) {
                            detachOnEdt(detachment);
                        }
                    }
                });
            }
```

**(e) Reference sites that stay unchanged and define the contracts reused by the new
listener:**

- `handleMapChanged`, lines 949–977 — the single publication path with full revalidation:

```java
    private void handleMapChanged(final Entry entry, final MapModel map, final long generation) {
        final Runnable callback = new Runnable() {
            @Override
            public void run() {
                if (!edt.isEdt()) {
                    edt.execute(callbackRunnable(entry, map, generation));
                    return;
                }
                final MapAdapterEvent event;
                synchronized (monitor) {
                    if (closed || entries.get(entry.id) != entry || entry.generation != generation
                            || entry.model != map || entry.leaseCount == 0 || entry.loading) {
                        return;
                    }
                    final MapOperationalState nextState = entry.state == MapOperationalState.RELOAD_REQUIRED
                        ? MapOperationalState.RELOAD_REQUIRED : stateForModel(map);
                    entry.state = nextState;
                    event = new MapAdapterEvent(entry.id, nextState);
                }
                publish(event);
            }
        };
        if (edt.isEdt()) {
            callback.run();
        }
        else {
            edt.execute(callback);
        }
    }
```

- `callbackRunnable`, lines 979–986, and `LifecycleListener`, lines 1448–1488 (the EDT
  dispatch pattern the new listener mirrors).
- `publish`, lines 1331–1352 — checks `closed` before snapshot and before each listener,
  and swallows each listener's `RuntimeException`.
- `listenerFor`, lines 1263–1295 — the existing `IMapChangeListener` factory; it is not
  modified by this spec.

**(f) Freeplane event channel this subscription attaches to (unchanged core code):**

- `MapController.nodeChanged(NodeModel, Object, Object, Object)`, lines 959–963 (the
  one-argument overload is at lines 954–957), creates a
  `NodeChangeEvent(node, property, oldValue, newValue, true, true)` and calls
  `nodeRefresh`.
- `MapController.nodeRefresh(NodeChangeEvent)`, lines 976–1019, returns early while
  `mapReader.isMapLoadingInProcess()` (lines 978–980) and ends with
  `fireNodeChanged(node, nodeChangeEvent)` (line 1018).
- `MapController.fireNodeChanged(NodeModel, NodeChangeEvent)`, lines 733–737, snapshots the
  controller-level `nodeChangeListeners` list and delegates to
  `NodeModel.fireNodeChanged(INodeChangeListener[], NodeChangeEvent)`.
- `NodeModel.fireNodeChanged(INodeChangeListener[], NodeChangeEvent)`, lines 225–233, fires
  one event per content clone:

```java
	void fireNodeChanged(INodeChangeListener[] nodeChangeListeners, final NodeChangeEvent nodeChangeEvent) {
	    AccessController.doPrivileged( (PrivilegedAction<Void>) () -> {
	        for(NodeModel node : clones[CONTENT.ordinal()]){
	            final NodeChangeEvent cloneEvent = nodeChangeEvent.forNode(node);
	            node.fireSingleNodeChanged(nodeChangeListeners, cloneEvent);
	        }
	        return null;
	    });
	}
```

- `MapController.addNodeChangeListener`, lines 620–623, and
  `MapController.removeNodeChangeListener`, lines 1164–1166; the controller keeps one list
  (`nodeChangeListeners`, line 332) shared by all maps.
- `INodeChangeListener`, `freeplane/src/main/java/org/freeplane/features/map/INodeChangeListener.java`:

```java
public interface INodeChangeListener extends ComparableByPriority{
    public void nodeChanged(NodeChangeEvent event);
}
```

`ComparableByPriority` supplies the default `priority()`/`compareTo`, so the new listener
does not override either method.

- Canonical text-edit producer, `MTextController.setNodeObject`,
  `freeplane/src/main/java/org/freeplane/features/text/mindmapmode/MTextController.java:560-594`:
  on `act()` it calls `node.setUserObject(newObject)` and then
  `MapController.nodeChanged(node, NodeModel.NODE_TEXT, oldText, newObject)`; on `undo()` it
  swaps the values. `NodeModel.setText(String)` (line 634) only mutates state and never
  notifies, so tests must call `nodeChanged` explicitly.

### 2.1 New fields and imports

Required import additions (alphabetical, into the existing block at lines 30–36; all other
imports stay unchanged): add `import org.freeplane.features.map.INodeChangeListener;`
immediately after `import org.freeplane.features.map.IMapLifeCycleListener;`, and add
`import org.freeplane.features.map.NodeChangeEvent;` between
`import org.freeplane.features.map.MapModel;` and `import org.freeplane.features.map.NodeModel;`.

Required field declarations:

| Name | Type | Visibility | Initializer | Guard |
| --- | --- | --- | --- | --- |
| `nodeChangeListener` | `org.freeplane.features.map.INodeChangeListener` | `private final` | `new NodeChangeListener()` | immutable |
| `nodeListenerRegistered` | `boolean` | `private` | none (Java default `false`) | `monitor` |

Placement: add `nodeChangeListener` immediately after `lifecycleListener` (current line 96);
add `nodeListenerRegistered` immediately after `lifecycleListenerRegistered` (current line
100). No other field changes.

### 2.2 Constructor registration

In the `runOnEdtAndWait` runnable of the private constructor, immediately after
`mapController.addMapLifeCycleListener(lifecycleListener)`, add
`mapController.addNodeChangeListener(nodeChangeListener)` and set the flag inside the same
`monitor` block. The block becomes exactly:

```java
        try {
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

Threading and flag handling:

- Both registration calls execute on the EDT (`runOnEdtAndWait`).
- `nodeListenerRegistered = true` is written under `monitor` on the EDT, exactly mirroring
  `lifecycleListenerRegistered`.
- No extra rollback is introduced. If `addNodeChangeListener` throws, the existing
  `catch (RuntimeException failure)` (current lines 252–256) shuts down the fallback and
  owned schedulers and rethrows. If the throw happens after `addMapLifeCycleListener`
  succeeded, the lifecycle listener remains registered with its flag unset; that is the
  pre-existing exposure and the node listener adds no new failure mode. If both registrations
  succeed and a later constructor step (for example scheduler scheduling) throws, both flags
  are already `true`, so a subsequent `close()` removes both listeners.

### 2.3 `NodeChangeListener` inner class

Add a private inner class named `NodeChangeListener` immediately after the existing
`LifecycleListener` class (which ends at current line 1488). Exact code:

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

Requirements:

- The outer `catch (RuntimeException)` wraps the entire method body, including the inline EDT
  branch, so an editor operation that triggers the listener can never fail because of the
  graph plugin.
- The inner `catch (RuntimeException)` around `edt.execute` mirrors
  `LifecycleListener` and isolates a rejected/closed EDT executor.
- When the caller is not on the EDT, the runnable is submitted through the manager's
  `EdtExecutor` and reads `node.getMap()` inside the runnable.
- Null events and events whose `getNode()` is null are ignored without touching any lock.

### 2.4 `handleNodeChangedOnEdt(MapModel)`

Add a private method immediately after `callbackRunnable` (which ends at current line 986)
and before `loadOnEdt` (current line 988). Exact code:

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

Requirements:

- Threading: called only on the EDT (either inline from `NodeChangeListener` or from the
  EDT-dispatched runnable).
- Lookup: `entry.model == map` is an identity comparison; the scan is bounded by the number
  of entries in `entries` (active workspace maps) and stops at the first match. No new index
  or ordering invariant is introduced.
- The generation is read only under `monitor` and only for the matched entry. The subsequent
  `handleMapChanged` call revalidates closed state, entry identity, generation, model
  identity, `leaseCount > 0`, and `!loading`; it mutates state only when all checks pass.
- `handleNodeChangedOnEdt` does not mutate `entries` or `Entry`, does not acquire
  `settlementLock`, and releases `monitor` before calling `handleMapChanged`.
- Postcondition: `handleNodeChangedOnEdt` itself returns normally for every `MapModel` value
  (including `null`) and every manager state; its own failure surface is limited to the null
  checks and the identity scan. The residual surface is the pre-existing
  `handleMapChanged`/`publish` behavior, which isolates per-listener failures. For off-EDT
  delivery a residual `handleMapChanged` failure would surface on the EDT uncaught handler,
  not inside `nodeChanged`; this mirrors the existing `LifecycleListener` pattern and does not
  affect the inline-EDT guarantee that an editor operation cannot fail because of the graph
  plugin.

### 2.5 `close()` changes

Three edits inside `close()`:

1. Declare the capture flag next to `removeLifecycle` (current line 444):

```java
        boolean removeLifecycle;
        boolean removeNodeListener;
```

2. Capture and clear both flags inside the same `synchronized (monitor)` block, after the
   lifecycle lines (current lines 457–458):

```java
                removeLifecycle = lifecycleListenerRegistered;
                lifecycleListenerRegistered = false;
                removeNodeListener = nodeListenerRegistered;
                nodeListenerRegistered = false;
```

3. Extend the outer EDT-runnable condition and remove the node listener inside it (current
   lines 496–508):

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

Requirements:

- Flag capture/clear happens exactly once, inside the existing `monitor` section, and
  `closed = true` is set before the capture (current line 451). A second `close()` returns at
  the `closed` guard (current lines 448–450) and never recaptures or removes listeners.
- Both listener removals happen in the same single `runOnEdtAndWait` call and in the same
  EDT runnable as the detachments. That runnable executes after `settlementLock` has been
  released, exactly as today.
- No change to the outer `finally` shutdown of the fallback and owned schedulers, or to the
  `completeExceptionally(pending, new IllegalStateException("Map lease manager is closed"))`
  call.

### 2.6 Production non-changes

The following stay exactly as they are: `acquire`, `release`, `addListener`, `publish`,
`handleMapChanged`, `listenerFor`, `loadOnEdt`, `finishLoad`, `followNewlyCreatedModel`,
`LifecycleRemovalOperation`, `ExternalReloadOperation`, externals polling, settlement
plumbing, and all `Entry` state transitions. No new field on `Entry`; no new method other
than `handleNodeChangedOnEdt`, `NodeChangeListener.nodeChanged`, and the constructor/field/
close edits listed above.

## 3. Contracts

### 3.1 Preconditions and postconditions of the node-change path

Preconditions for publication of a node change:

- P1. `nodeChanged` may be invoked on any thread, with any `NodeChangeEvent`, including
  `null`; defensive null handling is part of the contract (Section 2.3).
- P2. For publication, at lookup time: `event.getNode() != null`, `node.getMap() != null`, a
  live `Entry` exists with `entries.get(entry.id) == entry`, `entry.model == node.getMap()`
  (identity), `entry.leaseCount > 0`, `!entry.loading`, and `!closed`.
- P3. For publication, at `handleMapChanged` revalidation time on the EDT, P2 must still
  hold and `entry.generation` must equal the generation captured in Section 2.4.

Postconditions:

- Q1. If P2/P3 hold, exactly one `MapAdapterEvent(entry.id, nextState)` is passed to
  `publish`, where `nextState` is `entry.state` if `entry.state` is
  `MapOperationalState.RELOAD_REQUIRED`, otherwise `stateForModel(map)`. `entry.state` is set
  to `nextState`.
- Q2. If any condition in P2/P3 fails, no event is published and no state changes.
- Q3. The lookup itself changes no state.

### 3.2 Guarantees

- G1 — Coalescing. Every published `MapAdapterEvent` reaches
  `GraphUpdateCoordinator.handleMapAdapterEvent`, which calls
  `requestSourceRebuild(ChangeKind.MAP_STATE)`; the call reaches
  `ProjectionBatcher.request`. `ProjectionBatcher` cancels and reschedules one pending
  future per request with `DEBOUNCE_MILLIS = 150L`
  (`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/ProjectionBatcher.java:21`;
  `requestOnEdt`, lines 130–158).
  Any number of node-change publications within one 150 ms debounce window yields exactly
  one `AcceptedBatch` and one projection rebuild.
- G2 — No publication after close. `close()` sets `closed = true` before any listener
  removal; `handleNodeChangedOnEdt` returns on `closed`; `handleMapChanged` re-checks
  `closed`; `publish` checks `closed` before its listener snapshot and before each listener.
  An in-flight event queued before close therefore publishes nothing after close.
- G3 — No publication for unleased, inactive, or loading entries. An unleased model has
  `leaseCount == 0`; an inactive/replaced model is no longer `entries.get(entry.id)`;
  a loading entry has `loading == true`. Each predicate independently suppresses
  publication.
- G4 — Generation monotonicity. `Entry.generation` is only incremented (load, reload,
  removal, close). The lookup captures a snapshot and `handleMapChanged` requires exact
  equality, so a generation bump between lookup and publication discards the event.
  `ProjectionBatcher.acceptedGeneration` increments per accepted batch, and
  `GraphUpdateCoordinator.acceptBatchInternal` discards `batch.generation() <=
  acceptedGeneration`.
- G5 — No new lock acquisition ordering. The new code acquires `monitor` only. It never
  acquires `settlementLock`, never calls out under `monitor`, and does not add any
  `monitor -> settlementLock` edge. The existing global order (`settlementLock` held while
  taking `monitor`) is preserved.
- G6 — Content-clone bursts. `NodeModel.fireNodeChanged` fires one event per content clone
  (line 225–233). Every clone event resolves to the same `MapModel`, the same `Entry`, and
  the same generation, and G1 collapses the burst into one accepted batch.

### 3.3 Threading and lock discipline

- `NodeChangeListener.nodeChanged` runs on the thread that fires the Freeplane node event
  (typically the EDT for editor operations, but any thread is allowed by the contract).
- `handleNodeChangedOnEdt` runs on the EDT only.
- `monitor` is held only for the bounded identity scan and flag reads. `handleMapChanged`
  holds it only for revalidation and event construction; `publish` holds it only for the
  `closed`/listener-snapshot sections.
- `close()` may run on any thread; its listener removal runs via `runOnEdtAndWait` outside
  `settlementLock`.

## 4. Error Behavior

- E1 — Exception isolation. `NodeChangeListener.nodeChanged` catches `RuntimeException`
  around its entire body. The inline EDT call to `handleNodeChangedOnEdt` is inside that
  catch. The off-EDT branch catches a rejected `edt.execute` separately (mirroring
  `LifecycleListener.onCreate`). Therefore an editor operation that fires a node change
  never fails because of the graph plugin.
- E2 — Deferred execution. When off the EDT, only queueing happens on the caller's stack.
  The queued runnable executes `handleNodeChangedOnEdt`, whose contract (Section 2.4) makes
  it non-throwing for every `MapModel` value and manager state; publisher-listener failures
  are isolated inside `publish` (lines 1345–1350).
- E3 — Stale events. An event for an entry that was removed, reloaded, replaced, or reduced
  to `leaseCount == 0` before the EDT runnable executes either matches nothing in the scan
  or fails P3 in `handleMapChanged`; no publication or state change occurs.
- E4 — Replaced models. `entry.model` may be replaced by `finishLoad`,
  `followNewlyCreatedModel`, `LifecycleRemovalOperation`, or the manager-owned reload path.
  Because lookup and revalidation compare model identity (`entry.model == map`), events from
  the previous model are discarded.
- E5 — Content-clone bursts. Each clone fires an event; all match the same entry and
  coalesce (G1, G6). No per-clone duplicate publication escapes the debounce window.
- E6 — Repeated close. The second and later `close()` calls return at the `closed` guard
  before flag capture; no listener is removed twice, and no exception is thrown.
- E7 — Constructor-failure exposure. No rollback is added. If a constructor step after a
  successful registration throws, the registered node listener is left attached when the
  caller never obtains a `MapLeaseManager` to close. This is the same pre-existing exposure
  as the lifecycle listener and is accepted in Section 9.
- E8 — `publish` isolation is unchanged: a throwing `MapAdapterListener` is swallowed and
  later listeners still run.

## 5. Behavior Matrix

| Change | Event channel | After this spec | Notes |
| --- | --- | --- | --- |
| Node text edit / node-property edit that only calls `nodeChanged` | `MapController.nodeChanged` → `nodeRefresh` → `NodeModel.fireNodeChanged` → new `INodeChangeListener` | Live, coalesced (new) | Canonical producer `MTextController.setNodeObject`; also `mapController.nodeChanged(node)` (unknown property) |
| Undo/redo of a text edit | Same `nodeChanged` path, fired from `IActor.undo`/`redo` | Live, coalesced (new) | Same subscription as the forward edit |
| Node insert / delete / move | `MapModel`-level `IMapChangeListener` registered by `listenerFor` | Live (unchanged) | `MapController.fireNodeInserted/fireNodeDeleted/fireNodeMoved` call `map.fire*Event` |
| Graph Group marker toggle | `GraphGroupController.setMarker` → `fireMapChanged` → `IMapChangeListener.mapChanged` | Live (unchanged) | |
| Icon/style/filter changes that call `fireMapChanged` | `IMapChangeListener.mapChanged` | Live (unchanged) | |
| Map loading | none | No publication | `MapController.nodeRefresh` returns early while `mapReader.isMapLoadingInProcess()` (lines 978–980) |
| Node events on a map that is unleased, inactive, replaced, or in another workspace | New `INodeChangeListener` | Ignored (new) | No `Entry` match or revalidation failure |
| External file change | Existing external poll / reload path | Live (unchanged) | |

## 6. Test Specification

### 6.1 New unit tests in `MapLeaseManagerShould`

Add three `@Test` methods to
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`.
They use the existing `environment(edt)`, `manager(environment, edt, environment::newMap)`,
`eventsFrom(manager)`, and `acquire(manager, environment, edt)` helpers, and the Section 6.2
additions.

**6.1.1 `publishesAdapterEventForNodeChangesOnLeasedModels`**

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
```

Requirements:

- The acquisition emits exactly `LOADING` then `AVAILABLE`; those events are discarded by
  counting (`acquisitionEvents == 2`) rather than by clearing the list.
- Firing the node event from the test thread queues EDT work; `edt.runAll()` runs it.
- Exactly one additional event is published and it is `AVAILABLE` for the same reference id.

**6.1.2 `ignoresNodeChangesForUnleasedAndInactiveModels`**

```java
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
```

Requirements:

- The never-leased model is a `FakeMapModel` that is not referenced by any `Entry`; its node
  event publishes nothing.
- `lease.close()` releases the only lease and removes the entry, so the subsequent node
  event for the former model publishes nothing. No assertion is made about the model-listener
  count here (that is covered elsewhere).

**6.1.3 `removesNodeChangeListenerOnClose`**

```java
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

Requirements:

- Exactly one node listener is registered by construction; `close()` removes it.
- The `@After closeManagers()` second close must remain harmless (E6).

### 6.2 `MapLeaseManagerShould.TestEnvironment` additions

Add to the nested `TestEnvironment` class (declared at current line 2648):

1. Field, next to `lifecycleListeners`:

```java
        private final List<INodeChangeListener> nodeChangeListeners = new ArrayList<INodeChangeListener>();
```

2. Mockito capture in the constructor, next to the `addMapLifeCycleListener` stubs:

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

3. Fire helper:

```java
        private void fireNodeChange(final NodeModel node) {
            final NodeChangeEvent event =
                new NodeChangeEvent(node, NodeModel.NODE_TEXT, "before", "after", true, true);
            for (INodeChangeListener listener : new ArrayList<INodeChangeListener>(nodeChangeListeners)) {
                listener.nodeChanged(event);
            }
        }
```

4. Imports in `MapLeaseManagerShould.java`: add
   `org.freeplane.features.map.INodeChangeListener` and
   `org.freeplane.features.map.NodeChangeEvent`. `doAnswer`, `any`, `mock`, and the existing
   Mockito statics are already imported.

No other `TestEnvironment` member changes.

### 6.3 New integration class `GraphWorkspaceLiveNodeRefreshShould`

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLiveNodeRefreshShould.java`,
package `org.freeplane.plugin.graph.integration`, modeled on
`GraphWorkspaceColdReloadShould` (same `TemporaryFolder` rule, same controller/view/close
skeleton) and reusing the package-private `GraphWorkspaceIntegrationSupport`.

Required imports: `static org.assertj.core.api.Assertions.assertThat`,
`java.nio.charset.StandardCharsets`, `java.nio.file.Files`, `java.nio.file.Path`,
`java.util.UUID`, `org.freeplane.core.undo.IActor`, `org.freeplane.core.undo.IUndoHandler`,
`org.freeplane.features.map.MapModel`, `org.freeplane.features.map.NodeModel`,
`org.freeplane.plugin.graph.command.GraphCommands`,
`org.freeplane.plugin.graph.control.CanvasState`,
`org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController`,
`org.freeplane.plugin.graph.control.GraphWorkspaceHandle`,
`org.freeplane.plugin.graph.geometry.LayoutPoint`,
`org.freeplane.plugin.graph.projection.ProjectedNodeKey`,
`org.freeplane.plugin.graph.workspace.GraphCommandResult`,
`org.freeplane.plugin.graph.workspace.model.MapReferenceId`,
`org.junit.Rule`, `org.junit.Test`, `org.junit.rules.TemporaryFolder`.

Fixture constant with two marked nodes (root plus two graph groups):

```java
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
```

Shared setup steps for tests 1, 2, and 4 (same-URL editor model):

1. `GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane = new GraphWorkspaceIntegrationSupport.FreeplaneScope();`
2. Write `LIVE_REFRESH_FIXTURE` to
   `temporaryFolder.getRoot().toPath().resolve(<name>.mm)` and call
   `freeplane.installGraphGroups();`.
3. `MapModel sourceMap = freeplane.loadWithView(sourceMapFile);` — this exact-URL model is
   reused by the lease (`MapLeaseManager.MapLookup` first asks
   `mapController.getMap(canonicalUrl)`), so these tests pin the missing node subscription,
   not the follower path.
4. `DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(
   freeplane.modeController(), views);` with a
   `GraphWorkspaceIntegrationSupport.RecordingViewFactory views`.
5. `GraphWorkspaceHandle handle = controller.open(workspaceFile);`
6. Compute
   `MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
   sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));`
   and execute `handle.execute(GraphCommands.addMap(mapId, sourceMapFile.toUri()))`, asserting
   `GraphCommandResult.Status.APPLIED`.
7. Await the initial projection with
   `GraphWorkspaceIntegrationSupport.awaitCondition(
   () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(handle.currentProjection(), "alpha"),
   15000L, "initial live label did not appear")` (and check `"bravo"` the same way where the
   test uses it).
8. `finally`: close handle, then `controller.shutdown()`, then `freeplane.close()`, each
   guarded so the first failure is preserved (copy the `try`/`catch (RuntimeException
   ignored)` pattern of `GraphWorkspaceColdReloadShould`).

The edit actor used by tests 1, 2, and 4 (exactly `MTextController.setNodeObject`
semantics applied to `NodeModel.setText` + the 4-argument `nodeChanged`):

```java
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
```

and is run as
`freeplane.modeController().execute(textEdit(freeplane.modeController(), <node>, <oldText>, <newText>), sourceMap)`, where `<node>`, `<oldText>`, and `<newText>` are the values specified by each test.

**6.3.1 `nodeTextEditRefreshesTheLiveProjection`** (acceptance 1; falsifiability test)

1. Shared setup with the fixture, awaiting `"alpha"`.
2. `NodeModel alpha = sourceMap.getRootNode().getChildAt(0);`, `oldText = alpha.getText()`,
   `newText = "omega"` (same length as `"alpha"`).
3. `freeplane.modeController().execute(textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);`
4. Await
   `() -> GraphWorkspaceIntegrationSupport.hasNodeLabel(handle.currentProjection(), "omega")
   && !GraphWorkspaceIntegrationSupport.hasNodeLabel(handle.currentProjection(), "alpha")`
   with timeout `15000L` and failure message
   `"edited label did not reach the live projection"`.

This test fails on the pre-change code because no `INodeChangeListener` is registered.

**6.3.2 `nodeTextEditPreservesUnaffectedNodePositions`** (acceptance 2)

1. Shared setup with the fixture, awaiting both `"alpha"` and `"bravo"`.
2. Await `OperationalStatus.IDLE` on the latest binding before capturing the baseline:
   `GraphWorkspaceIntegrationSupport.awaitIdle(views.latestBinding(), 15000L);`
   (The idle wait is required because boundary correction can still adjust coordinates while
   the settle loop is stepping; an enclosure-count wait is not sufficient.)
3. Capture the baseline:

```java
        CanvasState baseline = views.latestBinding().currentCanvasState();
        long baselineGeneration = baseline.generation();
        ProjectedNodeKey alphaKey =
            GraphWorkspaceIntegrationSupport.nodeKeyByLabel(baseline.projection(), "alpha");
        ProjectedNodeKey bravoKey =
            GraphWorkspaceIntegrationSupport.nodeKeyByLabel(baseline.projection(), "bravo");
        LayoutPoint alphaBefore = baseline.layout().positions().nodes().get(alphaKey);
        LayoutPoint bravoBefore = baseline.layout().positions().nodes().get(bravoKey);
        assertThat(alphaBefore).isNotNull();
        assertThat(bravoBefore).isNotNull();
```

4. Edit only `"alpha"` with the same-length replacement `"alpha"` -> `"omega"` using the
   shared actor.
5. Await the canvas state that carries the new label and a newer generation:

```java
        GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
                CanvasState state = views.latestBinding().currentCanvasState();
                return state.generation() > baselineGeneration
                    && GraphWorkspaceIntegrationSupport.hasNodeLabel(state.projection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(state.projection(), "alpha");
            }, 15000L, "same-length text edit did not reach the live canvas state");
```

6. Await `OperationalStatus.IDLE` again so the frame used for the comparison has quiesced:
   `GraphWorkspaceIntegrationSupport.awaitIdle(views.latestBinding(), 15000L);`
7. Assert both nodes kept their baseline positions:

```java
        CanvasState edited = views.latestBinding().currentCanvasState();
        assertThat(edited.layout().positions().nodes().get(alphaKey)).isEqualTo(alphaBefore);
        assertThat(edited.layout().positions().nodes().get(bravoKey)).isEqualTo(bravoBefore);
```

A moved position is a finding: the test must fail, not be relaxed. Position equality is the
exact `LayoutPoint.equals` comparison (bitwise `Double.compare` on x and y).

**6.3.3 `nodeTextEditRefreshesWhenADistinctEditorModelAppears`** (acceptance 6: replaced /
followed editor-model path)

1. Create `realDirectory` and `aliasDirectory` under the temporary folder; write
   `LIVE_REFRESH_FIXTURE` to `realDirectory.resolve("symlinked.mm")`; create
   `aliasDirectory.resolve("symlinked.mm")` as a symbolic link to the real file (same pattern
   as `GraphWorkspaceColdReloadShould.unmarkingRefreshesWhenTheEditorOpensTheMapThroughASymlinkedPath`).
2. `freeplane.installGraphGroups();` then open the workspace, controller, and views; add the
   map with the alias URI `aliasMapFile.toUri()` (the lease canonicalizes to the real path);
   await the initial `"alpha"` label through the lease-loaded model.
3. `MapModel editorMap = freeplane.loadWithView(aliasMapFile);` — this loads a distinct
   editor model because the alias URL differs from the lease's canonical real URL, which
   exercises `MapLeaseManager.handleMapCreatedOnEdt` / `followNewlyCreatedModel`.
4. `freeplane.drainAsyncQueues();` — deterministic barrier before editing. `loadWithView`
   fires `MapController.fireMapCreated` on the calling thread, so `LifecycleListener.onCreate`
   queues `handleMapCreatedOnEdt` through `SwingUtilities.invokeLater`. `drainAsyncQueues()`
   awaits the scope executor and then posts its own `SwingUtilities.invokeAndWait`; the AWT
   EventQueue dispatches earlier posts first, so `handleMapCreatedOnEdt` /
   `followNewlyCreatedModel` has completed and `entry.model` is already the editor model when
   the edit runs. This avoids depending on interleaving between the test thread and the scope
   executor thread.
5. `NodeModel alpha = editorMap.getRootNode().getChildAt(0);`; run the shared actor on
   `editorMap` with `"alpha"` -> `"omega"`.
6. Await `hasNodeLabel(handle.currentProjection(), "omega")` and
   `!hasNodeLabel(handle.currentProjection(), "alpha")` with timeout `15000L` and message
   `"edited label did not reach the live projection after the editor model appeared"`.

**6.3.4 `undoOfNodeTextEditRefreshesTheLiveProjection`** (acceptance 1 for undo)

1. Shared setup with the fixture, awaiting `"alpha"`.
2. Run the shared actor `"alpha"` -> `"omega"` and await `"omega"` as in 6.3.1.
3. `sourceMap.getExtension(IUndoHandler.class).undo();`
4. Await `hasNodeLabel(handle.currentProjection(), "alpha")` and
   `!hasNodeLabel(handle.currentProjection(), "omega")` with timeout `15000L` and message
   `"undone label did not reach the live projection"`.

### 6.4 `GraphWorkspaceIntegrationSupport` additions

All edits are inside `GraphWorkspaceColdReloadShould.java`.

1. In `FreeplaneScope` (declared at line 1000), next to `mapLifecycleListenerCount()`
   (line 1190):

```java
        int mapNodeChangeListenerCount() {
            return mapController.getNodeChangeListeners().size();
        }
```

2. In `ResourceBaseline` (declared at line 885), add a `nodeChangeListeners` field, extend
   the constructor parameter list, include it in `matches`, and pass/assert it:

```java
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
```

3. `baseline(...)` (line 694) passes the new count:

```java
        return new ResourceBaseline(freeplane.mapLifecycleListenerCount(), freeplane.mapChangeListenerCount(),
            freeplane.mapNodeChangeListenerCount(), freeplane.viewCount(), freeplane.timerCount(),
            graphThreadProfile(), temporaryArtifacts(root));
```

4. `awaitBaseline(...)` (line 699) asserts it after the map-change assertion:

```java
        assertThat(freeplane.mapNodeChangeListenerCount()).isEqualTo(expected.nodeChangeListeners);
```

5. New label helpers used by `GraphWorkspaceLiveNodeRefreshShould`:

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
```

6. New idle helper:

```java
    static void awaitIdle(final GraphWorkspaceViewBinding binding, final long timeoutMillis)
            throws Exception {
        awaitCondition(() -> binding.currentCanvasState().status() == OperationalStatus.IDLE,
            timeoutMillis, "graph workspace did not reach OperationalStatus.IDLE");
    }
```

`ProjectedNode`, `ProjectedNodeKey`, `GraphProjection`, `GraphWorkspaceViewBinding`, and
`OperationalStatus` are already imported in `GraphWorkspaceColdReloadShould.java`.

### 6.5 Falsifiability and verification gates

- Falsifiability probe (manual evidence, no committed mutation): remove (comment out) the
  `mapController.addNodeChangeListener(nodeChangeListener);` line added in Section 2.2, then
  run
  `gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection"`.
  The test must fail at the await with the message
  `"edited label did not reach the live projection"`. Restore the line and re-run: the test
  must pass. This proves the test pins the subscription mechanism rather than some other
  refresh path.
- Full module gate: `gradle :freeplane_plugin_graph:test` with the repository's Java 21 Zulu
  (`~/.sdkman/candidates/java/21.0.8-zulu`).
- Core gate: `git diff --stat` shows no file under `freeplane/` or `freeplane_api/` modified.

## 7. Acceptance Criteria

| # | Criterion | Named test(s) |
| --- | --- | --- |
| AC1 | With the workspace open and the map active, editing a node's text updates the graph label without deactivate/reactivate, within the existing coalescing window. | `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection`; `GraphWorkspaceLiveNodeRefreshShould.undoOfNodeTextEditRefreshesTheLiveProjection`; `MapLeaseManagerShould.publishesAdapterEventForNodeChangesOnLeasedModels` |
| AC2 | Unaffected node positions are unchanged by a text-only edit. | `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditPreservesUnaffectedNodePositions` |
| AC3 | Structural edits, marker toggles, and style/icon changes refresh exactly as before; no duplicate or missing rebuilds. | `GraphWorkspaceColdReloadShould.unmarkingAGroupMarkerRemovesTheBoundaryFromTheLiveProjection`; `GraphWorkspaceColdReloadShould.coldReloadsProductionWorkspaceStateAndProjection`; full `:freeplane_plugin_graph:test` suite |
| AC4 | Workspace close removes the node listener; existing resource-baseline checks stay green. | `MapLeaseManagerShould.removesNodeChangeListenerOnClose`; `GraphWorkspaceLifecycleShould.returnsProductionResourcesToBaselineAcrossTwentyFiveOpenCloseRestartCycles`; `GraphWorkspaceLifecycleShould.savesDirtyWorkspaceSynchronouslyWhenClosedDuringDebounce`; `GraphWorkspaceLifecycleShould.preservesFailedCloseRetryDiscardAndCancelSemantics`; `GraphWorkspaceLifecycleShould.doesNotDeliverCallbacksAfterClose` (all through the extended `ResourceBaseline.nodeChangeListeners`) |
| AC5 | The full `:freeplane_plugin_graph:test` suite passes. | `gradle :freeplane_plugin_graph:test` |
| AC6 | Unleased and inactive entries never publish; a distinct editor model is followed and publishes. | `MapLeaseManagerShould.ignoresNodeChangesForUnleasedAndInactiveModels`; `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesWhenADistinctEditorModelAppears` |

## 8. Traceability

| Design section | Spec section | Test method(s) |
| --- | --- | --- |
| Background / Confirmed defect and evidence | 1, 2.0, 3 | `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection` |
| Goals | 1.1, 3.2 | all named tests |
| Non-Goals | 1.2 | n/a (scope guard; core diff check in 6.5) |
| Design / Subscription | 2.1, 2.2 | `MapLeaseManagerShould.removesNodeChangeListenerOnClose` |
| Design / Event handling | 2.3, 2.4, 3.1, 3.2, 3.3 | `MapLeaseManagerShould.publishesAdapterEventForNodeChangesOnLeasedModels`; `MapLeaseManagerShould.ignoresNodeChangesForUnleasedAndInactiveModels` |
| Design / Data flow | 3.2 (G1), 5 | `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection`; `GraphWorkspaceLiveNodeRefreshShould.nodeTextEditPreservesUnaffectedNodePositions` |
| Design / Lifecycle and error handling | 2.5, 4 | `MapLeaseManagerShould.removesNodeChangeListenerOnClose`; `GraphWorkspaceLifecycleShould.*` baseline tests |
| Design / Behavior summary | 5 | matrix rows map to the named tests above |
| Testing Strategy / Unit | 6.1, 6.2 | the three `MapLeaseManagerShould` tests |
| Testing Strategy / Integration | 6.3, 6.4 | the four `GraphWorkspaceLiveNodeRefreshShould` tests |
| Gates | 6.5 | falsifiability probe; full suite |
| Acceptance Criteria | 7 | AC1–AC6 |
| Alternatives Considered | 1.2 | n/a (excluded scope) |
| Risks | 9 | risk 3 is arbitrated by `nodeTextEditPreservesUnaffectedNodePositions` |

## 9. Risks Accepted

Copied from the approved design:

1. Node-change events that cannot affect the projection still schedule one coalesced rebuild
   (for example a non-visual property refresh). Accepted: the rebuild is pure, debounced, and
   generation-guarded; property filtering can be added later if measurements justify it.
2. The subscription is global, so every node change anywhere in the application invokes the
   handler and scans `entries` under `monitor` on the EDT. The scan is bounded by the number
   of active workspace maps and the rebuild is debounced; an `entries.isEmpty()` early return
   can be added if measurements ever justify it.
3. If text-only edits turn out to reset positions under a live rebuild, acceptance test 2
   surfaces it as a separate finding; the design does not assume position preservation without
   a test.

Known limitation (review note, accepted):

4. Two workspace registrations can resolve to the same canonical model (distinct stored URIs,
   same file). The specified node-event lookup stops at the first matching entry, so only that
   entry publishes for the shared model. This is inherited from the approved design's
   first-match pseudocode and is not exercised by the acceptance tests; it is recorded here as
   a known limitation rather than silently handled.
