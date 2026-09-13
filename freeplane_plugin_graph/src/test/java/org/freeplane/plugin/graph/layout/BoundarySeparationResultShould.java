package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.junit.Test;

public class BoundarySeparationResultShould {
    @Test
    public void exposePositionsDiagnosticsAndTimings() {
        final BoundarySeparationDiagnostics diagnostics = BoundarySeparationDiagnostics.empty();
        final BoundarySeparationTimings timings = new BoundarySeparationTimings(1L, 2L, 3L, 4L);
        final LayoutPositions positions = LayoutPositions.of(
            Collections.<org.freeplane.plugin.graph.projection.ProjectedNodeKey,
                org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap(),
            Collections.<org.freeplane.plugin.graph.projection.EnclosureHullKey,
                org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap());

        final BoundarySeparationResult result = new BoundarySeparationResult(positions, diagnostics, 7, timings);

        assertThat(result.positions()).isSameAs(positions);
        assertThat(result.diagnostics()).isSameAs(diagnostics);
        assertThat(result.nodeResidualViolations()).isEqualTo(7);
        assertThat(result.timings()).isSameAs(timings);
        assertThat(result.appliedDisplacements()).isSameAs(diagnostics.appliedDisplacements());
    }

    @Test
    public void rejectNegativeNodeResidualViolations() {
        assertThatThrownBy(() -> new BoundarySeparationResult(positions(), diagnostics(), -1, timings()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Node residual violations must be nonnegative");
    }

    @Test
    public void rejectNullPositions() {
        assertThatThrownBy(() -> new BoundarySeparationResult(null, diagnostics(), 0, timings()))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("positions");
    }

    @Test
    public void rejectNullDiagnostics() {
        assertThatThrownBy(() -> new BoundarySeparationResult(positions(), null, 0, timings()))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("diagnostics");
    }

    @Test
    public void rejectNullTimings() {
        assertThatThrownBy(() -> new BoundarySeparationResult(positions(), diagnostics(), 0, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("timings");
    }

    private static LayoutPositions positions() {
        return LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
    }

    private static BoundarySeparationDiagnostics diagnostics() {
        return BoundarySeparationDiagnostics.empty();
    }

    private static BoundarySeparationTimings timings() {
        return new BoundarySeparationTimings(1L, 2L, 3L, 4L);
    }
}
