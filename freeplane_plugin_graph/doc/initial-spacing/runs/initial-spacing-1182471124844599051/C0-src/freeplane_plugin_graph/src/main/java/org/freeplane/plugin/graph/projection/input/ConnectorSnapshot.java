package org.freeplane.plugin.graph.projection.input;

import java.util.Objects;

import org.freeplane.plugin.graph.projection.ContributorKey;

public final class ConnectorSnapshot {
    private final ContributorKey key;
    private final int occurrence;
    private final ConnectorDescriptor descriptor;

    private ConnectorSnapshot(final int occurrence, final ConnectorDescriptor descriptor) {
        if (occurrence < 0) {
            throw new IllegalArgumentException("Connector occurrence must be nonnegative");
        }
        this.occurrence = occurrence;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.key = ContributorKey.nativeConnector(descriptor.source().mapReferenceId(), descriptor.source(),
            occurrence);
    }

    public static ConnectorSnapshot of(final int occurrence, final ConnectorDescriptor descriptor) {
        return new ConnectorSnapshot(occurrence, descriptor);
    }

    public ContributorKey key() {
        return key;
    }

    public int occurrence() {
        return occurrence;
    }

    public ConnectorDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectorSnapshot)) {
            return false;
        }
        final ConnectorSnapshot that = (ConnectorSnapshot) other;
        return occurrence == that.occurrence && key.equals(that.key) && descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, occurrence, descriptor);
    }

    @Override
    public String toString() {
        return "ConnectorSnapshot{" + "key=" + key + ", occurrence=" + occurrence + '}';
    }
}
