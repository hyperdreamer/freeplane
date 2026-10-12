package org.freeplane.plugin.graph.layout;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class EdgeConflictCorrection {
    public EdgeConflictCorrection() {
    }

    public EdgeConflictResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedEndpointKey> visibleEndpoints,
            final BoundarySeparationResult boundaryResult) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(pins, "pins");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        Objects.requireNonNull(boundaryResult, "boundaryResult");
        return new EdgeConflictResult(positions, EdgeConflictDiagnostics.empty(),
            new EdgeConflictTimings(0L, 0L, 0L, 0L), boundaryResult.diagnostics(),
            boundaryResult.nodeResidualViolations(), Collections.<String, LayoutPoint>emptyMap());
    }
}
