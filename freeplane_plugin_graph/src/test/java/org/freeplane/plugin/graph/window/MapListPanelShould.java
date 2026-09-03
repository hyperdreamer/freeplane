package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

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

public class MapListPanelShould {
    private static final MapReferenceId ACTIVE_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId INACTIVE_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MISSING_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000003");

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
    public void partitionActiveAndInactiveRowsIntoSeparateLists() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        assertThat(panel.activeList().getModel().getSize()).isEqualTo(1);
        assertThat(panel.activeList().getModel().getElementAt(0).mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.inactiveList().getModel().getSize()).isEqualTo(1);
        assertThat(panel.inactiveList().getModel().getElementAt(0).mapReferenceId()).isEqualTo(INACTIVE_MAP);
    }

    @Test
    public void maintainMutuallyExclusiveSelectionBetweenLists() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        panel.activeList().setSelectedIndex(0);
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(-1);

        panel.inactiveList().setSelectedIndex(0);
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    public void swapButtonLabelsAndEnablementBasedOnSelectionPartition() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        // No selection
        assertThat(panel.addButton().getText()).isEqualTo("graph_workspace.action.add_map");
        assertThat(panel.addButton().isEnabled()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("graph_workspace.action.deactivate_map");
        assertThat(panel.removeButton().isEnabled()).isFalse();

        // Active selection
        panel.selectMap(ACTIVE_MAP);
        assertThat(panel.addButton().getText()).isEqualTo("graph_workspace.action.add_map");
        assertThat(panel.addButton().isEnabled()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("graph_workspace.action.deactivate_map");
        assertThat(panel.removeButton().isEnabled()).isTrue();

        // Inactive selection
        panel.selectMap(INACTIVE_MAP);
        assertThat(panel.addButton().getText()).isEqualTo("graph_workspace.action.reactivate_map");
        assertThat(panel.addButton().isEnabled()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("graph_workspace.action.delete_map");
        assertThat(panel.removeButton().isEnabled()).isTrue();
    }

    @Test
    public void dispatchDeactivateAndPreserveSelectionInInactiveListOnNextUpdate() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false)));
        panel.selectMap(ACTIVE_MAP);

        panel.removeButton().doClick();

        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.RemoveMap.class);

        // State updates with map now inactive
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(panel.addButton().getText()).isEqualTo("graph_workspace.action.reactivate_map");
    }

    @Test
    public void dispatchReactivateAndPreserveSelectionInActiveListOnNextUpdate() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));
        panel.selectMap(INACTIVE_MAP);

        panel.addButton().doClick(); // Displays "Reactivate Map"

        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.ReactivateMap.class);

        // State updates with map now active
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 4, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(panel.removeButton().getText()).isEqualTo("graph_workspace.action.deactivate_map");
    }

    @Test
    public void promptConfirmationBeforeDeleteAndClearSelectionOnDelete() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        AtomicBoolean promptCalled = new AtomicBoolean(false);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> {
            promptCalled.set(true);
            return false; // User cancels
        });

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));
        panel.selectMap(INACTIVE_MAP);

        panel.removeButton().doClick(); // Displays "Delete Map"
        assertThat(promptCalled.get()).isTrue();
        verify(handle, never()).execute(org.mockito.ArgumentMatchers.any());

        // Now test when confirmed
        MapListPanel panelConfirmed = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);
        panelConfirmed.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));
        panelConfirmed.selectMap(INACTIVE_MAP);

        panelConfirmed.removeButton().doClick();
        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.DeleteMap.class);

        // When updated after deletion, selection is cleared
        panelConfirmed.setRows(Arrays.asList());
        assertThat(panelConfirmed.selectedRow()).isNull();
    }
}
