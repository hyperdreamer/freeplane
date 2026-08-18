package org.freeplane.plugin.graph.canvas;

import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;

public final class GraphInteractionController {
    private static final double DRAG_THRESHOLD_PIXELS = 3.0;
    private static final double EDGE_TOLERANCE_PIXELS = 8.0;
    private static final double NORMAL_ARROW_PIXELS = 20.0;
    private static final double ACCELERATED_ARROW_PIXELS = 80.0;
    private static final double MIN_WHEEL_FACTOR = 0.5;
    private static final double MAX_WHEEL_FACTOR = 2.0;
    private static final double WHEEL_BASE = 1.1;

    private final GraphInteractionListener listener;
    private final MouseAdapter mouseListener;
    private final MouseMotionAdapter mouseMotionListener;
    private final MouseWheelListener mouseWheelListener;
    private final KeyAdapter keyListener;
    private Set<AWTKeyStroke> previousForwardTraversalKeys;
    private Set<AWTKeyStroke> previousBackwardTraversalKeys;
    private boolean previousFocusTraversalKeysEnabled;
    private InteractionTool tool = InteractionTool.SELECT;
    private RelationshipDirection relationshipDirection = RelationshipDirection.FORWARD;
    private GraphCanvas canvas;
    private DragState drag;
    private ProjectedEndpointKey previewSource;
    private LayoutPoint previewFrom;
    private boolean contextHandled;
    private boolean contextGestureDispatched;

    public GraphInteractionController(final GraphInteractionListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent event) {
                handleMousePressed(event);
            }

            @Override
            public void mouseReleased(final MouseEvent event) {
                handleMouseReleased(event);
            }

            @Override
            public void mouseClicked(final MouseEvent event) {
                handleMouseClicked(event);
            }

