package org.freeplane.plugin.graph.canvas;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.Viewport;

public final class GraphCanvas extends JComponent implements Accessible {
    private static final long serialVersionUID = 1L;
    private static final int MIN_SURFACE_WIDTH = 800;
    private static final int MIN_SURFACE_HEIGHT = 560;
    private static final double WORLD_MARGIN = 80.0;

    private final AdaptiveRenderingPolicy renderingPolicy = new AdaptiveRenderingPolicy();
    private final GraphPainter painter = new GraphPainter();
    private volatile CanvasState canvasState;
    private volatile GraphPaintState paintState;
    private volatile GraphViewport viewport;
    private volatile GraphTheme theme;
    private volatile GraphInteractionController interactionController;
    private volatile boolean showArrowheads;
    private volatile boolean dimUnrelated;
    private volatile int viewportPositioningDepth;
    private volatile int pendingViewportClamps;

    public GraphCanvas() {
        paintState = GraphPaintState.empty();
        viewport = GraphViewport.of(0.0, 0.0, 1.0);
        theme = GraphTheme.resolve(CanvasTheme.FOLLOW_FREEPLANE);
        showArrowheads = true;
        dimUnrelated = true;
        setOpaque(true);
        setFocusable(true);
        setDoubleBuffered(true);
    }

    public void setCanvasState(final CanvasState state) {
        final CanvasState value = Objects.requireNonNull(state, "state");
        onEdt(new Runnable() {
            @Override
            public void run() {
                canvasState = value;
                final GraphInteractionController controller = interactionController;
                if (controller != null) {
                    controller.canvasStateChanged(value);
                }
                resizeSurface(value);
                repaint();
            }
        });
    }

    public void setPaintState(final GraphPaintState state) {
        final GraphPaintState value = Objects.requireNonNull(state, "state");
        onEdt(new Runnable() {
            @Override
            public void run() {
                paintState = value;
                repaint();
            }
        });
    }

    public void setViewport(final GraphViewport value) {
        final GraphViewport next = Objects.requireNonNull(value, "viewport");
        onEdt(new Runnable() {
            @Override
            public void run() {
                viewport = next;
                final CanvasState state = canvasState;
                if (state != null) {
                    resizeSurface(state);
                }
                centerScrollView();
                repaint();
            }
        });
    }

    public GraphViewport viewport() {
        return viewport;
    }

    public GraphViewport visibleViewport() {
        final JViewport scrollViewport = containingViewport();
        final GraphViewport anchor = viewport;
        if (scrollViewport == null) {
            return anchor;
        }
        final Point viewPosition = scrollViewport.getViewPosition();
        final Dimension extent = scrollViewport.getExtentSize();
        final double visibleCenterX = viewPosition.getX() + extent.getWidth() * 0.5;
        final double visibleCenterY = viewPosition.getY() + extent.getHeight() * 0.5;
        final double centerX = anchor.centerX()
            + (visibleCenterX - getWidth() * 0.5) / anchor.zoom();
        final double centerY = anchor.centerY()
            + (visibleCenterY - getHeight() * 0.5) / anchor.zoom();
        return GraphViewport.from(Viewport.of(centerX, centerY, anchor.zoom(),
            anchor.toPersisted().unknownXml()));
    }

    public boolean isProgrammaticViewportChange() {
        return viewportPositioningDepth > 0 || pendingViewportClamps > 0;
    }

