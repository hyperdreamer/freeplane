package org.freeplane.plugin.graph.workspace.model;

import java.util.Objects;

public final class PersistedNodeId {
    private final String value;

    private PersistedNodeId(final String value) {
        this.value = value;
    }

    public static PersistedNodeId of(final String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Persisted node ID must not be empty");
        }
        return new PersistedNodeId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PersistedNodeId)) {
            return false;
        }
        final PersistedNodeId that = (PersistedNodeId) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
