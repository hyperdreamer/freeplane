# Technical Specification: Separate Maps into Active and Inactive Categories

## 1. Overview and Problem Statement
In Freeplane Graph Workspace, multiple mind maps (`.mm`) can be loaded into a shared workspace document (`WorkspaceDocument`). Each map is represented as a `MapReference` with an `active` boolean status. Currently:
- All maps appear in a single unpartitioned `JList` in `MapListPanel`.
- Removing an active map sets `active = false` via `WorkspaceCommands.removeMap(id)`, hiding its nodes from the canvas while preserving its relationships and pins in the workspace document.
- Re-adding an inactive map via `addMap` re-activates it and brings back its relationship connections.
- However, there is no visual distinction between active and inactive maps in the sidebar, no direct reactivation action in the UI, and no capability to permanently delete an inactive map along with its associated relationships and pins.

This specification details the end-to-end architecture, data models, command contracts, Swing UI components, event flows, error handling, localization, and test suites to implement partitioning maps into **Active** and **Inactive** categories.

---

## 2. Precise Data Structures and Types

### 2.1 Workspace Document & Core Identifiers
The data layer models maps, relationships, and pins as immutable records:
- `MapReferenceId`: UUID-backed value object representing a distinct map reference in the workspace.
- `MapReference`: Immutable record:
  ```java
  public final class MapReference {
      private final MapReferenceId id;
      private final long sequence;
      private final URI storedUri;
      private final boolean active;
      private final String groupMarkerColor;
      private final List<UnknownXml> unknownXml;
      ...
  }
  ```
- `GraphRelationshipRecord`: Immutable record linking a source `NodeReference` to a target `NodeReference` with sequence, direction, and unknown XML.
- `PinRecord`: Immutable record associating a `NodeReference` with layout coordinates `(x, y)`.
- `NodeReference`: Value object holding `(MapReferenceId mapReferenceId, PersistedNodeId persitedNodeId)`.
- `WorkspaceDocument`: Root immutable document containing:
  ```java
  List<MapReference> maps();
  List<GraphRelationshipRecord> relationships();
  List<PinRecord> pins();
  DisplaySettings display();
  List<UnknownXml> unknownXml();
  ```

### 2.2 Map List Panel Models
- `MapPartition`: Enum representing the visual category partition:
  ```java
  public enum MapPartition {
      ACTIVE,
      INACTIVE
  }
  ```
- `RowState`: Status of the map reference:
  ```java
  public enum RowState {
      ACTIVE,
      LOADING,
      MISSING,
      READ_ONLY,
      RETRYABLE,
      INACTIVE
  }
  ```
- `MapRow`: Value object representing a row rendered in `MapListPanel`:
  ```java
  public static final class MapRow {
      private final MapReferenceId mapReferenceId;
      private final String displayName;
      private final RowState state;
      private final MapPartition partition;
      private final int projectedNodeCount;
      private final boolean selected;

      public static MapRow of(MapReferenceId id, String displayName, RowState state,
                              MapPartition partition, int projectedNodeCount, boolean selected);

      // Backward-compatible overload
      public static MapRow of(MapReferenceId id, String displayName, RowState state,
                              int projectedNodeCount, boolean selected) {
          return of(id, displayName, state,
              state == RowState.INACTIVE ? MapPartition.INACTIVE : MapPartition.ACTIVE,
              projectedNodeCount, selected);
      }
      ...
  }
  ```

---

## 3. Command Contracts & Routing

### 3.1 `WorkspaceCommands.deleteMap(MapReferenceId id)`
- **Signature**:
  ```java
  public static WorkspaceCommand deleteMap(final MapReferenceId id)
  ```
- **Validation**:
  - `Objects.requireNonNull(id, "id")`
  - If `mapById(before, id) == null`, returns `WorkspaceTransition.rejected(before, MAP_NOT_FOUND, id)`.
