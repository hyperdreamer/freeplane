package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphIntent;
import org.freeplane.plugin.graph.canvas.GraphInteractionController;
import org.freeplane.plugin.graph.canvas.GraphPaintState;
import org.freeplane.plugin.graph.canvas.GraphSearchModel;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.canvas.InteractionTool;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.CanvasStateListener;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.Viewport;

final class GraphWorkspaceWindow extends JFrame implements GraphWorkspaceView {
    private static final long serialVersionUID = 1L;
    private static final Dimension CANVAS_PREFERRED_SIZE = new Dimension(800, 560);
    private static final Dimension WINDOW_SIZE = new Dimension(1280, 800);

    private final GraphWorkspaceHandle handle;
    private final GraphWorkspaceViewBinding binding;
    private final WorkspaceCloseController closeController;
    private final GraphCanvas canvas;
    private final MapListPanel mapList;
    private final WorkspaceToolbar toolbar;
    private final WorkspaceSettingsPanel settingsPanel;
    private final JPanel statusSlot;
    private final GraphInteractionController interactionController;
    private final ListenerRegistration canvasRegistration;
    private final Action undoWorkspaceAction;
    private final Action redoWorkspaceAction;
    private CanvasState currentState;
    private GraphPaintState paintState = GraphPaintState.empty();
    private ProjectedNodeKey selectedNode;
    private boolean readOnly;
    private boolean closed;

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        super("Graph Workspace");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        Objects.requireNonNull(applicationController, "applicationController");
        Objects.requireNonNull(pathChooser, "pathChooser");
        setName("graph-workspace-window");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setResizable(true);

        canvas = new GraphCanvas();
        canvas.setName("graph-workspace-canvas");
        canvas.setPreferredSize(CANVAS_PREFERRED_SIZE);
        canvas.setMinimumSize(new Dimension(320, 240));
        mapList = new MapListPanel(handle, pathChooser);
        toolbar = new WorkspaceToolbar(applicationController, handle, canvas, pathChooser);
        settingsPanel = new WorkspaceSettingsPanel(handle, DisplaySettings.defaults());
        statusSlot = new JPanel(new BorderLayout());
        statusSlot.setName("graph-workspace-status-slot");
        statusSlot.setPreferredSize(new Dimension(0, 26));
        statusSlot.setMinimumSize(new Dimension(0, 26));

        interactionController = new GraphInteractionController(this::handleIntent);
        interactionController.install(canvas);
        toolbar.setInteractionController(interactionController);
        toolbar.setSettingsAction(() -> settingsPanel.setVisible(!settingsPanel.isVisible()));
        toolbar.setToolListener(tool -> interactionController.setTool(tool));
        toolbar.setDirectionListener(interactionController::setRelationshipDirection);
        toolbar.setSearchListener(this::search);
        toolbar.setViewportListener(viewport -> {
            if (!readOnly) {
                handle.execute(org.freeplane.plugin.graph.command.GraphCommands.viewport(viewport.toPersisted()));
            }
        });
        toolbar.setPinAction(this::pinSelectedNode);
        toolbar.setUnpinAction(this::unpinSelectedNode);

