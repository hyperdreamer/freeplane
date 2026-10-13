# Map-Entry Focus Persistence During Graph Settle — Technical Specification

- Date: 2026-10-13
- Topic slug: `map-entry-focus-during-graph-settle`
- Approved design (immutable input): `docs/superpowers/specs/2026-10-13-map-entry-focus-during-graph-settle-design.md`
- Authority: this document is authoritative over `2026-08-30-active-inactive-maps-spec.md`
  §4.2.1–4 for the rebuild, deactivate/reactivate, and delete selection cases. The behavior
  contract of those sections is retained here.
- Module: `freeplane_plugin_graph` only; no Freeplane core change
- Code baseline: this worktree at commit `68f9c66285`; every `file:line` reference below is
  to that commit. The two green-baseline classes were re-verified on 2026-10-13 with
  `gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.MapListPanelShould"
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull`
  (Gradle 9.0.0, JDK `~/.sdkman/candidates/java/21.0.8-zulu`): `BUILD SUCCESSFUL`.
- Status: implementation-ready

## 1. Scope, Terminology, and Non-Goals

### 1.1 Scope

1. Make `MapListPanel.setRows(List<MapRow>)` capture the focused `MapReferenceId` **before**
   clearing the list models, and re-apply it after repopulating, with the priority rules of
   Section 3.1. This preserves a clicked row's focus across every neutral rebuild.
2. Move every programmatic `JList` selection mutation into one private
   `MapListPanel.applySelection(MapReferenceId)` routine. `selectMap(MapReferenceId)` keeps
   its signature and null rejection; a new package-private `clearSelection()` is added.
3. Delete the `pendingSelectionId` field and all its assignments and branches; the capture
   mechanism subsumes it.
4. Add the explicit graph-selection application in
   `GraphWorkspaceWindowModel.handleIntent(GraphIntent.ChangeSelection)`: node selection calls
   `mapList.selectMap(selectedNode.mapReferenceId())`, every other selection calls
   `mapList.clearSelection()`. The call is placed after `updateMapRows(currentState)` and
   before `updateStatusBar()`.
5. Leave `GraphWorkspaceWindowModel.updateMapRows(CanvasState)` row-flag computation
   unchanged.
6. Add the regression tests of Section 6, prove the three seam tests against the
   capture-only mutant, and run the full module suite.

### 1.2 Terminology (normative)

| Term | Definition |
| --- | --- |
| **focus** | The `MapReferenceId` of `MapListPanel.selectedRow()`, i.e. the map row selected in `activeList` or `inactiveList`. At most one of the two lists has a selected index. `null` means "no focus". |
| **captured selection** | The value of `selectedRow()` read at the top of `setRows` **before** `activeModel.clear()` / `inactiveModel.clear()` run, converted to a `MapReferenceId` (`null` when nothing is selected). |
| **flagged row** | An incoming `MapRow` whose `selected()` flag is `true`. In production the flag is computed exclusively by `updateMapRows` from `GraphWorkspaceWindowModel.selectedNode` (line 1460), so at most one incoming row is flagged per call because `accumulators` is keyed by `MapReferenceId`. |
| **neutral rebuild** | Any `updateMapRows(...)` call that does not explicitly apply a graph selection: the `acceptCanvasState(...)` path (line 1128), the `setReadOnlyOnEdt(...)` path (line 1169), and every command-driven canvas-state publication that reaches `acceptCanvasState`. |
| **explicit graph-selection application** | The `selectMap(...)` / `clearSelection()` call added to `handleIntent(GraphIntent.ChangeSelection)` after `updateMapRows(currentState)` (Section 2.6). |
| **graph selection** | The model pair `selectedEndpoint` / `selectedNode`, changed only in `handleIntent(GraphIntent.ChangeSelection)` (lines 1504–1515). |
| **partition** | `MapPartition.ACTIVE` or `MapPartition.INACTIVE`, derived in `updateMapRows` solely from `MapAvailability.INACTIVE` versus every other availability (lines 1461–1462). |
| **applySelection** | The single private routine that clears both `JList` selections and sets at most one index, then runs `synchronizeSelection()` exactly once (Section 2.3). |

### 1.3 Non-Goals

- No change to the layout settle loop, projection, geometry, or canvas rendering.
- No change to the graph node selection model, its intents, or pin behavior.
- No change to `GraphInteractionController`'s arrow-key path that clears only the canvas
  visual selection (Section 5.3 documents it as an unchanged baseline divergence).
- No change to map-list row layout, renderers, rail, or sidebar behavior.
- No new action, command, or persisted state.
- No change to map-list scroll position or to the other audited components (workspace
  settings controls, sidebar width/collapse, canvas viewport and paint state, search field,
  toolbar, contributor inspector).
- No Freeplane core change, no workspace-format change, no translation change.

### 1.4 Constraints

- Java 8 source/target (`build.gradle:90–91`); production code uses Java 8 syntax and 4-space
  indentation, UTF-8 encoding.
- Production diff is limited to
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
  and `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`.
- No new field, listener, thread, or public API. All new members are private, except the new
  package-private `clearSelection()`.
- All production call paths stay on the EDT exactly as today: `acceptCanvasState` and
  `setReadOnlyOnEdt` are invoked through `GraphWorkspaceWindow.runOnEdt`; `handleIntent` is
  invoked from `GraphInteractionController` (AWT/EDT) or from the package-private
  `acceptIntent` test seam. No synchronization primitive is added.

### 1.5 File Inventory

| Action | File |
| --- | --- |
| Modify (production) | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java` |
| Modify (production) | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java` |
| Add (tests) | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java` |
| Modify (tests) | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` |
| Add (evidence, not committed) | `$STATE_ROOT/reports/capture-only-mutant-seam-tests.txt` (Section 6.4) |

## 2. Exact Production Changes

### 2.0 Baseline code sites being modified

**(a) `MapListPanel.java:179` — field to delete:**

```java
    private MapReferenceId pendingSelectionId;
```

**(b) `MapListPanel.java:314–322` — the defect: models are cleared before the focus is read,
and the pending id overrides the captured focus:**

```java
        updatingSelection = true;
        try {
            activeModel.clear();
            inactiveModel.clear();
            int selectedActiveIndex = -1;
            int selectedInactiveIndex = -1;

            final MapReferenceId targetSelection = pendingSelectionId != null
                ? pendingSelectionId : (selectedRow() != null ? selectedRow().mapReferenceId() : null);
```

**(c) `MapListPanel.java:350–367` — selection application and the pending reset:**

```java
            if (selectedActiveIndex >= 0) {
                activeList.setSelectedIndex(selectedActiveIndex);
                inactiveList.clearSelection();
            }
            else if (selectedInactiveIndex >= 0) {
                inactiveList.setSelectedIndex(selectedInactiveIndex);
                activeList.clearSelection();
            }
            else {
                activeList.clearSelection();
                inactiveList.clearSelection();
            }
            pendingSelectionId = null;
        }
        finally {
            updatingSelection = false;
        }
        synchronizeSelection();
```

**(d) `MapListPanel.java:378–403` — `selectMap`, to be replaced by the four methods of
Sections 2.3–2.4:**

```java
    void selectMap(final MapReferenceId mapReferenceId) {
        Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        updatingSelection = true;
        try {
            for (int i = 0; i < activeModel.size(); i++) {
                if (mapReferenceId.equals(activeModel.get(i).mapReferenceId())) {
                    activeList.setSelectedIndex(i);
                    inactiveList.clearSelection();
                    return;
                }
            }
            for (int i = 0; i < inactiveModel.size(); i++) {
                if (mapReferenceId.equals(inactiveModel.get(i).mapReferenceId())) {
                    inactiveList.setSelectedIndex(i);
                    activeList.clearSelection();
                    return;
                }
            }
            activeList.clearSelection();
            inactiveList.clearSelection();
        }
        finally {
            updatingSelection = false;
            synchronizeSelection();
        }
    }
```

