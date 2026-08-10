package org.freeplane.plugin.graph.workspace.model;

import java.util.Objects;
import java.util.UUID;

public final class RelationshipId {
    private final UUID value;

    private RelationshipId(final UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static RelationshipId of(final UUID value) {
        return new RelationshipId(value);
    }

    public static RelationshipId of(final String canonicalUuid) {
        return new RelationshipId(UuidValueSupport.parseCanonical(canonicalUuid));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RelationshipId)) {
            return false;
        }
        final RelationshipId that = (RelationshipId) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
