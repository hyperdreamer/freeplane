package org.freeplane.plugin.graph.workspace.model;

import java.util.List;
import java.util.Objects;

public final class GraphRelationshipRecord {
    private final RelationshipId id;
    private final long sequence;
    private final NodeReference source;
    private final NodeReference target;
    private final RelationshipDirection direction;
    private final List<UnknownXml> unknownXml;

    private GraphRelationshipRecord(final RelationshipId id, final long sequence,
            final NodeReference source, final NodeReference target, final RelationshipDirection direction,
            final List<UnknownXml> unknownXml) {
        this.id = Objects.requireNonNull(id, "id");
        if (sequence <= 0) {
            throw new IllegalArgumentException("Relationship sequence must be positive");
        }
        this.sequence = sequence;
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        if (source.equals(target) || source.mapReferenceId().equals(target.mapReferenceId())) {
            throw new IllegalArgumentException("Relationships must connect distinct nodes across maps");
        }
        this.direction = Objects.requireNonNull(direction, "direction");
        this.unknownXml = UnknownXml.forRecord(unknownXml);
    }

    public static GraphRelationshipRecord of(final RelationshipId id, final long sequence,
            final NodeReference source, final NodeReference target, final RelationshipDirection direction,
            final List<UnknownXml> unknownXml) {
        return new GraphRelationshipRecord(id, sequence, source, target, direction, unknownXml);
    }

    public RelationshipId id() {
        return id;
    }

    public long sequence() {
        return sequence;
    }

    public NodeReference source() {
        return source;
    }

    public NodeReference target() {
        return target;
    }

    public RelationshipDirection direction() {
        return direction;
    }

    public List<UnknownXml> unknownXml() {
        return unknownXml;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphRelationshipRecord)) {
            return false;
        }
        final GraphRelationshipRecord that = (GraphRelationshipRecord) other;
        return sequence == that.sequence && id.equals(that.id) && source.equals(that.source)
            && target.equals(that.target) && direction == that.direction && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sequence, source, target, direction, unknownXml);
    }

    @Override
    public String toString() {
        return "GraphRelationshipRecord{" + "id=" + id + ", sequence=" + sequence + ", source=" + source
            + ", target=" + target + ", direction=" + direction + ", unknownXml=" + unknownXml + '}';
    }
}
