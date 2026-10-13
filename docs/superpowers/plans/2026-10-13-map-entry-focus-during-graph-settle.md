# Map-Entry Focus Persistence During Graph Settle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a clicked or programmatically selected map row keep its focus — highlight, enabled
row actions, and enabled Maps menu items — across every neutral graph-workspace rebuild during
layout settle, while re-applying the graph node selection explicitly only when the graph
selection changes.

**Architecture:** `MapListPanel.setRows` captures the focused `MapReferenceId` before clearing
the list models, rebuilds rows, and re-applies the captured id (priority 1) or the first
`selected()`-flagged row (priority 2) through one private `applySelection(MapReferenceId)`
routine that becomes the single programmatic `JList` selection mutation site;
`selectMap`/`clearSelection` route through it and `pendingSelectionId` is deleted.
`GraphWorkspaceWindowModel.handleIntent(GraphIntent.ChangeSelection)` applies the graph selection
explicitly after `updateMapRows` and before `updateStatusBar`. One lane
(`map-entry-focus`) is used because all three tasks are sequential and Task 2 consumes Task 1's
committed `clearSelection()`.

**Tech Stack:** Java 8 source/target on runtime Java 21.0.8-zulu, Gradle multi-project build
(module `freeplane_plugin_graph`), JUnit 4 with AssertJ and Mockito (including `MockedStatic`),
and a running-application acceptance run under Xvfb using X11 tooling.

**Implementation contract:** `docs/superpowers/specs/2026-10-13-map-entry-focus-during-graph-settle-spec.md`
(the authority; every value a task needs is restated in the task, so no task has to open it),
derived from the approved design
`docs/superpowers/specs/2026-10-13-map-entry-focus-during-graph-settle-design.md`. Baseline:
this integration worktree at commit `47d38a4bb0` (a docs-only child of `68f9c66285`, so every
`file:line` reference below is current).

## Global Constraints

- Java 8 source and target (`sourceCompatibility`/`targetCompatibility` `JavaVersion.VERSION_1_8`);
  production excerpts use Java 8 syntax and 4-space indentation, UTF-8 encoding, no tabs.
- Toolchain is Zulu 21 at `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`; before
  every Gradle command run `export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`
  and `export PATH="$JAVA_HOME/bin:$PATH"`.
- Use `gradle` (never `gradlew`, never Maven) from the worktree root, with
  `--console=plain --offline` on every invocation; add `-PTestLoggingFull` whenever a run must
  show verbose failures.
- Production changes are confined to
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java` and
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`.
- Test changes are confined to the new class
  `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java`
  and to the existing
  `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.
- No Freeplane core change: no file under `freeplane/`, `freeplane_api/`, or
  `freeplane_framework/` may be modified.
- No persistence, schema, or workspace-format change; no new persisted field and no `.fpg`
  reader/writer or display-settings code touched.
- No translation resource is touched; no `.properties` file is edited and
  `gradle format_translation` does not apply.
- Every commit message ends with `[2026-10-13-map-entry-focus-during-graph-settle]`.
- Red-phase and capture-only-mutant evidence archives go to the absolute PM run reports
  directory
  `/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports/`
  (`red-panel.txt`, `capture-only-mutant-seam-tests.txt`) and must never be committed into the
  repository.
- Acceptance evidence may be committed only under
  `docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle/`; transient
  captures under `freeplane_plugin_graph/build/evidence/map-entry-focus-mid-settle/` (git-ignored)
  and `/tmp/fp-focus-evidence/` are never committed, and the PM-run copy under the reports
  directory above is never committed.
- Do not modify the design or the spec.

## Task 1: Preserve the map-list row focus across neutral rebuilds

**Implementer tier:** Standard
**Lane:** map-entry-focus

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java:179` (delete the `pendingSelectionId` field), `:307-368` (replace `setRows`), `:378-403` (replace `selectMap` with `selectMap` + `clearSelection` + `applySelection` + `targetSelection`), `:473-496` (delete the three `pendingSelectionId` assignments)
- Archive (never commit):
  `/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports/red-panel.txt`

**Interfaces:**

- Consumes: the baseline `MapListPanel` members `selectedRow(): MapRow`, `rows(): List<MapRow>`,
  `activeList(): JList<MapRow>`, `inactiveList(): JList<MapRow>`, `removeButton(): JButton`,
  `addButton(): JButton`, `selectMap(MapReferenceId): void`;
  `MapListPanel.MapRow.of(MapReferenceId, String, RowState, MapPartition, int, boolean)`;
  `MapPartition.ACTIVE` / `MapPartition.INACTIVE`; `MapListPanel.RowState.ACTIVE` /
  `RowState.INACTIVE`; `GraphWorkspaceHandle.execute(GraphCommand)`;
  `GraphCommands.RemoveMap` / `GraphCommands.ReactivateMap`; the existing `TextUtils` and
  `ResourceController` `MockedStatic` fixture convention of `MapListPanelShould`.
