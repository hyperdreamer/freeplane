package org.freeplane.plugin.graph.adapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.plugin.graph.group.GraphGroupModel;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

public final class MapSnapshotFactory {
    private static final SafeNodeLabel EXCLUDED_LABEL = SafeNodeLabel.of("Node", "Node");

    private final SafeNodeLabelExtractor labelExtractor = new SafeNodeLabelExtractor();
    private final ConnectorSnapshotFactory connectorFactory = new ConnectorSnapshotFactory();

    public MapSnapshot snapshot(final MapLease lease) {
        final MapLeaseAccess access = accessFor(lease);
        return access.withModelOnEdt(new MapModelCallback<MapSnapshot>() {
            @Override
            public MapSnapshot apply(final MapModel model, final int workspaceOrder) {
                final String mapTitle = model.getTitle();
                if (mapTitle == null || mapTitle.isEmpty()) {
                    throw new IllegalArgumentException("Map title must not be empty");
                }
                final NodeModel root = Objects.requireNonNull(model.getRootNode(), "map root");
                final TraversalAccumulator accumulator = new TraversalAccumulator();
                final NodeSnapshot rootSnapshot = snapshotNode(root, lease.mapReferenceId(),
                    new ArrayList<Integer>(), false, accumulator);
                final MapSnapshot safeNodes = MapSnapshot.of(lease.mapReferenceId(), workspaceOrder, mapTitle,
                    rootSnapshot, accumulator.attachedPersistentIds, accumulator.hasInaccessibleBranch);
                final List<ConnectorSnapshot> connectors = connectorFactory
                    .snapshotReachableConnectors(model, workspaceOrder, safeNodes);
                return safeNodes.withConnectors(connectors);
            }
        });
    }

    private MapLeaseAccess accessFor(final MapLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (!(lease instanceof MapLeaseAccess)) {
            throw new IllegalArgumentException("Map lease does not provide model access");
        }
        return (MapLeaseAccess) lease;
    }

    private NodeSnapshot snapshotNode(final NodeModel node, final MapReferenceId mapId,
            final List<Integer> structuralPath, final boolean ancestorExcluded,
            final TraversalAccumulator accumulator) {
        final List<NodeModel> children = new ArrayList<NodeModel>(node.getChildren());
        final boolean structuralLeaf = children.isEmpty();
        final String id = node.getID();
        final SourceNodeKey key = keyFor(mapId, id, structuralPath);
        if (id != null) {
            accumulator.attachedPersistentIds.add(PersistedNodeId.of(id));
        }
        final EncryptionModel encryption = EncryptionModel.getModel(node);
        if (encryption != null && encryption.isLocked()) {
            accumulator.hasInaccessibleBranch = true;
        }
        final boolean excluded = ancestorExcluded || NodeVisibility.isHidden(node) || SummaryNode.isHidden(node);
        final List<NodeSnapshot> childSnapshots = new ArrayList<NodeSnapshot>(children.size());
        for (int index = 0; index < children.size(); index++) {
            final List<Integer> childPath = new ArrayList<Integer>(structuralPath);
            childPath.add(Integer.valueOf(index));
            childSnapshots.add(snapshotNode(children.get(index), mapId, childPath, excluded, accumulator));
        }
        final SafeNodeLabel label;
        final boolean graphGroup;
        if (excluded) {
            label = EXCLUDED_LABEL;
            graphGroup = false;
        }
        else {
            label = labelExtractor.extract(node);
            graphGroup = GraphGroupModel.isMarked(node);
        }
        return NodeSnapshot.of(key, label, structuralLeaf, graphGroup, excluded, childSnapshots);
    }

    private SourceNodeKey keyFor(final MapReferenceId mapId, final String id, final List<Integer> structuralPath) {
        if (id != null) {
            return SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of(id)));
        }
        return SourceNodeKey.transientPath(mapId, structuralPath);
    }

    private static final class TraversalAccumulator {
        private final Set<PersistedNodeId> attachedPersistentIds = new LinkedHashSet<PersistedNodeId>();
        private boolean hasInaccessibleBranch;
    }
}
