package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.NodeProminence;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
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

public class NodeSeparationProjectionShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final int PROMINENT_TARGETS = 14;

    @Test
    public void separatesTwoProminentNodesAndClearsTheResidual() {
        GraphProjection projection = redPhaseProjection();
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "p1", "p2" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(24.0, 0.0) }));

        assertThat(violationCount(projection, raw)).isEqualTo(1);

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.positions().nodes().get(key("p1"))).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(key("p2"))).isEqualTo(LayoutPoint.of(29.0, 0.0));
        assertThat(distance(result.positions().nodes().get(key("p1")),
            result.positions().nodes().get(key("p2")))).isEqualTo(34.0);
        assertThat(result.residualViolations()).isZero();
        assertThat(result.passes()).isEqualTo(2);
        assertThat(violationCount(projection, result.positions())).isZero();
    }

    @Test
    public void returnsAlreadySatisfiedProminentPositionsUnchanged() {
        GraphProjection projection = redPhaseProjection();
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "p1", "p2" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(40.0, 0.0) }));

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.positions().nodes()).isEqualTo(raw.nodes());
        assertThat(result.residualViolations()).isZero();
        assertThat(result.passes()).isEqualTo(1);
    }

    @Test
    public void reportsTheNonConvergingSandwichAtThePassCap() {
        GraphProjection projection = projection("a", "b", "m");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b", "m" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(40.0, 0.0),
                LayoutPoint.of(20.0, 0.0) }));
        Set<ProjectedNodeKey> pinned = pinned("a", "b");

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw, pinned);

        assertThat(result.passes()).isEqualTo(64);
        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(40.0, 0.0));
        assertThat(result.positions().nodes().get(key("m"))).isEqualTo(LayoutPoint.of(18.0, 0.0));
        assertThat(result.residualViolations()).isEqualTo(1);
        assertFinite(result.positions());
    }

    @Test
    public void leavesPinnedPinnedOverlapUntouchedAndCounted() {
        GraphProjection projection = projection("a", "b", "m");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b", "m" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0),
                LayoutPoint.of(10.0, 0.0) }));
        Set<ProjectedNodeKey> pinned = pinned("a", "b");

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw, pinned);

        assertThat(result.passes()).isEqualTo(2);
        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(20.0, 0.0));
        assertThat(result.positions().nodes().get(key("m"))).isEqualTo(LayoutPoint.of(42.0, 0.0));
        assertThat(result.residualViolations()).isEqualTo(1);
    }

    @Test
    public void convergesASolvablePairToTheMinimumDistance() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0) }));

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.passes()).isEqualTo(2);
        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(-1.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(21.0, 0.0));
        assertThat(result.residualViolations()).isZero();
        assertThat(distance(result.positions().nodes().get(key("a")),
            result.positions().nodes().get(key("b")))).isEqualTo(22.0);
    }

    @Test
    public void separatesCoincidentParticlesDeterministically() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 0.0) }));

        NodeSeparationResult first = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());
        NodeSeparationResult second = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(first.passes()).isEqualTo(3);
        assertThat(first.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(-11.0, 0.0));
        assertThat(first.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(11.0, 0.0));
        assertThat(first.residualViolations()).isZero();
        assertThat(second.positions().nodes()).isEqualTo(first.positions().nodes());
        assertFinite(first.positions());
    }

    @Test
    public void copiesAnchorsThroughUnchanged() {
        GraphProjection projection = projection("a", "b");
        EnclosureHullKey hull = EnclosureHullKey.of(
            Collections.singletonList(EnclosureKey.of(source("hull"))));
        Map<ProjectedNodeKey, LayoutPoint> nodes = orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0) });
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hull, LayoutPoint.of(7.5, -3.25));
        LayoutPositions raw = LayoutPositions.of(nodes, anchors);

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.positions().anchors()).isEqualTo(raw.anchors());
        assertThat(result.positions().anchors().get(hull)).isEqualTo(LayoutPoint.of(7.5, -3.25));
    }

    @Test
    public void ignoresPinnedKeysThatAreNotInThePositions() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0) }));
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        pinned.add(key("missing"));

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw, pinned);

        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(-1.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(21.0, 0.0));
        assertThat(result.residualViolations()).isZero();
    }

    @Test
    public void rejectsNonFiniteInputCoordinates() {
        GraphProjection projection = projection("a", "b");

        assertThatThrownBy(() -> {
            LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
                new LayoutPoint[] { LayoutPoint.of(Double.NaN, 0.0), LayoutPoint.of(20.0, 0.0) }));
            new NodeSeparationProjection().project(projection, raw,
                Collections.<ProjectedNodeKey>emptySet());
        }).isInstanceOf(IllegalArgumentException.class);
    }

    private static GraphProjection redPhaseProjection() {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node("p1"));
        nodes.add(node("p2"));
        for (int index = 0; index < 2 * PROMINENT_TARGETS; index++) {
            nodes.add(node("t" + index));
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int index = 0; index < PROMINENT_TARGETS; index++) {
            edges.add(directedEdge("p1", "t" + index));
            edges.add(directedEdge("p2", "t" + (PROMINENT_TARGETS + index)));
        }
        return GraphProjection.projected(1L, nodes,
            Collections.<ProjectedEnclosure>emptyList(), edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static GraphProjection projection(String... ids) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (String id : ids) {
            nodes.add(node(id));
        }
        return GraphProjection.structure(1L, nodes, Collections.<ProjectedEnclosure>emptyList());
    }

    private static ProjectedNode node(String id) {
        return ProjectedNode.of(key(id), SafeNodeLabel.of(id, id), "Map", false);
    }

    private static ProjectedEdge directedEdge(String from, String to) {
        SourceNodeKey source = source(from);
        NodeReference target = reference(to);
        ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofNode(key(from));
        ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofNode(key(to));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, target, false, true,
            "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(0, descriptor), sourceEndpoint, targetEndpoint);
        return ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(contributor));
    }

    private static LayoutPositions positions(GraphProjection projection, Map<ProjectedNodeKey, LayoutPoint> nodes) {
        return LayoutPositions.of(nodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
    }

    private static Map<ProjectedNodeKey, LayoutPoint> orderedPoints(String[] ids, LayoutPoint[] points) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < ids.length; index++) {
            nodes.put(key(ids[index]), points[index]);
        }
        return nodes;
    }

    private static Set<ProjectedNodeKey> pinned(String... ids) {
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (String id : ids) {
            pinned.add(key(id));
        }
        return pinned;
    }

    private static int violationCount(GraphProjection projection, LayoutPositions positions) {
        List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        int violations = 0;
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                double need = radius(projection, keys.get(first)) + radius(projection, keys.get(second)) + 6.0;
                if (distance(positions.nodes().get(keys.get(first)), positions.nodes().get(keys.get(second))) < need) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static double radius(GraphProjection projection, ProjectedNodeKey key) {
        NodeProminence prominence = projection.prominence().get(key);
        return 8.0 * (prominence == null ? 1.0 : prominence.scale());
    }

    private static double distance(LayoutPoint first, LayoutPoint second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private static void assertFinite(LayoutPositions positions) {
        for (LayoutPoint point : positions.nodes().values()) {
            assertThat(Double.isFinite(point.x()) && Double.isFinite(point.y())).isTrue();
        }
    }

    private static ProjectedNodeKey key(String id) {
        return ProjectedNodeKey.of(source(id));
    }

    private static SourceNodeKey source(String id) {
        return SourceNodeKey.persisted(reference(id));
    }

    private static NodeReference reference(String id) {
        return NodeReference.of(MAP, PersistedNodeId.of(id));
    }
}
