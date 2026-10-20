package org.freeplane.plugin.graph.projection;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;

public final class ContributorKey implements Comparable<ContributorKey> {
    private enum Kind {
        NATIVE_CONNECTOR,
        GRAPH_RELATIONSHIP
    }

    private final Kind kind;
    private final MapReferenceId mapReferenceId;
    private final SourceNodeKey source;
    private final int occurrence;
    private final RelationshipId relationshipId;

    private ContributorKey(final Kind kind, final MapReferenceId mapReferenceId, final SourceNodeKey source,
            final int occurrence, final RelationshipId relationshipId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.mapReferenceId = mapReferenceId;
        this.source = source;
        this.occurrence = occurrence;
        this.relationshipId = relationshipId;
        if (kind == Kind.NATIVE_CONNECTOR) {
            if (mapReferenceId == null || source == null || relationshipId != null || occurrence < 0) {
                throw new IllegalArgumentException("Invalid native connector key");
            }
            if (!mapReferenceId.equals(source.mapReferenceId())) {
                throw new IllegalArgumentException("Native key map and source must agree");
            }
        }
        else if (mapReferenceId != null || source != null || occurrence != 0 || relationshipId == null) {
            throw new IllegalArgumentException("Invalid graph relationship key");
        }
    }

    public static ContributorKey nativeConnector(final MapReferenceId map, final SourceNodeKey source,
            final int occurrence) {
        return new ContributorKey(Kind.NATIVE_CONNECTOR, Objects.requireNonNull(map, "map"),
            Objects.requireNonNull(source, "source"), occurrence, null);
    }

    public static ContributorKey graphRelationship(final RelationshipId relationship) {
        return new ContributorKey(Kind.GRAPH_RELATIONSHIP, null, null, 0,
            Objects.requireNonNull(relationship, "relationship"));
    }

    public boolean isNativeConnector() {
        return kind == Kind.NATIVE_CONNECTOR;
    }

    public boolean isGraphRelationship() {
        return kind == Kind.GRAPH_RELATIONSHIP;
    }

    public Optional<MapReferenceId> mapReferenceId() {
        return Optional.ofNullable(mapReferenceId);
    }

    public Optional<SourceNodeKey> source() {
        return Optional.ofNullable(source);
    }

    public OptionalInt occurrence() {
        return isNativeConnector() ? OptionalInt.of(occurrence) : OptionalInt.empty();
    }

    public Optional<RelationshipId> relationshipId() {
        return Optional.ofNullable(relationshipId);
    }

    @Override
    public int compareTo(final ContributorKey other) {
        Objects.requireNonNull(other, "other");
        int result = kind.compareTo(other.kind);
        if (result != 0) {
            return result;
        }
        if (isGraphRelationship()) {
            return relationshipId.value().compareTo(other.relationshipId.value());
        }
        result = mapReferenceId.value().toString().compareTo(other.mapReferenceId.value().toString());
        if (result != 0) {
            return result;
        }
        result = compareSources(source, other.source);
        if (result != 0) {
            return result;
        }
        return Integer.compare(occurrence, other.occurrence);
    }

    private static int compareSources(final SourceNodeKey first, final SourceNodeKey second) {
        int result = Boolean.compare(first.persistent(), second.persistent());
        if (result != 0) {
            return result;
        }
        if (first.persistent()) {
            result = first.persistedReference().get().nodeId().value()
                .compareTo(second.persistedReference().get().nodeId().value());
            if (result != 0) {
                return result;
            }
        }
        final int limit = Math.min(first.structuralPath().size(), second.structuralPath().size());
        for (int index = 0; index < limit; index++) {
            result = Integer.compare(first.structuralPath().get(index).intValue(),
                second.structuralPath().get(index).intValue());
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(first.structuralPath().size(), second.structuralPath().size());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContributorKey)) {
            return false;
        }
        final ContributorKey that = (ContributorKey) other;
        return kind == that.kind && occurrence == that.occurrence
            && Objects.equals(mapReferenceId, that.mapReferenceId) && Objects.equals(source, that.source)
            && Objects.equals(relationshipId, that.relationshipId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, mapReferenceId, source, occurrence, relationshipId);
    }

    @Override
    public String toString() {
        if (isNativeConnector()) {
            return "ContributorKey{kind=nativeConnector, mapReferenceId=" + mapReferenceId
                + ", source=" + source + ", occurrence=" + occurrence + '}';
        }
        return "ContributorKey{kind=graphRelationship, relationshipId=" + relationshipId + '}';
    }
}
