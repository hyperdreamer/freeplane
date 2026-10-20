package org.freeplane.plugin.graph.projection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class ProjectedEndpointKey implements Comparable<ProjectedEndpointKey> {
    private final ProjectedNodeKey node;
    private final EnclosureKey enclosure;

    private ProjectedEndpointKey(final ProjectedNodeKey node, final EnclosureKey enclosure) {
        this.node = node;
        this.enclosure = enclosure;
    }

    public static ProjectedEndpointKey ofNode(final ProjectedNodeKey node) {
        return new ProjectedEndpointKey(Objects.requireNonNull(node, "node"), null);
    }

    public static ProjectedEndpointKey ofEnclosure(final EnclosureKey enclosure) {
        return new ProjectedEndpointKey(null, Objects.requireNonNull(enclosure, "enclosure"));
    }

    public boolean isNode() {
        return node != null;
    }

    public boolean isEnclosure() {
        return enclosure != null;
    }

    public Optional<ProjectedNodeKey> node() {
        return Optional.ofNullable(node);
    }

    public Optional<EnclosureKey> enclosure() {
        return Optional.ofNullable(enclosure);
    }

    public MapReferenceId mapReferenceId() {
        return isNode() ? node.mapReferenceId() : enclosure.mapReferenceId();
    }

    @Override
    public int compareTo(final ProjectedEndpointKey other) {
        Objects.requireNonNull(other, "other");
        int result = mapReferenceId().value().compareTo(other.mapReferenceId().value());
        if (result != 0) {
            return result;
        }
        result = compareSources(source(), other.source());
        if (result != 0) {
            return result;
        }
        return Boolean.compare(isNode(), other.isNode());
    }

    private SourceNodeKey source() {
        return isNode() ? node.source() : enclosure.source();
    }

    private static int compareSources(final SourceNodeKey first, final SourceNodeKey second) {
        int result = Boolean.compare(first.persistent(), second.persistent());
        if (result != 0) {
            return result;
        }
        if (first.persistent()) {
            result = first.persistedReference().get().nodeId().value()
                .compareTo(second.persistedReference().get().nodeId().value());
            if (result != 0) {
                return result;
            }
        }
        return comparePaths(first.structuralPath(), second.structuralPath());
    }

    private static int comparePaths(final List<Integer> first, final List<Integer> second) {
        final int limit = Math.min(first.size(), second.size());
        for (int index = 0; index < limit; index++) {
            final int result = Integer.compare(first.get(index).intValue(), second.get(index).intValue());
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(first.size(), second.size());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectedEndpointKey)) {
            return false;
        }
        final ProjectedEndpointKey that = (ProjectedEndpointKey) other;
        return Objects.equals(node, that.node) && Objects.equals(enclosure, that.enclosure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, enclosure);
    }

    @Override
    public String toString() {
        return "ProjectedEndpointKey{" + "mapReferenceId=" + mapReferenceId() + ", kind="
            + (isNode() ? "node" : "enclosure") + '}';
    }
}
