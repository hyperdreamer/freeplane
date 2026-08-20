package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GraphWorkspaceWindowModelShould {
    private static final Path OPEN_PATH = Paths.get("/tmp/opened.graph-workspace");

    @Test
    public void composesAHiddenModelessWorkspaceWithStablePanelsAndApprovedControls() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, Collections.<UnknownXml>emptyList()),
            nodeState(LayoutPoint.of(0.0, 0.0)));

        GraphWorkspaceWindow window = fixture.window();

        assertThat(window.isVisible()).isFalse();
        assertThat(window.getModalExclusionType()).isEqualTo(java.awt.Dialog.ModalExclusionType.NO_EXCLUDE);
        assertThat(window.getJMenuBar()).isNotNull();
        assertThat(window.toolbar()).isNotNull();
        assertThat(window.mapList()).isNotNull();
        assertThat(window.canvas()).isInstanceOf(GraphCanvas.class);
        assertThat(window.settingsPanel()).isNotNull();
        assertThat(window.statusSlot()).isNotNull();
        assertThat(window.statusSlot().getName()).isEqualTo("graph-workspace-status-slot");
        assertThat(window.mapList().getPreferredSize().width).isGreaterThan(0);
        assertThat(window.settingsPanel().getPreferredSize().width).isGreaterThan(0);
        assertThat(window.toolbar().getPreferredSize().height).isGreaterThan(0);
        assertThat(window.canvas().getPreferredSize()).isEqualTo(new Dimension(800, 560));
        assertThat(window.toolbar().approvedControlNames()).contains(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin", "unpin");
        assertThat(window.settingsPanel().approvedSettingNames()).contains(
            "show-arrowheads", "canvas-theme", "remember-viewport", "dim-unrelated");
        assertThat(window.mapList().rowHeight()).isEqualTo(MapListPanel.ROW_HEIGHT);
        assertThat(window.toolbar().getComponentCount()).isGreaterThan(0);
        assertThat(window.settingsPanel().getComponentCount()).isGreaterThan(0);

        window.close();
    }

    @Test
    public void routesApplicationOpenToTheApplicationControllerAndSessionActionsToTheHandle() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, Collections.<UnknownXml>emptyList()),
            nodeState(LayoutPoint.of(0.0, 0.0)));
        GraphWorkspaceWindow window = fixture.window();

        window.toolbar().openButton().doClick();
        window.toolbar().saveButton().doClick();
        window.toolbar().undoButton().doClick();
        window.toolbar().redoButton().doClick();
        window.toolbar().zoomInButton().doClick();
        window.toolbar().resetZoomButton().doClick();
        window.settingsPanel().showArrowheads().doClick();

        verify(fixture.applicationController).open(OPEN_PATH);
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(6)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").contains(
            GraphCommands.Save.class, GraphCommands.UndoWorkspace.class, GraphCommands.RedoWorkspace.class,
            GraphCommands.Viewport.class, GraphCommands.Display.class);

        window.close();
    }

    @Test
    public void exposesAllMapRowStatesWithProjectedCountsAndEmitsSessionCommands() {
        MapReferenceId activeId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MapReferenceId loadingId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        MapReferenceId missingId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        MapReferenceId readOnlyId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        MapReferenceId retryId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        MapListPanel panel = new MapListPanel(mock(GraphWorkspaceHandle.class), () -> Paths.get("/tmp/map.mm"));
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(activeId, "Active", MapListPanel.RowState.ACTIVE, 7, false),
            MapListPanel.MapRow.of(loadingId, "Loading", MapListPanel.RowState.LOADING, 0, false),
            MapListPanel.MapRow.of(missingId, "Missing", MapListPanel.RowState.MISSING, 0, false),
            MapListPanel.MapRow.of(readOnlyId, "Read only", MapListPanel.RowState.READ_ONLY, 2, false),
            MapListPanel.MapRow.of(retryId, "Retry", MapListPanel.RowState.RETRYABLE, 1, true)));

        assertThat(panel.rows()).extracting(MapListPanel.MapRow::state).containsExactly(
            MapListPanel.RowState.ACTIVE, MapListPanel.RowState.LOADING, MapListPanel.RowState.MISSING,
            MapListPanel.RowState.READ_ONLY, MapListPanel.RowState.RETRYABLE);
        assertThat(panel.rows().get(0).projectedNodeCount()).isEqualTo(7);
        assertThat(panel.rows().get(4).selected()).isTrue();
        assertThat(panel.rowHeight()).isEqualTo(MapListPanel.ROW_HEIGHT);
    }

    @Test
    public void routesMapActionsToTheWorkspaceHandle() {
        MapReferenceId activeId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000021"));
        MapReferenceId missingId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000022"));
        MapReferenceId retryId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000023"));
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"));
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(activeId, "Active", MapListPanel.RowState.ACTIVE, 2, false),
            MapListPanel.MapRow.of(missingId, "Missing", MapListPanel.RowState.MISSING, 0, false),
            MapListPanel.MapRow.of(retryId, "Retry", MapListPanel.RowState.RETRYABLE, 1, true)));

        panel.addButton().doClick();
        panel.selectMap(missingId);
        panel.locateButton().doClick();
        panel.selectMap(retryId);
        panel.retryButton().doClick();
        panel.selectMap(activeId);
        panel.removeButton().doClick();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle, org.mockito.Mockito.times(4)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").containsExactly(
            GraphCommands.AddMap.class, GraphCommands.LocateMap.class,
            GraphCommands.RetryMap.class, GraphCommands.RemoveMap.class);
    }

    @Test
    public void disablesMutatingControlsForReadOnlySessions() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, Collections.<UnknownXml>emptyList()),
            nodeState(LayoutPoint.of(0.0, 0.0)));
        when(fixture.binding.isReadOnly()).thenReturn(true);
        GraphWorkspaceWindow window = fixture.window();

        assertThat(window.toolbar().saveButton().isEnabled()).isFalse();
        assertThat(window.toolbar().pinButton().isEnabled()).isFalse();
        assertThat(window.toolbar().unpinButton().isEnabled()).isFalse();
        assertThat(window.mapList().removeButton().isEnabled()).isFalse();
        assertThat(window.mapList().addButton().isEnabled()).isFalse();
        assertThat(window.settingsPanel().isReadOnly()).isTrue();

        window.close();
    }

    @Test
    public void appliesThePersistedViewportAndFitsWhenItDoesNotOverlapTheGraph() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Viewport persisted = Viewport.of(0.0, 0.0, 1.0, Collections.<UnknownXml>emptyList());
        Fixture fixture = fixture(persisted, nodeState(LayoutPoint.of(10_000.0, 10_000.0)));

        GraphWorkspaceWindow window = fixture.window();

        assertThat(window.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(window.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(window.canvas().viewport().zoom()).isGreaterThan(1.0);
        window.close();
    }

    @Test
    public void preservesThePersistedViewportWhenItOverlapsTheGraph() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Viewport persisted = Viewport.of(10_000.0, 10_000.0, 1.0, Collections.<UnknownXml>emptyList());
        Fixture fixture = fixture(persisted, nodeState(LayoutPoint.of(10_000.0, 10_000.0)));

        GraphWorkspaceWindow window = fixture.window();

        assertThat(window.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(window.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(window.canvas().viewport().zoom()).isEqualTo(1.0);
        window.close();
    }

    @Test
    public void delegatesWindowClosingToTheCloseControllerBeforeDisposing() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, Collections.<UnknownXml>emptyList()),
            nodeState(LayoutPoint.of(0.0, 0.0)));
        when(fixture.closeController.saveAndClose()).thenReturn(false, true);
        GraphWorkspaceWindow window = fixture.window();

        SwingUtilities.invokeAndWait(() -> window.dispatchEvent(
            new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        verify(fixture.closeController).saveAndClose();
        assertThat(window.isDisplayable()).isTrue();

        SwingUtilities.invokeAndWait(() -> window.dispatchEvent(
            new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        assertThat(window.isDisplayable()).isFalse();
        verify(fixture.registration).close();
    }

    @Test
    public void factoryDoesNotPublishOrShowAWindowBeforeConstructionCompletes() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, Collections.<UnknownXml>emptyList()),
            nodeState(LayoutPoint.of(0.0, 0.0)));
        SwingGraphWorkspaceViewFactory factory = new SwingGraphWorkspaceViewFactory(
            fixture.applicationController, () -> OPEN_PATH);

        GraphWorkspaceView view = factory.create(fixture.handle, fixture.binding, fixture.closeController);

        assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
        assertThat(((GraphWorkspaceWindow) view).isVisible()).isFalse();
        ((GraphWorkspaceWindow) view).close();
    }

    private static Fixture fixture(Viewport viewport, CanvasState state) {
        GraphWorkspaceController applicationController = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        WorkspaceCloseController closeController = mock(WorkspaceCloseController.class);
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        ListenerRegistration registration = mock(ListenerRegistration.class);
        when(binding.currentViewport()).thenReturn(viewport);
        when(binding.currentCanvasState()).thenReturn(state);
        when(binding.addCanvasStateListener(any())).thenReturn(registration);
        when(handle.currentProjection()).thenReturn(GraphProjection.structure(0L,
            Collections.emptyList(), Collections.emptyList()));
        return new Fixture(applicationController, handle, closeController, binding, registration);
    }

    private static CanvasState nodeState(LayoutPoint center) {
        MapReferenceId mapId = MapReferenceId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        SourceNodeKey source = SourceNodeKey.transientPath(mapId, Collections.emptyList());
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", false);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key, NodeGeometry.of(center, 10.0)),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, center), Collections.emptyMap()), false);
        return CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE);
    }

    private static final class Fixture {
        private final GraphWorkspaceController applicationController;
        private final GraphWorkspaceHandle handle;
        private final WorkspaceCloseController closeController;
        private final GraphWorkspaceViewBinding binding;
        private final ListenerRegistration registration;

        private Fixture(GraphWorkspaceController applicationController, GraphWorkspaceHandle handle,
                WorkspaceCloseController closeController, GraphWorkspaceViewBinding binding,
                ListenerRegistration registration) {
            this.applicationController = applicationController;
            this.handle = handle;
            this.closeController = closeController;
            this.binding = binding;
            this.registration = registration;
        }

        private GraphWorkspaceWindow window() {
            final GraphWorkspaceWindow[] result = new GraphWorkspaceWindow[1];
            try {
                SwingUtilities.invokeAndWait(() -> result[0] = new GraphWorkspaceWindow(handle, binding,
                    closeController, applicationController, () -> OPEN_PATH));
            }
            catch (Exception exception) {
                throw new AssertionError(exception);
            }
            return result[0];
        }
    }
}