**(e) `MapListPanel.java:473–497` — pending assignments to delete** (the surrounding
predicates stay unchanged):

```java
    void deactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.ACTIVE) {
            pendingSelectionId = row.mapReferenceId();            // delete this line
            execute(GraphCommands.removeMap(row.mapReferenceId()));
        }
    }

    void reactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            pendingSelectionId = row.mapReferenceId();            // delete this line
            execute(GraphCommands.reactivateMap(row.mapReferenceId()));
        }
    }

    void deleteSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            if (deletePrompt.confirmDelete(this, row.displayName())) {
                pendingSelectionId = null;                        // delete this line
                execute(GraphCommands.deleteMap(row.mapReferenceId()));
            }
        }
    }
```

**(f) `GraphWorkspaceWindow.java:1504–1515` — the `ChangeSelection` branch to extend:**

```java
        if (intent instanceof GraphIntent.ChangeSelection) {
            final Optional<ProjectedEndpointKey> selection = ((GraphIntent.ChangeSelection) intent).selection();
            selectedEndpoint = selection.orElse(null);
            selectedNode = selection.isPresent() && selection.get().isNode()
                ? selection.get().node().get() : null;
            paintState = selection.isPresent() ? paintState.withSelection(selection.get())
                : GraphPaintState.empty().withSearchMatches(
                    GraphSearchModel.search(currentState, toolbar.searchField().getText()));
            canvas.setPaintState(paintState);
            updateMapRows(currentState);
            updateStatusBar();
        }
```

### 2.1 Field removal in `MapListPanel`

Delete `private MapReferenceId pendingSelectionId;` (baseline line 179). Do not add or
rename any other field. `updatingSelection` (baseline line 182), `rows` (line 180), and
`readOnly` (line 181) are unchanged.

### 2.2 `MapListPanel.setRows` replacement

Replace the whole method (baseline lines 307–368) with exactly:

```java
    void setRows(final List<MapRow> values) {
        Objects.requireNonNull(values, "rows");
        final List<MapRow> copy = new ArrayList<MapRow>(values.size());
        for (final MapRow row : values) {
            copy.add(Objects.requireNonNull(row, "row"));
        }
        rows = Collections.unmodifiableList(copy);
        // Capture the focus before clearing the models: DefaultListModel.clear()
        // clears the JList selection synchronously.
        final MapRow previousFocus = selectedRow();
        final MapReferenceId capturedSelection = previousFocus == null ? null : previousFocus.mapReferenceId();
        updatingSelection = true;
        try {
            activeModel.clear();
            inactiveModel.clear();
            for (final MapRow row : rows) {
                if (row.partition() == MapPartition.ACTIVE) {
                    activeModel.addElement(row);
                }
                else {
                    inactiveModel.addElement(row);
                }
            }
            activeHeader.setText(TextUtils.format("graph_workspace.map_list.active_heading", activeModel.size()));
            inactiveHeader.setText(TextUtils.format("graph_workspace.map_list.inactive_heading", inactiveModel.size()));
            syncListWidths();
        }
        finally {
            updatingSelection = false;
        }
        applySelection(targetSelection(capturedSelection));
    }
```

Mandatory ordering and structure:

1. `Objects.requireNonNull(values, "rows")` and the element-wise
   `Objects.requireNonNull(row, "row")` copy loop stay first, exactly as in the baseline. A
   null element throws before `rows` is reassigned and before any model or `JList` mutation.
2. `rows` is assigned the unmodifiable copy before the capture.
3. The capture `final MapRow previousFocus = selectedRow();` (and the derived
   `capturedSelection`) is the first read of `selectedRow()` in the method and must happen
   **before** `activeModel.clear()`. Whether it precedes or follows the `rows` assignment is
   immaterial because `selectedRow()` reads only the two `JList`s.
4. Both models are cleared and repopulated inside one `updatingSelection = true` window
   restored in `finally`; the headers and `syncListWidths()` stay inside that window. No
   `JList` selection index is touched in this window.
5. `setRows` must not call `synchronizeSelection()` itself. Exactly one
   `applySelection(targetSelection(capturedSelection))` call is made after the guard window,
   and that routine is the only caller of `synchronizeSelection()` for this method.
6. `setRows` no longer reads or writes `pendingSelectionId` anywhere.

Effective target resolution (`targetSelection`, defined in Section 2.3) implements the
priority rules of Section 3.1.

### 2.3 `MapListPanel.applySelection` and `targetSelection`

Add immediately after `selectMap`/`clearSelection` (Section 2.4) and before `selectedRow()`
(baseline line 405), with exactly:

```java
    private void applySelection(final MapReferenceId mapReferenceId) {
        updatingSelection = true;
        try {
            activeList.clearSelection();
            inactiveList.clearSelection();
            if (mapReferenceId == null) {
                return;
            }
            for (int i = 0; i < activeModel.size(); i++) {
                if (mapReferenceId.equals(activeModel.get(i).mapReferenceId())) {
                    activeList.setSelectedIndex(i);
                    return;
                }
            }
            for (int i = 0; i < inactiveModel.size(); i++) {
                if (mapReferenceId.equals(inactiveModel.get(i).mapReferenceId())) {
                    inactiveList.setSelectedIndex(i);
                    return;
                }
            }
        }
        finally {
            updatingSelection = false;
            synchronizeSelection();
        }
    }

    private MapReferenceId targetSelection(final MapReferenceId capturedSelection) {
        if (capturedSelection != null) {
            for (final MapRow row : rows) {
                if (capturedSelection.equals(row.mapReferenceId())) {
                    return capturedSelection;
                }
            }
        }
        for (final MapRow row : rows) {
            if (row.selected()) {
                return row.mapReferenceId();
            }
        }
        return null;
    }
```

`applySelection` contract:

- Accepts `null`; `null` clears both lists and selects nothing.
- Clears `activeList` and `inactiveList` first, then sets at most one index. The active model
  is scanned before the inactive model; the first matching index in the scanned model wins,
  and the routine returns immediately after setting it. No match leaves both lists cleared.
- Every `JList` mutation happens inside a `updatingSelection = true` window restored in
  `finally`, so the two `ListSelectionListener` callbacks never run their
  mutual-exclusion bodies for programmatic mutations.
- `synchronizeSelection()` runs exactly once per invocation, on every return path and on
  every exception path, in the `finally` after `updatingSelection` is restored to `false`.
  This matches the baseline `selectMap` finalization.
- `applySelection` does not read `rows` and does not compare `MapRow.selected()` flags. It is
  a pure "select this id or clear" routine; priority 2 lives in `targetSelection`, which is
  called only by `setRows`.
- The method must only be called after the models reflect the incoming rows (after the
  `setRows` rebuild, or, for `selectMap`/`clearSelection`, with the current models).
  Production callers: `setRows`, `selectMap`, `clearSelection` only.

`targetSelection` contract (private, called only by `setRows`):

1. If `capturedSelection != null` and some row in `rows` has that `MapReferenceId`, return
   `capturedSelection` (priority 1). A flagged row cannot win over it.
2. Otherwise return the `MapReferenceId` of the first row in incoming `rows` order whose
   `selected()` flag is true (priority 2).
3. Otherwise return `null` (priority 3).

Because `updateMapRows` produces at most one flagged row (Section 1.2), priority 2 picks "the
graph-selected row" in production; for manually supplied rows with multiple flags, incoming
order is authoritative.

### 2.4 `MapListPanel.selectMap` and `MapListPanel.clearSelection`

Replace the whole `selectMap` method (baseline lines 378–403) with exactly these two methods,
placed where `selectMap` was:

```java
    void selectMap(final MapReferenceId mapReferenceId) {
        applySelection(Objects.requireNonNull(mapReferenceId, "mapReferenceId"));
    }

    void clearSelection() {
        applySelection(null);
    }
```

- `selectMap` keeps the `Objects.requireNonNull(mapReferenceId, "mapReferenceId")`
  rejection. The exception is thrown before any model or `JList` access.
