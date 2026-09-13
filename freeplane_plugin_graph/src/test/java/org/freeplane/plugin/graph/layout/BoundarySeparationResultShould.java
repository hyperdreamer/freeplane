package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.freeplane.plugin.graph.geometry.LayoutPositions;
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
}
