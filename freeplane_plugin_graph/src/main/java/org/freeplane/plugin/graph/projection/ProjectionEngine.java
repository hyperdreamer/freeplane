package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class ProjectionEngine {
    private static final Comparator<MapSnapshot> MAP_ORDER = new Comparator<MapSnapshot>() {
        @Override
        public int compare(final MapSnapshot first, final MapSnapshot second) {
            int result = Integer.compare(first.workspaceOrder(), second.workspaceOrder());
            if (result != 0) {
                return result;
            }
            return first.mapReferenceId().value().toString().compareTo(second.mapReferenceId().value().toString());
        }
    };

    public GraphProjection projectStructure(final long generation, final WorkspaceDocument workspace,
            final List<MapSnapshot> maps) {
        if (generation < 0) {
            throw new IllegalArgumentException("Generation must be nonnegative");
        }
        final WorkspaceDocument document = Objects.requireNonNull(workspace, "workspace");
        final List<MapSnapshot> selectedMaps = selectedMaps(document, maps);
        final List<ProjectedNode> projectedNodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> projectedEnclosures = new ArrayList<ProjectedEnclosure>();

        for (final MapSnapshot map : selectedMaps) {
            validateSafeIdentityTraversal(map);
            final StructuralElement root = projectNode(map.root(), map.mapName());
            if (root == null) {
                continue;
            }
            collectNodes(root, projectedNodes);
            if (root instanceof ExactEnclosure) {
                collectEnclosures(compress((ExactEnclosure) root, Optional.<EnclosureHullKey>empty()),
                    projectedEnclosures);
            }
        }
        return GraphProjection.structure(generation, projectedNodes, projectedEnclosures);
    }

    private static List<MapSnapshot> selectedMaps(final WorkspaceDocument workspace,
            final List<MapSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "maps");
        final Map<MapReferenceId, MapReference> registrations = new HashMap<MapReferenceId, MapReference>();
        for (final MapReference registration : workspace.maps()) {
            registrations.put(registration.id(), registration);
        }

        final List<MapSnapshot> selected = new ArrayList<MapSnapshot>();
        final Set<MapReferenceId> mapIds = new HashSet<MapReferenceId>();
        final Set<Integer> workspaceOrders = new HashSet<Integer>();
        for (final MapSnapshot value : snapshots) {
            final MapSnapshot snapshot = Objects.requireNonNull(value, "maps entry");
            if (!mapIds.add(snapshot.mapReferenceId())) {
                throw new IllegalArgumentException("Snapshot map IDs must be unique");
            }
            if (!workspaceOrders.add(Integer.valueOf(snapshot.workspaceOrder()))) {
                throw new IllegalArgumentException("Snapshot workspace orders must be unique");
            }
            final MapReference registration = registrations.get(snapshot.mapReferenceId());
            if (registration == null) {
                throw new IllegalArgumentException("Snapshots must reference registered maps");
            }
            if (registration.active()) {
                selected.add(snapshot);
            }
        }
        Collections.sort(selected, MAP_ORDER);
        return selected;
    }

    private static void validateSafeIdentityTraversal(final MapSnapshot map) {
        validateSafeIdentityTraversal(map.root(), map.mapReferenceId(), new HashSet<SourceNodeKey>());
    }

    private static void validateSafeIdentityTraversal(final NodeSnapshot snapshot, final MapReferenceId mapReferenceId,
            final Set<SourceNodeKey> sourceKeys) {
        final NodeSnapshot node = Objects.requireNonNull(snapshot, "snapshot node");
        final SourceNodeKey source = Objects.requireNonNull(node.key(), "snapshot node key");
        if (!mapReferenceId.equals(source.mapReferenceId())) {
            throw new IllegalArgumentException("Snapshot keys must belong to the map");
        }
        if (!sourceKeys.add(source)) {
            throw new IllegalArgumentException("Snapshot source keys must be unique");
        }
        if (node.structuralLeaf() && !node.children().isEmpty()) {
            throw new IllegalArgumentException("Structural leaves must not have snapshot children");
        }
        for (final NodeSnapshot child : node.children()) {
            validateSafeIdentityTraversal(child, mapReferenceId, sourceKeys);
        }
    }

    private static StructuralElement projectNode(final NodeSnapshot snapshot, final String mapName) {
        if (snapshot.excluded()) {
            return null;
        }
        if (snapshot.graphGroup()) {
            return new ExactNode(ProjectedNode.of(ProjectedNodeKey.of(snapshot.key()), snapshot.label(), mapName,
                true));
        }
        if (snapshot.structuralLeaf()) {
            return new ExactNode(ProjectedNode.of(ProjectedNodeKey.of(snapshot.key()), snapshot.label(), mapName,
                false));
        }
        final ExactEnclosure enclosure = new ExactEnclosure(EnclosureKey.of(snapshot.key()), snapshot.label(), mapName);
        for (final NodeSnapshot child : snapshot.children()) {
            final StructuralElement childElement = projectNode(child, mapName);
            if (childElement != null) {
                enclosure.children.add(childElement);
            }
        }
        return enclosure;
    }

    private static void collectNodes(final StructuralElement element, final List<ProjectedNode> nodes) {
        if (element instanceof ExactNode) {
            nodes.add(((ExactNode) element).node);
            return;
        }
        for (final StructuralElement child : ((ExactEnclosure) element).children) {
            collectNodes(child, nodes);
        }
    }

    private static HullTree compress(final ExactEnclosure start, final Optional<EnclosureHullKey> parentHull) {
        final List<ExactEnclosure> chain = new ArrayList<ExactEnclosure>();
        chain.add(start);
        ExactEnclosure deepest = start;
        while (deepest.children.size() == 1 && deepest.children.get(0) instanceof ExactEnclosure) {
            final ExactEnclosure child = (ExactEnclosure) deepest.children.get(0);
            if (child.children.size() != 1) {
                break;
            }
            chain.add(child);
            deepest = child;
        }

        final List<EnclosureKey> endpointKeys = new ArrayList<EnclosureKey>(chain.size());
        final List<SafeNodeLabel> labels = new ArrayList<SafeNodeLabel>(chain.size());
        for (final ExactEnclosure enclosure : chain) {
            endpointKeys.add(enclosure.key);
            labels.add(enclosure.label);
        }
        final EnclosureHullKey hullKey = EnclosureHullKey.of(endpointKeys);
        final List<ProjectedNodeKey> directNodes = new ArrayList<ProjectedNodeKey>();
        final List<HullTree> childHulls = new ArrayList<HullTree>();
        for (final StructuralElement child : deepest.children) {
            if (child instanceof ExactNode) {
                directNodes.add(((ExactNode) child).node.key());
            }
            else {
                childHulls.add(compress((ExactEnclosure) child, Optional.of(hullKey)));
            }
        }
        final List<EnclosureHullKey> directEnclosures = new ArrayList<EnclosureHullKey>(childHulls.size());
        for (final HullTree childHull : childHulls) {
            directEnclosures.add(childHull.enclosure.hullKey());
        }
        final ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, endpointKeys, labels,
            start.mapName, parentHull, directNodes, directEnclosures, !parentHull.isPresent());
        return new HullTree(enclosure, childHulls);
    }

    private static void collectEnclosures(final HullTree tree, final List<ProjectedEnclosure> enclosures) {
        enclosures.add(tree.enclosure);
        for (final HullTree child : tree.children) {
            collectEnclosures(child, enclosures);
        }
    }

    private interface StructuralElement {
    }

    private static final class ExactNode implements StructuralElement {
        private final ProjectedNode node;

        private ExactNode(final ProjectedNode node) {
            this.node = node;
        }
    }

    private static final class ExactEnclosure implements StructuralElement {
        private final EnclosureKey key;
        private final SafeNodeLabel label;
        private final String mapName;
        private final List<StructuralElement> children = new ArrayList<StructuralElement>();

        private ExactEnclosure(final EnclosureKey key, final SafeNodeLabel label, final String mapName) {
            this.key = key;
            this.label = label;
            this.mapName = mapName;
        }
    }

    private static final class HullTree {
        private final ProjectedEnclosure enclosure;
        private final List<HullTree> children;

        private HullTree(final ProjectedEnclosure enclosure, final List<HullTree> children) {
            this.enclosure = enclosure;
            this.children = children;
        }
    }
}
