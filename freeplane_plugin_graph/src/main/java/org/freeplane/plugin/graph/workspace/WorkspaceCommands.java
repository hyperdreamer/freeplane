package org.freeplane.plugin.graph.workspace;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceCommands {
    private static final String[] PALETTE = new String[] {
        "#4E79A7", "#F28E2B", "#59A14F", "#E15759",
        "#76B7B2", "#B07AA1", "#EDC948", "#9C755F"
    };
    private static final String NO_CHANGE = "graph_workspace.command.no_change";
    private static final String MAP_ADDED = "graph_workspace.map.added";
    private static final String MAP_REACTIVATED = "graph_workspace.map.reactivated";
    private static final String MAP_REMOVED = "graph_workspace.map.removed";
    private static final String MAP_LOCATED = "graph_workspace.map.located";
    private static final String MAP_NOT_FOUND = "graph_workspace.map.not_found";
    private static final String MAP_ID_IN_USE = "graph_workspace.map.id_in_use";
    private static final String MAP_URI_IN_USE = "graph_workspace.map.uri_in_use";
    private static final String RELATIONSHIP_CREATED = "graph_workspace.relationship.created";
    private static final String RELATIONSHIP_UPDATED = "graph_workspace.relationship.updated";
    private static final String RELATIONSHIP_DELETED = "graph_workspace.relationship.deleted";
    private static final String RELATIONSHIP_NOT_FOUND = "graph_workspace.relationship.not_found";
    private static final String RELATIONSHIP_ID_IN_USE = "graph_workspace.relationship.id_in_use";
    private static final String RELATIONSHIP_INVALID = "graph_workspace.relationship.invalid";
    private static final String RELATIONSHIP_DUPLICATE = "graph_workspace.relationship.duplicate";
    private static final String PIN_SET = "graph_workspace.pin.set";
    private static final String PIN_REMOVED = "graph_workspace.pin.removed";
    private static final String PIN_INVALID = "graph_workspace.pin.invalid";
    private static final String PINS_REMOVED = "graph_workspace.pin.all_removed";
    private static final String DISPLAY_UPDATED = "graph_workspace.display.updated";
    private static final String RELATIONSHIPS_PURGED = "graph_workspace.relationships.purged";
    private static final String PURGE_NOT_FOUND = "graph_workspace.purge.relationship_not_found";

    private WorkspaceCommands() {
    }

    public static WorkspaceCommand addMap(final MapReferenceId proposedId, final URI storedUri) {
        return new AddMapCommand(Objects.requireNonNull(proposedId, "proposedId"),
            Objects.requireNonNull(storedUri, "storedUri"));
    }

    public static WorkspaceCommand reactivateMap(final MapReferenceId id) {
        return new ReactivateMapCommand(Objects.requireNonNull(id, "id"));
    }

    public static WorkspaceCommand removeMap(final MapReferenceId id) {
        return new RemoveMapCommand(Objects.requireNonNull(id, "id"));
    }

    public static WorkspaceCommand locateMap(final MapReferenceId id, final URI replacementUri) {
        return new LocateMapCommand(Objects.requireNonNull(id, "id"),
            Objects.requireNonNull(replacementUri, "replacementUri"));
    }

    public static WorkspaceCommand createRelationship(final RelationshipId id, final NodeReference source,
            final NodeReference target, final RelationshipDirection direction) {
        return new CreateRelationshipCommand(Objects.requireNonNull(id, "id"),
            Objects.requireNonNull(source, "source"), Objects.requireNonNull(target, "target"),
            Objects.requireNonNull(direction, "direction"));
    }

    public static WorkspaceCommand updateRelationship(final RelationshipId id, final NodeReference source,
            final NodeReference target, final RelationshipDirection direction) {
        return new UpdateRelationshipCommand(Objects.requireNonNull(id, "id"),
            Objects.requireNonNull(source, "source"), Objects.requireNonNull(target, "target"),
            Objects.requireNonNull(direction, "direction"));
    }

    public static WorkspaceCommand deleteRelationship(final RelationshipId id) {
        return new DeleteRelationshipCommand(Objects.requireNonNull(id, "id"));
    }

    public static WorkspaceCommand pin(final NodeReference node, final double x, final double y) {
        return new PinCommand(Objects.requireNonNull(node, "node"), x, y);
    }

    public static WorkspaceCommand unpin(final NodeReference node) {
        return new UnpinCommand(Objects.requireNonNull(node, "node"));
    }

    public static WorkspaceCommand unpinAll() {
        return new UnpinAllCommand();
    }

    public static WorkspaceCommand setDisplaySettings(final DisplaySettings settings) {
        return new SetDisplaySettingsCommand(Objects.requireNonNull(settings, "settings"));
    }

    public static WorkspaceCommand purgeRelationships(final Set<RelationshipId> exactIds) {
        return new PurgeRelationshipsCommand(copyIds(exactIds));
    }

    private static final class AddMapCommand implements WorkspaceCommand {
        private final MapReferenceId proposedId;
        private final URI storedUri;

        private AddMapCommand(final MapReferenceId proposedId, final URI storedUri) {
            this.proposedId = proposedId;
            this.storedUri = storedUri;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final MapReference existingUri = mapByUri(before, storedUri);
            if (existingUri != null) {
                final MapReference existingId = mapById(before, proposedId);
                if (existingId != null && !existingId.id().equals(existingUri.id())) {
                    return WorkspaceTransition.rejected(before, MAP_ID_IN_USE, proposedId);
                }
                if (existingUri.active()) {
                    return noChange(before, "addMap");
                }
                return replaceMap(before, existingUri, copyMap(existingUri, true, existingUri.storedUri()),
                    MAP_REACTIVATED, existingUri.id());
            }
            if (mapById(before, proposedId) != null) {
                return WorkspaceTransition.rejected(before, MAP_ID_IN_USE, proposedId);
            }
            final Long sequence = nextMapSequence(before.maps());
            if (sequence == null) {
                return WorkspaceTransition.rejected(before, MAP_ID_IN_USE, proposedId);
            }
            try {
                final MapReference map = MapReference.of(proposedId, sequence.longValue(), storedUri, true,
                    colorFor(sequence.longValue()), Collections.<UnknownXml>emptyList());
                final List<MapReference> maps = new ArrayList<MapReference>(before.maps());
                maps.add(map);
                return WorkspaceTransition.applied(before.toBuilder().maps(maps).build(), MAP_ADDED, proposedId);
            }
            catch (final IllegalArgumentException exception) {
                return WorkspaceTransition.rejected(before, MAP_URI_IN_USE, storedUri);
            }
        }
    }

    private static final class ReactivateMapCommand implements WorkspaceCommand {
        private final MapReferenceId id;

        private ReactivateMapCommand(final MapReferenceId id) {
            this.id = id;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final MapReference existing = mapById(before, id);
            if (existing == null) {
                return WorkspaceTransition.rejected(before, MAP_NOT_FOUND, id);
            }
            if (existing.active()) {
                return noChange(before, "reactivateMap");
            }
            return replaceMap(before, existing, copyMap(existing, true, existing.storedUri()), MAP_REACTIVATED, id);
        }
    }

    private static final class RemoveMapCommand implements WorkspaceCommand {
        private final MapReferenceId id;

        private RemoveMapCommand(final MapReferenceId id) {
            this.id = id;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final MapReference existing = mapById(before, id);
            if (existing == null) {
                return WorkspaceTransition.rejected(before, MAP_NOT_FOUND, id);
            }
            if (!existing.active()) {
                return noChange(before, "removeMap");
            }
            return replaceMap(before, existing, copyMap(existing, false, existing.storedUri()), MAP_REMOVED, id);
        }
    }

    private static final class LocateMapCommand implements WorkspaceCommand {
        private final MapReferenceId id;
        private final URI replacementUri;

        private LocateMapCommand(final MapReferenceId id, final URI replacementUri) {
            this.id = id;
            this.replacementUri = replacementUri;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final MapReference existing = mapById(before, id);
            if (existing == null) {
                return WorkspaceTransition.rejected(before, MAP_NOT_FOUND, id);
            }
            if (existing.storedUri().equals(replacementUri)) {
                return noChange(before, "locateMap");
            }
            final MapReference sameUri = mapByUri(before, replacementUri);
            if (sameUri != null && !sameUri.id().equals(existing.id())) {
                return WorkspaceTransition.rejected(before, MAP_URI_IN_USE, replacementUri);
            }
            try {
                return replaceMap(before, existing, copyMap(existing, existing.active(), replacementUri), MAP_LOCATED,
                    id);
            }
            catch (final IllegalArgumentException exception) {
                return WorkspaceTransition.rejected(before, MAP_URI_IN_USE, replacementUri);
            }
        }
    }

    private static final class CreateRelationshipCommand implements WorkspaceCommand {
        private final RelationshipId id;
        private final NodeReference source;
        private final NodeReference target;
        private final RelationshipDirection direction;

        private CreateRelationshipCommand(final RelationshipId id, final NodeReference source,
                final NodeReference target, final RelationshipDirection direction) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.direction = direction;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            if (relationshipById(before, id) != null) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_ID_IN_USE, id);
            }
            if (!validRelationshipEndpoints(before, source, target)) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_INVALID, id);
            }
            final NodePair endpoints = canonicalize(source, target, direction);
            final GraphRelationshipRecord duplicate = duplicateRelationship(before, endpoints, direction, null);
            if (duplicate != null) {
                return WorkspaceTransition.noOp(before, RELATIONSHIP_DUPLICATE, duplicate.id());
            }
            final Long sequence = nextRelationshipSequence(before.relationships());
            if (sequence == null) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_INVALID, id);
            }
            try {
                final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(id, sequence.longValue(),
                    endpoints.source, endpoints.target, direction, Collections.<UnknownXml>emptyList());
                final List<GraphRelationshipRecord> relationships =
                    new ArrayList<GraphRelationshipRecord>(before.relationships());
                relationships.add(relationship);
                return WorkspaceTransition.applied(before.toBuilder().relationships(relationships).build(),
                    RELATIONSHIP_CREATED, id);
            }
            catch (final IllegalArgumentException exception) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_INVALID, id);
            }
        }
    }

    private static final class UpdateRelationshipCommand implements WorkspaceCommand {
        private final RelationshipId id;
        private final NodeReference source;
        private final NodeReference target;
        private final RelationshipDirection direction;

        private UpdateRelationshipCommand(final RelationshipId id, final NodeReference source,
                final NodeReference target, final RelationshipDirection direction) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.direction = direction;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final GraphRelationshipRecord existing = relationshipById(before, id);
            if (existing == null) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_NOT_FOUND, id);
            }
            if (!validRelationshipEndpoints(before, source, target)) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_INVALID, id);
            }
            final NodePair endpoints = canonicalize(source, target, direction);
            final GraphRelationshipRecord replacement;
            try {
                replacement = GraphRelationshipRecord.of(existing.id(), existing.sequence(), endpoints.source,
                    endpoints.target, direction, existing.unknownXml());
            }
            catch (final IllegalArgumentException exception) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_INVALID, id);
            }
            if (replacement.equals(existing)) {
                return noChange(before, "updateRelationship");
            }
            final GraphRelationshipRecord duplicate = duplicateRelationship(before, endpoints, direction, id);
            if (duplicate != null) {
                return WorkspaceTransition.noOp(before, RELATIONSHIP_DUPLICATE, duplicate.id());
            }
            return replaceRelationship(before, existing, replacement, RELATIONSHIP_UPDATED, id);
        }
    }

    private static final class DeleteRelationshipCommand implements WorkspaceCommand {
        private final RelationshipId id;

        private DeleteRelationshipCommand(final RelationshipId id) {
            this.id = id;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final GraphRelationshipRecord existing = relationshipById(before, id);
            if (existing == null) {
                return WorkspaceTransition.rejected(before, RELATIONSHIP_NOT_FOUND, id);
            }
            final List<GraphRelationshipRecord> relationships =
                new ArrayList<GraphRelationshipRecord>(before.relationships());
            relationships.remove(existing);
            return WorkspaceTransition.applied(before.toBuilder().relationships(relationships).build(),
                RELATIONSHIP_DELETED, id);
        }
    }

    private static final class PinCommand implements WorkspaceCommand {
        private final NodeReference node;
        private final double x;
        private final double y;

        private PinCommand(final NodeReference node, final double x, final double y) {
            this.node = node;
            this.x = x;
            this.y = y;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            if (!hasMap(before, node.mapReferenceId()) || !isFinite(x) || !isFinite(y)) {
                return WorkspaceTransition.rejected(before, PIN_INVALID, node);
            }
            final PinRecord existing = pinByNode(before, node);
            if (existing != null && Double.compare(existing.x(), x) == 0 && Double.compare(existing.y(), y) == 0) {
                return noChange(before, "pin");
            }
            try {
                final List<UnknownXml> unknownXml = existing == null
                    ? Collections.<UnknownXml>emptyList() : existing.unknownXml();
                final PinRecord replacement = PinRecord.of(node, x, y, unknownXml);
                final List<PinRecord> pins = new ArrayList<PinRecord>(before.pins());
                if (existing == null) {
                    pins.add(replacement);
                } else {
                    pins.set(pins.indexOf(existing), replacement);
                }
                return WorkspaceTransition.applied(before.toBuilder().pins(pins).build(), PIN_SET, node);
            }
            catch (final IllegalArgumentException exception) {
                return WorkspaceTransition.rejected(before, PIN_INVALID, node);
            }
        }
    }

    private static final class UnpinCommand implements WorkspaceCommand {
        private final NodeReference node;

        private UnpinCommand(final NodeReference node) {
            this.node = node;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final PinRecord existing = pinByNode(before, node);
            if (existing == null) {
                return noChange(before, "unpin");
            }
            final List<PinRecord> pins = new ArrayList<PinRecord>(before.pins());
            pins.remove(existing);
            return WorkspaceTransition.applied(before.toBuilder().pins(pins).build(), PIN_REMOVED, node);
        }
    }

    private static final class UnpinAllCommand implements WorkspaceCommand {
        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            if (before.pins().isEmpty()) {
                return noChange(before, "unpinAll");
            }
            final int pinCount = before.pins().size();
            return WorkspaceTransition.applied(before.toBuilder().pins(Collections.<PinRecord>emptyList()).build(),
                PINS_REMOVED, pinCount);
        }
    }

    private static final class SetDisplaySettingsCommand implements WorkspaceCommand {
        private final DisplaySettings settings;

        private SetDisplaySettingsCommand(final DisplaySettings settings) {
            this.settings = settings;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            if (settings.equals(before.displaySettings())) {
                return noChange(before, "setDisplaySettings");
            }
            return WorkspaceTransition.applied(before.toBuilder().displaySettings(settings).build(), DISPLAY_UPDATED);
        }
    }

    private static final class PurgeRelationshipsCommand implements WorkspaceCommand {
        private final Set<RelationshipId> exactIds;

        private PurgeRelationshipsCommand(final Set<RelationshipId> exactIds) {
            this.exactIds = exactIds;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            if (exactIds.isEmpty()) {
                return noChange(before, "purgeRelationships");
            }
            final List<RelationshipId> missing = new ArrayList<RelationshipId>();
            for (final RelationshipId id : exactIds) {
                if (relationshipById(before, id) == null) {
                    missing.add(id);
                }
            }
            if (!missing.isEmpty()) {
                Collections.sort(missing, new Comparator<RelationshipId>() {
                    @Override
                    public int compare(final RelationshipId first, final RelationshipId second) {
                        return first.value().toString().compareTo(second.value().toString());
                    }
                });
                return WorkspaceTransition.rejected(before, PURGE_NOT_FOUND, missing.get(0));
            }
            final List<GraphRelationshipRecord> relationships = new ArrayList<GraphRelationshipRecord>();
            for (final GraphRelationshipRecord relationship : before.relationships()) {
                if (!exactIds.contains(relationship.id())) {
                    relationships.add(relationship);
                }
            }
            return WorkspaceTransition.applied(before.toBuilder().relationships(relationships).build(),
                RELATIONSHIPS_PURGED, exactIds.size());
        }
    }

    private static WorkspaceTransition replaceMap(final WorkspaceDocument before, final MapReference existing,
            final MapReference replacement, final String messageKey, final MapReferenceId messageArgument) {
        try {
            final List<MapReference> maps = new ArrayList<MapReference>(before.maps());
            maps.set(maps.indexOf(existing), replacement);
            return WorkspaceTransition.applied(before.toBuilder().maps(maps).build(), messageKey, messageArgument);
        }
        catch (final IllegalArgumentException exception) {
            return WorkspaceTransition.rejected(before, MAP_URI_IN_USE, replacement.storedUri());
        }
    }

    private static WorkspaceTransition replaceRelationship(final WorkspaceDocument before,
            final GraphRelationshipRecord existing, final GraphRelationshipRecord replacement, final String messageKey,
            final RelationshipId messageArgument) {
        try {
            final List<GraphRelationshipRecord> relationships =
                new ArrayList<GraphRelationshipRecord>(before.relationships());
            relationships.set(relationships.indexOf(existing), replacement);
            return WorkspaceTransition.applied(before.toBuilder().relationships(relationships).build(), messageKey,
                messageArgument);
        }
        catch (final IllegalArgumentException exception) {
            return WorkspaceTransition.rejected(before, RELATIONSHIP_INVALID, messageArgument);
        }
    }

    private static MapReference mapById(final WorkspaceDocument document, final MapReferenceId id) {
        for (final MapReference map : document.maps()) {
            if (map.id().equals(id)) {
                return map;
            }
        }
        return null;
    }

    private static MapReference mapByUri(final WorkspaceDocument document, final URI uri) {
        for (final MapReference map : document.maps()) {
            if (map.storedUri().equals(uri)) {
                return map;
            }
        }
        return null;
    }

    private static boolean hasMap(final WorkspaceDocument document, final MapReferenceId id) {
        return mapById(document, id) != null;
    }

    private static GraphRelationshipRecord relationshipById(final WorkspaceDocument document,
            final RelationshipId id) {
        for (final GraphRelationshipRecord relationship : document.relationships()) {
            if (relationship.id().equals(id)) {
                return relationship;
            }
        }
        return null;
    }

    private static PinRecord pinByNode(final WorkspaceDocument document, final NodeReference node) {
        for (final PinRecord pin : document.pins()) {
            if (pin.node().equals(node)) {
                return pin;
            }
        }
        return null;
    }

    private static MapReference copyMap(final MapReference map, final boolean active, final URI storedUri) {
        return MapReference.of(map.id(), map.sequence(), storedUri, active, map.color(), map.unknownXml());
    }

    private static Long nextMapSequence(final List<MapReference> maps) {
        long maximum = 0;
        for (final MapReference map : maps) {
            if (map.sequence() > maximum) {
                maximum = map.sequence();
            }
        }
        return maximum == Long.MAX_VALUE ? null : Long.valueOf(maximum + 1);
    }

    private static Long nextRelationshipSequence(final List<GraphRelationshipRecord> relationships) {
        long maximum = 0;
        for (final GraphRelationshipRecord relationship : relationships) {
            if (relationship.sequence() > maximum) {
                maximum = relationship.sequence();
            }
        }
        return maximum == Long.MAX_VALUE ? null : Long.valueOf(maximum + 1);
    }

    private static String colorFor(final long sequence) {
        return PALETTE[(int) ((sequence - 1) % PALETTE.length)];
    }

    private static boolean validRelationshipEndpoints(final WorkspaceDocument document, final NodeReference source,
            final NodeReference target) {
        return hasMap(document, source.mapReferenceId()) && hasMap(document, target.mapReferenceId())
            && !source.mapReferenceId().equals(target.mapReferenceId());
    }

    private static GraphRelationshipRecord duplicateRelationship(final WorkspaceDocument document,
            final NodePair endpoints, final RelationshipDirection direction, final RelationshipId excludedId) {
        for (final GraphRelationshipRecord relationship : document.relationships()) {
            if (relationship.id().equals(excludedId)) {
                continue;
            }
            if (relationship.direction() != direction) {
                continue;
            }
            final NodePair existing = canonicalize(relationship.source(), relationship.target(), relationship.direction());
            if (existing.source.equals(endpoints.source) && existing.target.equals(endpoints.target)) {
                return relationship;
            }
        }
        return null;
    }

    private static NodePair canonicalize(final NodeReference source, final NodeReference target,
            final RelationshipDirection direction) {
        if (direction == RelationshipDirection.FORWARD || compareNodes(source, target) <= 0) {
            return new NodePair(source, target);
        }
        return new NodePair(target, source);
    }

    private static int compareNodes(final NodeReference first, final NodeReference second) {
        int result = first.mapReferenceId().value().toString().compareTo(second.mapReferenceId().value().toString());
        if (result != 0) {
            return result;
        }
        return first.nodeId().value().compareTo(second.nodeId().value());
    }

    private static boolean isFinite(final double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static WorkspaceTransition noChange(final WorkspaceDocument document, final String operation) {
        return WorkspaceTransition.noOp(document, NO_CHANGE, operation);
    }

    private static Set<RelationshipId> copyIds(final Set<RelationshipId> ids) {
        Objects.requireNonNull(ids, "exactIds");
        final Set<RelationshipId> copy = new HashSet<RelationshipId>();
        for (final RelationshipId id : ids) {
            copy.add(Objects.requireNonNull(id, "relationshipId"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static final class NodePair {
        private final NodeReference source;
        private final NodeReference target;

        private NodePair(final NodeReference source, final NodeReference target) {
            this.source = source;
            this.target = target;
        }
    }
}
