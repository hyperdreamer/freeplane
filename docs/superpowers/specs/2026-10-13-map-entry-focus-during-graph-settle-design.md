# Map-Entry Focus Persistence During Graph Settle — Design

- Date: 2026-10-13
- Topic slug: `map-entry-focus-during-graph-settle`
- Status: Approved design, revision 3 (user-approved 2026-10-13; refined through two design-review passes)
- Scope: bundled plugin `freeplane_plugin_graph` only; no Freeplane core change
- Supersedes: selection-persistence mechanism in `2026-08-30-active-inactive-maps-spec.md` §4.2.1–4 (behavior contract retained and made authoritative here)

## Background

The Graph Workspace window shows a "Maps" sidebar listing active and inactive maps as two
`JList`s (`MapListPanel`). The selected map row is the focus for the row actions
("Add Map" / "Deactivate Map" / "Reactivate Map" / "Delete Map" / "Retry Map" / "Locate Map"),
the Maps menu items, and the enablement of those controls.

While the layout settle loop is still adjusting node positions, every frame is delivered to
`GraphWorkspaceWindowModel.acceptCanvasState(...)`, which calls `updateMapRows(state)` and then
`MapListPanel.setRows(rows)`. A reported defect: a clicked map entry immediately loses its
focus while the graph is settling and only becomes stable after the layout stops.

### Confirmed defects and evidence

`MapListPanel.setRows(List<MapRow>)` clears `activeModel` and `inactiveModel` first, and only
then computes the selection target from `selectedRow()`:

```java
activeModel.clear();
inactiveModel.clear();
...
final MapReferenceId targetSelection = pendingSelectionId != null
    ? pendingSelectionId : (selectedRow() != null ? selectedRow().mapReferenceId() : null);
```

Clearing a `DefaultListModel` clears the `JList` selection synchronously, so the
"currently selected `MapReferenceId`" required by the existing specification
(`2026-08-30-active-inactive-maps-spec.md` §4.2.4) is always `null` at capture time. A row is
then re-selected only when an incoming `MapRow.selected()` flag is true — a flag derived
exclusively from the graph node selection in `GraphWorkspaceWindowModel.updateMapRows(...)`.
Consequently every neutral rebuild drops a directly clicked row.

Five disposable headless probe assertions were run against the pinned baseline `9b8259bcd1`
(evidence archived in the PM run's `$STATE_ROOT/reports/root-cause-probe.txt` and
`$STATE_ROOT/reports/sibling-bugs-probe.txt`, probe sources alongside as `*.java.evidence`).
Four defect assertions failed before any fix; the fifth — map-list scroll position across a
neutral rebuild — passed and is positive evidence for the unaffected-components claim below.

| # | Observed defect | Probe |
| --- | --- | --- |
| 1 | A clicked row (active or inactive) loses focus on the next neutral rebuild; its buttons and menu items disable until the graph stops updating | `activeRowSelectionSurvivesNeutralRefresh`, `inactiveRowSelectionSurvivesNeutralRefresh` |
| 2 | A clicked row snaps back to the row of the graph-selected node's map on the next neutral rebuild | `clickedRowMustNotSnapBackToStaleGraphSelectionDuringRefresh` |
| 3 | A deactivate/reactivate focus is consumed by an interleaved settle frame (`pendingSelectionId = null`) and is lost when the partition transition lands | `deactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` |
| 4 | `updateButtons()` and `updateMenuEnablement()` derive from `selectedRow()`, so defect 1–3 also disable "Deactivate Map", "Retry Map", "Locate Map", "Delete Map" and the corresponding Maps menu items | code path; no separate probe |

The map-list scroll position, workspace settings controls, sidebar width/collapse, canvas
viewport and selection paint state, search field, toolbar state, and the contributor
inspector were audited along the same `acceptCanvasState` path and are **not** affected:
scroll position survived a neutral rebuild in the passing probe; every other component either
persists its own state or is recomputed from the current frame (store commands complete
synchronously on the EDT, so `refreshPresentation()` never observes a stale presentation, and
`canvas.setCanvasState(...)` keeps the viewport and paint selection).

## Goals

