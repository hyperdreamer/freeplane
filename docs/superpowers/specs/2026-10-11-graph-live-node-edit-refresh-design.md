# Graph Live Refresh on Node-Level Map Changes — Design

- Date: 2026-10-11
- Topic slug: `graph-live-node-edit-refresh`
- Status: Approved design (pending design review)
- Scope: bundled plugin `freeplane_plugin_graph` only; no Freeplane core change

## Background

The Graph Workspace projects active maps into a graph and is required to "update the graph
live while source maps change" (`2026-08-10-graph-workspace-design.md`, goal 9) and to
"repaint labels without resetting positions" for text-only changes (same document,
"Threading And Live Updates").

A reported defect: after editing a map, the graph view does not reflect the change until the
map is deactivated and then reactivated in the workspace sidebar.

### Confirmed defect and evidence

The workspace map adapter (`org.freeplane.plugin.graph.adapter.MapLeaseManager`) subscribes
only to the map-level change channel. For every leased model it registers an
`IMapChangeListener` on the `MapModel` (`listenerFor(...)`, `model.addMapChangeListener(...)`)
and reacts to `mapChanged`, `onNodeInserted`, `onNodeDeleted`, and `onNodeMoved`. Its
`onPreNodeMoved`/`onPreNodeDelete` handlers are unreachable: `MapController` dispatches
pre-events only to its controller-level `mapChangeListeners`, and `MapModel` never fires
pre-events.

Freeplane delivers ordinary node edits through a different channel:

1. `MTextController.setNodeObject` (and most node-property actors) mutate the node and call
   `MapController.nodeChanged(node, property, oldValue, newValue)`.
2. `MapController.nodeChanged` → `MapController.nodeRefresh` →
   `NodeModel.fireNodeChanged(nodeChangeListeners, event)`, which notifies only
   `INodeChangeListener`s registered on the `MapController`.
3. `MapModel.fireMapChangeEvent` is **not** called on this path.

The plugin registers no `INodeChangeListener` in `src/main`. Therefore a plain text edit
never reaches the adapter, no `MapAdapterEvent` is published, and no projection rebuild is
requested. Structural changes still refresh because `MapController.fireNodeInserted`,
`fireNodeDeleted`, and `fireNodeMoved` call `map.fireNodeInsertionEvent`,
`map.fireNodeDeletionEvent`, and `map.fireNodeMovedEvent` on the `MapModel`. Marker toggles
and some style/icon edits refresh because their controllers call `fireMapChanged(...)`
explicitly (for example `GraphGroupController.setMarker`).

Deactivate/Reactivate works around the defect because both commands produce a workspace
`DOCUMENT_CHANGED` event; `WorkspaceMapCoordinator.installDocumentOnEdt` reinstalls the
registration, re-acquires the lease, and forces a full re-snapshot rebuild.

A disposable probe (headless `FreeplaneScope`, no worktree edits) reproduced the defect:

| Probe edit | Result |
| --- | --- |
| `setText(...)` + `mapController.nodeChanged(node)` (canonical text-edit path) | Live projection label stayed stale past 5 s — **fails** |
| Same edit plus `mapController.fireMapChanged(new MapChangeEvent(...))` | Label refreshed immediately — **passes** |

The second probe is the control: the snapshot, batcher, projection, diff, and layout paths
are live-capable. Only the event subscription is missing.

## Goals

1. Node-level map changes on a leased, active map trigger the existing coalesced projection
   rebuild while the workspace is open, with no deactivate/reactivate.
2. Reuse the existing publication, generation, coalescing, and layout machinery; add no new
   projection, snapshot, layout, or canvas behavior.
3. Preserve positions for unaffected nodes on text-only edits.
4. Remove the new subscription cleanly when the manager closes; leave no listener or thread
   residue.

## Non-Goals

- No Freeplane core change (no modification of `MapController`, `MapModel`, or `NodeModel`
  notification semantics).
- No property-level filtering of node-change events.
- No new UI, no new commands, no persistence or workspace-format change.
- No changes to projection, snapshot, layout, canvas, or hit-testing behavior.

## Design

### Subscription

`MapLeaseManager` gains one `INodeChangeListener` instance (a private inner class, mirroring
the existing `LifecycleListener`) and a `nodeListenerRegistered` flag guarded by `monitor`.
It is registered exactly where the lifecycle listener is registered today: inside the
constructor's `runOnEdtAndWait` block, immediately after
`mapController.addMapLifeCycleListener(lifecycleListener)`.

