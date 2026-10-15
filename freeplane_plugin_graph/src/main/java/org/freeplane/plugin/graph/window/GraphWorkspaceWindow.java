package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.FrameResynchronizer;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.group.GraphGroupColors;
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
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;

final class GraphWorkspaceWindow extends JFrame implements GraphWorkspaceView, ScreenFillSurface {
    private static final long serialVersionUID = 1L;
    private static final Dimension WINDOW_SIZE = new Dimension(1280, 800);

    private final WorkspaceCloseController closeController;
    private final GraphWorkspaceWindowModel model;
    private final IFreeplanePropertyListener colorChangeListener;
    private final GraphPresentationMode presentationMode;
    private final FrameTransitions frameTransitions;
    private final ComponentAdapter presentationFrameListener;
    private boolean closed;

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser, final RecentWorkspaceList recentWorkspaces) {
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
            }, message -> Controller.getCurrentController().getViewController().out(message), recentWorkspaces);
        setJMenuBar(model.menuBar());
        setContentPane(model.content());
        model.installWorkspaceHistoryKeys(getRootPane());
        final Consumer<String> presentationLog = message ->
            Controller.getCurrentController().getViewController().out(message);
        frameTransitions = new FrameTransitions(this::rawEnterFullScreen, this::rawRestoreWindowed, presentationLog);
        presentationMode = model.createPresentationMode(this, getLayeredPane(),
            () -> canvas().requestFocusInWindow(), presentationLog);
        installPresentationKeys(getRootPane(), presentationMode);
        presentationFrameListener = new ComponentAdapter() {
            @Override
            public void componentMoved(final ComponentEvent event) {
                model.presentationFrameGeometryChanged(getBounds());
            }

            @Override
            public void componentResized(final ComponentEvent event) {
                model.presentationFrameGeometryChanged(getBounds());
            }
        };
        addComponentListener(presentationFrameListener);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                requestClose();
            }
        });
        // X11/KWin window managers may leave the AWT coordinate cache stale after
        // maximize, unmaximize, or arbitrary moves; without this resynchronizer
        // the frame's popup menus are misplaced and dismiss on release.
        FrameResynchronizer.install(this);
        colorChangeListener = repaintOnColorChange(new Runnable() {
            @Override
            public void run() {
                canvas().repaint();
            }
        });
        ResourceController.getResourceController().addPropertyChangeListener(colorChangeListener);
        pack();
        setSize(WINDOW_SIZE);
        validate();
        doLayout();
        model.completeInitialLayout();
        setLocationByPlatform(true);
    }

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        this(handle, binding, closeController, applicationController, pathChooser, RecentWorkspaceList.empty());
    }

    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController) {
        this(handle, binding, closeController, applicationController, GraphWorkspaceWindow::chooseWorkspacePath);
    }

    static IFreeplanePropertyListener repaintOnColorChange(final Runnable repaint) {
        return new IFreeplanePropertyListener() {
            @Override
            public void propertyChanged(final String propertyName, final String newValue,
                    final String oldValue) {
                if (GraphGroupColors.COLOR_PROPERTY_KEY.equals(propertyName)) {
                    repaint.run();
                }
            }
        };
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
                    showWindowOnEdt();
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
                        showWindowOnEdt();
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

    @SuppressWarnings("deprecation")
    private void showWindowOnEdt() {
        // setVisible(true) dispatches to this GraphWorkspaceView.show() override.
        super.show();
    }

    private void closeOnEdt() {
        if (closed) {
            return;
        }
        closed = true;
        presentationMode.windowClosed();
        removeComponentListener(presentationFrameListener);
        ResourceController.getResourceController().removePropertyChangeListener(colorChangeListener);
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

    @Override
    public FrameState captureFrameState() {
        return new FrameState(getBounds(), getExtendedState());
    }

    @Override
    public Survival applyScreenFill(final FrameState previous) {
        return frameTransitions.applyScreenFill(previous);
    }

    @Override
    public Survival restoreFrameState(final FrameState previous) {
        return frameTransitions.restoreFrameState(previous);
    }

    void rawEnterFullScreen() {
        final Rectangle graphics = getGraphicsConfiguration().getBounds();
        dispose();
        setUndecorated(true);
        setExtendedState(Frame.MAXIMIZED_BOTH);
        setBounds(graphics);
        if (Compat.isWindowsOS()) {
            setResizable(false);
        }
        setVisible(true);
        validate();
    }

    void rawRestoreWindowed(final FrameState previous) {
        dispose();
        setUndecorated(false);
        setBounds(previous.bounds());
        setExtendedState(previous.extendedState());
        if (Compat.isWindowsOS()) {
            setResizable(false);
        }
        setResizable(true);
        setVisible(true);
        validate();
    }

    static void installPresentationKeys(final JRootPane root, final GraphPresentationMode mode) {
        final String actionName = "graph-workspace-toggle-full-screen";
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), actionName);
        root.getActionMap().put(actionName, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                if (mode.isAvailable()) {
                    mode.toggle();
                }
            }
        });
    }

    static final class FrameTransitions {
        private final Runnable enterRaw;
        private final Consumer<FrameState> restoreRaw;
        private final Consumer<String> log;

        FrameTransitions(final Runnable enterRaw, final Consumer<FrameState> restoreRaw,
                final Consumer<String> log) {
            this.enterRaw = Objects.requireNonNull(enterRaw, "enterRaw");
            this.restoreRaw = Objects.requireNonNull(restoreRaw, "restoreRaw");
            this.log = Objects.requireNonNull(log, "log");
        }

        Survival applyScreenFill(final FrameState previous) {
            try {
                enterRaw.run();
                return Survival.REENTERED_FULLSCREEN;
            }
            catch (RuntimeException | Error primary) {
                logFailure("enter full screen", primary);
                try {
                    restoreRaw.accept(previous);
                    return Survival.RESTORED_WINDOWED;
                }
                catch (RuntimeException | Error inverse) {
                    logFailure("restore windowed after failed entry", inverse);
                    return Survival.UNKNOWN_FRAME;
                }
            }
        }

        Survival restoreFrameState(final FrameState previous) {
            try {
                restoreRaw.accept(previous);
                return Survival.RESTORED_WINDOWED;
            }
            catch (RuntimeException | Error primary) {
                logFailure("restore windowed", primary);
                try {
                    enterRaw.run();
                    return Survival.REENTERED_FULLSCREEN;
                }
                catch (RuntimeException | Error inverse) {
                    logFailure("enter full screen after failed restore", inverse);
                    return Survival.UNKNOWN_FRAME;
                }
            }
        }

        private void logFailure(final String operation, final Throwable failure) {
            try {
                log.accept("Graph presentation " + operation + " failed: " + failure);
            }
            catch (RuntimeException | Error loggingFailure) {
                // Recovery remains bounded even when the diagnostic sink fails.
            }
        }
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

final class GraphWorkspaceWindowModel implements PresentationChrome {
    static final Dimension CANVAS_PREFERRED_SIZE = new Dimension(800, 560);

    private final GraphWorkspaceHandle handle;
    private final GraphWorkspaceViewBinding binding;
    private final GraphWorkspaceController applicationController;
    private final RecentWorkspaceList recentWorkspaces;
    private final Runnable closeRequest;
    private final GraphCanvas canvas;
    private final JScrollPane graphScrollPane;
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
    private final Consumer<String> commandMessageSink;
    private final GraphWorkspaceHandle routedHandle;
    private final Action undoWorkspaceAction;
    private final Action redoWorkspaceAction;
    private final Action sourceMapUndoAction;
    private final GraphViewport initialViewport;
    private JMenuItem fileSaveMenuItem;
    private JMenuItem fileSaveAsMenuItem;
    private JMenu recentWorkspacesMenu;
    private JMenuItem viewSettingsMenuItem;
    private JMenuItem mapsAddMenuItem;
    private JMenuItem mapsDeactivateMenuItem;
    private JMenuItem mapsReactivateMenuItem;
    private JMenuItem mapsDeleteMenuItem;
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
    private boolean suppressViewportEvents = true;
    private GraphViewport lastPublishedViewport;
    private boolean readOnly;
    private boolean closed;
    private WorkspaceCloseDialog closeDialog;
    private ContributorInspector contributorInspector;
    private final MapSidebarPanel sidebar;
    private JSplitPane splitPane;
    private JCheckBoxMenuItem mapsSidebarMenuItem;
    private JCheckBoxMenuItem fullScreenMenuItem;
    private GraphPresentationMode presentationMode;
    private int appliedSidebarWidth;
    private boolean sidebarGestureActive;
    private boolean sidebarApplyPending;
    private boolean sidebarApplying;
    private Integer sessionSidebarWidth;
    private Boolean sessionSidebarHidden;
    private ChromeSnapshot chromeSnapshot;
    private SidebarOverride sidebarOverride;
    private boolean presentationHidden;
    private boolean sidebarKeyboardAction;
    private final ViewportHold viewportHold = new ViewportHold();
    private boolean applyingViewport;
    private boolean toolbarViewportOperation;
    private boolean baseViewportSuppression = true;
    private int viewportSuppressionDepth;
    private int userScrollDepth;
    private PresentationScheduler presentationScheduler = new PresentationScheduler() {
        @Override
        public long nowMillis() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override
        public ScheduledWork schedule(final int delayMillis, final Runnable callback) {
            final javax.swing.Timer timer = new javax.swing.Timer(delayMillis, event -> callback.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        }
    };
    private static final Runnable NO_SETTLE_CORRECTION = () -> { };

    interface PresentationScheduler {
        long nowMillis();
        ScheduledWork schedule(int delayMillis, Runnable callback);
    }

    interface ScheduledWork {
        void cancel();
    }

    PresentationViewport presentationViewport() {
        return viewportHold;
    }

    void setPresentationScheduler(final PresentationScheduler scheduler) {
        if (viewportHold.armed || viewportHold.closed) {
            throw new IllegalStateException("Presentation scheduler cannot change while armed or closed");
        }
        presentationScheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    void presentationFrameGeometryChanged(final Rectangle bounds) {
        viewportHold.frameGeometryChanged(bounds);
    }

    GraphPresentationMode createPresentationMode(final ScreenFillSurface surface, final JLayeredPane host,
            final Runnable focusCanvas, final Consumer<String> log) {
        if (presentationMode == null) {
            presentationMode = new GraphPresentationMode(surface, this, viewportHold,
                new PresentationHint(host, TextUtils.getText("graph_workspace.hint.full_screen_exit")),
                fullScreenMenuItem, focusCanvas, log);
            interactionController.setEscapeFallback(presentationMode::requestExit);
            fullScreenMenuItem.addActionListener(event -> presentationMode.toggle());
        }
        return presentationMode;
    }

    private final class ViewportHold implements PresentationViewport {
        private boolean armed;
        private boolean closed;
        private long generation;
        private Point baselinePosition;
        private Dimension baselineExtent;
        private Dimension baselineSurface;
        private GraphViewport baselineAnchor;
        private Dimension observedExtent;
        private Dimension observedSurface;
        private Point observedPosition;
        private Rectangle observedFrame;
        private boolean correcting;
        private boolean baselineSuppression;
        private long lastTransitionGeometryAt;
        private long lastFrameEventAt;
        private long lastViewportEventAt;
        private ScheduledWork quietWork;
        private long registeredSettleGeneration = -1;

        @Override
        public void capture() {
            if (closed || GraphWorkspaceWindowModel.this.closed) {
                return;
            }
            if (!armed) {
                baselineSuppression = baseViewportSuppression;
            }
            generation++;
            cancelWork();
            captureGeometry();
            armed = true;
            updateViewportSuppression();
            lastTransitionGeometryAt = presentationScheduler.nowMillis();
            lastFrameEventAt = lastTransitionGeometryAt;
            lastViewportEventAt = lastTransitionGeometryAt;
            scheduleQuietObservation(2500);
        }

        private void captureGeometry() {
            final javax.swing.JViewport viewport = graphScrollPane.getViewport();
            baselinePosition = viewport.getViewPosition();
            baselineExtent = viewport.getExtentSize();
            baselineSurface = canvas.getSize();
            baselineAnchor = canvas.viewport();
            observeGeometry();
        }

        private void modelViewportApplied() {
            if (!armed || closed) {
                return;
            }
            generation++;
            cancelWork();
            captureGeometry();
            // A model assignment changes the view being protected, without extending
            // the presentation transition's original horizon or absolute cap.
            final long next = Math.min(lastTransitionGeometryAt + 5000,
                Math.max(lastTransitionGeometryAt + 2500,
                    Math.max(lastFrameEventAt, lastViewportEventAt) + 200));
            scheduleQuietObservation((int) Math.max(1L, next - presentationScheduler.nowMillis()));
        }

        @Override
        public void compensate() {
            if (!armed || closed) {
                return;
            }
            lastTransitionGeometryAt = presentationScheduler.nowMillis();
            correctGeometry();
            if (quietWork != null) {
                quietWork.cancel();
            }
            scheduleQuietObservation(2500);
        }

        private void correctGeometry() {
            if (!armed || closed || correcting) {
                return;
            }
            correcting = true;
            try {
                final javax.swing.JViewport viewport = graphScrollPane.getViewport();
                final Dimension surface = canvas.getSize();
                final Dimension extent = viewport.getExtentSize();
                final int x = targetAxis(baselinePosition.x, baselineSurface.width, baselineExtent.width,
                    surface.width, extent.width);
                final int y = targetAxis(baselinePosition.y, baselineSurface.height, baselineExtent.height,
                    surface.height, extent.height);
                runWithViewportEventsSuppressed(() -> viewport.setViewPosition(new Point(x, y)));
                lastPublishedViewport = canvas.visibleViewport();
            }
            finally {
                observeGeometry();
                correcting = false;
            }
        }

        private int targetAxis(final int p0, final int w0, final int e0, final int w1, final int e1) {
            final double requested = (double) p0 + (((double) w1 - w0) - ((double) e1 - e0)) / 2.0;
            final long rounded = Math.round(requested);
            final int maximum = Math.max(0, w1 - e1);
            final int applied = (int) Math.max(0L, Math.min((long) maximum, rounded));
            if (rounded != applied) {
                logClamp(requested, rounded, applied, requested - applied);
            }
            return applied;
        }

        private void logClamp(final double requested, final long rounded, final int applied, final double residual) {
            logPresentation("Graph presentation viewport clamp requested=" + requested + " rounded=" + rounded
                + " applied=" + applied + " residual=" + residual);
        }

        @Override
        public void classify(final ChangeEvent event) {
            lastViewportEventAt = presentationScheduler.nowMillis();
            if (closed || GraphWorkspaceWindowModel.this.closed || correcting) {
                return;
            }
            if (!armed) {
                publishExternalViewport();
                return;
            }
            try {
                if (applyingViewport || toolbarViewportOperation) {
                    return;
                }
                if (!baselineAnchor.equals(canvas.viewport())) {
                    publishUserView();
                }
                else if (canvas.isViewportPositioningActive()) {
                    registerSettleCorrection();
                }
                else if (observedExtent.equals(graphScrollPane.getViewport().getExtentSize())
                        && observedSurface.equals(canvas.getSize())) {
                    if (!observedPosition.equals(graphScrollPane.getViewport().getViewPosition())) {
                        if (userScrollInputActive()) {
                            publishUserView();
                        }
                        else {
                            correctGeometry();
                        }
                    }
                }
                else {
                    correctGeometry();
                }
            }
            finally {
                observeGeometry();
            }
        }

        private void publishUserView() {
            emitViewportIfChanged(canvas.visibleViewport());
            disarm();
        }

        private void observeGeometry() {
            observedExtent = graphScrollPane.getViewport().getExtentSize();
            observedSurface = canvas.getSize();
            observedPosition = graphScrollPane.getViewport().getViewPosition();
        }

        private void registerSettleCorrection() {
            if (!armed || closed || registeredSettleGeneration == generation) {
                return;
            }
            final long registered = generation;
            registeredSettleGeneration = registered;
            canvas.runWhenViewportSettled(() -> {
                if (!closed && !GraphWorkspaceWindowModel.this.closed && armed && generation == registered) {
                    correctGeometry();
                }
            });
        }

        @Override
        public void canvasPublication() {
            disarm();
        }

        @Override
        public void toolbarPublication(final GraphViewport value) {
            disarm();
        }

        @Override
        public void disarm() {
            lastPublishedViewport = canvas.visibleViewport();
            if (!armed) {
                return;
            }
            generation++;
            cancelWork();
            armed = false;
            baseViewportSuppression = baselineSuppression;
            updateViewportSuppression();
        }

        @Override
        public void windowClosed() {
            if (closed) {
                return;
            }
            closed = true;
            generation++;
            cancelWork();
            lastPublishedViewport = canvas.visibleViewport();
            if (armed) {
                baseViewportSuppression = baselineSuppression;
            }
            armed = false;
            updateViewportSuppression();
        }

        private void cancelWork() {
            if (quietWork != null) {
                quietWork.cancel();
                quietWork = null;
            }
            registeredSettleGeneration = -1;
            canvas.runWhenViewportSettled(NO_SETTLE_CORRECTION);
        }

        private void frameGeometryChanged(final Rectangle bounds) {
            if (closed) {
                return;
            }
            final Rectangle next = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
            if (!next.equals(observedFrame)) {
                observedFrame = next;
                lastFrameEventAt = presentationScheduler.nowMillis();
            }
        }

        private void scheduleQuietObservation(final int delayMillis) {
            final long scheduledGeneration = generation;
            quietWork = presentationScheduler.schedule(delayMillis, () -> {
                if (closed || GraphWorkspaceWindowModel.this.closed || !armed || generation != scheduledGeneration) {
                    return;
                }
                quietWork = null;
                final long now = presentationScheduler.nowMillis();
                final long horizon = lastTransitionGeometryAt + 2500;
                final long cap = lastTransitionGeometryAt + 5000;
                final long quietAt = Math.max(lastFrameEventAt, lastViewportEventAt) + 200;
                if (now >= horizon && now >= quietAt) {
                    disarm();
                }
                else if (now >= cap) {
                    logPresentation("Graph presentation settlement failure generation=" + generation
                        + " transition=" + lastTransitionGeometryAt + " frame=" + lastFrameEventAt
                        + " viewport=" + lastViewportEventAt + " now=" + now);
                    disarm();
                }
                else {
                    scheduleQuietObservation((int) Math.max(1L, Math.min(cap, Math.max(horizon, quietAt)) - now));
                }
            });
        }
    }

    private boolean userScrollInputActive() {
        return userScrollDepth > 0
            || graphScrollPane.getHorizontalScrollBar().getValueIsAdjusting()
            || graphScrollPane.getVerticalScrollBar().getValueIsAdjusting();
    }

    private final class UserScrollPane extends JScrollPane {
        private static final long serialVersionUID = 1L;

        UserScrollPane() {
            super(canvas);
            installScrollbarMouseMarker(getHorizontalScrollBar());
            installScrollbarMouseMarker(getVerticalScrollBar());
        }

        @Override
        protected void processMouseWheelEvent(final MouseWheelEvent event) {
            userScrollDepth++;
            try {
                super.processMouseWheelEvent(event);
            }
            finally {
                userScrollDepth--;
            }
        }

        @Override
        protected boolean processKeyBinding(final KeyStroke stroke, final KeyEvent event,
                final int condition, final boolean pressed) {
            final int key = event.getKeyCode();
            final boolean scroll = isFocusOwner() && (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT
                || key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN || key == KeyEvent.VK_PAGE_UP
                || key == KeyEvent.VK_PAGE_DOWN || key == KeyEvent.VK_HOME || key == KeyEvent.VK_END);
            if (scroll) {
                userScrollDepth++;
            }
            try {
                return super.processKeyBinding(stroke, event, condition, pressed);
            }
            finally {
                if (scroll) {
                    userScrollDepth--;
                }
            }
        }

        @Override
        public JScrollBar createHorizontalScrollBar() {
            return userScrollBar(JScrollBar.HORIZONTAL);
        }

        @Override
        public JScrollBar createVerticalScrollBar() {
            return userScrollBar(JScrollBar.VERTICAL);
        }

        private JScrollBar userScrollBar(final int orientation) {
            return new ScrollBar(orientation) {
                private static final long serialVersionUID = 1L;

                @Override
                public void setValueIsAdjusting(final boolean adjusting) {
                    final boolean input = adjusting || getValueIsAdjusting();
                    if (input) {
                        userScrollDepth++;
                    }
                    try {
                        super.setValueIsAdjusting(adjusting);
                    }
                    finally {
                        if (input) {
                            userScrollDepth--;
                        }
                    }
                }
            };
        }

        private void installScrollbarMouseMarker(final Component component) {
            final boolean[] down = {false};
            final MouseListener[] delegates = component.getMouseListeners();
            for (MouseListener listener : delegates) {
                component.removeMouseListener(listener);
            }
            // Mark before the UI's press handler, including scrollbar arrow buttons.
            component.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(final MouseEvent event) {
                    if (!down[0]) {
                        down[0] = true;
                        userScrollDepth++;
                    }
                }
            });
            for (MouseListener listener : delegates) {
                component.addMouseListener(listener);
            }
            component.addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(final MouseEvent event) {
                    if (down[0]) {
                        down[0] = false;
                        userScrollDepth--;
                    }
                }
            });
            if (component instanceof java.awt.Container) {
                for (Component child : ((java.awt.Container) component).getComponents()) {
                    installScrollbarMouseMarker(child);
                }
            }
        }
    }

    private void logPresentation(final String message) {
        try {
            commandMessageSink.accept(message);
        }
        catch (RuntimeException | Error loggingFailure) {
            // Diagnostics cannot break viewport recovery or timer cleanup.
        }
    }

    private static final class SidebarOverride {
        final boolean collapsed;
        final int dividerLocation;
        final int dividerSize;
        final boolean enabled;

        SidebarOverride(final boolean collapsed, final int dividerLocation, final int dividerSize,
                final boolean enabled) {
            this.collapsed = collapsed;
            this.dividerLocation = dividerLocation;
            this.dividerSize = dividerSize;
            this.enabled = enabled;
        }
    }

    private static final class ChromeSnapshot {
        final boolean menuVisible;
        final boolean toolbarVisible;
        final boolean sidebarVisible;
        final boolean settingsVisible;
        final boolean statusVisible;
        final SidebarOverride sidebar;

        ChromeSnapshot(final boolean menuVisible, final boolean toolbarVisible, final boolean sidebarVisible,
                final boolean settingsVisible, final boolean statusVisible, final SidebarOverride sidebar) {
            this.menuVisible = menuVisible;
            this.toolbarVisible = toolbarVisible;
            this.sidebarVisible = sidebarVisible;
            this.settingsVisible = settingsVisible;
            this.statusVisible = statusVisible;
            this.sidebar = sidebar;
        }
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final Runnable closeRequest) {
        this(handle, binding, applicationController, pathChooser, noOpCloseController(), closeRequest, null,
            closeRequest, noOpCommandMessageSink());
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final Runnable closeRequest, final Runnable graphFocus) {
        this(handle, binding, applicationController, pathChooser, noOpCloseController(), closeRequest, graphFocus,
            closeRequest, noOpCommandMessageSink());
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus) {
        this(handle, binding, applicationController, pathChooser, closeController, closeRequest, graphFocus,
            closeRequest, noOpCommandMessageSink());
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus, final Runnable closeCompletion) {
        this(handle, binding, applicationController, pathChooser, closeController, closeRequest, graphFocus,
            closeCompletion, noOpCommandMessageSink());
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus, final Runnable closeCompletion, final Consumer<String> commandMessageSink) {
        this(handle, binding, applicationController, pathChooser, closeController, closeRequest, graphFocus,
            closeCompletion, commandMessageSink, RecentWorkspaceList.empty());
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus, final Runnable closeCompletion, final Consumer<String> commandMessageSink,
            final RecentWorkspaceList recentWorkspaces) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        this.closeRequest = Objects.requireNonNull(closeRequest, "closeRequest");
        this.closeCompletion = Objects.requireNonNull(closeCompletion, "closeCompletion");
        this.commandMessageSink = Objects.requireNonNull(commandMessageSink, "commandMessageSink");
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
        graphScrollPane = new UserScrollPane();
        graphScrollPane.setName("graph-workspace-scroll-pane");
        graphScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        graphScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        graphScrollPane.getViewport().addChangeListener(event -> presentationViewport().classify(event));
        canvas.setUserViewportChangeListener(this::publishCanvasViewport);
        mapList = new MapListPanel(routedHandle, pathChooser);
        mapList.addSelectionListener(row -> updateMenuEnablement());
        sidebar = new MapSidebarPanel(mapList, this::setSidebarCollapsed);
        sidebar.collapseButton().addActionListener(event -> sidebarToggledFromUser(true));
        sidebar.rail().restoreButton().addActionListener(event -> sidebarToggledFromUser(false));
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
        toolbar.setSettingsAction(this::toggleSettingsPanel);
        toolbar.setToolListener(tool -> interactionController.setTool(tool));
        toolbar.setDirectionListener(interactionController::setRelationshipDirection);
        toolbar.setSearchListener(this::search);
        toolbar.setViewportOperation(this::runToolbarViewportOperation);
        toolbar.setViewportListener(this::publishToolbarViewport);
        toolbar.setPinAction(this::togglePinSelectedNode);

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
        toolbar.setSettingsVisible(settingsPanel.isVisible());

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
        applyModelViewport(new Runnable() {
            @Override
            public void run() {
                canvas.setViewport(initialViewport);
            }
        });
        lastPublishedViewport = canvas.visibleViewport();
        baseViewportSuppression = false;
        updateViewportSuppression();
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

    private static Consumer<String> noOpCommandMessageSink() {
        return new Consumer<String>() {
            @Override
            public void accept(final String message) {
            }
        };
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

    void applyPresentation(final GraphWorkspacePresentation value) {
        final GraphWorkspacePresentation next = Objects.requireNonNull(value, "presentation");
        final DisplaySettings previousSettings = currentPresentation == null
            ? null : currentPresentation.displaySettings();
        final DisplaySettings settings = next.displaySettings();
        if (previousSettings != null && (previousSettings.mapSidebarWidth() != settings.mapSidebarWidth()
                || previousSettings.mapSidebarHidden() != settings.mapSidebarHidden())) {
            sidebarOverride = null;
        }
        currentPresentation = next;
        settingsPanel.setSettings(settings);
        canvas.setTheme(GraphTheme.resolve(settings.canvasTheme(), palette(next)));
        canvas.setShowArrowheads(settings.showArrowheads());
        canvas.setDimUnrelated(settings.dimUnrelatedNodes());
        if (previousSettings != null && previousSettings.rememberViewport()
                && !settings.rememberViewport()) {
            initialViewportPending = true;
        }
        applySidebarSettings(settings);
    }

    @Override
    public void capture() {
        try {
            final SidebarOverride effective = new SidebarOverride(sidebar.isCollapsed(),
                splitPane.getDividerLocation(), splitPane.getDividerSize(), splitPane.isEnabled());
            final ChromeSnapshot captured = new ChromeSnapshot(menuBar.isVisible(), toolbar.isVisible(),
                sidebar.isVisible(), settingsPanel.isVisible(), statusSlot.isVisible(), effective);
            chromeSnapshot = captured;
            sidebarOverride = effective;
        }
        catch (RuntimeException | Error failure) {
            if (chromeSnapshot == null || sidebarOverride == null) {
                discard();
            }
            throw failure;
        }
    }

    @Override
    public void hide() {
        presentationHidden = true;
        menuBar.setVisible(false);
        toolbar.setVisible(false);
        sidebar.setVisible(false);
        splitPane.setDividerSize(0);
        settingsPanel.setVisible(false);
        toolbar.setSettingsVisible(false);
        statusSlot.setVisible(false);
    }

    @Override
    public void restore() {
        final ChromeSnapshot captured = chromeSnapshot;
        try {
            if (captured == null) {
                return;
            }
            restoreChromePart(() -> menuBar.setVisible(captured.menuVisible));
            restoreChromePart(() -> toolbar.setVisible(captured.toolbarVisible));
            restoreChromePart(() -> sidebar.setVisible(captured.sidebarVisible));
            restoreChromePart(() -> settingsPanel.setVisible(captured.settingsVisible));
            restoreChromePart(() -> toolbar.setSettingsVisible(settingsPanel.isVisible()));
            restoreChromePart(() -> statusSlot.setVisible(captured.statusVisible));
            sidebarOverride = captured.sidebar;
            applySidebarOverride(captured.sidebar);
        }
        finally {
            presentationHidden = false;
        }
    }

    private void restoreChromePart(final Runnable operation) {
        try {
            operation.run();
        }
        catch (RuntimeException | Error failure) {
            try {
                commandMessageSink.accept("Graph presentation chrome restore failed: " + failure);
            }
            catch (RuntimeException | Error loggingFailure) {
                // Restoration must continue even if the diagnostic sink fails.
            }
        }
    }

    @Override
    public void discard() {
        chromeSnapshot = null;
        sidebarOverride = null;
        presentationHidden = false;
    }

    void sidebarToggledFromUser(final boolean collapsed) {
        sidebarOverride = null;
        setSidebarCollapsed(collapsed);
    }

    int appliedSidebarWidth() {
        return appliedSidebarWidth;
    }

    void applySidebarSettings(final DisplaySettings settings) {
        if (presentationHidden) {
            return;
        }
        if (sidebarOverride != null) {
            applySidebarOverride(sidebarOverride);
            return;
        }
        applyCurrentSidebarSettings(settings);
    }

    private void applySidebarOverride(final SidebarOverride effective) {
        runWithSidebarGuard(() -> {
            restoreChromePart(() -> sidebar.setCollapsed(effective.collapsed));
            restoreChromePart(() -> splitPane.setDividerSize(effective.dividerSize));
            restoreChromePart(() -> splitPane.setEnabled(effective.enabled));
            restoreChromePart(() -> sidebar.setMinimumSize(new Dimension(effective.collapsed
                ? MapSidebarLayout.RAIL_WIDTH
                : MapSidebarLayout.effectiveMinimum(splitWidth(), effectiveDividerWidth(), splitInsets()), 0)));
            restoreChromePart(() -> graphScrollPane.setMinimumSize(new Dimension(
                MapSidebarLayout.canvasMinimumWidth(splitWidth(), effectiveDividerWidth(), splitInsets()), 0)));
            restoreChromePart(() -> mapsSidebarMenuItem.setSelected(!effective.collapsed));
            appliedSidebarWidth = effective.dividerLocation;
            sidebarGestureActive = false;
            sidebarApplyPending = splitPane.getWidth() <= 0;
            if (!sidebarApplyPending) {
                restoreChromePart(() -> splitPane.setDividerLocation(effective.dividerLocation));
            }
            restoreChromePart(() -> sidebar.mapList().syncListWidths());
        });
    }

    private void applyCurrentSidebarSettings(final DisplaySettings settings) {
        final boolean hidden = readOnly && sessionSidebarHidden != null
            ? sessionSidebarHidden.booleanValue() : settings.mapSidebarHidden();
        final int width = readOnly && sessionSidebarWidth != null
            ? sessionSidebarWidth.intValue() : settings.mapSidebarWidth();
        if (sidebarGestureActive) {
            sidebarApplyPending = true;
            return;
        }
        if (splitPane.getWidth() <= 0) {
            sidebarApplyPending = true;
            return;
        }
        runWithSidebarGuard(new Runnable() {
            @Override
            public void run() {
                sidebar.setCollapsed(hidden);
                splitPane.setDividerSize(hidden ? 0 : 6);
                splitPane.setEnabled(!hidden);
                final int splitWidth = splitWidth();
                final int dividerWidth = effectiveDividerWidth();
                final Insets insets = splitInsets();
                final int effectiveMinimum = MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets);
                final int canvasMinimum = MapSidebarLayout.canvasMinimumWidth(splitWidth, dividerWidth, insets);
                final int appliedWidth = hidden ? MapSidebarLayout.RAIL_WIDTH
                    : MapSidebarLayout.clampWidth(width, splitWidth, dividerWidth, insets);
                sidebar.setMinimumSize(new Dimension(hidden ? MapSidebarLayout.RAIL_WIDTH : effectiveMinimum, 0));
                splitPane.setDividerLocation(appliedWidth);
                mapsSidebarMenuItem.setSelected(!hidden);
                appliedSidebarWidth = appliedWidth;
                graphScrollPane.setMinimumSize(new Dimension(canvasMinimum, 0));
                sidebar.mapList().syncListWidths();
                sidebarApplyPending = false;
            }
        });
    }

    void setSidebarCollapsed(final boolean collapsed) {
        if (presentationHidden || sidebarOverride != null) {
            return;
        }
        if (sidebarApplying) {
            return;
        }
        sidebarGestureActive = false;
        runWithSidebarGuard(new Runnable() {
            @Override
            public void run() {
                sidebar.setCollapsed(collapsed);
            }
        });
        sessionSidebarHidden = Boolean.valueOf(collapsed);
        if (!readOnly) {
            commitSidebarDisplaySettings(currentPresentation.displaySettings().mapSidebarWidth(), collapsed);
        }
        applySidebarSettings(currentPresentation.displaySettings());
    }

    void commitSidebarWidth(final int location) {
        if (presentationHidden || sidebarOverride != null) {
            return;
        }
        final int splitWidth = splitWidth();
        final int dividerWidth = effectiveDividerWidth();
        final Insets insets = splitInsets();
        if (sidebar.isCollapsed()) {
            return;
        }
        if (MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets) < MapSidebarLayout.MIN_WIDTH) {
            return;
        }
        final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, dividerWidth, insets);
        if (clamped == appliedSidebarWidth) {
            return;
        }
        sessionSidebarWidth = Integer.valueOf(clamped);
        if (!readOnly) {
            commitSidebarDisplaySettings(clamped, currentPresentation.displaySettings().mapSidebarHidden());
        }
        applySidebarSettings(currentPresentation.displaySettings());
    }

    void resetSidebarWidth() {
        if (presentationHidden || sidebarOverride != null) {
            return;
        }
        final int splitWidth = splitWidth();
        final int dividerWidth = effectiveDividerWidth();
        final Insets insets = splitInsets();
        if (sidebar.isCollapsed() || sidebarGestureActive) {
            return;
        }
        if (MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets) < MapSidebarLayout.MIN_WIDTH) {
            return;
        }
        final int clamped = MapSidebarLayout.clampWidth(MapSidebarLayout.DEFAULT_WIDTH, splitWidth, dividerWidth,
            insets);
        if (clamped == appliedSidebarWidth) {
            return;
        }
        sessionSidebarWidth = Integer.valueOf(clamped);
        if (!readOnly) {
            commitSidebarDisplaySettings(clamped, currentPresentation.displaySettings().mapSidebarHidden());
        }
        applySidebarSettings(currentPresentation.displaySettings());
    }

    private void runPendingSidebarApply() {
        if (sidebarApplyPending) {
            applySidebarSettings(currentPresentation.displaySettings());
        }
    }

    void commitSidebarDisplaySettings(final int width, final boolean hidden) {
        final DisplaySettings current = currentPresentation.displaySettings();
        executeCommand(GraphCommands.display(DisplaySettings.of(current.showArrowheads(), current.canvasTheme(),
            current.rememberViewport(), current.dimUnrelatedNodes(), width, hidden, current.unknownXml())));
    }

    private void runWithSidebarGuard(final Runnable action) {
        final boolean previous = sidebarApplying;
        sidebarApplying = true;
        try {
            action.run();
        }
        finally {
            sidebarApplying = previous;
        }
    }

    private int splitWidth() {
        return splitPane.getWidth();
    }

    private int effectiveDividerWidth() {
        final BasicSplitPaneDivider divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
        return divider == null ? 0 : divider.getDividerSize();
    }

    private Insets splitInsets() {
        final Insets insets = splitPane.getInsets();
        return insets == null ? new Insets(0, 0, 0, 0) : insets;
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
        final boolean nodeSelected = selectedNode != null && currentState != null;
        final boolean pinned = nodeSelected && PinProjection.isPinned(currentState.projection(), selectedNode);
        toolbar.setPinState(nodeSelected, pinned);
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

    private void publishExternalViewport() {
        if (suppressViewportEvents || canvas.isProgrammaticViewportChange() || closed) {
            return;
        }
        emitViewportIfChanged(canvas.visibleViewport());
    }

    private void publishCanvasViewport() {
        if (applyingViewport || closed || (!viewportHold.armed && suppressViewportEvents)) {
            return;
        }
        emitViewportIfChanged(canvas.visibleViewport());
        viewportHold.canvasPublication();
    }

    private void publishToolbarViewport(final GraphViewport value) {
        if (applyingViewport || closed) {
            return;
        }
        emitViewportIfChanged(Objects.requireNonNull(value, "viewport"));
        viewportHold.toolbarPublication(value);
    }

    private void emitViewportIfChanged(final GraphViewport value) {
        final GraphViewport next = Objects.requireNonNull(value, "viewport");
        if (lastPublishedViewport != null && lastPublishedViewport.equals(next)) {
            return;
        }
        lastPublishedViewport = next;
        if (!readOnly) {
            executeCommand(GraphCommands.viewport(next.toPersisted()));
            lastPublishedViewport = canvas.visibleViewport();
        }
    }

    private void runWithViewportEventsSuppressed(final Runnable action) {
        viewportSuppressionDepth++;
        updateViewportSuppression();
        try {
            Objects.requireNonNull(action, "action").run();
        }
        finally {
            viewportSuppressionDepth--;
            updateViewportSuppression();
        }
    }

    private void updateViewportSuppression() {
        suppressViewportEvents = baseViewportSuppression || viewportSuppressionDepth > 0 || viewportHold.armed;
    }

    private void runToolbarViewportOperation(final Runnable operation) {
        final boolean previous = toolbarViewportOperation;
        toolbarViewportOperation = true;
        try {
            runWithViewportEventsSuppressed(operation);
        }
        finally {
            toolbarViewportOperation = previous;
        }
    }

    private void applyModelViewport(final Runnable application) {
        final boolean previousApplying = applyingViewport;
        applyingViewport = true;
        try {
            runWithViewportEventsSuppressed(application);
            viewportHold.modelViewportApplied();
        }
        finally {
            applyingViewport = previousApplying;
        }
    }

    private GraphCommandResult executeCommand(final GraphCommand command) {
        return executeCommand(command, false);
    }

    private GraphCommandResult executeCommand(final GraphCommand command, final boolean silent) {
        final GraphCommandResult result = handle.execute(Objects.requireNonNull(command, "command"));
        refreshPresentation();
        applyInitialViewport(currentState);
        if (result != null && result.editorViewActivated()) {
            graphFocus.run();
        }
        if (!silent && result != null && (result.status() == GraphCommandResult.Status.REJECTED
                || result.status() == GraphCommandResult.Status.NO_OP)) {
            commandMessageSink.accept(TextUtils.format(result.messageKey(), result.messageArguments().toArray()));
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
                runWithViewportEventsSuppressed(new Runnable() {
                    @Override
                    public void run() {
                        canvas.setCanvasState(state);
                        applyInitialViewport(state);
                    }
                });
                lastPublishedViewport = canvas.visibleViewport();
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
                viewportHold.windowClosed();
                discard();
                canvasRegistration.close();
                sessionStatusRegistration.close();
                interactionController.uninstall();
            }
        });
    }

    private void toggleSettingsPanel() {
        settingsPanel.setVisible(!settingsPanel.isVisible());
        toolbar.setSettingsVisible(settingsPanel.isVisible());
    }

    private void setReadOnlyOnEdt(final boolean value) {
        readOnly = value;
        toolbar.setReadOnly(value);
        toolbar.settingsButton().setEnabled(true);
        mapList.setReadOnly(value);
        sidebar.setReadOnly(value);
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
        if (!value) {
            sessionSidebarWidth = null;
            sessionSidebarHidden = null;
        }
    }

    private void updateMenuEnablement() {
        if (fileSaveMenuItem == null) {
            return;
        }
        fileSaveMenuItem.setEnabled(toolbar.saveButton().isEnabled());
        fileSaveAsMenuItem.setEnabled(toolbar.saveAsButton().isEnabled());
        toolbar.settingsButton().setEnabled(true);
        viewSettingsMenuItem.setEnabled(true);
        mapsSidebarMenuItem.setEnabled(true);
        final MapListPanel.MapRow selectedMap = mapList.selectedRow();
        mapsAddMenuItem.setEnabled(!readOnly);
        mapsDeactivateMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.ACTIVE
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsReactivateMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.INACTIVE
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsDeleteMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.INACTIVE
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsRetryMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.ACTIVE
            && selectedMap.state() == MapListPanel.RowState.RETRYABLE);
        mapsLocateMenuItem.setEnabled(!readOnly && selectedMap != null
            && (selectedMap.state() == MapListPanel.RowState.MISSING
                || selectedMap.state() == MapListPanel.RowState.RETRYABLE));
    }

    private JPanel createContent() {
        final JPanel graphArea = new JPanel(new BorderLayout(0, 0));
        graphArea.setName("graph-workspace-graph-area");
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, graphScrollPane);
        splitPane.setName("graph-workspace-split");
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(6);
        splitPane.setResizeWeight(0.0);
        installSidebarListeners();
        graphArea.add(splitPane, BorderLayout.CENTER);
        graphArea.add(settingsPanel, BorderLayout.EAST);

        final JPanel result = new JPanel(new BorderLayout(0, 0));
        result.setName("graph-workspace-content");
        result.add(toolbar, BorderLayout.NORTH);
        result.add(graphArea, BorderLayout.CENTER);
        result.add(statusSlot, BorderLayout.SOUTH);
        return result;
    }

    private void installSidebarListeners() {
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent event) {
                applySidebarSettings(currentPresentation.displaySettings());
            }
        });
        final Component divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
        if (divider != null) {
            divider.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    sidebarOverride = null;
                    if (event.getClickCount() == 2) {
                        sidebarGestureActive = false;
                        resetSidebarWidth();
                        return;
                    }
                    sidebarGestureActive = true;
                }

                @Override
                public void mouseReleased(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1 || !sidebarGestureActive) {
                        return;
                    }
                    sidebarGestureActive = false;
                    commitSidebarWidth(splitPane.getDividerLocation());
                    runPendingSidebarApply();
                }
            });
        }
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (sidebarApplying || presentationHidden || sidebarOverride != null || sidebarKeyboardAction) {
                return;
            }
            final int splitWidth = splitWidth();
            final int dividerWidth = effectiveDividerWidth();
            final Insets insets = splitInsets();
            final int location = splitPane.getDividerLocation();
            if (sidebar.isCollapsed()) {
                if (location != MapSidebarLayout.RAIL_WIDTH) {
                    runWithSidebarGuard(new Runnable() {
                        @Override
                        public void run() {
                            splitPane.setDividerLocation(MapSidebarLayout.RAIL_WIDTH);
                        }
                    });
                }
                return;
            }
            if (sidebarGestureActive) {
                return;
            }
            if (MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets) < MapSidebarLayout.MIN_WIDTH) {
                return;
            }
            final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, dividerWidth, insets);
            if (clamped != location) {
                runWithSidebarGuard(new Runnable() {
                    @Override
                    public void run() {
                        splitPane.setDividerLocation(clamped);
                    }
                });
            }
        });
        splitPane.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                if (presentationHidden || sidebarOverride != null || sidebarKeyboardAction) {
                    return;
                }
                if (sidebarGestureActive) {
                    sidebarGestureActive = false;
                    runPendingSidebarApply();
                    return;
                }
                commitSidebarWidth(splitPane.getDividerLocation());
                runPendingSidebarApply();
            }
        });
        for (final String key : new String[] {"selectMin", "selectMax", "negativeIncrement", "positiveIncrement"}) {
            final Action delegate = splitPane.getActionMap().get(key);
            if (delegate == null) {
                continue;
            }
            splitPane.getActionMap().put(key, new Action() {
                @Override
                public Object getValue(final String name) {
                    return delegate.getValue(name);
                }

                @Override
                public void putValue(final String name, final Object value) {
                    delegate.putValue(name, value);
                }

                @Override
                public boolean isEnabled() {
                    return delegate.isEnabled();
                }

                @Override
                public void setEnabled(final boolean enabled) {
                    delegate.setEnabled(enabled);
                }

                @Override
                public void addPropertyChangeListener(final java.beans.PropertyChangeListener listener) {
                    delegate.addPropertyChangeListener(listener);
                }

                @Override
                public void removePropertyChangeListener(final java.beans.PropertyChangeListener listener) {
                    delegate.removePropertyChangeListener(listener);
                }

                @Override
                public void actionPerformed(final ActionEvent event) {
                    if (presentationHidden || !delegate.isEnabled()) {
                        return;
                    }
                    sidebarOverride = null;
                    sidebarKeyboardAction = true;
                    try {
                        delegate.actionPerformed(event);
                    }
                    finally {
                        sidebarKeyboardAction = false;
                    }
                    commitSidebarWidth(splitPane.getDividerLocation());
                    runPendingSidebarApply();
                }
            });
        }
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
        recentWorkspacesMenu = new JMenu(TextUtils.getText("graph_workspace.menu.recent_workspaces"));
        recentWorkspacesMenu.setName("graph-workspace-recent-workspaces-menu");
        recentWorkspacesMenu.getPopupMenu().addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent event) {
                rebuildRecentWorkspacesMenu();
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent event) {
            }

            @Override
            public void popupMenuCanceled(final PopupMenuEvent event) {
            }
        });
        file.add(recentWorkspacesMenu);
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
        fullScreenMenuItem = new JCheckBoxMenuItem(TextUtils.getText("graph_workspace.action.full_screen"));
        fullScreenMenuItem.setName("graph-workspace-full-screen-menu-item");
        fullScreenMenuItem.setEnabled(false);
        view.add(fullScreenMenuItem);
        mapsSidebarMenuItem = new JCheckBoxMenuItem(TextUtils.getText("graph_workspace.action.maps_sidebar"));
        mapsSidebarMenuItem.setName("graph-workspace-maps-sidebar-menu-item");
        mapsSidebarMenuItem.addActionListener(event -> sidebarToggledFromUser(!sidebar.isCollapsed()));
        view.add(mapsSidebarMenuItem);

        final JMenu maps = menu("graph_workspace.menu.maps", "graph-workspace-maps-menu");
        mapsAddMenuItem = item("graph_workspace.action.add_map", "add-map",
            event -> mapList.addMapFromChooser());
        maps.add(mapsAddMenuItem);
        mapsDeactivateMenuItem = item("graph_workspace.action.deactivate_map", "deactivate-map",
            event -> mapList.deactivateSelected());
        maps.add(mapsDeactivateMenuItem);
        mapsReactivateMenuItem = item("graph_workspace.action.reactivate_map", "reactivate-map",
            event -> mapList.reactivateSelected());
        maps.add(mapsReactivateMenuItem);
        mapsDeleteMenuItem = item("graph_workspace.action.delete_map", "delete-map",
            event -> mapList.deleteSelected());
        maps.add(mapsDeleteMenuItem);
        mapsRetryMenuItem = item("graph_workspace.action.retry_map", "retry-map",
            event -> mapList.retrySelected());
        maps.add(mapsRetryMenuItem);
        mapsLocateMenuItem = item("graph_workspace.action.locate_map", "locate-map",
            event -> mapList.locateSelected());
        maps.add(mapsLocateMenuItem);

        result.add(file);
        result.add(edit);
        result.add(view);
        result.add(maps);
        return result;
    }

    void rebuildRecentWorkspacesMenu() {
        recentWorkspacesMenu.removeAll();
        final List<RecentWorkspaceList.Entry> entries = recentWorkspaces.displayEntries();
        if (entries.isEmpty()) {
            final JMenuItem empty = new JMenuItem(TextUtils.getText("graph_workspace.recent_workspaces.empty"));
            empty.setName("graph-workspace-recent-workspaces-empty");
            empty.setEnabled(false);
            recentWorkspacesMenu.add(empty);
            if (recentWorkspaces.hasStoredEntries()) {
                recentWorkspacesMenu.addSeparator();
                recentWorkspacesMenu.add(clearRecentWorkspacesItem());
            }
            recentWorkspacesMenu.setEnabled(true);
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            final RecentWorkspaceList.Entry entry = entries.get(index);
            final JMenuItem item = new JMenuItem(entry.label());
            item.setName("graph-workspace-recent-workspace-" + index);
            item.addActionListener(event -> openRecentWorkspace(entry.path()));
            recentWorkspacesMenu.add(item);
        }
        recentWorkspacesMenu.addSeparator();
        recentWorkspacesMenu.add(clearRecentWorkspacesItem());
        recentWorkspacesMenu.setEnabled(true);
    }

    private JMenuItem clearRecentWorkspacesItem() {
        final JMenuItem item = new JMenuItem(TextUtils.getText("graph_workspace.action.clear_recent_workspaces"));
        item.setName("graph-workspace-recent-workspaces-clear");
        item.addActionListener(event -> {
            recentWorkspaces.clear();
            rebuildRecentWorkspacesMenu();
        });
        return item;
    }

    private void openRecentWorkspace(final Path path) {
        try {
            applicationController.openExisting(path);
        }
        catch (RuntimeException failure) {
            commandMessageSink.accept(TextUtils.format("graph_workspace.recent_workspaces.open_failed", path));
            rebuildRecentWorkspacesMenu();
        }
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
        }
        final List<MapListPanel.MapRow> rows = new ArrayList<MapListPanel.MapRow>(accumulators.size());
        for (final RowAccumulator accumulator : accumulators.values()) {
            final boolean isSelected = selectedNode != null && selectedNode.mapReferenceId().equals(accumulator.mapReferenceId);
            final MapPartition partition = accumulator.availability == MapAvailability.INACTIVE
                ? MapPartition.INACTIVE : MapPartition.ACTIVE;
            final MapListPanel.RowState rowState = readOnly ? MapListPanel.RowState.READ_ONLY
                : rowStateFor(accumulator.availability);
            rows.add(MapListPanel.MapRow.of(accumulator.mapReferenceId, accumulator.displayName,
                rowState, partition, accumulator.projectedNodeCount, isSelected));
        }
        int activeCount = 0;
        for (final MapListPanel.MapRow row : rows) {
            if (row.partition() == MapPartition.ACTIVE) {
                activeCount++;
            }
        }
        mapList.setRows(rows);
        sidebar.setActiveMapCount(activeCount);
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
            if (selectedNode != null) {
                mapList.selectMap(selectedNode.mapReferenceId());
            }
            else {
                mapList.clearSelection();
            }
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
            if (!connect.source().isNode() || !connect.target().isNode()) {
                return;
            }
            executeCommand(GraphCommands.connect(connect.source().node().get().source(),
                connect.target().node().get().source(), connect.direction()));
        }
        else if (intent instanceof GraphIntent.RevealSourceNode) {
            final ProjectedEndpointKey endpoint = ((GraphIntent.RevealSourceNode) intent).endpoint();
            executeCommand(GraphCommands.openSource(source(endpoint)), true);
        }
        else if (intent instanceof GraphIntent.OpenSourceNode) {
            final ProjectedEndpointKey endpoint = ((GraphIntent.OpenSourceNode) intent).endpoint();
            executeCommand(GraphCommands.openSource(source(endpoint)));
        }
    }

    private void togglePinSelectedNode() {
        if (selectedNode == null || currentState == null) {
            return;
        }
        if (PinProjection.isPinned(currentState.projection(), selectedNode)) {
            executeUnpin(selectedNode);
            return;
        }
        final NodeGeometry geometry = currentState.geometry().nodes().get(selectedNode);
        if (geometry != null) {
            executePin(selectedNode, geometry.center().x(), geometry.center().y());
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
        final Dimension overlapSize = graphScrollPane.getViewport().getExtentSize();
        final Dimension effectiveOverlapSize = overlapSize.width > 0 && overlapSize.height > 0
            ? overlapSize : CANVAS_PREFERRED_SIZE;
        applyModelViewport(new Runnable() {
            @Override
            public void run() {
                canvas.setViewport(initialViewport);
                if (!currentPresentation.displaySettings().rememberViewport()
                        || !initialViewport.overlaps(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY,
                            effectiveOverlapSize)) {
                    canvas.fitGraph();
                }
            }
        });
        lastPublishedViewport = canvas.visibleViewport();
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
            final Supplier<java.nio.file.Path> pathChooser, final RecentWorkspaceList recentWorkspaces) {
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
            }, message -> { }, recentWorkspaces);
        model.completeInitialLayout();
    }

    HeadlessGraphWorkspaceView(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        this(handle, binding, closeController, applicationController, pathChooser, RecentWorkspaceList.empty());
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
