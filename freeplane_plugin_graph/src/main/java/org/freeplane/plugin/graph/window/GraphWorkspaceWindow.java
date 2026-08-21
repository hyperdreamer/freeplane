package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
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
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphIntent;
import org.freeplane.plugin.graph.canvas.GraphInteractionController;
import org.freeplane.plugin.graph.canvas.GraphPaintState;
import org.freeplane.plugin.graph.canvas.GraphSearchModel;
import org.freeplane.plugin.graph.canvas.GraphTheme;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.canvas.InteractionTool;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.CanvasStateListener;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspacePresentation;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatusListener;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;

final class GraphWorkspaceWindow extends JFrame implements GraphWorkspaceView {
    private static final long serialVersionUID = 1L;
    private static final Dimension WINDOW_SIZE = new Dimension(1280, 800);

    private final WorkspaceCloseController closeController;
    private final GraphWorkspaceWindowModel model;
    private boolean closed;

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        super(TextUtils.getText("graph_workspace.window.title"));
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(applicationController, "applicationController");
        Objects.requireNonNull(pathChooser, "pathChooser");
        setName("graph-workspace-window");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setResizable(true);

        model = new GraphWorkspaceWindowModel(handle, binding, applicationController, pathChooser, closeController,
            new Runnable() {
                @Override
                public void run() {
                    requestClose();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    canvas().requestFocusInWindow();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    closeOnEdt();
                }
            });
        setJMenuBar(model.menuBar());
        setContentPane(model.content());
        model.installWorkspaceHistoryKeys(getRootPane());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                requestClose();
            }
        });
        pack();
        setSize(WINDOW_SIZE);
        validate();
        doLayout();
        model.completeInitialLayout();
        setLocationByPlatform(true);
    }

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController) {
        this(handle, binding, closeController, applicationController, GraphWorkspaceWindow::chooseWorkspacePath);
    }

    GraphCanvas canvas() {
        return model.canvas();
    }

    MapListPanel mapList() {
        return model.mapList();
    }

    WorkspaceToolbar toolbar() {
        return model.toolbar();
    }

    WorkspaceSettingsPanel settingsPanel() {
        return model.settingsPanel();
    }

    JPanel statusSlot() {
        return model.statusSlot();
    }

    void setReadOnly(final boolean value) {
        model.setReadOnly(value);
    }

    boolean isReadOnly() {
        return model.isReadOnly();
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
        model.close();
        dispose();
    }

    private void requestClose() {
        if (!closed) {
            model.requestClose();
        }
    }

    static java.nio.file.Path chooseWorkspacePath() {
        final javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        return chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION
            ? chooser.getSelectedFile().toPath() : null;
    }

    static void runOnEdt(final Runnable action) {
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
}

final class GraphWorkspaceWindowModel {
    static final Dimension CANVAS_PREFERRED_SIZE = new Dimension(800, 560);

