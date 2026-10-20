package org.freeplane.plugin.graph.projection.input;

import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;

public final class ConnectorDescriptor {
    private final SourceNodeKey source;
    private final NodeReference target;
    private final boolean arrowAtSource;
    private final boolean arrowAtTarget;
    private final String sourceLabel;
    private final String middleLabel;
    private final String targetLabel;

    private ConnectorDescriptor(final SourceNodeKey source, final NodeReference target,
            final boolean arrowAtSource, final boolean arrowAtTarget, final String sourceLabel,
            final String middleLabel, final String targetLabel) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        final MapReferenceId sourceMap = this.source.mapReferenceId();
        if (!sourceMap.equals(this.target.mapReferenceId())) {
            throw new IllegalArgumentException("Connector endpoints must belong to one map");
        }
        if (this.source.persistent() && this.source.persistedReference().get().equals(this.target)) {
            throw new IllegalArgumentException("A connector must not connect a node to itself");
        }
        this.arrowAtSource = arrowAtSource;
        this.arrowAtTarget = arrowAtTarget;
        this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
        this.middleLabel = Objects.requireNonNull(middleLabel, "middleLabel");
        this.targetLabel = Objects.requireNonNull(targetLabel, "targetLabel");
    }

    public static ConnectorDescriptor of(final SourceNodeKey source, final NodeReference target,
            final boolean arrowAtSource, final boolean arrowAtTarget, final String sourceLabel,
            final String middleLabel, final String targetLabel) {
        return new ConnectorDescriptor(source, target, arrowAtSource, arrowAtTarget, sourceLabel, middleLabel,
            targetLabel);
    }

    public SourceNodeKey source() {
        return source;
    }

    public NodeReference target() {
        return target;
    }

    public boolean arrowAtSource() {
        return arrowAtSource;
    }

    public boolean arrowAtTarget() {
        return arrowAtTarget;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public String middleLabel() {
        return middleLabel;
    }

    public String targetLabel() {
        return targetLabel;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectorDescriptor)) {
            return false;
        }
        final ConnectorDescriptor that = (ConnectorDescriptor) other;
        return arrowAtSource == that.arrowAtSource && arrowAtTarget == that.arrowAtTarget
            && source.equals(that.source) && target.equals(that.target) && sourceLabel.equals(that.sourceLabel)
            && middleLabel.equals(that.middleLabel) && targetLabel.equals(that.targetLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, arrowAtSource, arrowAtTarget, sourceLabel, middleLabel, targetLabel);
    }

    @Override
    public String toString() {
        return "ConnectorDescriptor{" + "source=" + source + ", target=" + target
            + ", arrowAtSource=" + arrowAtSource + ", arrowAtTarget=" + arrowAtTarget + '}';
    }
}