1. A clicked map row keeps its focus — highlight, enabled action buttons, enabled Maps menu
   items, and the selected row returned by `MapListPanel.selectedRow()` — across every neutral
   rebuild while a row with the same `MapReferenceId` exists in either list.
2. Focus follows a map that moves between the active and inactive partitions.
3. Graph selection semantics are unchanged: selecting a node focuses the row of that node's
   map; clearing the graph selection (empty canvas click, ESC) or selecting an enclosure
   clears the row focus. Deleting the focused map's row clears that focus; when another row
   carries the graph-selected flag, the same rebuild falls through to it (baseline parity).
4. One selection mechanism: no parallel legacy path remains.
5. No visual or layout change; no change to the canvas, projection, layout, or persistence
   behavior.

## Non-Goals

- No change to the layout settle loop, projection, geometry, or canvas rendering.
- No change to the graph node selection model, its intents, or the pin behavior.
- No change to the interaction controller's arrow-key path that clears only the canvas
  visual selection (documented below as an unchanged baseline divergence).
- No change to the map list row layout, renderers, rail, or sidebar behavior.
- No new action, command, or persisted state.
- No change to map-list scroll position or the other audited components.

## Design

### Behavior contract

`MapListPanel` owns exactly one focus value: the `MapReferenceId` of the row selected in
either list. Row-model rebuilds pass through `setRows`; programmatic selection mutation from
the panel API (`setRows`, `selectMap(id)`, `clearSelection()`) routes through one private
`applySelection(MapReferenceId)` routine that accepts a nullable id, where `null` means
"clear both lists". The two user-driven `ListSelectionListener` callbacks keep clearing the
sibling list directly, exactly as in the baseline. The focus survives a rebuild iff `setRows`
captures it before clearing the models and re-applies it after repopulating them.

Selection priority inside `setRows` becomes:

1. The `MapReferenceId` captured from `selectedRow()` **before** the models are cleared, when
   a row with that id exists in the incoming rows.
2. The first incoming `MapRow` whose `selected()` flag is true, consulted **in the same
   `setRows` call** whenever priority 1 yields no match: no captured id, or the captured map
   is absent from the incoming rows (for example after a deletion). `selectedNode` is `null`
   at window construction, so this is not an initial-population path; it re-asserts the
   current graph selection on rebuilds where no captured focus survives.
3. No rows match: both lists stay cleared.

The incoming flag is still computed by `GraphWorkspaceWindowModel.updateMapRows(...)` from
`selectedNode`, so `rows()` keeps exposing the focus state. A surviving captured focus
(priority 1) always wins over the flag, which removes defect 2 and prevents a graph-selected
map row from stealing focus back from a clicked row. The same-call fall-through to priority 2
is required for baseline parity on the deletion frame: `deleteSelected()` clears the pending
target, and the baseline selects the first flagged row immediately rather than one frame
later. Priority 2 is retained rather than removed with the explicit seam because it is the
only rebuild path that follows `selectedNode` when no focus is held; it is a graph-driven
fallback, not a parallel legacy mechanism.

### Explicit graph-selection application

`GraphWorkspaceWindowModel.handleIntent(GraphIntent.ChangeSelection)` is the only place where
the window's model of the graph selection (`selectedEndpoint` / `selectedNode`) changes. It
already distinguishes a node selection (`selectedNode != null`) from every other selection.
After `updateMapRows(currentState)` it applies the graph selection to the panel explicitly:

- node selection → `mapList.selectMap(selectedNode.mapReferenceId())`;
- otherwise → `mapList.clearSelection()` (a new package-private panel method that clears both
  lists and synchronizes the row flags and listeners).

This keeps the pinned semantics of `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` and
`boundarySelectionDoesNotPinOrSelectAMapRow` without the panel having to infer an
authoritative selection from row flags. Neutral callers — `acceptCanvasState(...)` and
`setReadOnlyOnEdt(...)` — keep calling plain `updateMapRows(...)`, which now preserves focus.

`MapListPanel.selectMap(id)`, the new `clearSelection()`, and the final selection application
of `setRows` all route through one private `applySelection(MapReferenceId)` routine; the id is
nullable and `null` means "clear both lists". The routine clears both lists, sets at most one
index, and runs `synchronizeSelection()` once, so programmatic `JList` selection mutation
exists in a single place (Goal 4). `selectMap(id)` itself keeps rejecting a null argument.