- Produces: `void setRows(List<MapRow>)` with capture-before-clear semantics and the priority
  rules of spec sections 3.1 Q1–Q3; `void selectMap(MapReferenceId)` routed through
  `applySelection`; `void clearSelection()` (package-private, no argument);
  `private void applySelection(MapReferenceId)`; `private MapReferenceId targetSelection(MapReferenceId)`;
  `pendingSelectionId` no longer exists anywhere in `MapListPanel`.

- [ ] **Step 1: Write the failing panel regression test class**

Create
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java`
with exactly this content:

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

- [ ] **Step 2: Run the tests and confirm they fail, archiving the red output**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
PM_REPORTS=/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports
mkdir -p "$PM_REPORTS"
{
  echo "date: $(date --iso-8601=seconds)"
  echo 'command: gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould" -PTestLoggingFull --console=plain --offline'
  "$JAVA_HOME/bin/java" -version 2>&1
  gradle --version --console=plain --offline 2>&1
} > "$PM_REPORTS/red-panel.txt"
set -o pipefail
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould" \
  -PTestLoggingFull --console=plain --offline 2>&1 | tee -a "$PM_REPORTS/red-panel.txt"
status=${PIPESTATUS[0]}
echo "gradle exit: $status" | tee -a "$PM_REPORTS/red-panel.txt"
set +o pipefail
ls -l "$PM_REPORTS/red-panel.txt"
```

Expected: `gradle exit: 1` and `BUILD FAILED` with exactly six failing tests, one per test
method, at these assertions (baseline-observed values from spec section 6.2):

- `activeRowSelectionSurvivesNeutralRefresh` and `inactiveRowSelectionSurvivesNeutralRefresh`:
  `selectedRow()` is `null` after the second `setRows`, so the `isNotNull()` assertion fails.
- `clickedRowMustNotSnapBackToStaleGraphSelectionDuringRefresh`: the second `setRows` re-selects
  the flagged row (`ACTIVE_MAP`, index 0); the assertion expecting `OTHER_MAP` fails with actual
  `ACTIVE_MAP`.
- `deactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition` and
  `reactivateClickMustSurviveAnInterleavedSettleFrameAndThePartitionTransition`: the interleaved
  `setRows` consumes the pending id (`pendingSelectionId = null`), so the transition frame leaves
  `selectedRow() == null` and the final `isNotNull()` fails.
- `laterRowClickWinsOverAnEarlierDeactivateTarget`: the pending id still holds `ACTIVE_MAP`, so
  the transition frame focuses `ACTIVE_MAP`; the assertion expecting `OTHER_MAP` fails.

If any test passes, the test does not pin the defect: fix the test to match the spec's test code
before proceeding. Do not touch production code in this step. The archive
`red-panel.txt` must contain the full console output of this run and is never committed.

- [ ] **Step 3: Replace `MapListPanel.setRows` with the capture-before-clear version**

Open `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
and replace the whole method currently at lines 307-368:

```java
    void setRows(final List<MapRow> values) {
        Objects.requireNonNull(values, "rows");
        final List<MapRow> copy = new ArrayList<MapRow>(values.size());
        for (final MapRow row : values) {
            copy.add(Objects.requireNonNull(row, "row"));
        }
        rows = Collections.unmodifiableList(copy);
        updatingSelection = true;
        try {
            activeModel.clear();
            inactiveModel.clear();
            int selectedActiveIndex = -1;
            int selectedInactiveIndex = -1;

            final MapReferenceId targetSelection = pendingSelectionId != null
                ? pendingSelectionId : (selectedRow() != null ? selectedRow().mapReferenceId() : null);

            for (final MapRow row : rows) {
                if (row.partition() == MapPartition.ACTIVE) {
                    activeModel.addElement(row);
                    if (targetSelection != null && targetSelection.equals(row.mapReferenceId())) {
                        selectedActiveIndex = activeModel.size() - 1;
                    }
                    else if (row.selected() && selectedActiveIndex < 0 && targetSelection == null) {
                        selectedActiveIndex = activeModel.size() - 1;
                    }
                }
                else {
                    inactiveModel.addElement(row);
                    if (targetSelection != null && targetSelection.equals(row.mapReferenceId())) {
                        selectedInactiveIndex = inactiveModel.size() - 1;
                    }
                    else if (row.selected() && selectedInactiveIndex < 0 && targetSelection == null) {
                        selectedInactiveIndex = inactiveModel.size() - 1;
                    }
                }
            }

            activeHeader.setText(TextUtils.format("graph_workspace.map_list.active_heading", activeModel.size()));
            inactiveHeader.setText(TextUtils.format("graph_workspace.map_list.inactive_heading", inactiveModel.size()));

            syncListWidths();

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
    }
```

with exactly:

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

Mandatory structure: the null checks and the copy loop stay first; `rows` is assigned the
unmodifiable copy before the capture; the capture is the first `selectedRow()` read and happens
before `activeModel.clear()`; both models are cleared and repopulated, headers refreshed, and
`syncListWidths()` called inside one `updatingSelection` window; no `JList` selection index is
touched in that window; `setRows` does not call `synchronizeSelection()` itself.

- [ ] **Step 4: Delete the `pendingSelectionId` field and its three action assignments**

In the same file:

1. Delete the field declaration at line 179:

```java
    private MapReferenceId pendingSelectionId;
```

2. In `deactivateSelected` (baseline lines 473-479), delete the line
   `            pendingSelectionId = row.mapReferenceId();` so the method reads:

```java
    void deactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.ACTIVE) {
            execute(GraphCommands.removeMap(row.mapReferenceId()));
        }
    }