- **Mutation Semantics**:
  - Filter `before.maps()`: remove the `MapReference` whose `id()` matches `id`.
  - Filter `before.relationships()`: remove all `GraphRelationshipRecord`s where:
    ```java
    relationship.source().mapReferenceId().equals(id) || relationship.target().mapReferenceId().equals(id)
    ```
    *(Both source, target, and self-referential relationships are purged atomically).*
  - Filter `before.pins()`: remove all `PinRecord`s where:
    ```java
    pin.node().mapReferenceId().equals(id)
    ```
  - Monotonic sequences: Sequence numbers of remaining maps and relationships are not reindexed.
  - Return:
    ```java
    WorkspaceTransition.applied(before.toBuilder()
        .maps(filteredMaps)
        .relationships(filteredRelationships)
        .pins(filteredPins)
        .build(), MAP_DELETED, id);
    ```
- **Undo / Redo Invariant**:
  `WorkspaceHistory` executes `WorkspaceCommand` through immutable state snapshots. Undo of `deleteMap` restores the deleted `MapReference`, all purged relationships, and all purged pins atomically.

### 3.2 `WorkspaceCommands.reactivateMap(MapReferenceId id)`
- Existing command in `WorkspaceCommands.java:140`:
  - If map not found: `rejected(MAP_NOT_FOUND, id)`.
  - If map already active: `noChange(before, "reactivateMap")`.
  - If inactive: returns `applied` with `active = true` on the map reference, retaining all existing relationships and pins. Message key: `MAP_REACTIVATED`.

### 3.3 `GraphCommands` & Routing Layer
Add to `GraphCommands`:
```java
public static ReactivateMap reactivateMap(final MapReferenceId mapReferenceId);
public static DeleteMap deleteMap(final MapReferenceId mapReferenceId);
```
Add to `GraphCommandRouter`:
- `executeReactivateMap(GraphCommands.ReactivateMap command)`:
  - Invokes `executeWorkspace(WorkspaceCommands.reactivateMap(command.mapReferenceId()))`.
- `executeDeleteMap(GraphCommands.DeleteMap command)`:
  - Validates that the target map exists.
  - Precondition: Inactive state check. If `reference.active() == true`, returns `rejected("graph_workspace.map.delete_active", command.mapReferenceId())`. Permanent deletion requires deactivation first.
  - Invokes `executeWorkspace(WorkspaceCommands.deleteMap(command.mapReferenceId()))`.
- **Reactive Coordinator Seam**:
  Applying `WorkspaceCommands.deleteMap` emits a `WorkspaceStoreEvent`. `WorkspaceMapCoordinator` detects map removal and coordinates with `MapLeaseManager` to detach any live or lingering leases and release lifecycle listeners.

---

## 4. State Transitions, Invariants & Validation Rules

### 4.1 Action Button & Menu Enablement Matrix
The south actions panel has a 2x2 grid with 4 buttons:
- `actionButton1` (name: `graph-workspace-add-map`)
- `actionButton2` (name: `graph-workspace-remove-map`)
- `retryButton` (name: `graph-workspace-retry-map`)
- `locateButton` (name: `graph-workspace-locate-map`)

| Partition | RowState | Action 1 (Label / State) | Action 2 (Label / State) | Retry | Locate |
|---|---|---|---|---|---|
| *None selected* | *None* | "Add Map" (Enabled) | "Deactivate Map" (Disabled) | Disabled | Disabled |
| **Active** | `ACTIVE` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Disabled | Disabled |
| **Active** | `LOADING` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Disabled | Disabled |
| **Active** | `RETRYABLE` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Enabled | Enabled |
| **Active** | `MISSING` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Disabled | Enabled |
| **Active** | `READ_ONLY` | "Add Map" (Disabled) | "Deactivate Map" (Disabled) | Disabled | Disabled |
| **Inactive** | `INACTIVE` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Disabled |
| **Inactive** | `LOADING` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Disabled |
| **Inactive** | `RETRYABLE` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Enabled |
| **Inactive** | `MISSING` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Enabled |
| **Inactive** | `READ_ONLY`| "Reactivate Map" (Disabled)| "Delete Map" (Disabled) | Disabled | Disabled |

*(When `panel.isReadOnly() == true`, all mutating actions are disabled).*

### 4.2 Post-Transition Selection Rules & Persistence Across Updates
1. **Deactivate Action**:
   When an active map is deactivated via `deactivateSelected()`, `MapListPanel` sets its internal `pendingSelectionId = id`. When the resulting state update is processed in `setRows(...)`, the deactivated map is automatically selected in the `inactiveList`.
