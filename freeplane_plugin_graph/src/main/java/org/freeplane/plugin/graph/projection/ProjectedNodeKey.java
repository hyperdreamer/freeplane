package org.freeplane.plugin.graph.projection;

import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class ProjectedNodeKey {
    private final SourceNodeKey source;

    private ProjectedNodeKey(final SourceNodeKey source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public static ProjectedNodeKey of(final SourceNodeKey source) {
        return new ProjectedNodeKey(source);
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
        if (!(other instanceof ProjectedNodeKey)) {
            return false;
        }
        final ProjectedNodeKey that = (ProjectedNodeKey) other;
        return source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectedNodeKey{" + "source=" + source + '}';
    }
}