```

3. In `reactivateSelected` (baseline lines 481-487), delete the line
   `            pendingSelectionId = row.mapReferenceId();` so the method reads:

```java
    void reactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            execute(GraphCommands.reactivateMap(row.mapReferenceId()));
        }
    }
```

4. In `deleteSelected` (baseline lines 489-496), delete the line
   `                pendingSelectionId = null;` so the method reads:

```java
    void deleteSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            if (deletePrompt.confirmDelete(this, row.displayName())) {
                execute(GraphCommands.deleteMap(row.mapReferenceId()));
            }
        }
    }
```

Do not change any other predicate, command, or method. After this step
`grep -n pendingSelectionId freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
must print nothing.

- [ ] **Step 5: Replace `selectMap` and add `clearSelection`, `applySelection`, `targetSelection`**

Replace the whole `selectMap` method currently at lines 378-403:

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

with exactly these four methods, in this order, immediately before `MapRow selectedRow()`:

```java
    void selectMap(final MapReferenceId mapReferenceId) {
        applySelection(Objects.requireNonNull(mapReferenceId, "mapReferenceId"));
    }

    void clearSelection() {
        applySelection(null);
    }

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

Contracts: `applySelection` accepts `null`, clears both lists, scans `activeModel` then
`inactiveModel`, sets at most one index, restores `updatingSelection` in `finally`, and calls
`synchronizeSelection()` exactly once on every return and exception path after the guard is
restored to `false`. `targetSelection` implements priority 1 (captured id present in `rows`),
then priority 2 (first `rows` entry whose `selected()` is true), then priority 3 (`null`).
`selectMap` keeps the `Objects.requireNonNull(mapReferenceId, "mapReferenceId")` rejection and
contains no loop or direct `JList` mutation; `clearSelection` is package-private and takes no
argument. A null element in `setRows` still throws `NullPointerException("row")` before any
model or `JList` mutation.

- [ ] **Step 6: Run the panel tests green**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould" \
  --tests "org.freeplane.plugin.graph.window.MapListPanelShould" \
  -PTestLoggingFull --console=plain --offline
```

Expected: `BUILD SUCCESSFUL`, 17 tests green (6 new + 11 `MapListPanelShould`, including the
pinned `dispatchDeactivateAndPreserveSelectionInInactiveListOnNextUpdate` and
`dispatchReactivateAndPreserveSelectionInActiveListOnNextUpdate`). Do not run
`GraphWorkspaceWindowModelShould` at this point: in this capture-only tree
`selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` is expected to fail, and Task 2 Step 2 turns
that failure into archived mutant evidence.

- [ ] **Step 7: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListSelectionPersistenceShould.java
git commit -m "fix(graph): preserve the map-list row focus across neutral rebuilds [2026-10-13-map-entry-focus-during-graph-settle]"
git log -1 --format="%H %s"
```

Expected: one commit whose message ends with `[2026-10-13-map-entry-focus-during-graph-settle]`.
Never stage the PM reports directory.

## Task 2: Apply the graph selection explicitly on change selection

**Implementer tier:** Advanced
**Lane:** map-entry-focus

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1504-1515` (extend the `ChangeSelection` branch)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:2670` (insert seven `@Test` methods after `boundarySelectionDoesNotPinOrSelectAMapRow()`), `:2996` (insert `transientNodeEndpoint` after `nodeState`)
- Archive (never commit):
  `/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports/capture-only-mutant-seam-tests.txt`

**Interfaces:**

- Consumes: `MapListPanel.selectMap(MapReferenceId)`, `MapListPanel.clearSelection()`, and the
  capture-before-clear `setRows(List<MapRow>)` committed by Task 1; the existing private fixture
  builders of `GraphWorkspaceWindowModelShould`: `fixture(Viewport, CanvasState, List<MapRegistration>, boolean)`,
  `Fixture.model()`, `Fixture.binding`, `Fixture.handle`, `registration(MapReferenceId, String, MapAvailability)`,
  `id(long)`, `emptyUnknownXml()`, `nodeState(MapReferenceId, LayoutPoint)`, and
  `menuItem(GraphWorkspaceWindowModel, String)`; `GraphWorkspaceWindowModel.acceptIntent(GraphIntent)`,
  `acceptCanvasState(CanvasState)`, `execute(GraphCommand)`, `mapList()`, `setReadOnly(boolean)`,
  `close()`; `GraphIntent.ChangeSelection(Optional<ProjectedEndpointKey>)`;
  `ProjectedEndpointKey.ofNode(ProjectedNodeKey)` / `ofEnclosure(EnclosureKey)`;
  `ProjectedNodeKey.of(SourceNodeKey)`; `SourceNodeKey.transientPath(MapReferenceId, List<Integer>)`;
  `EnclosureKey.of(SourceNodeKey)`; `GraphCommands.deleteMap(MapReferenceId)`;
  `MapListPanel.RowState`; `MapAvailability.AVAILABLE` / `MapAvailability.INACTIVE`.
- Produces: the seven tests `graphSelectionOverridesAClickedMapRow`,
  `emptyGraphSelectionOverridesAClickedMapRow`, `enclosureGraphSelectionOverridesAClickedMapRow`,
  `neutralCanvasUpdatePreservesAClickedRowAndItsActions`,
  `readOnlyTogglePreservesTheClickedRowAndOnlyDisablesItsActions`,
  `clearingTheGraphSelectionDisablesTheMapMenuItems`, and
  `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow`, plus the private helper
  `transientNodeEndpoint(MapReferenceId): ProjectedEndpointKey`; and the extended
  `GraphWorkspaceWindowModel.handleIntent(GraphIntent.ChangeSelection)` branch that applies
  `mapList.selectMap(selectedNode.mapReferenceId())` for a node selection and
  `mapList.clearSelection()` otherwise, between `updateMapRows(currentState)` and
  `updateStatusBar()`.

- [ ] **Step 1: Add the seven window-model tests and the transient endpoint helper**

Open
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
and insert the following seven `@Test` methods immediately after the closing brace of
`boundarySelectionDoesNotPinOrSelectAMapRow()` (baseline line 2670, before the `@Test` of
`tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates`):

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

Then insert this private helper with the other private static helpers, immediately after the
closing brace of `nodeState` (baseline line 2996, before `wideNodeState`):

```java
    private static ProjectedEndpointKey transientNodeEndpoint(final MapReferenceId mapId) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(
            SourceNodeKey.transientPath(mapId, Collections.emptyList())));
    }