2. **Reactivate Action**:
   When an inactive map is reactivated via `reactivateSelected()`, `MapListPanel` sets `pendingSelectionId = id`. When `setRows(...)` is called, it is automatically selected in the `activeList`.
3. **Delete Action**:
   When an inactive map is permanently deleted via `deleteSelected()`, selection is cleared (`pendingSelectionId = null`).
4. **Canvas Refresh Selection Persistence**:
   `GraphWorkspaceWindow.updateMapRows(...)` constructs `MapRow` objects. Inactive maps have 0 nodes on canvas and will never have `selectedNode` match. To prevent canvas refreshes from wiping out selection on inactive maps, `MapListPanel.setRows(List<MapRow> values)` checks `pendingSelectionId` or the currently selected `MapReferenceId` and re-applies selection to the matching row regardless of whether `row.selected()` was set by canvas node selection.

---

## 5. UI Component Hierarchy & Swing Layout

### 5.1 Structure of `MapListPanel`
```
MapListPanel (JPanel, BorderLayout(0, 4), width: 264px)
├── NORTH: JLabel heading ("Maps" - name: "graph-workspace-map-list-heading")
├── CENTER: JScrollPane (name: "graph-workspace-map-list-scroll")
│    └── Viewport: ScrollableListContainer (JPanel, BoxLayout(Y_AXIS))
│         ├── ActiveSection (JPanel, BorderLayout)
│         │    ├── NORTH: JLabel ("ACTIVE (n)" - name: "graph-workspace-active-header")
│         │    └── CENTER: JList<MapRow> (name: "graph-workspace-active-map-list")
│         └── InactiveSection (JPanel, BorderLayout)
│              ├── NORTH: JLabel ("INACTIVE (m)" - name: "graph-workspace-inactive-header")
│              └── CENTER: JList<MapRow> (name: "graph-workspace-inactive-map-list")
└── SOUTH: JPanel (GridLayout(2, 2, 4, 4), name: "graph-workspace-map-list-actions")
     ├── actionButton1 (name: "graph-workspace-add-map")
     ├── actionButton2 (name: "graph-workspace-remove-map")
     ├── retryButton (name: "graph-workspace-retry-map")
     └── locateButton (name: "graph-workspace-locate-map")
```

### 5.2 `ScrollableListContainer` Sizing
To prevent `JList` sizing issues inside `JScrollPane`:
- The container implements `Scrollable`.
- `getScrollableTracksViewportWidth()` returns `true`.
- Both `activeList` and `inactiveList` configure:
  - `setLayoutOrientation(JList.VERTICAL)`
  - `setVisibleRowCount(0)`
  - Fixed cell height `ROW_HEIGHT = 52`
- When rows are updated in `setRows()`, the preferred size of each list is computed as `row_count * ROW_HEIGHT`, allowing the single enclosing `JScrollPane` to scroll the entire container naturally.

### 5.3 Selection Synchronization & Event Handling
- `updatingSelection` boolean flag prevents re-entrant event loops.
- `activeList.addListSelectionListener`:
  If `!event.getValueIsAdjusting() && !updatingSelection`:
  - `updatingSelection = true`
  - `inactiveList.clearSelection()`
  - `synchronizeSelection()`
  - `updatingSelection = false`
- `inactiveList.addListSelectionListener`:
  If `!event.getValueIsAdjusting() && !updatingSelection`:
  - `updatingSelection = true`
  - `activeList.clearSelection()`
  - `synchronizeSelection()`
  - `updatingSelection = false`
- `MapListPanel` exposes a unified selection listener:
  ```java
  public void addSelectionListener(Consumer<MapRow> listener)
  ```
  `GraphWorkspaceWindow` registers its `updateMenuEnablement()` via this listener.

### 5.4 Partition Determination in Read-Only Mode
In `GraphWorkspaceWindow.updateMapRows(CanvasState state)`:
```java
final MapPartition partition = accumulator.availability == MapAvailability.INACTIVE
    ? MapPartition.INACTIVE : MapPartition.ACTIVE;
final MapListPanel.RowState rowState = readOnly ? MapListPanel.RowState.READ_ONLY
    : rowStateFor(accumulator.availability);
rows.add(MapListPanel.MapRow.of(accumulator.mapReferenceId, accumulator.displayName,
    rowState, partition, accumulator.projectedNodeCount, isSelected));
```
This guarantees that inactive maps in read-only workspaces remain strictly in `MapPartition.INACTIVE`.

