package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class TypedForcesShould {
    private static final WorkspaceId WORKSPACE_ONE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000111");
    private static final WorkspaceId WORKSPACE_TWO =
        WorkspaceId.of("00000000-0000-0000-0000-000000000222");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void produceIdenticalFramesForEqualRequests() {
        GraphProjection projection = baseline(1);

        LayoutFrame first = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());
        LayoutFrame second = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());

        assertThat(first.positions()).isEqualTo(second.positions());
    }

    @Test
    public void deriveDistinctDeterministicSeedsForDifferentWorkspaces() {
        GraphProjection projection = baseline(1);

        LayoutFrame first = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());
        LayoutFrame second = frameAfterOneStep(WORKSPACE_TWO, projection, Collections.<PinProjection>emptyList());

        assertThat(first.positions()).isNotEqualTo(second.positions());
    }

    @Test
    public void spreadSmallWorkspaceNodePositionsBeyondOneWorldUnitBeforeStepping() {
        GraphProjection projection = baseline(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            assertThat(greatestPairwiseDistance(frame.positions().nodes().values())).isGreaterThan(1.0);
        }
    }

    @Test
    public void exposeEveryVisibleNodeAndEnclosureAnchor() {
        GraphProjection projection = baseline(1);

        LayoutFrame frame = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());

        assertCoverage(frame, projection);
    }

    @Test
    public void moveAnUnpinnedParticleThroughTheGraphStreamSolver() {
        ProjectedNode firstNode = node(MAP_ONE, "solver-one");
        ProjectedNode secondNode = node(MAP_TWO, "solver-two");
        GraphProjection projection = projection(1, Arrays.asList(firstNode, secondNode),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList());
        List<PinProjection> positioningPins = Arrays.asList(pin(firstNode.key(), 0.0, 0.0),
            pin(secondNode.key(), 20.0, 0.0));
        List<PinProjection> activePins = Collections.singletonList(pin(secondNode.key(), 20.0, 0.0));

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, projection, projection, positioningPins));
            LayoutFrame before = engine.apply(request(WORKSPACE_ONE, projection, projection, activePins));
            LayoutFrame after = engine.step();

            assertThat(after.failed()).isFalse();
            assertThat(distance(before.positions().nodes().get(firstNode.key()),
                after.positions().nodes().get(firstNode.key()))).isGreaterThan(0.0);
        }
    }

    @Test
    public void acceptRelationshipAndContainmentEdgesBetweenTheSameParticles() {
        GraphProjection projection = withParallelGraphEdges(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame applied = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            LayoutFrame stepped = engine.step();

            assertThat(applied.failed()).isFalse();
            assertThat(stepped.failed()).isFalse();
            assertCoverage(stepped, projection);
        }
    }

    @Test
    public void retainDirectContainmentAndHierarchyAnchorsAcrossSteps() {
        GraphProjection projection = baseline(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, projection, projection, Collections.<PinProjection>emptyList()));
            LayoutFrame first = engine.step();
            LayoutFrame second = engine.step();

            assertCoverage(first, projection);
            assertCoverage(second, projection);
            assertThat(second.positions().anchors()).containsKeys(rootHull(), childHull());
        }
    }

    @Test
    public void moveAContainmentAnchorCloserToItsPinnedDirectNode() {
        GraphProjection contained = containmentProjection(1, true);
        GraphProjection detached = containmentProjection(1, false);
        ProjectedNodeKey directNode = key(MAP_ONE, "contained-node");
        LayoutPoint pinPosition = LayoutPoint.of(100.0, 0.0);
        List<PinProjection> pins = Collections.singletonList(pin(directNode, pinPosition.x(), pinPosition.y()));

        LayoutFrame containedFrame = frameAfterOneStep(WORKSPACE_ONE, contained, pins);
        LayoutFrame detachedFrame = frameAfterOneStep(WORKSPACE_ONE, detached, pins);

        assertThat(distance(containedFrame.positions().anchors().get(containmentHull()), pinPosition))
            .isLessThan(distance(detachedFrame.positions().anchors().get(containmentHull()), pinPosition));
    }

    @Test
    public void reduceAnchorDistanceChangeWhenEnclosuresHaveAHierarchyLink() {
        GraphProjection nested = hierarchyProjection(1, true);
        GraphProjection peers = hierarchyProjection(1, false);
        List<PinProjection> pins = Arrays.asList(pin(key(MAP_ONE, "hierarchy-parent-node"), 0.0, 0.0),
            pin(key(MAP_ONE, "hierarchy-child-node"), 24.0, 0.0));
        double nestedChange = anchorDistanceChangeAfterOneStep(WORKSPACE_ONE, nested, pins,
            hierarchyParentHull(), hierarchyChildHull());
        double peerChange = anchorDistanceChangeAfterOneStep(WORKSPACE_ONE, peers, pins,
            hierarchyParentHull(), hierarchyChildHull());

        assertThat(nestedChange).isLessThan(peerChange);
    }

    @Test
    public void returnToBaselineCoverageAfterOneHundredAddRemoveCycles() {
        GraphProjection baseline = baseline(1);
        GraphProjection expanded = expanded(2);
        LayoutFrame finalFrame = null;

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, baseline, baseline, Collections.<PinProjection>emptyList()));
            engine.step();
            for (int cycle = 0; cycle < 100; cycle++) {
                engine.apply(request(WORKSPACE_ONE, baseline, expanded, Collections.<PinProjection>emptyList()));
                engine.step();
                engine.apply(request(WORKSPACE_ONE, expanded, baseline, Collections.<PinProjection>emptyList()));
                finalFrame = engine.step();
            }
        }

        assertThat(finalFrame).isNotNull();
        assertCoverage(finalFrame, baseline);
    }

    @Test
    public void capAggregateCrossMapFanOutDisplacementOncePerParticle() {
        GraphProjection projection = crossMapFanOut(1);
        ProjectedNodeKey center = key(MAP_ONE, "center");

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame before = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            LayoutFrame after = engine.step();

            assertThat(distance(before.positions().nodes().get(center), after.positions().nodes().get(center)))
                .isLessThanOrEqualTo(0.0050000001);
        }
    }

    @Test
    public void increaseSeparationForHigherProminence() {
        GraphProjection lowProminence = prominenceProjection(1, false);
        GraphProjection highProminence = prominenceProjection(1, true);
        ProjectedNodeKey source = key(MAP_ONE, "source");
        ProjectedNodeKey neighbor = key(MAP_ONE, "neighbor");

        LayoutFrame low = frameAfterPositioningAndOneStep(WORKSPACE_ONE, lowProminence,
            Arrays.asList(pin(source, 24.0, 0.0), pin(neighbor, 0.0, 0.0)),
            Collections.singletonList(pin(neighbor, 0.0, 0.0)));
        LayoutFrame high = frameAfterPositioningAndOneStep(WORKSPACE_ONE, highProminence,
            Arrays.asList(pin(source, 24.0, 0.0), pin(neighbor, 0.0, 0.0),
                pin(key(MAP_ONE, "target-one"), 100.0, 100.0),
                pin(key(MAP_ONE, "target-two"), 100.0, -100.0)),
            Arrays.asList(pin(neighbor, 0.0, 0.0), pin(key(MAP_ONE, "target-one"), 100.0, 100.0),
                pin(key(MAP_ONE, "target-two"), 100.0, -100.0)));

        assertThat(distance(high.positions().nodes().get(source), high.positions().nodes().get(neighbor)))
            .isGreaterThan(distance(low.positions().nodes().get(source), low.positions().nodes().get(neighbor)));
    }

    @Test
    public void keepAnActivePinnedNeighborAtItsExactLayoutCoordinates() {
        GraphProjection projection = baseline(1);
        ProjectedNodeKey pinned = key(MAP_TWO, "b-one");
        PinProjection activePin = pin(pinned, 12.5, -9.75);

        LayoutFrame frame = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.singletonList(activePin));

        assertThat(frame.positions().nodes().get(pinned)).isEqualTo(LayoutPoint.of(12.5, -9.75));
    }

    private static LayoutFrame frameAfterOneStep(WorkspaceId workspace, GraphProjection projection,
            List<PinProjection> pins) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(workspace, projection, projection, pins));
            return engine.step();
        }
    }

    private static LayoutFrame frameAfterPositioningAndOneStep(WorkspaceId workspace, GraphProjection projection,
            List<PinProjection> positioningPins, List<PinProjection> activePins) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(workspace, projection, projection, positioningPins));
            engine.apply(request(workspace, projection, projection, activePins));
            return engine.step();
        }
    }

    private static double anchorDistanceChangeAfterOneStep(WorkspaceId workspace, GraphProjection projection,
            List<PinProjection> pins, EnclosureHullKey first, EnclosureHullKey second) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame before = engine.apply(request(workspace, projection, projection, pins));
            LayoutFrame after = engine.step();
            return distance(after.positions().anchors().get(first), after.positions().anchors().get(second))
                - distance(before.positions().anchors().get(first), before.positions().anchors().get(second));
        }
    }

    private static double greatestPairwiseDistance(Iterable<LayoutPoint> points) {
        List<LayoutPoint> values = new ArrayList<LayoutPoint>();
        for (LayoutPoint point : points) {
            values.add(point);
        }
        double greatest = 0.0;
        for (int first = 0; first < values.size(); first++) {
            for (int second = first + 1; second < values.size(); second++) {
                greatest = Math.max(greatest, distance(values.get(first), values.get(second)));
            }
        }
        return greatest;
    }


    private static LayoutRequest request(WorkspaceId workspace, GraphProjection before, GraphProjection after,
            List<PinProjection> pins) {
        return LayoutRequest.of(workspace, after, ProjectionDiff.between(before, after), pins);
    }

    private static void assertCoverage(LayoutFrame frame, GraphProjection projection) {
        List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) {
            nodes.add(node.key());
        }
        List<EnclosureHullKey> anchors = new ArrayList<EnclosureHullKey>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.add(enclosure.hullKey());
        }
        assertThat(frame.positions().nodes().keySet()).containsExactlyElementsOf(nodes);
        assertThat(frame.positions().anchors().keySet()).containsExactlyElementsOf(anchors);
    }

    private static GraphProjection baseline(long generation) {
        ProjectedNode aOne = node(MAP_ONE, "a-one");
        ProjectedNode aTwo = node(MAP_ONE, "a-two");
        ProjectedNode bOne = node(MAP_TWO, "b-one");
        ProjectedEnclosure root = enclosure(MAP_ONE, "root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(aOne.key()), Collections.singletonList(childHull()), true);
        ProjectedEnclosure child = enclosure(MAP_ONE, "child", Optional.of(root.hullKey()),
            Collections.singletonList(aTwo.key()), Collections.<EnclosureHullKey>emptyList(), false);
        ProjectedEnclosure otherRoot = enclosure(MAP_TWO, "other-root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(bOne.key()), Collections.<EnclosureHullKey>emptyList(), true);
        return projection(generation, Arrays.asList(aOne, aTwo, bOne), Arrays.asList(root, child, otherRoot),
            Collections.singletonList(edge(0, aOne.key(), bOne.key())));
    }

    private static GraphProjection expanded(long generation) {
        ProjectedNode aOne = node(MAP_ONE, "a-one");
        ProjectedNode aTwo = node(MAP_ONE, "a-two");
        ProjectedNode aExtra = node(MAP_ONE, "a-extra");
        ProjectedNode bOne = node(MAP_TWO, "b-one");
        ProjectedEnclosure root = enclosure(MAP_ONE, "root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(aOne.key()), Arrays.asList(childHull(), extraHull()), true);
        ProjectedEnclosure child = enclosure(MAP_ONE, "child", Optional.of(root.hullKey()),
            Collections.singletonList(aTwo.key()), Collections.<EnclosureHullKey>emptyList(), false);
        ProjectedEnclosure extra = enclosure(MAP_ONE, "extra", Optional.of(root.hullKey()),
            Collections.singletonList(aExtra.key()), Collections.<EnclosureHullKey>emptyList(), false);
        ProjectedEnclosure otherRoot = enclosure(MAP_TWO, "other-root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(bOne.key()), Collections.<EnclosureHullKey>emptyList(), true);
        return projection(generation, Arrays.asList(aOne, aTwo, aExtra, bOne),
            Arrays.asList(root, child, extra, otherRoot), Arrays.asList(edge(0, aOne.key(), bOne.key()),
                edge(1, aExtra.key(), bOne.key())));
    }

    private static GraphProjection withParallelGraphEdges(long generation) {
        ProjectedNode directNode = node(MAP_ONE, "parallel-node");
        ProjectedEnclosure enclosure = enclosure(MAP_ONE, "parallel-root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(directNode.key()), Collections.<EnclosureHullKey>emptyList(), true);
        ProjectedEndpointKey anchor = ProjectedEndpointKey.ofEnclosure(enclosure.endpointKeys().get(0));
        ProjectedEndpointKey node = ProjectedEndpointKey.ofNode(directNode.key());
        return projection(generation, Collections.singletonList(directNode), Collections.singletonList(enclosure),
            Collections.singletonList(connectorEdge(0, anchor, node)));
    }

    private static GraphProjection containmentProjection(long generation, boolean includeContainment) {
        ProjectedNode directNode = node(MAP_ONE, "contained-node");
        ProjectedEnclosure enclosure = enclosure(MAP_ONE, "containment-root",
            Optional.<EnclosureHullKey>empty(), includeContainment ? Collections.singletonList(directNode.key())
                : Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), true);
        return projection(generation, Collections.singletonList(directNode), Collections.singletonList(enclosure),
            Collections.<ProjectedEdge>emptyList());
    }

    private static GraphProjection hierarchyProjection(long generation, boolean includeHierarchy) {
        ProjectedNode parentNode = node(MAP_ONE, "hierarchy-parent-node");
        ProjectedNode childNode = node(MAP_ONE, "hierarchy-child-node");
        ProjectedEnclosure parent = enclosure(MAP_ONE, "hierarchy-parent", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(parentNode.key()), Collections.<EnclosureHullKey>emptyList(), true);
        ProjectedEnclosure child = enclosure(MAP_ONE, "hierarchy-child",
            includeHierarchy ? Optional.of(parent.hullKey()) : Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(childNode.key()), Collections.<EnclosureHullKey>emptyList(), false);
        return projection(generation, Arrays.asList(parentNode, childNode), Arrays.asList(parent, child),
            Collections.<ProjectedEdge>emptyList());
    }

    private static GraphProjection crossMapFanOut(long generation) {
        ProjectedNode center = node(MAP_ONE, "center");
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        nodes.add(center);
        for (int index = 0; index < 8; index++) {
            ProjectedNode leaf = node(MAP_TWO, "leaf-" + index);
            nodes.add(leaf);
            edges.add(edge(index, center.key(), leaf.key()));
        }
        return projection(generation, nodes, Collections.<ProjectedEnclosure>emptyList(), edges);
    }

    private static GraphProjection prominenceProjection(long generation, boolean highProminence) {
        ProjectedNode source = node(MAP_ONE, "source");
        ProjectedNode neighbor = node(MAP_ONE, "neighbor");
        ProjectedNode targetOne = node(MAP_ONE, "target-one");
        ProjectedNode targetTwo = node(MAP_ONE, "target-two");
        List<ProjectedNode> nodes = highProminence
            ? Arrays.asList(source, neighbor, targetOne, targetTwo)
            : Arrays.asList(source, neighbor);
        List<ProjectedEdge> edges = highProminence
            ? Arrays.asList(edge(0, source.key(), targetOne.key()), edge(1, source.key(), targetTwo.key()))
            : Collections.<ProjectedEdge>emptyList();
        return projection(generation, nodes, Collections.<ProjectedEnclosure>emptyList(), edges);
    }

    private static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges) {
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedNode node(MapReferenceId map, String id) {
        ProjectedNodeKey key = key(map, id);
        return ProjectedNode.of(key, SafeNodeLabel.of(id, id), "Map " + map.value(), false);
    }

    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(reference(map, id)));
    }

    private static ProjectedEnclosure enclosure(MapReferenceId map, String id, Optional<EnclosureHullKey> parent,
            List<ProjectedNodeKey> directNodes, List<EnclosureHullKey> directEnclosures, boolean mapRoot) {
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)));
        EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(endpoint));
        return ProjectedEnclosure.of(hull, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map " + map.value(), parent, directNodes,
            directEnclosures, mapRoot, BoundaryTier.SUBTLE);
    }

    private static ProjectedEdge connectorEdge(int occurrence, ProjectedEndpointKey source,
            ProjectedEndpointKey target) {
        SourceNodeKey sourceKey = endpointSource(source);
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(sourceKey, endpointReference(target), false, true,
            "source", "middle", "target");
        ConnectorSnapshot snapshot = ConnectorSnapshot.of(occurrence, descriptor);
        return ProjectedEdge.of(ProjectedEdgeKey.of(source, target),
            Collections.singletonList(EdgeContributor.nativeConnector(snapshot, source, target)));
    }

    private static SourceNodeKey endpointSource(ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            return endpoint.node().get().source();
        }
        return endpoint.enclosure().get().source();
    }

    private static NodeReference endpointReference(ProjectedEndpointKey endpoint) {
        return endpointSource(endpoint).persistedReference().get();
    }

    private static ProjectedEdge edge(int occurrence, ProjectedNodeKey source, ProjectedNodeKey target) {
        ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofNode(source);
        ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofNode(target);
        EdgeContributor contributor;
        if (source.mapReferenceId().equals(target.mapReferenceId())) {
            ConnectorDescriptor descriptor = ConnectorDescriptor.of(source.source(),
                target.source().persistedReference().get(), false, true, "source", "middle", "target");
            contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor),
                sourceEndpoint, targetEndpoint);
        }
        else {
            long sequence = occurrence + 1L;
            GraphRelationshipRecord relationship = GraphRelationshipRecord.of(RelationshipId.of(String.format(
                "20000000-0000-0000-0000-%012d", Long.valueOf(sequence))), sequence,
                source.source().persistedReference().get(), target.source().persistedReference().get(),
                RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
            contributor = EdgeContributor.graphRelationship(relationship, sourceEndpoint, targetEndpoint);
        }
        return ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(contributor));
    }

    private static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        PinRecord record = PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList());
        return PinProjection.active(record, node);
    }

    private static EnclosureHullKey containmentHull() {
        return hull(MAP_ONE, "containment-root");
    }

    private static EnclosureHullKey hierarchyParentHull() {
        return hull(MAP_ONE, "hierarchy-parent");
    }

    private static EnclosureHullKey hierarchyChildHull() {
        return hull(MAP_ONE, "hierarchy-child");
    }

    private static EnclosureHullKey rootHull() {
        return hull(MAP_ONE, "root");
    }

    private static EnclosureHullKey childHull() {
        return hull(MAP_ONE, "child");
    }

    private static EnclosureHullKey extraHull() {
        return hull(MAP_ONE, "extra");
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)))));
    }

    private static NodeReference reference(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static double distance(LayoutPoint first, LayoutPoint second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        return Math.sqrt(x * x + y * y);
    }
}
