package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class CanonicalKeys {
    private CanonicalKeys() {
    }

    public static String escape(final String value) {
        Objects.requireNonNull(value, "value");
        return value.replace("%", "%25").replace("|", "%7C").replace(":", "%3A").replace(",", "%2C");
    }

    public static String endpoint(final SourceNodeKey source) {
        Objects.requireNonNull(source, "source");
        final StringBuilder builder = new StringBuilder();
        builder.append("m:").append(source.mapReferenceId().value()).append('|');
        if (source.persistent()) {
            builder.append("p:").append(escape(source.persistedReference().get().nodeId().value()));
        }
        else {
            builder.append("t:");
            final List<Integer> path = source.structuralPath();
            for (int index = 0; index < path.size(); index++) {
                if (index > 0) {
                    builder.append('.');
                }
                builder.append(path.get(index).intValue());
            }
        }
        return builder.toString();
    }

    public static String endpoint(final ProjectedEndpointKey endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.isNode()) {
            return "n:" + endpoint(endpoint.node().get().source());
        }
        return "e:" + endpoint(endpoint.enclosure().get().source());
    }

    public static String hull(final EnclosureHullKey hull) {
        Objects.requireNonNull(hull, "hull");
        final List<String> endpoints = new ArrayList<String>();
        for (final EnclosureKey endpoint : hull.endpointKeys()) {
            endpoints.add(endpoint(endpoint.source()));
        }
        Collections.sort(endpoints);
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < endpoints.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(endpoints.get(index));
        }
        return builder.toString();
    }

    public static String nodeField(final ProjectedNodeKey node) {
        Objects.requireNonNull(node, "node");
        return "n:" + endpoint(node.source());
    }

    public static String anchorField(final EnclosureHullKey hull) {
        return "a:" + hull(hull);
    }

    public static String pair(final EnclosureHullKey first, final EnclosureHullKey second) {
        return sortedJoin(hull(first), hull(second));
    }

    public static String node(final ProjectedNodeKey node) {
        return nodeField(node);
    }

    public static String nodePair(final ProjectedNodeKey first, final ProjectedNodeKey second) {
        return sortedJoin(node(first), node(second));
    }

    public static String hullPair(final EnclosureHullKey first, final EnclosureHullKey second) {
        return pair(first, second);
    }

    public static String edge(final ProjectedEdgeKey edge) {
        Objects.requireNonNull(edge, "edge");
        return endpoint(edge.first()) + "~" + endpoint(edge.second());
    }

    public static String edgePair(final ProjectedEdgeKey first, final ProjectedEdgeKey second) {
        return sortedJoin(edge(first), edge(second));
    }

    public static String edgeNodePair(final ProjectedEdgeKey edge, final ProjectedNodeKey node) {
        return edge(edge) + "|" + node(node);
    }

    public static MapReferenceId mapOfField(final String field) {
        Objects.requireNonNull(field, "field");
        if (field.length() < 5
                || (field.charAt(0) != 'n' && field.charAt(0) != 'a') || field.charAt(1) != ':') {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        final String endpoint = field.substring(2);
        if (!endpoint.startsWith("m:")) {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        final int separator = endpoint.indexOf('|');
        if (separator < 0) {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        return MapReferenceId.of(endpoint.substring(2, separator));
    }

    private static String sortedJoin(final String first, final String second) {
        if (first.compareTo(second) <= 0) {
            return first + "|" + second;
        }
        return second + "|" + first;
    }
}
