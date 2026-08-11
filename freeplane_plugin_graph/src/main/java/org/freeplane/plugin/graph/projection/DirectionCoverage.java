package org.freeplane.plugin.graph.projection;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;

public final class DirectionCoverage {
    private DirectionCoverage() {
    }

    public static boolean covers(final Collection<EdgeContributor> contributors, final NodeReference source,
            final NodeReference target, final RelationshipDirection requested) {
        final Collection<EdgeContributor> values = Objects.requireNonNull(contributors, "contributors");
        final NodeReference requestedSource = Objects.requireNonNull(source, "source");
        final NodeReference requestedTarget = Objects.requireNonNull(target, "target");
        final RelationshipDirection direction = Objects.requireNonNull(requested, "requested");
        if (requestedSource.equals(requestedTarget)) {
            throw new IllegalArgumentException("A relationship pair must contain distinct nodes");
        }

        boolean arrowAtSource = false;
        boolean arrowAtTarget = false;
        boolean hasUndirectedContributor = false;
        for (final EdgeContributor value : values) {
            final EdgeContributor contributor = Objects.requireNonNull(value, "contributors entry");
            final Optional<NodeReference> contributorSource = contributor.sourceReference();
            if (!contributorSource.isPresent()) {
                continue;
            }
            final boolean sameOrientation = contributorSource.get().equals(requestedSource)
                && contributor.target().equals(requestedTarget);
            final boolean reverseOrientation = contributorSource.get().equals(requestedTarget)
                && contributor.target().equals(requestedSource);
            if (!sameOrientation && !reverseOrientation) {
                continue;
            }
            if (!contributor.arrowAtSource() && !contributor.arrowAtTarget()) {
                hasUndirectedContributor = true;
            }
            if (sameOrientation) {
                arrowAtSource = arrowAtSource || contributor.arrowAtSource();
                arrowAtTarget = arrowAtTarget || contributor.arrowAtTarget();
            }
            else {
                arrowAtSource = arrowAtSource || contributor.arrowAtTarget();
                arrowAtTarget = arrowAtTarget || contributor.arrowAtSource();
            }
        }

        if (direction == RelationshipDirection.FORWARD) {
            return arrowAtTarget;
        }
        if (direction == RelationshipDirection.BIDIRECTIONAL) {
            return arrowAtSource && arrowAtTarget;
        }
        if (direction == RelationshipDirection.UNDIRECTED) {
            return hasUndirectedContributor;
        }
        throw new IllegalArgumentException("Unknown relationship direction");
    }
}