- `selectMap` routes through the single `applySelection` routine; it must contain no loop,
  no direct `setSelectedIndex`, and no direct `clearSelection` call.
- `clearSelection()` is package-private, takes no argument, and is the only production entry
  point for "clear the row focus"; it routes through `applySelection(null)`.

### 2.5 Removal of `pendingSelectionId`

Exact edits:

1. Delete the field declaration (Section 2.0(a)).
2. Delete the assignment in `deactivateSelected` (Section 2.0(e)).
3. Delete the assignment in `reactivateSelected` (Section 2.0(e)).
4. Delete the assignment in `deleteSelected` (Section 2.0(e)).
5. Delete the `targetSelection` ternary and the `pendingSelectionId = null;` reset as part of
   the `setRows` replacement (Section 2.2).

Resulting behavior differences (intentional, from the approved design):

- An interleaved neutral rebuild no longer consumes the action target. Baseline: the first
  `setRows` after a deactivate/reactivate selected the map via the pending id and then set
  `pendingSelectionId = null`, so the partition-transition frame lost the focus. After the
  fix the captured id survives every intervening neutral rebuild and is re-applied on the
  transition frame in the map's new list.
- A later row click before the transition lands wins over the earlier action target.
  Baseline: `selectMap(otherId)` did not clear `pendingSelectionId`, so the next `setRows`
  snapped focus back to the earlier deactivate/reactivate target. After the fix the captured
  focus is the user's latest click.
- Deletion still clears the deleted map's focus because the captured id matches no incoming
  row; in the same `setRows` call, priority 2 may focus the row carrying the graph-selected
  flag (baseline parity), and deleting any other row leaves the held focus on the surviving
  row.

### 2.6 `GraphWorkspaceWindowModel.handleIntent(GraphIntent.ChangeSelection)`

In `GraphWorkspaceWindow.java`, replace the `ChangeSelection` branch (baseline lines
1504–1515) with exactly:

```java
        if (intent instanceof GraphIntent.ChangeSelection) {
            final Optional<ProjectedEndpointKey> selection = ((GraphIntent.ChangeSelection) intent).selection();
            selectedEndpoint = selection.orElse(null);
            selectedNode = selection.isPresent() && selection.get().isNode()
                ? selection.get().node().get() : null;
            paintState = selection.isPresent() ? paintState.withSelection(selection.get())
                : GraphPaintState.empty().withSearchMatches(
                    GraphSearchModel.search(currentState, toolbar.searchField().getText()));
            canvas.setPaintState(paintState);
            updateMapRows(currentState);
            if (selectedNode != null) {
                mapList.selectMap(selectedNode.mapReferenceId());
            }
            else {
                mapList.clearSelection();
            }
            updateStatusBar();
        }
```

Mandatory order for every `ChangeSelection`:

1. `selectedEndpoint = selection.orElse(null);`
2. `selectedNode = selection.isPresent() && selection.get().isNode() ? selection.get().node().get() : null;`
3. `paintState = ...;`
4. `canvas.setPaintState(paintState);`
5. `updateMapRows(currentState);`
6. the new node/non-node branch: `mapList.selectMap(selectedNode.mapReferenceId())` when
   `selectedNode != null`, otherwise `mapList.clearSelection()`;
7. `updateStatusBar();`

The explicit application therefore always runs **after** `updateMapRows` and **before**
`updateStatusBar`, so `updateStatusBar`'s `updateMenuEnablement()` observes the applied
selection. `mapList.synchronizeSelection()` also notifies the panel's selection listener
(`updateMenuEnablement`) from inside `updateMapRows`, so enablement is notified three times
in total: the notification inside `updateMapRows` observes the rebuild selection (preserved
focus or priority-2 fallback), the notification caused by the explicit
`selectMap`/`clearSelection` observes the applied graph selection and corrects it, and
`updateStatusBar`'s `updateMenuEnablement()` observes the applied selection. The later
notifications are idempotent corrections of the first, not conflicting writes.

Node versus non-node handling:

- Node selection (`selectedNode != null`, set only when the endpoint `isNode()`): the row of
  that node's map is focused. If no incoming row carries that `MapReferenceId`, `selectMap`
  finds no match and both lists are cleared.
- Empty selection (`Optional.empty()`): `selectedEndpoint` and `selectedNode` become `null`;
  `clearSelection()` clears the focus.
- Enclosure selection (endpoint present but `isNode()` false): `selectedEndpoint` is the
  enclosure, `selectedNode` is `null`; `clearSelection()` clears the focus. The enclosure
  branch never calls `selectMap`.

No other `handleIntent` branch changes.

### 2.7 `GraphWorkspaceWindowModel.updateMapRows(CanvasState)` — unchanged

The method (baseline lines 1432–1477) is not modified. In particular:

- The flag computation stays exactly
  `final boolean isSelected = selectedNode != null && selectedNode.mapReferenceId().equals(accumulator.mapReferenceId);`
  (line 1460).
- The partition and row-state computation (lines 1461–1464), the accumulation by
  `MapReferenceId` (lines 1437–1457), the `mapList.setRows(rows)` call (line 1474),
  `sidebar.setActiveMapCount(activeCount)` (line 1475), and the trailing
  `updateMenuEnablement()` (line 1476) are unchanged.
- No capture, no clear, and no explicit application is added to `updateMapRows`; that is
  precisely what keeps neutral rebuilds focus-preserving.

### 2.8 `updatingSelection` and `synchronizeSelection` invariants

- `updatingSelection` is `true` for every programmatic mutation of `activeModel`,
  `inactiveModel`, `activeList` selection, or `inactiveList` selection; it is restored to
  `false` in a `finally`. The two `ListSelectionListener` bodies run their logic only when
  `!event.getValueIsAdjusting() && !updatingSelection`.
- The two listener bodies are unchanged: active-list events clear `inactiveList` and call
  `synchronizeSelection()`; inactive-list events clear `activeList` and call
  `synchronizeSelection()`. They are the only user-driven selection entry points and keep
  clearing the sibling list directly, exactly as in the baseline.
- `synchronizeSelection()` (baseline lines 538–549) is unchanged. It reads `selectedRow()`,
  rebuilds `rows` with `MapRow.withSelected(...)` flags set only for the row whose
  `MapReferenceId` equals the selected row's id (all `false` when nothing is selected),
  assigns the unmodifiable result to `rows`, calls `updateButtons()`, then notifies every
  registered `selectionListeners` consumer with the selected `MapRow` (nullable) in
  registration order.
- `synchronizeSelection()` is called exactly once per `setRows`, per `selectMap`, and per
  `clearSelection` invocation, via `applySelection`'s `finally`. It is not called by
  `setReadOnly`, `updateButtons`, or `updateMenuEnablement`.
- `updateButtons()` (baseline lines 551–...) is unchanged: it derives every label and
  enablement from `selectedRow()` and `readOnly`.

### 2.9 Production non-changes

Unchanged: `MapRow`, `RowState`, `MapPartition` handling, `rows()`, `addSelectionListener`,
`selectedRow()`, the `activeList`/`inactiveList`/`rowList` accessors, all buttons and their
listeners, `syncListWidths`, `setReadOnly`, `isReadOnly`, `addMapFromChooser`,
`deactivateSelected`/`reactivateSelected`/`deleteSelected` predicates and commands,
`retrySelected`, `locateSelected`, `handleActionButton1/2`, `execute`, `updateButtons`,
`RowRenderer`, `ScrollableListContainer`, the `MapListPanel` constructors and layout, and the
default delete confirmation prompt. In `GraphWorkspaceWindow.java`, unchanged:
`acceptCanvasState`, `setReadOnlyOnEdt`, `updateStatusBar`, `refreshPresentation`,
`updateMenuEnablement`, `executeCommand`, and all intent branches other than
`ChangeSelection`.

## 3. Contract Summary

### 3.1 `MapListPanel.setRows(List<MapRow> values)` — preconditions and postconditions

Preconditions:

- P1. `values` is non-null; every element is non-null. (Enforced with
  `Objects.requireNonNull`.)
