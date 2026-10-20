package org.freeplane.plugin.graph.workspace.model;

import java.util.Objects;
import java.util.UUID;

public final class MapReferenceId {
    private final UUID value;

    private MapReferenceId(final UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static MapReferenceId of(final UUID value) {
        return new MapReferenceId(value);
    }

    public static MapReferenceId of(final String canonicalUuid) {
        return new MapReferenceId(UuidValueSupport.parseCanonical(canonicalUuid));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MapReferenceId)) {
            return false;
        }
        final MapReferenceId that = (MapReferenceId) other;
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