```

`EnclosureKey`, `ProjectedEndpointKey`, `ProjectedNodeKey`, `SourceNodeKey`, `MapAvailability`,
`Viewport`, `Optional`, `Arrays`, `Collections`, and `when` are already imported in the file; do
not add imports. `deletionFrameFallsThroughToTheFlaggedGraphSelectedRow` must not call
`model.mapList().deleteSelected()`, because the model's default `DeleteConfirmationPrompt` opens
a modal `JOptionPane`.

- [ ] **Step 2: Run the three seam tests against the capture-only tree and archive the failures**

The tree now contains the whole panel half (Task 1) but not yet the explicit graph-selection
application, which is exactly the capture-only mutant of spec section 6.4. Run only the three
seam tests and archive the complete console output:

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
PM_REPORTS=/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports
mkdir -p "$PM_REPORTS"
REPORT="$PM_REPORTS/capture-only-mutant-seam-tests.txt"
{
  echo "date: $(date --iso-8601=seconds)"
  echo 'command: gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.graphSelectionOverridesAClickedMapRow" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.emptyGraphSelectionOverridesAClickedMapRow" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.enclosureGraphSelectionOverridesAClickedMapRow" -PTestLoggingFull --rerun-tasks --console=plain --offline'
  "$JAVA_HOME/bin/java" -version 2>&1
  gradle --version --console=plain --offline 2>&1
} > "$REPORT"
set -o pipefail
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.graphSelectionOverridesAClickedMapRow" \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.emptyGraphSelectionOverridesAClickedMapRow" \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.enclosureGraphSelectionOverridesAClickedMapRow" \
  -PTestLoggingFull --rerun-tasks --console=plain --offline 2>&1 | tee -a "$REPORT"
status=${PIPESTATUS[0]}
echo "gradle exit: $status" | tee -a "$REPORT"
set +o pipefail
ls -l "$REPORT"
```

Expected: `gradle exit: 1` and exactly three failures, each at the focus assertion:

- `graphSelectionOverridesAClickedMapRow`: `selectedRow().mapReferenceId()` is `activeB`; the
  assertion expecting `activeA` fails.
- `emptyGraphSelectionOverridesAClickedMapRow`: `selectedRow()` is `activeB`;
  `assertThat(...).isNull()` fails.
- `enclosureGraphSelectionOverridesAClickedMapRow`: `selectedRow()` is `activeB`;
  `assertThat(...).isNull()` fails.

Corroborating witness, expected in the same capture-only tree and not a replacement for the
three failures: the existing `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` also fails at
its `assertThat(model.mapList().rows().get(0).selected()).isFalse()` line after the empty
`ChangeSelection`, because the capture-only `setRows` keeps the previously selected row. Do not
run `deleteSelected` or any other modal-dialog path. The archive is never committed.

- [ ] **Step 3: Apply the explicit graph selection in `handleIntent(ChangeSelection)`**

Open `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
and replace the `ChangeSelection` branch currently at lines 1504-1515:

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

with exactly:

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

Mandatory order for every `ChangeSelection`: model pair update, `paintState`, `canvas.setPaintState`,
`updateMapRows(currentState)`, the new node/non-node branch, then `updateStatusBar()`.
`updateMapRows(CanvasState)` itself is not modified; no other `handleIntent` branch changes.

- [ ] **Step 4: Run the focused green phase**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.window.MapListSelectionPersistenceShould" \
  --tests "org.freeplane.plugin.graph.window.MapListPanelShould" \
  --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" \
  -PTestLoggingFull --console=plain --offline
```

