package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

public class EnclosureTierShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void retainTwoRegistrationTiersThroughMissingLoadingRetryAndAvailableTransitions() {
        NodeSnapshot childLeaf = node(MAP_ONE, "child-leaf", true, false, false);
        NodeSnapshot child = node(MAP_ONE, "child", false, true, false, childLeaf);
        NodeSnapshot directLeaf = node(MAP_ONE, "direct-leaf", true, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, child, directLeaf);
        NodeSnapshot secondRoot = node(MAP_TWO, "second-root", true, false, false);
        WorkspaceDocument twoActive = workspace(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true));

        assertTwoMapTiers(project(twoActive, MapAvailability.AVAILABLE, MapAvailability.MISSING,
            map(MAP_ONE, 1, root)), root, child);
        assertTwoMapTiers(project(twoActive, MapAvailability.AVAILABLE, MapAvailability.LOADING,
            map(MAP_ONE, 1, root)), root, child);
        assertTwoMapTiers(project(twoActive, MapAvailability.AVAILABLE, MapAvailability.RELOAD_REQUIRED,
            map(MAP_ONE, 1, root)), root, child);
        assertTwoMapTiers(project(twoActive, MapAvailability.AVAILABLE, MapAvailability.AVAILABLE,
            map(MAP_ONE, 1, root), map(MAP_TWO, 2, secondRoot)), root, child);

        WorkspaceDocument oneActive = workspace(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, false));
        GraphProjection removed = project(oneActive, MapAvailability.AVAILABLE, MapAvailability.INACTIVE,
            map(MAP_ONE, 1, root), map(MAP_TWO, 2, secondRoot));
        assertThat(tierFor(removed, root)).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(tierFor(removed, child)).isEqualTo(BoundaryTier.EMPHATIC);

        GraphProjection reactivatedBeforeAvailable = project(twoActive,
            MapAvailability.AVAILABLE, MapAvailability.LOADING, map(MAP_ONE, 1, root));
        assertTwoMapTiers(reactivatedBeforeAvailable, root, child);
    }

    @Test
    public void showOnlyFirstAndSecondLevelsForOneActiveMindmap() {
        GraphProjection projection = project(oneActiveWorkspace(), MapAvailability.AVAILABLE,
            map(MAP_ONE, 1, deepBranchRoot()));

        assertThat(tierFor(projection, rootNode())).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(tierFor(projection, firstLevelNode())).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(tierFor(projection, secondLevelNode())).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(tierFor(projection, thirdLevelNode())).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(enclosureFor(projection, thirdLevelNode()).endpointKeys())
            .containsExactly(EnclosureKey.of(thirdLevelNode().key()));
    }

    @Test
    public void showRootAndFirstLevelForMultipleActiveMindmaps() {
        GraphProjection projection = project(twoActiveWorkspace(), MapAvailability.AVAILABLE,
            MapAvailability.AVAILABLE, map(MAP_ONE, 1, deepBranchRoot()), map(MAP_TWO, 2, leafRoot()));

        assertThat(tierFor(projection, rootNode())).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(tierFor(projection, firstLevelNode())).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(tierFor(projection, secondLevelNode())).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(tierFor(projection, thirdLevelNode())).isEqualTo(BoundaryTier.SUPPRESSED);
    }

    @Test
    public void suppressTheMapRootFrameAndEmphasizeItsFirstBoundary() {
        NodeSnapshot firstLeaf = node(MAP_ONE, "first", true, false, false);
        NodeSnapshot secondLeaf = node(MAP_ONE, "second", true, false, false);
        NodeSnapshot inner = node(MAP_ONE, "inner", false, true, false, firstLeaf, secondLeaf);
        NodeSnapshot middle = node(MAP_ONE, "middle", false, false, false, inner);
        NodeSnapshot root = node(MAP_ONE, "root", false, false, false, middle);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));

        GraphProjection projection = project(workspace, MapAvailability.AVAILABLE, map(MAP_ONE, 1, root));

        ProjectedEnclosure rootHull = enclosureFor(projection, root);
        assertThat(rootHull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootHull.boundaryTier()).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(tierFor(projection, inner)).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(enclosureFor(projection, inner).endpointKeys()).containsExactly(EnclosureKey.of(inner.key()));
    }

    private static WorkspaceDocument oneActiveWorkspace() {
        return workspace(registration(MAP_ONE, 1, true));
    }

    private static WorkspaceDocument twoActiveWorkspace() {
        return workspace(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true));
    }

    private static NodeSnapshot deepBranchRoot() {
        return rootNode();
    }

    private static NodeSnapshot rootNode() {
        return node(MAP_ONE, "root", false, false, false, firstLevelNode(), rootSiblingNode());
    }

    private static NodeSnapshot firstLevelNode() {
        return node(MAP_ONE, "first-level", false, true, false, secondLevelNode(), firstLevelSiblingNode());
    }

    private static NodeSnapshot secondLevelNode() {
        return node(MAP_ONE, "second-level", false, true, false, thirdLevelNode(), secondLevelSiblingNode());
    }

    private static NodeSnapshot thirdLevelNode() {
        return node(MAP_ONE, "third-level", false, true, false, leaf("third-left"), leaf("third-right"));
    }

    private static NodeSnapshot rootSiblingNode() {
        return node(MAP_ONE, "root-sibling", false, false, false, leaf("root-sibling-left"),
            leaf("root-sibling-right"));
    }

    private static NodeSnapshot firstLevelSiblingNode() {
        return node(MAP_ONE, "first-level-sibling", false, false, false, leaf("first-sibling-left"),
            leaf("first-sibling-right"));
    }

    private static NodeSnapshot secondLevelSiblingNode() {
        return node(MAP_ONE, "second-level-sibling", false, false, false, leaf("second-sibling-left"),
            leaf("second-sibling-right"));
    }

    private static NodeSnapshot leafRoot() {
        return node(MAP_TWO, "leaf-root", true, false, false);
    }

    private static NodeSnapshot leaf(String id) {
        return node(MAP_ONE, id, true, false, false);
    }

    private static void assertTwoMapTiers(GraphProjection projection, NodeSnapshot root, NodeSnapshot child) {
        assertThat(tierFor(projection, root)).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(tierFor(projection, child)).isEqualTo(BoundaryTier.SUBTLE);
    }

    private static BoundaryTier tierFor(GraphProjection projection, NodeSnapshot endpoint) {
        return enclosureFor(projection, endpoint).boundaryTier();
    }

    private static ProjectedEnclosure enclosureFor(GraphProjection projection, NodeSnapshot endpoint) {
        EnclosureKey key = EnclosureKey.of(endpoint.key());
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.endpointKeys().contains(key)) {
                return enclosure;
            }
        }
        throw new AssertionError("No enclosure for " + key);
    }

    private static GraphProjection project(WorkspaceDocument workspace, MapAvailability first,
            MapAvailability second, MapSnapshot... maps) {
        return project(workspace, availability(workspace, first, second), maps);
    }

    private static GraphProjection project(WorkspaceDocument workspace, MapAvailability only,
            MapSnapshot... maps) {
        return project(workspace, availability(workspace, only), maps);
    }

    private static GraphProjection project(WorkspaceDocument workspace, Map<MapReferenceId, MapAvailability> statuses,
            MapSnapshot... maps) {
        return new ProjectionEngine().project(ProjectionInput.of(5, workspace, Arrays.asList(maps), statuses));
    }

    private static WorkspaceDocument workspace(MapReference... maps) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder()
            .maps(Arrays.asList(maps))
            .build();
    }

    private static MapReference registration(MapReferenceId id, long sequence, boolean active) {
        return MapReference.of(id, sequence, URI.create("maps/" + id.value() + ".mm"), active, "#4E79A7",
            Collections.<UnknownXml>emptyList());
    }

    private static Map<MapReferenceId, MapAvailability> availability(WorkspaceDocument workspace,
            MapAvailability... values) {
        if (workspace.maps().size() != values.length) {
            throw new IllegalArgumentException("Availability count must match registrations");
        }
        Map<MapReferenceId, MapAvailability> result = new LinkedHashMap<MapReferenceId, MapAvailability>();
        for (int index = 0; index < values.length; index++) {
            result.put(workspace.maps().get(index).id(), values[index]);
        }
        return result;
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root) {
        return MapSnapshot.of(id, workspaceOrder, "Map " + id.value(), root,
            Collections.<PersistedNodeId>emptySet(), false);
    }

    private static NodeSnapshot node(MapReferenceId map, String id, boolean structuralLeaf,
            boolean graphGroup, boolean excluded, NodeSnapshot... children) {
        return NodeSnapshot.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))),
            SafeNodeLabel.of(id, id), structuralLeaf, graphGroup, excluded, Arrays.asList(children));
    }
}
