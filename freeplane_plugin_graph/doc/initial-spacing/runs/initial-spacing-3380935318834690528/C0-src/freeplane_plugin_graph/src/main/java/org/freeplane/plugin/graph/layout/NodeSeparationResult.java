package org.freeplane.plugin.graph.layout;

import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class NodeSeparationResult {
    private final LayoutPositions positions;
    private final int residualViolations;
    private final int passes;

    private NodeSeparationResult(final LayoutPositions positions, final int residualViolations,
            final int passes) {
        this.positions = Objects.requireNonNull(positions, "positions");
        if (residualViolations < 0) {
            throw new IllegalArgumentException("Residual violations must be nonnegative");
        }
        if (passes < 1 || passes > NodeSeparationProjection.MAX_PASSES) {
            throw new IllegalArgumentException("Pass count must be within the pass budget");
        }
        this.residualViolations = residualViolations;
        this.passes = passes;
    }

    public static NodeSeparationResult of(final LayoutPositions positions, final int residualViolations,
            final int passes) {
        return new NodeSeparationResult(positions, residualViolations, passes);
    }

    public LayoutPositions positions() {
        return positions;
    }

    public int residualViolations() {
        return residualViolations;
    }

    public int passes() {
        return passes;
    }
}