### 5.5 Delete Confirmation Prompt & Test Injection
- Functional interface:
  ```java
  @FunctionalInterface
  interface DeleteConfirmationPrompt {
      boolean confirmDelete(Component parent, String mapDisplayName);
  }
  ```
- Default implementation:
  ```java
  if (GraphicsEnvironment.isHeadless()) {
      return true;
  }
  final String title = TextUtils.getText("graph_workspace.dialog.delete_map.title");
  final String message = TextUtils.format("graph_workspace.dialog.delete_map.message", mapDisplayName);
  return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
  ```
- Constructor chaining:
  ```java
  MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser) {
      this(handle, pathChooser, defaultDeleteConfirmationPrompt());
  }
  MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser,
               final DeleteConfirmationPrompt prompt) { ... }
  ```

### 5.6 Action Trigger Methods & Backward Compatibility
`MapListPanel` exposes package-private methods invoked by both panel buttons and menu items:
- `addMapFromChooser()`
- `deactivateSelected()`
- `reactivateSelected()`
- `deleteSelected()`
- `retrySelected()`
- `locateSelected()`
- Backward-compatible accessors:
  - `addButton()` returns `actionButton1`
  - `removeButton()` returns `actionButton2`
  - `rowList()` returns `activeList` (with `activeList()` and `inactiveList()` provided)
  - `selectedRow()` returns the selected row from whichever list has an active selection.

### 5.7 Menu Bar Integration (`GraphWorkspaceWindow`)
In `GraphWorkspaceWindow.createMenuBar()`:
```java
mapsAddMenuItem = item("graph_workspace.action.add_map", "add-map",
    event -> mapList.addMapFromChooser());
mapsDeactivateMenuItem = item("graph_workspace.action.deactivate_map", "deactivate-map",
    event -> mapList.deactivateSelected());
mapsReactivateMenuItem = item("graph_workspace.action.reactivate_map", "reactivate-map",
    event -> mapList.reactivateSelected());
mapsDeleteMenuItem = item("graph_workspace.action.delete_map", "delete-map",
    event -> mapList.deleteSelected());
mapsRetryMenuItem = item("graph_workspace.action.retry_map", "retry-map",
    event -> mapList.retrySelected());
mapsLocateMenuItem = item("graph_workspace.action.locate_map", "locate-map",
    event -> mapList.locateSelected());
```
In `GraphWorkspaceWindow.updateMenuEnablement()`:
- `mapsAddMenuItem`: enabled when `!readOnly`.
- `mapsDeactivateMenuItem`: enabled when `!readOnly && selected != null && selected.partition() == MapPartition.ACTIVE`.
- `mapsReactivateMenuItem`: enabled when `!readOnly && selected != null && selected.partition() == MapPartition.INACTIVE`.
- `mapsDeleteMenuItem`: enabled when `!readOnly && selected != null && selected.partition() == MapPartition.INACTIVE`.
- `mapsRetryMenuItem`: enabled when `!readOnly && selected != null && selected.partition() == MapPartition.ACTIVE && selected.state() == RowState.RETRYABLE`.
- `mapsLocateMenuItem`: enabled when `!readOnly && selected != null && (selected.state() == RowState.MISSING || selected.state() == RowState.RETRYABLE)`.

---

## 6. Exact i18n Keys and Values

Add to `freeplane/src/viewer/resources/translations/Resources_en.properties`:
```properties
graph_workspace.action.deactivate_map=Deactivate Map
graph_workspace.action.reactivate_map=Reactivate Map
graph_workspace.action.delete_map=Delete Map
graph_workspace.map_list.active_heading=ACTIVE ({0})
graph_workspace.map_list.inactive_heading=INACTIVE ({0})
graph_workspace.dialog.delete_map.title=Delete Map
graph_workspace.dialog.delete_map.message=Are you sure you want to permanently delete "{0}" and all associated relationships and pins?
graph_workspace.map.deleted=Map deleted: {0}
graph_workspace.map.delete_active=Active map cannot be deleted directly: {0}
```