A pre-existing canvas divergence is out of scope and unchanged:
`GraphInteractionController`'s arrow-key handling clears only the canvas visual selection with
`setSelectionVisual(null)` when the selected endpoint is absent from `GraphTraversalOrder`, or
when `state == null`, without emitting `ChangeSelection(empty)`. The model selection and the
row focus therefore persist in that path; no command is added and no behavior is altered.

### Removal of `pendingSelectionId`

`pendingSelectionId` existed only to re-select a row across a partition transition. With
capture-before-clear it is subsumed: `setRows` searches both lists for the captured id, so a
map that moved active→inactive or inactive→active is selected in its new list. The field,
its assignments in `deactivateSelected()`/`reactivateSelected()`/`deleteSelected()`, and its
branch in `setRows` are removed. Two behavior differences are intentional and documented:

- An interleaved neutral rebuild no longer consumes the pending target (defect 3).
- A later row click before the transition lands wins over the earlier action target; the
  user's latest focus is authoritative.

Deletion still clears the deleted map's focus because the captured id matches no row. In the
same `setRows` call, another row carrying the graph-selected flag can take the focus (baseline
parity, priority 2); deleting any other row leaves the held focus on the surviving row. This
makes the present document authoritative over `2026-08-30-active-inactive-maps-spec.md`
§4.2.3 for the delete case.

### Selection synchronization guard

`updatingSelection` continues to gate the `ListSelectionListener` callbacks and
`synchronizeSelection()` continues to rewrite the row flags and notify the window's
`updateMenuEnablement()` listener after each rebuild. No new listener or thread is
introduced; all changes stay on the EDT.

### Mockups

This work changes no pixels: the selected/unselected row rendering, the rail, the buttons,
and the layout are untouched. The user explicitly confirmed that no visual mockup applies
(2026-10-13, design approval), and no mockup artifact is produced.

## Error Handling and Edge Cases

| Case | Behavior after the fix |
| --- | --- |
| Focused row disappears (map deleted) | The captured id matches no row, so the same `setRows` call falls through to priority 2 and focuses the current graph-selected node's map row when one is flagged (baseline parity) |
| Map moves active↔inactive | Focus follows the captured id into the new list |
| Graph selection changes between a deactivate/reactivate action and its transition | The graph selection wins; the earlier action target no longer overrides it (new, intentional) |
| Any command rebuild (add/locate/retry/pin/…) | A held click focus is preserved; no command clears it implicitly |
| Arrow-key clear of a vanished canvas selection | Model and row focus persist; only the canvas visual selection is cleared (unchanged baseline divergence) |
| Map unloads/reloads (availability change) | Row remains present and keeps focus; state label updates |
| `selectMap(id)` for an absent id | All lists cleared (unchanged) |
| Read-only toggle | Focus preserved (new: this neutral rebuild now keeps a clicked row, which the baseline cleared); mutating controls disabled (unchanged) |
| Empty rows list | Focus cleared |
| Re-entrant selection events | `updatingSelection` guard preserved |
| Node selected in a map, then a different row clicked, then a settle frame | Clicked row keeps focus; node's row does not steal it back |
| Graph selection cleared or enclosure selected | Row focus cleared |

## Test Strategy

### Red-before-fix regression tests

The three confirmed defects become pinned tests in a new panel-level class
`org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould` (following the
`MapListPanelShould` fixture conventions), all failing against the current code:

1. `activeRowSelectionSurvivesNeutralRefresh` and `inactiveRowSelectionSurvivesNeutralRefresh`
   — `setRows(sameRows)` must not clear `selectedRow()`.
2. `clickedRowMustNotSnapBackToStaleGraphSelectionDuringRefresh` — a later rebuild carrying an
   unrelated `selected()` flag must not move focus off the clicked row.
3. `deactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` — a neutral
   rebuild between the action and the transition must not consume the target; the transition
   frame must move focus to the same map's row in the inactive list (assert id and partition).
4. `reactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` — mirror of
   item 3 for the inactive-to-active direction.
