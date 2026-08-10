package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

public final class MapSnapshot {
    private static final Comparator<PersistedNodeId> PERSISTED_NODE_ID_ORDER = new Comparator<PersistedNodeId>() {
        @Override
        public int compare(final PersistedNodeId first, final PersistedNodeId second) {
            return first.value().compareTo(second.value());
        }
    };

    private final MapReferenceId mapReferenceId;
    private final int workspaceOrder;
    private final String mapName;
    private final NodeSnapshot root;
    private final Set<PersistedNodeId> attachedPersistentIds;
    private final boolean hasInaccessibleBranch;
    private final List<ConnectorSnapshot> connectors;

    private MapSnapshot(final MapReferenceId mapReferenceId, final int workspaceOrder, final String mapName,
            final NodeSnapshot root, final Set<PersistedNodeId> attachedPersistentIds,
            final boolean hasInaccessibleBranch) {
        this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        if (workspaceOrder <= 0) {
            throw new IllegalArgumentException("Workspace order must be positive");
        }
        this.workspaceOrder = workspaceOrder;
        this.mapName = requireMapName(mapName);
        this.root = Objects.requireNonNull(root, "root");
        validateTree(this.root, this.mapReferenceId, new HashSet<SourceNodeKey>());
        this.attachedPersistentIds = sortedIds(attachedPersistentIds);
        this.hasInaccessibleBranch = hasInaccessibleBranch;
        this.connectors = Collections.emptyList();
    }

    public static MapSnapshot of(final MapReferenceId mapReferenceId, final int workspaceOrder, final String mapName,
            final NodeSnapshot root, final Set<PersistedNodeId> attachedPersistentIds,
            final boolean hasInaccessibleBranch) {
        return new MapSnapshot(mapReferenceId, workspaceOrder, mapName, root, attachedPersistentIds,
            hasInaccessibleBranch);
    }

    public MapReferenceId mapReferenceId() {
        return mapReferenceId;
    }

    public int workspaceOrder() {
        return workspaceOrder;
    }

    public String mapName() {
        return mapName;
    }

    public NodeSnapshot root() {
        return root;
    }

    public Set<PersistedNodeId> attachedPersistentIds() {
        return attachedPersistentIds;
    }

    public boolean hasInaccessibleBranch() {
        return hasInaccessibleBranch;
    }

    public List<ConnectorSnapshot> connectors() {
        return connectors;
    }

    private static String requireMapName(final String value) {
        Objects.requireNonNull(value, "mapName");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Map name must not be empty");
        }
        return value;
    }

    private static void validateTree(final NodeSnapshot node, final MapReferenceId mapId,
            final Set<SourceNodeKey> keys) {
        if (!mapId.equals(node.key().mapReferenceId())) {
            throw new IllegalArgumentException("Snapshot keys must belong to the map");
        }
        if (!keys.add(node.key())) {
            throw new IllegalArgumentException("Snapshot source keys must be unique");
        }
        for (final NodeSnapshot child : node.children()) {
            validateTree(child, mapId, keys);
        }
    }

    private static Set<PersistedNodeId> sortedIds(final Set<PersistedNodeId> values) {
        Objects.requireNonNull(values, "attachedPersistentIds");
        final List<PersistedNodeId> sorted = new ArrayList<PersistedNodeId>(values.size());
        for (final PersistedNodeId value : values) {
            sorted.add(Objects.requireNonNull(value, "attachedPersistentIds entry"));
        }
        Collections.sort(sorted, PERSISTED_NODE_ID_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<PersistedNodeId>(sorted));
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MapSnapshot)) {
            return false;
        }
        final MapSnapshot that = (MapSnapshot) other;
        return workspaceOrder == that.workspaceOrder && hasInaccessibleBranch == that.hasInaccessibleBranch
            && mapReferenceId.equals(that.mapReferenceId) && mapName.equals(that.mapName) && root.equals(that.root)
            && attachedPersistentIds.equals(that.attachedPersistentIds) && connectors.equals(that.connectors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapReferenceId, workspaceOrder, mapName, root, attachedPersistentIds,
            hasInaccessibleBranch, connectors);
    }

    @Override
    public String toString() {
        return "MapSnapshot{" + "mapReferenceId=" + mapReferenceId + ", workspaceOrder=" + workspaceOrder
            + ", root=" + root + ", attachedPersistentIds=" + attachedPersistentIds
            + ", hasInaccessibleBranch=" + hasInaccessibleBranch + '}';
    }
}

// Removed when Task 9 creates public ConnectorSnapshot.java.
final class ConnectorSnapshot {
    private ConnectorSnapshot() {
    }
}
