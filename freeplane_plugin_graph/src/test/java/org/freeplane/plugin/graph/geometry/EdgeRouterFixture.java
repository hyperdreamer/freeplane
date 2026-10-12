package org.freeplane.plugin.graph.geometry;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

final class EdgeRouterFixture {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(
        new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

    final GraphProjection projection;
    final LayoutPositions positions;
    final GraphGeometry geometry;
    final Set<ProjectedEndpointKey> visibleEndpoints;

    private EdgeRouterFixture(final GraphProjection projection, final LayoutPositions positions) {
        this.projection = projection;
        this.positions = positions;
        this.geometry = new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        this.visibleEndpoints = ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(),
            projection.enclosures());
    }

    static EdgeRouterFixture empty() {
        return new EdgeRouterFixture(GraphProjection.structure(1L,
            Collections.<ProjectedNode>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList()),
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture reportedConvexQuad() {
        final ProjectedNodeKey a = node("ID_A");
        final ProjectedNodeKey b = node("ID_B");
        final ProjectedNodeKey c = node("ID_C");
        final ProjectedNodeKey d = node("ID_D");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, c, d), Arrays.asList(
            edge(a, c), edge(b, d))), LayoutPositions.of(
            points(a, 272.0, 170.0, b, 307.0, 202.0, c, 275.0, 240.0, d, 240.0, 208.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture laterChordBow() {
        final ProjectedNodeKey one = node("ID_1");
        final ProjectedNodeKey two = node("ID_2");
        final ProjectedNodeKey three = node("ID_3");
        final ProjectedNodeKey four = node("ID_4");
        return new EdgeRouterFixture(projection(Arrays.asList(one, two, three, four), Arrays.asList(
            edge(one, two), edge(three, four))), LayoutPositions.of(
            points(one, -90.0, 0.0, two, 90.0, 0.0, three, 0.0, -25.0, four, 0.0, 25.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture fanCrossing() {
        final ProjectedNodeKey x = node("X");
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey o = node("O");
        return new EdgeRouterFixture(projection(Arrays.asList(x, a, b, o), Arrays.asList(
            edge(x, a), edge(x, b))), LayoutPositions.of(
            points(x, 0.0, 0.0, a, 12.997, 48.089, b, 38.39, 23.412, o, 4.808, 23.714),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static EdgeRouterFixture oneResolvableEdgeAndOneBrokenEdge() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey missing = node("MISSING");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b), Arrays.asList(
            edge(a, b), edge(a, missing))), LayoutPositions.of(
            points(a, 0.0, 0.0, b, 100.0, 0.0), Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
    }

    static ProjectedEdgeKey exitEdge(final String first, final String second) {
        return ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(node(first)),
            ProjectedEndpointKey.ofNode(node(second)));
    }

    static ProjectedNodeKey node(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static GraphProjection projection(final List<ProjectedNodeKey> keys,
            final List<ProjectedEdgeKey> edges) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : keys) {
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(key.source().toString(),
                key.source().toString()), "map", false));
        }
        final List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        for (final ProjectedEdgeKey edge : edges) {
            final SourceNodeKey sourceKey = edge.first().node().get().source();
            final NodeReference targetReference = NodeReference.of(
                edge.second().node().get().source().mapReferenceId(),
                edge.second().node().get().source().persistedReference().get().nodeId());
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(sourceKey, targetReference,
                false, false, "source", "middle", "target");
            projectedEdges.add(ProjectedEdge.of(edge, Collections.singletonList(
                EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor), edge.first(),
                    edge.second()))));
        }
        return GraphProjection.projected(1L, nodes,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList(),
            projectedEdges, Collections.<org.freeplane.plugin.graph.projection.RelationshipResolution>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList());
    }

    private static ProjectedEdgeKey edge(final ProjectedNodeKey first, final ProjectedNodeKey second) {
        return ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(first), ProjectedEndpointKey.ofNode(second));
    }

    private static Map<ProjectedNodeKey, LayoutPoint> points(final Object... values) {
        final Map<ProjectedNodeKey, LayoutPoint> result = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((ProjectedNodeKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }
}
