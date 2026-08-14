package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
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

public class HullGeometryShould {
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final String MAP_NAME = "Map";

    @Test
    public void rejectsNonFinitePointsInvalidRadiiAndMutableOrMismatchedPositionState() {
        assertThatThrownBy(() -> LayoutPoint.of(Double.NaN, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LayoutPoint.of(1.0, Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LayoutPoint.of(Double.NEGATIVE_INFINITY, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
        LayoutPoint normalized = LayoutPoint.of(-0.0, -0.0);
        assertThat(Double.doubleToLongBits(normalized.x())).isEqualTo(Double.doubleToLongBits(0.0));
        assertThat(Double.doubleToLongBits(normalized.y())).isEqualTo(Double.doubleToLongBits(0.0));

        assertThatThrownBy(() -> NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 0.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NodeGeometry.of(LayoutPoint.of(0.0, 0.0), -1.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NodeGeometry.of(LayoutPoint.of(0.0, 0.0), Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NodeGeometry.of(LayoutPoint.of(0.0, 0.0), Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);

        Map<ProjectedNodeKey, LayoutPoint> nullKey = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nullKey.put(null, LayoutPoint.of(0.0, 0.0));
        assertThatThrownBy(() -> LayoutPositions.of(nullKey, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))
            .isInstanceOf(NullPointerException.class);
        Map<ProjectedNodeKey, LayoutPoint> nullValue = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nullValue.put(nodeKey("n1"), null);
        assertThatThrownBy(() -> LayoutPositions.of(nullValue, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LayoutPositions.of(null, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))
            .isInstanceOf(NullPointerException.class);

        LayoutPositions positions = LayoutPositions.of(
            Collections.singletonMap(nodeKey("n1"), LayoutPoint.of(0.0, 0.0)),
            Collections.singletonMap(hullKey("e1"), LayoutPoint.of(0.0, 0.0)));
        assertThatThrownBy(() -> positions.nodes().put(nodeKey("other"), LayoutPoint.of(0.0, 0.0)))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> positions.anchors().put(hullKey("other"), LayoutPoint.of(0.0, 0.0)))
            .isInstanceOf(UnsupportedOperationException.class);

        GraphGeometry geometry = GraphGeometry.of(
            Collections.singletonMap(nodeKey("n1"), NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 8.0)),
            Collections.singletonMap(hullKey("e1"), HullGeometry.of(square(), LayoutPoint.of(0.0, 0.0))));
        assertThatThrownBy(() -> geometry.nodes().put(nodeKey("other"),
            NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 8.0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> geometry.hulls().put(hullKey("other"), geometry.hulls().get(hullKey("e1"))))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> GraphGeometry.of(
            Collections.singletonMap(nodeKey("n1"), null), Collections.<EnclosureHullKey, HullGeometry>emptyMap()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HullGeometry.of(
            Arrays.asList(LayoutPoint.of(0.0, 0.0), null, LayoutPoint.of(1.0, 1.0)), LayoutPoint.of(0.0, 0.0)))
                .isInstanceOf(NullPointerException.class);

        ProjectedNode n1 = node("n1");
        EnclosureHullKey e1 = hullKey("e1");
        ProjectedEnclosure enclosure = enclosure(e1, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(n1.key()), Collections.<EnclosureHullKey>emptyList());
        GraphProjection projection = projection(Collections.singletonList(n1), Collections.singletonList(enclosure),
            Collections.<ProjectedEdge>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodePositions =
            Collections.singletonMap(n1.key(), LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = Collections.singletonMap(e1, LayoutPoint.of(0.0, 0.0));
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(projection,
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), anchors)))
                .isInstanceOf(IllegalArgumentException.class);
        Map<ProjectedNodeKey, LayoutPoint> extraNode = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(nodePositions);
        extraNode.put(nodeKey("stale"), LayoutPoint.of(1.0, 1.0));
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(projection,
            LayoutPositions.of(extraNode, anchors))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(projection,
            LayoutPositions.of(nodePositions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap())))
                .isInstanceOf(IllegalArgumentException.class);
        Map<EnclosureHullKey, LayoutPoint> extraAnchor = new LinkedHashMap<EnclosureHullKey, LayoutPoint>(anchors);
        extraAnchor.put(hullKey("stale"), LayoutPoint.of(1.0, 1.0));
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(projection,
            LayoutPositions.of(nodePositions, extraAnchor))).isInstanceOf(IllegalArgumentException.class);
        GraphProjection structure = GraphProjection.structure(1, Collections.singletonList(n1),
            Collections.singletonList(enclosure));
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(structure,
            LayoutPositions.of(nodePositions, anchors))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void computesChildHullsBeforeParentsButPublishesProjectionOrder() {
        ProjectedNode n1 = node("n1");
        ProjectedNodeKey n1Key = n1.key();
        EnclosureHullKey childKey = hullKey("child");
        EnclosureHullKey parentKey = hullKey("parent");
        ProjectedEnclosure child = enclosure(childKey, Optional.of(parentKey), Collections.singletonList(n1Key),
            Collections.<EnclosureHullKey>emptyList());
        ProjectedEnclosure parent = enclosure(parentKey, Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(childKey));
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(n1Key, LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(parentKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(childKey, LayoutPoint.of(0.0, 0.0));
        LayoutPositions positions = LayoutPositions.of(nodePositions, anchors);

        GraphGeometry parentFirst = compute(
            projection(Collections.singletonList(n1), Arrays.asList(parent, child),
                Collections.<ProjectedEdge>emptyList()),
            positions);
        GraphGeometry childFirst = compute(
            projection(Collections.singletonList(n1), Arrays.asList(child, parent),
                Collections.<ProjectedEdge>emptyList()),
            positions);

        assertThat(new ArrayList<EnclosureHullKey>(parentFirst.hulls().keySet()))
            .containsExactly(parentKey, childKey);
        assertThat(new ArrayList<EnclosureHullKey>(childFirst.hulls().keySet()))
            .containsExactly(childKey, parentKey);
        assertThat(childFirst.hulls().get(parentKey)).isEqualTo(parentFirst.hulls().get(parentKey));
        assertThat(childFirst.hulls().get(childKey)).isEqualTo(parentFirst.hulls().get(childKey));
        assertThat(new ArrayList<ProjectedNodeKey>(parentFirst.nodes().keySet())).containsExactly(n1Key);

        HullGeometry childHull = parentFirst.hulls().get(childKey);
        HullGeometry parentHull = parentFirst.hulls().get(parentKey);
        assertThat(childHull.minX()).isEqualTo(-24.0);
        assertThat(childHull.maxX()).isEqualTo(24.0);
        assertThat(childHull.minY()).isEqualTo(-24.0);
        assertThat(childHull.maxY()).isEqualTo(24.0);
        assertThat(parentHull.minX()).isEqualTo(-40.0);
        assertThat(parentHull.maxX()).isEqualTo(40.0);
        for (LayoutPoint vertex : childHull.exactPolygon()) {
            assertThat(parentHull.contains(vertex)).isTrue();
        }
    }

    @Test
    public void containsEveryDirectNodeAndChildHullWithFixedClearance() {
        ProjectedNode originNode = node("origin");
        ProjectedNode offsetNode = node("offset");
        EnclosureHullKey originKey = hullKey("origin-hull");
        EnclosureHullKey offsetKey = hullKey("offset-hull");
        EnclosureHullKey parentKey = hullKey("parent-hull");
        ProjectedEnclosure originEnclosure = enclosure(originKey, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(originNode.key()), Collections.<EnclosureHullKey>emptyList());
        ProjectedEnclosure offsetEnclosure = enclosure(offsetKey, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(offsetNode.key()), Collections.<EnclosureHullKey>emptyList());
        ProjectedEnclosure parentEnclosure = enclosure(parentKey, Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(originKey));
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(originNode.key(), LayoutPoint.of(0.0, 0.0));
        nodePositions.put(offsetNode.key(), LayoutPoint.of(30.0, 40.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(originKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(offsetKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(parentKey, LayoutPoint.of(0.0, 0.0));
        GraphGeometry geometry = compute(projection(Arrays.asList(originNode, offsetNode),
            Arrays.asList(originEnclosure, offsetEnclosure, parentEnclosure), Collections.<ProjectedEdge>emptyList()),
            LayoutPositions.of(nodePositions, anchors));

        HullGeometry originHull = geometry.hulls().get(originKey);
        assertThat(originHull.minX()).isEqualTo(-24.0);
        assertThat(originHull.maxX()).isEqualTo(24.0);
        assertThat(originHull.minY()).isEqualTo(-24.0);
        assertThat(originHull.maxY()).isEqualTo(24.0);
        assertCircleInside(geometry.nodes().get(originNode.key()), originHull);
        assertCircleInside(geometry.nodes().get(offsetNode.key()), geometry.hulls().get(offsetKey));
        for (LayoutPoint vertex : originHull.exactPolygon()) {
            assertThat(geometry.hulls().get(parentKey).contains(vertex)).isTrue();
        }
    }

    @Test
    public void createsASmoothDeterministicClosedPathWithoutCuttingDirectChildren() {
        ProjectedNode originNode = node("origin");
        EnclosureHullKey childKey = hullKey("child");
        EnclosureHullKey parentKey = hullKey("parent");
        ProjectedEnclosure child = enclosure(childKey, Optional.of(parentKey),
            Collections.singletonList(originNode.key()), Collections.<EnclosureHullKey>emptyList());
        ProjectedEnclosure parent = enclosure(parentKey, Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(childKey));
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(originNode.key(), LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(childKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(parentKey, LayoutPoint.of(0.0, 0.0));
        GraphGeometry geometry = compute(
            projection(Collections.singletonList(originNode), Arrays.asList(child, parent),
                Collections.<ProjectedEdge>emptyList()),
            LayoutPositions.of(nodePositions, anchors));

        HullGeometry childHull = geometry.hulls().get(childKey);
        HullGeometry parentHull = geometry.hulls().get(parentKey);
        Shape firstPath = childHull.smoothPath();
        Shape secondPath = childHull.smoothPath();
        assertThat(segmentSignature(secondPath)).containsExactlyElementsOf(segmentSignature(firstPath));

        PathIterator iterator = firstPath.getPathIterator(null);
        double[] coords = new double[6];
        assertThat(iterator.currentSegment(coords)).isEqualTo(PathIterator.SEG_MOVETO);
        boolean sawQuadratic = false;
        int lastSegment = -1;
        while (!iterator.isDone()) {
            lastSegment = iterator.currentSegment(coords);
            if (lastSegment == PathIterator.SEG_QUADTO) {
                sawQuadratic = true;
            }
            iterator.next();
        }
        assertThat(sawQuadratic).isTrue();
        assertThat(lastSegment).isEqualTo(PathIterator.SEG_CLOSE);

        NodeGeometry nodeGeometry = geometry.nodes().get(originNode.key());
        for (LayoutPoint sample : samplePath(childHull.smoothPath())) {
            assertThat(nodeGeometry.contains(sample)).isFalse();
        }
        for (LayoutPoint sample : samplePath(parentHull.smoothPath())) {
            assertThat(childHull.contains(sample)).isFalse();
        }

        Path2D.Double mutated = (Path2D.Double) childHull.smoothPath();
        mutated.lineTo(9999.0, 9999.0);
        assertThat(segmentSignature(childHull.smoothPath())).containsExactlyElementsOf(segmentSignature(firstPath));
    }

    @Test
    public void anchorsAnEmptyEnclosureAtItsSuppliedLayoutAnchor() {
        EnclosureHullKey emptyKey = hullKey("empty");
        ProjectedEnclosure empty = enclosure(emptyKey, Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList());
        LayoutPoint anchor = LayoutPoint.of(30.0, -20.0);
        GraphGeometry geometry = compute(projection(Collections.<ProjectedNode>emptyList(),
            Collections.singletonList(empty), Collections.<ProjectedEdge>emptyList()),
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                Collections.singletonMap(emptyKey, anchor)));

        HullGeometry hull = geometry.hulls().get(emptyKey);
        assertThat(hull.labelAnchor()).isEqualTo(anchor);
        assertThat(hull.exactPolygon()).hasSize(8);
        assertThat(hull.minX()).isEqualTo(14.0);
        assertThat(hull.maxX()).isEqualTo(46.0);
        assertThat(hull.minY()).isEqualTo(-36.0);
        assertThat(hull.maxY()).isEqualTo(-4.0);
        assertThat(hull.contains(anchor)).isTrue();
    }

    @Test
    public void canonicalizesEquivalentConvexPolygonsAndPublishesDeepImmutableValues() {
        List<LayoutPoint> hexagon = Arrays.asList(
            LayoutPoint.of(-10.0, 0.0), LayoutPoint.of(0.0, -10.0), LayoutPoint.of(10.0, -10.0),
            LayoutPoint.of(14.0, 0.0), LayoutPoint.of(10.0, 10.0), LayoutPoint.of(0.0, 10.0));
        LayoutPoint anchor = LayoutPoint.of(0.0, 0.0);
        HullGeometry canonical = HullGeometry.of(hexagon, anchor);
        HullGeometry rotated = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(10.0, -10.0), LayoutPoint.of(14.0, 0.0), LayoutPoint.of(10.0, 10.0),
            LayoutPoint.of(0.0, 10.0), LayoutPoint.of(-10.0, 0.0), LayoutPoint.of(0.0, -10.0)), anchor);
        HullGeometry reversed = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(-10.0, 0.0), LayoutPoint.of(0.0, 10.0), LayoutPoint.of(10.0, 10.0),
            LayoutPoint.of(14.0, 0.0), LayoutPoint.of(10.0, -10.0), LayoutPoint.of(0.0, -10.0)), anchor);
        HullGeometry closed = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(-10.0, 0.0), LayoutPoint.of(0.0, -10.0), LayoutPoint.of(10.0, -10.0),
            LayoutPoint.of(14.0, 0.0), LayoutPoint.of(10.0, 10.0), LayoutPoint.of(0.0, 10.0),
            LayoutPoint.of(-10.0, 0.0)), anchor);
        HullGeometry collinear = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(-10.0, 0.0), LayoutPoint.of(-5.0, -5.0), LayoutPoint.of(0.0, -10.0),
            LayoutPoint.of(10.0, -10.0), LayoutPoint.of(14.0, 0.0), LayoutPoint.of(10.0, 10.0),
            LayoutPoint.of(0.0, 10.0)), anchor);
        assertThat(rotated).isEqualTo(canonical);
        assertThat(reversed).isEqualTo(canonical);
        assertThat(closed).isEqualTo(canonical);
        assertThat(collinear).isEqualTo(canonical);
        assertThat(rotated.hashCode()).isEqualTo(canonical.hashCode());

        assertThatThrownBy(() -> HullGeometry.of(Arrays.asList(
            LayoutPoint.of(-10.0, 0.0), LayoutPoint.of(0.0, -10.0), LayoutPoint.of(10.0, -10.0),
            LayoutPoint.of(4.0, 0.0), LayoutPoint.of(10.0, 10.0), LayoutPoint.of(0.0, 10.0)), anchor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0), LayoutPoint.of(5.0, 0.0), LayoutPoint.of(10.0, 0.0)), anchor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0), LayoutPoint.of(1.0, 1.0)), anchor))
                .isInstanceOf(IllegalArgumentException.class);

        List<LayoutPoint> mutable = new ArrayList<LayoutPoint>(hexagon);
        HullGeometry stable = HullGeometry.of(mutable, anchor);
        mutable.clear();
        assertThat(stable.exactPolygon()).containsExactlyElementsOf(canonical.exactPolygon());
        assertThatThrownBy(() -> stable.exactPolygon().add(LayoutPoint.of(1.0, 1.0)))
            .isInstanceOf(UnsupportedOperationException.class);

        Map<ProjectedNodeKey, LayoutPoint> first = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        first.put(nodeKey("a"), LayoutPoint.of(1.0, 2.0));
        first.put(nodeKey("b"), LayoutPoint.of(3.0, 4.0));
        Map<ProjectedNodeKey, LayoutPoint> second = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        second.put(nodeKey("b"), LayoutPoint.of(3.0, 4.0));
        second.put(nodeKey("a"), LayoutPoint.of(1.0, 2.0));
        LayoutPositions firstPositions =
            LayoutPositions.of(first, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        LayoutPositions secondPositions =
            LayoutPositions.of(second, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        assertThat(firstPositions).isEqualTo(secondPositions);
        assertThat(firstPositions.hashCode()).isEqualTo(secondPositions.hashCode());
        assertThat(new ArrayList<ProjectedNodeKey>(firstPositions.nodes().keySet()))
            .containsExactly(nodeKey("a"), nodeKey("b"));

        assertThat(LayoutPoint.of(1.0, 2.0)).isEqualTo(LayoutPoint.of(1.0, 2.0));
        assertThat(LayoutPoint.of(-0.0, 2.0)).isEqualTo(LayoutPoint.of(0.0, 2.0));
        assertThat(LayoutPoint.of(1.0, 2.0).hashCode()).isEqualTo(LayoutPoint.of(1.0, 2.0).hashCode());
        assertThat(LayoutPoint.of(1.0, 2.0).toString()).contains("1.0").contains("2.0");

        Map<ProjectedNodeKey, NodeGeometry> firstNodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        firstNodes.put(nodeKey("n1"), NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> firstHulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        firstHulls.put(hullKey("e1"), canonical);
        Map<ProjectedNodeKey, NodeGeometry> secondNodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        secondNodes.put(nodeKey("n1"), NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> secondHulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        secondHulls.put(hullKey("e1"), HullGeometry.of(hexagon, anchor));
        GraphGeometry firstGeometry = GraphGeometry.of(firstNodes, firstHulls);
        GraphGeometry secondGeometry = GraphGeometry.of(secondNodes, secondHulls);
        assertThat(firstGeometry).isEqualTo(secondGeometry);
        assertThat(firstGeometry.hashCode()).isEqualTo(secondGeometry.hashCode());
        assertThat(firstGeometry.toString()).contains("nodeCount=");
        assertThat(canonical.toString()).contains("vertexCount=");
        assertThat(canonical.toString()).doesNotContain("x=");
        assertThat(firstPositions.toString()).contains("nodeCount=");
        assertThat(NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 8.0).toString()).contains("radius=");
    }

    @Test
    public void scalesNodeExtentFromPublishedProminenceWithoutScalingHullClearance() {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node("source"));
        for (int index = 1; index <= 14; index++) {
            nodes.add(node("target-" + index));
        }
        nodes.add(node("unconnected"));
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int index = 1; index <= 14; index++) {
            edges.add(directedEdge("source", "target-" + index, index));
        }
        EnclosureHullKey sourceHullKey = hullKey("source-hull");
        ProjectedEnclosure sourceHull = enclosure(sourceHullKey, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(nodeKey("source")), Collections.<EnclosureHullKey>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(nodeKey("source"), LayoutPoint.of(0.0, 0.0));
        for (int index = 1; index <= 14; index++) {
            nodePositions.put(nodeKey("target-" + index), LayoutPoint.of(200.0 + index, 0.0));
        }
        nodePositions.put(nodeKey("unconnected"), LayoutPoint.of(-50.0, -50.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(sourceHullKey, LayoutPoint.of(0.0, 0.0));
        GraphGeometry geometry = compute(projection(nodes, Collections.singletonList(sourceHull), edges),
            LayoutPositions.of(nodePositions, anchors));

        assertThat(geometry.nodes().get(nodeKey("source")).radius()).isEqualTo(14.0);
        assertThat(geometry.nodes().get(nodeKey("target-1")).radius()).isEqualTo(8.0);
        assertThat(geometry.nodes().get(nodeKey("unconnected")).radius()).isEqualTo(8.0);
        assertThat(geometry.edgeAttachment(ProjectedEndpointKey.ofNode(nodeKey("source")),
            LayoutPoint.of(100.0, 0.0))).isEqualTo(LayoutPoint.of(14.0, 0.0));
        HullGeometry sourceHullGeometry = geometry.hulls().get(sourceHullKey);
        assertThat(sourceHullGeometry.maxX()).isEqualTo(30.0);
        assertThat(sourceHullGeometry.maxX() - geometry.nodes().get(nodeKey("source")).radius()).isEqualTo(16.0);
    }

    @Test
    public void containsACappedProminenceNodeInItsDirectHull() {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node("source"));
        for (int index = 1; index <= 14; index++) {
            nodes.add(node("target-" + index));
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int index = 1; index <= 14; index++) {
            edges.add(directedEdge("source", "target-" + index, index));
        }
        EnclosureHullKey sourceHullKey = hullKey("source-hull");
        ProjectedEnclosure sourceHull = enclosure(sourceHullKey, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(nodeKey("source")), Collections.<EnclosureHullKey>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(nodeKey("source"), LayoutPoint.of(0.0, 0.0));
        for (int index = 1; index <= 14; index++) {
            nodePositions.put(nodeKey("target-" + index), LayoutPoint.of(200.0 + index, 0.0));
        }
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(sourceHullKey, LayoutPoint.of(0.0, 0.0));
        GraphGeometry geometry = compute(projection(nodes, Collections.singletonList(sourceHull), edges),
            LayoutPositions.of(nodePositions, anchors));

        NodeGeometry source = geometry.nodes().get(nodeKey("source"));
        assertThat(source.radius()).isEqualTo(14.0);
        assertCircleInside(source, geometry.hulls().get(sourceHullKey));
    }

    @Test
    public void attachesToTheScaledNodeBoundaryAndNearestExactHullBoundary() {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node("source"));
        for (int index = 1; index <= 14; index++) {
            nodes.add(node("target-" + index));
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int index = 1; index <= 14; index++) {
            edges.add(directedEdge("source", "target-" + index, index));
        }
        EnclosureHullKey sourceHullKey = hullKey("source-hull");
        ProjectedEnclosure sourceHull = enclosure(sourceHullKey, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(nodeKey("source")), Collections.<EnclosureHullKey>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(nodeKey("source"), LayoutPoint.of(0.0, 0.0));
        for (int index = 1; index <= 14; index++) {
            nodePositions.put(nodeKey("target-" + index), LayoutPoint.of(200.0 + index, 0.0));
        }
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(sourceHullKey, LayoutPoint.of(0.0, 0.0));
        GraphGeometry geometry = compute(projection(nodes, Collections.singletonList(sourceHull), edges),
            LayoutPositions.of(nodePositions, anchors));

        assertThat(geometry.edgeAttachment(ProjectedEndpointKey.ofNode(nodeKey("source")),
            LayoutPoint.of(100.0, 0.0))).isEqualTo(LayoutPoint.of(14.0, 0.0));
        LayoutPoint diagonal = geometry.edgeAttachment(ProjectedEndpointKey.ofNode(nodeKey("source")),
            LayoutPoint.of(100.0, 100.0));
        assertThat(diagonal.x()).isCloseTo(14.0 * Math.sqrt(0.5), within(1e-9));
        assertThat(diagonal.y()).isCloseTo(14.0 * Math.sqrt(0.5), within(1e-9));
        assertThat(geometry.edgeAttachment(ProjectedEndpointKey.ofNode(nodeKey("source")),
            LayoutPoint.of(0.0, 0.0))).isEqualTo(LayoutPoint.of(14.0, 0.0));
        LayoutPoint hullAttachment = geometry.edgeAttachment(
            ProjectedEndpointKey.ofEnclosure(enclosureKey("source-hull")), LayoutPoint.of(100.0, 0.0));
        assertThat(hullAttachment.x()).isCloseTo(30.0, within(1e-9));
        assertThat(hullAttachment.y()).isCloseTo(0.0, within(1e-9));

        assertThatThrownBy(() -> geometry.edgeAttachment(ProjectedEndpointKey.ofNode(nodeKey("ghost")),
            LayoutPoint.of(0.0, 0.0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> geometry.edgeAttachment(
            ProjectedEndpointKey.ofEnclosure(enclosureKey("ghost")), LayoutPoint.of(0.0, 0.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void mapsEveryAddressableAncestorInAUnaryHullToOneVisibleBoundary() {
        EnclosureHullKey collapsedKey = hullKey("ancestor-one", "ancestor-two");
        ProjectedEnclosure collapsed = enclosure(collapsedKey, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(nodeKey("n1")), Collections.<EnclosureHullKey>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodePositions.put(nodeKey("n1"), LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(collapsedKey, LayoutPoint.of(0.0, 0.0));
        GraphGeometry geometry = compute(
            projection(Collections.singletonList(node("n1")), Collections.singletonList(collapsed),
                Collections.<ProjectedEdge>emptyList()),
            LayoutPositions.of(nodePositions, anchors));

        LayoutPoint toward = LayoutPoint.of(100.0, 0.0);
        LayoutPoint firstAttachment = geometry.edgeAttachment(
            ProjectedEndpointKey.ofEnclosure(enclosureKey("ancestor-one")), toward);
        LayoutPoint secondAttachment = geometry.edgeAttachment(
            ProjectedEndpointKey.ofEnclosure(enclosureKey("ancestor-two")), toward);
        assertThat(firstAttachment).isEqualTo(secondAttachment);
        assertThat(firstAttachment).isEqualTo(geometry.hulls().get(collapsedKey).nearestBoundaryPoint(toward));
        assertThat(firstAttachment.x()).isCloseTo(24.0, within(1e-9));
        assertThat(firstAttachment.y()).isCloseTo(0.0, within(1e-9));
        assertThat(geometry.hulls().get(collapsedKey).contains(firstAttachment)).isTrue();

        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(nodeKey("n1"), NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 8.0));
        HullGeometry shared = HullGeometry.of(square(), LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(hullKey("ancestor-one"), shared);
        hulls.put(hullKey("ancestor-one", "ancestor-two"), shared);
        assertThatThrownBy(() -> GraphGeometry.of(nodes, hulls)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectsASelfIntersectingStarPolygon() {
        List<LayoutPoint> star = Arrays.asList(
            LayoutPoint.of(0.0, 10.0),
            LayoutPoint.of(5.877852522924732, -8.090169943749475),
            LayoutPoint.of(-9.510565162951535, 3.090169943749474),
            LayoutPoint.of(9.510565162951535, 3.090169943749474),
            LayoutPoint.of(-5.877852522924732, -8.090169943749475));
        assertThatThrownBy(() -> HullGeometry.of(star, LayoutPoint.of(0.0, 0.0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectsAdjacentCollinearBacktrackingBeforeCanonicalization() {
        assertThatThrownBy(() -> HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(10.0, 0.0),
            LayoutPoint.of(5.0, 0.0),
            LayoutPoint.of(10.0, 10.0),
            LayoutPoint.of(0.0, 10.0)), LayoutPoint.of(0.0, 0.0)))
                .isInstanceOf(IllegalArgumentException.class);

        HullGeometry canonical = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(5.0, 0.0),
            LayoutPoint.of(10.0, 0.0),
            LayoutPoint.of(10.0, 10.0),
            LayoutPoint.of(0.0, 10.0)), LayoutPoint.of(0.0, 0.0));
        assertThat(canonical.exactPolygon()).containsExactlyElementsOf(square());
    }

    @Test
    public void rejectsAScaledSelfIntersectingStarPolygon() {
        List<LayoutPoint> scaledStar = Arrays.asList(
            LayoutPoint.of(0.0, 1.0e308),
            LayoutPoint.of(5.877852522924732e307, -8.090169943749475e307),
            LayoutPoint.of(-9.510565162951535e307, 3.090169943749474e307),
            LayoutPoint.of(9.510565162951535e307, 3.090169943749474e307),
            LayoutPoint.of(-5.877852522924732e307, -8.090169943749475e307));

        assertThatThrownBy(() -> HullGeometry.of(scaledStar, LayoutPoint.of(0.0, 0.0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void containsVerticesOfANearLimitConvexHull() {
        HullGeometry diamond = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 8.0e307),
            LayoutPoint.of(8.0e307, 0.0),
            LayoutPoint.of(0.0, -8.0e307),
            LayoutPoint.of(-8.0e307, 0.0)), LayoutPoint.of(0.0, 0.0));

        assertThat(diamond.contains(LayoutPoint.of(0.0, 0.0))).isTrue();
        for (LayoutPoint vertex : diamond.exactPolygon()) {
            assertThat(diamond.contains(vertex)).isTrue();
        }
        assertThat(diamond.contains(LayoutPoint.of(9.0e307, 0.0))).isFalse();
    }

    @Test
    public void acceptsMixedMagnitudeFiniteTriangleWithoutLosingOrientation() {
        double huge = Math.scalb(1.0, 996);
        double tiny = Math.scalb(1.0, -85);
        HullGeometry hull = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(huge, 0.0),
            LayoutPoint.of(tiny, tiny)), LayoutPoint.of(0.0, 0.0));

        assertThat(hull.exactPolygon()).hasSize(3);
        for (LayoutPoint vertex : hull.exactPolygon()) {
            assertThat(hull.contains(vertex)).isTrue();
        }
    }

    @Test
    public void acceptsFiniteTriangleJustAboveAbsoluteOrientationThreshold() {
        double huge = Math.scalb(1.0, 1023);
        double tiny = Double.longBitsToDouble(2251800L);
        List<LayoutPoint> triangle = Arrays.asList(
            LayoutPoint.of(tiny, 0.0),
            LayoutPoint.of(huge, huge),
            LayoutPoint.of(0.0, 0.0));

        assertThat(huge * tiny).isGreaterThan(1e-9);
        HullGeometry hull = HullGeometry.of(triangle, LayoutPoint.of(0.0, 0.0));

        assertThat(hull.exactPolygon()).hasSize(3);
        for (LayoutPoint vertex : triangle) {
            assertThat(hull.contains(vertex)).isTrue();
        }
    }

    @Test
    public void doesNotTreatBoundsOnlyEndpointAsSegmentIntersection() throws Exception {
        Method segmentsIntersect = HullGeometry.class.getDeclaredMethod("segmentsIntersect",
            LayoutPoint.class, LayoutPoint.class, LayoutPoint.class, LayoutPoint.class);
        segmentsIntersect.setAccessible(true);
        boolean disjoint = (Boolean) segmentsIntersect.invoke(null,
            LayoutPoint.of(0.0, 0.0), LayoutPoint.of(2.0, 0.0),
            LayoutPoint.of(3.0, 0.0), LayoutPoint.of(1.0, 1.0));
        assertThat(disjoint).isFalse();
        boolean crossing = (Boolean) segmentsIntersect.invoke(null,
            LayoutPoint.of(0.0, 0.0), LayoutPoint.of(4.0, 0.0),
            LayoutPoint.of(2.0, -1.0), LayoutPoint.of(2.0, 1.0));
        assertThat(crossing).isTrue();
        boolean touching = (Boolean) segmentsIntersect.invoke(null,
            LayoutPoint.of(0.0, 0.0), LayoutPoint.of(2.0, 0.0),
            LayoutPoint.of(2.0, 0.0), LayoutPoint.of(3.0, 1.0));
        assertThat(touching).isTrue();
    }

    @Test
    public void rejectsDuplicateProjectedNodeAndEnclosureKeys() {
        ProjectedNode first = node("n1");
        ProjectedNode duplicate = node("n1");
        EnclosureHullKey e1 = hullKey("e1");
        ProjectedEnclosure enclosure = enclosure(e1, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(first.key()), Collections.<EnclosureHullKey>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodePositions =
            Collections.singletonMap(first.key(), LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = Collections.singletonMap(e1, LayoutPoint.of(0.0, 0.0));
        GraphProjection duplicateNodeProjection = projection(Arrays.asList(first, duplicate),
            Collections.singletonList(enclosure), Collections.<ProjectedEdge>emptyList());
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(duplicateNodeProjection,
            LayoutPositions.of(nodePositions, anchors))).isInstanceOf(IllegalArgumentException.class);
        ProjectedEnclosure duplicateEnclosure = enclosure(e1, Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(first.key()), Collections.<EnclosureHullKey>emptyList());
        GraphProjection duplicateEnclosureProjection = projection(Collections.singletonList(first),
            Arrays.asList(enclosure, duplicateEnclosure), Collections.<ProjectedEdge>emptyList());
        assertThatThrownBy(() -> new GraphGeometryEngine().computeHulls(duplicateEnclosureProjection,
            LayoutPositions.of(nodePositions, anchors))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void keepsHugeFiniteCircleMathOverflowSafe() {
        NodeGeometry huge = NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 1e200);
        assertThat(huge.contains(LayoutPoint.of(5e199, 0.0))).isTrue();
        assertThat(huge.contains(LayoutPoint.of(2e200, 0.0))).isFalse();
        assertThat(huge.contains(LayoutPoint.of(7e199, 7e199))).isTrue();
        assertThat(huge.boundaryToward(LayoutPoint.of(2e200, 0.0))).isEqualTo(LayoutPoint.of(1e200, 0.0));
        LayoutPoint diagonal = huge.boundaryToward(LayoutPoint.of(2e200, 2e200));
        assertThat(diagonal.x()).isCloseTo(1e200 * Math.sqrt(0.5), within(1e185));
        assertThat(diagonal.y()).isCloseTo(1e200 * Math.sqrt(0.5), within(1e185));
    }

    @Test
    public void keepsBoundaryTowardFiniteForOppositeSignNearLimitCoordinates() {
        NodeGeometry left = NodeGeometry.of(LayoutPoint.of(-1e308, 0.0), 1e307);
        assertThat(left.boundaryToward(LayoutPoint.of(1e308, 0.0)))
            .isEqualTo(LayoutPoint.of(-9e307, 0.0));

        NodeGeometry right = NodeGeometry.of(LayoutPoint.of(1e308, 0.0), 1e307);
        assertThat(right.boundaryToward(LayoutPoint.of(-1e308, 0.0)))
            .isEqualTo(LayoutPoint.of(9e307, 0.0));
    }

    @Test
    public void preservesRepresentableMinorBoundaryDisplacementOnANearLimitRay() {
        NodeGeometry node = NodeGeometry.of(LayoutPoint.of(-1.0e308, 0.0), 1.0e307);

        LayoutPoint boundary = node.boundaryToward(LayoutPoint.of(1.0e308, 1.0e-100));

        assertThat(boundary.x()).isEqualTo(-9.0e307);
        assertThat(boundary.y()).isCloseTo(5.0e-102, within(1.0e-116));
    }

    @Test
    public void preservesSubtractionResidualAfterBoundaryCenterCancellation() {
        double centerY = Math.scalb(1.0, -100);
        double radius = Math.scalb(1.0, 900);
        double towardX = Math.scalb(1.0, 1000);
        NodeGeometry node = NodeGeometry.of(LayoutPoint.of(0.0, centerY), radius);

        LayoutPoint boundary = node.boundaryToward(LayoutPoint.of(towardX, -1.0));

        assertThat(boundary.x()).isEqualTo(Math.scalb(1.0, 900));
        assertThat(boundary.y()).isEqualTo(Math.scalb(-1.0, -200));
    }

    @Test
    public void preservesDominantAxisLengthResidualAfterBoundaryCancellation() {
        double u = Math.scalb(1.0, -100);
        NodeGeometry node = NodeGeometry.of(LayoutPoint.of(0.0, u), u);

        LayoutPoint boundary = node.boundaryToward(
            LayoutPoint.of(Math.scalb(1.0, -200), -1.0));

        assertThat(boundary.x()).isEqualTo(Math.scalb(1.0, -300));
        assertThat(boundary.y()).isEqualTo(Math.scalb(1.0, -501));
    }

    @Test
    public void preservesDominantBoundaryCorrectionAcrossExtremeExponents() {
        double radius = Math.scalb(1.0, 1001);
        NodeGeometry node = NodeGeometry.of(LayoutPoint.of(-radius, 0.0), radius);

        LayoutPoint boundary = node.boundaryToward(
            LayoutPoint.of(0.0, Math.scalb(1.0, 466)));

        assertThat(boundary.x()).isEqualTo(Math.scalb(-1.0, -70));
        assertThat(boundary.y()).isEqualTo(Math.scalb(1.0, 466));
    }

    @Test
    public void preservesSubnormalDominantBoundaryCorrectionAfterCancellation() {
        double radius = Math.scalb(1.0, 1000);
        NodeGeometry node = NodeGeometry.of(LayoutPoint.of(-radius, 0.0), radius);

        LayoutPoint boundary = node.boundaryToward(
            LayoutPoint.of(0.0, Math.scalb(19.0 / 16.0, -37)));

        assertThat(boundary.x()).isEqualTo(-Double.MIN_VALUE);
        assertThat(boundary.y()).isEqualTo(Math.scalb(19.0 / 16.0, -37));
    }

    @Test
    public void preservesNormalBoundaryCoordinateWhileRetainingSubnormalCorrection() {
        NodeGeometry node = NodeGeometry.of(
            LayoutPoint.of(-0x1.e9e1806d3baccp-17, -0x1.5e1c60bc36cd6p-192),
            0x1.e9e1806d3baccp-17);

        LayoutPoint boundary = node.boundaryToward(
            LayoutPoint.of(0x1.0p1000, -0x1.6a80009d77199p498));

        assertThat(boundary.x()).isEqualTo(-0x1.eb21516ee14e9p-1021);
        assertThat(boundary.y()).isEqualTo(-0x1.5e1c60bc36cd6p-192);
    }

    @Test
    public void roundsFiniteBoundaryRayToNearestEvenCoordinate() {
        NodeGeometry node = NodeGeometry.of(
            LayoutPoint.of(-2.30665597377219E56, -2.2117294275241294E-19),
            1.0283265339240514E57);

        LayoutPoint boundary = node.boundaryToward(
            LayoutPoint.of(7.09268585234678E-75, -2.3275574432766924E12));

        assertThat(Double.doubleToRawLongBits(boundary.x())).isEqualTo(0x4bc043fc003baf8bL);
        assertThat(Double.doubleToRawLongBits(boundary.y())).isEqualTo(0xc2a2dfe8bc8ed4feL);
    }

    @Test
    public void limitsEverySmoothPathTangentToFourWorldUnits() {
        HullGeometry rectangle = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(100.0, 0.0),
            LayoutPoint.of(100.0, 20.0),
            LayoutPoint.of(0.0, 20.0)), LayoutPoint.of(0.0, 0.0));
        assertTangentsWithinFourWorldUnits(rectangle);

        HullGeometry huge = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(1e160, 0.0),
            LayoutPoint.of(1e160, 1e160),
            LayoutPoint.of(0.0, 1e160)), LayoutPoint.of(0.0, 0.0));
        assertTangentsWithinFourWorldUnits(huge);
        PathIterator hugeIterator = huge.smoothPath().getPathIterator(null);
        double[] hugeCoords = new double[6];
        assertThat(hugeIterator.currentSegment(hugeCoords)).isEqualTo(PathIterator.SEG_MOVETO);
        assertThat(LayoutPoint.of(hugeCoords[0], hugeCoords[1])).isEqualTo(LayoutPoint.of(0.0, 4.0));
        hugeIterator.next();
        assertThat(hugeIterator.currentSegment(hugeCoords)).isEqualTo(PathIterator.SEG_QUADTO);
        assertThat(LayoutPoint.of(hugeCoords[2], hugeCoords[3])).isEqualTo(LayoutPoint.of(4.0, 0.0));
    }

    @Test
    public void keepsSmoothTangentsWithinFourWhenOneUlpExceedsFour() {
        double offset = Math.nextUp(Math.scalb(1.0, 55));
        assertThat(Math.ulp(offset)).isEqualTo(8.0);
        HullGeometry rectangle = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(offset, 0.0),
            LayoutPoint.of(offset + 80.0, 0.0),
            LayoutPoint.of(offset + 80.0, 20.0),
            LayoutPoint.of(offset, 20.0)), LayoutPoint.of(offset, 10.0));

        assertTangentsWithinFourWorldUnits(rectangle);
    }

    @Test
    public void findsRepresentableInteriorProjectionAfterLargeProductCancellation() {
        double a = 3.6519210675856295e120;
        double targetY = Math.nextUp(-a);
        HullGeometry triangle = HullGeometry.of(Arrays.asList(
            LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(a, a),
            LayoutPoint.of(-a, a)), LayoutPoint.of(0.0, 0.0));
        double expectedCoordinate = (a + targetY) / 2.0;

        assertThat(triangle.nearestBoundaryPoint(LayoutPoint.of(a, targetY)))
            .isEqualTo(LayoutPoint.of(expectedCoordinate, expectedCoordinate));
    }

    @Test
    public void findsNearestBoundaryForFarFiniteTargets() {
        HullGeometry squareHull = HullGeometry.of(square(), LayoutPoint.of(0.0, 0.0));
        assertThat(squareHull.nearestBoundaryPoint(LayoutPoint.of(1e200, 1e200)))
            .isEqualTo(LayoutPoint.of(10.0, 10.0));
    }

    @Test
    public void resolvesNearestBoundaryNearTiesToTheEarlierCanonicalEdge() {
        HullGeometry squareHull = HullGeometry.of(square(), LayoutPoint.of(0.0, 0.0));
        assertThat(squareHull.nearestBoundaryPoint(LayoutPoint.of(5.0, 5.00000000001)))
            .isEqualTo(LayoutPoint.of(5.0, 0.0));
    }

    private static void assertTangentsWithinFourWorldUnits(final HullGeometry hull) {
        PathIterator iterator = hull.smoothPath().getPathIterator(null);
        double[] coords = new double[6];
        double currentX = 0.0;
        double currentY = 0.0;
        int quadraticCount = 0;
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                currentX = coords[0];
                currentY = coords[1];
            }
            else if (type == PathIterator.SEG_QUADTO) {
                assertThat(Math.hypot(currentX - coords[0], currentY - coords[1]))
                    .isLessThanOrEqualTo(4.0);
                assertThat(Math.hypot(coords[2] - coords[0], coords[3] - coords[1]))
                    .isLessThanOrEqualTo(4.0);
                currentX = coords[2];
                currentY = coords[3];
                quadraticCount++;
            }
            iterator.next();
        }
        assertThat(quadraticCount).isEqualTo(hull.exactPolygon().size());
    }

    private static GraphGeometry compute(final GraphProjection projection, final LayoutPositions positions) {
        return new GraphGeometryEngine().computeHulls(projection, positions);
    }

    private static void assertCircleInside(final NodeGeometry node, final HullGeometry hull) {
        for (int degrees = 0; degrees < 360; degrees += 5) {
            double angle = Math.toRadians(degrees);
            LayoutPoint sample = LayoutPoint.of(node.center().x() + node.radius() * Math.cos(angle),
                node.center().y() + node.radius() * Math.sin(angle));
            assertThat(hull.contains(sample)).as("circle sample at %d degrees", degrees).isTrue();
        }
    }

    private static List<String> segmentSignature(final Shape shape) {
        List<String> segments = new ArrayList<String>();
        PathIterator iterator = shape.getPathIterator(null);
        double[] coords = new double[6];
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            StringBuilder signature = new StringBuilder();
            signature.append(type);
            int count = 0;
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                count = 2;
            }
            else if (type == PathIterator.SEG_QUADTO) {
                count = 4;
            }
            else if (type == PathIterator.SEG_CUBICTO) {
                count = 6;
            }
            for (int index = 0; index < count; index++) {
                signature.append(',').append(coords[index]);
            }
            segments.add(signature.toString());
            iterator.next();
        }
        return segments;
    }

    private static List<LayoutPoint> samplePath(final Shape shape) {
        List<LayoutPoint> samples = new ArrayList<LayoutPoint>();
        PathIterator iterator = shape.getPathIterator(null, 0.5);
        double[] coords = new double[6];
        double firstX = 0.0;
        double firstY = 0.0;
        double previousX = 0.0;
        double previousY = 0.0;
        boolean hasPrevious = false;
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                firstX = coords[0];
                firstY = coords[1];
                previousX = coords[0];
                previousY = coords[1];
                samples.add(LayoutPoint.of(coords[0], coords[1]));
                hasPrevious = true;
            }
            else if (type == PathIterator.SEG_LINETO) {
                samples.add(LayoutPoint.of(coords[0], coords[1]));
                samples.add(LayoutPoint.of((previousX + coords[0]) * 0.5, (previousY + coords[1]) * 0.5));
                previousX = coords[0];
                previousY = coords[1];
            }
            else if (type == PathIterator.SEG_CLOSE && hasPrevious) {
                samples.add(LayoutPoint.of((previousX + firstX) * 0.5, (previousY + firstY) * 0.5));
            }
            iterator.next();
        }
        return samples;
    }

    private static List<LayoutPoint> square() {
        return Arrays.asList(LayoutPoint.of(0.0, 0.0), LayoutPoint.of(10.0, 0.0), LayoutPoint.of(10.0, 10.0),
            LayoutPoint.of(0.0, 10.0));
    }

    private static GraphProjection projection(final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<ProjectedEdge> edges) {
        return GraphProjection.projected(1, nodes, enclosures, edges, Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static ProjectedNode node(final String id) {
        return ProjectedNode.of(nodeKey(id), SafeNodeLabel.of(id, id), MAP_NAME, false);
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(source(id));
    }

    private static EnclosureKey enclosureKey(final String id) {
        return EnclosureKey.of(source(id));
    }

    private static EnclosureHullKey hullKey(final String... ids) {
        List<EnclosureKey> endpoints = new ArrayList<EnclosureKey>();
        for (String id : ids) {
            endpoints.add(enclosureKey(id));
        }
        return EnclosureHullKey.of(endpoints);
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hullKey,
            final Optional<EnclosureHullKey> parent, final List<ProjectedNodeKey> directNodes,
            final List<EnclosureHullKey> directEnclosures) {
        List<EnclosureKey> endpoints = hullKey.endpointKeys();
        List<SafeNodeLabel> labels = new ArrayList<SafeNodeLabel>();
        for (EnclosureKey endpoint : endpoints) {
            String id = endpoint.source().persistedReference().get().nodeId().value();
            labels.add(SafeNodeLabel.of(id, id));
        }
        return ProjectedEnclosure.of(hullKey, endpoints, labels, MAP_NAME, parent, directNodes, directEnclosures,
            true, BoundaryTier.SUBTLE);
    }

    private static ProjectedEdge directedEdge(final String from, final String to, final int occurrence) {
        SourceNodeKey source = source(from);
        ProjectedEndpointKey fromEndpoint = ProjectedEndpointKey.ofNode(nodeKey(from));
        ProjectedEndpointKey toEndpoint = ProjectedEndpointKey.ofNode(nodeKey(to));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, NodeReference.of(MAP, PersistedNodeId.of(to)),
            false, true, "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor),
            fromEndpoint, toEndpoint);
        return ProjectedEdge.of(ProjectedEdgeKey.of(fromEndpoint, toEndpoint),
            Collections.singletonList(contributor));
    }

    private static SourceNodeKey source(final String id) {
        return SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id)));
    }
}
