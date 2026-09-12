package org.freeplane.plugin.graph.canvas;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class LabelPlacementRequest {
    private final GraphProjection projection;
    private final GraphGeometry geometry;
    private final LayoutPositions positions;
    private final double zoom;
    private final double centerX;
    private final double centerY;
    private final Rectangle2D placementArea;
    private final Set<ProjectedEndpointKey> forced;
    private final RenderingLevel renderingLevel;

    private LabelPlacementRequest(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final double zoom, final double centerX, final double centerY,
            final Rectangle2D placementArea, final Set<ProjectedEndpointKey> forced,
            final RenderingLevel renderingLevel) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.positions = Objects.requireNonNull(positions, "positions");
        if (!Double.isFinite(zoom) || !(zoom > 0.0)) {
            throw new IllegalArgumentException("zoom must be finite and positive");
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            throw new IllegalArgumentException("viewport centre must be finite");
        }
        if (placementArea == null || !Double.isFinite(placementArea.getWidth())
                || !Double.isFinite(placementArea.getHeight())
                || !(placementArea.getWidth() > 0.0) || !(placementArea.getHeight() > 0.0)
                || !Double.isFinite(placementArea.getX()) || !Double.isFinite(placementArea.getY())) {
            throw new IllegalArgumentException("placement area must be finite and positive");
        }
        this.zoom = zoom;
        this.centerX = centerX;
        this.centerY = centerY;
        this.placementArea = placementArea;
        final Set<ProjectedEndpointKey> copy = new LinkedHashSet<ProjectedEndpointKey>();
        for (final ProjectedEndpointKey endpoint : Objects.requireNonNull(forced, "forced")) {
            copy.add(Objects.requireNonNull(endpoint, "forced entry"));
        }
        this.forced = Collections.unmodifiableSet(copy);
        this.renderingLevel = Objects.requireNonNull(renderingLevel, "renderingLevel");
    }

    public static LabelPlacementRequest of(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final double zoom, final double centerX, final double centerY,
            final Rectangle2D placementArea, final Set<ProjectedEndpointKey> forced,
            final RenderingLevel renderingLevel) {
        return new LabelPlacementRequest(projection, geometry, positions, zoom, centerX, centerY,
            placementArea, forced, renderingLevel);
    }

    public GraphProjection projection() {
        return projection;
    }

    public GraphGeometry geometry() {
        return geometry;
    }

    public LayoutPositions positions() {
        return positions;
    }

    public double zoom() {
        return zoom;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public Rectangle2D placementArea() {
        return placementArea;
    }

    public Set<ProjectedEndpointKey> forced() {
        return forced;
    }

    public RenderingLevel renderingLevel() {
        return renderingLevel;
    }

    double screenX(final double worldX) {
        return placementArea.getWidth() * 0.5 + zoom * (worldX - centerX);
    }

    double screenY(final double worldY) {
        return placementArea.getHeight() * 0.5 + zoom * (worldY - centerY);
    }

    double worldX(final double screenX) {
        return centerX + (screenX - placementArea.getWidth() * 0.5) / zoom;
    }

    double worldY(final double screenY) {
        return centerY + (screenY - placementArea.getHeight() * 0.5) / zoom;
    }
}
