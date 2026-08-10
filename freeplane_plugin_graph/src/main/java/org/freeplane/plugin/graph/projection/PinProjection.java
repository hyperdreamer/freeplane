package org.freeplane.plugin.graph.projection;

import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PinRecord;

public final class PinProjection {
    private final PinRecord record;
    private final Optional<ProjectedNodeKey> projectedNode;

    private PinProjection(final PinRecord record, final Optional<ProjectedNodeKey> projectedNode) {
        this.record = Objects.requireNonNull(record, "record");
        this.projectedNode = Objects.requireNonNull(projectedNode, "projectedNode");
    }

    public static PinProjection active(final PinRecord record, final ProjectedNodeKey node) {
        final PinRecord pin = Objects.requireNonNull(record, "record");
        final ProjectedNodeKey projected = Objects.requireNonNull(node, "node");
        if (!projected.source().persistent()
                || !projected.source().persistedReference().get().equals(pin.node())) {
            throw new IllegalArgumentException("Active pin must retain its exact persisted node reference");
        }
        return new PinProjection(pin, Optional.of(projected));
    }

    public static PinProjection dormant(final PinRecord record) {
        return new PinProjection(Objects.requireNonNull(record, "record"), Optional.<ProjectedNodeKey>empty());
    }

    public PinRecord record() {
        return record;
    }

    public NodeReference source() {
        return record.node();
    }

    public Optional<ProjectedNodeKey> projectedNode() {
        return projectedNode;
    }

    public boolean active() {
        return projectedNode.isPresent();
    }

    public boolean dormant() {
        return !active();
    }

    public double x() {
        return record.x();
    }

    public double y() {
        return record.y();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PinProjection)) {
            return false;
        }
        final PinProjection that = (PinProjection) other;
        return record.equals(that.record) && projectedNode.equals(that.projectedNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(record, projectedNode);
    }

    @Override
    public String toString() {
        return "PinProjection{" + "source=" + source() + ", active=" + active() + '}';
    }
}
