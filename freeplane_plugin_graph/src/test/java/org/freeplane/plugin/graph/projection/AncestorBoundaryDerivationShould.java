package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
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

public class AncestorBoundaryDerivationShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void projectOneMapMathShapeTreeWithExactTiersAndDirectNodes() {
        // Tree:
        // root (depth 0, unmarked)
        //   -> ZFC (depth 1, unmarked)
        //        -> Axioms (depth 2, unmarked)
        //             -> Extensionality (marked)
        //             -> Pairing (marked)
        //             -> Union (marked)
        //        -> Basic Definitions and Theorems (depth 2, unmarked)
        //             -> Russell Theorem (marked)
        NodeSnapshot extensionality = markedNode(MAP_ONE, "ext", "Extensionality");
        NodeSnapshot pairing = markedNode(MAP_ONE, "pair", "Pairing");
        NodeSnapshot union = markedNode(MAP_ONE, "union", "Union");
        NodeSnapshot axioms = plainNode(MAP_ONE, "axioms", "Axioms", extensionality, pairing, union);

        NodeSnapshot russell = markedNode(MAP_ONE, "russell", "Russell Theorem");
        NodeSnapshot definitions = plainNode(MAP_ONE, "defs", "Basic Definitions and Theorems", russell);

        NodeSnapshot zfc = plainNode(MAP_ONE, "zfc", "ZFC", axioms, definitions);
        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", zfc);

        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        // 4 marked nodes
        assertThat(projection.nodes()).hasSize(4);
        assertThat(projection.nodes()).extracting(ProjectedNode::key)
            .containsExactly(
                ProjectedNodeKey.of(extensionality.key()),
                ProjectedNodeKey.of(pairing.key()),
                ProjectedNodeKey.of(union.key()),
                ProjectedNodeKey.of(russell.key())
            );

        // Enclosures: root (suppressed), ZFC (emphatic), Axioms (subtle), Definitions (subtle)
        assertThat(projection.enclosures()).hasSize(4);

        ProjectedEnclosure rootEnclosure = projection.enclosures().get(0);
        assertThat(rootEnclosure.mapRoot()).isTrue();
        assertThat(rootEnclosure.boundaryTier()).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(rootEnclosure.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootEnclosure.parentHull()).isEmpty();

