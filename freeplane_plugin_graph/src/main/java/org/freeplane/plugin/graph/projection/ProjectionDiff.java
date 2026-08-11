package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProjectionDiff {
    private final long beforeGeneration;
    private final long afterGeneration;
    private final List<ProjectedNodeKey> addedNodes;
    private final List<ProjectedNodeKey> removedNodes;
    private final List<ProjectedNodeKey> changedNodes;
    private final List<EnclosureHullKey> addedEnclosures;
    private final List<EnclosureHullKey> removedEnclosures;
    private final List<EnclosureHullKey> changedEnclosures;
    private final List<ProjectedEdgeKey> addedEdges;
    private final List<ProjectedEdgeKey> removedEdges;
    private final List<ProjectedEdgeKey> changedEdges;

    private ProjectionDiff(final GraphProjection before, final GraphProjection after,
            final List<ProjectedNodeKey> addedNodes, final List<ProjectedNodeKey> removedNodes,
            final List<ProjectedNodeKey> changedNodes, final List<EnclosureHullKey> addedEnclosures,
            final List<EnclosureHullKey> removedEnclosures, final List<EnclosureHullKey> changedEnclosures,
            final List<ProjectedEdgeKey> addedEdges, final List<ProjectedEdgeKey> removedEdges,
            final List<ProjectedEdgeKey> changedEdges) {
        this.beforeGeneration = before.generation();
        this.afterGeneration = after.generation();
        this.addedNodes = immutable(addedNodes);
        this.removedNodes = immutable(removedNodes);
        this.changedNodes = immutable(changedNodes);
        this.addedEnclosures = immutable(addedEnclosures);
        this.removedEnclosures = immutable(removedEnclosures);
        this.changedEnclosures = immutable(changedEnclosures);
        this.addedEdges = immutable(addedEdges);
        this.removedEdges = immutable(removedEdges);
        this.changedEdges = immutable(changedEdges);
    }

    public static ProjectionDiff between(final GraphProjection before, final GraphProjection after) {
        final GraphProjection previous = Objects.requireNonNull(before, "before");
        final GraphProjection current = Objects.requireNonNull(after, "after");
        final Map<ProjectedNodeKey, ProjectedNode> beforeNodes = indexNodes(previous);
        final Map<ProjectedNodeKey, ProjectedNode> afterNodes = indexNodes(current);
        final Map<EnclosureHullKey, ProjectedEnclosure> beforeEnclosures = indexEnclosures(previous);
        final Map<EnclosureHullKey, ProjectedEnclosure> afterEnclosures = indexEnclosures(current);
        final Map<ProjectedEdgeKey, ProjectedEdge> beforeEdges = indexEdges(previous);
        final Map<ProjectedEdgeKey, ProjectedEdge> afterEdges = indexEdges(current);

        final List<ProjectedNodeKey> addedNodes = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedNodeKey> changedNodes = new ArrayList<ProjectedNodeKey>();
        for (final ProjectedNode node : current.nodes()) {
            final ProjectedNode previousNode = beforeNodes.get(node.key());
            if (previousNode == null) {
                addedNodes.add(node.key());
            }
            else if (!previousNode.equals(node)) {
                changedNodes.add(node.key());
            }
        }
        final List<ProjectedNodeKey> removedNodes = new ArrayList<ProjectedNodeKey>();
        for (final ProjectedNode node : previous.nodes()) {
            if (!afterNodes.containsKey(node.key())) {
                removedNodes.add(node.key());
            }
        }

        final List<EnclosureHullKey> addedEnclosures = new ArrayList<EnclosureHullKey>();
        final List<EnclosureHullKey> changedEnclosures = new ArrayList<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : current.enclosures()) {
            final ProjectedEnclosure previousEnclosure = beforeEnclosures.get(enclosure.hullKey());
            if (previousEnclosure == null) {
                addedEnclosures.add(enclosure.hullKey());
            }
            else if (!previousEnclosure.equals(enclosure)) {
                changedEnclosures.add(enclosure.hullKey());
            }
        }
        final List<EnclosureHullKey> removedEnclosures = new ArrayList<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : previous.enclosures()) {
            if (!afterEnclosures.containsKey(enclosure.hullKey())) {
                removedEnclosures.add(enclosure.hullKey());
            }
        }

        final List<ProjectedEdgeKey> addedEdges = new ArrayList<ProjectedEdgeKey>();
        final List<ProjectedEdgeKey> changedEdges = new ArrayList<ProjectedEdgeKey>();
        for (final ProjectedEdge edge : current.edges()) {
            final ProjectedEdge previousEdge = beforeEdges.get(edge.key());
            if (previousEdge == null) {
                addedEdges.add(edge.key());
            }
            else if (!previousEdge.equals(edge)) {
                changedEdges.add(edge.key());
            }
        }
        final List<ProjectedEdgeKey> removedEdges = new ArrayList<ProjectedEdgeKey>();
        for (final ProjectedEdge edge : previous.edges()) {
            if (!afterEdges.containsKey(edge.key())) {
                removedEdges.add(edge.key());
            }
        }
        return new ProjectionDiff(previous, current, addedNodes, removedNodes, changedNodes, addedEnclosures,
            removedEnclosures, changedEnclosures, addedEdges, removedEdges, changedEdges);
    }

    public long beforeGeneration() {
        return beforeGeneration;
    }

    public long afterGeneration() {
        return afterGeneration;
    }

    public List<ProjectedNodeKey> addedNodes() {
        return addedNodes;
    }

    public List<ProjectedNodeKey> removedNodes() {
        return removedNodes;
    }

    public List<ProjectedNodeKey> changedNodes() {
        return changedNodes;
    }

    public List<EnclosureHullKey> addedEnclosures() {
        return addedEnclosures;
    }

    public List<EnclosureHullKey> removedEnclosures() {
        return removedEnclosures;
    }

    public List<EnclosureHullKey> changedEnclosures() {
        return changedEnclosures;
    }

    public List<ProjectedEdgeKey> addedEdges() {
        return addedEdges;
    }

    public List<ProjectedEdgeKey> removedEdges() {
        return removedEdges;
    }

    public List<ProjectedEdgeKey> changedEdges() {
        return changedEdges;
    }

    public boolean isEmpty() {
        return addedNodes.isEmpty() && removedNodes.isEmpty() && changedNodes.isEmpty()
            && addedEnclosures.isEmpty() && removedEnclosures.isEmpty() && changedEnclosures.isEmpty()
            && addedEdges.isEmpty() && removedEdges.isEmpty() && changedEdges.isEmpty();
    }

    private static Map<ProjectedNodeKey, ProjectedNode> indexNodes(final GraphProjection projection) {
        final Map<ProjectedNodeKey, ProjectedNode> result = new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        for (final ProjectedNode node : projection.nodes()) {
            if (result.put(node.key(), node) != null) {
                throw new IllegalArgumentException("Projection node keys must be unique");
            }
        }
        return result;
    }

    private static Map<EnclosureHullKey, ProjectedEnclosure> indexEnclosures(final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> result =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (result.put(enclosure.hullKey(), enclosure) != null) {
                throw new IllegalArgumentException("Projection enclosure keys must be unique");
            }
        }
        return result;
    }

    private static Map<ProjectedEdgeKey, ProjectedEdge> indexEdges(final GraphProjection projection) {
        final Map<ProjectedEdgeKey, ProjectedEdge> result = new LinkedHashMap<ProjectedEdgeKey, ProjectedEdge>();
        for (final ProjectedEdge edge : projection.edges()) {
            if (result.put(edge.key(), edge) != null) {
                throw new IllegalArgumentException("Projection edge keys must be unique");
            }
        }
        return result;
    }

    private static <T> List<T> immutable(final List<T> values) {
        Objects.requireNonNull(values, "values");
        final List<T> copy = new ArrayList<T>(values.size());
        for (final T value : values) {
            copy.add(Objects.requireNonNull(value, "values entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectionDiff)) {
            return false;
        }
        final ProjectionDiff that = (ProjectionDiff) other;
        return beforeGeneration == that.beforeGeneration && afterGeneration == that.afterGeneration
            && addedNodes.equals(that.addedNodes) && removedNodes.equals(that.removedNodes)
            && changedNodes.equals(that.changedNodes) && addedEnclosures.equals(that.addedEnclosures)
            && removedEnclosures.equals(that.removedEnclosures) && changedEnclosures.equals(that.changedEnclosures)
            && addedEdges.equals(that.addedEdges) && removedEdges.equals(that.removedEdges)
            && changedEdges.equals(that.changedEdges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beforeGeneration, afterGeneration, addedNodes, removedNodes, changedNodes,
            addedEnclosures, removedEnclosures, changedEnclosures, addedEdges, removedEdges, changedEdges);
    }

    @Override
    public String toString() {
        return "ProjectionDiff{" + "beforeGeneration=" + beforeGeneration + ", afterGeneration="
            + afterGeneration + ", addedNodeCount=" + addedNodes.size() + ", removedNodeCount="
            + removedNodes.size() + ", changedNodeCount=" + changedNodes.size() + ", addedEnclosureCount="
            + addedEnclosures.size() + ", removedEnclosureCount=" + removedEnclosures.size()
            + ", changedEnclosureCount=" + changedEnclosures.size() + ", addedEdgeCount=" + addedEdges.size()
            + ", removedEdgeCount=" + removedEdges.size() + ", changedEdgeCount=" + changedEdges.size() + '}';
    }
}
