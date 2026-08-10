package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GraphProjection {
    private final long generation;
    private final List<ProjectedNode> nodes;
    private final List<ProjectedEnclosure> enclosures;
    private final List<ProjectedEdge> edges;
    private final List<RelationshipResolution> relationshipResolutions;
    private final List<PinProjection> pins;

    private GraphProjection(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<RelationshipResolution> relationshipResolutions,
            final List<PinProjection> pins) {
        if (generation < 0) {
            throw new IllegalArgumentException("Generation must be nonnegative");
        }
        this.generation = generation;
        this.nodes = copyValues(nodes, "nodes");
        this.enclosures = copyValues(enclosures, "enclosures");
        this.edges = Collections.emptyList();
        this.relationshipResolutions = copyValues(relationshipResolutions, "relationshipResolutions");
        this.pins = copyValues(pins, "pins");
    }

    public static GraphProjection structure(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures) {
        return new GraphProjection(generation, nodes, enclosures, Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    public static GraphProjection resolved(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<RelationshipResolution> relationshipResolutions,
            final List<PinProjection> pins) {
        return new GraphProjection(generation, nodes, enclosures, relationshipResolutions, pins);
    }

    public long generation() {
        return generation;
    }

    public List<ProjectedNode> nodes() {
        return nodes;
    }

    public List<ProjectedEnclosure> enclosures() {
        return enclosures;
    }

    public List<ProjectedEdge> edges() {
        return edges;
    }

    public List<RelationshipResolution> relationshipResolutions() {
        return relationshipResolutions;
    }

    public List<PinProjection> pins() {
        return pins;
    }

    public int projectedNodeCount() {
        return nodes.size();
    }

    public int projectedEdgeCount() {
        return edges.size();
    }

    private static <T> List<T> copyValues(final List<T> values, final String name) {
        Objects.requireNonNull(values, name);
        final List<T> copy = new ArrayList<T>(values.size());
        for (final T value : values) {
            copy.add(Objects.requireNonNull(value, name + " entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphProjection)) {
            return false;
        }
        final GraphProjection that = (GraphProjection) other;
        return generation == that.generation && nodes.equals(that.nodes) && enclosures.equals(that.enclosures)
            && edges.equals(that.edges) && relationshipResolutions.equals(that.relationshipResolutions)
            && pins.equals(that.pins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generation, nodes, enclosures, edges, relationshipResolutions, pins);
    }

    @Override
    public String toString() {
        return "GraphProjection{" + "generation=" + generation + ", nodeCount=" + nodes.size()
            + ", enclosureCount=" + enclosures.size() + ", edgeCount=" + edges.size() + '}';
    }
}

// Removed when Task 9 creates public ProjectedEdge.java.
final class ProjectedEdge {
    private ProjectedEdge() {
    }
}