`MapController` keeps node-change listeners in a single controller-level list, so this is one
global subscription per `MapLeaseManager` instance, not one per map. Filtering to leased
models happens in the event handler.

### Event handling

```
NodeChangeListener.nodeChanged(NodeChangeEvent event):
    try:
        if event == null: return
        node = event.getNode()
        if node == null: return
        dispatch to EDT (same pattern as LifecycleListener)
        on EDT: handleNodeChangedOnEdt(node.getMap())
    catch RuntimeException: ignore

handleNodeChangedOnEdt(MapModel map):
    if map == null: return
    Entry match = null; long generation = 0
    synchronized (monitor):
        if closed: return
        for entry in entries.values():
            if entry.model == map and entry.leaseCount > 0 and not entry.loading:
                match = entry; generation = entry.generation; break
    if match != null: handleMapChanged(match, map, generation)
```

The lookup is an identity scan over `entries` (bounded by the number of active maps in the
workspace); `handleMapCreatedOnEdt` already scans the same collection the same way, so no new
index or invariant is introduced. All reachable `entry.model` mutations
(`finishLoad`, `followNewlyCreatedModel`, `continueManagerOwnedReloadOnEdt`,
`LifecycleRemovalOperation`, `detachmentFor`/`removeEntryIfUnused`, `close`) remain owned by
their existing code paths.

`handleMapChanged` remains the single publication path and keeps its full revalidation:
entry identity, generation, model identity, `leaseCount > 0`, `!loading`, and EDT re-posting.
The new listener therefore cannot publish for a stale entry, a replaced model, or a map that
is merely registered but inactive.

### Data flow (unchanged after publication)

```
node edit (EDT) → MapLeaseManager publishes MapAdapterEvent(id, AVAILABLE)
  → GraphUpdateCoordinator.handleMapAdapterEvent
  → requestSourceRebuild(ChangeKind.MAP_STATE)
  → ProjectionBatcher (150 ms debounce, cancelled-and-rescheduled per request)
  → WorkspaceMapCoordinator.capture (fresh MapSnapshotFactory snapshot on EDT)
  → ProjectionEngine.project (off EDT)
  → ProjectionDiff.between(previous, next)
  → LayoutSettleLoop.start (retains the last valid frame when it covers the projection)
  → CanvasState published on EDT
```

Stable-key value changes (label/style) land in `ProjectionDiff.changedNodes` because
`ProjectedNode` equality includes the label. The normal path keeps existing coordinates:
`GraphStreamLayoutEngine.synchronize` retains the x/y of existing particles for stable ids,
and an empty diff takes the engine fast path. The `covers(...)`-based retained frame in
`LayoutSettleLoop` is consulted only on the worker-failure path, so no new layout guard is
required; acceptance test 2 is the arbiter.

### Lifecycle and error handling

- `close()` removes the node listener in the same EDT runnable that removes the lifecycle
  listener. That runnable's outer condition becomes
  `removeLifecycle || nodeListenerRegistered || !detachments.isEmpty()`; both flags are
  captured and cleared under `monitor` exactly once. The constructor-failure window that can
  leave the lifecycle listener registered (pre-existing) carries the same exposure for the
  node listener; no new failure mode is added.
- After `closed` is set, `handleNodeChangedOnEdt` returns without publishing.
- The listener boundary catches `RuntimeException` around the entire handler body (including
  the inline EDT branch), so an editor keystroke can never fail because of the graph plugin.
- Per-`MapAdapterListener` failure isolation inside `publish` is unchanged.
- Content clones: `NodeModel.fireNodeChanged` fires one event per content clone; each event
  resolves to the same map and the same entry, and the batcher collapses the burst into one
  accepted batch.
- Map loading fires no node events: `MapController.nodeRefresh` returns early while
  `mapReader.isMapLoadingInProcess()`.
- Edits on maps that are inactive, unleased, or belong to other workspaces match no entry and
  are ignored.

### Behavior summary

| Change | Before | After |
| --- | --- | --- |
| Node text / node-property edit (`nodeChanged` only) | Stale until deactivate/reactivate | Live, coalesced |
| Node insert / delete / move | Live | Live (unchanged) |
| Graph Group marker toggle | Live | Live (unchanged) |
| Icon / style / filter changes that call `fireMapChanged` | Live | Live (unchanged) |
| Map undo/redo of a text edit | Stale until deactivate/reactivate | Live (same `nodeChanged` actor path) |
| External file change | Live (polling/reload path) | Live (unchanged) |

## Testing Strategy

### Unit — `MapLeaseManagerShould`

