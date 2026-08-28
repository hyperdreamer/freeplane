# Graph Workspace Click-to-Center Design

- Date: 2026-08-28
- Status: Approved for implementation
- Scope: Clicking a node (or group-marked boundary) in the Graph Workspace jumps the mindmap to that node — selects it and centers it. Group-marked boundaries are accounted as nodes in every graph node count. Graph plugin only; no `freeplane_api` or core changes.

## Context

The Graph Workspace renders group-marked nodes as marker-style boundaries (hulls) and map roots as frames (`2026-08-27-graph-workspace-group-only-boundaries`, merge `2bd4626ca2`). Today:

- **Single-click** on a shape only changes the graph's own selection (`GraphIntent.ChangeSelection`); the mindmap is untouched.
- **Double-click / Enter** emits `GraphIntent.OpenSourceNode`, which runs `SourceNavigation.open()`: lease check, traversal resolution, `mapController.select(node)` — selecting the node and scrolling it **into view** (`selectAsTheOnlyOneSelected` → `scrollNodeToVisible`), not centering it. Status messages on failure; `editorViewActivated` re-focuses the graph canvas.
- **Node counts are all zero.** Since the group-only merge, `ProjectedNode` emission is gone (`projection.nodes()` is always empty), yet three places still count nodes from it: `GraphProjection.projectedNodeCount()` (= `nodes().size()`, feeds the status bar and the 2,000-node warning), `GraphWorkspaceWindow.updateMapRows` (per-map row counts), and `GraphCanvas` (rendering policy `forCounts`). A map with group markers always shows "0 nodes".

User requirements (confirmed through design dialogue):

1. **Single-click lockstep.** Clicking a visible shape (group-marked boundary, map root frame — any clickable endpoint) with the SELECT tool selects it in the graph *and* jumps the mindmap to its source node: select **and center**. The graph window keeps focus and stays in front.
2. **Empty-space click** clears the graph selection only; the mindmap is untouched.
3. **Double-click / Enter** keep their current gesture (open source node) and failure messages, and are upgraded to also center (they share `SourceNavigation.open`).
4. **Keyboard traversal** (arrow keys, Tab, accessibility selection) stays selection-only — no mindmap jumps.
5. **Group-marked nodes are accounted as nodes.** Every place the graph reports a node count (status bar total, map list rows, canvas rendering policy) counts group-marked boundaries as nodes; map root frames remain containers (a map with no group markers shows 0 nodes).
6. **Single-click failures are silent** (no status message); only the deliberate double-click/Enter gesture reports them.

## Design

### 1. New intent: `GraphIntent.RevealSourceNode`

New nested class in `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphIntent.java`, mirroring `OpenSourceNode`: carries a `ProjectedEndpointKey`, `of(...)` factory, `endpoint()`/`key()` accessors, value equality/hashCode over the endpoint. Semantics: "reveal this endpoint's source node in the mindmap, silently".

### 2. Single-click emission (`GraphInteractionController.handleMouseClicked`)

Single-click branch (SELECT tool, left button, `getClickCount() < 2`):

- endpoint present → `setSelectionVisual(endpoint)`, emit `ChangeSelection(Optional.of(endpoint))`, then emit `RevealSourceNode(endpoint)`
- endpoint absent (empty space) → emit `ChangeSelection(Optional.empty())` only (unchanged)

Double-click branch untouched (`OpenSourceNode`). `handleMousePressed`/`handleMouseDragged` untouched (pan/select-rectangle gestures do not navigate).

### 3. Silent execution (`GraphWorkspaceWindowModel`)

`handleIntent` gains a branch for `RevealSourceNode`: resolve `source(endpoint)` (existing static helper, handles node and enclosure endpoints) and execute `GraphCommands.openSource(...)` **silently**.

`executeCommand(command)` is refactored so the status-message sink is conditional: `executeCommand(command, boolean silent)` — or an `executeCommandSilently` wrapper. Silent mode still:
- executes the command and `refreshPresentation()`
- applies the initial viewport when pending
- honors `editorViewActivated()` → `graphFocus.run()` (keeps the graph canvas focused — requirement 1)
- skips only the `REJECTED`/`NO_OP` message-sink call

`RevealSourceNode` is a navigation gesture: no `readOnly` gate (same as `OpenSourceNode` today).

### 4. Centering (`SourceNavigation.open`)

After `mapController.select(resolved.get())`, call `mapController.centerNode(resolved.get())`. `MapController.centerNode` already exists in core (`Controller.getCurrentController().getSelection().scrollNodeToCenter(node)`) — no core change. Because double-click/Enter share `open()`, they get centering for free (requirement 3). The `APPLIED` result keeps `withEditorViewActivated(true)`.

### 5. Node accounting (group-marked boundaries count as nodes)

Rule: a projected **node** is any `ProjectedEnclosure` with `mapRoot() == false` (group-marked boundary). Map root frames are containers, not nodes.

