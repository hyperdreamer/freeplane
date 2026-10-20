package org.freeplane.plugin.graph.workspace.model;

import java.util.Objects;

public final class NodeReference {
    private final MapReferenceId mapReferenceId;
    private final PersistedNodeId nodeId;

    private NodeReference(final MapReferenceId mapReferenceId, final PersistedNodeId nodeId) {
        this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
    }

    public static NodeReference of(final MapReferenceId map, final PersistedNodeId node) {
        return new NodeReference(map, node);
    }

    public MapReferenceId mapReferenceId() {
        return mapReferenceId;
    }

    public PersistedNodeId nodeId() {
        return nodeId;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeReference)) {
            return false;
        }
        final NodeReference that = (NodeReference) other;
        return mapReferenceId.equals(that.mapReferenceId) && nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return 31 * mapReferenceId.hashCode() + nodeId.hashCode();
    }

    @Override
    public String toString() {
        return "NodeReference{" + "mapReferenceId=" + mapReferenceId + ", nodeId=" + nodeId + '}';
    }
}
