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
    public void exposeEveryVisibleNodeAndEnclosureAnchor() {
        GraphProjection projection = baseline(1);

        LayoutFrame frame = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());

        assertCoverage(frame, projection);
    }

    @Test
    public void acceptParallelProjectedEdgesWithoutLosingPositionCoverage() {
        GraphProjection projection = withParallelProjectedEdges(1);

        LayoutFrame frame = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());

        assertCoverage(frame, projection);
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
        List<PinProjection> lowPins = Collections.singletonList(pin(neighbor, 0.0, 0.0));
        List<PinProjection> highPins = Arrays.asList(pin(neighbor, 0.0, 0.0), pin(key(MAP_ONE, "target-one"),
            100.0, 100.0), pin(key(MAP_ONE, "target-two"), 100.0, -100.0));

        LayoutFrame low = frameAfterOneStep(WORKSPACE_ONE, lowProminence, lowPins);
        LayoutFrame high = frameAfterOneStep(WORKSPACE_ONE, highProminence, highPins);

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

    private static GraphProjection withParallelProjectedEdges(long generation) {
        ProjectedNode aOne = node(MAP_ONE, "a-one");
        ProjectedNode bOne = node(MAP_TWO, "b-one");
        ProjectedEnclosure root = enclosure(MAP_ONE, "root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(aOne.key()), Collections.<EnclosureHullKey>emptyList(), true);
        ProjectedEnclosure otherRoot = enclosure(MAP_TWO, "other-root", Optional.<EnclosureHullKey>empty(),
            Collections.singletonList(bOne.key()), Collections.<EnclosureHullKey>emptyList(), true);
        return projection(generation, Arrays.asList(aOne, bOne), Arrays.asList(root, otherRoot),
            Collections.singletonList(parallelEdge(0, 1, aOne.key(), bOne.key())));
    }

    private static ProjectedEdge parallelEdge(int firstOccurrence, int secondOccurrence,
            ProjectedNodeKey source, ProjectedNodeKey target) {
        ProjectedEdge first = edge(firstOccurrence, source, target);
        ProjectedEdge second = edge(secondOccurrence, source, target);
        return ProjectedEdge.of(first.key(), Arrays.asList(first.contributors().get(0), second.contributors().get(0)));
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
