package org.freeplane.plugin.graph.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;

public final class GraphSearchModel {
    public GraphSearchModel() {
    }

    public static Set<ProjectedEndpointKey> search(final CanvasState state, final String query) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(query, "query");
        final String normalizedQuery = normalize(query.trim());
        if (normalizedQuery.isEmpty()) {
            return Collections.emptySet();
        }
        final Map<ProjectedEndpointKey, SafeText> index = index(state);
        final List<ProjectedEndpointKey> endpoints = new ArrayList<ProjectedEndpointKey>();
        for (Map.Entry<ProjectedEndpointKey, SafeText> entry : index.entrySet()) {
            if (entry.getValue().matches(normalizedQuery)) {
                endpoints.add(entry.getKey());
            }
        }
        Collections.sort(endpoints);
        return immutableOrderedSet(endpoints);
    }

    public static String tooltip(final CanvasState state, final ProjectedEndpointKey endpoint) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(endpoint, "endpoint");
        final SafeText value = index(state).get(endpoint);
        return value == null ? null : value.tooltip();
    }

    private static Map<ProjectedEndpointKey, SafeText> index(final CanvasState state) {
        final Map<ProjectedEndpointKey, SafeText> index =
            new LinkedHashMap<ProjectedEndpointKey, SafeText>();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final List<SafeNodeLabel> labels = enclosure.labels();
            for (int i = 0; i < enclosure.endpointKeys().size(); i++) {
                final org.freeplane.plugin.graph.projection.EnclosureKey key =
                    enclosure.endpointKeys().get(i);
                final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(key);
                final SafeText value = new SafeText(enclosureLabel(labels, i), enclosure.mapName());
                index.put(endpoint, merge(index.get(endpoint), value));
            }
        }
        return index;
    }

    private static SafeText merge(final SafeText first, final SafeText second) {
        if (first == null) {
            return second;
        }
        return new SafeText(first.text + " / " + second.text, first.mapName);
    }

    private static String enclosureLabel(final List<SafeNodeLabel> labels, final int index) {
        if (labels.isEmpty()) {
            return "";
        }
        final int labelIndex = labels.size() == 1 ? 0 : Math.min(index, labels.size() - 1);
        return labels.get(labelIndex).fullText();
    }

    private static String normalize(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static Set<ProjectedEndpointKey> immutableOrderedSet(
            final List<ProjectedEndpointKey> endpoints) {
        final LinkedHashSet<ProjectedEndpointKey> result =
            new LinkedHashSet<ProjectedEndpointKey>(endpoints);
        return Collections.unmodifiableSet(result);
    }

    private static final class SafeText {
        private final String text;
        private final String mapName;

        private SafeText(final String text, final String mapName) {
            this.text = text;
            this.mapName = mapName;
        }

        private boolean matches(final String query) {
            return normalize(text).contains(query) || normalize(mapName).contains(query);
        }

        private String tooltip() {
            return text + " - " + mapName;
        }
    }
}