        ProjectedEnclosure zfcEnclosure = projection.enclosures().get(1);
        assertThat(zfcEnclosure.mapRoot()).isFalse();
        assertThat(zfcEnclosure.boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(zfcEnclosure.endpointKeys()).containsExactly(EnclosureKey.of(zfc.key()));
        assertThat(zfcEnclosure.parentHull()).contains(rootEnclosure.hullKey());

        ProjectedEnclosure axiomsEnclosure = projection.enclosures().get(2);
        assertThat(axiomsEnclosure.mapRoot()).isFalse();
        assertThat(axiomsEnclosure.boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(axiomsEnclosure.endpointKeys()).containsExactly(EnclosureKey.of(axioms.key()));
        assertThat(axiomsEnclosure.parentHull()).contains(zfcEnclosure.hullKey());
        assertThat(axiomsEnclosure.directNodes()).containsExactly(
            ProjectedNodeKey.of(extensionality.key()),
            ProjectedNodeKey.of(pairing.key()),
            ProjectedNodeKey.of(union.key())
        );

        ProjectedEnclosure defsEnclosure = projection.enclosures().get(3);
        assertThat(defsEnclosure.mapRoot()).isFalse();
        assertThat(defsEnclosure.boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(defsEnclosure.endpointKeys()).containsExactly(EnclosureKey.of(definitions.key()));
        assertThat(defsEnclosure.parentHull()).contains(zfcEnclosure.hullKey());
        assertThat(defsEnclosure.directNodes()).containsExactly(
            ProjectedNodeKey.of(russell.key())
        );

        assertThat(rootEnclosure.directEnclosures()).containsExactly(zfcEnclosure.hullKey());
        assertThat(zfcEnclosure.directEnclosures()).containsExactly(
            axiomsEnclosure.hullKey(), defsEnclosure.hullKey()
        );
        assertThat(axiomsEnclosure.directEnclosures()).isEmpty();
        assertThat(defsEnclosure.directEnclosures()).isEmpty();
    }

    @Test
    public void preserveLevel1AndLevel2BoundariesAndForwardMarkedNodeThroughTransparentLevel3() {
        // root (0) -> Level 1 (1) -> Level 2 (2) -> Level 3 (3) -> marked Group (4)
        NodeSnapshot group = markedNode(MAP_ONE, "group", "Group");
        NodeSnapshot level3 = plainNode(MAP_ONE, "level3", "Level 3", group);
        NodeSnapshot level2 = plainNode(MAP_ONE, "level2", "Level 2", level3);
        NodeSnapshot level1 = plainNode(MAP_ONE, "level1", "Level 1", level2);
        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", level1);

        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        // 1 marked node
        assertThat(projection.nodes()).hasSize(1);
        assertThat(projection.nodes().get(0).key()).isEqualTo(ProjectedNodeKey.of(group.key()));

        // Enclosures: root (suppressed), Level 1 (emphatic), Level 2 (subtle). No Level 3 hull!
        assertThat(projection.enclosures()).hasSize(3);

        ProjectedEnclosure rootEnclosure = projection.enclosures().get(0);
        ProjectedEnclosure level1Enclosure = projection.enclosures().get(1);
        ProjectedEnclosure level2Enclosure = projection.enclosures().get(2);

        assertThat(level1Enclosure.endpointKeys()).containsExactly(EnclosureKey.of(level1.key()));
        assertThat(level1Enclosure.boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(level1Enclosure.parentHull()).contains(rootEnclosure.hullKey());

        assertThat(level2Enclosure.endpointKeys()).containsExactly(EnclosureKey.of(level2.key()));
        assertThat(level2Enclosure.boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(level2Enclosure.parentHull()).contains(level1Enclosure.hullKey());

        // Marked node forwarded to the Level 2 boundary
        assertThat(level2Enclosure.directNodes()).containsExactly(ProjectedNodeKey.of(group.key()));
        assertThat(level2Enclosure.directEnclosures()).isEmpty();
    }

    @Test
    public void atomizeMarkedNodeWithDescendantsAndOmitDescendantNodesAndEnclosures() {
        // Marked node with marked and unmarked descendants
        NodeSnapshot descendantLeaf = plainNode(MAP_ONE, "leaf", "Descendant Leaf");
        NodeSnapshot descendantMarked = markedNode(MAP_ONE, "inner-marked", "Inner Marked");
        NodeSnapshot parentMarked = NodeSnapshot.of(
            SourceNodeKey.persisted(NodeReference.of(MAP_ONE, PersistedNodeId.of("parent-marked"))),
            SafeNodeLabel.of("Parent Marked", "Parent Marked"),
            false, true, false, Arrays.asList(descendantLeaf, descendantMarked)
        );
        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", parentMarked);

        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).hasSize(1);
        assertThat(projection.nodes().get(0).key()).isEqualTo(ProjectedNodeKey.of(parentMarked.key()));

        // Only root enclosure should exist, parentMarked is a direct node of root
        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.enclosures().get(0).directNodes())
            .containsExactly(ProjectedNodeKey.of(parentMarked.key()));
    }

    @Test
    public void projectMarkedNodeAtRawDepth1AsDirectNodeOfRootNotAsBoundary() {
        // root (0) -> marked Node (1)
        NodeSnapshot marked = markedNode(MAP_ONE, "marked", "Marked at 1");
        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", marked);

        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).hasSize(1);
        assertThat(projection.nodes().get(0).key()).isEqualTo(ProjectedNodeKey.of(marked.key()));

        // Only root enclosure, marked node is direct node of root
        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projection.enclosures().get(0).endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(projection.enclosures().get(0).directNodes()).containsExactly(ProjectedNodeKey.of(marked.key()));
    }

    @Test
    public void useRegisteredActiveMapCountToSelectMultiMapTiersEvenWithOnlyOneAvailableSnapshot() {
        // Two registered active maps, but only MAP_ONE is available (MAP_TWO is LOADING)
        NodeSnapshot group = markedNode(MAP_ONE, "group", "Group");
        NodeSnapshot level1 = plainNode(MAP_ONE, "level1", "Level 1", group);
        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", level1);

        WorkspaceDocument workspace = workspace(
            registration(MAP_ONE, 1, true),
            registration(MAP_TWO, 2, true)
        );

        Map<MapReferenceId, MapAvailability> availability = new LinkedHashMap<MapReferenceId, MapAvailability>();
        availability.put(MAP_ONE, MapAvailability.AVAILABLE);
        availability.put(MAP_TWO, MapAvailability.LOADING);

        GraphProjection projection = new ProjectionEngine().project(ProjectionInput.of(
            1, workspace, Collections.singletonList(map(MAP_ONE, 1, root)), availability
        ));

        // Two active maps: root is EMPHATIC, depth 1 is SUBTLE, depth 2 would be transparent
        assertThat(projection.enclosures()).hasSize(2);
        ProjectedEnclosure rootEnclosure = projection.enclosures().get(0);
        assertThat(rootEnclosure.boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(rootEnclosure.mapRoot()).isTrue();

        ProjectedEnclosure level1Enclosure = projection.enclosures().get(1);
        assertThat(level1Enclosure.boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(level1Enclosure.endpointKeys()).containsExactly(EnclosureKey.of(level1.key()));
    }

    @Test
    public void omitExcludedBranchesAndBranchesWithoutReachableMarkedNodesExceptMapRoot() {
        NodeSnapshot marked = markedNode(MAP_ONE, "marked", "Marked");
        NodeSnapshot branchWithMarked = plainNode(MAP_ONE, "valid", "Valid Branch", marked);

        NodeSnapshot excludedMarked = NodeSnapshot.of(
            SourceNodeKey.persisted(NodeReference.of(MAP_ONE, PersistedNodeId.of("ex-marked"))),
            SafeNodeLabel.of("Excluded Marked", "Excluded Marked"),
            true, true, true, Collections.<NodeSnapshot>emptyList()
        );
        NodeSnapshot excludedBranch = plainNode(MAP_ONE, "ex-branch", "Excluded Branch", excludedMarked);

        NodeSnapshot emptyLeaf = plainNode(MAP_ONE, "empty-leaf", "Empty Leaf");
        NodeSnapshot emptyBranch = plainNode(MAP_ONE, "empty-branch", "Empty Branch", emptyLeaf);

        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", branchWithMarked, excludedBranch, emptyBranch);

        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        // Only root and branchWithMarked enclosures exist
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(0).endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(projection.enclosures().get(1).endpointKeys()).containsExactly(EnclosureKey.of(branchWithMarked.key()));
        assertThat(projection.nodes()).extracting(ProjectedNode::key)
            .containsExactly(ProjectedNodeKey.of(marked.key()));

        // Also test: an available map whose root has no reachable marked node still emits its map-root frame
        NodeSnapshot emptyRoot = plainNode(MAP_ONE, "empty-root", "Empty Root", emptyLeaf);
        GraphProjection emptyProjection = project(workspace, map(MAP_ONE, 1, emptyRoot));
        assertThat(emptyProjection.nodes()).isEmpty();
        assertThat(emptyProjection.enclosures()).hasSize(1);
        assertThat(emptyProjection.enclosures().get(0).endpointKeys()).containsExactly(EnclosureKey.of(emptyRoot.key()));
        assertThat(emptyProjection.enclosures().get(0).mapRoot()).isTrue();
    }

    @Test
    public void countProjectedNodesAsMarkedProjectedNodesNotVisibleNonRootEnclosures() {
        NodeSnapshot group1 = markedNode(MAP_ONE, "g1", "Group 1");
        NodeSnapshot group2 = markedNode(MAP_ONE, "g2", "Group 2");
        NodeSnapshot level1 = plainNode(MAP_ONE, "level1", "Level 1", group1, group2);
        NodeSnapshot root = plainNode(MAP_ONE, "root", "Root", level1);

        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));
        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).hasSize(2);
        // non-root enclosures is 1 (level1), but projectedNodeCount() must be 2 (marked nodes)
        assertThat(projection.projectedNodeCount()).isEqualTo(2);
    }

    private static WorkspaceDocument workspace(MapReference... registrations) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder()
            .maps(Arrays.asList(registrations))
            .build();
    }

    private static MapReference registration(MapReferenceId id, long sequence, boolean active) {
        return MapReference.of(id, sequence, URI.create("maps/" + id.value() + ".mm"), active, "#4E79A7",
            Collections.<UnknownXml>emptyList());
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root) {
        return MapSnapshot.of(id, workspaceOrder, "Map " + id.value(), root,
            Collections.<PersistedNodeId>emptySet(), false);
    }

    private static NodeSnapshot markedNode(MapReferenceId map, String id, String label) {
        return NodeSnapshot.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))),
            SafeNodeLabel.of(label, label),
            true, true, false, Collections.<NodeSnapshot>emptyList()
        );
    }

    private static NodeSnapshot plainNode(MapReferenceId map, String id, String label, NodeSnapshot... children) {
        return NodeSnapshot.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))),
            SafeNodeLabel.of(label, label),
            children.length == 0, false, false, Arrays.asList(children)
        );
    }

    private static GraphProjection project(WorkspaceDocument workspace, MapSnapshot... maps) {
        return new ProjectionEngine().projectStructure(1, workspace, Arrays.asList(maps));
    }
}
