package org.freeplane.plugin.graph.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class EdgeConflictResult {
    private final LayoutPositions positions;
    private final EdgeConflictDiagnostics diagnostics;
    private final EdgeConflictTimings timings;
    private final BoundarySeparationDiagnostics boundaryDiagnostics;
    private final int nodeResidualViolations;
    private final Map<String, LayoutPoint> appliedDisplacements;

    public EdgeConflictResult(final LayoutPositions positions, final EdgeConflictDiagnostics diagnostics,
            final EdgeConflictTimings timings,
            final BoundarySeparationDiagnostics boundaryDiagnostics,
            final int nodeResidualViolations, final Map<String, LayoutPoint> appliedDisplacements) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.timings = Objects.requireNonNull(timings, "timings");
        this.boundaryDiagnostics = Objects.requireNonNull(boundaryDiagnostics, "boundaryDiagnostics");
        if (nodeResidualViolations < 0) {
            throw new IllegalArgumentException("Node residual violations must be nonnegative");
        }
        this.nodeResidualViolations = nodeResidualViolations;
        this.appliedDisplacements = Collections.unmodifiableMap(
            new LinkedHashMap<String, LayoutPoint>(Objects.requireNonNull(appliedDisplacements,
                "appliedDisplacements")));
    }

    public LayoutPositions positions() { return positions; }
    public EdgeConflictDiagnostics diagnostics() { return diagnostics; }
    public EdgeConflictTimings timings() { return timings; }
    public BoundarySeparationDiagnostics boundaryDiagnostics() { return boundaryDiagnostics; }
    public int nodeResidualViolations() { return nodeResidualViolations; }
    public Map<String, LayoutPoint> appliedDisplacements() { return appliedDisplacements; }
}