- P2. The incoming rows are the complete desired content of both lists. Production callers
  guarantee at most one row per `MapReferenceId`.

Postconditions (target resolution order):

- Q1 (priority 1). If a row was selected in either list before the call **and** an incoming
  row has the same `MapReferenceId`, then after the call exactly that id is focused, in
  whichever incoming partition carries it. For the unique-id inputs guaranteed by P2,
  `rows()` exposes `selected() == true` on that row and `false` on every other row. If a
  manual caller supplies duplicate ids (permitted by §5.1), every row whose id matches the
  focused id is exposed as `selected() == true`, consistent with `synchronizeSelection()`.
  Incoming `selected()` flags cannot override Q1.
- Q2 (priority 2). Otherwise, if any incoming row has `selected() == true`, the first such row
  in incoming `values` order is focused in the same `setRows` call, with the same flag
  postcondition. This applies both when there was no captured focus and when the captured
  id is absent from the incoming rows.
- Q3 (priority 3). Otherwise both lists are cleared and `selectedRow()` is `null`; every
  incoming row's exposed `selected()` flag is `false`.

Additional postconditions:

- The two list models contain exactly the active/inactive partitions of the incoming rows, in
  incoming order, and the active/inactive headers and preferred sizes are refreshed exactly as
  in the baseline.
- `updateButtons()` has run exactly once against the resulting selection, and every
  registered selection listener has been notified exactly once with the resulting selected
  row (or `null`).
- At most one of `activeList.getSelectedIndex()` and `inactiveList.getSelectedIndex()` is
  `>= 0`.
- `setRows` performs no I/O, emits no command, and does not touch `selectedNode` /
  `selectedEndpoint`.

### 3.2 `applySelection(MapReferenceId)` — private contract

- Signature: `private void applySelection(final MapReferenceId mapReferenceId)`.
- Input domain includes `null`. No exception is thrown for any `MapReferenceId`, known or
  unknown.
- Effect: clear both JList selections; if the id is non-null and present, select the first
  match scanning `activeModel` then `inactiveModel`; otherwise leave both cleared.
- Exactly one `synchronizeSelection()` per call, on every path, after the `updatingSelection`
  guard is restored to `false`.
- Ordering: selection mutation happens after the models are in their final state. Callers must
  not invoke `applySelection` between `activeModel.clear()` and the end of the repopulation.

### 3.3 `selectMap` / `clearSelection` contracts

- `void selectMap(final MapReferenceId mapReferenceId)`: throws
  `NullPointerException("mapReferenceId")` for `null` before touching any state; otherwise
  `applySelection(mapReferenceId)`. Selecting an absent id clears both lists. Behavior is
  observationally identical to the baseline `selectMap` for every non-null id.
- `void clearSelection()`: `applySelection(null)`. Clears both lists, rewrites every row flag
  to `false`, refreshes buttons and enablement, and notifies selection listeners with `null`.

### 3.4 `handleIntent(GraphIntent.ChangeSelection)` contract

- The model pair is updated first, `updateMapRows` recomputes rows and flags second, the
  explicit panel application runs third, and `updateStatusBar` runs last (Section 2.6 order).
- For a node selection the panel focus is the node's map row (or none when the map has no
  row). For empty and enclosure selections the panel focus is none.
- The explicit application overrides any focus that `setRows` preserved in the same call; it
  never touches `selectedNode`/`selectedEndpoint`.

### 3.5 Threading and event ordering

- No new thread, executor, listener registration, or lock. The new code runs on the thread
  that already invokes `setRows`/`handleIntent`; production paths are EDT (Section 1.4).
- A single `ChangeSelection` produces this exact sequence on the calling thread:
  `selectedEndpoint`/`selectedNode` write → `paintState` write → `canvas.setPaintState` →
  `updateMapRows` (rows rebuild → `setRows` → `applySelection` → `synchronizeSelection` →
  `updateButtons` → selection listeners → `updateMenuEnablement`) → explicit
  `selectMap`/`clearSelection` (→ `applySelection` → `synchronizeSelection` → `updateButtons`
  → selection listeners → `updateMenuEnablement`) → `updateStatusBar` (→
  `updateMenuEnablement`).

## 4. Focus State Machine / Decision Table

`focus` is the resulting `MapListPanel.selectedRow()` id (`—` means none). "Captured" is as
defined in Section 1.2; "flagged" is as defined in Section 1.2. Events 1–20 do not emit a
`ChangeSelection` intent; events 21–24 do.

| # | Event / trigger | Pre-state and changes | Captured | Effective target | Resulting focus |
| --- | --- | --- | --- | --- | --- |
| 1 | Window construction: `setReadOnlyOnEdt` → `updateMapRows(currentState)` | `selectedNode == null`, `selectedEndpoint == null`; no rows selected | — | no flagged row → priority 3 | — |
| 2 | Row click in `activeList` (user) | Listener clears `inactiveList`, `synchronizeSelection` rewrites flags | n/a | n/a | clicked active row; its `selected()` flag `true`, all others `false` |
| 3 | Row click in `inactiveList` (user) | Mirror of 2 | n/a | n/a | clicked inactive row |
| 4 | `selectMap(id)`, id in active list | `applySelection(id)` | n/a | id | that active row (active index `>= 0`, inactive index `-1`) |
| 5 | `selectMap(id)`, id only in inactive list | `applySelection(id)` | n/a | id | that inactive row |
| 6 | `selectMap(id)`, id absent | `applySelection(id)` | n/a | no match | — |
| 7 | `clearSelection()` | `applySelection(null)` | n/a | null | — |
| 8 | Neutral rebuild (acceptCanvasState / setReadOnlyOnEdt / command publication) with the focused map still present | rows for the same id, possibly different partition/state; flags any | focused id | priority 1 | the same map's row in its current partition; flagged rows cannot steal it |
| 9 | Neutral rebuild after the focused map was deleted, another row flagged | incoming rows omit the focused id; one flagged row | focused (deleted) id | priority 2 | the first flagged row |
| 10 | Neutral rebuild after the focused map was deleted, nothing flagged | incoming rows omit the focused id; no flag | focused (deleted) id | priority 3 | — |
| 11 | Neutral rebuild with no focus and one flagged row | no selection before the call | — | priority 2 | flagged row |
| 12 | Neutral rebuild with no focus and no flagged row | — | — | priority 3 | — |
| 13 | Neutral rebuild with an empty rows list | any pre-state | focused id | priority 3 (no rows) | — |
| 14 | Deactivate action on focused active map A | `deactivateSelected()` executes `RemoveMap(A)`; no pending state. Interleaved neutral frames keep A active | A (if selected) | priority 1 while A is present | A stays focused in `activeList` across interleaved frames |
| 15 | Deactivate transition frame | A arrives in the inactive partition, unflagged | A | priority 1 | A focused in `inactiveList`; buttons become Reactivate/Delete |
| 16 | Reactivate action on focused inactive map A, then transition frame | `reactivateSelected()` executes `ReactivateMap(A)`; interleaved frames keep A inactive | A | priority 1 | A focused in `activeList` after the transition |
| 17 | Delete focused inactive map B, another row flagged by the graph selection | `deleteSelected()` executes `DeleteMap(B)` after confirmation; next frame omits B | B | priority 2 | first flagged row (baseline parity) |
| 18 | Delete focused inactive map B, nothing flagged | as 17 without a flagged row | B | priority 3 | — |
| 19 | Delete a non-focused row | the focused id survives in incoming rows | focused id | priority 1 | unchanged focus |
| 20 | Map availability change with an unchanged id (AVAILABLE ↔ MISSING/RETRYABLE/INACTIVE) | row remains, partition may change | focused id | priority 1 | same map's row; partition and state label follow availability |
| 21 | Read-only on (`setReadOnlyOnEdt(true)`) | neutral rebuild; row states become `READ_ONLY` | focused id | priority 1 | focus preserved; mutating buttons and Maps menu items disabled |
| 22 | Read-only off | neutral rebuild; row states re-derived | focused id | priority 1 | focus preserved; enablement restored |
| 23 | `ChangeSelection(node)` | `selectedNode` = node; `updateMapRows` flags the node's map (setRows may keep a clicked row), then explicit `selectMap(node.mapReferenceId())` | clicked id, if any | explicit id | the node's map row, overriding the clicked row; none if the map has no row |
| 24 | `ChangeSelection(empty)` | `selectedNode = null`; `updateMapRows` unflags all; then explicit `clearSelection()` | clicked id, if any | null (explicit) | — |
| 25 | `ChangeSelection(enclosure)` | `selectedNode = null`; then explicit `clearSelection()` | clicked id, if any | null (explicit) | — |
| 26 | Deactivate A, then click B before the transition frame | `selectMap(B)` changes focus to B before the frame arrives | B | priority 1 | B (the later click wins; A is not re-focused) |
| 27 | Arrow-key clear when the selected endpoint is absent from `GraphTraversalOrder`, or `state == null` | no `ChangeSelection` intent is emitted | n/a | n/a | focus persists unchanged; only the canvas visual selection is cleared (unchanged divergence) |