            @Override
            public void mouseExited(final MouseEvent event) {
                handleMouseExited(event);
            }
        };
        mouseMotionListener = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(final MouseEvent event) {
                handleMouseDragged(event);
            }

            @Override
            public void mouseMoved(final MouseEvent event) {
                handleMouseMoved(event);
            }
        };
        mouseWheelListener = new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(final MouseWheelEvent event) {
                handleMouseWheel(event);
            }
        };
        keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent event) {
                handleKeyPressed(event);
            }
        };
    }

    public void install(final GraphCanvas value) {
        final GraphCanvas next = Objects.requireNonNull(value, "canvas");
        if (canvas != null) {
            throw new IllegalStateException("GraphInteractionController is already installed");
        }
        previousForwardTraversalKeys = next.getFocusTraversalKeys(
            KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
        previousBackwardTraversalKeys = next.getFocusTraversalKeys(
            KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
        previousFocusTraversalKeysEnabled = next.getFocusTraversalKeysEnabled();
        next.setFocusTraversalKeysEnabled(false);
        canvas = next;
        next.setInteractionController(this);
        next.addMouseListener(mouseListener);
        next.addMouseMotionListener(mouseMotionListener);
        next.addMouseWheelListener(mouseWheelListener);
        next.addKeyListener(keyListener);
        next.setFocusable(true);
    }

    public void uninstall() {
        final GraphCanvas oldCanvas = canvas;
        if (oldCanvas == null) {
            return;
        }
        oldCanvas.removeMouseListener(mouseListener);
        oldCanvas.removeMouseMotionListener(mouseMotionListener);
        oldCanvas.removeMouseWheelListener(mouseWheelListener);
        oldCanvas.removeKeyListener(keyListener);
        oldCanvas.setInteractionController(null);
        drag = null;
        previewSource = null;
        previewFrom = null;
        contextHandled = false;
        contextGestureDispatched = false;
        final GraphPaintState state = oldCanvas.paintState();
        oldCanvas.setPaintState(state.withoutConnectionPreview().withoutHover()
            .withDimUnrelated(false));
        oldCanvas.updateTooltip(null);
        oldCanvas.resetInteractionCursor();
        oldCanvas.repaintCanvas();
        oldCanvas.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
            previousForwardTraversalKeys);
        oldCanvas.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
            previousBackwardTraversalKeys);
        oldCanvas.setFocusTraversalKeysEnabled(previousFocusTraversalKeysEnabled);
        previousForwardTraversalKeys = null;
        previousBackwardTraversalKeys = null;
        canvas = null;
    }

    public void setTool(final InteractionTool value) {
        final InteractionTool next = Objects.requireNonNull(value, "tool");
        tool = next;
        if (next != InteractionTool.CONNECT) {
            cancelPreview();
        }
    }

    public void setRelationshipDirection(final RelationshipDirection value) {
        relationshipDirection = Objects.requireNonNull(value, "direction");
    }

    void unpinAll() {
        emit(new GraphIntent.UnpinAll());
    }

    void deleteContributor(final ContributorKey contributor) {
        emit(new GraphIntent.DeleteContributor(contributor));
    }

    void deleteAllContributors(final ProjectedEdgeKey edge, final List<ContributorKey> contributors) {
        emit(new GraphIntent.DeleteAllContributors(edge, contributors));
    }

    boolean activateAccessible(final ProjectedEndpointKey endpoint, final boolean open) {
        final ProjectedEndpointKey value = Objects.requireNonNull(endpoint, "endpoint");
        final CanvasState state = canvas == null ? null : canvas.canvasState();
        if (state == null || !GraphTraversalOrder.tabOrder(state).contains(value)) {
            return false;
        }
        setSelectionVisual(value);
        emit(new GraphIntent.ChangeSelection(Optional.of(value)));
        if (open) {
            emit(new GraphIntent.OpenSourceNode(value));
        }
        return true;
    }

    private void handleMousePressed(final MouseEvent event) {
        if (!isInstalled()) {
            return;
        }
        if (isContextEvent(event)) {
            contextGestureDispatched = true;
            contextHandled = true;
            handleContext(event);
            return;
        }
        contextGestureDispatched = false;
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        contextHandled = false;
        canvas.requestFocusInWindow();
        final CanvasState state = canvas.canvasState();
        if (state == null) {
            return;
        }
        final LayoutPoint world = canvas.worldAt(event.getPoint());
        final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex().endpointAt(world);
        if (tool == InteractionTool.CONNECT) {
            beginConnect(state, endpoint, world, event);
        }
        else {
            beginSelect(state, endpoint, world, event);
        }
    }

    private void handleMouseReleased(final MouseEvent event) {
        if (!isInstalled()) {
            return;
        }
        if (isContextEvent(event)) {
            if (!contextHandled) {
                contextGestureDispatched = true;
                handleContext(event);
            }
            contextHandled = false;
            return;
        }
        final DragState current = drag;
        if (current == null) {
            return;
        }
        if (current.connect) {
            finishConnect(event);
        }
        else {
            finishSelect(event, current);
        }
        drag = null;
    }

    private void handleMouseClicked(final MouseEvent event) {
        if (!isInstalled()) {
            return;
        }
        if (isContextEvent(event)) {
            if (!contextGestureDispatched) {
                handleContext(event);
            }
            contextGestureDispatched = false;
            contextHandled = false;
            return;
        }
        contextGestureDispatched = false;
        if (tool != InteractionTool.SELECT || !SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        final CanvasState state = canvas.canvasState();
        if (state == null) {
            return;
        }
        final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex()
            .endpointAt(canvas.worldAt(event.getPoint()));
        if (event.getClickCount() >= 2) {
            if (endpoint.isPresent()) {
                setSelectionVisual(endpoint.get());
                emit(new GraphIntent.OpenSourceNode(endpoint.get()));
            }
            return;
        }
        setSelectionVisual(endpoint.orElse(null));
        emit(new GraphIntent.ChangeSelection(endpoint));
    }

    private void handleMouseDragged(final MouseEvent event) {
        if (!isInstalled() || drag == null) {
            return;
        }
        final DragState current = drag;
        final double dx = event.getX() - current.lastX;
        final double dy = event.getY() - current.lastY;
        if (Math.hypot(event.getX() - current.startX, event.getY() - current.startY)
                >= DRAG_THRESHOLD_PIXELS) {
            current.moved = true;
        }
        current.lastX = event.getX();
        current.lastY = event.getY();
        final LayoutPoint world = canvas.worldAt(event.getPoint());
        if (current.connect) {
            if (previewSource != null) {
                updatePreview(world);
            }
            return;
        }
        if (current.endpoint == null && current.moved) {
            canvas.panByPixels(dx, dy);
        }
    }

    private void handleMouseMoved(final MouseEvent event) {
        if (!isInstalled()) {
            return;
        }
        final CanvasState state = canvas.canvasState();
        if (state == null) {
            return;
        }
        final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex()
            .endpointAt(canvas.worldAt(event.getPoint()));
        final GraphPaintState current = canvas.paintState();
        if (endpoint.isPresent()) {
            canvas.setPaintState(current.withHover(endpoint.get()).withDimUnrelated(true));
            canvas.updateTooltip(endpoint.get());
        }
        else {
            canvas.setPaintState(current.withoutHover().withDimUnrelated(false));
            canvas.updateTooltip(null);
        }
    }

    private void handleMouseExited(final MouseEvent event) {
        if (!isInstalled()) {
            return;
        }
        final GraphPaintState current = canvas.paintState();
        canvas.setPaintState(current.withoutHover().withDimUnrelated(false));
        canvas.updateTooltip(null);
    }

    private void handleMouseWheel(final MouseWheelEvent event) {
        if (!isInstalled()) {
            return;
        }
        final double rotation = event.getPreciseWheelRotation();
        if (!Double.isFinite(rotation) || rotation == 0.0) {
            return;
        }
        double factor = Math.pow(WHEEL_BASE, -rotation);
        if (!Double.isFinite(factor)) {
            factor = rotation < 0.0 ? MAX_WHEEL_FACTOR : MIN_WHEEL_FACTOR;
        }
        factor = Math.max(MIN_WHEEL_FACTOR, Math.min(MAX_WHEEL_FACTOR, factor));
        canvas.zoomAround(event.getPoint(), factor);
        event.consume();
    }

    private void handleKeyPressed(final KeyEvent event) {
        if (!isInstalled()) {
            return;
        }
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (previewSource != null) {
                cancelPreview();
            }
            else {
                setSelectionVisual(null);
                emit(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
            }
            event.consume();
            return;
        }
        final int modifiers = event.getModifiersEx();
        final int navigationModifiers = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK;
        if ((modifiers & navigationModifiers) != 0) {
            return;
        }
        if (event.getKeyCode() == KeyEvent.VK_TAB) {
            final boolean reverse = (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
            if (cycleSelection(reverse)) {
                event.consume();
            }
            else if (previousFocusTraversalKeysEnabled) {
                if (reverse) {
                    canvas.transferFocusBackward();
                }
                else {
                    canvas.transferFocus();
                }
            }
            return;
        }
        if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            final Optional<ProjectedEndpointKey> selection = canvas.paintState().selection();
            final CanvasState state = canvas.canvasState();
            if (selection.isPresent() && state != null
                    && GraphTraversalOrder.tabOrder(state).contains(selection.get())) {
                setSelectionVisual(selection.get());
                emit(new GraphIntent.OpenSourceNode(selection.get()));
                event.consume();
            }
            return;
        }
        if (!isArrow(event.getKeyCode())) {
            return;
        }
        final boolean shift = (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
        if (!shift && canvas.paintState().selection().isPresent()) {
            final CanvasState state = canvas.canvasState();
            if (state != null) {
                final Optional<ProjectedEndpointKey> next = GraphTraversalOrder.nearest(
                    state, canvas.paintState().selection().get(), directionFor(event.getKeyCode()));
                if (next.isPresent()) {
                    selectEndpoint(next.get());
                }
            }
            event.consume();
            return;
        }
        final double pixels = shift ? ACCELERATED_ARROW_PIXELS : NORMAL_ARROW_PIXELS;
        panForArrow(event.getKeyCode(), pixels);
        event.consume();
    }

    private void beginSelect(final CanvasState state, final Optional<ProjectedEndpointKey> endpoint,
            final LayoutPoint world, final MouseEvent event) {
        final ProjectedEndpointKey value = endpoint.orElse(null);
        final boolean pinCandidate = value != null && value.isNode()
            && !isPinned(state, value.node().get());
        drag = DragState.select(value, pinCandidate, event.getX(), event.getY());
    }

    private void beginConnect(final CanvasState state,
            final Optional<ProjectedEndpointKey> endpoint, final LayoutPoint world,
            final MouseEvent event) {
        if (!endpoint.isPresent()) {
            drag = null;
            return;
        }
        previewSource = endpoint.get();
        previewFrom = previewStart(state, previewSource, world);
        drag = DragState.connect(previewSource, event.getX(), event.getY());
        updatePreview(world);
    }

    private void finishSelect(final MouseEvent event, final DragState current) {
        if (!current.pinCandidate) {
            return;
        }
        final boolean moved = current.moved
            || Math.hypot(event.getX() - current.startX, event.getY() - current.startY)
                >= DRAG_THRESHOLD_PIXELS;
        if (!moved || !current.endpoint.isNode()) {
            return;
        }
        final LayoutPoint world = canvas.worldAt(event.getPoint());
        emit(new GraphIntent.Pin(current.endpoint.node().get(), world.x(), world.y()));
    }

    private void finishConnect(final MouseEvent event) {
        final ProjectedEndpointKey source = previewSource;
        final LayoutPoint world = canvas.worldAt(event.getPoint());
        final Optional<ProjectedEndpointKey> target = canvas.hitIndex().endpointAt(world);
        cancelPreview();
        if (source == null || !target.isPresent() || source.equals(target.get())) {
            return;
        }
        emit(new GraphIntent.Connect(source, target.get(), relationshipDirection));
    }

    private void handleContext(final MouseEvent event) {
        final CanvasState state = canvas.canvasState();
        if (state == null) {
            return;
        }
        final LayoutPoint world = canvas.worldAt(event.getPoint());
        final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex().endpointAt(world);
        if (endpoint.isPresent() && endpoint.get().isNode()
                && isPinned(state, endpoint.get().node().get())) {
            emit(new GraphIntent.Unpin(endpoint.get().node().get()));
            return;
        }
        final double zoom = canvas.viewport().zoom();
        final double tolerance = Double.isFinite(zoom) && zoom > 0.0
            ? EDGE_TOLERANCE_PIXELS / zoom : EDGE_TOLERANCE_PIXELS;
        final Optional<ProjectedEdgeKey> edge = canvas.hitIndex().edgeAt(world, tolerance);
        if (edge.isPresent()) {
            emit(new GraphIntent.InspectEdge(edge.get()));
        }
    }

    private void setSelectionVisual(final ProjectedEndpointKey endpoint) {
        final GraphPaintState current = canvas.paintState().withoutSelection();
        canvas.setPaintState(endpoint == null ? current : current.withSelection(endpoint));
    }

    private void updatePreview(final LayoutPoint world) {
        if (previewSource == null || previewFrom == null) {
            return;
        }
        canvas.setPaintState(canvas.paintState().withConnectionPreview(
            GraphPaintState.ConnectionPreview.of(previewFrom, world)));
        canvas.repaintCanvas();
    }

    private void cancelPreview() {
        final GraphCanvas currentCanvas = canvas;
        previewSource = null;
        previewFrom = null;
        if (currentCanvas != null) {
            currentCanvas.setPaintState(currentCanvas.paintState().withoutConnectionPreview());
            currentCanvas.repaintCanvas();
        }
    }

    private LayoutPoint previewStart(final CanvasState state, final ProjectedEndpointKey endpoint,
            final LayoutPoint toward) {
        final LayoutPoint anchor = endpointAnchor(state, endpoint);
        if (anchor == null) {
            return toward;
        }
        try {
            return state.geometry().edgeAttachment(endpoint, toward);
        }
        catch (final IllegalArgumentException exception) {
            return anchor;
        }
    }

    private static LayoutPoint endpointAnchor(final CanvasState state,
            final ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            final org.freeplane.plugin.graph.geometry.NodeGeometry geometry =
                state.geometry().nodes().get(endpoint.node().get());
            return geometry == null ? null : geometry.center();
        }
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure
                : state.projection().enclosures()) {
            if (!enclosure.endpointKeys().contains(endpoint.enclosure().get())) {
                continue;
            }
            final org.freeplane.plugin.graph.geometry.HullGeometry geometry =
                state.geometry().hulls().get(enclosure.hullKey());
            return geometry == null ? null : geometry.labelAnchor();
        }
        return null;
    }

    private static boolean isPinned(final CanvasState state, final ProjectedNodeKey node) {
        for (final PinProjection pin : state.projection().pins()) {
            if (pin.active() && pin.projectedNode().isPresent()
                    && node.equals(pin.projectedNode().get())) {
                return true;
            }
        }
        return false;
    }

    private void selectEndpoint(final ProjectedEndpointKey endpoint) {
        setSelectionVisual(endpoint);
        emit(new GraphIntent.ChangeSelection(Optional.of(endpoint)));
    }

    private boolean cycleSelection(final boolean reverse) {
        final CanvasState state = canvas.canvasState();
        if (state == null) {
            return false;
        }
        final List<ProjectedEndpointKey> order = GraphTraversalOrder.tabOrder(state);
        if (order.isEmpty()) {
            return false;
        }
        final Optional<ProjectedEndpointKey> selection = canvas.paintState().selection();
        final int current = selection.isPresent() ? order.indexOf(selection.get()) : -1;
        final int next;
        if (current < 0) {
            next = reverse ? order.size() - 1 : 0;
        }
        else if (reverse) {
            next = (current + order.size() - 1) % order.size();
        }
        else {
            next = (current + 1) % order.size();
        }
        selectEndpoint(order.get(next));
        return true;
    }

    private static TraversalDirection directionFor(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                return TraversalDirection.LEFT;
            case KeyEvent.VK_RIGHT:
                return TraversalDirection.RIGHT;
            case KeyEvent.VK_UP:
                return TraversalDirection.UP;
            case KeyEvent.VK_DOWN:
                return TraversalDirection.DOWN;
            default:
                throw new IllegalArgumentException("Not an arrow key");
        }
    }

    private void panForArrow(final int keyCode, final double pixels) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                canvas.panByPixels(pixels, 0.0);
                break;
            case KeyEvent.VK_RIGHT:
                canvas.panByPixels(-pixels, 0.0);
                break;
            case KeyEvent.VK_UP:
                canvas.panByPixels(0.0, pixels);
                break;
            case KeyEvent.VK_DOWN:
                canvas.panByPixels(0.0, -pixels);
                break;
            default:
                break;
        }
    }

    private static boolean isArrow(final int keyCode) {
        return keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT
            || keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;
    }

    private static boolean isContextEvent(final MouseEvent event) {
        return event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event)
            || event.getButton() == MouseEvent.BUTTON3;
    }

    private boolean isInstalled() {
        return canvas != null;
    }

    private void emit(final GraphIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (!isInstalled()) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            listener.onGraphIntent(intent);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    if (isInstalled()) {
                        listener.onGraphIntent(intent);
                    }
                }
            });
        }
        catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while dispatching graph intent", exception);
        }
        catch (final InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Graph intent listener failed", cause);
        }
    }

    private static final class DragState {
        private final ProjectedEndpointKey endpoint;
        private final boolean pinCandidate;
        private final boolean connect;
        private final int startX;
        private final int startY;
        private int lastX;
        private int lastY;
        private boolean moved;

        private DragState(final ProjectedEndpointKey endpoint, final boolean pinCandidate,
                final boolean connect, final int startX, final int startY) {
            this.endpoint = endpoint;
            this.pinCandidate = pinCandidate;
            this.connect = connect;
            this.startX = startX;
            this.startY = startY;
            this.lastX = startX;
            this.lastY = startY;
        }

        private static DragState select(final ProjectedEndpointKey endpoint,
                final boolean pinCandidate, final int x, final int y) {
            return new DragState(endpoint, pinCandidate, false, x, y);
        }

        private static DragState connect(final ProjectedEndpointKey endpoint, final int x,
                final int y) {
            return new DragState(endpoint, false, true, x, y);
        }
    }
}
