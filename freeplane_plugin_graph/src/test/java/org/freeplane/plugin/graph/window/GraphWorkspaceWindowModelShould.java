package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.namespace.QName;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JViewport;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphIntent;
import org.freeplane.plugin.graph.canvas.GraphTheme;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
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
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.FrameResynchronizer;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphWorkspaceWindowModelShould {
    private static final Path OPEN_PATH = Paths.get("/tmp/opened.graph-workspace");
    private static final MapReferenceId ACTIVE_ID = id(1L);
    private static final java.util.List<EdtResources> RESOURCES =
        new java.util.ArrayList<EdtResources>();
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        assertThat(graphArea.getComponent(1)).isInstanceOf(JScrollPane.class);
        JScrollPane graphScrollPane = (JScrollPane) graphArea.getComponent(1);
        assertThat(graphScrollPane.getName()).isEqualTo("graph-workspace-scroll-pane");
        assertThat(graphScrollPane.getViewport().getView()).isSameAs(model.canvas());
        assertThat(graphArea.getComponent(2)).isSameAs(model.settingsPanel());
        model.close();
    }

    @Test
    public void rendersFourIconOnlyControlsThroughTheScalableIconLookup() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        Icon undoIcon = icon(24, 16);
        Icon redoIcon = icon(16, 16);
        Icon zoomInIcon = icon(20, 16);
        Icon zoomOutIcon = icon(28, 16);
        fixture.stubIcon("/images/undo.svg?useAccentColor=true", undoIcon);
        fixture.stubIcon("/images/redo.svg?useAccentColor=true", redoIcon);
        fixture.stubIcon("/images/ZoomIn24.svg?useAccentColor=true", zoomInIcon);
        fixture.stubIcon("/images/ZoomOut24.svg?useAccentColor=true", zoomOutIcon);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().getIcon()).isSameAs(undoIcon);
        assertThat(model.toolbar().undoButton().getText()).isNull();
        assertThat(model.toolbar().undoButton().getToolTipText())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getName()).isEqualTo("graph-workspace-undo");

        assertThat(model.toolbar().redoButton().getIcon()).isSameAs(redoIcon);
        assertThat(model.toolbar().redoButton().getText()).isNull();
        assertThat(model.toolbar().redoButton().getToolTipText())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getName()).isEqualTo("graph-workspace-redo");

        assertThat(model.toolbar().zoomInButton().getIcon()).isSameAs(zoomInIcon);
        assertThat(model.toolbar().zoomInButton().getText()).isNull();
        assertThat(model.toolbar().zoomInButton().getToolTipText())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getName()).isEqualTo("graph-workspace-zoom-in");

        assertThat(model.toolbar().zoomOutButton().getIcon()).isSameAs(zoomOutIcon);
        assertThat(model.toolbar().zoomOutButton().getText()).isNull();
        assertThat(model.toolbar().zoomOutButton().getToolTipText())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getName()).isEqualTo("graph-workspace-zoom-out");

        assertThat(model.toolbar().undoButton().getPreferredSize().width
            - model.toolbar().redoButton().getPreferredSize().width).isEqualTo(8);
        assertThat(model.toolbar().zoomOutButton().getPreferredSize().width
            - model.toolbar().zoomInButton().getPreferredSize().width).isEqualTo(8);

        verify(fixture.resourceController()).getOptionalIcon("/images/undo.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/redo.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/ZoomIn24.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/ZoomOut24.svg?useAccentColor=true");
        model.close();
    }

    @Test
    public void keepsTextFallbackWhenTheIconLookupReturnsNoIcon() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().getIcon()).isNull();
        assertThat(model.toolbar().undoButton().getText())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getToolTipText()).isNull();
        assertThat(model.toolbar().undoButton().getName()).isEqualTo("graph-workspace-undo");
        assertThat(model.toolbar().redoButton().getIcon()).isNull();
        assertThat(model.toolbar().redoButton().getText())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getToolTipText()).isNull();
        assertThat(model.toolbar().redoButton().getName()).isEqualTo("graph-workspace-redo");
        assertThat(model.toolbar().zoomInButton().getIcon()).isNull();
        assertThat(model.toolbar().zoomInButton().getText())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getToolTipText()).isNull();
        assertThat(model.toolbar().zoomInButton().getName()).isEqualTo("graph-workspace-zoom-in");
        assertThat(model.toolbar().zoomOutButton().getIcon()).isNull();
        assertThat(model.toolbar().zoomOutButton().getText())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getToolTipText()).isNull();
        assertThat(model.toolbar().zoomOutButton().getName()).isEqualTo("graph-workspace-zoom-out");

        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.toolbar().zoomInButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(model.toolbar().zoomOutButton().isEnabled()).isTrue();
        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.toolbar().zoomInButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();

        Fixture readOnlyFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel readOnlyModel = readOnlyFixture.model();

        assertThat(readOnlyModel.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().zoomOutButton().isEnabled()).isTrue();
        org.mockito.Mockito.clearInvocations(readOnlyFixture.handle);
        readOnlyModel.toolbar().zoomInButton().doClick();
        verify(readOnlyFixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));
        readOnlyModel.close();
    }

    @Test
    public void growsTheScrollableSurfaceForVisibleWorldGeometry() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JPanel graphArea = (JPanel) model.content().getComponent(1);
        JScrollPane graphScrollPane = (JScrollPane) graphArea.getComponent(1);

        model.acceptCanvasState(wideNodeState(ACTIVE_ID));

        assertThat(model.canvas().getPreferredSize().width).isGreaterThan(800);
        assertThat(graphScrollPane.getViewport().getView()).isSameAs(model.canvas());
        model.close();
    }

    @Test
    public void persistsExternalScrollAsTheVisibleViewportOnce() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JPanel graphArea = (JPanel) model.content().getComponent(1);
        JScrollPane graphScrollPane = (JScrollPane) graphArea.getComponent(1);
        graphScrollPane.setSize(new Dimension(420, 300));
        graphScrollPane.doLayout();
        javax.swing.JViewport viewport = graphScrollPane.getViewport();
        viewport.setViewPosition(new Point(80, 60));
        org.mockito.Mockito.clearInvocations(fixture.handle);

        viewport.setViewPosition(new Point(120, 60));

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Viewport.class);
        GraphCommands.Viewport command = (GraphCommands.Viewport) commands.getValue();
        GraphViewport visible = model.canvas().visibleViewport();
        GraphViewport anchor = model.canvas().viewport();
        assertThat(command.viewport().centerX()).isEqualTo(visible.centerX());
        assertThat(command.viewport().centerY()).isEqualTo(visible.centerY());
        assertThat(command.viewport().zoom()).isEqualTo(visible.zoom());
        assertThat(visible.centerX()).isNotEqualTo(anchor.centerX());
        assertThat(visible.centerY()).isNotEqualTo(anchor.centerY());
        model.close();
    }

    @Test
    public void persistsCanvasDragAsTheVisibleViewportOnce() {
        assertCanvasGesturePersistsVisibleViewportOnce(canvas -> {
            int centerX = canvas.getWidth() / 2;
            int centerY = canvas.getHeight() / 2;
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, centerX, centerY,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_DRAGGED, centerX + 15, centerY,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_DRAGGED, centerX + 30, centerY,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_RELEASED, centerX + 30, centerY,
                0, MouseEvent.BUTTON1));
        });
    }

    @Test
    public void persistsCanvasArrowPanAsTheVisibleViewportOnce() {
        assertCanvasGesturePersistsVisibleViewportOnce(canvas -> {
            KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED);
            for (KeyListener listener : canvas.getKeyListeners()) {
                listener.keyPressed(event);
            }
        });
    }

    @Test
    public void persistsCanvasWheelZoomAsTheVisibleViewportOnce() {
        assertCanvasGesturePersistsVisibleViewportOnce(canvas -> canvas.dispatchEvent(
            new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                canvas.getWidth() / 2, canvas.getHeight() / 2, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1)));
    }

    @Test
    public void preservesLoadedViewportUnknownXmlThroughToolbarZoomFitAndReset() {
        UnknownXml retained = UnknownXml.attribute(UnknownXml.Owner.RECORD,
            new QName("urn:freeplane:test", "retained"), "viewport-value");
        List<UnknownXml> unknownXml = Collections.singletonList(retained);
        Fixture fixture = fixture(Viewport.of(125.0, -75.0, 2.0, unknownXml),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        scrollViewport(model, new Dimension(420, 300));
        awaitDeferredViewportClamp();
        org.mockito.Mockito.clearInvocations(fixture.handle);

        model.toolbar().zoomInButton().doClick();
        model.toolbar().fitGraphButton().doClick();
        model.toolbar().resetZoomButton().doClick();
        awaitDeferredViewportClamp();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.times(3)).execute(commands.capture());
        for (GraphCommand command : commands.getAllValues()) {
            assertThat(command).isInstanceOf(GraphCommands.Viewport.class);
            GraphCommands.Viewport viewportCommand = (GraphCommands.Viewport) command;
            assertThat(viewportCommand.viewport().unknownXml()).containsExactly(retained);
        }
        model.close();
    }

    @Test
    public void suppressesDeferredSurfaceResizeViewportPersistenceAfterExternalScroll() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID, 500.0, 500.0),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JViewport viewport = scrollViewport(model, new Dimension(1000, 600));
        int widthBeforeResize = model.canvas().getWidth();
        Point externalPosition = scrollOffCenter(model, viewport);
        awaitDeferredViewportClamp();
        List<Point> viewportPositions = new ArrayList<Point>();
        final boolean[] deferredSetupQueued = new boolean[] {false};
        final int[] maximumX = new int[] {-1};
        viewport.addChangeListener(event -> {
            viewportPositions.add(viewport.getViewPosition());
            if (!deferredSetupQueued[0]) {
                deferredSetupQueued[0] = true;
                // Run after resize compensation but before the canvas's deferred clamp.
                javax.swing.SwingUtilities.invokeLater(() -> {
                    Dimension viewSize = viewport.getView().getSize();
                    Dimension extent = viewport.getExtentSize();
                    maximumX[0] = Math.max(0, viewSize.width - extent.width);
                    viewport.setViewPosition(new Point(maximumX[0] + 1,
                        viewport.getViewPosition().y));
                });
            }
        });
        org.mockito.Mockito.clearInvocations(fixture.handle);

        model.acceptCanvasState(wideNodeState(ACTIVE_ID, 460.0, 460.0));
        awaitDeferredViewportClamp();

        assertThat(deferredSetupQueued[0]).isTrue();
        assertThat(model.canvas().getWidth()).isLessThan(widthBeforeResize);
        assertThat(maximumX[0]).isGreaterThan(0);
        assertThat(viewport.getViewPosition().x).isEqualTo(maximumX[0]);
        assertThat(viewport.getViewPosition()).isNotEqualTo(externalPosition);
        assertThat(viewportPositions).anyMatch(position -> position.x == maximumX[0] + 1);
        assertThat(viewportPositions).anyMatch(position -> position.x == maximumX[0]);
        assertViewportCommandCount(fixture, model, 0);
        model.close();
    }

    @Test
    public void suppressesZoomViewportPersistenceAfterExternalScroll() {
        assertViewportCommandCountAfterExternalScroll(1, wideNodeState(ACTIVE_ID),
            model -> model.toolbar().zoomInButton().doClick());
    }

    @Test
    public void suppressesFitViewportPersistenceAfterExternalScroll() {
        assertViewportCommandCountAfterExternalScroll(1, wideNodeState(ACTIVE_ID),
            model -> model.toolbar().fitGraphButton().doClick());
    }

    @Test
    public void suppressesResetViewportPersistenceAfterExternalScroll() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 2.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JViewport viewport = scrollViewport(model, new Dimension(420, 300));
        assertThat(model.canvas().viewport().zoom()).isEqualTo(2.0);
        int widthBeforeReset = model.canvas().getWidth();
        Point externalPosition = scrollOffCenter(model, viewport);
        awaitDeferredViewportClamp();
        final boolean[] callbackMovedViewport = new boolean[] {false};
        org.mockito.Mockito.clearInvocations(fixture.handle);
        // Command handling can synchronously refresh the scroll position during reset publication.
        when(fixture.handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
            if (!callbackMovedViewport[0]) {
                callbackMovedViewport[0] = true;
                Point finalPosition = viewport.getViewPosition();
                int maximumX = Math.max(0, model.canvas().getWidth() - viewport.getExtentSize().width);
                int alternateX = finalPosition.x < maximumX ? finalPosition.x + 1 : finalPosition.x - 1;
                viewport.setViewPosition(new Point(alternateX, finalPosition.y));
                viewport.setViewPosition(finalPosition);
            }
            return null;
        });

        model.toolbar().resetZoomButton().doClick();
        awaitDeferredViewportClamp();

        assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(0.0);
        assertThat(model.canvas().viewport().zoom()).isEqualTo(1.0);
        assertThat(model.canvas().getWidth()).isEqualTo(widthBeforeReset);
        assertThat(viewport.getViewPosition()).isNotEqualTo(externalPosition);
        assertThat(callbackMovedViewport[0]).isTrue();
        assertViewportCommandCount(fixture, model, 1);
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
    public void revealsSourceNodeSilentlyOnSingleClickIntentWhileDoubleClickOpenStillReportsFailures() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            enclosureState(ACTIVE_ID), Collections.singletonList(registration(ACTIVE_ID, "Active",
                MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.rejected(emptyDocument(), "graph_workspace.test.rejected")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);
        final SourceNodeKey source = SourceNodeKey.transientPath(ACTIVE_ID, Collections.emptyList());
        final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source));

        model.acceptIntent(new GraphIntent.RevealSourceNode(endpoint));

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.OpenSource.class);
        assertThat(((GraphCommands.OpenSource) commands.getValue()).source()).isEqualTo(source);
        assertThat(messages).isEmpty();

        model.acceptIntent(new GraphIntent.OpenSourceNode(endpoint));

        assertThat(messages).containsExactly("graph_workspace.test.rejected[]");
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
    public void mapRowRecordsAndExposesMapPartition() {
        MapReferenceId mapId = id(31L);
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            mapId, "My Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, true);

        assertThat(row.mapReferenceId()).isEqualTo(mapId);
        assertThat(row.displayName()).isEqualTo("My Map");
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.ACTIVE);
        assertThat(row.partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(row.projectedNodeCount()).isEqualTo(5);
        assertThat(row.selected()).isTrue();
    }

    @Test
    public void mapRowOfWithPartitionSetsPartitionProperly() {
        MapReferenceId mapId = id(32L);
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            mapId, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false);

        assertThat(row.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.INACTIVE);
    }

    @Test
    public void backwardCompatibleMapRowOfDerivesPartitionFromState() {
        MapReferenceId mapId = id(33L);
        MapListPanel.MapRow activeRow = MapListPanel.MapRow.of(
            mapId, "Active Map", MapListPanel.RowState.ACTIVE, 3, false);
        MapListPanel.MapRow inactiveRow = MapListPanel.MapRow.of(
            mapId, "Inactive Map", MapListPanel.RowState.INACTIVE, 0, false);
        MapListPanel.MapRow missingRow = MapListPanel.MapRow.of(
            mapId, "Missing Map", MapListPanel.RowState.MISSING, 0, false);

        assertThat(activeRow.partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(inactiveRow.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(missingRow.partition()).isEqualTo(MapPartition.ACTIVE);
    }

    @Test
    public void mapRowWithSelectedPreservesPartition() {
        MapReferenceId mapId = id(34L);
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            mapId, "My Map", MapListPanel.RowState.READ_ONLY, MapPartition.INACTIVE, 0, false);
        MapListPanel.MapRow updated = row.withSelected(true);

        assertThat(updated.selected()).isTrue();
        assertThat(updated.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(updated.state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
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
    public void updatesMapsMenuEnablementForActiveAndInactivePartitions() {
        MapReferenceId activeId = id(101L);
        MapReferenceId inactiveId = id(102L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(activeId, "Active", MapAvailability.AVAILABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false);
        GraphWorkspaceWindowModel model = fixture.model();

        // Select Active map
        model.mapList().selectMap(activeId);
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();

        // Select Inactive map
        model.mapList().selectMap(inactiveId);
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "delete-map").isEnabled()).isTrue();

        model.close();
    }

    @Test
    public void preservesInactivePartitionInReadOnlyMode() {
        MapReferenceId inactiveId = id(103L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Collections.singletonList(
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, true);
        GraphWorkspaceWindowModel model = fixture.model();

        MapListPanel.MapRow row = model.mapList().rows().get(0);
        assertThat(row.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();

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
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
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
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
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

    @Test
    public void installsPopupFrameResynchronizationOnTheWorkspaceWindow() {
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
            // The X11/KWin coordinate resynchronizer must be installed before the
            // window can be shown so that popup menus stay selectable after
            // maximize, unmaximize, or arbitrary window manager moves.
            assertThat(window.getWindowListeners()).anyMatch(listener -> listener instanceof FrameResynchronizer);
            assertThat(window.getWindowStateListeners()).anyMatch(listener -> listener instanceof FrameResynchronizer);
            assertThat(window.getComponentListeners()).anyMatch(listener -> listener instanceof FrameResynchronizer);
        }
        finally {
            window.close();
            resourceScope.close();
        }
    }

    private static void assertViewportCommandCountAfterExternalScroll(final int expectedCount,
            final CanvasState initialState, final Consumer<GraphWorkspaceWindowModel> operation) {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()), initialState,
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JViewport viewport = scrollViewport(model, new Dimension(420, 300));
        scrollOffCenter(model, viewport);
        GraphViewport external = model.canvas().visibleViewport();
        assertThat(external.centerX()).isNotEqualTo(model.canvas().viewport().centerX());
        org.mockito.Mockito.clearInvocations(fixture.handle);

        operation.accept(model);
        awaitDeferredViewportClamp();

        assertViewportCommandCount(fixture, model, expectedCount);
        model.close();
    }

    private static void assertCanvasGesturePersistsVisibleViewportOnce(final Consumer<GraphCanvas> gesture) {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        scrollViewport(model, new Dimension(420, 300));
        awaitDeferredViewportClamp();
        org.mockito.Mockito.clearInvocations(fixture.handle);

        gesture.accept(model.canvas());
        awaitDeferredViewportClamp();

        assertViewportCommandCount(fixture, model, 1);
        model.close();
    }

    private static MouseEvent mouse(final GraphCanvas canvas, final int id, final int x, final int y,
            final int modifiers, final int button) {
        return new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button);
    }

    private static JViewport scrollViewport(final GraphWorkspaceWindowModel model, final Dimension size) {
        JScrollPane graphScrollPane = graphScrollPane(model);
        graphScrollPane.setSize(size);
        graphScrollPane.doLayout();
        return graphScrollPane.getViewport();
    }

    private static Point scrollOffCenter(final GraphWorkspaceWindowModel model, final JViewport viewport) {
        int maxX = Math.max(0, model.canvas().getWidth() - viewport.getExtentSize().width);
        int maxY = Math.max(0, model.canvas().getHeight() - viewport.getExtentSize().height);
        viewport.setViewPosition(new Point(Math.max(1, maxX * 3 / 4), Math.max(1, maxY * 3 / 4)));
        Point position = viewport.getViewPosition();
        assertThat(position.x).isGreaterThan(0);
        return position;
    }

    private static void awaitDeferredViewportClamp() {
        GraphWorkspaceWindow.runOnEdt(() -> { });
    }

    private static void assertViewportCommandCount(final Fixture fixture, final GraphWorkspaceWindowModel model,
            final int expectedCount) {
        verify(fixture.handle, org.mockito.Mockito.times(expectedCount)).execute(any(GraphCommand.class));
        if (expectedCount == 1) {
            ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
            verify(fixture.handle, org.mockito.Mockito.times(1)).execute(commands.capture());
            assertThat(commands.getValue()).isInstanceOf(GraphCommands.Viewport.class);
            GraphCommands.Viewport command = (GraphCommands.Viewport) commands.getValue();
            GraphViewport visible = model.canvas().visibleViewport();
            assertThat(command.viewport().centerX()).isEqualTo(visible.centerX());
            assertThat(command.viewport().centerY()).isEqualTo(visible.centerY());
            assertThat(command.viewport().zoom()).isEqualTo(visible.zoom());
        }
    }

    private static JScrollPane graphScrollPane(final GraphWorkspaceWindowModel model) {
        JPanel graphArea = (JPanel) model.content().getComponent(1);
        return (JScrollPane) graphArea.getComponent(1);
    }
    @Test
    public void placesRecentWorkspacesMenuBetweenSaveAsAndClose() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        JMenu file = menu(model, "graph-workspace-file-menu");

        assertThat(file.getMenuComponentCount()).isEqualTo(6);
        assertThat(file.getMenuComponent(2).getName()).isEqualTo("graph-workspace-menu-item-save-as");
        assertThat(file.getMenuComponent(3)).isInstanceOf(JMenu.class);
        JMenu recents = (JMenu) file.getMenuComponent(3);
        assertThat(recents.getName()).isEqualTo("graph-workspace-recent-workspaces-menu");
        assertThat(recents.getText()).isEqualTo("graph_workspace.menu.recent_workspaces");
        assertThat(file.getMenuComponent(4)).isInstanceOf(JSeparator.class);
        assertThat(file.getMenuComponent(5).getName()).isEqualTo("graph-workspace-menu-item-close");
        model.close();
    }

    @Test
    public void rebuildsRecentEntriesInOrderWithLabels() throws Exception {
        Path first = temporaryFolder.newFile("menu-first.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("menu-second.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(first, second);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();
        List<RecentWorkspaceList.Entry> entries = list.displayEntries();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(4);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-0");
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getText())
                    .isEqualTo(entries.get(0).label());
                assertThat(((JMenuItem) recents.getMenuComponent(1)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-1");
                assertThat(((JMenuItem) recents.getMenuComponent(1)).getText())
                    .isEqualTo(entries.get(1).label());
                assertThat(recents.getMenuComponent(2)).isInstanceOf(JSeparator.class);
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(3);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");
                assertThat(clear.isEnabled()).isTrue();
            }
        });
        model.close();
    }

    @Test
    public void limitsRebuiltEntriesToTheDisplayCap() throws Exception {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        for (int index = 0; index < 10; index++) {
            list.record(temporaryFolder.newFile("menu-cap-" + index + ".fpg").toPath().toRealPath());
        }
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(10);
                for (int index = 0; index < RecentWorkspaceList.DISPLAY_CAPACITY; index++) {
                    assertThat(((JMenuItem) recents.getMenuComponent(index)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-" + index);
                }
                assertThat(recents.getMenuComponent(8)).isInstanceOf(JSeparator.class);
                assertThat(((JMenuItem) recents.getMenuComponent(9)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        model.close();
    }

    @Test
    public void hidesMissingEntriesFromTheRebuiltMenu() throws Exception {
        Path existing = temporaryFolder.newFile("menu-existing.fpg").toPath().toRealPath();
        Path missing = temporaryFolder.newFile("menu-missing.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing, missing);
        Files.delete(missing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();
        String label = list.displayEntries().get(0).label();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                JMenuItem entry = (JMenuItem) recents.getMenuComponent(0);
                assertThat(entry.getName()).isEqualTo("graph-workspace-recent-workspace-0");
                assertThat(entry.getText()).isEqualTo(label);
                assertThat(recents.getMenuComponent(1)).isInstanceOf(JSeparator.class);
                assertThat(((JMenuItem) recents.getMenuComponent(2)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        model.close();
    }

    @Test
    public void showsDisabledEmptyRowWithoutClearWhenNothingIsStored() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                recents.setEnabled(false);

                model.rebuildRecentWorkspacesMenu();

                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                JMenuItem empty = (JMenuItem) recents.getMenuComponent(0);
                assertThat(empty.getName()).isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(empty.getText()).isEqualTo("graph_workspace.recent_workspaces.empty");
                assertThat(empty.isEnabled()).isFalse();
            }
        });
        model.close();
    }

    @Test
    public void keepsClearAndTheMenuEnabledWhenOnlyHiddenEntriesAreStored() throws Exception {
        Path missing = temporaryFolder.newFile("menu-hidden.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(missing);
        Files.delete(missing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                recents.setEnabled(false);

                model.rebuildRecentWorkspacesMenu();

                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                JMenuItem empty = (JMenuItem) recents.getMenuComponent(0);
                assertThat(empty.getName()).isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(empty.isEnabled()).isFalse();
                assertThat(recents.getMenuComponent(1)).isInstanceOf(JSeparator.class);
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(2);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");
                assertThat(clear.isEnabled()).isTrue();
            }
        });
        model.close();
    }

    @Test
    public void clearsTheListAndRebuildsWhenClearIsClicked() throws Exception {
        Path existing = temporaryFolder.newFile("menu-clear.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(2);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");

                clear.doClick();

                assertThat(list.hasStoredEntries()).isFalse();
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
            }
        });
        model.close();
    }

    @Test
    public void reportsAFailedOpenFromTheMenuAndNeverCreatesAWorkspace() throws Exception {
        Path existing = temporaryFolder.newFile("menu-failing.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        List<String> messages = new ArrayList<String>();
        GraphWorkspaceWindowModel model = fixture.model(messages::add);
        when(fixture.applicationController.openExisting(any(Path.class)))
            .thenThrow(new GraphWorkspaceOpenException(existing, new IllegalStateException("missing")));
        AtomicReference<JMenuItem> entry = new AtomicReference<JMenuItem>();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                entry.set((JMenuItem) recents.getMenuComponent(0));
            }
        });

        Files.delete(existing);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                entry.get().doClick();

                assertThat(messages).containsExactly(
                    "graph_workspace.recent_workspaces.open_failed[" + existing + "]");
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(((JMenuItem) recents.getMenuComponent(2)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        verify(fixture.applicationController, never()).open(any());
        model.close();
    }

    @Test
    public void rebuildsTheMenuFromThePopupListener() throws Exception {
        Path existing = temporaryFolder.newFile("menu-popup.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");

                recentsPopupListener(recents).popupMenuWillBecomeVisible(
                    new PopupMenuEvent(recents.getPopupMenu()));

                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-0");
            }
        });
        model.close();
    }

    @Test
    public void keepsTheNineArgumentConstructorForTheUiEvidenceHarness() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        final GraphWorkspaceWindowModel[] result = new GraphWorkspaceWindowModel[1];
        final EdtResources[] edtResources = new EdtResources[1];
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                edtResources[0] = new EdtResources();
                try {
                    Constructor<GraphWorkspaceWindowModel> constructor =
                        GraphWorkspaceWindowModel.class.getDeclaredConstructor(
                            GraphWorkspaceHandle.class, GraphWorkspaceViewBinding.class,
                            GraphWorkspaceController.class, Supplier.class, WorkspaceCloseController.class,
                            Runnable.class, Runnable.class, Runnable.class, Consumer.class);
                    result[0] = constructor.newInstance(fixture.handle, fixture.binding,
                        fixture.applicationController, (Supplier<Path>) () -> OPEN_PATH,
                        fixture.closeController, (Runnable) () -> { }, (Runnable) () -> { },
                        (Runnable) () -> { }, (Consumer<String>) message -> { });
                }
                catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
        });
        RESOURCES.add(edtResources[0]);
        GraphWorkspaceWindowModel model = result[0];

        assertThat(model).isNotNull();
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
            }
        });
        model.close();
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
        return fixture(viewport, state, registrations, readOnly, sessionStatus, presentation,
            RecentWorkspaceList.empty());
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus, GraphWorkspacePresentation presentation,
            RecentWorkspaceList recentWorkspaces) {
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
            sessionRegistration, recentWorkspaces);
    }

    private static Fixture fixture(final RecentWorkspaceList recentWorkspaces) {
        return fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()), emptyState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(DisplaySettings.defaults(), ACTIVE_ID),
            recentWorkspaces);
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

    @Test
    public void selectsTheSelectedNodeMapRowAndPinsTheSelectedNode() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));

        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        model.toolbar().pinButton().doClick();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Pin.class);
        GraphCommands.Pin pin = (GraphCommands.Pin) commands.getValue();
        assertThat(pin.node()).isEqualTo(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")));
        assertThat(pin.x()).isEqualTo(2.0);
        assertThat(pin.y()).isEqualTo(-3.0);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        model.toolbar().unpinButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void boundarySelectionDoesNotPinOrSelectAMapRow() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            boundaryWithNodeState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(2))));
        ProjectedEndpointKey boundaryEndpoint = ProjectedEndpointKey.ofEnclosure(boundary);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(boundaryEndpoint)));

        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        model.toolbar().pinButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void connectIntentRequiresBothNodeEndpoints() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        SourceNodeKey left = SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(0)));
        SourceNodeKey right = SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(1)));
        ProjectedEndpointKey leftEndpoint = ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(left));
        ProjectedEndpointKey rightEndpoint = ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(right));
        ProjectedEndpointKey boundaryEndpoint = ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(
            SourceNodeKey.transientPath(ACTIVE_ID, Collections.singletonList(Integer.valueOf(2)))));

        model.acceptIntent(new GraphIntent.Connect(leftEndpoint, boundaryEndpoint,
            RelationshipDirection.FORWARD));
        verify(fixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));

        model.acceptIntent(new GraphIntent.Connect(leftEndpoint, rightEndpoint,
            RelationshipDirection.FORWARD));
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Connect.class);
        GraphCommands.Connect connect = (GraphCommands.Connect) commands.getValue();
        assertThat(connect.source()).isEqualTo(left);
        assertThat(connect.target()).isEqualTo(right);
        assertThat(connect.direction()).isEqualTo(RelationshipDirection.FORWARD);
        model.close();
    }

    private static CanvasState persistedNodeState(MapReferenceId mapId, LayoutPoint center) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key, NodeGeometry.of(center, 10.0)),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, center), Collections.emptyMap()), false);
        return CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE);
    }

    private static CanvasState boundaryWithNodeState() {
        SourceNodeKey nodeSource = SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(0)));
        ProjectedNodeKey key = ProjectedNodeKey.of(nodeSource);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", true);
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(2))));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(boundary));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(boundary),
            Collections.singletonList(SafeNodeLabel.of("Boundary", "Boundary")), "Map",
            java.util.Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC);
        LayoutPoint anchor = LayoutPoint.of(-40.0, 0.0);
        HullGeometry hull = HullGeometry.of(Arrays.asList(LayoutPoint.of(-60.0, -20.0),
            LayoutPoint.of(-20.0, -20.0), LayoutPoint.of(-20.0, 20.0), LayoutPoint.of(-60.0, 20.0)), anchor);
        GraphProjection projection = GraphProjection.projected(0L, Collections.singletonList(node),
            Collections.singletonList(enclosure), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key,
            NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 10.0)), Collections.singletonMap(hullKey, hull),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonMap(hullKey, anchor)), false);
        return CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE);
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

    private static CanvasState wideNodeState(MapReferenceId mapId) {
        return wideNodeState(mapId, 500.0);
    }

    private static CanvasState wideNodeState(MapReferenceId mapId, double coordinate) {
        return wideNodeState(mapId, coordinate, 0.0);
    }

    private static CanvasState wideNodeState(MapReferenceId mapId, double coordinate,
            double verticalCoordinate) {
        SourceNodeKey leftSource = SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(0)));
        SourceNodeKey rightSource = SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(1)));
        ProjectedNodeKey leftKey = ProjectedNodeKey.of(leftSource);
        ProjectedNodeKey rightKey = ProjectedNodeKey.of(rightSource);
        ProjectedNode left = ProjectedNode.of(leftKey, SafeNodeLabel.of("Left", "Left"), "Map", false);
        ProjectedNode right = ProjectedNode.of(rightKey, SafeNodeLabel.of("Right", "Right"), "Map", false);
        List<ProjectedNode> nodes = Arrays.asList(left, right);
        GraphProjection projection = GraphProjection.structure(0L, nodes, Collections.emptyList());
        LayoutPoint leftCenter = LayoutPoint.of(-coordinate, -verticalCoordinate);
        LayoutPoint rightCenter = LayoutPoint.of(coordinate, verticalCoordinate);
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometry.put(leftKey, NodeGeometry.of(leftCenter, 10.0));
        geometry.put(rightKey, NodeGeometry.of(rightCenter, 10.0));
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(leftKey, leftCenter);
        positions.put(rightKey, rightCenter);
        return CanvasState.of(0L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(positions, Collections.emptyMap()), false),
            GraphGeometry.of(geometry, Collections.emptyMap()), OperationalStatus.IDLE);
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

    private static RecentWorkspaceList recentList(final Path... paths) {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        for (Path path : paths) {
            list.record(path);
        }
        return list;
    }

    private static JMenu menu(final GraphWorkspaceWindowModel model, final String name) {
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            JMenu found = findMenu(model.menuBar().getMenu(menuIndex), name);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("Missing menu " + name);
    }

    private static JMenu findMenu(final JMenu menu, final String name) {
        if (name.equals(menu.getName())) {
            return menu;
        }
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu) {
                JMenu found = findMenu((JMenu) component, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static PopupMenuListener recentsPopupListener(final JMenu menu) {
        PopupMenuListener[] listeners = menu.getPopupMenu().getPopupMenuListeners();
        for (PopupMenuListener listener : listeners) {
            if (!listener.getClass().getName().startsWith("javax.swing.")) {
                return listener;
            }
        }
        throw new AssertionError("Missing recent-workspaces popup listener");
    }

    private static Icon icon(final int width, final int height) {
        final Icon icon = mock(Icon.class);
        when(icon.getIconWidth()).thenReturn(Integer.valueOf(width));
        when(icon.getIconHeight()).thenReturn(Integer.valueOf(height));
        return icon;
    }

    private static final class Fixture {
        private final GraphWorkspaceController applicationController;
        private final GraphWorkspaceHandle handle;
        private final WorkspaceCloseController closeController;
        private final GraphWorkspaceViewBinding binding;
        private final ListenerRegistration registration;
        private final ListenerRegistration sessionRegistration;
        private final RecentWorkspaceList recentWorkspaces;
        private final Map<String, Icon> iconStubs = new LinkedHashMap<String, Icon>();
        private EdtResources resources;

        private Fixture(GraphWorkspaceController applicationController, GraphWorkspaceHandle handle,
                WorkspaceCloseController closeController, GraphWorkspaceViewBinding binding,
                ListenerRegistration registration, ListenerRegistration sessionRegistration,
                RecentWorkspaceList recentWorkspaces) {
            this.applicationController = applicationController;
            this.handle = handle;
            this.closeController = closeController;
            this.binding = binding;
            this.registration = registration;
            this.sessionRegistration = sessionRegistration;
            this.recentWorkspaces = recentWorkspaces;
        }

        private Fixture stubIcon(final String path, final Icon icon) {
            iconStubs.put(path, icon);
            return this;
        }

        private ResourceController resourceController() {
            return resources.controller();
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
                    edtResources[0] = new EdtResources(iconStubs);
                    result[0] = new GraphWorkspaceWindowModel(handle, binding, applicationController,
                        () -> OPEN_PATH, closeController, () -> { }, () -> { }, () -> { }, commandMessageSink,
                        recentWorkspaces);
                }
            });
            resources = edtResources[0];
            RESOURCES.add(edtResources[0]);
            return result[0];
        }
    }

    private static final class EdtResources {
        private final MockedStatic<TextUtils> textUtils;
        private final MockedStatic<ResourceController> resourceController;
        private final ResourceController controller;
        private boolean closed;

        private EdtResources() {
            this(Collections.<String, Icon>emptyMap());
        }

        private EdtResources(final Map<String, Icon> iconStubs) {
            closePreviouslyRegisteredResources();
            controller = mock(ResourceController.class);
            resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
            resourceController.when(ResourceController::getResourceController).thenReturn(controller);
            when(controller.getOptionalIcon(any(String.class)))
                .thenAnswer(invocation -> iconStubs.get(invocation.getArgument(0)));
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

        private ResourceController controller() {
            return controller;
        }

        private void closePreviouslyRegisteredResources() {
            final java.util.Iterator<EdtResources> registered = RESOURCES.iterator();
            while (registered.hasNext()) {
                final EdtResources resource = registered.next();
                if (resource != this) {
                    resource.closeOnEdt();
                }
                registered.remove();
            }
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
