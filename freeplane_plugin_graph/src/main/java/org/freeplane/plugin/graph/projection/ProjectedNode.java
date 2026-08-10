package org.freeplane.plugin.graph.projection;

import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class ProjectedNode {
    private final ProjectedNodeKey key;
    private final SafeNodeLabel label;
    private final String mapName;
    private final boolean graphGroup;

    private ProjectedNode(final ProjectedNodeKey key, final SafeNodeLabel label, final String mapName,
            final boolean graphGroup) {
        this.key = Objects.requireNonNull(key, "key");
        this.label = Objects.requireNonNull(label, "label");
        this.mapName = requireMapName(mapName);
        this.graphGroup = graphGroup;
    }

    public static ProjectedNode of(final ProjectedNodeKey key, final SafeNodeLabel label, final String mapName,
            final boolean graphGroup) {
        return new ProjectedNode(key, label, mapName, graphGroup);
    }

    public ProjectedNodeKey key() {
        return key;
    }

    public SourceNodeKey source() {
        return key.source();
    }

    public MapReferenceId mapReferenceId() {
        return key.mapReferenceId();
    }

    public SafeNodeLabel label() {
        return label;
    }

    public String mapName() {
        return mapName;
    }

    public boolean graphGroup() {
        return graphGroup;
    }

    private static String requireMapName(final String value) {
        Objects.requireNonNull(value, "mapName");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Map name must not be empty");
        }
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectedNode)) {
            return false;
        }
        final ProjectedNode that = (ProjectedNode) other;
        return graphGroup == that.graphGroup && key.equals(that.key) && label.equals(that.label)
            && mapName.equals(that.mapName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, label, mapName, graphGroup);
    }

    @Override
    public String toString() {
        return "ProjectedNode{" + "key=" + key + ", graphGroup=" + graphGroup + '}';
    }
}