---

## 7. Error Handling, Edge Cases & Invariants

1. **Delete Map with Both Incoming and Outgoing Cross-Map Relationships**:
   - `WorkspaceCommands.deleteMap(id)` purges any relationship where `source.mapReferenceId().equals(id)` OR `target.mapReferenceId().equals(id)`.
   - Connected nodes in other maps are unaffected and remain valid nodes in their respective maps.
2. **Delete Map with Pins**:
   - Any pinned node belonging to the deleted map is purged from `before.pins()`. Pins of other maps remain untouched.
3. **Direct Deletion of Active Map Rejected**:
   - `GraphCommandRouter` rejects `DeleteMap` if the map is active, requiring explicit deactivation (`removeMap`) first. This ensures intentional two-step lifecycle for active maps.
4. **Reactivating Missing Map**:
   - Reactivating an inactive map whose file has moved on disk successfully marks it `active = true`, after which availability discovery flags it as `MISSING`, enabling the `Locate` button.
5. **Undo / Redo Completeness**:
   - `deleteMap` transition contains the exact snapshot of removed items. Undoing `deleteMap` restores the `MapReference`, all relationships, and all pins atomically.
6. **Read-Only Mode Integrity**:
   - In read-only mode, inactive maps remain rendered in the `INACTIVE` section, and all mutating actions (`Add`, `Deactivate`, `Reactivate`, `Delete`, `Retry`, `Locate`) are disabled.

---

## 8. Test Plan & Test Fixtures

### 8.1 Test Classes
1. `WorkspaceCommandsShould.java`
   - `deleteMapPurgesMapAndCascadesRelationshipsAndPinsBothSourceAndTarget()`:
     Verify deleting a map purges it, purges relationships where it is source, purges relationships where it is target, and purges its pins.
   - `deleteMapRejectsUnknownMapId()`:
     Verify deleting a non-existent map returns `Status.REJECTED` with `MAP_NOT_FOUND`.
   - `deleteMapIsUndoableAndRedoableWithAllCascadedEntities()`:
     Verify `WorkspaceHistory` compensation restores the deleted map, relationships, and pins.
   - `reactivateMapRestoresActiveStatusAndPreservesRelationships()`:
     Verify reactivating an inactive map transitions `active` to `true` and preserves relationships.

2. `GraphCommandRouterShould.java`
   - `routesReactivateMapToWorkspaceCommand()`:
     Verify `GraphCommands.reactivateMap` transitions map to active.
   - `routesDeleteMapForInactiveMap()`:
     Verify `GraphCommands.deleteMap` applies `WorkspaceCommands.deleteMap` for inactive map.
   - `rejectsDeleteMapForActiveMap()`:
     Verify `GraphCommands.deleteMap` returns `Status.REJECTED` with `graph_workspace.map.delete_active`.

3. `MapListPanelShould.java` / `GraphWorkspaceWindowModelShould.java`
   - `partitionsActiveAndInactiveMapsCorrectly()`:
     Verify active rows appear in `activeList` and inactive rows appear in `inactiveList`.
   - `partitionsInactiveMapsCorrectlyInReadOnlyMode()`:
     Verify inactive maps in a read-only workspace remain in `inactiveList`.
   - `swapsButtonLabelsAndEnablementBasedOnPartitionAndState()`:
     Verify button 1 toggles "Add Map" / "Reactivate Map" and button 2 toggles "Deactivate Map" / "Delete Map".
   - `dispatchesDeactivateAndSelectsNewlyDeactivatedMapInInactiveList()`:
     Verify deactivating an active map preserves selection on the item in `inactiveList`.
   - `dispatchesReactivateAndSelectsNewlyReactivatedMapInActiveList()`:
     Verify reactivating an inactive map preserves selection on the item in `activeList`.
   - `promptsConfirmationBeforeDeleteAndClearsSelectionOnDelete()`:
     Verify delete prompts `DeleteConfirmationPrompt` and clears selection on approval; cancelling does not dispatch command.
   - `mutuallyExclusiveSelectionAcrossLists()`:
     Selecting item in `activeList` clears `inactiveList`, and vice versa, without re-entrant loop.