## 5. Error Behavior and Edge Cases

### 5.1 Exception behavior

| Input / case | Exact expected outcome |
| --- | --- |
| `setRows(null)` | `NullPointerException` with message `"rows"`; no state change. |
| `setRows` list containing `null` | `NullPointerException` with message `"row"`; no model, `JList`, or `rows`-field mutation (the copy loop precedes the `rows` assignment). |
| `setRows(Collections.emptyList())` | No exception; both lists cleared; `selectedRow() == null`; all listeners notified with `null`; buttons recomputed. |
| `selectMap(null)` | `NullPointerException` with message `"mapReferenceId"` thrown before any model/`JList` access. |
| `clearSelection()` | No exception; clears focus. |
| `applySelection(absentId)` (private, via `selectMap`) | No exception; both lists cleared. |
| `acceptIntent(null)` / `handleIntent(null)` | `NullPointerException` with message `"intent"` (unchanged). |
| `new GraphIntent.ChangeSelection(null)` | `NullPointerException` with message `"selection"` (unchanged constructor). |
| Re-entrant selection events during programmatic mutation | Suppressed by the `updatingSelection` guard; listener bodies do not run. |
| `updateMapRows(null)` (private, `currentState == null`) | Rows are built from `binding.currentMapRows()` alone; the focus capture/priority rules run normally; no exception. |
| Incoming rows with duplicate `MapReferenceId`s (only possible from manual test callers) | Deterministic: `targetSelection` uses the first `rows` occurrence; `applySelection` prefers the active-list match over the inactive-list match. |
| Multiple flagged rows (only possible from manual test callers) | Deterministic: the first flagged row in incoming `values` order wins (priority 2). |

### 5.2 Focus edge cases

| Case | Behavior after the fix |
| --- | --- |
| Focused row disappears (map deleted) | Priority 1 finds no match; the same `setRows` call falls through to priority 2 and focuses the flagged graph-selected row when one exists, otherwise clears. |
| Map moves active ↔ inactive | Focus follows the captured id into the new list (events 15/16). |
| Graph selection changes between a deactivate/reactivate action and its transition | The graph selection wins; the earlier action target no longer overrides it, because the pending mechanism is gone. |
| Any command rebuild (add/locate/retry/pin/…) | A held click focus is preserved across the resulting `acceptCanvasState`; no command clears it implicitly. |
| Arrow-key clear of a vanished canvas selection | `GraphInteractionController` calls only `setSelectionVisual(null)` (baseline lines ~411–425); no `ChangeSelection` is emitted, so `selectedNode`/`selectedEndpoint`, the model paint state, and the row focus persist. This is an unchanged baseline divergence and is not "fixed". |
| Map unloads/reloads (availability change) | Row remains present and keeps focus; the state label updates. |
| Node selected in a map, then a different row clicked, then a settle frame | The clicked row keeps focus; the node's map row does not steal it back (priority 1 beats the flag). |
| Graph selection cleared or enclosure selected | Row focus cleared via the explicit `clearSelection()` (events 24/25). |
| Read-only toggle | Focus preserved; `READ_ONLY` row state and disabled mutating controls. |
| Empty rows list | Focus cleared. |
| Window construction with `selectedNode == null` | No initial selection even when the first state carries nodes (no row is flagged). |

## 6. Test Specification

### 6.1 New panel test class `MapListSelectionPersistenceShould`

Create
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java`
with exactly the following content (package `org.freeplane.plugin.graph.window`):

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class MapListSelectionPersistenceShould {
    private static final MapReferenceId ACTIVE_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId OTHER_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId INACTIVE_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000003");

    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController)
            .thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
            .thenAnswer(invocation -> formattedText(invocation));
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    private static MapListPanel panel() {
        return panel(mock(GraphWorkspaceHandle.class));
    }

    private static MapListPanel panel(final GraphWorkspaceHandle handle) {
        return new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);
    }

    private static MapListPanel.MapRow row(final MapReferenceId id, final String name,
            final MapListPanel.RowState state, final MapPartition partition, final int count,
            final boolean selected) {
        return MapListPanel.MapRow.of(id, name, state, partition, count, selected);
    }

    private static String formattedText(final org.mockito.invocation.InvocationOnMock invocation) {
        final Object[] invocationArguments = invocation.getArguments();
        final Object[] formatArguments;
        if (invocationArguments.length == 2 && invocationArguments[1] instanceof Object[]) {
            formatArguments = (Object[]) invocationArguments[1];
        }
        else {
            formatArguments = Arrays.copyOfRange(invocationArguments, 1, invocationArguments.length);
        }
        return invocationArguments[0] + Arrays.toString(formatArguments);
    }

    @Test
    public void activeRowSelectionSurvivesNeutralRefresh() {
        MapListPanel panel = panel();
        List<MapListPanel.MapRow> rows = Arrays.asList(row(ACTIVE_MAP, "Active Map",
            MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 3, false));
        panel.setRows(rows);
        panel.selectMap(ACTIVE_MAP);

        panel.setRows(rows);

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(panel.rows().get(0).selected()).isTrue();
        assertThat(panel.removeButton().isEnabled()).isTrue();
    }

    @Test
    public void inactiveRowSelectionSurvivesNeutralRefresh() {
        MapListPanel panel = panel();
        List<MapListPanel.MapRow> rows = Arrays.asList(row(INACTIVE_MAP, "Inactive Map",
            MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false));
        panel.setRows(rows);
        panel.selectMap(INACTIVE_MAP);

        panel.setRows(rows);

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(0);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(panel.rows().get(0).selected()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("graph_workspace.action.delete_map");
    }

    @Test
    public void clickedRowMustNotSnapBackToStaleGraphSelectionDuringRefresh() {
        MapListPanel panel = panel();
        List<MapListPanel.MapRow> rows = Arrays.asList(
            row(ACTIVE_MAP, "Graph Selected", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 3, true),
            row(OTHER_MAP, "Clicked", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 1, false));
        panel.setRows(rows);
        panel.selectMap(OTHER_MAP);

        panel.setRows(rows);

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(OTHER_MAP);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(1);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    public void deactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = panel(handle);
        List<MapListPanel.MapRow> activeRows = Arrays.asList(row(ACTIVE_MAP, "Active Map",
            MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 3, false));
        panel.setRows(activeRows);
        panel.selectMap(ACTIVE_MAP);

        panel.removeButton().doClick();

        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.RemoveMap.class);

        // Interleaved settle frame: the map is still active and the focus must survive it.
        panel.setRows(activeRows);
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);

        // Partition transition lands: the same map is now inactive.
        panel.setRows(Arrays.asList(row(ACTIVE_MAP, "Active Map",
            MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(0);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    public void reactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = panel(handle);
        List<MapListPanel.MapRow> inactiveRows = Arrays.asList(row(INACTIVE_MAP, "Inactive Map",
            MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false));
        panel.setRows(inactiveRows);
        panel.selectMap(INACTIVE_MAP);

        panel.addButton().doClick(); // Displays "Reactivate Map"

        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.ReactivateMap.class);

        // Interleaved settle frame: the map is still inactive and the focus must survive it.
        panel.setRows(inactiveRows);
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);

        // Partition transition lands: the same map is now active.
        panel.setRows(Arrays.asList(row(INACTIVE_MAP, "Inactive Map",
            MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 2, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    public void laterRowClickWinsOverAnEarlierDeactivateTarget() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = panel(handle);
        List<MapListPanel.MapRow> activeRows = Arrays.asList(
            row(ACTIVE_MAP, "First", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 3, false),
            row(OTHER_MAP, "Second", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 1, false));
        panel.setRows(activeRows);
        panel.selectMap(ACTIVE_MAP);

        panel.removeButton().doClick(); // Deactivate the first map.
        panel.selectMap(OTHER_MAP);     // The later click is the user's latest focus.

        panel.setRows(Arrays.asList(
            row(ACTIVE_MAP, "First", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false),
            row(OTHER_MAP, "Second", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 1, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(OTHER_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(-1);
    }
}
```

