package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class EndpointResolutionShould {
    private static final MapReferenceId MAP_ONE = mapId("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = mapId("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_THREE = mapId("00000000-0000-0000-0000-000000000003");
    private static final MapReferenceId MAP_FOUR = mapId("00000000-0000-0000-0000-000000000004");
    private static final MapReferenceId MAP_FIVE = mapId("00000000-0000-0000-0000-000000000005");
    private static final MapReferenceId MAP_SIX = mapId("00000000-0000-0000-0000-000000000006");
    private static final MapReferenceId MAP_SEVEN = mapId("00000000-0000-0000-0000-000000000007");
    private static final MapReferenceId MAP_EIGHT = mapId("00000000-0000-0000-0000-000000000008");

    @Test
    public void resolveLeafAndEveryExactEnclosureEndpointInAUnaryHull() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", true, true, false);
        NodeSnapshot middle = node(MAP_ONE, "middle", false, true, false, leaf);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, middle);
        NodeSnapshot target = node(MAP_TWO, "target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            relationships(
                relationship(1, reference(MAP_ONE, "root"), reference(MAP_TWO, "target")),
                relationship(2, reference(MAP_ONE, "middle"), reference(MAP_TWO, "target")),
                relationship(3, reference(MAP_ONE, "leaf"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, root), map(MAP_TWO, 2, target));

        assertThat(projection.nodes()).isEmpty();
        assertSourceEndpoint(projection.relationshipResolutions().get(0),
            ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(root.key())));
        assertSourceEndpoint(projection.relationshipResolutions().get(1),
            ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(middle.key())));
        assertSourceEndpoint(projection.relationshipResolutions().get(2),
            ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(leaf.key())));
        assertThat(projection.relationshipResolutions()).allMatch(resolution ->
            resolution.status() == RelationshipStatus.ACTIVE && resolution.recoverableReasons().isEmpty());
    }

    @Test
    public void resolveGroupBoundariesExactlyAndFoldPlainDescendantsToTheirOuterGroup() {
        NodeSnapshot nestedGroup = node(MAP_ONE, "nested-group", false, true, false);
        NodeSnapshot descendant = node(MAP_ONE, "descendant", true, false, false);
        NodeSnapshot outerGroup = node(MAP_ONE, "outer-group", false, true, false, nestedGroup, descendant);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, outerGroup);
        NodeSnapshot target = node(MAP_TWO, "target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            relationships(
                relationship(1, reference(MAP_ONE, "outer-group"), reference(MAP_TWO, "target")),
                relationship(2, reference(MAP_ONE, "nested-group"), reference(MAP_TWO, "target")),
                relationship(3, reference(MAP_ONE, "descendant"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, root), map(MAP_TWO, 2, target));

        assertThat(projection.nodes()).isEmpty();
        ProjectedEndpointKey outerEndpoint =
            ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(outerGroup.key()));
        ProjectedEndpointKey nestedEndpoint =
            ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(nestedGroup.key()));
        assertSourceEndpoint(projection.relationshipResolutions().get(0), outerEndpoint);
        assertSourceEndpoint(projection.relationshipResolutions().get(1), nestedEndpoint);
        assertSourceEndpoint(projection.relationshipResolutions().get(2), outerEndpoint);
        for (RelationshipResolution resolution : projection.relationshipResolutions()) {
            assertThat(resolution.status()).isEqualTo(RelationshipStatus.ACTIVE);
        }
    }

    @Test
    public void keepFormerGroupEndpointsUnresolvedAfterUngrouping() {
        NodeSnapshot descendant = node(MAP_ONE, "descendant", true, false, false);
        NodeSnapshot formerGroup = node(MAP_ONE, "former-group", false, false, false, descendant);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, formerGroup);
        NodeSnapshot target = node(MAP_TWO, "target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.singletonList(
                relationship(1, reference(MAP_ONE, "former-group"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, root, new LinkedHashSet<PersistedNodeId>(Arrays.asList(
                PersistedNodeId.of("former-group"), PersistedNodeId.of("descendant"))), false),
            map(MAP_TWO, 2, target));

        RelationshipResolution resolution = projection.relationshipResolutions().get(0);
        assertThat(resolution.status()).isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(resolution.recoverableReasons()).containsExactly(RecoverableReason.NODE_INACCESSIBLE);
        assertThat(resolution.source()).isEmpty();
        assertThat(resolution.target()).isPresent();
    }

    @Test
    public void treatAllUnavailableStatesAndTheirStaleSnapshotsAsRecoverable() {
        NodeSnapshot inactive = node(MAP_ONE, "inactive", true, false, false);
        NodeSnapshot loading = node(MAP_TWO, "loading", true, false, false);
        NodeSnapshot available = node(MAP_THREE, "available", true, false, false);
        NodeSnapshot missing = node(MAP_FOUR, "missing", true, false, false);
        NodeSnapshot unreadable = node(MAP_FIVE, "unreadable", true, false, false);
        NodeSnapshot password = node(MAP_SIX, "password", true, false, false);
        NodeSnapshot reload = node(MAP_SEVEN, "reload", true, false, false);
        NodeSnapshot target = node(MAP_EIGHT, "target", true, false, false);
        WorkspaceDocument workspace = workspace(registrations(
            registration(MAP_ONE, 1, false), registration(MAP_TWO, 2, true), registration(MAP_THREE, 3, true),
            registration(MAP_FOUR, 4, true), registration(MAP_FIVE, 5, true), registration(MAP_SIX, 6, true),
            registration(MAP_SEVEN, 7, true), registration(MAP_EIGHT, 8, true)), relationships(
            relationship(1, reference(MAP_ONE, "inactive"), reference(MAP_EIGHT, "target")),
            relationship(2, reference(MAP_TWO, "loading"), reference(MAP_EIGHT, "target")),
            relationship(3, reference(MAP_THREE, "available"), reference(MAP_EIGHT, "target")),
            relationship(4, reference(MAP_FOUR, "missing"), reference(MAP_EIGHT, "target")),
            relationship(5, reference(MAP_FIVE, "unreadable"), reference(MAP_EIGHT, "target")),
            relationship(6, reference(MAP_SIX, "password"), reference(MAP_EIGHT, "target")),
            relationship(7, reference(MAP_SEVEN, "reload"), reference(MAP_EIGHT, "target"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.INACTIVE, MapAvailability.LOADING, MapAvailability.AVAILABLE, MapAvailability.MISSING,
            MapAvailability.UNREADABLE, MapAvailability.PASSWORD_REQUIRED, MapAvailability.RELOAD_REQUIRED,
            MapAvailability.AVAILABLE), map(MAP_ONE, 1, inactive), map(MAP_TWO, 2, loading),
            map(MAP_THREE, 3, available), map(MAP_FOUR, 4, missing), map(MAP_FIVE, 5, unreadable),
            map(MAP_SIX, 6, password), map(MAP_SEVEN, 7, reload), map(MAP_EIGHT, 8, target));

        assertRecoverable(projection.relationshipResolutions().get(0), RecoverableReason.MAP_INACTIVE);
        assertRecoverable(projection.relationshipResolutions().get(1), RecoverableReason.MAP_LOADING);
        assertThat(projection.relationshipResolutions().get(2).status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertRecoverable(projection.relationshipResolutions().get(3), RecoverableReason.MAP_MISSING);
        assertRecoverable(projection.relationshipResolutions().get(4), RecoverableReason.MAP_UNREADABLE);
        assertRecoverable(projection.relationshipResolutions().get(5), RecoverableReason.MAP_PASSWORD_REQUIRED);
        assertRecoverable(projection.relationshipResolutions().get(6), RecoverableReason.MAP_RELOAD_REQUIRED);
        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).extracting(ProjectedEnclosure::mapReferenceId)
            .containsExactly(MAP_THREE, MAP_EIGHT);
    }

    @Test
    public void retainRecoverableUncertaintyForExcludedAndInaccessibleAbsenceUntilFullyAccessible() {
        NodeSnapshot excluded = node(MAP_ONE, "excluded", true, false, true);
        NodeSnapshot visible = node(MAP_ONE, "visible", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, excluded, visible);
        NodeSnapshot target = node(MAP_TWO, "target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            relationships(
                relationship(1, reference(MAP_ONE, "excluded"), reference(MAP_TWO, "target")),
                relationship(2, reference(MAP_ONE, "attached-only"), reference(MAP_TWO, "target")),
                relationship(3, reference(MAP_ONE, "ambiguous"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());
        Set<PersistedNodeId> attached = new LinkedHashSet<PersistedNodeId>(Collections.singletonList(
            PersistedNodeId.of("attached-only")));

        GraphProjection uncertain = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, root, attached, true), map(MAP_TWO, 2, target));

        assertRecoverable(uncertain.relationshipResolutions().get(0), RecoverableReason.NODE_EXCLUDED);
        assertRecoverable(uncertain.relationshipResolutions().get(1), RecoverableReason.NODE_INACCESSIBLE);
        assertRecoverable(uncertain.relationshipResolutions().get(2), RecoverableReason.NODE_INACCESSIBLE);

        WorkspaceDocument missingWorkspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.singletonList(
                relationship(1, reference(MAP_ONE, "ambiguous"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());
        GraphProjection missing = project(missingWorkspace, availability(missingWorkspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, root), map(MAP_TWO, 2, target));

        assertThat(missing.relationshipResolutions().get(0).status())
            .isEqualTo(RelationshipStatus.UNRESOLVED_MISSING_NODE);
        assertThat(missing.relationshipResolutions().get(0).recoverableReasons()).isEmpty();
    }

    @Test
    public void letRecoverableUncertaintyDominateAnEndpointMissingFromAnotherFullyAccessibleMap() {
        NodeSnapshot sourceRoot = node(MAP_ONE, "visible", true, false, false);
        NodeSnapshot targetRoot = node(MAP_TWO, "visible-target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.singletonList(
                relationship(1, reference(MAP_ONE, "uncertain"), reference(MAP_TWO, "missing"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, sourceRoot, Collections.<PersistedNodeId>emptySet(), true),
            map(MAP_TWO, 2, targetRoot));

        RelationshipResolution resolution = projection.relationshipResolutions().get(0);
        assertRecoverable(resolution, RecoverableReason.NODE_INACCESSIBLE);
        assertThat(resolution.target()).isEmpty();
    }

    @Test
    public void retainAResolvedSourceWhenTheTargetIsRecoverablyUnavailable() {
        NodeSnapshot source = node(MAP_ONE, "source", true, false, false);
        NodeSnapshot target = node(MAP_TWO, "target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.singletonList(
                relationship(1, reference(MAP_ONE, "source"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.PASSWORD_REQUIRED),
            map(MAP_ONE, 1, source), map(MAP_TWO, 2, target));

        RelationshipResolution resolution = projection.relationshipResolutions().get(0);
        assertThat(resolution.status()).isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(resolution.source()).contains(ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source.key())));
        assertThat(resolution.target()).isEmpty();
        assertThat(resolution.recoverableReasons()).containsExactly(RecoverableReason.MAP_PASSWORD_REQUIRED);
    }

    @Test
    public void aggregateDistinctSourceAndTargetRecoverableReasonsInDeclarationOrder() {
        NodeSnapshot source = node(MAP_ONE, "source", true, false, false);
        NodeSnapshot target = node(MAP_TWO, "target", true, false, false);
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.singletonList(
                relationship(1, reference(MAP_ONE, "source"), reference(MAP_TWO, "target"))),
            Collections.<PinRecord>emptyList());

        GraphProjection projection = project(workspace, availability(workspace,
            MapAvailability.LOADING, MapAvailability.PASSWORD_REQUIRED),
            map(MAP_ONE, 1, source), map(MAP_TWO, 2, target));

        RelationshipResolution resolution = projection.relationshipResolutions().get(0);
        assertThat(resolution.status()).isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(resolution.source()).isEmpty();
        assertThat(resolution.target()).isEmpty();
        assertThat(resolution.recoverableReasons()).containsExactly(
            RecoverableReason.MAP_LOADING, RecoverableReason.MAP_PASSWORD_REQUIRED);
    }

    @Test
    public void connectorsToPlainNodesDoNotResolve() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", false, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, leaf, group);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        MapSnapshot map = map(MAP_ONE, 1, root, connector(MAP_ONE, "leaf", "group"));

        GraphProjection projection = project(workspace, map);

        assertThat(projection.edges()).isEmpty();
        assertThat(projection.relationshipResolutions()).isEmpty();
    }

    @Test
    public void connectorsToGroupDescendantsFoldToTheBoundary() {
        NodeSnapshot inner = node(MAP_ONE, "inner", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", false, true, false, inner);
        NodeSnapshot otherGroup = node(MAP_ONE, "other-group", false, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, group, otherGroup);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        MapSnapshot map = map(MAP_ONE, 1, root, connector(MAP_ONE, "inner", "other-group"));

        GraphProjection projection = project(workspace, map);

        assertThat(projection.edges()).hasSize(1);
        ProjectedEdge edge = projection.edges().get(0);
        assertThat(edge.first().isEnclosure()).isTrue();
        assertThat(edge.second().isEnclosure()).isTrue();
        assertThat(edge.first().enclosure().get().source()).isEqualTo(group.key());
        assertThat(edge.second().enclosure().get().source()).isEqualTo(otherGroup.key());
    }

    @Test
    public void connectorsInsideAGroupDoNotCreateSelfLoops() {
        NodeSnapshot inner = node(MAP_ONE, "inner", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", false, true, false, inner);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, group);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        MapSnapshot map = map(MAP_ONE, 1, root, connector(MAP_ONE, "inner", "group"));

        GraphProjection projection = project(workspace, map);

        assertThat(projection.edges()).isEmpty();
    }

    @Test
    public void activateOnlyPinsWhoseNodesAreMapRootsOrGroupMarked() {
        NodeSnapshot visible = node(MAP_ONE, "visible", true, false, false);
        NodeSnapshot groupedDescendant = node(MAP_ONE, "grouped-descendant", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", false, true, false, groupedDescendant);
        NodeSnapshot enclosureLeaf = node(MAP_ONE, "enclosure-leaf", true, false, false);
        NodeSnapshot enclosure = node(MAP_ONE, "enclosure", false, false, false, enclosureLeaf);
        NodeSnapshot excluded = node(MAP_ONE, "excluded", true, false, true);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, visible, group, enclosure, excluded);
        NodeSnapshot unavailable = node(MAP_TWO, "unavailable", true, false, false);
        List<PinRecord> pins = Arrays.asList(
            pin(MAP_ONE, "visible", 1, 2), pin(MAP_ONE, "group", 3, 4),
            pin(MAP_ONE, "grouped-descendant", 5, 6), pin(MAP_ONE, "enclosure", 7, 8),
            pin(MAP_ONE, "excluded", 9, 10), pin(MAP_TWO, "unavailable", 11, 12),
            pin(MAP_ONE, "root", 13, 14));
        WorkspaceDocument workspace = workspace(
            registrations(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true)),
            Collections.<GraphRelationshipRecord>emptyList(), pins);

        GraphProjection first = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.PASSWORD_REQUIRED),
            map(MAP_ONE, 1, root), map(MAP_TWO, 2, unavailable));

        assertActivePin(first, reference(MAP_ONE, "group"));
        assertActivePin(first, reference(MAP_ONE, "root"));
        assertDormantPin(first, reference(MAP_ONE, "visible"));
        assertDormantPin(first, reference(MAP_ONE, "grouped-descendant"));
        assertDormantPin(first, reference(MAP_ONE, "enclosure"));
        assertDormantPin(first, reference(MAP_ONE, "excluded"));
        assertDormantPin(first, reference(MAP_TWO, "unavailable"));
        assertThat(pin(first, reference(MAP_ONE, "grouped-descendant")).x()).isEqualTo(5.0);
        assertThat(pin(first, reference(MAP_ONE, "grouped-descendant")).y()).isEqualTo(6.0);

        NodeSnapshot reactivatedDescendant = node(MAP_ONE, "grouped-descendant", true, true, false);
        NodeSnapshot formerGroup = node(MAP_ONE, "group", false, false, false, reactivatedDescendant);
        NodeSnapshot reactivatedExcluded = node(MAP_ONE, "excluded", true, false, false);
        NodeSnapshot reactivatedRoot = node(MAP_ONE, "root", false, false, false,
            visible, formerGroup, enclosure, reactivatedExcluded);
        GraphProjection second = project(workspace, availability(workspace,
            MapAvailability.AVAILABLE, MapAvailability.AVAILABLE),
            map(MAP_ONE, 1, reactivatedRoot), map(MAP_TWO, 2, unavailable));

        assertActivePin(second, reference(MAP_ONE, "grouped-descendant"));
        assertActivePin(second, reference(MAP_ONE, "root"));
        assertActivePin(second, reference(MAP_TWO, "unavailable"));
        assertDormantPin(second, reference(MAP_ONE, "group"));
        assertDormantPin(second, reference(MAP_ONE, "visible"));
        assertDormantPin(second, reference(MAP_ONE, "enclosure"));
        assertDormantPin(second, reference(MAP_ONE, "excluded"));
    }

    private static void assertSourceEndpoint(RelationshipResolution resolution, ProjectedEndpointKey expected) {
        assertThat(resolution.source()).contains(expected);
        assertThat(resolution.target()).isPresent();
    }

    private static void assertRecoverable(RelationshipResolution resolution, RecoverableReason reason) {
        assertThat(resolution.status()).isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(resolution.source()).isEmpty();
        assertThat(resolution.recoverableReasons()).containsExactly(reason);
    }

    private static void assertActivePin(GraphProjection projection, NodeReference source) {
        PinProjection pin = pin(projection, source);
        assertThat(pin.active()).isTrue();
        assertThat(pin.dormant()).isFalse();
        assertThat(pin.projectedNode()).isPresent();
        assertThat(pin.projectedNode().get().source().persistedReference()).contains(source);
    }

    private static void assertDormantPin(GraphProjection projection, NodeReference source) {
        PinProjection pin = pin(projection, source);
        assertThat(pin.active()).isFalse();
        assertThat(pin.dormant()).isTrue();
        assertThat(pin.projectedNode()).isEmpty();
    }

    private static PinProjection pin(GraphProjection projection, NodeReference source) {
        for (PinProjection candidate : projection.pins()) {
            if (candidate.source().equals(source)) {
                return candidate;
            }
        }
        throw new AssertionError("Missing pin for " + source);
    }

    private static GraphProjection project(WorkspaceDocument workspace, Map<MapReferenceId, MapAvailability> statuses,
            MapSnapshot... maps) {
        return new ProjectionEngine().project(ProjectionInput.of(7, workspace, Arrays.asList(maps), statuses));
    }

    private static GraphProjection project(WorkspaceDocument workspace, MapSnapshot... maps) {
        return new ProjectionEngine().project(ProjectionInput.of(7, workspace, Arrays.asList(maps),
            allAvailable(workspace)));
    }

    private static Map<MapReferenceId, MapAvailability> allAvailable(WorkspaceDocument workspace) {
        Map<MapReferenceId, MapAvailability> result = new LinkedHashMap<MapReferenceId, MapAvailability>();
        for (MapReference registration : workspace.maps()) {
            result.put(registration.id(), MapAvailability.AVAILABLE);
        }
        return result;
    }

    private static WorkspaceDocument workspace(MapReference... registrations) {
        return workspace(registrations(registrations), Collections.<GraphRelationshipRecord>emptyList(),
            Collections.<PinRecord>emptyList());
    }

    private static WorkspaceDocument workspace(List<MapReference> registrations,
            List<GraphRelationshipRecord> relationships, List<PinRecord> pins) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder()
            .maps(registrations)
            .relationships(relationships)
            .pins(pins)
            .build();
    }

    private static List<MapReference> registrations(MapReference... values) {
        return Arrays.asList(values);
    }

    private static List<GraphRelationshipRecord> relationships(GraphRelationshipRecord... values) {
        return Arrays.asList(values);
    }

    private static MapReference registration(MapReferenceId id, long sequence, boolean active) {
        return MapReference.of(id, sequence, URI.create("maps/" + id.value() + ".mm"), active, "#4E79A7",
            Collections.<UnknownXml>emptyList());
    }

    private static GraphRelationshipRecord relationship(long sequence, NodeReference source, NodeReference target) {
        return GraphRelationshipRecord.of(RelationshipId.of(String.format(
            "10000000-0000-0000-0000-%012d", Long.valueOf(sequence))), sequence, source, target,
            RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
    }

    private static PinRecord pin(MapReferenceId map, String id, double x, double y) {
        return PinRecord.of(reference(map, id), x, y, Collections.<UnknownXml>emptyList());
    }

    private static Map<MapReferenceId, MapAvailability> availability(WorkspaceDocument workspace,
            MapAvailability... values) {
        if (workspace.maps().size() != values.length) {
            throw new IllegalArgumentException("Availability count must match registrations");
        }
        Map<MapReferenceId, MapAvailability> result = new LinkedHashMap<MapReferenceId, MapAvailability>();
        for (int index = 0; index < values.length; index++) {
            result.put(workspace.maps().get(index).id(), values[index]);
        }
        return result;
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root) {
        return map(id, workspaceOrder, root, Collections.<PersistedNodeId>emptySet(), false);
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root,
            ConnectorSnapshot... connectors) {
        return map(id, workspaceOrder, root).withConnectors(Arrays.asList(connectors));
    }

    private static ConnectorSnapshot connector(MapReferenceId map, String sourceId, String targetId) {
        return ConnectorSnapshot.of(0, ConnectorDescriptor.of(SourceNodeKey.persisted(reference(map, sourceId)),
            reference(map, targetId), false, false, "source", "middle", "target"));
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root,
            Set<PersistedNodeId> attachedPersistentIds, boolean hasInaccessibleBranch) {
        return MapSnapshot.of(id, workspaceOrder, "Map " + id.value(), root, attachedPersistentIds,
            hasInaccessibleBranch);
    }

    private static NodeSnapshot node(MapReferenceId map, String id, boolean structuralLeaf,
            boolean graphGroup, boolean excluded, NodeSnapshot... children) {
        return NodeSnapshot.of(SourceNodeKey.persisted(reference(map, id)), SafeNodeLabel.of(id, id), structuralLeaf,
            graphGroup, excluded, Arrays.asList(children));
    }

    private static NodeReference reference(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static MapReferenceId mapId(String value) {
        return MapReferenceId.of(value);
    }
}
