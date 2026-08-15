package org.freeplane.plugin.graph.geometry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class GraphGeometry {
    private final Map<ProjectedNodeKey, NodeGeometry> nodes;
    private final Map<EnclosureHullKey, HullGeometry> hulls;
    private final Map<EnclosureKey, LabelPlacement> labels;
    private final Map<EnclosureKey, EnclosureHullKey> hullByEnclosureKey;

    private GraphGeometry(final Map<ProjectedNodeKey, NodeGeometry> nodes,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        this(nodes, hulls, Collections.<EnclosureKey, LabelPlacement>emptyMap());
    }

    private GraphGeometry(final Map<ProjectedNodeKey, NodeGeometry> nodes,
            final Map<EnclosureHullKey, HullGeometry> hulls,
            final Map<EnclosureKey, LabelPlacement> labels) {
        this.nodes = copyNodes(nodes);
        this.hulls = copyHulls(hulls);
        final Map<EnclosureKey, EnclosureHullKey> lookup = new LinkedHashMap<EnclosureKey, EnclosureHullKey>();
        for (final EnclosureHullKey hullKey : this.hulls.keySet()) {
            for (final EnclosureKey endpoint : hullKey.endpointKeys()) {
                if (lookup.put(endpoint, hullKey) != null) {
                    throw new IllegalArgumentException("An enclosure key must belong to exactly one hull");
                }
            }
        }
        this.hullByEnclosureKey = Collections.unmodifiableMap(lookup);
        this.labels = copyLabels(labels, lookup);
    }

    public static GraphGeometry of(final Map<ProjectedNodeKey, NodeGeometry> nodes,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        return new GraphGeometry(nodes, hulls);
    }

    public static GraphGeometry of(final Map<ProjectedNodeKey, NodeGeometry> nodes,
            final Map<EnclosureHullKey, HullGeometry> hulls,
            final Map<EnclosureKey, LabelPlacement> labels) {
        return new GraphGeometry(nodes, hulls, labels);
    }

    public Map<ProjectedNodeKey, NodeGeometry> nodes() {
        return nodes;
    }

    public Map<EnclosureHullKey, HullGeometry> hulls() {
        return hulls;
    }

    public Map<EnclosureKey, LabelPlacement> labels() {
        return labels;
    }

    public LayoutPoint edgeAttachment(final ProjectedEndpointKey endpoint, final LayoutPoint toward) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(toward, "toward");
        if (endpoint.isNode()) {
            final NodeGeometry geometry = nodes.get(endpoint.node().get());
            if (geometry == null) {
                throw new IllegalArgumentException("Geometry has no node for endpoint " + endpoint);
            }
            return geometry.boundaryToward(toward);
        }
        final EnclosureHullKey hullKey = hullByEnclosureKey.get(endpoint.enclosure().get());
        if (hullKey == null) {
            throw new IllegalArgumentException("Geometry has no hull for endpoint " + endpoint);
        }
        return hulls.get(hullKey).nearestBoundaryPoint(toward);
    }

    private static Map<ProjectedNodeKey, NodeGeometry> copyNodes(final Map<ProjectedNodeKey, NodeGeometry> values) {
        Objects.requireNonNull(values, "nodes");
        final Map<ProjectedNodeKey, NodeGeometry> copy = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (final Map.Entry<ProjectedNodeKey, NodeGeometry> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "nodes key"),
                Objects.requireNonNull(entry.getValue(), "nodes value"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<EnclosureHullKey, HullGeometry> copyHulls(final Map<EnclosureHullKey, HullGeometry> values) {
        Objects.requireNonNull(values, "hulls");
        final Map<EnclosureHullKey, HullGeometry> copy = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        for (final Map.Entry<EnclosureHullKey, HullGeometry> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "hulls key"),
                Objects.requireNonNull(entry.getValue(), "hulls value"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<EnclosureKey, LabelPlacement> copyLabels(
            final Map<EnclosureKey, LabelPlacement> values,
            final Map<EnclosureKey, EnclosureHullKey> hullLookup) {
        Objects.requireNonNull(values, "labels");
        final Map<EnclosureKey, LabelPlacement> copy = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        for (final Map.Entry<EnclosureKey, LabelPlacement> entry : values.entrySet()) {
            final EnclosureKey key = Objects.requireNonNull(entry.getKey(), "labels key");
            final LabelPlacement value = Objects.requireNonNull(entry.getValue(), "labels value");
            if (!hullLookup.containsKey(key)) {
                throw new IllegalArgumentException("Labels must address an existing enclosure hull endpoint");
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphGeometry)) {
            return false;
        }
        final GraphGeometry that = (GraphGeometry) other;
        return nodes.equals(that.nodes) && hulls.equals(that.hulls) && labels.equals(that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, hulls, labels);
    }

    @Override
    public String toString() {
        return "GraphGeometry{" + "nodeCount=" + nodes.size() + ", hullCount=" + hulls.size()
            + ", labelCount=" + labels.size() + '}';
    }
}
