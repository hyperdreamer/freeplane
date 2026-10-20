package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GraphProjection {
    private final long generation;
    private final List<ProjectedNode> nodes;
    private final List<ProjectedEnclosure> enclosures;
    private final List<ProjectedEdge> edges;
    private final List<RelationshipResolution> relationshipResolutions;
    private final List<PinProjection> pins;
    private final Map<ProjectedNodeKey, NodeProminence> prominence;

    private GraphProjection(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<ProjectedEdge> edges,
            final List<RelationshipResolution> relationshipResolutions, final List<PinProjection> pins,
            final Map<ProjectedNodeKey, NodeProminence> prominence) {
        if (generation < 0) {
            throw new IllegalArgumentException("Generation must be nonnegative");
        }
        this.generation = generation;
        this.nodes = copyValues(nodes, "nodes");
        this.enclosures = copyValues(enclosures, "enclosures");
        this.edges = copyValues(edges, "edges");
        this.relationshipResolutions = copyValues(relationshipResolutions, "relationshipResolutions");
        this.pins = copyValues(pins, "pins");
        this.prominence = copyProminence(prominence);
    }

    public static GraphProjection structure(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures) {
        return new GraphProjection(generation, nodes, enclosures, Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList(),
            Collections.<ProjectedNodeKey, NodeProminence>emptyMap());
    }

    public static GraphProjection resolved(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<RelationshipResolution> relationshipResolutions,
            final List<PinProjection> pins) {
        return new GraphProjection(generation, nodes, enclosures, Collections.<ProjectedEdge>emptyList(),
            relationshipResolutions, pins, Collections.<ProjectedNodeKey, NodeProminence>emptyMap());
    }

    public static GraphProjection projected(final long generation, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final List<ProjectedEdge> edges,
            final List<RelationshipResolution> relationshipResolutions, final List<PinProjection> pins) {
        return new GraphProjection(generation, nodes, enclosures, edges, relationshipResolutions, pins,
            ProminenceCalculator.calculate(nodes, enclosures, edges));
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

    public Map<ProjectedNodeKey, NodeProminence> prominence() {
        return prominence;
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

    private static Map<ProjectedNodeKey, NodeProminence> copyProminence(
            final Map<ProjectedNodeKey, NodeProminence> values) {
        Objects.requireNonNull(values, "prominence");
        final Map<ProjectedNodeKey, NodeProminence> copy =
            new LinkedHashMap<ProjectedNodeKey, NodeProminence>();
        for (final Map.Entry<ProjectedNodeKey, NodeProminence> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "prominence key"),
                Objects.requireNonNull(entry.getValue(), "prominence value"));
        }
        return Collections.unmodifiableMap(copy);
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
            && pins.equals(that.pins) && prominence.equals(that.prominence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generation, nodes, enclosures, edges, relationshipResolutions, pins, prominence);
    }

    @Override
    public String toString() {
        return "GraphProjection{" + "generation=" + generation + ", nodeCount=" + nodes.size()
            + ", enclosureCount=" + enclosures.size() + ", edgeCount=" + edges.size() + '}';
    }
}
