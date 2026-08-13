package org.freeplane.plugin.graph.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;

public final class TraversalNodeResolver {
    public Optional<NodeModel> resolve(final MapLease lease, final SourceNodeKey key) {
        final MapLeaseAccess access = accessFor(lease);
        final SourceNodeKey requestedKey = Objects.requireNonNull(key, "key");
        if (!lease.mapReferenceId().equals(requestedKey.mapReferenceId())) {
            return Optional.empty();
        }
        return access.withModelOnEdt(new MapModelCallback<Optional<NodeModel>>() {
            @Override
            public Optional<NodeModel> apply(final MapModel model, final int workspaceOrder) {
                final NodeModel root = model.getRootNode();
                if (root == null) {
                    return Optional.empty();
                }
                if (requestedKey.persistent()) {
                    final String requestedId = requestedKey.persistedReference().get().nodeId().value();
                    return Optional.ofNullable(findPersistent(root, requestedId, false));
                }
                return findTransient(root, requestedKey.structuralPath());
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

    private NodeModel findPersistent(final NodeModel node, final String requestedId,
            final boolean ancestorExcluded) {
        final boolean excluded = excluded(node, ancestorExcluded);
        if (excluded) {
            return null;
        }
        final String nodeId = node.getID();
        if (nodeId != null && requestedId.equals(nodeId)) {
            return node;
        }
        final List<NodeModel> children = new ArrayList<NodeModel>(node.getChildren());
        for (NodeModel child : children) {
            final NodeModel resolved = findPersistent(child, requestedId, excluded);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private Optional<NodeModel> findTransient(final NodeModel root, final List<Integer> structuralPath) {
        NodeModel current = root;
        boolean ancestorExcluded = false;
        for (int pathIndex = 0; pathIndex <= structuralPath.size(); pathIndex++) {
            final boolean excluded = excluded(current, ancestorExcluded);
            if (excluded) {
                return Optional.empty();
            }
            if (pathIndex == structuralPath.size()) {
                return Optional.of(current);
            }
            final List<NodeModel> children = new ArrayList<NodeModel>(current.getChildren());
            final int childIndex = structuralPath.get(pathIndex).intValue();
            if (childIndex >= children.size()) {
                return Optional.empty();
            }
            current = children.get(childIndex);
            ancestorExcluded = excluded;
        }
        return Optional.empty();
    }

    private boolean excluded(final NodeModel node, final boolean ancestorExcluded) {
        return ancestorExcluded || NodeVisibility.isHidden(node) || SummaryNode.isHidden(node);
    }
}
