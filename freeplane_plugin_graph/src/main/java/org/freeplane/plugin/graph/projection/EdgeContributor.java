package org.freeplane.plugin.graph.projection;

import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;

public final class EdgeContributor {
    private final ContributorKey key;
    private final SourceNodeKey source;
    private final Optional<NodeReference> sourceReference;
    private final NodeReference target;
    private final ProjectedEndpointKey projectedSource;
    private final ProjectedEndpointKey projectedTarget;
    private final boolean arrowAtSource;
    private final boolean arrowAtTarget;
    private final String sourceLabel;
    private final String middleLabel;
    private final String targetLabel;
    private final Optional<ConnectorDescriptor> connectorDescriptor;
    private final Optional<GraphRelationshipRecord> graphRelationship;

    private EdgeContributor(final ContributorKey key, final SourceNodeKey source,
            final NodeReference target, final ProjectedEndpointKey projectedSource,
            final ProjectedEndpointKey projectedTarget, final boolean arrowAtSource,
            final boolean arrowAtTarget, final String sourceLabel, final String middleLabel,
            final String targetLabel, final Optional<ConnectorDescriptor> connectorDescriptor,
            final Optional<GraphRelationshipRecord> graphRelationship) {
        this.key = Objects.requireNonNull(key, "key");
        this.source = Objects.requireNonNull(source, "source");
        this.sourceReference = source.persistedReference();
        this.target = Objects.requireNonNull(target, "target");
        this.projectedSource = Objects.requireNonNull(projectedSource, "projectedSource");
        this.projectedTarget = Objects.requireNonNull(projectedTarget, "projectedTarget");
        if (!source.mapReferenceId().equals(projectedSource.mapReferenceId())
                || !target.mapReferenceId().equals(projectedTarget.mapReferenceId())) {
            throw new IllegalArgumentException("Projected endpoints must retain exact endpoint map IDs");
        }
        this.arrowAtSource = arrowAtSource;
        this.arrowAtTarget = arrowAtTarget;
        this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
        this.middleLabel = Objects.requireNonNull(middleLabel, "middleLabel");
        this.targetLabel = Objects.requireNonNull(targetLabel, "targetLabel");
        this.connectorDescriptor = Objects.requireNonNull(connectorDescriptor, "connectorDescriptor");
        this.graphRelationship = Objects.requireNonNull(graphRelationship, "graphRelationship");
        if (this.connectorDescriptor.isPresent() == this.graphRelationship.isPresent()) {
            throw new IllegalArgumentException("A contributor must have exactly one source record");
        }
    }

    public static EdgeContributor nativeConnector(final ConnectorSnapshot connector,
            final ProjectedEndpointKey projectedSource, final ProjectedEndpointKey projectedTarget) {
        final ConnectorSnapshot snapshot = Objects.requireNonNull(connector, "connector");
        final ProjectedEndpointKey sourceEndpoint = Objects.requireNonNull(projectedSource, "projectedSource");
        final ProjectedEndpointKey targetEndpoint = Objects.requireNonNull(projectedTarget, "projectedTarget");
        final ConnectorDescriptor descriptor = snapshot.descriptor();
        if (!descriptor.source().mapReferenceId().equals(sourceEndpoint.mapReferenceId())
                || !descriptor.target().mapReferenceId().equals(targetEndpoint.mapReferenceId())) {
            throw new IllegalArgumentException("Projected endpoints must retain connector map IDs");
        }
        return new EdgeContributor(snapshot.key(), descriptor.source(), descriptor.target(), sourceEndpoint,
            targetEndpoint, descriptor.arrowAtSource(), descriptor.arrowAtTarget(), descriptor.sourceLabel(),
            descriptor.middleLabel(), descriptor.targetLabel(), Optional.of(descriptor),
            Optional.<GraphRelationshipRecord>empty());
    }

    public static EdgeContributor graphRelationship(final GraphRelationshipRecord relationship,
            final ProjectedEndpointKey projectedSource, final ProjectedEndpointKey projectedTarget) {
        final GraphRelationshipRecord record = Objects.requireNonNull(relationship, "relationship");
        final ProjectedEndpointKey sourceEndpoint = Objects.requireNonNull(projectedSource, "projectedSource");
        final ProjectedEndpointKey targetEndpoint = Objects.requireNonNull(projectedTarget, "projectedTarget");
        if (!record.source().mapReferenceId().equals(sourceEndpoint.mapReferenceId())
                || !record.target().mapReferenceId().equals(targetEndpoint.mapReferenceId())) {
            throw new IllegalArgumentException("Projected endpoints must retain relationship map IDs");
        }
        final boolean arrowAtSource;
        final boolean arrowAtTarget;
        if (record.direction() == RelationshipDirection.FORWARD) {
            arrowAtSource = false;
            arrowAtTarget = true;
        }
        else if (record.direction() == RelationshipDirection.BIDIRECTIONAL) {
            arrowAtSource = true;
            arrowAtTarget = true;
        }
        else if (record.direction() == RelationshipDirection.UNDIRECTED) {
            arrowAtSource = false;
            arrowAtTarget = false;
        }
        else {
            throw new IllegalArgumentException("Unknown relationship direction");
        }
        return new EdgeContributor(ContributorKey.graphRelationship(record.id()),
            SourceNodeKey.persisted(record.source()), record.target(), sourceEndpoint, targetEndpoint,
            arrowAtSource, arrowAtTarget, "", "", "", Optional.<ConnectorDescriptor>empty(),
            Optional.of(record));
    }

    public ContributorKey key() {
        return key;
    }

    public SourceNodeKey source() {
        return source;
    }

    public Optional<NodeReference> sourceReference() {
        return sourceReference;
    }

    public NodeReference target() {
        return target;
    }

    public ProjectedEndpointKey projectedSource() {
        return projectedSource;
    }

    public ProjectedEndpointKey projectedTarget() {
        return projectedTarget;
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

    public Optional<ConnectorDescriptor> connectorDescriptor() {
        return connectorDescriptor;
    }

    public Optional<GraphRelationshipRecord> graphRelationship() {
        return graphRelationship;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EdgeContributor)) {
            return false;
        }
        final EdgeContributor that = (EdgeContributor) other;
        return arrowAtSource == that.arrowAtSource && arrowAtTarget == that.arrowAtTarget
            && key.equals(that.key) && source.equals(that.source) && target.equals(that.target)
            && projectedSource.equals(that.projectedSource) && projectedTarget.equals(that.projectedTarget)
            && sourceLabel.equals(that.sourceLabel) && middleLabel.equals(that.middleLabel)
            && targetLabel.equals(that.targetLabel) && connectorDescriptor.equals(that.connectorDescriptor)
            && graphRelationship.equals(that.graphRelationship);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, source, target, projectedSource, projectedTarget, arrowAtSource, arrowAtTarget,
            sourceLabel, middleLabel, targetLabel, connectorDescriptor, graphRelationship);
    }

    @Override
    public String toString() {
        return "EdgeContributor{" + "key=" + key + ", source=" + source + ", target=" + target
            + ", projectedSource=" + projectedSource + ", projectedTarget=" + projectedTarget
            + ", arrowAtSource=" + arrowAtSource + ", arrowAtTarget=" + arrowAtTarget + '}';
    }
}
