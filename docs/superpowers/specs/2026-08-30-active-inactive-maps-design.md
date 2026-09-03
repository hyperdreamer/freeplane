# Design: Separate Maps into Active and Inactive Categories

## Context & Background
In Freeplane Graph Workspace, multiple mind maps (`.mm`) can be loaded into a shared workspace document. Each map has an `active` state:
- Currently, all maps appear in a single unpartitioned list in `MapListPanel`.
- Removing an active map sets `active = false`, hiding its nodes from the canvas while preserving its relationships and pins in the workspace document.
- Re-adding an inactive map re-activates it and brings back its relationship connections.
- However, there is no visual distinction between active and inactive maps in the sidebar, no direct reactivation button in the UI, and no capability to permanently delete an inactive map along with its associated relationships and pins.

## Goals & Requirements
1. **Partitioning Maps into Categories**:
   - The maps panel displays maps partitioned into two distinct visual categories: **Active Maps** and **Inactive Maps**.
   - Newly added maps default to the **Active** category.
   - If either category is empty, its section header still renders (e.g. `ACTIVE (0)`), with an empty list container below it.
   - The entire list container is wrapped in a single `JScrollPane` with vertical scrolling enabled as needed.
2. **Category-Specific Actions & Dynamic Button Labels**:
   - **When an Active map is selected**:
     - Action button 1 displays **"Add Map"** (key: `graph_workspace.action.add_map`).
     - Action button 2 displays **"Deactivate Map"** (key: `graph_workspace.action.deactivate_map`).
     - Clicking "Deactivate Map" dispatches `deactivateSelected()`, which sends `GraphCommands.removeMap(id)`, transitioning the map to Inactive (`active = false`). Its relationships and pins remain preserved in the document.
     - Post-action selection: the newly deactivated map becomes selected in the Inactive list, immediately updating button states to the Inactive matrix.
   - **When an Inactive map is selected**:
     - Action button 1 displays **"Reactivate Map"** (key: `graph_workspace.action.reactivate_map`).
     - Clicking "Reactivate Map" dispatches `reactivateSelected()`, which sends `GraphCommands.reactivateMap(id)`, transitioning the map back to Active (`active = true`), immediately restoring all its nodes, geometry, and cross-map connections on the canvas.
     - Post-action selection: the newly reactivated map becomes selected in the Active list.
     - Action button 2 displays **"Delete Map"** (key: `graph_workspace.action.delete_map`).
     - Clicking "Delete Map" dispatches `deleteSelected()`, prompting a confirmation dialog:
       - Dialog title: `graph_workspace.dialog.delete_map.title` ("Delete Map")
       - Message: `graph_workspace.dialog.delete_map.message` ("Permanently delete '{0}' and all associated relationship connections and pins?")
       - If confirmed, dispatches `GraphCommands.deleteMap(id)` which permanently purges the map and cascades removal of associated relationships and pins.
       - Post-action selection: selection is cleared.
   - **When no map is selected**:
     - Action button 1 displays **"Add Map"** (Enabled, opens file chooser).
     - Action button 2 displays **"Deactivate Map"** (Disabled).
3. **Selection Model & Encapsulation**:
   - Selection is mutually exclusive across the Active and Inactive lists (at most one map is selected across the entire panel at any time).
   - Selecting a map in the Active list deselects any item in the Inactive list, and vice versa, guarded by an `updatingSelection` flag to prevent re-entrant event loops.
   - Arrow-key navigation functions properly within the focused list.
   - `selectedRow()` returns the selected row from whichever list has selection (or `null` if none).
   - `selectMap(MapReferenceId id)` searches both lists, selects the matching row in its respective list, and clears selection in the other list.
   - `MapListPanel` provides trigger methods callable by both panel buttons and window menu items:
     - `addMapFromChooser()`
     - `deactivateSelected()`
     - `reactivateSelected()`
     - `deleteSelected()`
     - `retrySelected()`
     - `locateSelected()`
   - Backward-compatible accessors: `addButton()` returns action button 1, `removeButton()` returns action button 2, and `rowList()` returns the active list (or active selection provider) for existing test assertions.
4. **Scrolling & Layout Constraints**:
   - Both sections reside inside a single vertical scrollable container (`JScrollPane`) with vertical scrolling enabled.
   - The panel layout respects the standard width (`PANEL_WIDTH = 264px`).
   - The action buttons in the south panel are laid out in a 2x2 grid (`GridLayout(2, 2, 4, 4)`):
     - Top row: `[ Action 1 (Add / Reactivate) ] [ Action 2 (Deactivate / Delete) ]`
     - Bottom row: `[ Retry ] [ Locate ]`
     This avoids any horizontal clipping or text truncation on the fixed-width sidebar.

