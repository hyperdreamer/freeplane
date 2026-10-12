package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.junit.Test;

public class EdgePathShould {
    @Test
    public void exposeStraightDefiningPointsSamplesAndTerminalValues() {
        final EdgePath path = EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(100.0, 0.0));

        assertThat(path.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(path.definingPoints()).containsExactly(LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(100.0, 0.0));
        assertThat(path.samples()).isEqualTo(path.definingPoints());
        assertThat(path.length()).isEqualTo(100.0);
        assertThat(path.arcLengthMidpoint()).isEqualTo(LayoutPoint.of(50.0, 0.0));
        assertThat(path.arrowAnchor(true)).isEqualTo(LayoutPoint.of(100.0, 0.0));
        assertThat(path.arrowAnchor(false)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(path.terminalSegmentLength(true)).isEqualTo(100.0);
        assertThat(path.terminalSegmentLength(false)).isEqualTo(100.0);
        assertThat(path.control()).isEmpty();
    }

    @Test
    public void sampleABowWithThePinnedCountFormula() {
        final EdgePath path = EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 90.0),
            LayoutPoint.of(180.0, 0.0));

        // d = 90, n = ceil(sqrt(90 / 1.5)) = ceil(sqrt(60)) = 8, so 9 samples.
        assertThat(path.samples()).hasSize(9);
        assertThat(path.samples().get(0)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(path.samples().get(8)).isEqualTo(LayoutPoint.of(180.0, 0.0));
        assertThat(path.samples().get(4).x()).isEqualTo(45.0);
        assertThat(path.samples().get(4).y()).isEqualTo(45.0);
        assertThat(path.control()).contains(LayoutPoint.of(0.0, 90.0));
        // The terminal segment is control -> arrival attachment.
        assertThat(path.arrowAnchor(false)).isEqualTo(LayoutPoint.of(0.0, 90.0));
        assertThat(path.terminalSegmentLength(false))
            .isEqualTo(Math.hypot(180.0, 90.0));
    }

    @Test
    public void exposeDoglegDefiningPointsAndTerminalValues() {
        final EdgePath path = EdgePath.dogleg(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(10.0, 20.0),
            LayoutPoint.of(20.0, 20.0), LayoutPoint.of(30.0, 0.0));

        assertThat(path.definingPoints()).containsExactly(LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(10.0, 20.0), LayoutPoint.of(20.0, 20.0), LayoutPoint.of(30.0, 0.0));
        assertThat(path.samples()).isEqualTo(path.definingPoints());
        assertThat(path.arrowAnchor(true)).isEqualTo(LayoutPoint.of(10.0, 20.0));
        assertThat(path.arrowAnchor(false)).isEqualTo(LayoutPoint.of(20.0, 20.0));
        assertThat(path.terminalSegmentLength(false)).isEqualTo(Math.hypot(10.0, 20.0));
        assertThat(path.length()).isEqualTo(Math.hypot(10.0, 20.0) + 10.0 + Math.hypot(10.0, 20.0));
    }

    @Test
    public void useValueEqualityAcrossEquivalentShapes() {
        assertThat(EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 2.0)))
            .isEqualTo(EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 2.0)))
            .isNotEqualTo(EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(2.0, 1.0)));
        assertThat(EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 1.0),
            LayoutPoint.of(1.0, 0.0)).hashCode())
            .isEqualTo(EdgePath.bow(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 1.0),
                LayoutPoint.of(1.0, 0.0)).hashCode());
    }

    @Test
    public void returnTheFirstSampleForADegeneratePath() {
        final EdgePath path = EdgePath.straight(LayoutPoint.of(1.0, 1.0), LayoutPoint.of(1.0, 1.0));

        assertThat(path.length()).isZero();
        assertThat(path.arcLengthMidpoint()).isEqualTo(LayoutPoint.of(1.0, 1.0));
    }

    @Test
    public void freezeEdgePathsInCanonicalInsertionOrderAndRejectNulls() {
        final ProjectedEdgeKey first = ProjectedEdgeKey.of(
            org.freeplane.plugin.graph.projection.ProjectedEndpointKey.ofNode(
                org.freeplane.plugin.graph.projection.ProjectedNodeKey.of(
                    org.freeplane.plugin.graph.projection.input.SourceNodeKey.persisted(
                        org.freeplane.plugin.graph.workspace.model.NodeReference.of(
                            org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(
                                "8055d8c8-d71e-40f3-a8ed-5bf2502f2cad"),
                            org.freeplane.plugin.graph.workspace.model.PersistedNodeId.of("a"))))),
            org.freeplane.plugin.graph.projection.ProjectedEndpointKey.ofNode(
                org.freeplane.plugin.graph.projection.ProjectedNodeKey.of(
                    org.freeplane.plugin.graph.projection.input.SourceNodeKey.persisted(
                        org.freeplane.plugin.graph.workspace.model.NodeReference.of(
                            org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(
                                "8055d8c8-d71e-40f3-a8ed-5bf2502f2cad"),
                            org.freeplane.plugin.graph.workspace.model.PersistedNodeId.of("b"))))));
        final Map<ProjectedEdgeKey, EdgePath> byEdge = new java.util.LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        byEdge.put(first, EdgePath.straight(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 1.0)));
        final EdgePaths paths = EdgePaths.of(byEdge, EdgeRouteDiagnostics.empty(),
            new EdgeRouteTimings(1L, 2L, 3L));

        assertThat(paths.byEdge()).containsExactlyEntriesOf(byEdge);
        assertThat(paths.path(first)).isPresent();
        assertThat(paths.timings().totalNanos()).isEqualTo(6L);
        assertThat(EdgePaths.empty().byEdge()).isEmpty();
        assertThat(EdgePaths.empty().path(first)).isEmpty();
        assertThatThrownBy(() -> EdgePaths.of(null, EdgeRouteDiagnostics.empty(),
            new EdgeRouteTimings(0L, 0L, 0L))).isInstanceOf(NullPointerException.class);
    }
}