    private final GraphWorkspaceHandle handle;
    private final GraphWorkspaceViewBinding binding;
    private final Runnable closeRequest;
    private final GraphCanvas canvas;
    private final MapListPanel mapList;
    private final WorkspaceToolbar toolbar;
    private final WorkspaceSettingsPanel settingsPanel;
    private final JPanel statusSlot;
    private final JPanel content;
    private final JMenuBar menuBar;
    private final GraphInteractionController interactionController;
    private final ListenerRegistration canvasRegistration;
    private final ListenerRegistration sessionStatusRegistration;
    private final GraphStatusBar statusBar;
    private final WorkspaceCloseController closeController;
    private final Runnable graphFocus;
    private final Runnable closeCompletion;
    private final GraphWorkspaceHandle routedHandle;
    private final Action undoWorkspaceAction;
    private final Action redoWorkspaceAction;
    private final Action sourceMapUndoAction;
    private final GraphViewport initialViewport;
    private JMenuItem fileSaveMenuItem;
    private JMenuItem fileSaveAsMenuItem;
    private JMenuItem viewSettingsMenuItem;
    private JMenuItem mapsAddMenuItem;
    private JMenuItem mapsRemoveMenuItem;
    private JMenuItem mapsRetryMenuItem;
    private JMenuItem mapsLocateMenuItem;
    private CanvasState currentState;
    private WorkspaceSessionStatus currentSessionStatus;
    private GraphWorkspacePresentation currentPresentation;
    private GraphPaintState paintState = GraphPaintState.empty();
    private ProjectedNodeKey selectedNode;
    private ProjectedEndpointKey selectedEndpoint;
    private boolean initialViewportLayoutReady;
    private boolean initialViewportPending = true;
    private boolean readOnly;
    private boolean closed;
    private WorkspaceCloseDialog closeDialog;
    private ContributorInspector contributorInspector;

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final Runnable closeRequest) {
        this(handle, binding, applicationController, pathChooser, noOpCloseController(), closeRequest, null);
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final Runnable closeRequest, final Runnable graphFocus) {
        this(handle, binding, applicationController, pathChooser, noOpCloseController(), closeRequest, graphFocus);
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus) {
        this(handle, binding, applicationController, pathChooser, closeController, closeRequest, graphFocus,
            closeRequest);
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus, final Runnable closeCompletion) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        this.closeRequest = Objects.requireNonNull(closeRequest, "closeRequest");
        this.closeCompletion = Objects.requireNonNull(closeCompletion, "closeCompletion");
        Objects.requireNonNull(applicationController, "applicationController");
        Objects.requireNonNull(pathChooser, "pathChooser");

        canvas = new GraphCanvas();
        this.graphFocus = graphFocus == null ? new Runnable() {
            @Override
            public void run() {
                canvas.requestFocusInWindow();
            }
        } : Objects.requireNonNull(graphFocus, "graphFocus");
        routedHandle = new GraphWorkspaceHandle() {
            @Override
            public GraphProjection currentProjection() {
                return handle.currentProjection();
            }

            @Override
            public GraphCommandResult execute(final GraphCommand command) {
                return executeCommand(command);
            }

            @Override
            public ListenerRegistration addProjectionListener(
                    final org.freeplane.plugin.graph.control.GraphProjectionListener listener) {
                return handle.addProjectionListener(listener);
            }

            @Override
            public void close() {
                handle.close();
            }
        };
        canvas.setName("graph-workspace-canvas");
        canvas.setPreferredSize(CANVAS_PREFERRED_SIZE);
        canvas.setMinimumSize(new Dimension(320, 240));
        canvas.setSize(CANVAS_PREFERRED_SIZE);
        mapList = new MapListPanel(routedHandle, pathChooser);
        mapList.rowList().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateMenuEnablement();
            }
        });
        toolbar = new WorkspaceToolbar(applicationController, routedHandle, canvas, pathChooser);
        currentPresentation = presentationOrDefault(binding.currentPresentation());
        settingsPanel = new WorkspaceSettingsPanel(routedHandle, currentPresentation.displaySettings());
        statusSlot = new JPanel(new BorderLayout());
        statusSlot.setName("graph-workspace-status-slot");
        statusSlot.setPreferredSize(new Dimension(0, 26));
        statusSlot.setMinimumSize(new Dimension(0, 26));
        statusBar = new GraphStatusBar(this::executeCommand);
        statusSlot.add(statusBar, BorderLayout.CENTER);

        interactionController = new GraphInteractionController(this::handleIntent);
        interactionController.install(canvas);
        toolbar.setInteractionController(interactionController);
        toolbar.setSettingsAction(new Runnable() {
            @Override
            public void run() {
                settingsPanel.setVisible(!settingsPanel.isVisible());
            }
        });
        toolbar.setToolListener(tool -> interactionController.setTool(tool));
        toolbar.setDirectionListener(interactionController::setRelationshipDirection);
        toolbar.setSearchListener(this::search);
        toolbar.setViewportListener(viewport -> {
            if (!readOnly) {
                executeCommand(GraphCommands.viewport(viewport.toPersisted()));
            }
        });
        toolbar.setPinAction(this::pinSelectedNode);
        toolbar.setUnpinAction(this::unpinSelectedNode);

        undoWorkspaceAction = new AbstractAction(TextUtils.getText("graph_workspace.action.undo_workspace")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                if (!readOnly && currentSessionStatus.workspaceUndoAvailable()) {
                    executeCommand(GraphCommands.undoWorkspace());
                }
            }
        };
        redoWorkspaceAction = new AbstractAction(TextUtils.getText("graph_workspace.action.redo_workspace")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                if (!readOnly && currentSessionStatus.workspaceRedoAvailable()) {
                    executeCommand(GraphCommands.redoWorkspace());
                }
            }
        };
        sourceMapUndoAction = new AbstractAction(TextUtils.getText("graph_workspace.action.undo_source_map.none")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                if (!readOnly && currentSessionStatus.sourceMapUndoTarget().isPresent()
                        && currentSessionStatus.sourceMapUndoTarget().get().canUndo()) {
                    executeCommand(GraphCommands.undoSourceMap());
                }
            }
        };
        sourceMapUndoAction.setEnabled(false);
        menuBar = createMenuBar();
        content = createContent();

        currentState = binding.currentCanvasState();
        currentSessionStatus = binding.currentSessionStatus();
        if (currentSessionStatus == null) {
            currentSessionStatus = WorkspaceSessionStatus.empty();
        }
        applyPresentation(currentPresentation);
        if (currentState != null) {
            canvas.setCanvasState(currentState);
        }
        initialViewport = GraphViewport.from(Objects.requireNonNull(binding.currentViewport(), "currentViewport"));
        canvas.setViewport(initialViewport);
        setReadOnlyOnEdt(binding.isReadOnly());
        updateStatusBar();
        sessionStatusRegistration = registrationOrNoOp(binding.addSessionStatusListener(
            new WorkspaceSessionStatusListener() {
                @Override
                public void onWorkspaceSessionStatus(final WorkspaceSessionStatus status) {
                    acceptSessionStatus(status);
                }
            }));
        canvasRegistration = registrationOrNoOp(binding.addCanvasStateListener(new CanvasStateListener() {
            @Override
            public void onCanvasState(final CanvasState state) {
                acceptCanvasState(state);
            }
        }));
    }

    private static GraphWorkspacePresentation presentationOrDefault(
            final GraphWorkspacePresentation presentation) {
        return presentation == null ? GraphWorkspacePresentation.defaults() : presentation;
    }

    private static Map<MapReferenceId, String> palette(final GraphWorkspacePresentation presentation) {
        final Map<MapReferenceId, String> result = new LinkedHashMap<MapReferenceId, String>();
        for (final GraphWorkspacePresentation.MapColor color : presentation.mapColors()) {
            result.put(color.mapReferenceId(), color.color());
        }
        return result;
    }

    private void applyPresentation(final GraphWorkspacePresentation value) {
        final GraphWorkspacePresentation next = Objects.requireNonNull(value, "presentation");
        final DisplaySettings previousSettings = currentPresentation == null
            ? null : currentPresentation.displaySettings();
        currentPresentation = next;
        final DisplaySettings settings = next.displaySettings();
        settingsPanel.setSettings(settings);
        canvas.setTheme(GraphTheme.resolve(settings.canvasTheme(), palette(next)));
        canvas.setShowArrowheads(settings.showArrowheads());
        canvas.setDimUnrelated(settings.dimUnrelatedNodes());
        if (previousSettings != null && previousSettings.rememberViewport()
                && !settings.rememberViewport()) {
            initialViewportPending = true;
        }
    }

    private void refreshPresentation() {
        final GraphWorkspacePresentation presentation = binding.currentPresentation();
        if (presentation != null) {
            applyPresentation(presentation);
        }
    }

    private static ListenerRegistration registrationOrNoOp(final ListenerRegistration registration) {
        return registration == null ? new ListenerRegistration() {
            @Override
            public void close() {
            }
        } : registration;
    }

    private static WorkspaceCloseController noOpCloseController() {
        return new WorkspaceCloseController() {
            @Override
            public boolean saveAndClose() {
                return false;
            }

            @Override
            public boolean retrySaveAndClose() {
                return false;
            }

            @Override
            public boolean discardAndClose() {
                return false;
            }

            @Override
            public void cancelClose() {
            }
        };
    }

    private void acceptSessionStatus(final WorkspaceSessionStatus status) {
        Objects.requireNonNull(status, "status");
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (!closed) {
                    currentSessionStatus = status;
                    updateStatusBar();
                }
            }
        });
    }

    private void updateStatusBar() {
        statusBar.setStatus(currentState, selectedEndpointText(), binding.currentMapRows(),
            currentSessionStatus, readOnly);
        toolbar.setHistoryAvailability(currentSessionStatus.workspaceUndoAvailable(),
            currentSessionStatus.workspaceRedoAvailable());
        undoWorkspaceAction.setEnabled(currentSessionStatus.workspaceUndoAvailable() && !readOnly);
        redoWorkspaceAction.setEnabled(currentSessionStatus.workspaceRedoAvailable() && !readOnly);
        if (currentSessionStatus.sourceMapUndoTarget().isPresent()) {
            final org.freeplane.plugin.graph.command.MapUndoTarget target =
                currentSessionStatus.sourceMapUndoTarget().get();
            sourceMapUndoAction.putValue(Action.NAME, TextUtils.format("graph_workspace.action.undo_source_map",
                target.mapName()));
            sourceMapUndoAction.setEnabled(target.canUndo() && !readOnly);
        }
        else {
            sourceMapUndoAction.putValue(Action.NAME,
                TextUtils.getText("graph_workspace.action.undo_source_map.none"));
            sourceMapUndoAction.setEnabled(false);
        }
        updateMenuEnablement();
    }

    private Optional<String> selectedEndpointText() {
        return selectedEndpoint != null && currentState != null
            ? Optional.of(GraphWindowEndpointLabels.displayText(currentState.projection(), selectedEndpoint))
            : Optional.<String>empty();
    }

    private GraphCommandResult executeCommand(final GraphCommand command) {
        final GraphCommandResult result = handle.execute(Objects.requireNonNull(command, "command"));
        refreshPresentation();
        applyInitialViewport(currentState);
        if (result != null && result.editorViewActivated()) {
            graphFocus.run();
        }
        return result;
    }

    GraphCommandResult execute(final GraphCommand command) {
        return executeCommand(command);
    }

    void requestClose() {
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                if (closeController.saveAndClose()) {
                    closeCompletion.run();
                    return;
                }
                closeDialog = new WorkspaceCloseDialog(closeController, closeCompletion);
                if (!GraphicsEnvironment.isHeadless()) {
                    showDialog("graph_workspace.dialog.close.title", closeDialog);
                }
            }
        });
    }

    WorkspaceCloseDialog closeDialog() {
        if (closeDialog == null) {
            closeDialog = new WorkspaceCloseDialog(closeController, closeCompletion);
        }
        return closeDialog;
    }

    GraphStatusBar statusBar() {
        return statusBar;
    }

    PurgeConfirmationDialog createPurgeConfirmationDialog() {
        if (currentState == null || readOnly) {
            return null;
        }
        final PurgeConfirmationDialog dialog = PurgeConfirmationDialog.from(currentState,
            binding.currentMapRows(), this::executeCommand);
        return dialog.isEmpty() ? null : dialog;
    }

    PurgeConfirmationDialog purgeMissingNodes() {
        final PurgeConfirmationDialog dialog = createPurgeConfirmationDialog();
        if (dialog != null && !GraphicsEnvironment.isHeadless()) {
            showDialog("graph_workspace.dialog.purge.title", dialog);
        }
        return dialog;
    }

    ContributorInspector inspectEdge(final org.freeplane.plugin.graph.projection.ProjectedEdgeKey key) {
        if (currentState == null) {
            return null;
        }
        final GraphProjection projection = currentState.projection();
        final ProjectedEdge edge = findEdge(projection, Objects.requireNonNull(key, "key"));
        if (edge == null) {
            return null;
        }
        contributorInspector = new ContributorInspector(currentState.generation(), projection, edge,
            binding.currentMapRows(), this::executeCommand);
        contributorInspector.setReadOnly(readOnly);
        return contributorInspector;
    }

    private void handleEdgeIntent(final GraphIntent intent) {
        if (currentState == null) {
            return;
        }
        final GraphProjection projection = currentState.projection();
        if (intent instanceof GraphIntent.InspectEdge) {
            final ContributorInspector inspector = inspectEdge(((GraphIntent.InspectEdge) intent).edge());
            if (inspector != null && !GraphicsEnvironment.isHeadless()) {
                showDialog("graph_workspace.dialog.contributors.title", inspector);
            }
        }
        else if (intent instanceof GraphIntent.DeleteContributor) {
            if (readOnly) {
                return;
            }
            final GraphIntent.DeleteContributor delete = (GraphIntent.DeleteContributor) intent;
            final ProjectedEdge edge = findEdgeContaining(projection, delete.contributor());
            if (edge != null) {
                final EdgeContributor contributor = findContributor(edge, delete.contributor());
                if (contributor != null) {
                    executeCommand(GraphCommands.deleteContributor(currentState.generation(), contributor.key(),
                        contributor.connectorDescriptor().orElse(null)));
                }
            }
        }
        else if (intent instanceof GraphIntent.DeleteAllContributors) {
            if (readOnly) {
                return;
            }
            final GraphIntent.DeleteAllContributors delete = (GraphIntent.DeleteAllContributors) intent;
            final ProjectedEdge edge = findEdge(projection, delete.edge());
            if (edge != null) {
                final List<org.freeplane.plugin.graph.projection.ContributorKey> keys = new ArrayList<org.freeplane.plugin.graph.projection.ContributorKey>();
                final Map<org.freeplane.plugin.graph.projection.ContributorKey, org.freeplane.plugin.graph.projection.input.ConnectorDescriptor> descriptors =
                    new LinkedHashMap<org.freeplane.plugin.graph.projection.ContributorKey, org.freeplane.plugin.graph.projection.input.ConnectorDescriptor>();
                for (final org.freeplane.plugin.graph.projection.ContributorKey key : delete.contributors()) {
                    final EdgeContributor contributor = findContributor(edge, key);
                    if (contributor == null) {
                        return;
                    }
                    keys.add(key);
                    if (contributor.connectorDescriptor().isPresent()) {
                        descriptors.put(key, contributor.connectorDescriptor().get());
                    }
                }
                executeCommand(GraphCommands.deleteAllContributors(currentState.generation(), edge.key(), keys,
                    descriptors));
            }
        }
    }

    private static ProjectedEdge findEdge(final GraphProjection projection,
            final org.freeplane.plugin.graph.projection.ProjectedEdgeKey key) {
        for (final ProjectedEdge edge : projection.edges()) {
            if (edge.key().equals(key)) {
                return edge;
            }
        }
        return null;
    }

    private static ProjectedEdge findEdgeContaining(final GraphProjection projection,
            final org.freeplane.plugin.graph.projection.ContributorKey key) {
        for (final ProjectedEdge edge : projection.edges()) {
            if (findContributor(edge, key) != null) {
                return edge;
            }
        }
        return null;
    }

    private static EdgeContributor findContributor(final ProjectedEdge edge,
            final org.freeplane.plugin.graph.projection.ContributorKey key) {
        for (final EdgeContributor contributor : edge.contributors()) {
            if (contributor.key().equals(key)) {
                return contributor;
            }
        }
        return null;
    }

    private void showDialog(final String titleKey, final JPanel panel) {
        final JDialog window = new JDialog((Window) null, TextUtils.getText(titleKey),
            Dialog.ModalityType.APPLICATION_MODAL);
        if (panel instanceof WorkspaceCloseDialog) {
            ((WorkspaceCloseDialog) panel).attachWindow(window);
        }
        window.setContentPane(panel);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
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

    JMenuBar menuBar() {
        return menuBar;
    }

    JPanel content() {
        return content;
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

    void installWorkspaceHistoryKeys(final JRootPane root) {
        final JRootPane value = Objects.requireNonNull(root, "root");
        value.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl Z"),
            "graph-workspace-undo");
        value.getActionMap().put("graph-workspace-undo", undoWorkspaceAction);
        value.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl Y"),
            "graph-workspace-redo");
        value.getActionMap().put("graph-workspace-redo", redoWorkspaceAction);
    }

    void setReadOnly(final boolean value) {
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                setReadOnlyOnEdt(value);
            }
        });
    }

    boolean isReadOnly() {
        return readOnly;
    }

    void completeInitialLayout() {
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                initialViewportLayoutReady = true;
                applyInitialViewport(currentState);
            }
        });
    }

    void acceptCanvasState(final CanvasState state) {
        Objects.requireNonNull(state, "state");
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                currentState = state;
                refreshPresentation();
                canvas.setCanvasState(state);
                applyInitialViewport(state);
                updateMapRows(state);
                updateStatusBar();
                search(toolbar.searchField().getText());
            }
        });
    }

    void close() {
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                closed = true;
                canvasRegistration.close();
                sessionStatusRegistration.close();
                interactionController.uninstall();
            }
        });
    }

    private void setReadOnlyOnEdt(final boolean value) {
        readOnly = value;
        toolbar.setReadOnly(value);
        toolbar.settingsButton().setEnabled(true);
        mapList.setReadOnly(value);
        settingsPanel.setReadOnly(value);
        if (value) {
            interactionController.setTool(InteractionTool.SELECT);
            toolbar.selectButton().setSelected(true);
        }
        if (contributorInspector != null) {
            contributorInspector.setReadOnly(value);
        }
        updateMapRows(currentState);
        updateStatusBar();
    }

    private void updateMenuEnablement() {
        if (fileSaveMenuItem == null) {
            return;
        }
        fileSaveMenuItem.setEnabled(toolbar.saveButton().isEnabled());
        fileSaveAsMenuItem.setEnabled(toolbar.saveAsButton().isEnabled());
        toolbar.settingsButton().setEnabled(true);
        viewSettingsMenuItem.setEnabled(true);
        final MapListPanel.MapRow selectedMap = mapList.selectedRow();
        mapsAddMenuItem.setEnabled(!readOnly);
        mapsRemoveMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsRetryMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.state() == MapListPanel.RowState.RETRYABLE);
        mapsLocateMenuItem.setEnabled(!readOnly && selectedMap != null
            && (selectedMap.state() == MapListPanel.RowState.MISSING
                || selectedMap.state() == MapListPanel.RowState.RETRYABLE));
    }

    private JPanel createContent() {
        final JPanel graphArea = new JPanel(new BorderLayout(0, 0));
        graphArea.setName("graph-workspace-graph-area");
        graphArea.add(mapList, BorderLayout.WEST);
        graphArea.add(canvas, BorderLayout.CENTER);
        graphArea.add(settingsPanel, BorderLayout.EAST);

        final JPanel result = new JPanel(new BorderLayout(0, 0));
        result.setName("graph-workspace-content");
        result.add(toolbar, BorderLayout.NORTH);
        result.add(graphArea, BorderLayout.CENTER);
        result.add(statusSlot, BorderLayout.SOUTH);
        return result;
    }

    private JMenuBar createMenuBar() {
        final JMenuBar result = new JMenuBar();
        result.setName("graph-workspace-menu-bar");
        final JMenu file = menu("graph_workspace.menu.file", "graph-workspace-file-menu");
        file.add(item("graph_workspace.action.open", "open", event -> toolbar.openButton().doClick()));
        fileSaveMenuItem = item("graph_workspace.action.save", "save",
            event -> toolbar.saveButton().doClick());
        file.add(fileSaveMenuItem);
        fileSaveAsMenuItem = item("graph_workspace.action.save_as", "save-as",
            event -> toolbar.saveAsButton().doClick());
        file.add(fileSaveAsMenuItem);
        file.addSeparator();
        file.add(item("graph_workspace.action.close", "close", event -> closeRequest.run()));

        final JMenu edit = menu("graph_workspace.menu.edit", "graph-workspace-edit-menu");
        edit.add(actionItem("undo", undoWorkspaceAction));
        edit.add(actionItem("redo", redoWorkspaceAction));
        edit.add(actionItem("undo-source-map", sourceMapUndoAction));

        final JMenu view = menu("graph_workspace.menu.view", "graph-workspace-view-menu");
        view.add(item("graph_workspace.action.fit_graph", "fit-graph", event -> toolbar.fitGraphButton().doClick()));
        view.add(item("graph_workspace.action.reset_zoom", "reset-zoom",
            event -> toolbar.resetZoomButton().doClick()));
        view.add(item("graph_workspace.action.zoom_in", "zoom-in", event -> toolbar.zoomInButton().doClick()));
        view.add(item("graph_workspace.action.zoom_out", "zoom-out", event -> toolbar.zoomOutButton().doClick()));
        viewSettingsMenuItem = item("graph_workspace.action.settings", "settings",
            event -> toolbar.settingsButton().doClick());
        view.add(viewSettingsMenuItem);

        final JMenu maps = menu("graph_workspace.menu.maps", "graph-workspace-maps-menu");
        mapsAddMenuItem = item("graph_workspace.action.add_map", "add-map",
            event -> mapList.addButton().doClick());
        maps.add(mapsAddMenuItem);
        mapsRemoveMenuItem = item("graph_workspace.action.remove_map", "remove-map",
            event -> mapList.removeButton().doClick());
        maps.add(mapsRemoveMenuItem);
        mapsRetryMenuItem = item("graph_workspace.action.retry_map", "retry-map",
            event -> mapList.retryButton().doClick());
        maps.add(mapsRetryMenuItem);
        mapsLocateMenuItem = item("graph_workspace.action.locate_map", "locate-map",
            event -> mapList.locateButton().doClick());
        maps.add(mapsLocateMenuItem);

        result.add(file);
        result.add(edit);
        result.add(view);
        result.add(maps);
        return result;
    }

    private void updateMapRows(final CanvasState state) {
        final Map<MapReferenceId, RowAccumulator> accumulators =
            new LinkedHashMap<MapReferenceId, RowAccumulator>();
        final List<GraphWorkspaceViewBinding.MapRegistration> registrations =
            Objects.requireNonNull(binding.currentMapRows(), "currentMapRows");
        for (final GraphWorkspaceViewBinding.MapRegistration registration : registrations) {
            final GraphWorkspaceViewBinding.MapRegistration value = Objects.requireNonNull(registration,
                "map registration");
            accumulators.put(value.mapReferenceId(), new RowAccumulator(value.mapReferenceId(),
                value.displayName(), value.availability()));
        }
        if (state != null) {
            final GraphProjection projection = state.projection();
            for (final ProjectedNode node : projection.nodes()) {
                final MapReferenceId mapId = node.mapReferenceId();
                RowAccumulator accumulator = accumulators.get(mapId);
                if (accumulator == null) {
                    accumulator = new RowAccumulator(mapId, node.mapName(), MapAvailability.AVAILABLE);
                    accumulators.put(mapId, accumulator);
                }
                else {
                    accumulator.displayName = node.mapName();
                }
                accumulator.projectedNodeCount++;
            }
            for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
                final MapReferenceId mapId = enclosure.mapReferenceId();
                RowAccumulator accumulator = accumulators.get(mapId);
                if (accumulator == null) {
                    accumulators.put(mapId, new RowAccumulator(mapId, enclosure.mapName(),
                        MapAvailability.AVAILABLE));
                }
                else if (accumulator.projectedNodeCount == 0) {
                    accumulator.displayName = enclosure.mapName();
                }
            }
        }
        final List<MapListPanel.MapRow> rows = new ArrayList<MapListPanel.MapRow>(accumulators.size());
        for (final RowAccumulator accumulator : accumulators.values()) {
            final MapListPanel.RowState rowState = readOnly ? MapListPanel.RowState.READ_ONLY
                : rowStateFor(accumulator.availability);
            rows.add(MapListPanel.MapRow.of(accumulator.mapReferenceId, accumulator.displayName, rowState,
                accumulator.projectedNodeCount, selectedNode != null
                    && selectedNode.mapReferenceId().equals(accumulator.mapReferenceId)));
        }
        mapList.setRows(rows);
        updateMenuEnablement();
    }

    private static MapListPanel.RowState rowStateFor(final MapAvailability availability) {
        switch (availability) {
        case AVAILABLE:
            return MapListPanel.RowState.ACTIVE;
        case LOADING:
            return MapListPanel.RowState.LOADING;
        case MISSING:
            return MapListPanel.RowState.MISSING;
        case INACTIVE:
            return MapListPanel.RowState.INACTIVE;
        case UNREADABLE:
        case PASSWORD_REQUIRED:
        case RELOAD_REQUIRED:
            return MapListPanel.RowState.RETRYABLE;
        default:
            throw new IllegalArgumentException("Unknown map availability");
        }
    }

    void acceptIntent(final GraphIntent intent) {
        handleIntent(Objects.requireNonNull(intent, "intent"));
    }

    private void handleIntent(final GraphIntent intent) {
        Objects.requireNonNull(intent, "intent");
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
        else if (intent instanceof GraphIntent.InspectEdge
                || intent instanceof GraphIntent.DeleteContributor
                || intent instanceof GraphIntent.DeleteAllContributors) {
            handleEdgeIntent(intent);
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
                executeCommand(GraphCommands.unpinAll());
            }
        }
        else if (intent instanceof GraphIntent.Connect) {
            if (readOnly) {
                return;
            }
            final GraphIntent.Connect connect = (GraphIntent.Connect) intent;
            executeCommand(GraphCommands.connect(connect.source().isNode()
                ? connect.source().node().get().source() : source(connect.source()),
                connect.target().isNode() ? connect.target().node().get().source() : source(connect.target()),
                connect.direction()));
        }
        else if (intent instanceof GraphIntent.OpenSourceNode) {
            final ProjectedEndpointKey endpoint = ((GraphIntent.OpenSourceNode) intent).endpoint();
            executeCommand(GraphCommands.openSource(source(endpoint)));
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
            executeCommand(GraphCommands.pin(reference, x, y));
        }
    }

    private void executeUnpin(final ProjectedNodeKey node) {
        final NodeReference reference = node.source().persistedReference().orElse(null);
        if (reference != null && !readOnly) {
            executeCommand(GraphCommands.unpin(reference));
        }
    }

    private static SourceNodeKey source(final ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get().source()
            : endpoint.enclosure().get().source();
    }

    private void applyInitialViewport(final CanvasState state) {
        if (!initialViewportLayoutReady || !initialViewportPending || state == null) {
            return;
        }
        final Bounds bounds = graphBounds(state);
        if (bounds == null) {
            return;
        }
        initialViewportPending = false;
        final Dimension size = canvas.getSize();
        final Dimension overlapSize = size.width > 0 && size.height > 0 ? size : CANVAS_PREFERRED_SIZE;
        if (!currentPresentation.displaySettings().rememberViewport()
                || !initialViewport.overlaps(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, overlapSize)) {
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

    private static JMenu menu(final String titleKey, final String name) {
        final JMenu menu = new JMenu(TextUtils.getText(titleKey));
        menu.setName(name);
        return menu;
    }

    private static JMenuItem actionItem(final String name, final Action action) {
        final JMenuItem item = new JMenuItem(action);
        item.setName("graph-workspace-menu-item-" + name);
        return item;
    }

    private static JMenuItem item(final String titleKey, final String name,
            final ActionListener listener) {
        final JMenuItem item = new JMenuItem(TextUtils.getText(titleKey));
        item.setName("graph-workspace-menu-item-" + name);
        item.addActionListener(listener);
        return item;
    }

    private static final class RowAccumulator {
        private final MapReferenceId mapReferenceId;
        private String displayName;
        private final MapAvailability availability;
        private int projectedNodeCount;

        private RowAccumulator(final MapReferenceId mapReferenceId, final String displayName,
                final MapAvailability availability) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.availability = Objects.requireNonNull(availability, "availability");
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

final class HeadlessGraphWorkspaceView implements GraphWorkspaceView {
    private final WorkspaceCloseController closeController;
    private final GraphWorkspaceWindowModel model;
    private boolean visible;
    private boolean closed;

    HeadlessGraphWorkspaceView(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        model = new GraphWorkspaceWindowModel(handle, binding, applicationController, pathChooser, closeController,
            new Runnable() {
                @Override
                public void run() {
                    requestClose();
                }
            }, null, new Runnable() {
                @Override
                public void run() {
                    close();
                }
            });
        model.completeInitialLayout();
    }

    @Override
    public void show() {
        if (!closed) {
            visible = true;
        }
    }

    @Override
    public void focus() {
        if (!closed) {
            visible = true;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        visible = false;
        model.close();
    }

    boolean isVisible() {
        return visible;
    }

    GraphWorkspaceWindowModel model() {
        return model;
    }

    private void requestClose() {
        if (!closed) {
            model.requestClose();
        }
    }
}