## UI Component Hierarchy & Specifications
```
MapListPanel (JPanel, BorderLayout)
├── NORTH: JLabel ("Maps" - graph-workspace-map-list-heading)
├── CENTER: JScrollPane (graph-workspace-map-list-scroll)
│    └── Viewport: JPanel (BoxLayout Y_AXIS, tracks viewport width)
│         ├── JPanel: Active Section (BorderLayout)
│         │    ├── NORTH: JLabel Section Header ("ACTIVE ({count})" - graph-workspace-active-header)
│         │    └── CENTER: JList<MapRow>: Active List (graph-workspace-active-map-list)
│         └── JPanel: Inactive Section (BorderLayout)
│              ├── NORTH: JLabel Section Header ("INACTIVE ({count})" - graph-workspace-inactive-header)
│              └── CENTER: JList<MapRow>: Inactive List (graph-workspace-inactive-map-list)
└── SOUTH: JPanel (GridLayout(2, 2, 4, 4) - graph-workspace-map-list-actions)
     ├── JButton actionButton1 (name: "graph-workspace-add-map", label: "Add Map" or "Reactivate Map")
     ├── JButton actionButton2 (name: "graph-workspace-remove-map", label: "Deactivate Map" or "Delete Map")
     ├── JButton retryButton (name: "graph-workspace-retry-map", label: "Retry")
     └── JButton locateButton (name: "graph-workspace-locate-map", label: "Locate")
```

### Action Button States & Matrix
| Partition | RowState | Action 1 (Add/Reactivate) | Action 2 (Deactivate/Delete) | Retry Button | Locate Button |
| --- | --- | --- | --- | --- | --- |
| *None* | *None* | "Add Map" (Enabled) | "Deactivate Map" (Disabled) | Disabled | Disabled |
| **Active** | `ACTIVE` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Disabled | Disabled |
| **Active** | `LOADING` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Disabled | Disabled |
| **Active** | `RETRYABLE` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Enabled | Enabled |
| **Active** | `MISSING` | "Add Map" (Enabled) | "Deactivate Map" (Enabled) | Disabled | Enabled |
| **Active** | `READ_ONLY` | "Add Map" (Disabled) | "Deactivate Map" (Disabled) | Disabled | Disabled |
| **Inactive** | `INACTIVE` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Disabled |
| **Inactive** | `LOADING` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Disabled |
| **Inactive** | `RETRYABLE` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled* | Enabled |
| **Inactive** | `MISSING` | "Reactivate Map" (Enabled) | "Delete Map" (Enabled) | Disabled | Enabled |
| **Inactive** | `READ_ONLY`| "Reactivate Map" (Disabled)| "Delete Map" (Disabled) | Disabled | Disabled |

*\* Note on Retry for Inactive Maps: In `GraphCommandRouter`, `executeRetryMap` explicitly enforces that a map must be active to retry (`MAP_RETRY_INACTIVE`). Thus, `Retry` is disabled for all Inactive rows, but `Locate` is enabled if `MISSING` or `RETRYABLE`, allowing the file URI to be rebound.*

### Menu Bar Integration (`GraphWorkspaceWindow`)
In `GraphWorkspaceWindow.createMenuBar()`:
- `mapsAddMenuItem`: "Add Map" (`graph_workspace.action.add_map`), triggers `mapList.addMapFromChooser()` directly (always enabled unless read-only).
- `mapsDeactivateMenuItem`: "Deactivate Map" (`graph_workspace.action.deactivate_map`), triggers `mapList.deactivateSelected()`. Enabled when an Active map is selected (unless read-only).
- `mapsReactivateMenuItem`: "Reactivate Map" (`graph_workspace.action.reactivate_map`), triggers `mapList.reactivateSelected()`. Enabled when an Inactive map is selected (unless read-only).
- `mapsDeleteMenuItem`: "Delete Map" (`graph_workspace.action.delete_map`), triggers `mapList.deleteSelected()`. Enabled when an Inactive map is selected (unless read-only).
- `mapsRetryMenuItem`: "Retry Map" (`graph_workspace.action.retry_map`), triggers `mapList.retrySelected()`. Enabled when selected map is active and `RETRYABLE`.
- `mapsLocateMenuItem`: "Locate Map" (`graph_workspace.action.locate_map`), triggers `mapList.locateSelected()`. Enabled when selected map is `MISSING` or `RETRYABLE`.

In `GraphWorkspaceWindow.updateMenuEnablement()`:
- The enabled state of each of the 6 menu items is updated dynamically based on `mapList.selectedRow()`, its partition (`Active` vs. `Inactive`), and `mapList.isReadOnly()`.

## Architecture & Command Pipeline

### 1. Workspace Command: `deleteMap`
- In `WorkspaceCommands`:
  ```java
  public static WorkspaceCommand deleteMap(final MapReferenceId id)
  ```
  - Verification: If `mapById(before, id) == null`, return `rejected(MAP_NOT_FOUND, id)`.
  - Mutation:
    - Removes `MapReference` with matching `id` from `before.maps()`.
    - Filters out all `GraphRelationshipRecord`s where `source.mapReferenceId().equals(id) || target.mapReferenceId().equals(id)`.
    - Filters out all `PinRecord`s where `node.mapReferenceId().equals(id)`.
    - Retains sequence numbering monotonic; discards unknown XML on deleted records.
    - Returns `applied(updatedDocument, MAP_DELETED, id)`.
  - Invariant: Fully supported by `WorkspaceHistory` for undo and redo transitions. If undone, the map, relationships, and pins are restored atomically.

