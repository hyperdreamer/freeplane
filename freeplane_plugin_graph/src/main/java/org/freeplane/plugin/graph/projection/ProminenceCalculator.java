package org.freeplane.plugin.graph.projection;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProminenceCalculator {
    private ProminenceCalculator() {
    }

    public static Map<ProjectedNodeKey, NodeProminence> calculate(final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<ProjectedEdge> edges) {
        final List<ProjectedNode> projectedNodes = Objects.requireNonNull(nodes, "nodes");
        final List<ProjectedEnclosure> projectedEnclosures = Objects.requireNonNull(enclosures, "enclosures");
        final List<ProjectedEdge> projectedEdges = Objects.requireNonNull(edges, "edges");
        final Map<EnclosureKey, EnclosureHullKey> hullsByEndpoint =
            new HashMap<EnclosureKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projectedEnclosures) {
            final EnclosureHullKey hullKey = enclosure.hullKey();
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                hullsByEndpoint.put(endpoint, hullKey);
            }
        }
        final Map<ProjectedNodeKey, MutableTargets> targetsByNode =
            new LinkedHashMap<ProjectedNodeKey, MutableTargets>();
        for (final ProjectedNode node : projectedNodes) {
            targetsByNode.put(node.key(), new MutableTargets());
        }
        for (final ProjectedEdge edge : projectedEdges) {
            if (edge.arrowAtSecond()) {
                register(targetsByNode, hullsByEndpoint, edge.first(), edge.second());
            }
            if (edge.arrowAtFirst()) {
                register(targetsByNode, hullsByEndpoint, edge.second(), edge.first());
            }
        }
        final Map<ProjectedNodeKey, NodeProminence> prominence =
            new LinkedHashMap<ProjectedNodeKey, NodeProminence>();
        for (final Map.Entry<ProjectedNodeKey, MutableTargets> entry : targetsByNode.entrySet()) {
            prominence.put(entry.getKey(), NodeProminence.of(entry.getValue().count()));
        }
        return Collections.unmodifiableMap(prominence);
    }

    private static void register(final Map<ProjectedNodeKey, MutableTargets> targetsByNode,
            final Map<EnclosureKey, EnclosureHullKey> hullsByEndpoint, final ProjectedEndpointKey source,
            final ProjectedEndpointKey target) {
        if (source.isEnclosure()) {
            return;
        }
        final MutableTargets targets = targetsByNode.get(source.node().get());
        if (targets == null) {
            return;
        }
        if (target.isNode()) {
            targets.addNode(target.node().get());
            return;
        }
        final EnclosureHullKey hullKey = hullsByEndpoint.get(target.enclosure().get());
        if (hullKey != null) {
            targets.addHull(hullKey);
        }
    }

    private static final class MutableTargets {
        private final Set<ProjectedNodeKey> nodes = new HashSet<ProjectedNodeKey>();
        private final Set<EnclosureHullKey> hulls = new HashSet<EnclosureHullKey>();

        private void addNode(final ProjectedNodeKey node) {
            nodes.add(node);
        }

        private void addHull(final EnclosureHullKey hull) {
            hulls.add(hull);
        }

        private int count() {
            return nodes.size() + hulls.size();
        }
    }
}