### 6.2 Red-before-fix expectations for the panel tests

Run Section 6.1 against the baseline production code (no production change applied). Every
test must fail; the first failing assertion and the baseline-observed value are:

| Test | Baseline outcome (must fail) |
| --- | --- |
| `activeRowSelectionSurvivesNeutralRefresh` | Second `setRows` clears the models before reading the selection, so `selectedRow()` returns `null`; `assertThat(panel.selectedRow()).isNotNull()` fails. |
| `inactiveRowSelectionSurvivesNeutralRefresh` | Same: `selectedRow()` is `null` after the second `setRows`; `isNotNull()` fails. |
| `clickedRowMustNotSnapBackToStaleGraphSelectionDuringRefresh` | Second `setRows` re-selects the flagged row at index 0 (`ACTIVE_MAP`); `assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(OTHER_MAP)` fails with actual `ACTIVE_MAP`. |
| `deactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` | The interleaved-frame assertion passes, but the interleaved `setRows` sets `pendingSelectionId = null`; the transition frame then finds no capture and no flag and clears both lists, so the final `assertThat(panel.selectedRow()).isNotNull()` fails. |
| `reactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` | Mirror of the deactivate test: the interleaved frame consumes the pending id; the transition frame leaves `selectedRow() == null`; `isNotNull()` fails. |
| `laterRowClickWinsOverAnEarlierDeactivateTarget` | `pendingSelectionId` still holds `ACTIVE_MAP` when the transition frame arrives, so the frame focuses `ACTIVE_MAP` in the inactive list; `assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(OTHER_MAP)` fails with actual `ACTIVE_MAP`. |

After the production change (Sections 2.1–2.6), all six tests pass.

### 6.3 New window-model tests in `GraphWorkspaceWindowModelShould`

Add the following seven `@Test` methods and one private helper to
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.
They use only existing private fixture builders and helpers of that class:
`fixture(Viewport, CanvasState, List<MapRegistration>, boolean)`, `Fixture.model()`,
`Fixture.binding`, `Fixture.handle`, `registration(MapReferenceId, String, MapAvailability)`,
`id(long)`, `emptyUnknownXml()`, `nodeState(MapReferenceId, LayoutPoint)`, and
`menuItem(GraphWorkspaceWindowModel, String)`.

Private helper (place with the other private static helpers, e.g. next to `nodeState`):

```java
    private static ProjectedEndpointKey transientNodeEndpoint(final MapReferenceId mapId) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(
            SourceNodeKey.transientPath(mapId, Collections.emptyList())));
    }
```

The three load-bearing seam tests (they pass on the baseline and on the fixed tree; they fail
only against the capture-only mutant of Section 6.4):

```java
    @Test
    public void graphSelectionOverridesAClickedMapRow() {
        MapReferenceId activeA = id(201L);
        MapReferenceId activeB = id(202L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(transientNodeEndpoint(activeA))));

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeA);
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        assertThat(model.mapList().rows().get(1).selected()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void emptyGraphSelectionOverridesAClickedMapRow() {
        MapReferenceId activeA = id(203L);
        MapReferenceId activeB = id(204L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));

        assertThat(model.mapList().selectedRow()).isNull();
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        assertThat(model.mapList().rows().get(1).selected()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "locate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        model.close();
    }

    @Test
    public void enclosureGraphSelectionOverridesAClickedMapRow() {
        MapReferenceId activeA = id(205L);
        MapReferenceId activeB = id(206L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(activeA,
            Collections.singletonList(Integer.valueOf(2))));

        model.acceptIntent(new GraphIntent.ChangeSelection(
            Optional.of(ProjectedEndpointKey.ofEnclosure(boundary))));

        assertThat(model.mapList().selectedRow()).isNull();
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        assertThat(model.mapList().rows().get(1).selected()).isFalse();
        model.close();
    }
```

The neutral-caller and enablement tests:

```java
    @Test
    public void neutralCanvasUpdatePreservesAClickedRowAndItsActions() {
        MapReferenceId activeA = id(207L);
        MapReferenceId activeB = id(208L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);

        model.acceptCanvasState(nodeState(activeA, LayoutPoint.of(5.0, 5.0)));

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeB);
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        assertThat(model.mapList().rows().get(1).selected()).isTrue();
        assertThat(model.mapList().removeButton().getText()).isEqualTo("graph_workspace.action.deactivate_map");
        assertThat(model.mapList().removeButton().isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void readOnlyTogglePreservesTheClickedRowAndOnlyDisablesItsActions() {
        MapReferenceId activeA = id(209L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(activeA, "A", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeA);

        model.setReadOnly(true);

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeA);
        assertThat(model.mapList().rows().get(0).state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();
        assertThat(model.mapList().retryButton().isEnabled()).isFalse();
        assertThat(model.mapList().locateButton().isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "locate-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void clearingTheGraphSelectionDisablesTheMapMenuItems() {
        MapReferenceId activeA = id(210L);
        MapReferenceId inactiveB = id(211L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(inactiveB, "B", MapAvailability.INACTIVE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeA);

        model.mapList().clearSelection();

        assertThat(model.mapList().selectedRow()).isNull();
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        for (MapListPanel.MapRow row : model.mapList().rows()) {
            assertThat(row.selected()).isFalse();
        }
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "locate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        model.close();
    }
```

The baseline-parity fall-through test:

```java
    @Test
    public void deletionFrameFallsThroughToTheFlaggedGraphSelectedRow() {
        MapReferenceId activeA = id(212L);
        MapReferenceId inactiveB = id(213L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(inactiveB, "B", MapAvailability.INACTIVE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(transientNodeEndpoint(activeA))));
        model.mapList().selectMap(inactiveB);
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(inactiveB);

        // The model test owns no DeleteConfirmationPrompt; execute the same command the
        // panel action dispatches and deliver the post-delete registration set.
        model.execute(GraphCommands.deleteMap(inactiveB));
        when(fixture.binding.currentMapRows()).thenReturn(Collections.singletonList(
            registration(activeA, "A", MapAvailability.AVAILABLE)));

        model.acceptCanvasState(nodeState(activeA, LayoutPoint.of(0.0, 0.0)));

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeA);
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(model.mapList().rows().size()).isEqualTo(1);
        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        model.close();
    }
```

Notes and constraints:

- On the pinned baseline `68f9c66285`, the class `GraphWorkspaceWindowModelShould` does not
  compile because `clearingTheGraphSelectionDisablesTheMapMenuItems` calls the
  not-yet-existing `MapListPanel.clearSelection()`. Excluding that one test, the three seam
  tests and `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow` pass on the baseline,
  while `neutralCanvasUpdatePreservesAClickedRowAndItsActions` and
  `readOnlyTogglePreservesTheClickedRowAndOnlyDisablesItsActions` are red (the baseline clears
  the clicked row on the neutral rebuild). All seven tests are green on the fixed tree.