Extend the existing mocked-`MMapController` `TestEnvironment` with
`addNodeChangeListener` / `removeNodeChangeListener` capture (the same pattern already used
for `addMapLifeCycleListener`), then add:

1. `publishesAdapterEventForNodeChangesOnLeasedModels` — acquire a lease, discard the
   acquisition events (`LOADING` then `AVAILABLE`), fire a `NodeChangeEvent` for a node of the
   leased model, and assert exactly one new `MapAdapterEvent` with `AVAILABLE`.
2. `ignoresNodeChangesForUnleasedAndInactiveModels` — after `lease.close()` (and for a model
   that was never leased), firing a node event publishes nothing.
3. `removesNodeChangeListenerOnClose` — `manager.close()` removes exactly the registered
   listener from the controller capture.

### Integration — headless `FreeplaneScope` (same package support class)

Add a focused class `GraphWorkspaceLiveNodeRefreshShould` (reusing
`GraphWorkspaceIntegrationSupport`) rather than growing `GraphWorkspaceColdReloadShould`:

1. `nodeTextEditRefreshesTheLiveProjection` — open a workspace, add a marked map, await the
   initial label, run an actor that performs `setText(newText)` plus
   `mapController.nodeChanged(node, NodeModel.NODE_TEXT, oldText, newText)` (exactly the
   `MTextController.setNodeObject` semantics), and await the new label in
   `handle.currentProjection()`. This test fails on the current code.
2. `nodeTextEditPreservesUnaffectedNodePositions` — two marked nodes; await
   `OperationalStatus.IDLE` on the latest binding before capturing baseline positions; edit
   one label with a same-length replacement; await the canvas state that carries the new label
   and generation; assert both nodes keep their baseline positions. Awaiting idle (not just an
   enclosure count) is required because boundary correction can still adjust coordinates while
   the settle loop is stepping. A moved position is a finding, not silently accepted.
3. `nodeTextEditRefreshesWhenADistinctEditorModelAppears` — lease-load the map through the
   workspace first, then open the same file through an alias/symlink URL (the pattern already
   used by `unmarkingRefreshesWhenTheEditorOpensTheMapThroughASymlinkedPath`), which is what
   actually forces a distinct editor model and exercises `handleMapCreatedOnEdt` /
   `followNewlyCreatedModel`. A same-URL `loadWithView` reuses the lease's model
   (`MapLoader`/`MMapController.getMap(URL)` match by exact URL), so it would not cover that
   path.
4. Undo/redo: undo of the text-edit actor must also refresh (it uses the same `nodeChanged`
   channel); assert once so the shared channel is pinned.
5. Listener residue: extend the existing resource-baseline assertions so the node-listener
   count returns to its baseline after workspace close.

### Gates

- `gradle :freeplane_plugin_graph:test` (full module, Zulu 21 per repository rules).
- The live-refresh tests must be falsifiable: temporarily removing the new subscription (or
  its registration) must make test 1 fail, proving the test pins the mechanism.

## Acceptance Criteria

1. With the workspace open and the map active, editing a node's text in the editor updates
   the graph label without deactivate/reactivate, within the existing coalescing window.
2. Unaffected node positions are unchanged by a text-only edit.
3. Structural edits, marker toggles, and style/icon changes refresh exactly as before; no
   duplicate or missing rebuilds.
4. Workspace close removes the node listener; existing resource-baseline checks stay green.
5. The full `:freeplane_plugin_graph:test` suite passes.

## Alternatives Considered

- **Core map-change re-fire** (make `MapController.nodeRefresh` call
  `map.fireMapChangeEvent`): rejected — wide blast radius across views and plugins, feedback
  risk, and it changes Freeplane core semantics for a plugin-local defect.
- **Polling / periodic re-snapshot**: rejected — inherent latency and CPU cost, still stale
  between polls, masks the defect instead of fixing it, and is harder to test
  deterministically.

## Risks

- Node-change events that cannot affect the projection still schedule one coalesced rebuild
  (for example a non-visual property refresh). Accepted: the rebuild is pure, debounced, and
  generation-guarded; property filtering can be added later if measurements justify it.
- The subscription is global, so every node change anywhere in the application invokes the
  handler and scans `entries` under `monitor` on the EDT. The scan is bounded by the number of
  active workspace maps and the rebuild is debounced; an `entries.isEmpty()` early return can
  be added if measurements ever justify it.
- If text-only edits turn out to reset positions under a live rebuild, acceptance test 2
  surfaces it as a separate finding; the design does not assume position preservation without
  a test.
