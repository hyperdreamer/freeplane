package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class EdgeProjectionShould {
    private static final MapReferenceId MAP_ONE = mapId("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = mapId("00000000-0000-0000-0000-000000000002");

    @Test
    public void canonicalizeReversedProjectedEdgeKeysAndOrderEngineEdges() {
        SourceNodeKey a = source(MAP_ONE, "a");
        SourceNodeKey b = source(MAP_ONE, "b");
        SourceNodeKey c = source(MAP_ONE, "c");
        ProjectedEndpointKey aEndpoint = endpoint(a);
        ProjectedEndpointKey bEndpoint = endpoint(b);

        assertThat(ProjectedEdgeKey.of(aEndpoint, bEndpoint))
            .isEqualTo(ProjectedEdgeKey.of(bEndpoint, aEndpoint));
        assertThat(ProjectedEdgeKey.of(aEndpoint, bEndpoint).first()).isEqualTo(aEndpoint);

        NodeSnapshot root = node(MAP_ONE, "root", false, false, false,
            node(MAP_ONE, "a", true, true, false), node(MAP_ONE, "b", true, true, false),
            node(MAP_ONE, "c", true, true, false));
        MapSnapshot snapshot = map(MAP_ONE, 1, root).withConnectors(Arrays.asList(
            connector(0, c, reference(MAP_ONE, "a"), false, true),
            connector(0, b, reference(MAP_ONE, "a"), false, true)));
        GraphProjection projection = new ProjectionEngine().projectStructure(1,
            workspace(registration(MAP_ONE, 1, true)), Collections.singletonList(snapshot));

        assertThat(projection.edges()).extracting(ProjectedEdge::key)
            .containsExactly(ProjectedEdgeKey.of(endpoint(a), endpoint(b)),
                ProjectedEdgeKey.of(endpoint(a), endpoint(c)));
    }

    @Test
    public void unionAllSixteenTwoContributorArrowBitCombinationsIncludingReversedOrientation() {
        SourceNodeKey a = source(MAP_ONE, "a");
        SourceNodeKey b = source(MAP_ONE, "b");
        ProjectedEdgeKey key = ProjectedEdgeKey.of(endpoint(a), endpoint(b));

        for (int firstBits = 0; firstBits < 4; firstBits++) {
            for (int secondBits = 0; secondBits < 4; secondBits++) {
                EdgeContributor first = nativeContributor(0, a, reference(MAP_ONE, "b"),
                    (firstBits & 1) != 0, (firstBits & 2) != 0);
                EdgeContributor second = nativeContributor(1, b, reference(MAP_ONE, "a"),
                    (secondBits & 1) != 0, (secondBits & 2) != 0);
                ProjectedEdge edge = ProjectedEdge.of(key, Arrays.asList(first, second));

                assertThat(edge.arrowAtFirst()).isEqualTo((firstBits & 1) != 0 || (secondBits & 2) != 0);
                assertThat(edge.arrowAtSecond()).isEqualTo((firstBits & 2) != 0 || (secondBits & 1) != 0);
            }
        }
    }

    @Test
    public void shareOneLineBetweenUndirectedAndDirectedContributors() {
        SourceNodeKey a = source(MAP_ONE, "a");
        SourceNodeKey b = source(MAP_ONE, "b");
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(endpoint(a), endpoint(b)), Arrays.asList(
            nativeContributor(0, a, reference(MAP_ONE, "b"), false, false),
            nativeContributor(1, a, reference(MAP_ONE, "b"), false, true)));

        assertThat(edge.contributorCount()).isEqualTo(2);
        assertThat(edge.arrowAtFirst()).isFalse();
        assertThat(edge.arrowAtSecond()).isTrue();
        assertThat(edge.hasMultiplicityCue()).isTrue();
    }

    @Test
    public void retainIdenticalNativeDescriptorsByDistinctOccurrencesAndExposeTheirLabels() {
        SourceNodeKey a = source(MAP_ONE, "a");
        SourceNodeKey b = source(MAP_ONE, "b");
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(a, reference(MAP_ONE, "b"), false, true,
            "source label", "middle label", "target label");
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false,
            node(MAP_ONE, "a", true, true, false), node(MAP_ONE, "b", true, true, false));
        MapSnapshot snapshot = map(MAP_ONE, 1, root).withConnectors(Arrays.asList(
            ConnectorSnapshot.of(1, descriptor), ConnectorSnapshot.of(0, descriptor)));

        GraphProjection projection = new ProjectionEngine().projectStructure(1,
            workspace(registration(MAP_ONE, 1, true)), Collections.singletonList(snapshot));
        ProjectedEdge edge = projection.edges().get(0);

        assertThat(edge.contributors()).hasSize(2);
        assertThat(edge.contributors().get(0).key().occurrence()).hasValue(0);
        assertThat(edge.contributors().get(1).key().occurrence()).hasValue(1);
        assertThat(edge.hasMultiplicityCue()).isTrue();
        EdgeContributor contributor = edge.contributors().get(0);
        assertThat(contributor.connectorDescriptor()).contains(descriptor);
        assertThat(contributor.sourceLabel()).isEqualTo("source label");
        assertThat(contributor.middleLabel()).isEqualTo("middle label");
        assertThat(contributor.targetLabel()).isEqualTo("target label");
        assertThat(edge.toString()).doesNotContain("source label", "middle label", "target label");
        assertThat(contributor.toString()).doesNotContain("source label", "middle label", "target label");
    }

    @Test
    public void retainRepeatedOppositeGraphRelationshipsAndUnionTheirDirections() {
        SourceNodeKey a = source(MAP_ONE, "a");
        SourceNodeKey b = source(MAP_TWO, "b");
        GraphRelationshipRecord forward = relationship(1, reference(MAP_ONE, "a"), reference(MAP_TWO, "b"),
            RelationshipDirection.FORWARD);
        GraphRelationshipRecord opposite = relationship(2, reference(MAP_TWO, "b"), reference(MAP_ONE, "a"),
            RelationshipDirection.FORWARD);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Arrays.asList(forward, opposite));
        GraphProjection projection = project(workspace,
            map(MAP_ONE, 1, node(MAP_ONE, "a", true, false, false)),
            map(MAP_TWO, 2, node(MAP_TWO, "b", true, false, false)));

        assertThat(projection.edges()).hasSize(1);
        ProjectedEdge edge = projection.edges().get(0);
        assertThat(edge.contributors()).extracting(EdgeContributor::graphRelationship)
            .extracting(optional -> optional.get().id()).containsExactly(forward.id(), opposite.id());
        assertThat(edge.arrowAtFirst()).isTrue();
        assertThat(edge.arrowAtSecond()).isTrue();
    }

    @Test
    public void omitInternalActiveGroupConnectorsWithoutChangingTheirSnapshot() {
        NodeSnapshot descendant = node(MAP_ONE, "descendant", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", false, true, false, descendant);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, group);
        MapSnapshot original = map(MAP_ONE, 1, root);
        MapSnapshot snapshot = original.withConnectors(Collections.singletonList(
            connector(0, source(MAP_ONE, "group"), reference(MAP_ONE, "descendant"), true, true)));

        GraphProjection projection = new ProjectionEngine().projectStructure(1,
            workspace(registration(MAP_ONE, 1, true)), Collections.singletonList(snapshot));

        assertThat(projection.edges()).isEmpty();
        assertThat(original.connectors()).isEmpty();
        assertThat(snapshot.connectors()).hasSize(1);
        assertThat(snapshot.withConnectors(snapshot.connectors()).connectors()).containsExactlyElementsOf(
            snapshot.connectors());
    }

    @Test
    public void omitUnresolvedRelationshipsAndConnectorsFromUnavailableStaleSnapshots() {
        SourceNodeKey available = source(MAP_ONE, "available");
        NodeSnapshot staleRoot = node(MAP_TWO, "stale-root", false, false, false,
            node(MAP_TWO, "stale-source", true, false, false), node(MAP_TWO, "stale-target", true, false, false));
        MapSnapshot stale = map(MAP_TWO, 2, staleRoot).withConnectors(Collections.singletonList(
            connector(0, source(MAP_TWO, "stale-source"), reference(MAP_TWO, "stale-target"), false, true)));
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.singletonList(relationship(1, reference(MAP_ONE, "available"),
                reference(MAP_TWO, "stale-source"), RelationshipDirection.FORWARD)));
        Map<MapReferenceId, MapAvailability> availability = new LinkedHashMap<MapReferenceId, MapAvailability>();
        availability.put(MAP_ONE, MapAvailability.AVAILABLE);
        availability.put(MAP_TWO, MapAvailability.LOADING);

        GraphProjection projection = new ProjectionEngine().project(ProjectionInput.of(1, workspace,
            Arrays.asList(map(MAP_ONE, 1, node(MAP_ONE, "available", true, false, false)), stale), availability));

        assertThat(projection.edges()).isEmpty();
        assertThat(projection.relationshipResolutions().get(0).status())
            .isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(stale.connectors()).hasSize(1);
        assertThat(projection.toString()).doesNotContain("stale");
    }

    @Test
    public void preserveProjectionAndContributorOrderingAcrossMapInputPermutations() {
        NodeSnapshot firstRoot = node(MAP_ONE, "root", false, false, false,
            node(MAP_ONE, "a", true, true, false), node(MAP_ONE, "b", true, true, false));
        MapSnapshot firstMap = map(MAP_ONE, 2, firstRoot).withConnectors(Arrays.asList(
            connector(1, source(MAP_ONE, "a"), reference(MAP_ONE, "b"), false, true),
            connector(0, source(MAP_ONE, "a"), reference(MAP_ONE, "b"), false, true)));
        MapSnapshot secondMap = map(MAP_TWO, 1, node(MAP_TWO, "root", true, false, false));
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 2, true), registration(MAP_TWO, 1, true)),
            Collections.<GraphRelationshipRecord>emptyList());

        GraphProjection first = new ProjectionEngine().project(ProjectionInput.of(1, workspace,
            Arrays.asList(firstMap, secondMap), availability(workspace,
                MapAvailability.AVAILABLE, MapAvailability.AVAILABLE)));
        GraphProjection second = new ProjectionEngine().project(ProjectionInput.of(1, workspace,
            Arrays.asList(secondMap, firstMap), availability(workspace,
                MapAvailability.AVAILABLE, MapAvailability.AVAILABLE)));

        assertThat(first).isEqualTo(second);
        assertThat(first.edges().get(0).contributors()).extracting(EdgeContributor::key)
            .extracting(key -> key.occurrence().getAsInt()).containsExactly(0, 1);
    }

    @Test
    public void reportPhysicalProjectionDiffsAndKeepItsCollectionsImmutable() {
        SourceNodeKey a = source(MAP_ONE, "a");
        SourceNodeKey b = source(MAP_ONE, "b");
        SourceNodeKey c = source(MAP_ONE, "c");
        SourceNodeKey added = source(MAP_ONE, "added");
        ProjectedNode beforeNode = projectedNode(a, "old");
        ProjectedNode removedNodeOne = projectedNode(b, "removed one");
        ProjectedNode removedNodeTwo = projectedNode(c, "removed two");
        ProjectedNode afterNode = projectedNode(a, "new");
        ProjectedNode addedNode = projectedNode(added, "added");
        EdgeContributor beforeContributor = nativeContributor(0, a, reference(MAP_ONE, "b"), false, true);
        EdgeContributor afterContributor = nativeContributor(0, a, reference(MAP_ONE, "b"), true, false);
        ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(endpoint(a), endpoint(b));
        ProjectedEdgeKey removedEdgeKeyOne = ProjectedEdgeKey.of(endpoint(b), endpoint(c));
        ProjectedEdgeKey removedEdgeKeyTwo = ProjectedEdgeKey.of(endpoint(a), endpoint(c));
        ProjectedEdge removedEdgeOne = ProjectedEdge.of(removedEdgeKeyOne,
            Collections.singletonList(nativeContributor(1, b, reference(MAP_ONE, "c"), false, true)));
        ProjectedEdge removedEdgeTwo = ProjectedEdge.of(removedEdgeKeyTwo,
            Collections.singletonList(nativeContributor(2, a, reference(MAP_ONE, "c"), true, false)));
        ProjectedEnclosure beforeEnclosure = enclosure(MAP_ONE, "hull", "old enclosure");
        ProjectedEnclosure removedEnclosureOne = enclosure(MAP_ONE, "hull-one", "removed enclosure one");
        ProjectedEnclosure removedEnclosureTwo = enclosure(MAP_ONE, "hull-two", "removed enclosure two");
        ProjectedEnclosure afterEnclosure = enclosure(MAP_ONE, "hull", "new enclosure");
        GraphProjection before = GraphProjection.projected(4,
            Arrays.asList(beforeNode, removedNodeOne, removedNodeTwo),
            Arrays.asList(beforeEnclosure, removedEnclosureOne, removedEnclosureTwo),
            Arrays.asList(ProjectedEdge.of(edgeKey, Collections.singletonList(beforeContributor)), removedEdgeOne,
                removedEdgeTwo),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        GraphProjection after = GraphProjection.projected(5, Arrays.asList(afterNode, addedNode),
            Collections.singletonList(afterEnclosure),
            Collections.singletonList(ProjectedEdge.of(edgeKey, Collections.singletonList(afterContributor))),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());

        ProjectionDiff diff = ProjectionDiff.between(before, after);

        assertThat(diff.beforeGeneration()).isEqualTo(4);
        assertThat(diff.afterGeneration()).isEqualTo(5);
        assertThat(diff.addedNodes()).containsExactly(addedNode.key());
        assertThat(diff.removedNodes()).containsExactly(removedNodeOne.key(), removedNodeTwo.key());
        assertThat(diff.changedNodes()).containsExactly(afterNode.key());
        assertThat(diff.removedEnclosures()).containsExactly(removedEnclosureOne.hullKey(),
            removedEnclosureTwo.hullKey());
        assertThat(diff.changedEnclosures()).containsExactly(afterEnclosure.hullKey());
        assertThat(diff.removedEdges()).containsExactly(removedEdgeKeyOne, removedEdgeKeyTwo);
        assertThat(diff.changedEdges()).containsExactly(edgeKey);
        assertThat(diff.isEmpty()).isFalse();
        assertThatThrownBy(() -> diff.addedNodes().clear()).isInstanceOf(UnsupportedOperationException.class);

        GraphProjection generationOnly = GraphProjection.projected(6, after.nodes(), after.enclosures(), after.edges(),
            after.relationshipResolutions(), after.pins());
        assertThat(ProjectionDiff.between(after, generationOnly).isEmpty()).isTrue();
    }

    @Test
    public void rejectDuplicateProjectedPhysicalKeysInProjectionDiff() {
        SourceNodeKey a = source(MAP_ONE, "a");
        ProjectedNode node = projectedNode(a, "a");
        GraphProjection duplicate = GraphProjection.projected(1, Arrays.asList(node, node),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());

        assertThatThrownBy(() -> ProjectionDiff.between(duplicate, GraphProjection.structure(2,
            Collections.singletonList(node), Collections.<ProjectedEnclosure>emptyList())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static EdgeContributor nativeContributor(int occurrence, SourceNodeKey source, NodeReference target,
            boolean arrowAtSource, boolean arrowAtTarget) {
        return EdgeContributor.nativeConnector(
            connector(occurrence, source, target, arrowAtSource, arrowAtTarget), endpoint(source), endpoint(source(target)));
    }

    private static ConnectorSnapshot connector(int occurrence, SourceNodeKey source, NodeReference target,
            boolean arrowAtSource, boolean arrowAtTarget) {
        return ConnectorSnapshot.of(occurrence, ConnectorDescriptor.of(source, target, arrowAtSource, arrowAtTarget,
            "source", "middle", "target"));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(reference(map, id));
    }

    private static SourceNodeKey source(NodeReference reference) {
        return SourceNodeKey.persisted(reference);
    }

    private static ProjectedEndpointKey endpoint(SourceNodeKey source) {
        return ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source));
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

    private static ProjectedNode projectedNode(SourceNodeKey source, String label) {
        return ProjectedNode.of(ProjectedNodeKey.of(source), SafeNodeLabel.of(label, label), "Map", false);
    }

    private static ProjectedEnclosure enclosure(MapReferenceId map, String id, String label) {
        EnclosureKey key = EnclosureKey.of(source(map, id));
        return ProjectedEnclosure.of(EnclosureHullKey.of(Collections.singletonList(key)),
            Collections.singletonList(key), Collections.singletonList(SafeNodeLabel.of(label, label)), "Map",
            java.util.Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
    }

    private static GraphProjection project(WorkspaceDocument workspace, MapSnapshot... maps) {
        return new ProjectionEngine().projectStructure(1, workspace, Arrays.asList(maps));
    }

    private static WorkspaceDocument workspace(MapReference... registrations) {
        return workspace(Arrays.asList(registrations), Collections.<GraphRelationshipRecord>emptyList());
    }

    private static WorkspaceDocument workspace(List<MapReference> registrations,
            List<GraphRelationshipRecord> relationships) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder().maps(registrations).relationships(relationships).build();
    }

    private static List<MapReference> registrations(MapReference... registrations) {
        return Arrays.asList(registrations);
    }

    private static MapReference registration(MapReferenceId map, long sequence, boolean active) {
        return MapReference.of(map, sequence, URI.create("maps/" + map.value() + ".mm"), active, "#4E79A7",
            Collections.<UnknownXml>emptyList());
    }

    private static GraphRelationshipRecord relationship(long sequence, NodeReference source, NodeReference target,
            RelationshipDirection direction) {
        return GraphRelationshipRecord.of(RelationshipId.of(String.format(
            "10000000-0000-0000-0000-%012d", Long.valueOf(sequence))), sequence, source, target, direction,
            Collections.<UnknownXml>emptyList());
    }

    private static Map<MapReferenceId, MapAvailability> availability(WorkspaceDocument workspace,
            MapAvailability... values) {
        Map<MapReferenceId, MapAvailability> result = new LinkedHashMap<MapReferenceId, MapAvailability>();
        for (int index = 0; index < values.length; index++) {
            result.put(workspace.maps().get(index).id(), values[index]);
        }
        return result;
    }

    private static NodeReference reference(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static MapReferenceId mapId(String value) {
        return MapReferenceId.of(value);
    }
}
