package org.freeplane.plugin.graph.canvas;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;

public final class GraphCanvas extends JComponent {
    private static final long serialVersionUID = 1L;

    private final AdaptiveRenderingPolicy renderingPolicy = new AdaptiveRenderingPolicy();
    private final GraphPainter painter = new GraphPainter();
    private volatile CanvasState canvasState;
    private volatile GraphPaintState paintState;
    private volatile GraphViewport viewport;
    private volatile GraphTheme theme;

    public GraphCanvas() {
        paintState = GraphPaintState.empty();
        viewport = GraphViewport.of(0.0, 0.0, 1.0);
        theme = GraphTheme.resolve(CanvasTheme.FOLLOW_FREEPLANE);
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
                repaint();
            }
        });
    }

    public GraphViewport viewport() {
        return viewport;
    }

    public void fitGraph() {
        onEdt(new Runnable() {
            @Override
            public void run() {
                final CanvasState state = canvasState;
                final Dimension size = new Dimension(getWidth(), getHeight());
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
                repaint();
            }
        });
    }

    public void resetZoom() {
        onEdt(new Runnable() {
            @Override
            public void run() {
                viewport = GraphViewport.of(0.0, 0.0, 1.0);
                repaint();
            }
        });
    }

    CanvasState canvasState() {
        return canvasState;
    }

    GraphPaintState paintState() {
        return paintState;
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

    GraphTheme theme() {
        return theme;
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
            renderingPolicy.forCounts(nodeCount, edgeCount));
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
