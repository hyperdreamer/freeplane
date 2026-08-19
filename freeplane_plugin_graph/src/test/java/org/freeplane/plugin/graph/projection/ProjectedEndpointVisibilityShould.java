package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class ProjectedEndpointVisibilityShould {
    private static final MapReferenceId MAP = MapReferenceId.of(
        "00000000-0000-0000-0000-000000000001");

    @Test
    public void includeNodesAndNonSuppressedEnclosureEndpointsInProjectedOrder() {
        ProjectedNode node = node("node");
        ProjectedEnclosure subtle = enclosure("subtle", BoundaryTier.SUBTLE);
        ProjectedEnclosure suppressed = enclosure("suppressed", BoundaryTier.SUPPRESSED);

        Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            Collections.singletonList(node), Arrays.asList(subtle, suppressed));

        assertThat(new ArrayList<ProjectedEndpointKey>(visible)).containsExactly(
            ProjectedEndpointKey.ofNode(node.key()),
            ProjectedEndpointKey.ofEnclosure(subtle.endpointKeys().get(0)));
        assertThatThrownBy(() -> visible.add(ProjectedEndpointKey.ofEnclosure(
            suppressed.endpointKeys().get(0))))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void rejectNullListsAndEntries() {
        ProjectedNode node = node("node");
        ProjectedEnclosure enclosure = enclosure("enclosure", BoundaryTier.SUBTLE);

        assertThatThrownBy(() -> ProjectedEndpointVisibility.visibleEndpoints(null,
            Collections.singletonList(enclosure))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProjectedEndpointVisibility.visibleEndpoints(
            Collections.singletonList(node), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProjectedEndpointVisibility.visibleEndpoints(
            Arrays.asList(node, null), Collections.singletonList(enclosure)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProjectedEndpointVisibility.visibleEndpoints(
            Collections.singletonList(node), Arrays.asList(enclosure, null)))
            .isInstanceOf(NullPointerException.class);
    }

    private static ProjectedNode node(String id) {
        return ProjectedNode.of(ProjectedNodeKey.of(source(id)), SafeNodeLabel.of(id, id), "Map", false);
    }

    private static ProjectedEnclosure enclosure(String id, BoundaryTier tier) {
        EnclosureKey endpoint = EnclosureKey.of(source(id));
        List<EnclosureKey> endpoints = Collections.singletonList(endpoint);
        return ProjectedEnclosure.of(EnclosureHullKey.of(endpoints), endpoints,
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, tier);
    }

    private static SourceNodeKey source(String id) {
        return SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id)));
    }
}
