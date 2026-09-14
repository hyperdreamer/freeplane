package org.freeplane.plugin.graph.layout;

import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class BoundarySeparationResult {
    private final LayoutPositions positions;
    private final BoundarySeparationDiagnostics diagnostics;
    private final int nodeResidualViolations;
    private final BoundarySeparationTimings timings;

    BoundarySeparationResult(final LayoutPositions positions,
            final BoundarySeparationDiagnostics diagnostics, final int nodeResidualViolations,
            final BoundarySeparationTimings timings) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (nodeResidualViolations < 0) {
            throw new IllegalArgumentException("Node residual violations must be nonnegative");
        }
        this.nodeResidualViolations = nodeResidualViolations;
        this.timings = Objects.requireNonNull(timings, "timings");
    }

    public LayoutPositions positions() {
        return positions;
    }

    public BoundarySeparationDiagnostics diagnostics() {
        return diagnostics;
    }

    public int nodeResidualViolations() {
        return nodeResidualViolations;
    }

    public Map<String, LayoutPoint> appliedDisplacements() {
        return diagnostics.appliedDisplacements();
    }

    public BoundarySeparationTimings timings() {
        return timings;
    }
}
