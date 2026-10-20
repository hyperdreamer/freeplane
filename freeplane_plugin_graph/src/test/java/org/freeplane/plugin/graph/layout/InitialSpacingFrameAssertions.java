package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.*;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.*;
import java.util.concurrent.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.projection.*;

public final class InitialSpacingFrameAssertions {
    private InitialSpacingFrameAssertions() { }

    public static GeometryTextMetrics metrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
    }

    public static LayoutFrame awaitWithinDeadline(CompletionStage<LayoutFrame> stage, long deadline)
            throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) throw new TimeoutException("Initial spacing shared deadline expired");
        return stage.toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
    }

    public static List<String> residualPairs(GraphProjection projection, LayoutPositions positions) {
        List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        Collections.sort(nodes, (a, b) -> CanonicalKeys.node(a).compareTo(CanonicalKeys.node(b)));
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ProjectedNodeKey a = nodes.get(i), b = nodes.get(j);
                LayoutPoint x = positions.nodes().get(a), y = positions.nodes().get(b);
                double need = 8.0 * scale(projection, a) + 8.0 * scale(projection, b) + 6.0;
                if (Math.hypot(y.x() - x.x(), y.y() - x.y()) < need) {
                    result.add(CanonicalKeys.nodePair(a, b));
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    private static double scale(GraphProjection p, ProjectedNodeKey key) {
        return p.prominence().containsKey(key) ? p.prominence().get(key).scale() : 1.0;
    }

    public static void check(GraphProjection projection, LayoutFrame frame,
            List<PinProjection> pins, boolean requireZero) {
        assertThat(frame.failed()).isFalse();
        assertThat(frame.verified()).isTrue();
        Set<ProjectedNodeKey> nodes = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) assertThat(nodes.add(node.key())).isTrue();
        Set<EnclosureHullKey> anchors = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : projection.enclosures()) assertThat(anchors.add(e.hullKey())).isTrue();
        assertThat(frame.positions().nodes().keySet()).isEqualTo(nodes);
        assertThat(frame.positions().anchors().keySet()).isEqualTo(anchors);
        for (LayoutPoint point : frame.positions().nodes().values()) point(point);
        for (LayoutPoint point : frame.positions().anchors().values()) point(point);
        List<String> residuals = residualPairs(projection, frame.positions());
        assertThat(residuals).doesNotHaveDuplicates();
        assertThat(frame.residualViolations()).isEqualTo(residuals.size());
        if (requireZero) assertThat(residuals).isEmpty();
        BoundarySeparationDiagnostics boundary = frame.boundaryDiagnostics();
        BoundaryInvariantAssertions.assertBijection(boundary);
        assertThat(boundary.boundaryCovered()).isTrue();
        if (pins.isEmpty() || boundary.boundaryVerified()) {
            assertThat(boundary.boundaryVerified()).isTrue();
            assertThat(boundary.hullResidualViolations()).isZero();
            BoundaryInvariantAssertions.assertVerifiedFrame(projection, frame, boundary, metrics(), pins);
        }
        for (PinProjection pin : pins) if (pin.active()) {
            assertThat(frame.positions().nodes().get(pin.projectedNode().get()))
                .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
        }
        assertThat(boundary.rounds()).isBetween(0, 4);
        finiteNonnegative(boundary.displacementRms());
        finiteNonnegative(boundary.displacementMax());
        finiteNonnegative(boundary.deltaRms());
        finiteNonnegative(boundary.deltaMax());
        for (LayoutPoint point : boundary.appliedDisplacements().values()) point(point);
        EdgeConflictDiagnostics edge = frame.edgeConflictDiagnostics();
        assertThat(edge.rounds()).isBetween(0, 4);
        assertThat(edge.verifications()).isBetween(0, 64);
        assertThat(edge.indexInsertedItems()).isBetween(0L, 262144L);
        assertThat(edge.indexPairChecks()).isBetween(0L, 131072L);
        assertThat(edge.verificationWork()).isBetween(0L, 65536L);
        assertThat(edge.conflictsDetected()).isBetween(0, 4096);
        assertThat(edge.conflictsResidual()).doesNotHaveDuplicates();
        assertThat(edge.indexBudgetExhausted()).isFalse();
        assertThat(edge.verificationBudgetExhausted()).isFalse();
        assertThat(edge.detectionTruncated()).isFalse();
        finiteNonnegative(frame.idle().rms());
        finiteNonnegative(frame.idle().max());
    }

    private static void point(LayoutPoint point) {
        assertThat(point).isNotNull();
        assertThat(Double.isFinite(point.x())).isTrue();
        assertThat(Double.isFinite(point.y())).isTrue();
    }

    private static void finiteNonnegative(double value) {
        assertThat(Double.isFinite(value)).isTrue();
        assertThat(value).isGreaterThanOrEqualTo(0.0);
    }
}
