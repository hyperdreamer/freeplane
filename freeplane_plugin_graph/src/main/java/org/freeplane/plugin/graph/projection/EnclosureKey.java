package org.freeplane.plugin.graph.projection;

import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class EnclosureKey {
    private final SourceNodeKey source;

    private EnclosureKey(final SourceNodeKey source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public static EnclosureKey of(final SourceNodeKey source) {
        return new EnclosureKey(source);
    }

    public SourceNodeKey source() {
        return source;
    }

    public MapReferenceId mapReferenceId() {
        return source.mapReferenceId();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EnclosureKey)) {
            return false;
        }
        final EnclosureKey that = (EnclosureKey) other;
        return source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public String toString() {
        return "EnclosureKey{" + "source=" + source + '}';
    }
}
