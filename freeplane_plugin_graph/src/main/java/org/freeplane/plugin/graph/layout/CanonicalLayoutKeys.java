package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class CanonicalLayoutKeys {
    private CanonicalLayoutKeys() {
    }

    static String escape(final String value) {
        Objects.requireNonNull(value, "value");
        return value.replace("%", "%25").replace("|", "%7C").replace(":", "%3A").replace(",", "%2C");
    }

    static String endpoint(final SourceNodeKey source) {
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

    static String hull(final EnclosureHullKey hull) {
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

    static String nodeField(final ProjectedNodeKey node) {
        Objects.requireNonNull(node, "node");
        return "n:" + endpoint(node.source());
    }

    static String anchorField(final EnclosureHullKey hull) {
        return "a:" + hull(hull);
    }

    static String pair(final EnclosureHullKey first, final EnclosureHullKey second) {
        final String firstKey = hull(first);
        final String secondKey = hull(second);
        if (firstKey.compareTo(secondKey) <= 0) {
            return firstKey + "|" + secondKey;
        }
        return secondKey + "|" + firstKey;
    }

    static MapReferenceId mapOfField(final String field) {
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
}
