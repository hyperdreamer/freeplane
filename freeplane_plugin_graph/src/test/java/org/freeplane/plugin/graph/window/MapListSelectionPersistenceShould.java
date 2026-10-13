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