- `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow` must **not** call
  `model.mapList().deleteSelected()`: the model constructs its `MapListPanel` with the
  default `DeleteConfirmationPrompt`, which opens a modal `JOptionPane` on a non-headless
  JVM. Executing `GraphCommands.deleteMap` plus re-stubbing
  `binding.currentMapRows()` delivers the same incoming-row transition deterministically.
- `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow` is the priority-2 (fall-through)
  guard: it fails against a mutant that re-applies only the captured id and never consults
  `selected()` flags.

### 6.4 Capture-only mutant procedure and archived evidence

The three seam tests pass on the baseline and on the fixed tree, so they cannot be shown red
by simply reverting the fix. Their falsifiability is proven against a **capture-only mutant**:
a tree that applies Sections 2.1–2.5 (the whole panel half, including capture-before-clear,
`applySelection`, `clearSelection`, and `pendingSelectionId` removal) but **not** Section 2.6
(no explicit graph-selection application in `handleIntent`).

Procedure:

1. Apply Sections 2.1–2.5 to `MapListPanel.java`. Do not touch
   `GraphWorkspaceWindowModel.handleIntent`.
2. Run exactly:
   ```bash
   export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
   export PATH="$JAVA_HOME/bin:$PATH"
   gradle :freeplane_plugin_graph:test \
     --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.graphSelectionOverridesAClickedMapRow" \
     --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.emptyGraphSelectionOverridesAClickedMapRow" \
     --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.enclosureGraphSelectionOverridesAClickedMapRow" \
     -PTestLoggingFull --rerun-tasks
   ```
3. Expected: **three failures**, one per test, each at the focus assertion:
   - `graphSelectionOverridesAClickedMapRow`: `selectedRow().mapReferenceId()` is `activeB`;
     the assertion expecting `activeA` fails.
   - `emptyGraphSelectionOverridesAClickedMapRow`: `selectedRow()` is `activeB`;
     `assertThat(...).isNull()` fails.
   - `enclosureGraphSelectionOverridesAClickedMapRow`: `selectedRow()` is `activeB`;
     `assertThat(...).isNull()` fails.
   - Corroborating witness (same mutant): the existing
     `GraphWorkspaceWindowModelShould.selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` also
     fails at its `assertThat(model.mapList().rows().get(0).selected()).isFalse()` line after
     the empty `ChangeSelection`, because the capture-only `setRows` keeps the previously
     selected row. This is expected and does not replace the three required seam-test
     failures.
4. Archive the complete console output (command line, date, JVM/Gradle versions, gradle exit
   status, and the full failure text of the three tests) to
   `$STATE_ROOT/reports/capture-only-mutant-seam-tests.txt`. `$STATE_ROOT` is the PM run's
   state root; this mirrors the design's archived probe convention
   (`$STATE_ROOT/reports/root-cause-probe.txt`). The evidence file is not committed to the
   repository.
5. Apply Section 2.6 and re-run the same three-test command. Expected: `BUILD SUCCESSFUL`,
   all three tests green.
6. The archived file is part of the delivery gate evidence (Section 7.3).

### 6.5 Compatibility tests that must stay green

| Class / scope | Required result |
| --- | --- |
| `MapListPanelShould` (all 11 tests: `partitionActiveAndInactiveRowsIntoSeparateLists`, `maintainMutuallyExclusiveSelectionBetweenLists`, `swapButtonLabelsAndEnablementBasedOnSelectionPartition`, `dispatchDeactivateAndPreserveSelectionInInactiveListOnNextUpdate`, `dispatchReactivateAndPreserveSelectionInActiveListOnNextUpdate`, `promptConfirmationBeforeDeleteAndClearSelectionOnDelete`, `hostsTheHeadingLabelAndTheNamedCollapseButton`, `resolvesTheCollapseChevronIconAndFallsBackToText`, `tracksTheListWidthWithThePanelContentWidthOnSetRows`, `tracksTheListWidthOnAPanelResizeWithoutSetRows`, `derivesTheRowRendererCellWidthFromTheListWidth`) | Green. Verified green on the baseline (Section header). |
| `GraphWorkspaceWindowModelShould` (full class, including `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode`, `boundarySelectionDoesNotPinOrSelectAMapRow`, `tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates`, `keepsPinLabelTruthfulWhenReadOnlyDisablesTheToggle`, `updatesMapsMenuEnablementForActiveAndInactivePartitions`, `preservesInactivePartitionInReadOnlyMode`, `keepsMenuEnablementAlignedWithReadOnlyAndIndependentHistoryRules`, `exposesAllMapRowStatesWithProjectedCountsAndEmitsSessionCommandsOnCanvasUpdates`, `routesMapActionsToTheWorkspaceHandle`, `disablesMutatingControlsForReadOnlySessions`, plus the seven new tests) | Green after the fix. Verified green on the baseline. |
| `MapSidebarPanelShould` (constructs a `MapListPanel` only) | Green. |
| Full `:freeplane_plugin_graph:test` | Green. |

The two "pinned preservation" tests
(`dispatchDeactivateAndPreserveSelectionInInactiveListOnNextUpdate`,
`dispatchReactivateAndPreserveSelectionInActiveListOnNextUpdate`) must pass after the
`pendingSelectionId` removal; they now pass through the capture mechanism (priority 1) rather
than the pending id.

## 7. Verification Commands

### 7.1 Environment

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd /data/home/henry-arch/.local/state/pi/project-manager/worktrees/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/integration
```

Use `gradle` (not `gradlew`). Add `-PTestLoggingFull` for verbose failures.

### 7.2 Red phase (panel tests before the production fix)

```bash
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould" \
  -PTestLoggingFull
```

Expected: `BUILD FAILED` with the six failures of Section 6.2. If any test passes, the test
does not pin the defect; fix the test before proceeding.

### 7.3 Capture-only mutant (Section 6.4)

```bash
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.graphSelectionOverridesAClickedMapRow" \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.emptyGraphSelectionOverridesAClickedMapRow" \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.enclosureGraphSelectionOverridesAClickedMapRow" \
  -PTestLoggingFull --rerun-tasks
```

Expected: three failures as in Section 6.4. Archive the output to
`$STATE_ROOT/reports/capture-only-mutant-seam-tests.txt`.

### 7.4 Green phase

```bash
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould" \
  --tests "org.freeplane.plugin.graph.window.MapListPanelShould" \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" \
  -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; 6 + 11 + (existing + 7) tests green.

### 7.5 Full module gate

```bash
gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Use `--rerun-tasks` for a clean re-run when the up-to-date
check could mask a change.

### 7.6 Scope gate

```bash
git status --porcelain
```

Expected: exactly the four paths below are reported (the new test class as `??`, the three
modified files as ` M`):
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`,
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`,
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java`,
and
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
are listed. No file under `freeplane/`, `freeplane_api/`, or a translation resource is
modified.

## 8. Running-Application Acceptance Evidence

### 8.1 Scenario and fixture

Required live scenario: a Graph Workspace with at least two active maps and at least one
inactive map (the reported scenario). Build the workspace either by hand-editing a
format-version 1 `.fpg` or by using the UI (`Add Map`, then select a row and
`Deactivate Map`). Each map fixture file must contain graph-group-marked nodes (for example
the `LIVE_REFRESH_FIXTURE` shape used by `GraphWorkspaceLiveNodeRefreshShould`: a root with
two `<node>` children each carrying `<graph_group version="1"/>`) so the projection and the
settle loop have nodes to move.

Run the application on a virtual display with an isolated profile so the user's own
instance is never touched:

1. Refresh the built distribution:
   `gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline`.
2. Create `/tmp/fp-focus-evidence/{home,config/freeplane/1.12.x,maps}` and write
   `auto.properties` with `create_new_map_if_no_maps_are_loaded=false`,
   `always_load_last_maps=false`, `load_last_maps=false`, `load_last_map=false`, and
   `graph_workspace_recent_workspaces=<abs path to the .fpg>`.