Expected: `BUILD SUCCESSFUL`, 118 tests green with zero failures: 6 new panel tests + 11
`MapListPanelShould` tests + 101 `GraphWorkspaceWindowModelShould` tests (the 94 existing `@Test`
methods at baseline plus the 7 added here). The three seam tests now pass, and the four
capture-only witnesses are gone.

- [ ] **Step 5: Run the full module gate**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test -PTestLoggingFull --console=plain --offline
```

Expected: `BUILD SUCCESSFUL` with zero failures across the full `freeplane_plugin_graph` module
(use `--rerun-tasks` for a clean re-run if an up-to-date check could mask a change). If the gate
surfaces a regression caused by the new selection application, fix it inside this task before
committing.

- [ ] **Step 6: Run the scope gate**

```bash
cd "$(git rev-parse --show-toplevel)"
git status --porcelain
```

Expected: exactly the two paths changed by this task are reported, as ` M`:
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
and
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
(the two Task 1 paths are already committed). No file under `freeplane/`, `freeplane_api/`, or a
translation resource is modified. If anything else is reported, unstage or revert it before
committing.

- [ ] **Step 7: Commit**

```bash
cd "$(git rev-parse --show-toplevel)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "fix(graph): apply the graph selection explicitly on change selection [2026-10-13-map-entry-focus-during-graph-settle]"
git log -1 --format="%H %s"
```

Expected: one commit whose message ends with `[2026-10-13-map-entry-focus-during-graph-settle]`.
Never stage the PM reports directory.

## Task 3: Record running-application mid-settle acceptance evidence

**Implementer tier:** Capable
**Lane:** map-entry-focus

**Files:**

- Create: `docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle/README.md`
- Create: `docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle/combined-annotated.png`
- Create: `docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle/C1.png`, `C2.png`, `C3.png`, `C4.png`, `C5.png`
- Transient and not committed: `/tmp/fp-focus-evidence/**` (isolated home/config, map and workspace seed files, raw captures, `captures.txt`, `targets.env`, `statuses.txt`, `app.log`, `README.md` draft)
- Transient and not committed (git-ignored): `freeplane_plugin_graph/build/evidence/map-entry-focus-mid-settle/**`
- Transient and not committed (PM run reports directory):
  `/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports/map-entry-focus-mid-settle/**`

**Interfaces:**

- Consumes: the committed Task 1 panel behavior and Task 2 `handleIntent` behavior; the module
  Gradle task `:freeplane_plugin_graph:graphSmokeInstallation`, which refreshes the git-ignored
  `BIN/` launcher tree; the app action `OpenGraphWorkspaceAction` in the View menu; the recent
  workspace property `graph_workspace_recent_workspaces`; the config version directory
  `XDG_CONFIG_HOME/freeplane/1.12.x` (`Compat.CURRENT_VERSION_DIR`); the `Restart Layout` status
  bar button that emits `GraphCommands.restartLayout()`; the status bar layout label
  `graph-workspace-status-layout-state` rendering `graph_workspace.layout_status.settling`
  ("Settling") and `graph_workspace.layout_status.idle` ("Idle"); and Xvfb, `xdotool`, and
  ImageMagick (`import`, `identify`, `convert`, `compare`, `montage`).
- Produces: the committed evidence files named above, plus the uncommitted copies under the
  git-ignored build directory and the PM run reports directory. The committed copy is limited to
  `docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle/` per the plan's
  Global Constraints.

The procedure below follows the repository skill
`/home/henry/.pi/agent/projects-memory/freeplane/skills/capture-freeplane-real-app-ui-evidence/SKILL.md`.
Every command runs in the lane worktree (the directory printed by
`git rev-parse --show-toplevel` from the task's working directory); never build or capture in a
different checkout. Never touch the user's own config under `/home/henry/.config/freeplane`,
never use display `:0`, and never kill a process this task did not start.

- [ ] **Step 1: Build the launcher from the lane worktree**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
git rev-parse HEAD
```

Expected: `BUILD SUCCESSFUL`, `BIN/` refreshed in the lane worktree, and the printed revision is
the Task 2 commit. `BIN` is git-ignored; never stage anything from it.

- [ ] **Step 2: Seed an isolated home, config, maps, and workspace**

```bash
RUN=/tmp/fp-focus-evidence
rm -rf "$RUN"
mkdir -p "$RUN/home" "$RUN/config/freeplane/1.12.x" "$RUN/maps"
for name in alpha beta gamma; do
  cat > "$RUN/maps/$name.mm" <<EOF
<map version="freeplane 1.12.15">
  <node TEXT="$name root" ID="ID_${name}_ROOT">
    <node TEXT="$name one" ID="ID_${name}_ONE">
      <graph_group version="1"/>
    </node>
    <node TEXT="$name two" ID="ID_${name}_TWO">
      <graph_group version="1"/>
    </node>
    <node TEXT="$name three" ID="ID_${name}_THREE">
      <graph_group version="1"/>
    </node>
    <node TEXT="$name four" ID="ID_${name}_FOUR">
      <graph_group version="1"/>
    </node>
    <node TEXT="$name five" ID="ID_${name}_FIVE">
      <graph_group version="1"/>
    </node>
  </node>
</map>
EOF
done
cat > "$RUN/maps/focus.fpg" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<graph-workspace format-version="1" id="00000000-0000-0000-0000-0000000000F1">
    <maps>
        <map id="00000000-0000-0000-0000-0000000000A1" sequence="1" uri="alpha.mm" active="true" color="#4E79A7"/>
        <map id="00000000-0000-0000-0000-0000000000A2" sequence="2" uri="beta.mm" active="true" color="#F28E2B"/>
        <map id="00000000-0000-0000-0000-0000000000A3" sequence="3" uri="gamma.mm" active="false" color="#59A14F"/>
    </maps>
    <relationships/>
    <pins/>
    <viewport center-x="0.0" center-y="0.0" zoom="1.0"/>
    <display-settings show-arrowheads="true" canvas-theme="LIGHT" remember-viewport="true" dim-unrelated-nodes="false"/>
</graph-workspace>
EOF
cat > "$RUN/config/freeplane/1.12.x/auto.properties" <<EOF
create_new_map_if_no_maps_are_loaded=false
always_load_last_maps=false
load_last_maps=false
load_last_map=false
graph_workspace_recent_workspaces=$RUN/maps/focus.fpg
EOF
ls -R "$RUN/maps" "$RUN/config"
```

The workspace has two active maps (`alpha`, `beta`) and one inactive map (`gamma`), and each map
root carries five graph-group-marked children so the restart-layout settle publishes visibly
moving frames. `load_last_maps=false` and `create_new_map_if_no_maps_are_loaded=false` prevent
any other map from opening first.

- [ ] **Step 3: Start Xvfb on a display other than :0 and launch the application**

```bash
RUN=/tmp/fp-focus-evidence
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp > "$RUN/xvfb.log" 2>&1 &
echo "xvfb=$!" > "$RUN/pids.txt"
sleep 1
cd "$(git rev-parse --show-toplevel)/BIN"
DISPLAY=:99 HOME="$RUN/home" XDG_CONFIG_HOME="$RUN/config" \
  JAVA_HOME="$JAVA_HOME" JAVA_OPTS="-Dorg.freeplane.nosplash=true" \
  ./freeplane.sh > "$RUN/app.log" 2>&1 &
echo "app=$!" >> "$RUN/pids.txt"
cat "$RUN/pids.txt"
```

Expected: display `:99` starts (if `:99` is already in use by another process, pick the next
free display number other than `:0` and use it consistently in every later command) and the
application starts with the isolated `HOME` and `XDG_CONFIG_HOME`. Capture `:99` in the evidence
README. Never kill a Freeplane process whose userfpdir is the user's own
`/home/henry/.config/freeplane`.

- [ ] **Step 4: Open the Graph Workspace from the seeded recent workspace**

```bash
RUN=/tmp/fp-focus-evidence
for attempt in $(seq 1 30); do
  if xdotool search --name '^Freeplane' >/dev/null 2>&1; then break; fi
  sleep 2
done
MAIN=$(xdotool search --name '^Freeplane' | head -1)
xdotool windowactivate --sync "$MAIN"
xdotool key --window "$MAIN" alt+v
sleep 1
import -window root "$RUN/view-menu.png"
identify "$RUN/view-menu.png"
```

Read `$RUN/view-menu.png` with the image reader and measure the integer screen coordinates of
the `Open Graph Workspace` item's centre (it is the first View-menu entry; `Alt+V` then `Enter`
is unreliable under Xvfb without a window manager). Click the measured coordinates, then wait
for the Graph Workspace window:

```bash
RUN=/tmp/fp-focus-evidence
xdotool mousemove <measured-x> <measured-y> click 1
for attempt in $(seq 1 15); do
  if xdotool search --name '^Graph Workspace$' >/dev/null 2>&1; then break; fi
  sleep 2
done
GRAPH=$(xdotool search --name '^Graph Workspace$' | head -1)
echo "GRAPH=$GRAPH" >> "$RUN/pids.txt"
xdotool windowactivate --sync "$GRAPH"
sleep 8
xdotool getwindowgeometry --shell "$GRAPH"
```

Expected: one `Graph Workspace` window id; `$RUN/app.log` shows the graph bundle active and the
seeded workspace loaded; the Maps sidebar shows the `alpha` and `beta` active rows and the
`gamma` inactive row. If no window appears within 30 s, inspect the View menu capture and
re-measure the item coordinates before retrying.

- [ ] **Step 5: Measure the click targets and the canvas rectangle from a baseline capture**

```bash
RUN=/tmp/fp-focus-evidence
GRAPH=$(xdotool search --name '^Graph Workspace$' | head -1)
xdotool windowactivate --sync "$GRAPH"
sleep 3
import -window "$GRAPH" "$RUN/baseline.png"
identify "$RUN/baseline.png"
xdotool getwindowgeometry --shell "$GRAPH"
```

Read `$RUN/baseline.png` with the image reader and record, as shell variables in
`$RUN/targets.env`:

- `ROW_X` / `ROW_Y`: the centre of the `beta` active map row in the Maps sidebar (the second
  active row; do not use the `alpha` row).
- `RESTART_X` / `RESTART_Y`: the centre of the `Restart Layout` button at the right end of the
  status bar.
- `CANVAS_CROP`: a `WxH+X+Y` crop of the canvas area only — right of the sidebar divider and
  above the status bar, showing nodes and no sidebar or status text. For a 1280x800 window the
  default is `980x640+300+40`; verify by cropping one capture and reading it.

```bash
RUN=/tmp/fp-focus-evidence
cat > "$RUN/targets.env" <<'EOF'
ROW_X=<measured row centre x>
ROW_Y=<measured row centre y>
RESTART_X=<measured restart button centre x>
RESTART_Y=<measured restart button centre y>
CANVAS_CROP=980x640+300+40
EOF
cat "$RUN/targets.env"
```

- [ ] **Step 6: Trigger a fresh settle and capture the C1-C5 witness sequence**

```bash
RUN=/tmp/fp-focus-evidence
PM_REPORTS=/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports
source "$RUN/targets.env"
GRAPH=$(xdotool search --name '^Graph Workspace$' | head -1)
capture() {
  printf '%s %s display=%s rect=%s\n' "$1" "$(date --iso-8601=seconds)" "$DISPLAY" \
    "$(xdotool getwindowgeometry --shell "$GRAPH" | tr '\n' ' ')" >> "$RUN/captures.txt"
  import -window "$GRAPH" "$RUN/$1.png"
}
: > "$RUN/captures.txt"
export DISPLAY=:99
xdotool windowactivate --sync "$GRAPH"
xdotool mousemove "$RESTART_X" "$RESTART_Y" click 1
capture C1
sleep 1
capture C2
xdotool mousemove "$ROW_X" "$ROW_Y" click 1
capture C3
sleep 1
capture C4
for attempt in $(seq 1 40); do
  import -window "$GRAPH" "$RUN/idle-a.png"
  sleep 1
  import -window "$GRAPH" "$RUN/idle-b.png"
  difference=$(compare -metric AE "$RUN/idle-a.png" "$RUN/idle-b.png" null: 2>&1 || true)
  if [ "$difference" = "0" ]; then break; fi
done
capture C5
cat "$RUN/captures.txt"
ls -l "$RUN"/C[1-5].png
```

Then read `C1.png` through `C5.png` with the image reader and record, in `$RUN/statuses.txt`, the
observed layout-status text of each capture (the `graph-workspace-status-layout-state` label in
the status bar), exactly one line per capture, for example `C1: Settling`. Also record which row
is highlighted in C3 and C4 and that the sidebar action buttons are enabled in C3 and C5.

- [ ] **Step 7: Verify the witness is falsifiable, and re-run if it is not**

```bash
RUN=/tmp/fp-focus-evidence
source "$RUN/targets.env"
for name in C1 C2 C3 C4 C5; do
  convert "$RUN/$name.png" -crop "$CANVAS_CROP" +repage "$RUN/$name-canvas.png"
done
compare -metric AE "$RUN/C1-canvas.png" "$RUN/C2-canvas.png" null: > "$RUN/ae-c1-c2.txt" 2>&1 || true
compare -metric AE "$RUN/C2-canvas.png" "$RUN/C3-canvas.png" null: > "$RUN/ae-c2-c3.txt" 2>&1 || true
compare -metric AE "$RUN/C3-canvas.png" "$RUN/C4-canvas.png" null: > "$RUN/ae-c3-c4.txt" 2>&1 || true
paste -d' ' "$RUN/ae-c1-c2.txt" "$RUN/ae-c2-c3.txt" "$RUN/ae-c3-c4.txt"
cat "$RUN/statuses.txt"
```

Acceptance rule (spec section 8.3): the sequence counts as evidence only when

- `C1`, `C2`, `C3`, and `C4` each show the `Settling` layout-status text;
- the C2-vs-C1, C3-vs-C2, and C4-vs-C3 canvas pixel differences (`compare -metric AE`) are all
  nonzero, proving the settle loop was still publishing frames at the click;
- `C3` shows the clicked `beta` row highlighted with its action buttons enabled;
- `C5` shows the layout-status text returned to `Idle` (or `Empty`) with the same `beta` row
  still highlighted and its action buttons still enabled.

If any condition fails — in particular if C2/C3/C4 no longer show `Settling`, or a required
canvas difference is `0` — the sequence does not count: repeat Step 6 (optionally after
increasing the number of graph-group children per map in `$RUN/maps/*.mm` and re-seeding the
workspace) until every condition holds. Do not record a failed sequence as evidence.

- [ ] **Step 8: Assemble the combined image, write the README, and copy the evidence**

```bash
cd "$(git rev-parse --show-toplevel)"
RUN=/tmp/fp-focus-evidence
export DISPLAY=:99
EVIDENCE=docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle
PM_REPORTS=/data/home/henry-arch/.local/state/pi/project-manager/runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-110607-a8bc2a4d/reports
REV=$(git rev-parse HEAD)
mkdir -p "$EVIDENCE" "$PM_REPORTS/map-entry-focus-mid-settle" \
  freeplane_plugin_graph/build/evidence/map-entry-focus-mid-settle
montage -label 'C1 settling' "$RUN/C1.png" \
        -label 'C2 settling (canvas differs)' "$RUN/C2.png" \
        -label 'C3 click mid-settle' "$RUN/C3.png" \
        -label 'C4 settling after click (canvas differs)' "$RUN/C4.png" \
        -label 'C5 idle: focus retained' "$RUN/C5.png" \
        -tile 2x3 -geometry 620x390+6+6 -background '#202020' -fill white \
        "$RUN/combined-annotated.png"
identify "$RUN/combined-annotated.png"
{
  echo "# Map-Entry Focus Mid-Settle Acceptance Evidence"
  echo
  echo "- Source revision: $REV"
  echo "- Build: gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline"
  echo "- Launch: cd BIN && DISPLAY=$DISPLAY HOME=$RUN/home XDG_CONFIG_HOME=$RUN/config JAVA_HOME=$JAVA_HOME JAVA_OPTS=-Dorg.freeplane.nosplash=true ./freeplane.sh"
  echo "- Run environment: Xvfb 1600x1000x24 on display $DISPLAY; isolated HOME and XDG_CONFIG_HOME; workspace seed $RUN/maps/focus.fpg with two active maps (alpha, beta) and one inactive map (gamma)"
  echo
  echo "## Capture log (name, absolute timestamp, display, window rectangle)"
  echo
  cat "$RUN/captures.txt"
  echo
  echo "## Observed layout-status text"
  echo
  cat "$RUN/statuses.txt"
  echo
  echo "## Canvas witness checks (nonzero pixel count proves frames still moving)"
  echo
  echo "- C2 vs C1: AE=$(cat "$RUN/ae-c1-c2.txt")"
  echo "- C3 vs C2: AE=$(cat "$RUN/ae-c2-c3.txt")"
  echo "- C4 vs C3: AE=$(cat "$RUN/ae-c3-c4.txt")"
  echo
  echo "## Result"
  echo
  echo "The beta row click landed while the status bar still showed Settling; the canvas changed across C1-C4; after settle (C5, Idle) the same row is still highlighted with its action buttons enabled."
} > "$RUN/README.md"
cp "$RUN/C1.png" "$RUN/C2.png" "$RUN/C3.png" "$RUN/C4.png" "$RUN/C5.png" \
   "$RUN/combined-annotated.png" "$RUN/README.md" "$EVIDENCE/"
cp "$RUN/C1.png" "$RUN/C2.png" "$RUN/C3.png" "$RUN/C4.png" "$RUN/C5.png" \
   "$RUN/combined-annotated.png" "$RUN/README.md" "$RUN/app.log" \
   "$PM_REPORTS/map-entry-focus-mid-settle/"
cp "$RUN/C1.png" "$RUN/C2.png" "$RUN/C3.png" "$RUN/C4.png" "$RUN/C5.png" \
   "$RUN/combined-annotated.png" "$RUN/app.log" \
   freeplane_plugin_graph/build/evidence/map-entry-focus-mid-settle/
cat "$RUN/README.md"
ls -l "$EVIDENCE"
```

- [ ] **Step 9: Stop only the processes this task started, and confirm the tree**

```bash
cd "$(git rev-parse --show-toplevel)"
RUN=/tmp/fp-focus-evidence
pgrep -af "org.freeplane.userfpdir=$RUN/config/freeplane" || true
pkill -f "org.freeplane.userfpdir=$RUN/config/freeplane" 2>/dev/null || true
kill "$(sed -n 's/^app=//p' "$RUN/pids.txt")" 2>/dev/null || true
kill "$(sed -n 's/^xvfb=//p' "$RUN/pids.txt")" 2>/dev/null || true
sleep 2
git status --porcelain
```

Expected: `pgrep` lists only the application launched in Step 3 (identified by the isolated
`userfpdir`); the user's own Freeplane instance and display are untouched; `git status --porcelain`
lists only untracked files under
`docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle/` (and nothing else).

- [ ] **Step 10: Commit the acceptance evidence**

```bash
cd "$(git rev-parse --show-toplevel)"
git add docs/superpowers/evidence/2026-10-13-map-entry-focus-during-graph-settle
git commit -m "Record map-entry focus mid-settle acceptance evidence [2026-10-13-map-entry-focus-during-graph-settle]"
git log -1 --format="%H %s"
git show --stat --oneline HEAD
```

Expected: one commit containing only the six evidence files (README, combined annotated image,
and the five panels) under the evidence directory, with a message ending in
`[2026-10-13-map-entry-focus-during-graph-settle]`. The transient `/tmp` captures, the
git-ignored `build/evidence/` copies, the PM run reports copies, and `BIN/` are never committed.