### 2. Workspace Command: `reactivateMap`
- `WorkspaceCommands.reactivateMap(MapReferenceId id)` already exists in `WorkspaceCommands.java`.
- Sets `active = true` on the matching `MapReference` and returns status `MAP_REACTIVATED`.

### 3. Graph Commands & Routing
- In `GraphCommands`:
  ```java
  public static ReactivateMap reactivateMap(final MapReferenceId mapReferenceId);
  public static DeleteMap deleteMap(final MapReferenceId mapReferenceId);
  ```
- In `GraphCommandRouter`:
  - Routes `ReactivateMap` to `executeWorkspace(WorkspaceCommands.reactivateMap(command.mapReferenceId()))`.
  - Routes `DeleteMap`:
    - Checks that the map reference exists.
    - Direct permanent deletion via `WorkspaceCommands.deleteMap(...)` is allowed for inactive maps (and rejects active maps with `graph_workspace.map.delete_active` if called directly without deactivating first, maintaining the deactivation-before-deletion invariant).
- Reactive Lease Handling:
  - Deleting a map applies a `WorkspaceTransition` to `GraphWorkspaceStore`.
  - `WorkspaceMapCoordinator` (which observes `WorkspaceStoreEvent`) detects the removal of the map reference and coordinates with `MapLeaseManager` to detach any leases cleanly.

### 4. Localization & i18n Keys
Add to `freeplane/src/viewer/resources/translations/Resources_en.properties`:
- `graph_workspace.action.deactivate_map=Deactivate Map`
- `graph_workspace.action.reactivate_map=Reactivate Map`
- `graph_workspace.action.delete_map=Delete Map`
- `graph_workspace.map_list.active_heading=ACTIVE ({0})`
- `graph_workspace.map_list.inactive_heading=INACTIVE ({0})`
- `graph_workspace.dialog.delete_map.title=Delete Map`
- `graph_workspace.dialog.delete_map.message=Are you sure you want to permanently delete "{0}" and all of its connections?`
- `graph_workspace.map.deleted=Map deleted: {0}`
- `graph_workspace.map.delete_active=Active map cannot be deleted directly: {0}`

### 5. Confirmation Dialog Pattern & Test Injection
- Define an injectable confirmation prompt functional interface:
  ```java
  @FunctionalInterface
  interface DeleteConfirmationPrompt {
      boolean confirmDelete(Component parent, String mapDisplayName);
  }
  ```
- `MapListPanel` provides constructors:
  ```java
  MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser) {
      this(handle, pathChooser, (parent, name) -> {
          if (GraphicsEnvironment.isHeadless()) {
              return true;
          }
          final String title = TextUtils.getText("graph_workspace.dialog.delete_map.title");
          final String message = TextUtils.format("graph_workspace.dialog.delete_map.message", name);
          return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
      });
  }

  MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser,
               final DeleteConfirmationPrompt confirmationPrompt) { ... }
  ```
- Headless unit tests can pass `(parent, name) -> true` or `(parent, name) -> false` to test confirmation paths deterministically without showing dialogs.

## Testing Strategy
1. **Unit Tests (`WorkspaceCommandsShould`)**:
   - `deleteMap` purges the map reference, cascading cross-map relationships, and pins atomically.
   - `deleteMap` on unknown ID returns `rejected`.
   - Undo/redo on `deleteMap` restores map reference, relationships, and pins.
   - `reactivateMap` moves map from inactive to active while keeping relationships and pins intact.
2. **Router & Controller Tests (`GraphCommandRouterShould`, `DefaultGraphWorkspaceControllerShould`)**:
   - Router correctly routes `GraphCommands.reactivateMap` to `WorkspaceCommands.reactivateMap`.
   - Router correctly routes `GraphCommands.deleteMap` to `WorkspaceCommands.deleteMap`.
   - Rejection when attempting to delete an active map directly without deactivation.
3. **UI / Panel Tests (`MapListPanelShould`, `GraphWorkspaceWindowModelShould`, `WorkspaceDialogsShould`)**:
   - Active and Inactive lists partition rows properly based on `active` flag.
   - Button labels and enablement toggle based on selection according to the state matrix.
   - 2x2 button grid layout renders cleanly within `PANEL_WIDTH`.
   - Trigger methods (`deactivateSelected()`, `reactivateSelected()`, `deleteSelected()`) operate correctly.
   - Delete action requests confirmation before dispatching `deleteMap`.
   - Selection synchronization across active and inactive lists with post-transition selection preservation.