3. Start `Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp` and launch
   `cd BIN && DISPLAY=:99 HOME=<run>/home XDG_CONFIG_HOME=<run>/config
   JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
   JAVA_OPTS="-Dorg.freeplane.nosplash=true" ./freeplane.sh > <run>/app.log 2>&1 &`.
4. Open `View → Open Graph Workspace`; the seeded recent workspace opens directly.

### 8.2 Falsifiable mid-settle witness

`LayoutSettleLoop` publishes `OperationalStatus.SETTLING` while `!frame.idle().idle()` and
`OperationalStatus.IDLE` when the frame is idle (`LayoutSettleLoop.java:548–550`). The status
bar renders that value through `graph_workspace.layout_status.settling` →
`graph_workspace.layout_status.<lowercase status>` (`GraphStatusBar.java:328–329`,
`freeplane/src/viewer/resources/translations/Resources_en.properties:871` → "Settling"), in
the label named `graph-workspace-status-layout-state`.

Trigger a fresh settle immediately before the sequence, for example by pressing the status
bar's restart-layout action (emits `GraphCommands.restartLayout()`), adding a map, or
changing a map's availability. The sequence below is falsifiable about the click landing
mid-settle:

1. **C1** — during settle: capture the full Graph Workspace window. The status bar shows the
   settling layout text, and the canvas shows the node layout at time t0.
2. **C2** — about one second later: capture again. The status bar still shows settling and at
   least one node's position differs from C1, proving the settle loop was still publishing
   frames.
3. **Click** a map entry (an active row, or the inactive row) while the settling text is
   still displayed.
4. **C3** — immediately after the click, before the next canvas frame: capture. The clicked
   row is highlighted and its action buttons/menu items are enabled, while the status bar
   still shows the settling layout text.
5. **C4** — approximately one second after the click: capture the canvas/status again. Node
   positions differ from C3 and the status bar still shows settling, proving the click landed
   while frames were still being published. A C4 that equals C3 (settle already finished)
   invalidates the sequence.
6. **C5** — after the status bar returns to idle/empty: capture. The same row is still
   highlighted and its actions are still enabled, proving focus persistence across the
   neutral rebuilds of the completed settle.

### 8.3 Evidence acceptance rule and archive

- A capture sequence in which step C3 cannot be shown to have occurred while the settling
  text was displayed, or in which C2/C4 do not differ from their predecessors, does **not**
  count as evidence. Re-run the sequence instead.
- Record for each capture: absolute timestamp, X display, window rectangle, and the observed
  layout-status text.
- Save the panels and a combined annotated image plus `app.log` under
  `freeplane_plugin_graph/build/evidence/map-entry-focus-mid-settle/` (git-ignored), and copy
  the combined image plus a short `README` (build revision from `git rev-parse HEAD`, the
  exact launch commands, and the C1–C5 timestamps) to
  `$STATE_ROOT/reports/map-entry-focus-mid-settle/`.
- Archive the evidence before the delivery gate; do not commit it.

## 9. Compatibility Notes

- **Java 8 target.** Production code must compile with `sourceCompatibility`/
  `targetCompatibility` `JavaVersion.VERSION_1_8` (`build.gradle:90–91`) under JDK 21. The
  reference code uses only Java 8 syntax; no `var`, no pattern matching, no streams in
  production.
- **Formatting.** 4-space indent, no tabs, UTF-8 encoding; the added methods follow the
  surrounding anonymous-class/early-return style.
- **No Freeplane core change.** No file under `freeplane/`, `freeplane_api/`, or
  `freeplane_framework/` is modified.
- **No persistence or workspace-format change.** No `.fpg` writer/reader, `WorkspaceDocument`,
  or display-settings code is touched; no new persisted field.
- **No translation change.** No `.properties` file is edited; the referenced keys
  (`graph_workspace.map_list.*`, `graph_workspace.action.*`,
  `graph_workspace.layout_status.settling`) already exist.
- **No new public API.** `clearSelection()` is package-private; `applySelection` and
  `targetSelection` are private. The OSGi bundle manifest/export set is unchanged.
- **No new listener, thread, lock, or timer.** The change reuses `synchronizeSelection` and
  the existing `selectionListeners` registration.
- **Scroll and other audited components.** The design records that the map-list scroll
  position survived a neutral rebuild in a passing baseline probe, and that workspace
  settings controls, sidebar width/collapse, canvas viewport and paint selection, search
  field, toolbar, and contributor inspector are unaffected; this spec introduces no change to
  any of them.

## 10. Traceability

| Design section | Spec section | Test method(s) |
| --- | --- | --- |
| Background / Confirmed defects 1–2 | 1.1, 2.2, 3.1 | `activeRowSelectionSurvivesNeutralRefresh`, `inactiveRowSelectionSurvivesNeutralRefresh`, `clickedRowMustNotSnapBackToStaleGraphSelectionDuringRefresh`, `neutralCanvasUpdatePreservesAClickedRowAndItsActions` |
| Confirmed defect 3 | 2.5, 4 (events 14–16) | `deactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition`, `reactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` |
| Confirmed defect 4 (button/menu enablement) | 2.6, 2.8, 4 (events 21–25) | `neutralCanvasUpdatePreservesAClickedRowAndItsActions`, `readOnlyTogglePreservesTheClickedRowAndOnlyDisablesItsActions`, `clearingTheGraphSelectionDisablesTheMapMenuItems` |
| Goals 1–2 | 3.1 Q1, 4 (events 8, 14–16, 20–22) | panel tests 1, 2, 4, 5 |
| Goal 3 (graph selection semantics) | 2.6, 2.7, 3.4, 4 (events 23–25) | `graphSelectionOverridesAClickedMapRow`, `emptyGraphSelectionOverridesAClickedMapRow`, `enclosureGraphSelectionOverridesAClickedMapRow`, `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode`, `boundarySelectionDoesNotPinOrSelectAMapRow` |
| Goal 4 (one mechanism) | 2.3, 2.4, 2.5 | `laterRowClickWinsOverAnEarlierDeactivateTarget`; diff review of `MapListPanel` |
| Goal 5 / Non-goals | 1.3, 2.7, 2.9, 9 | `git status --porcelain` scope gate; unchanged canvas/layout code |
| Delete-frame parity | 3.1 Q2, 4 (event 17) | `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow` |
| Explicit graph-selection application / risks | 2.6, 6.4 | three seam tests against the capture-only mutant |
| Compatibility | 6.5, 7.4, 7.5 | full `MapListPanelShould` and `GraphWorkspaceWindowModelShould`; full module suite |
| Running-application evidence | 8 | C1–C5 capture sequence |

## 11. Risks Accepted

Copied and refined from the approved design:

1. The capture search must scan **both** lists (the reference `targetSelection` scans the
   single incoming `rows` list and `applySelection` scans the active model then the inactive
   model), or active↔inactive transitions would lose focus. Pinned by panel tests 4 and 5.
2. The explicit graph-selection application must stay in
   `GraphWorkspaceWindowModel.handleIntent(ChangeSelection)`, not in `updateMapRows`; putting
   it in `updateMapRows` would clear a user's focus on every settle frame. Pinned by
   `neutralCanvasUpdatePreservesAClickedRowAndItsActions` and the three seam tests.
3. Removing `pendingSelectionId` must not regress the two pinned preservation tests
   (`dispatchDeactivateAndPreserveSelectionInInactiveListOnNextUpdate`,
   `dispatchReactivateAndPreserveSelectionInActiveListOnNextUpdate`); they are part of the
   compatibility gate.
4. Priority 2 must be consulted in the same `setRows` call when the captured row is gone, or
   the deletion frame would differ from baseline; pinned by
   `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow`.
5. `clearSelection()` and the explicit application are not read-only guarded; a node or
   enclosure selection still moves/clears row focus in a read-only session, while
   `updateButtons`/`updateMenuEnablement` keep every mutating control disabled. This matches
   the design and the baseline node-selection behavior.
