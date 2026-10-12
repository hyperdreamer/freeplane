package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class EdgeConflictCorrectionShould {
    private static final GeometryTextMetrics METRICS =
        new AwtGeometryTextMetrics(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12),
            new java.awt.font.FontRenderContext(null, true, true));
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    @Test
    public void returnsTheBoundaryResultUnchangedInTheScaffold() {
        final GraphProjection projection = GraphProjection.structure(1L,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedNode>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList());
        final LayoutPositions positions = LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        final GraphGeometry geometry = GraphGeometry.of(
            Collections.<ProjectedNodeKey, org.freeplane.plugin.graph.geometry.NodeGeometry>emptyMap(),
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));

        final EdgeConflictResult result = new EdgeConflictCorrection().apply(projection, positions, METRICS,
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEndpointKey>emptySet(), boundary);

        assertThat(result.positions()).isSameAs(positions);
        assertThat(result.diagnostics()).isEqualTo(EdgeConflictDiagnostics.empty());
        assertThat(result.boundaryDiagnostics()).isSameAs(boundary.diagnostics());
        assertThat(result.nodeResidualViolations()).isEqualTo(boundary.nodeResidualViolations());
        assertThat(result.appliedDisplacements()).isEmpty();
        assertThat(result.timings().totalNanos()).isZero();
    }

    @Test
    public void swapsTheReportedConvexQuadAndRemovesTheCrossing() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().edgeOverNodeDetected()).isZero();
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = result.diagnostics().repairsApplied().get(0);
        assertThat(repair.kind()).isEqualTo(EdgeConflictDiagnostics.RepairKind.SWAP);
        assertThat(repair.nodeKeys()).containsExactly(fixture.key(0), fixture.key(1));
        assertThat(repair.fromCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(repair.toCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(repair.fromCentres().get(fixture.key(1))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(repair.toCentres().get(fixture.key(1))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(result.positions().nodes().get(fixture.key(2))).isEqualTo(LayoutPoint.of(275.0, 240.0));
        assertThat(result.positions().nodes().get(fixture.key(3))).isEqualTo(LayoutPoint.of(240.0, 208.0));
        assertThat(result.diagnostics().crossingsResolved()).isEqualTo(1);
        assertThat(result.diagnostics().edgeOverNodeResolved()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void rejectsTheTwoAdjacentSwapBatchAndRetriesSingles() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        // Greedy batch = {swap01, swap23}; applying both leaves the two diagonals, so A1's
        // strict decrease fails, and the single swap01 is accepted.
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verifications()).isEqualTo(2);
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void retriesASingleCandidateAfterARejectedBatch() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        // A whole-batch discard (probe 10) would leave rounds == 0 and a residual crossing.
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        assertThat(result.diagnostics().repairsApplied().get(0).kind())
            .isEqualTo(EdgeConflictDiagnostics.RepairKind.SWAP);
        assertThat(result.diagnostics().crossingsResolved()).isEqualTo(1);
    }

    @Test
    public void detectsACrossingAtExtremeSpans() {
        final Fixture fixture = fixture(1L, new String[] {"A", "B", "C", "D"},
            new double[] {-1e300, -1e300, 1e300, 1e300, -1e300, 1e300, 1e300, -1e300},
            new int[][] {{0, 1}, {2, 3}});
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().indexBudgetExhausted()).isTrue();
    }

    @Test
    public void enumeratesEveryPairWhenSubdivisionExhausts() {
        final int chords = 65;
        final double[] x = new double[chords * 2];
        final double[] y = new double[chords * 2];
        for (int index = 0; index < chords; index++) {
            final double theta = 2.0 * Math.PI * index / (double) chords;
            x[index * 2] = 100.5 * Math.cos(theta);
            y[index * 2] = 100.5 * Math.sin(theta);
            x[index * 2 + 1] = -100.5 * Math.cos(theta);
            y[index * 2 + 1] = -100.5 * Math.sin(theta);
        }
        final int[][] edges = new int[chords][];
        for (int index = 0; index < chords; index++) {
            edges[index] = new int[] {index * 2, index * 2 + 1};
        }
        final String[] ids = new String[chords * 2];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = "C" + index;
        }
        final Fixture fixture = fixture(1L, ids, x, y, edges, 14.0);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(2080);
        assertThat(result.diagnostics().indexSubdivisions()).isEqualTo(4);
        assertThat(result.diagnostics().indexBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().indexPairChecks()).isGreaterThanOrEqualTo(2080L);
    }

    private static final class Fixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final List<ProjectedNodeKey> keys;

        private Fixture(GraphProjection projection, BoundarySeparationResult boundary,
                Set<ProjectedEndpointKey> visible, List<ProjectedNodeKey> keys) {
            this.projection = projection;
            this.boundary = boundary;
            this.visible = visible;
            this.keys = keys;
        }

        private ProjectedNodeKey key(int index) {
            return keys.get(index);
        }
    }

    private static EdgeConflictResult apply(final Fixture fixture) {
        return new EdgeConflictCorrection().apply(fixture.projection, fixture.boundary.positions(),
            METRICS, Collections.<PinProjection>emptyList(), fixture.visible, fixture.boundary);
    }

    private static Fixture reportedQuad() {
        return fixture(1L, new String[] {"ID_A", "ID_B", "ID_C", "ID_D"},
            new double[] {272.0, 170.0, 307.0, 202.0, 275.0, 240.0, 240.0, 208.0},
            new int[][] {{0, 2}, {1, 3}});
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] coordinates,
            final int[][] edges) {
        return fixture(generation, ids, coordinates, edges, 14.0);
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] x,
            final double[] y, final int[][] edges, final double radius) {
        final double[] coordinates = new double[x.length * 2];
        for (int index = 0; index < x.length; index++) {
            coordinates[index * 2] = x[index];
            coordinates[index * 2 + 1] = y[index];
        }
        return fixture(generation, ids, coordinates, edges, radius);
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] coordinates,
            final int[][] edges, final double radius) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (int index = 0; index < ids.length; index++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
                NodeReference.of(MAP, PersistedNodeId.of(ids[index]))));
            final LayoutPoint point = LayoutPoint.of(coordinates[index * 2], coordinates[index * 2 + 1]);
            keys.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(ids[index], ids[index]), "map", false));
            points.put(key, point);
            nodeGeometry.put(key, NodeGeometry.of(point, radius));
        }
        final List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        for (final int[] edge : edges) {
            final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(keys.get(edge[0]));
            final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(keys.get(edge[1]));
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(
                first.node().get().source(), NodeReference.of(second.mapReferenceId(),
                    PersistedNodeId.of(ids[edge[1]])), false, false, "s", "m", "t");
            projectedEdges.add(ProjectedEdge.of(ProjectedEdgeKey.of(first, second),
                Collections.singletonList(EdgeContributor.nativeConnector(
                    ConnectorSnapshot.of(0, descriptor), first, second))));
        }
        final GraphProjection projection = GraphProjection.projected(generation, nodes,
            Collections.<ProjectedEnclosure>emptyList(), projectedEdges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        final LayoutPositions positions = LayoutPositions.of(points,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        final GraphGeometry geometry = GraphGeometry.of(nodeGeometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        return new Fixture(projection, boundary, visible, keys);
    }
}