1. `GraphProjection.projectedNodeCount()`: count non-`mapRoot` enclosures instead of `nodes().size()`. Fixes the status bar total and the 2,000-node warning threshold.
2. `GraphWorkspaceWindow.updateMapRows`: per-map `projectedNodeCount` counts that map's non-`mapRoot` enclosures (replaces the `ProjectedNode` loop).
3. `GraphStatusBar.Status.from`: per-map `nodeCounts` counts non-`mapRoot` enclosures per map (same swap).
4. `GraphCanvas` (line ~362): `nodeCount` uses `projection.projectedNodeCount()` instead of `nodes().size()`.

No rendering change: boundaries still paint as marker-style shapes; only the accounting treats them as nodes.

## Data flow

```
mouse click (SELECT tool, left)
  └─ GraphInteractionController.handleMouseClicked
       ├─ endpoint present → ChangeSelection(endpoint)  → graph paint state, map rows, status bar
       └─ endpoint present → RevealSourceNode(endpoint) → window handleIntent
                                                          └─ executeCommandSilently(GraphCommands.openSource(source(endpoint)))
                                                               └─ GraphCommandRouter → SourceNavigation.open
                                                                    ├─ active lease? (MapOperationalState.AVAILABLE)
                                                                    ├─ traversal resolve → NodeModel
                                                                    ├─ mapController.select(node)   (switches map, selects, scrolls into view)
                                                                    ├─ mapController.centerNode(node)
                                                                    └─ APPLIED + editorViewActivated → graph canvas keeps focus
```

## Error handling

- **Map unavailable** (no active lease) / **node unresolved**: `SourceNavigation` returns `REJECTED` (`graph_workspace.source_map.unavailable` / `graph_workspace.source_node.not_found`). Single click: silent — the graph still selects the shape; no status message. Double-click/Enter: message shown as today.
- **Editor focus**: `editorViewActivated(true)` is preserved on both paths; `graphFocus.run()` keeps keyboard focus on the graph canvas, so browsing the graph is uninterrupted.
- **Map not yet displayed / no view exists**: `MapController.select` handles map switching; in the rare case where a new view must be created it defers via `IMapViewChangeListener` and re-invokes `select(node)` after display (ending in `scrollNodeToVisible`). `centerNode` after `select` is best-effort in that case (no-op when the view is absent). The node is still revealed; centering may fall back to scroll-into-view on a cold view. Implementation verifies whether a centering listener is cheap; if not, the fallback is a documented known limit.

## Testing & verification

**Controller (`GraphInteractionControllerShould`):**
- single click on a hit endpoint → intents contain `ChangeSelection` then `RevealSourceNode`
- single click on empty space → only `ChangeSelection` (mindmap untouched)
- double click → `OpenSourceNode` (unchanged); `scenario14` updated to expect `RevealSourceNode` on the single click
- connect tool clicks and drag gestures do not emit `RevealSourceNode`

**Navigation (`SourceNavigationShould`):**
- `open` selects via the traversal resolver **and** centers: verify `mapController.centerNode(node)` called with the resolved node after `select` (mock `MapController`); `editorViewActivated` still true
- rejected paths (unavailable lease, unresolved node) do not select and do not center

**Window (`GraphWorkspaceWindowModelShould`, `WorkspaceDialogsShould`):**
- `RevealSourceNode` executes `openSource` with the resolved source (node and enclosure endpoints)
- silent path: REJECTED result produces **no** status message; `editorViewActivated` still re-focuses the graph canvas
- `OpenSourceNode` (double-click/Enter) still reports REJECTED messages
- node-count assertions updated to group-marked semantics (`GraphWorkspaceWindowModelShould` row counts, `WorkspaceDialogsShould` status total)

**Projection (`StructuralProjectionShould` + new):**
- `projectedNodeCount()` counts non-`mapRoot` enclosures; map root frames excluded; map with no group markers → 0
- existing `projectedNodeCount()` assertion (line ~55) updated

**Falsifiability mutants (one per mechanism, disposable):**
1. Remove the `RevealSourceNode` emission → single-click controller test fails
2. Remove `mapController.centerNode` from `SourceNavigation.open` → navigation test fails
3. Count map root frames as nodes → projection/window counting tests fail
4. Silent path emits the status message → window silent-path test fails

## Preserved invariants

- Double-click/Enter `OpenSourceNode` gesture, its messages, and its accessibility path (`activateAccessible`, `AccessibleGraphCanvasShould`).
- Arrow-key traversal, Tab order, and accessibility selection emit only `ChangeSelection` — no navigation.
- Graph selection visuals, hover, search matches, dim-unrelated, connection preview, pins, and pan/zoom gestures unchanged.
- Read-only mode: navigation allowed, mutations still gated (unchanged).
- `SourceNavigation` lease/availability/traversal semantics; no ID assignment during resolution; no save hooks.
- Boundary rendering, layout, hull geometry, tiering, and workspace persistence untouched.

## Known limits

- Cold-view centering fallback (see Error handling): if a source map has no displayed view yet, centering is best-effort; the node is still revealed and scrolled into view. Revisited only if observed in practice.
- The 2,000-node warning threshold now applies to group-marked boundary count (the visible entity count), consistent with the reduced projection.
