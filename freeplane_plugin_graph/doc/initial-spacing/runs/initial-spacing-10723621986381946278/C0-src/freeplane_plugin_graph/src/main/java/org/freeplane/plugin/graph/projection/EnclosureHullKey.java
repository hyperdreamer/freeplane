package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class EnclosureHullKey {
    private final List<EnclosureKey> endpointKeys;
    private final MapReferenceId mapReferenceId;

    private EnclosureHullKey(final List<EnclosureKey> endpointKeys) {
        this.endpointKeys = copyEndpointKeys(endpointKeys);
        this.mapReferenceId = this.endpointKeys.get(0).mapReferenceId();
    }

    public static EnclosureHullKey of(final List<EnclosureKey> endpointKeys) {
        return new EnclosureHullKey(endpointKeys);
    }

    public List<EnclosureKey> endpointKeys() {
        return endpointKeys;
    }

    public MapReferenceId mapReferenceId() {
        return mapReferenceId;
    }

    private static List<EnclosureKey> copyEndpointKeys(final List<EnclosureKey> values) {
        Objects.requireNonNull(values, "endpointKeys");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Enclosure hull keys must not be empty");
        }
        final List<EnclosureKey> copy = new ArrayList<EnclosureKey>(values.size());
        final Set<EnclosureKey> unique = new HashSet<EnclosureKey>();
        MapReferenceId mapReferenceId = null;
        for (final EnclosureKey value : values) {
            final EnclosureKey endpoint = Objects.requireNonNull(value, "endpointKeys entry");
            if (mapReferenceId == null) {
                mapReferenceId = endpoint.mapReferenceId();
            }
            else if (!mapReferenceId.equals(endpoint.mapReferenceId())) {
                throw new IllegalArgumentException("Enclosure hull keys must belong to one map");
            }
            if (!unique.add(endpoint)) {
                throw new IllegalArgumentException("Enclosure hull keys must be unique");
            }
            copy.add(endpoint);
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EnclosureHullKey)) {
            return false;
        }
        final EnclosureHullKey that = (EnclosureHullKey) other;
        return endpointKeys.equals(that.endpointKeys);
    }

    @Override
    public int hashCode() {
        return endpointKeys.hashCode();
    }

    @Override
    public String toString() {
        return "EnclosureHullKey{" + "endpointKeys=" + endpointKeys + '}';
    }
}
