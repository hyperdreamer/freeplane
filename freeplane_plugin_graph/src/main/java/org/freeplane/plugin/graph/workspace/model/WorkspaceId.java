package org.freeplane.plugin.graph.workspace.model;

import java.util.Objects;
import java.util.UUID;

public final class WorkspaceId {
    private final UUID value;

    private WorkspaceId(final UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static WorkspaceId of(final UUID value) {
        return new WorkspaceId(value);
    }

    public static WorkspaceId of(final String canonicalUuid) {
        return new WorkspaceId(UuidValueSupport.parseCanonical(canonicalUuid));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkspaceId)) {
            return false;
        }
        final WorkspaceId that = (WorkspaceId) other;
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

final class UuidValueSupport {
    private static final String CANONICAL_UUID =
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private UuidValueSupport() {
    }

    static UUID parseCanonical(final String value) {
        Objects.requireNonNull(value, "value");
        if (!value.matches(CANONICAL_UUID)) {
            throw new IllegalArgumentException("UUID must use its canonical 36-character form");
        }
        return UUID.fromString(value);
    }
}