5. `laterRowClickWinsOverAnEarlierDeactivateTarget` — select A, deactivate A, then click B
   before the transition lands; B stays focused (baseline kept A; intentional change).

### Window-model tests (`GraphWorkspaceWindowModelShould`)

Load-bearing seam tests. They pass on the pinned baseline and on the fixed tree; their red
state is against a capture-only mutant that omits the explicit graph-selection application,
which is the half of the fix they protect:

- `graphSelectionOverridesAClickedMapRow` — `selectMap(B)`; `acceptIntent(ChangeSelection(node in A))`; assert `selectedRow()` is A with row flags and Maps menu enablement following.
- `emptyGraphSelectionOverridesAClickedMapRow` — same pre-state; `ChangeSelection(empty)`; assert no row focus and disabled map menu items.
- `enclosureGraphSelectionOverridesAClickedMapRow` — same pre-state; `ChangeSelection(enclosure)`; assert no row focus.

Neutral-caller, read-only, and enablement tests:

- `neutralCanvasUpdatePreservesAClickedRowAndItsActions` — `selectMap(id)` + `acceptCanvasState(...)` keeps `selectedRow()`, the row `selected()` flag, button labels/enablement, and Maps menu enablement.
- `readOnlyTogglePreservesTheClickedRowAndOnlyDisablesItsActions` — `selectMap(id)`; `setReadOnly(true)`; focus retained, row state `READ_ONLY`, mutating buttons and menu items disabled.
- `clearingTheGraphSelectionDisablesTheMapMenuItems` — after `clearSelection()`, `deactivate-map`, `delete-map`, `retry-map`, and `locate-map` are disabled and no row is flagged.

Seam-test falsifiability procedure (required by the plan):

- Implement the panel half first (capture-before-clear + `applySelection`), run the three
  seam tests against that capture-only tree, and archive their failure output; then add the
  `handleIntent(ChangeSelection)` application and re-run them green. This converts the
  described capture-only mutant into archived evidence rather than an assertion in prose.

Baseline-parity fall-through test:

- `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow` — focus an inactive map B while row A carries the graph-selected flag; delete B; rebuild with only A present; A is focused in the same call.

Existing graph-selection tests stay green: node selection focuses the map row; empty
  selection clears it; boundary selection selects no row; delete clears; deactivate/reactivate
  preserve the row across the transition.

### Compatibility

- `MapListPanelShould` full class (partitioning, mutual exclusion, button swap, preservation,
  delete prompt, width tracking, renderer) stays green after `pendingSelectionId` removal.
- `GraphWorkspaceWindowModelShould` full class stays green.

### Running-application acceptance evidence

Per the approved evidence choice, start Freeplane on a virtual display with a multi-map
workspace (matching the reported scenario: active maps plus one inactive map), then produce
evidence that is falsifiable about the click landing mid-settle:

1. Trigger a layout change and record two canvas captures roughly one second apart whose node
   positions differ — proving the settle loop was still publishing frames — with the status
   bar showing the settling layout state (`OperationalStatus.SETTLING` is published while
   `!frame.idle().idle()` and rendered from `graph_workspace.layout_status.settling`).
2. Click a map entry while that settling status is still displayed.
3. Capture the row highlight and the enabled action buttons immediately after the click, while
   the status still shows settling and the canvas capture again differs from the previous one.
4. Capture the same row after the status returns to idle/empty.

A capture sequence in which the click cannot be shown to have landed during settle does not
count as evidence. Evidence is archived before the delivery gate.

## Files

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Tests under `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/`

## Risks

- A capture-before-clear that only scans the previously selected list would fail on
  active↔inactive transitions; both lists must be searched (the current code already scans
  both).
- The graph-selection application must stay in
  `GraphWorkspaceWindowModel.handleIntent(ChangeSelection)`, not in
  `GraphWorkspaceWindowModel.updateMapRows(...)`, or neutral frames would clear a user's focus.
- Removing `pendingSelectionId` must not regress the two pinned preservation tests; they are
  part of the compatibility gate.
- Priority 2 must be consulted in the same `setRows` call when the captured row is gone, or
  the deletion frame would differ from baseline; the fall-through is pinned by
  `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow`.
