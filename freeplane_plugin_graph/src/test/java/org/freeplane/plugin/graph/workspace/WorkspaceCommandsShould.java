package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
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

public class WorkspaceCommandsShould {
    private static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_THREE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000003");
    private static final MapReferenceId MAP_FOUR =
        MapReferenceId.of("00000000-0000-0000-0000-000000000004");
    private static final MapReferenceId MAP_EIGHT =
        MapReferenceId.of("00000000-0000-0000-0000-000000000008");
    private static final MapReferenceId MAP_NINE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000009");
    private static final RelationshipId RELATIONSHIP_ONE =
        RelationshipId.of("00000000-0000-0000-0000-000000000101");
    private static final RelationshipId RELATIONSHIP_TWO =
        RelationshipId.of("00000000-0000-0000-0000-000000000102");
    private static final RelationshipId RELATIONSHIP_THREE =
        RelationshipId.of("00000000-0000-0000-0000-000000000103");
    private static final RelationshipId MISSING_FIRST =
        RelationshipId.of("00000000-0000-0000-0000-000000000901");
    private static final RelationshipId MISSING_SECOND =
        RelationshipId.of("00000000-0000-0000-0000-000000000902");

    @Test
    public void addMapsWithMonotonicSequencesAcrossGapsAndCycleThePalette() {
        WorkspaceDocument before = document(Arrays.asList(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
            map(MAP_TWO, 3, "maps/two.mm", true, "#59A14F", noUnknownXml())), noRelationships(), noPins());

        WorkspaceTransition transition = WorkspaceCommands.addMap(MAP_THREE, URI.create("maps/three.mm"))
            .apply(before);

        assertThat(transition.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(transition.messageKey()).isEqualTo("graph_workspace.map.added");
        assertThat(transition.messageArguments()).containsExactly(MAP_THREE);
        assertThat(transition.after().maps()).containsExactly(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
            map(MAP_TWO, 3, "maps/two.mm", true, "#59A14F", noUnknownXml()),
            map(MAP_THREE, 4, "maps/three.mm", true, "#E15759", noUnknownXml()));

        WorkspaceDocument eighth = document(Collections.singletonList(
            map(MAP_EIGHT, 8, "maps/eight.mm", true, "#9C755F", noUnknownXml())),
            noRelationships(), noPins());
        WorkspaceTransition ninth = WorkspaceCommands.addMap(MAP_NINE, URI.create("maps/nine.mm")).apply(eighth);

        assertThat(ninth.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(ninth.after().maps().get(1)).isEqualTo(
            map(MAP_NINE, 9, "maps/nine.mm", true, "#4E79A7", noUnknownXml()));
    }

    @Test
    public void reactivateAnExactUriWithoutChangingItsRegistrationAndRejectConflictingProposedIds() {
        UnknownXml mapUnknown = unknown("map", "kept");
        WorkspaceDocument before = document(Collections.singletonList(
            map(MAP_ONE, 7, "maps/one.mm", false, "#EDC948", Collections.singletonList(mapUnknown))),
            noRelationships(), noPins());

        WorkspaceTransition reactivated = WorkspaceCommands.addMap(MAP_THREE, URI.create("maps/one.mm"))
            .apply(before);

        assertThat(reactivated.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(reactivated.messageKey()).isEqualTo("graph_workspace.map.reactivated");
        assertThat(reactivated.messageArguments()).containsExactly(MAP_ONE);
        assertThat(reactivated.after().maps()).containsExactly(
            map(MAP_ONE, 7, "maps/one.mm", true, "#EDC948", Collections.singletonList(mapUnknown)));

        WorkspaceTransition unchanged = WorkspaceCommands.addMap(MAP_FOUR, URI.create("maps/one.mm"))
            .apply(reactivated.after());
        assertThat(unchanged.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(unchanged.messageKey()).isEqualTo("graph_workspace.command.no_change");
        assertThat(unchanged.messageArguments()).containsExactly("addMap");

        WorkspaceDocument withOtherId = document(Arrays.asList(
            before.maps().get(0), map(MAP_TWO, 8, "maps/two.mm", true, "#9C755F", noUnknownXml())),
            noRelationships(), noPins());
        WorkspaceTransition conflicting = WorkspaceCommands.addMap(MAP_TWO, URI.create("maps/one.mm"))
            .apply(withOtherId);
        assertThat(conflicting.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(conflicting.messageKey()).isEqualTo("graph_workspace.map.id_in_use");
        assertThat(conflicting.messageArguments()).containsExactly(MAP_TWO);
        assertThat(conflicting.after()).isEqualTo(withOtherId);
    }

    @Test
    public void removeAndReactivateMapsWithoutCascadingRelationshipsOrPins() {
        NodeReference source = node(MAP_ONE, "source");
        NodeReference target = node(MAP_TWO, "target");
        GraphRelationshipRecord relationship = relationship(RELATIONSHIP_ONE, 1, source, target,
            RelationshipDirection.FORWARD, noUnknownXml());
        PinRecord pin = PinRecord.of(source, 2, 3, noUnknownXml());
        WorkspaceDocument before = document(Arrays.asList(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
            map(MAP_TWO, 2, "maps/two.mm", true, "#F28E2B", noUnknownXml())),
            Collections.singletonList(relationship), Collections.singletonList(pin));

        WorkspaceTransition removed = WorkspaceCommands.removeMap(MAP_ONE).apply(before);

        assertThat(removed.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(removed.messageKey()).isEqualTo("graph_workspace.map.removed");
        assertThat(removed.messageArguments()).containsExactly(MAP_ONE);
        assertThat(removed.after().maps().get(0).active()).isFalse();
        assertThat(removed.after().relationships()).containsExactly(relationship);
        assertThat(removed.after().pins()).containsExactly(pin);

        WorkspaceTransition reactivated = WorkspaceCommands.reactivateMap(MAP_ONE).apply(removed.after());
        assertThat(reactivated.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(reactivated.messageKey()).isEqualTo("graph_workspace.map.reactivated");
        assertThat(reactivated.after().maps().get(0).active()).isTrue();

        WorkspaceTransition alreadyActive = WorkspaceCommands.reactivateMap(MAP_ONE).apply(reactivated.after());
        assertThat(alreadyActive.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(alreadyActive.messageArguments()).containsExactly("reactivateMap");

        WorkspaceTransition missing = WorkspaceCommands.removeMap(MAP_THREE).apply(reactivated.after());
        assertThat(missing.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(missing.messageKey()).isEqualTo("graph_workspace.map.not_found");
        assertThat(missing.messageArguments()).containsExactly(MAP_THREE);
    }

    @Test
    public void locateMapsOnlyByExplicitlyRebindingUris() {
        UnknownXml mapUnknown = unknown("map", "kept");
        WorkspaceDocument before = document(Arrays.asList(
            map(MAP_ONE, 5, "maps/one.mm", false, "#76B7B2", Collections.singletonList(mapUnknown)),
            map(MAP_TWO, 6, "maps/two.mm", true, "#B07AA1", noUnknownXml())),
            noRelationships(), noPins());

        WorkspaceTransition located = WorkspaceCommands.locateMap(MAP_ONE, URI.create("moved/one.mm"))
            .apply(before);

        assertThat(located.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(located.messageKey()).isEqualTo("graph_workspace.map.located");
        assertThat(located.messageArguments()).containsExactly(MAP_ONE);
        assertThat(located.after().maps().get(0)).isEqualTo(
            map(MAP_ONE, 5, "moved/one.mm", false, "#76B7B2", Collections.singletonList(mapUnknown)));

        WorkspaceTransition sameUri = WorkspaceCommands.locateMap(MAP_ONE, URI.create("moved/one.mm"))
            .apply(located.after());
        assertThat(sameUri.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(sameUri.messageArguments()).containsExactly("locateMap");

        WorkspaceTransition duplicateUri = WorkspaceCommands.locateMap(MAP_ONE, URI.create("maps/two.mm"))
            .apply(before);
        assertThat(duplicateUri.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(duplicateUri.messageKey()).isEqualTo("graph_workspace.map.uri_in_use");
        assertThat(duplicateUri.messageArguments()).containsExactly(URI.create("maps/two.mm"));
    }

    @Test
    public void rejectSequenceOverflowAndInvalidModelValuesWithoutMutatingTheDocument() {
        WorkspaceDocument mapOverflow = document(Collections.singletonList(
            map(MAP_ONE, Long.MAX_VALUE, "maps/one.mm", true, "#4E79A7", noUnknownXml())),
            noRelationships(), noPins());
        WorkspaceTransition mapResult = WorkspaceCommands.addMap(MAP_TWO, URI.create("maps/two.mm"))
            .apply(mapOverflow);

        assertThat(mapResult.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(mapResult.after()).isEqualTo(mapOverflow);

        WorkspaceDocument relationshipOverflow = document(Arrays.asList(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
            map(MAP_TWO, 2, "maps/two.mm", true, "#F28E2B", noUnknownXml())),
            Collections.singletonList(relationship(RELATIONSHIP_ONE, Long.MAX_VALUE,
                node(MAP_ONE, "one"), node(MAP_TWO, "two"), RelationshipDirection.FORWARD, noUnknownXml())),
            noPins());
        WorkspaceTransition relationshipResult = WorkspaceCommands.createRelationship(RELATIONSHIP_TWO,
            node(MAP_ONE, "other"), node(MAP_TWO, "other"), RelationshipDirection.FORWARD)
            .apply(relationshipOverflow);

        assertThat(relationshipResult.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(relationshipResult.after()).isEqualTo(relationshipOverflow);

        WorkspaceDocument empty = emptyDocument();
        WorkspaceTransition invalidUri = WorkspaceCommands.addMap(MAP_ONE, URI.create("maps/one.mm?bad=true"))
            .apply(empty);
        assertThat(invalidUri.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(invalidUri.after()).isEqualTo(empty);
    }

    @Test
    public void createRelationshipsWithCanonicalBidirectionalAndUndirectedEndpointsAndDetectDuplicates() {
        WorkspaceDocument before = twoMapDocument();
        NodeReference first = node(MAP_ONE, "a");
        NodeReference second = node(MAP_TWO, "z");

        WorkspaceTransition bidirectional = WorkspaceCommands.createRelationship(RELATIONSHIP_ONE, second, first,
            RelationshipDirection.BIDIRECTIONAL).apply(before);

        assertThat(bidirectional.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(bidirectional.messageKey()).isEqualTo("graph_workspace.relationship.created");
        assertThat(bidirectional.messageArguments()).containsExactly(RELATIONSHIP_ONE);
        assertThat(bidirectional.after().relationships()).containsExactly(
            relationship(RELATIONSHIP_ONE, 1, first, second, RelationshipDirection.BIDIRECTIONAL, noUnknownXml()));

        WorkspaceTransition duplicate = WorkspaceCommands.createRelationship(RELATIONSHIP_TWO, first, second,
            RelationshipDirection.BIDIRECTIONAL).apply(bidirectional.after());
        assertThat(duplicate.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(duplicate.messageKey()).isEqualTo("graph_workspace.relationship.duplicate");
        assertThat(duplicate.messageArguments()).containsExactly(RELATIONSHIP_ONE);

        WorkspaceTransition forwardReverse = WorkspaceCommands.createRelationship(RELATIONSHIP_TWO, second, first,
            RelationshipDirection.FORWARD).apply(bidirectional.after());
        assertThat(forwardReverse.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(forwardReverse.after().relationships().get(1).source()).isEqualTo(second);
        assertThat(forwardReverse.after().relationships().get(1).target()).isEqualTo(first);

        WorkspaceTransition undirected = WorkspaceCommands.createRelationship(RELATIONSHIP_THREE, second, first,
            RelationshipDirection.UNDIRECTED).apply(forwardReverse.after());
        assertThat(undirected.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(undirected.after().relationships().get(2).source()).isEqualTo(first);
        assertThat(undirected.after().relationships().get(2).target()).isEqualTo(second);
    }

    @Test
    public void updateRelationshipsWithoutChangingTheirIdentitySequenceOrUnknownXml() {
        UnknownXml relationshipUnknown = unknown("relationship", "kept");
        NodeReference oldSource = node(MAP_ONE, "old-source");
        NodeReference oldTarget = node(MAP_TWO, "old-target");
        NodeReference source = node(MAP_ONE, "source");
        NodeReference target = node(MAP_TWO, "target");
        GraphRelationshipRecord first = relationship(RELATIONSHIP_ONE, 7, oldSource, oldTarget,
            RelationshipDirection.FORWARD, Collections.singletonList(relationshipUnknown));
        GraphRelationshipRecord second = relationship(RELATIONSHIP_TWO, 8, node(MAP_ONE, "other-source"),
            node(MAP_TWO, "other-target"), RelationshipDirection.FORWARD, noUnknownXml());
        WorkspaceDocument before = document(twoMaps(), Arrays.asList(first, second), noPins());

        WorkspaceTransition updated = WorkspaceCommands.updateRelationship(RELATIONSHIP_ONE, target, source,
            RelationshipDirection.BIDIRECTIONAL).apply(before);

        assertThat(updated.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(updated.messageKey()).isEqualTo("graph_workspace.relationship.updated");
        GraphRelationshipRecord changed = updated.after().relationships().get(0);
        assertThat(changed.id()).isEqualTo(RELATIONSHIP_ONE);
        assertThat(changed.sequence()).isEqualTo(7);
        assertThat(changed.source()).isEqualTo(source);
        assertThat(changed.target()).isEqualTo(target);
        assertThat(changed.direction()).isEqualTo(RelationshipDirection.BIDIRECTIONAL);
        assertThat(changed.unknownXml()).containsExactly(relationshipUnknown);

        WorkspaceTransition unchanged = WorkspaceCommands.updateRelationship(RELATIONSHIP_ONE, source, target,
            RelationshipDirection.BIDIRECTIONAL).apply(updated.after());
        assertThat(unchanged.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(unchanged.messageArguments()).containsExactly("updateRelationship");

        WorkspaceTransition duplicate = WorkspaceCommands.updateRelationship(RELATIONSHIP_TWO, source, target,
            RelationshipDirection.BIDIRECTIONAL).apply(updated.after());
        assertThat(duplicate.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(duplicate.messageKey()).isEqualTo("graph_workspace.relationship.duplicate");
        assertThat(duplicate.messageArguments()).containsExactly(RELATIONSHIP_ONE);
    }

    @Test
    public void deleteRelationshipsAndRejectMissingOrInvalidRelationshipRequests() {
        WorkspaceDocument before = document(twoMaps(), Collections.singletonList(relationship(RELATIONSHIP_ONE, 1,
            node(MAP_ONE, "source"), node(MAP_TWO, "target"), RelationshipDirection.FORWARD, noUnknownXml())),
            noPins());

        WorkspaceTransition deleted = WorkspaceCommands.deleteRelationship(RELATIONSHIP_ONE).apply(before);
        assertThat(deleted.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(deleted.messageKey()).isEqualTo("graph_workspace.relationship.deleted");
        assertThat(deleted.messageArguments()).containsExactly(RELATIONSHIP_ONE);
        assertThat(deleted.after().relationships()).isEmpty();

        WorkspaceTransition missing = WorkspaceCommands.deleteRelationship(RELATIONSHIP_TWO).apply(deleted.after());
        assertThat(missing.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(missing.messageKey()).isEqualTo("graph_workspace.relationship.not_found");
        assertThat(missing.messageArguments()).containsExactly(RELATIONSHIP_TWO);

        WorkspaceTransition sameMap = WorkspaceCommands.createRelationship(RELATIONSHIP_TWO,
            node(MAP_ONE, "one"), node(MAP_ONE, "two"), RelationshipDirection.FORWARD).apply(before);
        assertThat(sameMap.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(sameMap.messageKey()).isEqualTo("graph_workspace.relationship.invalid");
        assertThat(sameMap.messageArguments()).containsExactly(RELATIONSHIP_TWO);

        WorkspaceTransition unregistered = WorkspaceCommands.updateRelationship(RELATIONSHIP_ONE,
            node(MAP_THREE, "missing"), node(MAP_TWO, "target"), RelationshipDirection.FORWARD).apply(before);
        assertThat(unregistered.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(unregistered.messageKey()).isEqualTo("graph_workspace.relationship.invalid");
        assertThat(unregistered.after()).isEqualTo(before);
    }

    @Test
    public void setAndRemovePinsWhilePreservingExistingUnknownXml() {
        NodeReference first = node(MAP_ONE, "first");
        NodeReference second = node(MAP_ONE, "second");
        UnknownXml pinUnknown = unknown("pin", "kept");
        WorkspaceDocument before = document(Collections.singletonList(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml())), noRelationships(),
            Collections.singletonList(PinRecord.of(first, 1, 2, Collections.singletonList(pinUnknown))));

        WorkspaceTransition updated = WorkspaceCommands.pin(first, 3, 4).apply(before);
        assertThat(updated.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(updated.messageKey()).isEqualTo("graph_workspace.pin.set");
        assertThat(updated.after().pins()).containsExactly(
            PinRecord.of(first, 3, 4, Collections.singletonList(pinUnknown)));

        WorkspaceTransition noChange = WorkspaceCommands.pin(first, 3, 4).apply(updated.after());
        assertThat(noChange.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(noChange.messageArguments()).containsExactly("pin");

        WorkspaceTransition added = WorkspaceCommands.pin(second, -1, 8).apply(updated.after());
        assertThat(added.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(added.after().pins().get(1)).isEqualTo(PinRecord.of(second, -1, 8, noUnknownXml()));

        WorkspaceTransition removed = WorkspaceCommands.unpin(first).apply(added.after());
        assertThat(removed.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(removed.messageKey()).isEqualTo("graph_workspace.pin.removed");
        assertThat(removed.messageArguments()).containsExactly(first);

        WorkspaceTransition missing = WorkspaceCommands.unpin(first).apply(removed.after());
        assertThat(missing.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(missing.messageArguments()).containsExactly("unpin");
    }

    @Test
    public void rejectInvalidPinsAndRemoveAllExistingPins() {
        NodeReference first = node(MAP_ONE, "first");
        NodeReference second = node(MAP_ONE, "second");
        WorkspaceDocument before = document(Collections.singletonList(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml())), noRelationships(),
            Arrays.asList(PinRecord.of(first, 1, 2, noUnknownXml()), PinRecord.of(second, 3, 4, noUnknownXml())));

        WorkspaceTransition nan = WorkspaceCommands.pin(first, Double.NaN, 0).apply(before);
        assertThat(nan.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(nan.messageKey()).isEqualTo("graph_workspace.pin.invalid");
        assertThat(nan.messageArguments()).containsExactly(first);

        NodeReference missingMap = node(MAP_TWO, "missing");
        WorkspaceTransition missing = WorkspaceCommands.pin(missingMap, 0, 0).apply(before);
        assertThat(missing.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(missing.messageKey()).isEqualTo("graph_workspace.pin.invalid");
        assertThat(missing.messageArguments()).containsExactly(missingMap);

        WorkspaceTransition removed = WorkspaceCommands.unpinAll().apply(before);
        assertThat(removed.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(removed.messageKey()).isEqualTo("graph_workspace.pin.all_removed");
        assertThat(removed.messageArguments()).containsExactly(2);
        assertThat(removed.after().pins()).isEmpty();

        WorkspaceTransition empty = WorkspaceCommands.unpinAll().apply(removed.after());
        assertThat(empty.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(empty.messageArguments()).containsExactly("unpinAll");
    }

    @Test
    public void replaceCompleteDisplaySettingsAndTreatEqualSettingsAsNoChange() {
        WorkspaceDocument before = emptyDocument();
        DisplaySettings settings = DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, false, false,
            Collections.singletonList(unknown("display", "kept")));

        WorkspaceTransition updated = WorkspaceCommands.setDisplaySettings(settings).apply(before);
        assertThat(updated.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(updated.messageKey()).isEqualTo("graph_workspace.display.updated");
        assertThat(updated.messageArguments()).isEmpty();
        assertThat(updated.after().displaySettings()).isEqualTo(settings);

        WorkspaceTransition unchanged = WorkspaceCommands.setDisplaySettings(settings).apply(updated.after());
        assertThat(unchanged.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(unchanged.messageArguments()).containsExactly("setDisplaySettings");
    }

    @Test
    public void purgeOnlyExactRelationshipsAtomicallyAndCopyTheRequestedIds() {
        GraphRelationshipRecord first = relationship(RELATIONSHIP_ONE, 1, node(MAP_ONE, "one"),
            node(MAP_TWO, "one"), RelationshipDirection.FORWARD, noUnknownXml());
        GraphRelationshipRecord second = relationship(RELATIONSHIP_TWO, 2, node(MAP_ONE, "two"),
            node(MAP_TWO, "two"), RelationshipDirection.FORWARD, noUnknownXml());
        PinRecord pin = PinRecord.of(node(MAP_ONE, "pin"), 5, 6, noUnknownXml());
        WorkspaceDocument before = document(twoMaps(), Arrays.asList(first, second), Collections.singletonList(pin));
        Set<RelationshipId> requested = new HashSet<RelationshipId>();
        requested.add(RELATIONSHIP_ONE);
        WorkspaceCommand purge = WorkspaceCommands.purgeRelationships(requested);
        requested.clear();

        WorkspaceTransition purged = purge.apply(before);
        assertThat(purged.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(purged.messageKey()).isEqualTo("graph_workspace.relationships.purged");
        assertThat(purged.messageArguments()).containsExactly(1);
        assertThat(purged.after().relationships()).containsExactly(second);
        assertThat(purged.after().maps()).isEqualTo(before.maps());
        assertThat(purged.after().pins()).containsExactly(pin);

        Set<RelationshipId> missingIds = new LinkedHashSet<RelationshipId>();
        missingIds.add(MISSING_SECOND);
        missingIds.add(MISSING_FIRST);
        WorkspaceTransition missing = WorkspaceCommands.purgeRelationships(missingIds).apply(before);
        assertThat(missing.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(missing.messageKey()).isEqualTo("graph_workspace.purge.relationship_not_found");
        assertThat(missing.messageArguments()).containsExactly(MISSING_FIRST);
        assertThat(missing.after()).isEqualTo(before);

        WorkspaceTransition empty = WorkspaceCommands.purgeRelationships(Collections.<RelationshipId>emptySet())
            .apply(before);
        assertThat(empty.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(empty.messageArguments()).containsExactly("purgeRelationships");
    }

    @Test
    public void rejectNullFactoryArgumentsImmediately() {
        NodeReference source = node(MAP_ONE, "source");
        NodeReference target = node(MAP_TWO, "target");

        assertThatThrownBy(() -> WorkspaceCommands.addMap(null, URI.create("maps/one.mm")))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.reactivateMap(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.removeMap(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.locateMap(MAP_ONE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.createRelationship(null, source, target,
            RelationshipDirection.FORWARD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.updateRelationship(null, source, target,
            RelationshipDirection.FORWARD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.deleteRelationship(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.pin(null, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.unpin(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.setDisplaySettings(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkspaceCommands.purgeRelationships(null)).isInstanceOf(NullPointerException.class);
    }

    private static WorkspaceDocument emptyDocument() {
        return WorkspaceDocument.createVersion1(WORKSPACE_ID);
    }

    private static WorkspaceDocument twoMapDocument() {
        return document(twoMaps(), noRelationships(), noPins());
    }

    private static List<MapReference> twoMaps() {
        return Arrays.asList(
            map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
            map(MAP_TWO, 2, "maps/two.mm", true, "#F28E2B", noUnknownXml()));
    }

    private static WorkspaceDocument document(List<MapReference> maps, List<GraphRelationshipRecord> relationships,
            List<PinRecord> pins) {
        return emptyDocument().toBuilder()
            .maps(maps)
            .relationships(relationships)
            .pins(pins)
            .build();
    }

    private static MapReference map(MapReferenceId id, long sequence, String uri, boolean active, String color,
            List<UnknownXml> unknownXml) {
        return MapReference.of(id, sequence, URI.create(uri), active, color, unknownXml);
    }

    private static GraphRelationshipRecord relationship(RelationshipId id, long sequence, NodeReference source,
            NodeReference target, RelationshipDirection direction, List<UnknownXml> unknownXml) {
        return GraphRelationshipRecord.of(id, sequence, source, target, direction, unknownXml);
    }

    private static NodeReference node(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static List<GraphRelationshipRecord> noRelationships() {
        return Collections.emptyList();
    }

    private static List<PinRecord> noPins() {
        return Collections.emptyList();
    }

    private static List<UnknownXml> noUnknownXml() {
        return Collections.emptyList();
    }

    private static UnknownXml unknown(String name, String value) {
        return UnknownXml.attribute(UnknownXml.Owner.RECORD, new QName("urn:test", name), value);
    }
}