        undoWorkspaceAction = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                toolbar.undoButton().doClick();
            }
        };
        redoWorkspaceAction = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                toolbar.redoButton().doClick();
            }
        };
        installWorkspaceHistoryKeys();
        setJMenuBar(createMenuBar());
        setContentPane(createContent());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                requestClose();
            }
        });

        currentState = binding.currentCanvasState();
        if (currentState != null) {
            canvas.setCanvasState(currentState);
            updateMapRows(currentState);
        }
        final Viewport persistedViewport = Objects.requireNonNull(binding.currentViewport(), "currentViewport");
        canvas.setViewport(GraphViewport.from(persistedViewport));
        setReadOnly(binding.isReadOnly());
        pack();
        setSize(WINDOW_SIZE);
        validate();
        doLayout();
        applyInitialViewport(currentState);
        canvasRegistration = binding.addCanvasStateListener(new CanvasStateListener() {
            @Override
            public void onCanvasState(final CanvasState state) {
                acceptCanvasState(state);
            }
        });
        setLocationByPlatform(true);
    }

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController) {
        this(handle, binding, closeController, applicationController, GraphWorkspaceWindow::chooseWorkspacePath);
    }

    GraphCanvas canvas() {
        return canvas;
    }

    MapListPanel mapList() {
        return mapList;
    }

    WorkspaceToolbar toolbar() {
        return toolbar;
    }

    WorkspaceSettingsPanel settingsPanel() {
        return settingsPanel;
    }

    JPanel statusSlot() {
        return statusSlot;
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        toolbar.setReadOnly(value);
        mapList.setReadOnly(value);
        settingsPanel.setReadOnly(value);
        if (value) {
            interactionController.setTool(InteractionTool.SELECT);
            toolbar.selectButton().setSelected(true);
        }
        updateMapRows(currentState);
    }

    boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void show() {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (!closed) {
                    setVisible(true);
                }
            }
        });
    }

    @Override
    public void focus() {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (!closed) {
                    if (!isVisible()) {
                        setVisible(true);
                    }
                    toFront();
                    requestFocus();
                }
            }
        });
    }

    @Override
    public void close() {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                closeOnEdt();
            }
        });
    }

    private void closeOnEdt() {
        if (closed) {
            return;
        }
        closed = true;
        canvasRegistration.close();
        interactionController.uninstall();
        dispose();
    }

    private void requestClose() {
        if (closed) {
            return;
        }
        if (closeController.saveAndClose()) {
            close();
        }
    }

    private JPanel createContent() {
        final JPanel graphArea = new JPanel(new BorderLayout(0, 0));
        graphArea.setName("graph-workspace-graph-area");
        graphArea.add(mapList, BorderLayout.WEST);
        graphArea.add(canvas, BorderLayout.CENTER);
        graphArea.add(settingsPanel, BorderLayout.EAST);

        final JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setName("graph-workspace-content");
        content.add(toolbar, BorderLayout.NORTH);
        content.add(graphArea, BorderLayout.CENTER);
        content.add(statusSlot, BorderLayout.SOUTH);
        return content;
    }

    private JMenuBar createMenuBar() {
        final JMenuBar menuBar = new JMenuBar();
        menuBar.setName("graph-workspace-menu-bar");
        final JMenu file = menu("File", "graph-workspace-file-menu");
        file.add(item("Open", "open", event -> toolbar.openButton().doClick()));
        file.add(item("Save", "save", event -> toolbar.saveButton().doClick()));
        file.add(item("Save As", "save-as", event -> toolbar.saveAsButton().doClick()));
        file.addSeparator();
        file.add(item("Close", "close", event -> requestClose()));

        final JMenu edit = menu("Edit", "graph-workspace-edit-menu");
        edit.add(actionItem("Undo", "undo", undoWorkspaceAction));
        edit.add(actionItem("Redo", "redo", redoWorkspaceAction));
        edit.add(item("Undo Source Map", "undo-source-map",
            event -> handle.execute(org.freeplane.plugin.graph.command.GraphCommands.undoSourceMap())));

        final JMenu view = menu("View", "graph-workspace-view-menu");
        view.add(item("Fit Graph", "fit-graph", event -> toolbar.fitGraphButton().doClick()));
        view.add(item("Reset Zoom", "reset-zoom", event -> toolbar.resetZoomButton().doClick()));
        view.add(item("Zoom In", "zoom-in", event -> toolbar.zoomInButton().doClick()));
        view.add(item("Zoom Out", "zoom-out", event -> toolbar.zoomOutButton().doClick()));
        view.add(item("Settings", "settings", event -> toolbar.settingsButton().doClick()));

        final JMenu maps = menu("Maps", "graph-workspace-maps-menu");
        maps.add(item("Add Map", "add-map", event -> mapList.addButton().doClick()));
        maps.add(item("Remove Map", "remove-map", event -> mapList.removeButton().doClick()));
        maps.add(item("Retry Map", "retry-map", event -> mapList.retryButton().doClick()));
        maps.add(item("Locate Map", "locate-map", event -> mapList.locateButton().doClick()));

        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(view);
        menuBar.add(maps);
        return menuBar;
    }

    private void installWorkspaceHistoryKeys() {
        final JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl Z"),
            "graph-workspace-undo");
        root.getActionMap().put("graph-workspace-undo", undoWorkspaceAction);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl Y"),
            "graph-workspace-redo");
        root.getActionMap().put("graph-workspace-redo", redoWorkspaceAction);
    }

    private void acceptCanvasState(final CanvasState state) {
        Objects.requireNonNull(state, "state");
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                currentState = state;
                canvas.setCanvasState(state);
                updateMapRows(state);
                search(toolbar.searchField().getText());
            }
        });
    }

    private void updateMapRows(final CanvasState state) {
        if (state == null) {
            mapList.setRows(Collections.<MapListPanel.MapRow>emptyList());
            return;
        }
        final GraphProjection projection = state.projection();
        final Map<org.freeplane.plugin.graph.workspace.model.MapReferenceId, RowAccumulator> accumulators =
            new LinkedHashMap<org.freeplane.plugin.graph.workspace.model.MapReferenceId, RowAccumulator>();
        for (final ProjectedNode node : projection.nodes()) {
            final org.freeplane.plugin.graph.workspace.model.MapReferenceId mapId = node.mapReferenceId();
            RowAccumulator accumulator = accumulators.get(mapId);
            if (accumulator == null) {
                accumulator = new RowAccumulator(mapId, node.mapName());
                accumulators.put(mapId, accumulator);
            }
            accumulator.projectedNodeCount++;
        }
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            final org.freeplane.plugin.graph.workspace.model.MapReferenceId mapId = enclosure.mapReferenceId();
            if (!accumulators.containsKey(mapId)) {
                accumulators.put(mapId, new RowAccumulator(mapId, enclosure.mapName()));
            }
        }
        final List<MapListPanel.MapRow> rows = new ArrayList<MapListPanel.MapRow>(accumulators.size());
        for (final RowAccumulator accumulator : accumulators.values()) {
            final MapListPanel.RowState rowState;
            if (readOnly) {
                rowState = MapListPanel.RowState.READ_ONLY;
            }
            else if (state.status() == OperationalStatus.LOADING && accumulator.projectedNodeCount == 0) {
                rowState = MapListPanel.RowState.LOADING;
            }
            else {
                rowState = MapListPanel.RowState.ACTIVE;
            }
            rows.add(MapListPanel.MapRow.of(accumulator.mapReferenceId, accumulator.displayName, rowState,
                accumulator.projectedNodeCount, selectedNode != null
                    && selectedNode.mapReferenceId().equals(accumulator.mapReferenceId)));
        }
        mapList.setRows(rows);
    }

    private void search(final String query) {
        final CanvasState state = currentState;
        if (state == null || query == null || query.trim().isEmpty()) {
            paintState = paintState.withSearchMatches(Collections.<ProjectedEndpointKey>emptySet());
        }
        else {
            paintState = paintState.withSearchMatches(GraphSearchModel.search(state, query));
        }
        canvas.setPaintState(paintState);
    }

    private void handleIntent(final GraphIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent instanceof GraphIntent.ChangeSelection) {
            final Optional<ProjectedEndpointKey> selection = ((GraphIntent.ChangeSelection) intent).selection();
            selectedNode = selection.isPresent() && selection.get().isNode()
                ? selection.get().node().get() : null;
            paintState = selection.isPresent() ? paintState.withSelection(selection.get())
                : GraphPaintState.empty().withSearchMatches(
                    GraphSearchModel.search(currentState, toolbar.searchField().getText()));
            canvas.setPaintState(paintState);
            updateMapRows(currentState);
        }
        else if (intent instanceof GraphIntent.Pin) {
            final GraphIntent.Pin pin = (GraphIntent.Pin) intent;
            executePin(pin.node(), pin.worldX(), pin.worldY());
        }
        else if (intent instanceof GraphIntent.Unpin) {
            executeUnpin(((GraphIntent.Unpin) intent).node());
        }
        else if (intent instanceof GraphIntent.UnpinAll) {
            if (!readOnly) {
                handle.execute(org.freeplane.plugin.graph.command.GraphCommands.unpinAll());
            }
        }
        else if (intent instanceof GraphIntent.Connect) {
            if (readOnly) {
                return;
            }
            final GraphIntent.Connect connect = (GraphIntent.Connect) intent;
            handle.execute(org.freeplane.plugin.graph.command.GraphCommands.connect(connect.source().isNode()
                ? connect.source().node().get().source() : source(connect.source()),
                connect.target().isNode() ? connect.target().node().get().source() : source(connect.target()),
                connect.direction()));
        }
        else if (intent instanceof GraphIntent.OpenSourceNode) {
            final ProjectedEndpointKey endpoint = ((GraphIntent.OpenSourceNode) intent).endpoint();
            handle.execute(org.freeplane.plugin.graph.command.GraphCommands.openSource(source(endpoint)));
        }
    }

    private void pinSelectedNode() {
        if (selectedNode == null || currentState == null) {
            return;
        }
        final NodeReference reference = selectedNode.source().persistedReference().orElse(null);
        final NodeGeometry geometry = currentState.geometry().nodes().get(selectedNode);
        if (reference != null && geometry != null) {
            executePin(selectedNode, geometry.center().x(), geometry.center().y());
        }
    }

    private void unpinSelectedNode() {
        if (selectedNode != null) {
            executeUnpin(selectedNode);
        }
    }

    private void executePin(final ProjectedNodeKey node, final double x, final double y) {
        final NodeReference reference = node.source().persistedReference().orElse(null);
        if (reference != null && !readOnly) {
            handle.execute(org.freeplane.plugin.graph.command.GraphCommands.pin(reference, x, y));
        }
    }

    private void executeUnpin(final ProjectedNodeKey node) {
        final NodeReference reference = node.source().persistedReference().orElse(null);
        if (reference != null && !readOnly) {
            handle.execute(org.freeplane.plugin.graph.command.GraphCommands.unpin(reference));
        }
    }

    private static SourceNodeKey source(final ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get().source()
            : endpoint.enclosure().get().source();
    }

    private void applyInitialViewport(final CanvasState state) {
        if (state == null) {
            return;
        }
        final Bounds bounds = graphBounds(state);
        if (bounds == null) {
            return;
        }
        final Dimension size = canvas.getSize();
        final Dimension overlapSize = size.width > 0 && size.height > 0 ? size : CANVAS_PREFERRED_SIZE;
        if (!canvas.viewport().overlaps(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, overlapSize)) {
            canvas.fitGraph();
        }
    }

    private static Bounds graphBounds(final CanvasState state) {
        final GraphProjection projection = state.projection();
        final GraphGeometry geometry = state.geometry();
        final Bounds bounds = new Bounds();
        for (final ProjectedNode node : projection.nodes()) {
            final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
            if (nodeGeometry != null) {
                bounds.include(nodeGeometry.minX(), nodeGeometry.minY(), nodeGeometry.maxX(), nodeGeometry.maxY());
            }
        }
        final Set<EnclosureHullKey> hulls = new java.util.HashSet<EnclosureHullKey>();
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED || !hulls.add(enclosure.hullKey())) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull != null) {
                bounds.include(hull.minX(), hull.minY(), hull.maxX(), hull.maxY());
            }
        }
        return bounds.empty() ? null : bounds;
    }

    private static JMenu menu(final String title, final String name) {
        final JMenu menu = new JMenu(title);
        menu.setName(name);
        return menu;
    }

    private static JMenuItem actionItem(final String title, final String name, final Action action) {
        final JMenuItem item = new JMenuItem(action);
        item.setText(title);
        item.setName("graph-workspace-menu-item-" + name);
        return item;
    }

    private static JMenuItem item(final String title, final String name,
            final ActionListener listener) {
        final JMenuItem item = new JMenuItem(title);
        item.setName("graph-workspace-menu-item-" + name);
        item.addActionListener(listener);
        return item;
    }

    static java.nio.file.Path chooseWorkspacePath() {
        final javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        return chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION
            ? chooser.getSelectedFile().toPath() : null;
    }

    private static void runOnEdt(final Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        }
        catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating graph workspace window", exception);
        }
        catch (final java.lang.reflect.InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Graph workspace window update failed", cause);
        }
    }

    private static final class RowAccumulator {
        private final org.freeplane.plugin.graph.workspace.model.MapReferenceId mapReferenceId;
        private final String displayName;
        private int projectedNodeCount;

        private RowAccumulator(final org.freeplane.plugin.graph.workspace.model.MapReferenceId mapReferenceId,
                final String displayName) {
            this.mapReferenceId = mapReferenceId;
            this.displayName = displayName;
        }
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        private void include(final double firstX, final double firstY, final double secondX,
                final double secondY) {
            if (!Double.isFinite(firstX) || !Double.isFinite(firstY) || !Double.isFinite(secondX)
                    || !Double.isFinite(secondY)) {
                return;
            }
            minX = Math.min(minX, firstX);
            minY = Math.min(minY, firstY);
            maxX = Math.max(maxX, secondX);
            maxY = Math.max(maxY, secondY);
        }

        private boolean empty() {
            return minX == Double.POSITIVE_INFINITY;
        }
    }
}
