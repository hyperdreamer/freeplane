package org.freeplane.plugin.graph.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.link.ArrowType;
import org.freeplane.features.link.ConnectorArrows;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

public final class ConnectorSnapshotFactory {
    public List<ConnectorSnapshot> snapshotReachableConnectors(final MapLease lease, final MapSnapshot safeNodes) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(safeNodes, "safeNodes");
        if (!lease.mapReferenceId().equals(safeNodes.mapReferenceId())) {
            throw new IllegalArgumentException("Safe snapshot map does not match the lease");
        }
        final MapLeaseAccess access = accessFor(lease);
        return access.withModelOnEdt(new MapModelCallback<List<ConnectorSnapshot>>() {
            @Override
            public List<ConnectorSnapshot> apply(final MapModel model, final int workspaceOrder) {
                return snapshotReachableConnectors(model, workspaceOrder, safeNodes);
            }
        });
    }

    List<ConnectorSnapshot> snapshotReachableConnectors(final MapModel model, final int workspaceOrder,
            final MapSnapshot safeNodes) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(safeNodes, "safeNodes");
        if (safeNodes.workspaceOrder() != workspaceOrder) {
            throw new IllegalArgumentException("Safe snapshot workspace order does not match the map callback");
        }
        final MapReferenceId mapId = safeNodes.mapReferenceId();
        final NodeModel root = Objects.requireNonNull(model.getRootNode(), "map root");
        final PairedTraversal traversal = new PairedTraversal(mapId);
        traversal.pair(root, safeNodes.root(), new ArrayList<Integer>(), false);
        final List<ConnectorSnapshot> connectors = new ArrayList<ConnectorSnapshot>();
        for (final PairedSource source : traversal.sources) {
            final NodeLinks links = NodeLinks.getLinkExtension(source.node);
            if (links == null) {
                continue;
            }
            int occurrence = 0;
            for (final NodeLinkModel link : links.getLinks()) {
                if (!(link instanceof ConnectorModel)) {
                    continue;
                }
                final ConnectorModel connector = (ConnectorModel) link;
                final int connectorOccurrence = occurrence++;
                final String targetId = connector.getTargetID();
                if (targetId == null || persistentSelfTarget(source.key, targetId)) {
                    continue;
                }
                final NodeReference target = traversal.targets.get(targetId);
                if (target == null) {
                    continue;
                }
                final ConnectorArrows arrows = connector.getArrows().orElse(ConnectorArrows.DEFAULT);
                final boolean arrowAtSource = arrows.start != ArrowType.NONE;
                final boolean arrowAtTarget = arrows.end != ArrowType.NONE;
                final String sourceLabel = label(connector.getSourceLabel());
                final String middleLabel = label(connector.getMiddleLabel());
                final String targetLabel = label(connector.getTargetLabel());
                connectors.add(ConnectorSnapshot.of(connectorOccurrence,
                    ConnectorDescriptor.of(source.key, target, arrowAtSource, arrowAtTarget, sourceLabel,
                        middleLabel, targetLabel)));
            }
        }
        return Collections.unmodifiableList(connectors);
    }

    private MapLeaseAccess accessFor(final MapLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (!(lease instanceof MapLeaseAccess)) {
            throw new IllegalArgumentException("Map lease does not provide model access");
        }
        return (MapLeaseAccess) lease;
    }

    private boolean persistentSelfTarget(final SourceNodeKey sourceKey, final String targetId) {
        return sourceKey.persistent() && sourceKey.persistedReference().get().nodeId().value().equals(targetId);
    }

    private static String label(final Optional<String> value) {
        if (!value.isPresent()) {
            return "";
        }
        final String raw = value.get();
        final StringBuilder normalized = new StringBuilder(raw.length());
        boolean inLineBreakRun = false;
        for (int index = 0; index < raw.length(); index++) {
            final char character = raw.charAt(index);
            if (character == '\r' || character == '\n') {
                inLineBreakRun = true;
            }
            else {
                if (inLineBreakRun) {
                    normalized.append(' ');
                    inLineBreakRun = false;
                }
                normalized.append(character);
            }
        }
        if (inLineBreakRun) {
            normalized.append(' ');
        }
        return normalized.toString();
    }

    private SourceNodeKey keyFor(final MapReferenceId mapId, final String id, final List<Integer> structuralPath) {
        if (id != null) {
            return SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of(id)));
        }
        return SourceNodeKey.transientPath(mapId, structuralPath);
    }

    private final class PairedTraversal {
        private final MapReferenceId mapId;
        private final List<PairedSource> sources = new ArrayList<PairedSource>();
        private final Map<String, NodeReference> targets = new HashMap<String, NodeReference>();

        PairedTraversal(final MapReferenceId mapId) {
            this.mapId = mapId;
        }

        void pair(final NodeModel node, final NodeSnapshot snapshot, final List<Integer> structuralPath,
                final boolean ancestorExcluded) {
            final String id = node.getID();
            final SourceNodeKey expectedKey = keyFor(mapId, id, structuralPath);
            if (!expectedKey.equals(snapshot.key())) {
                throw new IllegalArgumentException("Safe snapshot keys do not match the live map");
            }
            final List<NodeModel> children = new ArrayList<NodeModel>(node.getChildren());
            if (children.size() != snapshot.children().size()) {
                throw new IllegalArgumentException("Safe snapshot structure does not match the live map");
            }
            if (snapshot.structuralLeaf() != children.isEmpty()) {
                throw new IllegalArgumentException("Safe snapshot structure does not match the live map");
            }
            final boolean excluded = ancestorExcluded || NodeVisibility.isHidden(node) || SummaryNode.isHidden(node);
            if (excluded != snapshot.excluded()) {
                throw new IllegalArgumentException("Safe snapshot exclusion does not match the live map");
            }
            if (!excluded) {
                sources.add(new PairedSource(node, expectedKey));
                if (id != null) {
                    final NodeReference reference = NodeReference.of(mapId, PersistedNodeId.of(id));
                    if (targets.put(id, reference) != null) {
                        throw new IllegalArgumentException("Duplicate reachable node IDs are ambiguous");
                    }
                }
            }
            for (int index = 0; index < children.size(); index++) {
                final List<Integer> childPath = new ArrayList<Integer>(structuralPath);
                childPath.add(Integer.valueOf(index));
                pair(children.get(index), snapshot.children().get(index), childPath, excluded);
            }
        }
    }

    private static final class PairedSource {
        private final NodeModel node;
        private final SourceNodeKey key;

        PairedSource(final NodeModel node, final SourceNodeKey key) {
            this.node = node;
            this.key = key;
        }
    }
}
