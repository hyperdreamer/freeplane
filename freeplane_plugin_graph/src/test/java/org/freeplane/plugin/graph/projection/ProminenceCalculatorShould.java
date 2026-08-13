package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class ProminenceCalculatorShould {
    private static final MapReferenceId MAP = mapId("00000000-0000-0000-0000-000000000001");

    @Test
    public void countDirectedOutgoingTargetsOnlyForTheSourceEndpoint() {
        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.<ProjectedEnclosure>emptyList(),
            Collections.singletonList(directedEdge("a", "b")));

        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(prominence.get(nodeKey("b")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void countBidirectionalEdgesForBothEndpoints() {
        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.<ProjectedEnclosure>emptyList(),
            Collections.singletonList(bidirectionalEdge("a", "b")));

        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(prominence.get(nodeKey("b")).visibleOutgoingTargets()).isEqualTo(1);
    }

    @Test
    public void countUndirectedEdgesForNeitherEndpoint() {
        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.<ProjectedEnclosure>emptyList(),
            Collections.singletonList(undirectedEdge("a", "b")));

        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isZero();
        assertThat(prominence.get(nodeKey("b")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void rejectIdenticalProjectedEdgeEndpointsBeforeAnySelfContributorReachesTheCalculator() {
        assertThatThrownBy(() -> ProjectedEdgeKey.of(endpoint(source("a")), endpoint(source("a"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void countDuplicateContributorsReachingOneTargetOnce() {
        SourceNodeKey a = source("a");
        SourceNodeKey b = source("b");
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(endpoint(a), endpoint(b)), Arrays.asList(
            nativeContributor(0, a, reference("b"), false, true),
            nativeContributor(1, a, reference("b"), false, true)));

        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.<ProjectedEnclosure>emptyList(),
            Collections.singletonList(edge));

        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(prominence.get(nodeKey("b")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void countTwoCollapsedAncestorsSharingOneVisibleBoundaryOnceWhileBothRemainAddressable() {
        EnclosureKey ancestorOne = EnclosureKey.of(source("ancestor-one"));
        EnclosureKey ancestorTwo = EnclosureKey.of(source("ancestor-two"));
        ProjectedEnclosure collapsed = ProjectedEnclosure.of(
            EnclosureHullKey.of(Arrays.asList(ancestorOne, ancestorTwo)),
            Arrays.asList(ancestorOne, ancestorTwo),
            Arrays.asList(SafeNodeLabel.of("ancestor-one", "ancestor-one"),
                SafeNodeLabel.of("ancestor-two", "ancestor-two")),
            "Map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);

        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.singletonList(collapsed),
            Arrays.asList(directedToEnclosureEdge("a", ancestorOne), directedToEnclosureEdge("a", ancestorTwo)));

        assertThat(collapsed.endpointKeys()).containsExactly(ancestorOne, ancestorTwo);
        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(prominence.get(nodeKey("b")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void skipEnclosureTargetsAbsentFromTheSuppliedEnclosuresWithoutThrowing() {
        EnclosureKey missing = EnclosureKey.of(source("missing-ancestor"));

        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.singletonList(enclosure("other-ancestor")),
            Collections.singletonList(directedToEnclosureEdge("a", missing)));

        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isZero();
        assertThat(prominence.get(nodeKey("b")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void countActiveGraphGroupRootsLikeOrdinaryNodes() {
        NodeSnapshot group = node(MAP, "group", true, true, false);
        NodeSnapshot target = node(MAP, "target", true, false, false);
        NodeSnapshot root = node(MAP, "root", false, false, false, group, target);
        MapSnapshot snapshot = map(MAP, 1, root).withConnectors(Collections.singletonList(
            connector(0, source(MAP, "group"), reference(MAP, "target"), false, true)));

        GraphProjection projection = new ProjectionEngine().projectStructure(1,
            workspace(registration(MAP, 1, true)), Collections.singletonList(snapshot));

        ProjectedNode groupNode = projection.nodes().get(0);
        assertThat(groupNode.graphGroup()).isTrue();
        Map<ProjectedNodeKey, NodeProminence> prominence = projection.prominence();
        List<ProjectedNodeKey> expectedKeys = new ArrayList<ProjectedNodeKey>();
        for (ProjectedNode value : projection.nodes()) {
            expectedKeys.add(value.key());
        }
        assertThat(new ArrayList<ProjectedNodeKey>(prominence.keySet())).containsExactlyElementsOf(expectedKeys);
        assertThat(prominence.get(groupNode.key()).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(prominence.get(nodeKey("target")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void exposeNoPublicCallerSuppliedProminenceFactory() {
        List<String> callerSuppliedSignatures = new ArrayList<String>();
        for (Method method : GraphProjection.class.getDeclaredMethods()) {
            if (!method.getName().equals("projected")
                    || !Modifier.isPublic(method.getModifiers())
                    || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (Map.class.isAssignableFrom(parameterType)) {
                    callerSuppliedSignatures.add(method.getName() + Arrays.toString(method.getParameterTypes()));
                }
            }
        }
        assertThat(callerSuppliedSignatures)
            .as("no public projected(...) overload may accept a caller-supplied prominence map").isEmpty();
    }

    @Test
    public void fullyProjectedProjectionDerivesProminenceCoveringNodesInProjectedOrder() {
        List<ProjectedNode> nodes = Arrays.asList(node("a"), node("b"), node("c"));
        List<ProjectedEdge> edges = Arrays.asList(directedEdge("a", "b"), directedEdge("a", "c"));

        GraphProjection projection = GraphProjection.projected(1, nodes,
            Collections.<ProjectedEnclosure>emptyList(), edges, Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());

        List<ProjectedNodeKey> expectedKeys = new ArrayList<ProjectedNodeKey>();
        for (ProjectedNode value : nodes) {
            expectedKeys.add(value.key());
        }
        assertThat(new ArrayList<ProjectedNodeKey>(projection.prominence().keySet()))
            .as("prominence covers every projected node in projected-node order")
            .containsExactlyElementsOf(expectedKeys);
        assertThat(projection.prominence()).isEqualTo(ProminenceCalculator.calculate(nodes,
            Collections.<ProjectedEnclosure>emptyList(), edges));
        assertThat(projection.prominence().get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(2);
        assertThat(projection.prominence().get(nodeKey("b")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void keepStructureAndResolvedProjectionsFreeOfProminence() {
        ProjectedNode node = node("a");
        GraphProjection structure = GraphProjection.structure(1, Collections.singletonList(node),
            Collections.<ProjectedEnclosure>emptyList());
        GraphProjection resolved = GraphProjection.resolved(1, Collections.singletonList(node),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());

        assertThat(structure.prominence()).isEmpty();
        assertThat(resolved.prominence()).isEmpty();
    }

    @Test
    public void countEnclosureSourcesAsZeroAndKeepEnclosuresOutOfTheProminenceMap() {
        EnclosureKey ancestor = EnclosureKey.of(source("ancestor"));

        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Collections.singletonList(node("a")), Collections.singletonList(enclosure("ancestor")),
            Collections.singletonList(enclosureSourceEdge(ancestor, "a")));

        assertThat(prominence.keySet()).containsExactly(nodeKey("a"));
        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isZero();
    }

    @Test
    public void includeEveryZeroDegreeNodeInTheProminenceMap() {
        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b"), node("c")), Collections.<ProjectedEnclosure>emptyList(),
            Collections.singletonList(directedEdge("a", "b")));

        assertThat(prominence.keySet()).containsExactly(nodeKey("a"), nodeKey("b"), nodeKey("c"));
        assertThat(prominence.get(nodeKey("c")).visibleOutgoingTargets()).isZero();
        assertThat(prominence.get(nodeKey("c")).scale()).isEqualTo(1.0);
    }

    @Test
    public void scaleReachTwoDistinctVisibleTargetsOnTheSourceNode() {
        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b"), node("c")), Collections.<ProjectedEnclosure>emptyList(),
            Arrays.asList(directedEdge("a", "b"), directedEdge("a", "c")));

        assertThat(prominence.get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(2);
        assertThat(prominence.get(nodeKey("a")).scale()).isEqualTo(1.2);
    }

    @Test
    public void applyTheExactCappedLogarithmicScale() {
        assertThat(NodeProminence.of(0).visibleOutgoingTargets()).isZero();
        assertThat(NodeProminence.of(0).scale()).isEqualTo(1.0);
        assertThat(NodeProminence.of(1).scale()).isEqualTo(1.0);
        assertThat(NodeProminence.of(2).scale()).isEqualTo(1.2);
        assertThat(NodeProminence.of(4).scale()).isEqualTo(1.4);
        assertThat(NodeProminence.of(8).scale()).isEqualTo(1.6);
        assertThat(NodeProminence.of(13).scale()).isCloseTo(1.7400879436282186, within(1e-12));
        assertThat(NodeProminence.of(14).scale()).isEqualTo(1.75);
        assertThat(NodeProminence.of(20).scale()).isEqualTo(1.75);
        assertThat(NodeProminence.of(20).visibleOutgoingTargets()).isEqualTo(20);
    }

    @Test
    public void keepTheScaleMonotonicFiniteAndCapped() {
        double previous = 0.0;
        for (int targets = 0; targets <= 64; targets++) {
            double scale = NodeProminence.of(targets).scale();
            assertThat(scale).isGreaterThanOrEqualTo(previous);
            assertThat(scale).isLessThanOrEqualTo(1.75);
            assertThat(Double.isFinite(scale)).isTrue();
            previous = scale;
        }
    }

    @Test
    public void rejectNegativeTargetCounts() {
        assertThatThrownBy(() -> NodeProminence.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void returnAnUnmodifiableProminenceMap() {
        Map<ProjectedNodeKey, NodeProminence> prominence = ProminenceCalculator.calculate(
            Arrays.asList(node("a"), node("b")), Collections.<ProjectedEnclosure>emptyList(),
            Collections.singletonList(directedEdge("a", "b")));

        assertThatThrownBy(() -> prominence.put(nodeKey("c"), NodeProminence.of(0)))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> prominence.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void preserveProjectedNodeOrderingRegardlessOfEdgeAndEnclosureArgumentOrder() {
        EnclosureKey firstAncestor = EnclosureKey.of(source("first-ancestor"));
        EnclosureKey secondAncestor = EnclosureKey.of(source("second-ancestor"));
        ProjectedEnclosure firstEnclosure = enclosure("first-ancestor");
        ProjectedEnclosure secondEnclosure = enclosure("second-ancestor");
        List<ProjectedNode> nodes = Arrays.asList(node("a"), node("b"), node("c"));
        List<ProjectedEdge> edges = Arrays.asList(directedEdge("a", "b"), directedEdge("c", "a"),
            directedToEnclosureEdge("b", firstAncestor), directedToEnclosureEdge("c", secondAncestor));
        List<ProjectedEdge> reversedEdges = Arrays.asList(directedToEnclosureEdge("c", secondAncestor),
            directedToEnclosureEdge("b", firstAncestor), directedEdge("c", "a"), directedEdge("a", "b"));

        Map<ProjectedNodeKey, NodeProminence> first = ProminenceCalculator.calculate(nodes,
            Arrays.asList(firstEnclosure, secondEnclosure), edges);
        Map<ProjectedNodeKey, NodeProminence> second = ProminenceCalculator.calculate(nodes,
            Arrays.asList(secondEnclosure, firstEnclosure), reversedEdges);

        assertThat(first).isEqualTo(second);
        assertThat(new ArrayList<ProjectedNodeKey>(first.keySet()))
            .containsExactly(nodeKey("a"), nodeKey("b"), nodeKey("c"));
        assertThat(new ArrayList<ProjectedNodeKey>(second.keySet()))
            .containsExactly(nodeKey("a"), nodeKey("b"), nodeKey("c"));
        assertThat(first.get(nodeKey("a")).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(first.get(nodeKey("b")).visibleOutgoingTargets()).isEqualTo(1);
        assertThat(first.get(nodeKey("c")).visibleOutgoingTargets()).isEqualTo(2);
    }

    private static ProjectedNode node(String id) {
        return ProjectedNode.of(ProjectedNodeKey.of(source(id)), SafeNodeLabel.of(id, id), "Map", false);
    }

    private static ProjectedNodeKey nodeKey(String id) {
        return ProjectedNodeKey.of(source(id));
    }

    private static ProjectedEndpointKey endpoint(SourceNodeKey source) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source));
    }

    private static ProjectedEnclosure enclosure(String... ids) {
        List<EnclosureKey> endpoints = new ArrayList<EnclosureKey>();
        List<SafeNodeLabel> labels = new ArrayList<SafeNodeLabel>();
        for (String id : ids) {
            endpoints.add(EnclosureKey.of(source(id)));
            labels.add(SafeNodeLabel.of(id, id));
        }
        return ProjectedEnclosure.of(EnclosureHullKey.of(endpoints), endpoints, labels, "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
    }

    private static ProjectedEdge directedEdge(String from, String to) {
        SourceNodeKey source = source(from);
        return ProjectedEdge.of(ProjectedEdgeKey.of(endpoint(source), endpoint(source(to))),
            Collections.singletonList(nativeContributor(0, source, reference(to), false, true)));
    }

    private static ProjectedEdge bidirectionalEdge(String first, String second) {
        SourceNodeKey source = source(first);
        return ProjectedEdge.of(ProjectedEdgeKey.of(endpoint(source), endpoint(source(second))),
            Collections.singletonList(nativeContributor(0, source, reference(second), true, true)));
    }

    private static ProjectedEdge undirectedEdge(String first, String second) {
        SourceNodeKey source = source(first);
        return ProjectedEdge.of(ProjectedEdgeKey.of(endpoint(source), endpoint(source(second))),
            Collections.singletonList(nativeContributor(0, source, reference(second), false, false)));
    }

    private static ProjectedEdge directedToEnclosureEdge(String from, EnclosureKey target) {
        SourceNodeKey source = source(from);
        ProjectedEndpointKey sourceEndpoint = endpoint(source);
        ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofEnclosure(target);
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source,
            target.source().persistedReference().get(), false, true, "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor),
            sourceEndpoint, targetEndpoint);
        return ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(contributor));
    }

    private static ProjectedEdge enclosureSourceEdge(EnclosureKey source, String to) {
        ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofEnclosure(source);
        ProjectedEndpointKey targetEndpoint = endpoint(source(to));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source.source(), reference(to), false, true,
            "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor),
            sourceEndpoint, targetEndpoint);
        return ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(contributor));
    }

    private static EdgeContributor nativeContributor(int occurrence, SourceNodeKey source, NodeReference target,
            boolean arrowAtSource, boolean arrowAtTarget) {
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, target, arrowAtSource, arrowAtTarget,
            "source", "middle", "target");
        return EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor), endpoint(source),
            endpoint(SourceNodeKey.persisted(target)));
    }

    private static NodeSnapshot node(MapReferenceId map, String id, boolean structuralLeaf, boolean graphGroup,
            boolean excluded, NodeSnapshot... children) {
        return NodeSnapshot.of(source(map, id), SafeNodeLabel.of(id, id), structuralLeaf, graphGroup, excluded,
            Arrays.asList(children));
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root) {
        return MapSnapshot.of(id, workspaceOrder, "Map " + id.value(), root,
            Collections.<PersistedNodeId>emptySet(), false);
    }

    private static ConnectorSnapshot connector(int occurrence, SourceNodeKey source, NodeReference target,
            boolean arrowAtSource, boolean arrowAtTarget) {
        return ConnectorSnapshot.of(occurrence, ConnectorDescriptor.of(source, target, arrowAtSource, arrowAtTarget,
            "source", "middle", "target"));
    }

    private static WorkspaceDocument workspace(MapReference... registrations) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder().maps(Arrays.asList(registrations)).relationships(
                Collections.<GraphRelationshipRecord>emptyList()).build();
    }

    private static MapReference registration(MapReferenceId map, long sequence, boolean active) {
        return MapReference.of(map, sequence, URI.create("maps/" + map.value() + ".mm"), active, "#4E79A7",
            Collections.<UnknownXml>emptyList());
    }

    private static SourceNodeKey source(String id) {
        return SourceNodeKey.persisted(reference(id));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(reference(map, id));
    }

    private static NodeReference reference(String id) {
        return NodeReference.of(MAP, PersistedNodeId.of(id));
    }

    private static NodeReference reference(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static MapReferenceId mapId(String value) {
        return MapReferenceId.of(value);
    }
}
