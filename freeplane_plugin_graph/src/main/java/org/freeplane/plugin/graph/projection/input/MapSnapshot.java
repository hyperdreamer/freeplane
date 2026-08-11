package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
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
        this(mapReferenceId, workspaceOrder, mapName, root, attachedPersistentIds, hasInaccessibleBranch,
            Collections.<ConnectorSnapshot>emptyList());
    }

    private MapSnapshot(final MapReferenceId mapReferenceId, final int workspaceOrder, final String mapName,
            final NodeSnapshot root, final Set<PersistedNodeId> attachedPersistentIds,
            final boolean hasInaccessibleBranch, final List<ConnectorSnapshot> connectors) {
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
        this.connectors = canonicalConnectors(connectors, this.root, this.mapReferenceId);
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

    public MapSnapshot withConnectors(final List<ConnectorSnapshot> values) {
        return new MapSnapshot(mapReferenceId, workspaceOrder, mapName, root, attachedPersistentIds,
            hasInaccessibleBranch, values);
    }

    private static List<ConnectorSnapshot> canonicalConnectors(final List<ConnectorSnapshot> values,
            final NodeSnapshot root, final MapReferenceId mapReferenceId) {
        Objects.requireNonNull(values, "connectors");
        final List<SourceNodeKey> sourceTraversal = new ArrayList<SourceNodeKey>();
        final Set<NodeReference> persistedTargets = new HashSet<NodeReference>();
        collectConnectorNodes(root, sourceTraversal, persistedTargets);
        final Map<SourceNodeKey, Integer> sourceOrder = new HashMap<SourceNodeKey, Integer>();
        for (int index = 0; index < sourceTraversal.size(); index++) {
            sourceOrder.put(sourceTraversal.get(index), Integer.valueOf(index));
        }

        final List<ConnectorSnapshot> copy = new ArrayList<ConnectorSnapshot>(values.size());
        final Set<ContributorKey> keys = new HashSet<ContributorKey>();
        for (final ConnectorSnapshot value : values) {
            final ConnectorSnapshot connector = Objects.requireNonNull(value, "connectors entry");
            final ConnectorDescriptor descriptor = connector.descriptor();
            if (!mapReferenceId.equals(descriptor.source().mapReferenceId())
                    || !mapReferenceId.equals(descriptor.target().mapReferenceId())) {
                throw new IllegalArgumentException("Connector endpoints must belong to the snapshot map");
            }
            if (!sourceOrder.containsKey(descriptor.source())) {
                throw new IllegalArgumentException("Connector source must belong to the snapshot tree");
            }
            if (!persistedTargets.contains(descriptor.target())) {
                throw new IllegalArgumentException("Connector target must belong to the snapshot tree");
            }
            if (!keys.add(connector.key())) {
                throw new IllegalArgumentException("Connector contributor keys must be unique");
            }
            copy.add(connector);
        }
        Collections.sort(copy, new Comparator<ConnectorSnapshot>() {
            @Override
            public int compare(final ConnectorSnapshot first, final ConnectorSnapshot second) {
                int result = sourceOrder.get(first.descriptor().source()).compareTo(
                    sourceOrder.get(second.descriptor().source()));
                if (result != 0) {
                    return result;
                }
                result = Integer.compare(first.occurrence(), second.occurrence());
                if (result != 0) {
                    return result;
                }
                return first.key().compareTo(second.key());
            }
        });
        return Collections.unmodifiableList(copy);
    }

    private static void collectConnectorNodes(final NodeSnapshot node, final List<SourceNodeKey> sourceTraversal,
            final Set<NodeReference> persistedTargets) {
        sourceTraversal.add(node.key());
        if (node.key().persistent()) {
            persistedTargets.add(node.key().persistedReference().get());
        }
        for (final NodeSnapshot child : node.children()) {
            collectConnectorNodes(child, sourceTraversal, persistedTargets);
        }
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
