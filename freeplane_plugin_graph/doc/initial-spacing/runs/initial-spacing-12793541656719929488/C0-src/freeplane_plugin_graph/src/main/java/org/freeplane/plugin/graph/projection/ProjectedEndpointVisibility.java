package org.freeplane.plugin.graph.projection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProjectedEndpointVisibility {
    private ProjectedEndpointVisibility() {
    }

    public static Set<ProjectedEndpointKey> visibleEndpoints(final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures) {
        final List<ProjectedNode> projectedNodes = Objects.requireNonNull(nodes, "nodes");
        final List<ProjectedEnclosure> projectedEnclosures = Objects.requireNonNull(enclosures,
            "enclosures");
        final Set<ProjectedEndpointKey> visible = new LinkedHashSet<ProjectedEndpointKey>();
        for (final ProjectedNode node : projectedNodes) {
            final ProjectedNode projectedNode = Objects.requireNonNull(node, "nodes entry");
            visible.add(ProjectedEndpointKey.ofNode(projectedNode.key()));
        }
        for (final ProjectedEnclosure enclosure : projectedEnclosures) {
            final ProjectedEnclosure projectedEnclosure = Objects.requireNonNull(enclosure,
                "enclosures entry");
            if (projectedEnclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            for (final EnclosureKey endpoint : projectedEnclosure.endpointKeys()) {
                visible.add(ProjectedEndpointKey.ofEnclosure(Objects.requireNonNull(endpoint,
                    "enclosure endpoint")));
            }
        }
        return Collections.unmodifiableSet(visible);
    }
}