    @Override
    public synchronized AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleGraphCanvas(this);
        }
        return accessibleContext;
    }

    boolean activateAccessible(final org.freeplane.plugin.graph.projection.ProjectedEndpointKey endpoint,
            final boolean open) {
        final GraphInteractionController controller = interactionController;
        return controller != null && controller.activateAccessible(endpoint, open);
    }

    public void fitGraph() {
        onEdt(new Runnable() {
            @Override
            public void run() {
                final CanvasState state = canvasState;
                final Dimension size = fitSize();
                if (state == null || size.width <= 0 || size.height <= 0) {
                    return;
                }
                final Bounds bounds = visibleBounds(state);
                if (bounds == null) {
                    return;
                }
                final double spanX = bounds.spanX();
                final double spanY = bounds.spanY();
                final double fitWidth = Math.max(spanX, Double.MIN_VALUE);
                final double fitHeight = Math.max(spanY, Double.MIN_VALUE);
                final double horizontalZoom = size.getWidth() * 0.8 / fitWidth;
                final double verticalZoom = size.getHeight() * 0.8 / fitHeight;
                double zoom = Math.min(horizontalZoom, verticalZoom);
                if (!Double.isFinite(zoom) || !(zoom > 0.0)) {
                    zoom = viewport.zoom();
                }
                if (!Double.isFinite(zoom) || !(zoom > 0.0)) {
                    zoom = 1.0;
                }
                final double centerX = bounds.centerX();
                final double centerY = bounds.centerY();
                if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
                    return;
                }
                viewport = GraphViewport.of(centerX, centerY, zoom);
                resizeSurface(state);
                centerScrollView();
                repaint();
            }
        });
    }

    public void resetZoom() {
        onEdt(new Runnable() {
            @Override
            public void run() {
                viewport = GraphViewport.of(0.0, 0.0, 1.0);
                final CanvasState state = canvasState;
                if (state != null) {
                    resizeSurface(state);
                }
                centerScrollView();
                repaint();
            }
        });
    }

    void setInteractionController(final GraphInteractionController controller) {
        interactionController = controller;
    }

    CanvasState canvasState() {
        return canvasState;
    }

    GraphPaintState paintState() {
        return paintState;
    }

    GraphHitIndex hitIndex() {
        final CanvasState state = canvasState;
        return state == null ? GraphHitIndex.empty() : GraphHitIndex.from(state);
    }

    LayoutPoint worldAt(final Point point) {
        return viewport.toWorld(Objects.requireNonNull(point, "point"),
            new Dimension(getWidth(), getHeight()));
    }

    void updateTooltip(final org.freeplane.plugin.graph.projection.ProjectedEndpointKey endpoint) {
        final CanvasState state = canvasState;
        final String text = state == null || endpoint == null
            ? null : GraphSearchModel.tooltip(state, endpoint);
        onEdt(new Runnable() {
            @Override
            public void run() {
                setToolTipText(text);
            }
        });
    }

    void resetInteractionCursor() {
        onEdt(new Runnable() {
            @Override
            public void run() {
                setCursor(java.awt.Cursor.getDefaultCursor());
            }
        });
    }

    void repaintCanvas() {
        repaint();
    }

    public void setTheme(final GraphTheme value) {
        final GraphTheme next = Objects.requireNonNull(value, "theme");
        onEdt(new Runnable() {
            @Override
            public void run() {
                theme = next;
                repaint();
            }
        });
    }

    public void setShowArrowheads(final boolean value) {
        onEdt(new Runnable() {
            @Override
            public void run() {
                showArrowheads = value;
                repaint();
            }
        });
    }

    public void setDimUnrelated(final boolean value) {
        onEdt(new Runnable() {
            @Override
            public void run() {
                dimUnrelated = value;
                repaint();
            }
        });
    }

    GraphTheme theme() {
        return theme;
    }

    boolean showArrowheads() {
        return showArrowheads;
    }

    boolean dimUnrelated() {
        return dimUnrelated;
    }

    void panByPixels(final double dx, final double dy) {
        onEdt(new Runnable() {
            @Override
            public void run() {
                viewport = viewport.panPixels(dx, dy);
                repaint();
            }
        });
    }

    void zoomAround(final Point2D pointer, final double factor) {
        final Point2D value = Objects.requireNonNull(pointer, "pointer");
        onEdt(new Runnable() {
            @Override
            public void run() {
                viewport = viewport.zoomAround(value, factor, new Dimension(getWidth(), getHeight()));
                final CanvasState state = canvasState;
                if (state != null) {
                    resizeSurface(state);
                }
                repaint();
            }
        });
    }

    void zoomAround(final double x, final double y, final double factor) {
        zoomAround(new Point2D.Double(x, y), factor);
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Dimension size = new Dimension(getWidth(), getHeight());
        final CanvasState state = canvasState;
        final int nodeCount = state == null ? 0 : state.projection().nodes().size();
        final int edgeCount = state == null ? 0 : state.projection().edges().size();
        painter.paint((Graphics2D) graphics, state, paintState, viewport, size, theme,
            renderingPolicy.forCounts(nodeCount, edgeCount), showArrowheads, dimUnrelated);
    }

    private Bounds visibleBounds(final CanvasState state) {
        final GraphGeometry geometry = state.geometry();
        final Bounds bounds = new Bounds();
        for (final ProjectedNode node : state.projection().nodes()) {
            final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
            if (nodeGeometry != null) {
                bounds.include(nodeGeometry.minX(), nodeGeometry.minY(), nodeGeometry.maxX(),
                    nodeGeometry.maxY());
            }
        }
        final Set<EnclosureHullKey> includedHulls = new HashSet<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED
                    || !includedHulls.add(enclosure.hullKey())) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull != null) {
                bounds.include(hull.minX(), hull.minY(), hull.maxX(), hull.maxY());
            }
        }
        return bounds.isEmpty() ? null : bounds;
    }

    private void resizeSurface(final CanvasState state) {
        final Bounds bounds = visibleBounds(state);
        if (bounds == null || !Double.isFinite(viewport.zoom()) || !(viewport.zoom() > 0.0)) {
            return;
        }
        final JViewport scrollViewport = containingViewport();
        final Dimension available = scrollViewport == null ? null : scrollViewport.getExtentSize();
        final Dimension oldPreferred = getPreferredSize();
        final Dimension oldSize = getSize();
        final int oldWidth = oldSize.width > 0 ? oldSize.width : oldPreferred.width;
        final int oldHeight = oldSize.height > 0 ? oldSize.height : oldPreferred.height;
        final int width = surfacePixels(bounds.minX, bounds.maxX, viewport.centerX(), viewport.zoom(),
            MIN_SURFACE_WIDTH, available == null ? 0 : available.width);
        final int height = surfacePixels(bounds.minY, bounds.maxY, viewport.centerY(), viewport.zoom(),
            MIN_SURFACE_HEIGHT, available == null ? 0 : available.height);
        if (oldPreferred.width == width && oldPreferred.height == height
                && (scrollViewport == null || oldSize.width == width && oldSize.height == height)) {
            return;
        }
        final Point oldPosition = scrollViewport == null ? null : scrollViewport.getViewPosition();
        final Dimension nextSize = new Dimension(width, height);
        beginViewportPositioning();
        try {
            setPreferredSize(nextSize);
            if (scrollViewport != null) {
                setSize(nextSize);
                final int nextX = oldPosition == null ? 0
                    : oldPosition.x + (width - oldWidth) / 2;
                final int nextY = oldPosition == null ? 0
                    : oldPosition.y + (height - oldHeight) / 2;
                scrollViewport.setViewPosition(clampedPosition(scrollViewport, nextX, nextY));
            }
            revalidate();
            if (scrollViewport != null) {
                scheduleViewportClamp(scrollViewport);
            }
        }
        finally {
            endViewportPositioning();
        }
    }

    private static int surfacePixels(final double minWorld, final double maxWorld,
            final double anchor, final double zoom, final int minimum, final int available) {
        final double leftWorld = Math.max(0.0, anchor - minWorld + WORLD_MARGIN);
        final double rightWorld = Math.max(0.0, maxWorld - anchor + WORLD_MARGIN);
        final double requested = 2.0 * Math.max(leftWorld, rightWorld) * zoom;
        final double finiteRequested = Double.isFinite(requested) && requested > 0.0
            ? requested : minimum;
        final double bounded = Math.min(finiteRequested, Integer.MAX_VALUE - 1.0);
        return Math.max(minimum, Math.max(available, (int) Math.ceil(bounded)));
    }

    private Dimension fitSize() {
        final JViewport scrollViewport = containingViewport();
        if (scrollViewport != null) {
            final Dimension extent = scrollViewport.getExtentSize();
            if (extent.width > 0 && extent.height > 0) {
                return extent;
            }
        }
        final Dimension size = getSize();
        if (size.width > 0 && size.height > 0) {
            return size;
        }
        return getPreferredSize();
    }

    private void centerScrollView() {
        final JViewport scrollViewport = containingViewport();
        if (scrollViewport == null) {
            return;
        }
        final Dimension extent = scrollViewport.getExtentSize();
        final int centerX = (getWidth() - extent.width) / 2;
        final int centerY = (getHeight() - extent.height) / 2;
        setViewPositionProgrammatically(scrollViewport, centerX, centerY);
    }

    private JViewport containingViewport() {
        return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
    }

    private void setViewPositionProgrammatically(final JViewport scrollViewport, final int x,
            final int y) {
        beginViewportPositioning();
        try {
            scrollViewport.setViewPosition(clampedPosition(scrollViewport, x, y));
        }
        finally {
            endViewportPositioning();
        }
    }

    private void scheduleViewportClamp(final JViewport scrollViewport) {
        pendingViewportClamps++;
        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    beginViewportPositioning();
                    try {
                        if (containingViewport() == scrollViewport) {
                            final Point position = scrollViewport.getViewPosition();
                            scrollViewport.setViewPosition(clampedPosition(scrollViewport,
                                position.x, position.y));
                        }
                    }
                    finally {
                        endViewportPositioning();
                        pendingViewportClamps--;
                    }
                }
            });
        }
        catch (final RuntimeException exception) {
            pendingViewportClamps--;
            throw exception;
        }
    }

    private void beginViewportPositioning() {
        viewportPositioningDepth++;
    }

    private void endViewportPositioning() {
        viewportPositioningDepth--;
    }

    private static Point clampedPosition(final JViewport scrollViewport, final int x, final int y) {
        final java.awt.Component view = scrollViewport.getView();
        final Dimension viewSize = view == null ? new Dimension() : view.getSize();
        final Dimension extent = scrollViewport.getExtentSize();
        final int maxX = Math.max(0, viewSize.width - extent.width);
        final int maxY = Math.max(0, viewSize.height - extent.height);
        return new Point(clamp(x, 0, maxX), clamp(y, 0, maxY));
    }

    private static int clamp(final int value, final int minimum, final int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static void onEdt(final Runnable action) {
        Objects.requireNonNull(action, "action");
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        }
        catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating GraphCanvas", exception);
        }
        catch (final InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("GraphCanvas update failed", cause);
        }
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        private void include(final double firstX, final double firstY, final double secondX,
                final double secondY) {
            if (!Double.isFinite(firstX) || !Double.isFinite(firstY)
                    || !Double.isFinite(secondX) || !Double.isFinite(secondY)
                    || firstX > secondX || firstY > secondY) {
                return;
            }
            minX = Math.min(minX, firstX);
            minY = Math.min(minY, firstY);
            maxX = Math.max(maxX, secondX);
            maxY = Math.max(maxY, secondY);
        }

        private boolean isEmpty() {
            return minX == Double.POSITIVE_INFINITY;
        }

        private double spanX() {
            return maxX - minX;
        }

        private double spanY() {
            return maxY - minY;
        }

        private double centerX() {
            return minX + (maxX - minX) * 0.5;
        }

        private double centerY() {
            return minY + (maxY - minY) * 0.5;
        }
    }
}
