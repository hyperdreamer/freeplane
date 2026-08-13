package org.freeplane.plugin.graph.geometry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class LayoutPositions {
    private final Map<ProjectedNodeKey, LayoutPoint> nodes;
    private final Map<EnclosureHullKey, LayoutPoint> anchors;

    private LayoutPositions(final Map<ProjectedNodeKey, LayoutPoint> nodes,
            final Map<EnclosureHullKey, LayoutPoint> anchors) {
        this.nodes = copyNodes(nodes);
        this.anchors = copyAnchors(anchors);
    }

    public static LayoutPositions of(final Map<ProjectedNodeKey, LayoutPoint> nodes,
            final Map<EnclosureHullKey, LayoutPoint> anchors) {
        return new LayoutPositions(nodes, anchors);
    }

    public Map<ProjectedNodeKey, LayoutPoint> nodes() {
        return nodes;
    }

    public Map<EnclosureHullKey, LayoutPoint> anchors() {
        return anchors;
    }

    private static Map<ProjectedNodeKey, LayoutPoint> copyNodes(final Map<ProjectedNodeKey, LayoutPoint> values) {
        Objects.requireNonNull(values, "nodes");
        final Map<ProjectedNodeKey, LayoutPoint> copy = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "nodes key"),
                Objects.requireNonNull(entry.getValue(), "nodes value"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<EnclosureHullKey, LayoutPoint> copyAnchors(final Map<EnclosureHullKey, LayoutPoint> values) {
        Objects.requireNonNull(values, "anchors");
        final Map<EnclosureHullKey, LayoutPoint> copy = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "anchors key"),
                Objects.requireNonNull(entry.getValue(), "anchors value"));
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LayoutPositions)) {
            return false;
        }
        final LayoutPositions that = (LayoutPositions) other;
        return nodes.equals(that.nodes) && anchors.equals(that.anchors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, anchors);
    }

    @Override
    public String toString() {
        return "LayoutPositions{" + "nodeCount=" + nodes.size() + ", anchorCount=" + anchors.size() + '}';
    }
}
