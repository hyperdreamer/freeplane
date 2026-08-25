package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphTheme;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspacePresentation;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.resources.ResourceController;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphWorkspaceWindowModelShould {
    private static final Path OPEN_PATH = Paths.get("/tmp/opened.graph-workspace");
    private static final MapReferenceId ACTIVE_ID = id(1L);
    private static final java.util.List<EdtResources> RESOURCES =
        new java.util.ArrayList<EdtResources>();
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
        if (!RESOURCES.isEmpty()) {
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    for (EdtResources resource : RESOURCES) {
                        resource.closeOnEdt();
                    }
                }
            });
            RESOURCES.clear();
        }
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
    public void rendersVisibleShellControlsFromGraphWorkspaceResourceKeys() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().openButton().getText()).isEqualTo("graph_workspace.action.open");
        assertThat(model.toolbar().saveButton().getText()).isEqualTo("graph_workspace.action.save");
        assertThat(model.settingsPanel().getComponent(0).getName()).isEqualTo("graph-workspace-settings-heading");
        assertThat(model.mapList().getComponent(0).getName()).isEqualTo("graph-workspace-map-list-heading");
        model.close();
    }

    @Test
    public void composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.menuBar()).isNotNull();
        assertThat(model.toolbar()).isNotNull();
        assertThat(model.mapList()).isNotNull();
        assertThat(model.canvas()).isInstanceOf(GraphCanvas.class);
        assertThat(model.settingsPanel()).isNotNull();
        assertThat(model.statusSlot()).isNotNull();
        assertThat(model.statusSlot().getName()).isEqualTo("graph-workspace-status-slot");
        assertThat(model.mapList().getPreferredSize().width).isGreaterThan(0);
        assertThat(model.settingsPanel().getPreferredSize().width).isGreaterThan(0);
        assertThat(model.toolbar().getPreferredSize().height).isGreaterThan(0);
        assertThat(model.canvas().getPreferredSize()).isEqualTo(new Dimension(800, 560));
        assertThat(model.toolbar().approvedControlNames()).contains(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin", "unpin");
        assertThat(model.settingsPanel().approvedSettingNames()).contains(
            "show-arrowheads", "canvas-theme", "remember-viewport", "dim-unrelated");
        assertThat(model.mapList().rowHeight()).isEqualTo(MapListPanel.ROW_HEIGHT);
        assertThat(model.toolbar().getComponentCount()).isGreaterThan(0);
        assertThat(model.settingsPanel().getComponentCount()).isGreaterThan(0);
        assertThat(UIManager.getLookAndFeel()).isNotNull();

        JPanel graphArea = (JPanel) model.content().getComponent(1);
        assertThat(model.content().getComponentCount()).isEqualTo(3);
        assertThat(graphArea.getComponentCount()).isEqualTo(3);
        assertThat(graphArea.getComponent(0)).isSameAs(model.mapList());
        assertThat(graphArea.getComponent(1)).isSameAs(model.canvas());
        assertThat(graphArea.getComponent(2)).isSameAs(model.settingsPanel());
        model.close();
    }

    @Test
    public void routesApplicationOpenToTheApplicationControllerAndSessionActionsToTheHandle() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.of(false, true, true, false, Collections.<MapReferenceId>emptySet(),
                java.util.Optional.<org.freeplane.plugin.graph.command.MapUndoTarget>empty()));
        GraphWorkspaceWindowModel model = fixture.model();

        model.toolbar().openButton().doClick();
        model.toolbar().saveButton().doClick();
        model.toolbar().undoButton().doClick();
        model.toolbar().redoButton().doClick();
        model.toolbar().zoomInButton().doClick();
        model.toolbar().resetZoomButton().doClick();
        model.settingsPanel().showArrowheads().doClick();

        verify(fixture.applicationController).open(OPEN_PATH);
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(6)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").contains(
            GraphCommands.Save.class, GraphCommands.UndoWorkspace.class, GraphCommands.RedoWorkspace.class,
            GraphCommands.Viewport.class, GraphCommands.Display.class);
        model.close();
    }

    @Test
    public void publishesOneLocalizedMessageForRejectedCommand() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.rejected(emptyDocument(), "graph_workspace.test.rejected", "argument")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);

        model.execute(GraphCommands.save());

        assertThat(messages).containsExactly("graph_workspace.test.rejected[argument]");
        model.close();
    }

    @Test
    public void publishesOneLocalizedMessageForNoOpCommand() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.noOp(emptyDocument(), "graph_workspace.test.no_op", "argument")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);

        model.execute(GraphCommands.save());

        assertThat(messages).containsExactly("graph_workspace.test.no_op[argument]");
        model.close();
    }

    @Test
    public void doesNotPublishMessageForAppliedCommand() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.applied(emptyDocument(), "graph_workspace.test.applied", "argument")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);

        model.execute(GraphCommands.save());

        assertThat(messages).isEmpty();
        model.close();
    }

    @Test
    public void exposesAllMapRowStatesWithProjectedCountsAndEmitsSessionCommandsOnCanvasUpdates() {
        MapReferenceId loadingId = id(2L);
        MapReferenceId missingId = id(3L);
        MapReferenceId retryId = id(4L);
        MapReferenceId inactiveId = id(5L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE),
            registration(loadingId, "Loading", MapAvailability.LOADING),
            registration(missingId, "Missing", MapAvailability.MISSING),
            registration(retryId, "Retry", MapAvailability.UNREADABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false);
        GraphWorkspaceWindowModel model = fixture.model();

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)));

        assertThat(model.mapList().rows()).extracting(MapListPanel.MapRow::state).containsExactly(
            MapListPanel.RowState.ACTIVE, MapListPanel.RowState.LOADING, MapListPanel.RowState.MISSING,
            MapListPanel.RowState.RETRYABLE, MapListPanel.RowState.INACTIVE);
        assertThat(model.mapList().rows().get(0).projectedNodeCount()).isEqualTo(1);
        assertThat(model.mapList().rows().get(3).projectedNodeCount()).isEqualTo(0);

        model.mapList().selectMap(missingId);
        model.mapList().locateButton().doClick();
        model.mapList().selectMap(retryId);
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(retryId);
        assertThat(model.mapList().rows().get(3).selected()).isTrue();
        model.mapList().retryButton().doClick();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.times(2)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").containsExactly(
            GraphCommands.LocateMap.class, GraphCommands.RetryMap.class);
        model.close();
    }

    @Test
    public void routesMapActionsToTheWorkspaceHandle() {
        MapReferenceId activeId = id(21L);
        MapReferenceId missingId = id(22L);
        MapReferenceId retryId = id(23L);
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
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().saveButton().isEnabled()).isFalse();
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        assertThat(model.toolbar().unpinButton().isEnabled()).isFalse();
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.settingsPanel().isReadOnly()).isTrue();
        assertThat(model.mapList().rows().get(0).state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        model.close();
    }

    @Test
    public void startsWithPersistedPresentationValuesAndAppliesThePersistedThemeBeforePainting() {
        DisplaySettings settings = DisplaySettings.of(false, CanvasTheme.DARK, false, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.settingsPanel().showArrowheads().isSelected()).isFalse();
        assertThat(model.settingsPanel().canvasTheme().getSelectedItem()).isEqualTo(CanvasTheme.DARK);
        assertThat(model.settingsPanel().rememberViewport().isSelected()).isFalse();
        assertThat(model.settingsPanel().dimUnrelated().isSelected()).isFalse();
        BufferedImage image = paintCanvas(model.canvas());
        assertThat(image.getRGB(0, 0)).isEqualTo(GraphTheme.resolve(CanvasTheme.DARK,
            palette(ACTIVE_ID)).background().getRGB());
        model.close();
    }

    @Test
    public void refreshesCanvasPresentationAfterADisplayCommandWithoutResettingOtherSettings() {
        DisplaySettings initial = DisplaySettings.of(true, CanvasTheme.LIGHT, true, true,
            emptyUnknownXml());
        DisplaySettings changed = DisplaySettings.of(false, CanvasTheme.DARK, true, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(initial, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();
        when(fixture.binding.currentPresentation()).thenReturn(presentation(changed, ACTIVE_ID));

        model.execute(GraphCommands.display(changed));

        assertThat(model.settingsPanel().showArrowheads().isSelected()).isFalse();
        assertThat(model.settingsPanel().canvasTheme().getSelectedItem()).isEqualTo(CanvasTheme.DARK);
        assertThat(model.settingsPanel().rememberViewport().isSelected()).isTrue();
        assertThat(model.settingsPanel().dimUnrelated().isSelected()).isFalse();
        assertThat(paintCanvas(model.canvas()).getRGB(0, 0)).isEqualTo(GraphTheme.resolve(CanvasTheme.DARK,
            palette(ACTIVE_ID)).background().getRGB());
        verify(fixture.handle).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void paintsAVisibleEnclosureThroughTheWindowModel() {
        DisplaySettings settings = DisplaySettings.defaults();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            enclosureState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();

        BufferedImage image = paintCanvas(model.canvas());

        assertThat(nonBackgroundPixels(image, image.getRGB(0, 0))).isGreaterThan(0);
        model.close();
    }

    @Test
    public void fitsWhenRememberViewportIsDisabledEvenIfThePersistedViewportOverlaps() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.LIGHT, false, true,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);
        model.close();
    }

    @Test
    public void keepsMenuEnablementAlignedWithReadOnlyAndIndependentHistoryRules() {
        WorkspaceSessionStatus status = WorkspaceSessionStatus.of(true, true, true, false,
            Collections.<MapReferenceId>emptySet(),
            java.util.Optional.of(new org.freeplane.plugin.graph.command.MapUndoTarget(ACTIVE_ID,
                "Active", true)));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            status, presentation(DisplaySettings.defaults(), ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(ACTIVE_ID);

        assertThat(menuItem(model, "save").isEnabled()).isTrue();
        assertThat(menuItem(model, "save-as").isEnabled()).isTrue();
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "remove-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "fit-graph").isEnabled()).isTrue();
        assertThat(menuItem(model, "reset-zoom").isEnabled()).isTrue();
        assertThat(menuItem(model, "settings").isEnabled()).isTrue();
        assertThat(menuItem(model, "undo").isEnabled()).isTrue();
        assertThat(menuItem(model, "redo").isEnabled()).isTrue();
        assertThat(menuItem(model, "undo-source-map").isEnabled()).isTrue();

        model.setReadOnly(true);

        assertThat(menuItem(model, "save").isEnabled()).isFalse();
        assertThat(menuItem(model, "save-as").isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "remove-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "fit-graph").isEnabled()).isTrue();
        assertThat(menuItem(model, "reset-zoom").isEnabled()).isTrue();
        assertThat(menuItem(model, "settings").isEnabled()).isTrue();
        assertThat(model.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(menuItem(model, "undo").isEnabled()).isFalse();
        assertThat(menuItem(model, "redo").isEnabled()).isFalse();
        assertThat(menuItem(model, "undo-source-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void fitsAnOutOfRangeStateDeliveredBeforeInitialLayoutExactlyOnce() {
        Viewport persisted = Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml());
        Fixture fixture = fixture(persisted, emptyState(),
            Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(), false);
        GraphWorkspaceWindowModel model = fixture.modelWithoutLayout();

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));
        assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
        model.completeInitialLayout();
        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(20_000.0, 20_000.0)));
        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        model.close();
    }

    @Test
    public void appliesThePersistedViewportAndFitsWhenTheFirstNonEmptyCanvasStateDoesNotOverlap() {
        Viewport persisted = Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml());
        Fixture fixture = fixture(persisted, emptyState(), Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(),
            false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));

        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);
        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(20_000.0, 20_000.0)));
        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        model.close();
    }

    @Test
    public void preservesThePersistedViewportWhenTheFirstNonEmptyCanvasStateOverlaps() {
        Viewport persisted = Viewport.of(10_000.0, 10_000.0, 1.0, emptyUnknownXml());
        Fixture fixture = fixture(persisted, emptyState(), Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(),
            false);
        GraphWorkspaceWindowModel model = fixture.model();

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));

        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().zoom()).isEqualTo(1.0);
        model.close();
    }

    @Test
    public void closesTheHeadlessShellWithoutShowingIt() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        model.close();
        model.close();

        verify(fixture.registration).close();
        verify(fixture.sessionRegistration).close();
    }

    @Test
    public void factoryDoesNotPublishOrShowAViewBeforeConstructionCompletes() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        SwingGraphWorkspaceViewFactory factory = new SwingGraphWorkspaceViewFactory(
            fixture.applicationController, () -> OPEN_PATH);

        GraphWorkspaceView view = factory.create(fixture.handle, fixture.binding, fixture.closeController);

        if (view instanceof GraphWorkspaceWindow) {
            assertThat(((GraphWorkspaceWindow) view).isVisible()).isFalse();
        }
        else {
            assertThat(view).isInstanceOf(HeadlessGraphWorkspaceView.class);
            assertThat(((HeadlessGraphWorkspaceView) view).isVisible()).isFalse();
        }
        view.close();
        resourceScope.close();
    }

    @Test
    public void showsAWorkspaceWindowAfterOpeningIt() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(fixture.applicationController, () -> OPEN_PATH)
            .create(fixture.handle, fixture.binding, fixture.closeController);
        assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
        GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;

        try {
            window.show();

            assertThat(window.isVisible()).isTrue();
        }
        finally {
            window.close();
            resourceScope.close();
        }
    }

    @Test
    public void showsAHiddenWorkspaceWindowWhenFocusingIt() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(fixture.applicationController, () -> OPEN_PATH)
            .create(fixture.handle, fixture.binding, fixture.closeController);
        assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
        GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;

        try {
            assertThat(window.isVisible()).isFalse();
            window.focus();

            assertThat(window.isVisible()).isTrue();
        }
        finally {
            window.close();
            resourceScope.close();
        }
    }

    private static GraphCommandResult commandResult(final WorkspaceTransition transition) {
        return GraphCommandResult.from(transition);
    }

    private static WorkspaceDocument emptyDocument() {
        return WorkspaceDocument.createVersion1(WorkspaceId.of(
            "00000000-0000-0000-0000-000000000001"));
    }
    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly) {
        return fixture(viewport, state, registrations, readOnly, WorkspaceSessionStatus.empty(),
            presentation(DisplaySettings.defaults(), ACTIVE_ID));
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus) {
        return fixture(viewport, state, registrations, readOnly, sessionStatus,
            presentation(DisplaySettings.defaults(), ACTIVE_ID));
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus, GraphWorkspacePresentation presentation) {
        GraphWorkspaceController applicationController = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        WorkspaceCloseController closeController = mock(WorkspaceCloseController.class);
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        ListenerRegistration registration = mock(ListenerRegistration.class);
        ListenerRegistration sessionRegistration = mock(ListenerRegistration.class);
        when(binding.currentViewport()).thenReturn(viewport);
        when(binding.currentCanvasState()).thenReturn(state);
        when(binding.currentMapRows()).thenReturn(registrations);
        when(binding.isReadOnly()).thenReturn(readOnly);
        when(binding.currentPresentation()).thenReturn(presentation);
        when(binding.currentSessionStatus()).thenReturn(sessionStatus);
        when(binding.addCanvasStateListener(any())).thenReturn(registration);
        when(binding.addSessionStatusListener(any())).thenReturn(sessionRegistration);
        return new Fixture(applicationController, handle, closeController, binding, registration,
            sessionRegistration);
    }

    private static GraphWorkspaceViewBinding.MapRegistration registration(MapReferenceId id, String name,
            MapAvailability availability) {
        return GraphWorkspaceViewBinding.MapRegistration.of(id, name, availability);
    }

    private static MapReferenceId id(long value) {
        return MapReferenceId.of(UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value)));
    }

    private static List<UnknownXml> emptyUnknownXml() {
        return Collections.emptyList();
    }

    private static CanvasState emptyState() {
        return CanvasState.of(0L, GraphProjection.structure(0L, Collections.emptyList(), Collections.emptyList()),
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.LOADING);
    }

    private static CanvasState nodeState(MapReferenceId mapId, LayoutPoint center) {
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

    private static CanvasState enclosureState(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.transientPath(mapId, Collections.emptyList());
        EnclosureKey endpoint = EnclosureKey.of(source);
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of("Enclosure", "Enclosure")), "Map",
            java.util.Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC);
        LayoutPoint anchor = LayoutPoint.of(0.0, 0.0);
        HullGeometry hull = HullGeometry.of(Arrays.asList(LayoutPoint.of(-30.0, -20.0),
            LayoutPoint.of(30.0, -20.0), LayoutPoint.of(30.0, 20.0), LayoutPoint.of(-30.0, 20.0)), anchor);
        GraphProjection projection = GraphProjection.projected(0L,
            Collections.<ProjectedNode>emptyList(), Collections.singletonList(enclosure),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
            Collections.singletonMap(hullKey, hull), Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(),
            Collections.singletonMap(hullKey, anchor)), false);
        return CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE);
    }

    private static GraphWorkspacePresentation presentation(final DisplaySettings settings,
            final MapReferenceId... ids) {
        List<GraphWorkspacePresentation.MapColor> colors = new java.util.ArrayList<GraphWorkspacePresentation.MapColor>();
        String[] palette = new String[] {"#4E79A7", "#E15759", "#59A14F", "#F28E2B"};
        for (int index = 0; index < ids.length; index++) {
            colors.add(GraphWorkspacePresentation.MapColor.of(ids[index], palette[index % palette.length]));
        }
        return GraphWorkspacePresentation.of(settings, colors);
    }

    private static Map<MapReferenceId, String> palette(final MapReferenceId... ids) {
        Map<MapReferenceId, String> result = new LinkedHashMap<MapReferenceId, String>();
        String[] values = new String[] {"#4E79A7", "#E15759", "#59A14F", "#F28E2B"};
        for (int index = 0; index < ids.length; index++) {
            result.put(ids[index], values[index % values.length]);
        }
        return result;
    }

    private static BufferedImage paintCanvas(final GraphCanvas canvas) {
        int width = Math.max(1, canvas.getWidth());
        int height = Math.max(1, canvas.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static int nonBackgroundPixels(final BufferedImage image, final int background) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }

    private static JMenuItem menuItem(final GraphWorkspaceWindowModel model, final String name) {
        final String expected = "graph-workspace-menu-item-" + name;
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            JMenu menu = model.menuBar().getMenu(menuIndex);
            for (Component component : menu.getMenuComponents()) {
                if (component instanceof JMenuItem && expected.equals(component.getName())) {
                    return (JMenuItem) component;
                }
            }
        }
        throw new AssertionError("Missing menu item " + expected);
    }

    private static final class Fixture {
        private final GraphWorkspaceController applicationController;
        private final GraphWorkspaceHandle handle;
        private final WorkspaceCloseController closeController;
        private final GraphWorkspaceViewBinding binding;
        private final ListenerRegistration registration;
        private final ListenerRegistration sessionRegistration;

        private Fixture(GraphWorkspaceController applicationController, GraphWorkspaceHandle handle,
                WorkspaceCloseController closeController, GraphWorkspaceViewBinding binding,
                ListenerRegistration registration, ListenerRegistration sessionRegistration) {
            this.applicationController = applicationController;
            this.handle = handle;
            this.closeController = closeController;
            this.binding = binding;
            this.registration = registration;
            this.sessionRegistration = sessionRegistration;
        }

        private GraphWorkspaceWindowModel model() {
            final GraphWorkspaceWindowModel result = modelWithoutLayout();
            result.completeInitialLayout();
            return result;
        }

        private GraphWorkspaceWindowModel modelWithoutLayout() {
            return modelWithoutLayout(message -> { });
        }

        private GraphWorkspaceWindowModel model(final Consumer<String> commandMessageSink) {
            final GraphWorkspaceWindowModel result = modelWithoutLayout(commandMessageSink);
            result.completeInitialLayout();
            return result;
        }

        private GraphWorkspaceWindowModel modelWithoutLayout(final Consumer<String> commandMessageSink) {
            final GraphWorkspaceWindowModel[] result = new GraphWorkspaceWindowModel[1];
            final EdtResources[] edtResources = new EdtResources[1];
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    edtResources[0] = new EdtResources();
                    result[0] = new GraphWorkspaceWindowModel(handle, binding, applicationController,
                        () -> OPEN_PATH, closeController, () -> { }, () -> { }, () -> { }, commandMessageSink);
                }
            });
            RESOURCES.add(edtResources[0]);
            return result[0];
        }
    }

    private static final class EdtResources {
        private final MockedStatic<TextUtils> textUtils;
        private final MockedStatic<ResourceController> resourceController;
        private boolean closed;

        private EdtResources() {
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

        private void closeOnEdt() {
            if (!closed) {
                closed = true;
                textUtils.close();
                resourceController.close();
            }
        }
    }
}
