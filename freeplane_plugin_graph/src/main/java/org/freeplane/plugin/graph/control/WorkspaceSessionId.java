package org.freeplane.plugin.graph.control;

import java.util.Objects;
import java.util.UUID;

public final class WorkspaceSessionId {
    private static final String CANONICAL_UUID =
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final UUID value;

    private WorkspaceSessionId(final UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static WorkspaceSessionId of(final UUID value) {
        return new WorkspaceSessionId(value);
    }

    public static WorkspaceSessionId of(final String canonicalUuid) {
        return new WorkspaceSessionId(parseCanonical(canonicalUuid));
    }

    public UUID value() {
        return value;
    }

    private static UUID parseCanonical(final String value) {
        Objects.requireNonNull(value, "value");
        if (!value.matches(CANONICAL_UUID)) {
            throw new IllegalArgumentException("UUID must use its canonical 36-character form");
        }
        return UUID.fromString(value);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkspaceSessionId)) {
            return false;
        }
        final WorkspaceSessionId that = (WorkspaceSessionId) other;
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
