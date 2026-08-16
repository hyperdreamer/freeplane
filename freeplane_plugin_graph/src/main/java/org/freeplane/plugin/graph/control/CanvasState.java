package org.freeplane.plugin.graph.control;

import java.util.Objects;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;

public final class CanvasState {
    private final long generation;
    private final GraphProjection projection;
    private final LayoutFrame layout;
    private final GraphGeometry geometry;
    private final OperationalStatus status;

    private CanvasState(final long generation, final GraphProjection projection, final LayoutFrame layout,
            final GraphGeometry geometry, final OperationalStatus status) {
        if (generation < 0L) {
            throw new IllegalArgumentException("Generation must be nonnegative");
        }
        this.generation = generation;
        this.projection = Objects.requireNonNull(projection, "projection");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.status = Objects.requireNonNull(status, "status");
    }

    public static CanvasState of(final long generation, final GraphProjection projection,
            final LayoutFrame layout, final GraphGeometry geometry, final OperationalStatus status) {
        return new CanvasState(generation, projection, layout, geometry, status);
    }

    public long generation() {
        return generation;
    }

    public GraphProjection projection() {
        return projection;
    }

    public LayoutFrame layout() {
        return layout;
    }

    public GraphGeometry geometry() {
        return geometry;
    }

    public OperationalStatus status() {
        return status;
    }

    CanvasState withStatus(final OperationalStatus nextStatus) {
        return new CanvasState(generation, projection, layout, geometry, nextStatus);
    }
}
