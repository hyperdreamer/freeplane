package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
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
    public void retainSuppressedDeepEnclosureInProjectionWhileExcludingItsEndpointFromVisibility() {
        GraphProjection projection = project(deepBranchRoot());
        EnclosureKey deepKey = EnclosureKey.of(source("third-level"));
        ProjectedEndpointKey deepEndpoint = ProjectedEndpointKey.ofEnclosure(deepKey);

        assertThat(enclosureFor(projection, deepKey).endpointKeys()).containsExactly(deepKey);
        assertThat(ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures()))
            .doesNotContain(deepEndpoint);
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

    private static GraphProjection project(NodeSnapshot root) {
        WorkspaceDocument workspace = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder()
            .maps(Collections.singletonList(registration()))
            .build();
        Map<MapReferenceId, MapAvailability> availability = new LinkedHashMap<MapReferenceId, MapAvailability>();
        availability.put(MAP, MapAvailability.AVAILABLE);
        return new ProjectionEngine().project(ProjectionInput.of(1, workspace,
            Collections.singletonList(MapSnapshot.of(MAP, 1, "Map", root,
                Collections.<PersistedNodeId>emptySet(), false)), availability));
    }

    private static ProjectedEnclosure enclosureFor(GraphProjection projection, EnclosureKey key) {
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.endpointKeys().contains(key)) {
                return enclosure;
            }
        }
        throw new AssertionError("No enclosure for " + key);
    }

    private static MapReference registration() {
        return MapReference.of(MAP, 1, URI.create("maps/one.mm"), true, "#4E79A7",
            Collections.<UnknownXml>emptyList());
    }

    private static NodeSnapshot deepBranchRoot() {
        return enclosureNode("root", enclosureNode("first-level", enclosureNode("second-level",
            enclosureNode("third-level", leaf("third-left"), leaf("third-right"))),
            enclosureNode("first-level-sibling", leaf("first-sibling-left"), leaf("first-sibling-right"))),
            enclosureNode("root-sibling", leaf("root-sibling-left"), leaf("root-sibling-right")));
    }

    private static NodeSnapshot enclosureNode(String id, NodeSnapshot... children) {
        return NodeSnapshot.of(source(id), SafeNodeLabel.of(id, id), false, false, false, Arrays.asList(children));
    }

    private static NodeSnapshot leaf(String id) {
        return NodeSnapshot.of(source(id), SafeNodeLabel.of(id, id), true, false, false,
            Collections.<NodeSnapshot>emptyList());
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
