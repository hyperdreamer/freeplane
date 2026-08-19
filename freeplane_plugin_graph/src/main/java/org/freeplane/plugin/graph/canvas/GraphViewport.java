package org.freeplane.plugin.graph.canvas;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;

public final class GraphViewport {
    private final double centerX;
    private final double centerY;
    private final double zoom;
    private final List<UnknownXml> unknownXml;

    private GraphViewport(final double centerX, final double centerY, final double zoom,
            final List<UnknownXml> unknownXml) {
        requireFinite(centerX, "centerX");
        requireFinite(centerY, "centerY");
        requireFinite(zoom, "zoom");
        if (!(zoom > 0.0)) {
            throw new IllegalArgumentException("zoom must be positive");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.zoom = zoom;
        Objects.requireNonNull(unknownXml, "unknownXml");
        this.unknownXml = Collections.unmodifiableList(new ArrayList<UnknownXml>(unknownXml));
    }

    public static GraphViewport of(final double centerX, final double centerY, final double zoom) {
        return new GraphViewport(centerX, centerY, zoom, Collections.<UnknownXml>emptyList());
    }

    public static GraphViewport from(final Viewport persisted) {
        final Viewport value = Objects.requireNonNull(persisted, "persisted");
        return new GraphViewport(value.centerX(), value.centerY(), value.zoom(), value.unknownXml());
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public double zoom() {
        return zoom;
    }

    public Viewport toPersisted() {
        return Viewport.of(centerX, centerY, zoom, unknownXml);
    }

    public boolean overlaps(final double minX, final double minY, final double maxX, final double maxY,
            final Dimension size) {
        requireFiniteBounds(minX, minY, maxX, maxY);
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("World bounds must be ordered");
        }
        final Dimension value = requireSize(size);
        final double halfWidth = value.getWidth() / (2.0 * zoom);
        final double halfHeight = value.getHeight() / (2.0 * zoom);
        final double visibleMinX = centerX - halfWidth;
        final double visibleMaxX = centerX + halfWidth;
        final double visibleMinY = centerY - halfHeight;
        final double visibleMaxY = centerY + halfHeight;
        return maxX >= visibleMinX && minX <= visibleMaxX
            && maxY >= visibleMinY && minY <= visibleMaxY;
    }

    Point2D.Double toScreen(final LayoutPoint world, final Dimension size) {
        final LayoutPoint value = Objects.requireNonNull(world, "world");
        final Dimension componentSize = requireSize(size);
        return new Point2D.Double(componentSize.getWidth() * 0.5
                + (value.x() - centerX) * zoom,
            componentSize.getHeight() * 0.5 + (value.y() - centerY) * zoom);
    }

    Point2D.Double toScreen(final double worldX, final double worldY, final Dimension size) {
        requireFinite(worldX, "worldX");
        requireFinite(worldY, "worldY");
        return toScreen(LayoutPoint.of(worldX, worldY), size);
    }

    LayoutPoint toWorld(final Point2D screen, final Dimension size) {
        final Point2D value = Objects.requireNonNull(screen, "screen");
        requireFinite(value.getX(), "screenX");
        requireFinite(value.getY(), "screenY");
        final Dimension componentSize = requireSize(size);
        return LayoutPoint.of(centerX + (value.getX() - componentSize.getWidth() * 0.5) / zoom,
            centerY + (value.getY() - componentSize.getHeight() * 0.5) / zoom);
    }

    LayoutPoint toWorld(final Point screen, final Dimension size) {
        return toWorld((Point2D) Objects.requireNonNull(screen, "screen"), size);
    }

    LayoutPoint toWorld(final double screenX, final double screenY, final Dimension size) {
        requireFinite(screenX, "screenX");
        requireFinite(screenY, "screenY");
        return toWorld(new Point2D.Double(screenX, screenY), size);
    }

    GraphViewport panPixels(final double dx, final double dy) {
        requireFinite(dx, "dx");
        requireFinite(dy, "dy");
        return new GraphViewport(centerX - dx / zoom, centerY - dy / zoom, zoom, unknownXml);
    }

    GraphViewport zoomAround(final Point2D pointer, final double factor, final Dimension size) {
        final Point2D value = Objects.requireNonNull(pointer, "pointer");
        requireFinite(value.getX(), "pointerX");
        requireFinite(value.getY(), "pointerY");
        requireFinite(factor, "factor");
        if (!(factor > 0.0)) {
            throw new IllegalArgumentException("zoom factor must be positive");
        }
        final Dimension componentSize = requireSize(size);
        final LayoutPoint worldAtPointer = toWorld(value, componentSize);
        final double nextZoom = zoom * factor;
        final double nextCenterX = worldAtPointer.x()
            - (value.getX() - componentSize.getWidth() * 0.5) / nextZoom;
        final double nextCenterY = worldAtPointer.y()
            - (value.getY() - componentSize.getHeight() * 0.5) / nextZoom;
        return new GraphViewport(nextCenterX, nextCenterY, nextZoom, unknownXml);
    }

    static Point2D.Double toScreen(final GraphViewport viewport, final LayoutPoint world,
            final Dimension size) {
        return Objects.requireNonNull(viewport, "viewport").toScreen(world, size);
    }

    static Point2D.Double toScreen(final LayoutPoint world, final GraphViewport viewport,
            final Dimension size) {
        return Objects.requireNonNull(viewport, "viewport").toScreen(world, size);
    }

    static LayoutPoint toWorld(final GraphViewport viewport, final Point2D screen, final Dimension size) {
        return Objects.requireNonNull(viewport, "viewport").toWorld(screen, size);
    }

    static LayoutPoint toWorld(final Point2D screen, final GraphViewport viewport, final Dimension size) {
        return Objects.requireNonNull(viewport, "viewport").toWorld(screen, size);
    }

    static GraphViewport panPixels(final GraphViewport viewport, final double dx, final double dy) {
        return Objects.requireNonNull(viewport, "viewport").panPixels(dx, dy);
    }

    static GraphViewport zoomAround(final GraphViewport viewport, final Point2D pointer,
            final double factor, final Dimension size) {
        return Objects.requireNonNull(viewport, "viewport").zoomAround(pointer, factor, size);
    }

    private static Dimension requireSize(final Dimension size) {
        final Dimension value = Objects.requireNonNull(size, "size");
        if (value.getWidth() < 0.0 || value.getHeight() < 0.0) {
            throw new IllegalArgumentException("Component dimensions must not be negative");
        }
        return value;
    }

    private static void requireFiniteBounds(final double minX, final double minY, final double maxX,
            final double maxY) {
        requireFinite(minX, "minX");
        requireFinite(minY, "minY");
        requireFinite(maxX, "maxX");
        requireFinite(maxY, "maxY");
    }

    private static void requireFinite(final double value, final String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphViewport)) {
            return false;
        }
        final GraphViewport that = (GraphViewport) other;
        return Double.compare(centerX, that.centerX) == 0 && Double.compare(centerY, that.centerY) == 0
            && Double.compare(zoom, that.zoom) == 0 && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerX, centerY, zoom, unknownXml);
    }

    @Override
    public String toString() {
        return "GraphViewport{" + "centerX=" + centerX + ", centerY=" + centerY
            + ", zoom=" + zoom + '}';
    }
}
