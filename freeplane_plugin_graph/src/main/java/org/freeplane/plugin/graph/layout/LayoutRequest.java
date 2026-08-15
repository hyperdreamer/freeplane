package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class LayoutRequest {
    private final WorkspaceId workspace;
    private final GraphProjection projection;
    private final ProjectionDiff diff;
    private final List<PinProjection> pins;

    private LayoutRequest(final WorkspaceId workspace, final GraphProjection projection, final ProjectionDiff diff,
            final List<PinProjection> pins) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.diff = Objects.requireNonNull(diff, "diff");
        if (diff.afterGeneration() != projection.generation()) {
            throw new IllegalArgumentException("Projection diff must end at the projection generation");
        }
        validateProminence(projection);
        this.pins = copyPins(pins);
    }

    public static LayoutRequest of(final WorkspaceId workspace, final GraphProjection projection,
            final ProjectionDiff diff, final List<PinProjection> pins) {
        return new LayoutRequest(workspace, projection, diff, pins);
    }

    public WorkspaceId workspace() {
        return workspace;
    }

    public GraphProjection projection() {
        return projection;
    }

    public ProjectionDiff diff() {
        return diff;
    }

    public List<PinProjection> pins() {
        return pins;
    }

    private static List<PinProjection> copyPins(final List<PinProjection> values) {
        Objects.requireNonNull(values, "pins");
        final List<PinProjection> copy = new ArrayList<PinProjection>(values.size());
        for (final PinProjection value : values) {
            copy.add(Objects.requireNonNull(value, "pins entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static void validateProminence(final GraphProjection projection) {
        final Set<ProjectedNodeKey> nodeKeys = new LinkedHashSet<ProjectedNodeKey>();
        for (final ProjectedNode node : projection.nodes()) {
            if (!nodeKeys.add(node.key())) {
                throw new IllegalArgumentException("Projection node keys must be unique");
            }
        }
        if (!nodeKeys.equals(projection.prominence().keySet())) {
            throw new IllegalArgumentException("Projection prominence must cover exactly the projected nodes");
        }
    }
}
