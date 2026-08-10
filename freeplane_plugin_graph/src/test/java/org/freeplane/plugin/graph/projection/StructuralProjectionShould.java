package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
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

public class StructuralProjectionShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_THREE = MapReferenceId.of("00000000-0000-0000-0000-000000000003");

    @Test
    public void projectGroupsBeforeStructuralLeaves() {
        NodeSnapshot group = node(MAP_ONE, "group", "Group", true, true, false);
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group, leaf);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).hasSize(2);
        assertThat(projection.nodes().get(0).source()).isEqualTo(group.key());
        assertThat(projection.nodes().get(0).graphGroup()).isTrue();
        assertThat(projection.nodes().get(1).source()).isEqualTo(leaf.key());
        assertThat(projection.nodes().get(1).graphGroup()).isFalse();
        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projection.enclosures().get(0).endpointKeys())
            .containsExactly(EnclosureKey.of(root.key()));
        assertThat(projection.enclosures().get(0).directNodes()).containsExactly(
            ProjectedNodeKey.of(group.key()), ProjectedNodeKey.of(leaf.key()));
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.edges()).isEmpty();
        assertThat(projection.relationshipResolutions()).isEmpty();
        assertThat(projection.pins()).isEmpty();
        assertThat(projection.projectedNodeCount()).isEqualTo(2);
        assertThat(projection.projectedEdgeCount()).isZero();
    }

    @Test
    public void suppressOuterGroupsBeforeNestedGroupsAndReactivateThemWhenRemoved() {
        NodeSnapshot innerGroup = node(MAP_ONE, "inner", "INNER_SECRET", true, true, false);
        NodeSnapshot secretLeaf = node(MAP_ONE, "secret", "LEAF_SECRET", true, false, false);
        NodeSnapshot groupedOuter = node(MAP_ONE, "outer", "Outer", false, true, false, innerGroup, secretLeaf);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, groupedOuter);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));

        GraphProjection suppressed = project(workspace, map(MAP_ONE, 1, root));

        assertThat(suppressed.nodes()).hasSize(1);
        assertThat(suppressed.nodes().get(0).source()).isEqualTo(groupedOuter.key());
        assertThat(suppressed.nodes().get(0).graphGroup()).isTrue();
        assertThat(projectedLabels(suppressed)).doesNotContain("INNER_SECRET", "LEAF_SECRET");

        NodeSnapshot ordinaryOuter = node(MAP_ONE, "outer", "Outer", false, false, false, innerGroup, secretLeaf);
        NodeSnapshot ordinaryRoot = node(MAP_ONE, "root", "Root", false, false, false, ordinaryOuter);
        GraphProjection reactivated = project(workspace, map(MAP_ONE, 1, ordinaryRoot));

        assertThat(reactivated.nodes()).extracting(ProjectedNode::source)
            .containsExactly(innerGroup.key(), secretLeaf.key());
        assertThat(reactivated.nodes().get(0).graphGroup()).isTrue();
        assertThat(projectedLabels(reactivated)).contains("INNER_SECRET", "LEAF_SECRET");
    }

    @Test
    public void excludedSubtreesNeverProjectLabels() {
        NodeSnapshot visibleLeaf = node(MAP_ONE, "visible", "Visible", true, false, false);
        NodeSnapshot excludedLeaf = node(MAP_ONE, "hidden-leaf", "SECRET_DESCENDANT", true, false, false);
        NodeSnapshot excludedBranch = node(MAP_ONE, "hidden-branch", "SECRET_ENCLOSURE", false, false, true,
            excludedLeaf);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, visibleLeaf, excludedBranch);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).extracting(ProjectedNode::source).containsExactly(visibleLeaf.key());
        assertThat(projectedLabels(projection)).doesNotContain("SECRET_ENCLOSURE", "SECRET_DESCENDANT");
    }

    @Test
    public void projectLockedLeavesAndVisibleSummaryAndFreeNodesNormally() {
        NodeSnapshot visibleSummary = node(MAP_ONE, "summary", "Visible summary", true, false, false);
        NodeSnapshot freeNode = node(MAP_ONE, "free", "Free node", true, false, false);
        NodeSnapshot lockedLeaf = node(MAP_ONE, "locked", "Locked leaf", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false,
            visibleSummary, freeNode, lockedLeaf);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root, true));

        assertThat(projection.nodes()).extracting(ProjectedNode::source)
            .containsExactly(visibleSummary.key(), freeNode.key(), lockedLeaf.key());
        assertThat(projection.nodes()).allMatch(node -> !node.graphGroup());
    }

    @Test
    public void compressUnaryEnclosuresIntoOneMapRootHull() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot middle = node(MAP_ONE, "middle", "Middle", false, false, false, leaf);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, middle);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(1);
        ProjectedEnclosure hull = projection.enclosures().get(0);
        assertThat(hull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()), EnclosureKey.of(middle.key()));
        assertThat(hull.labels()).containsExactly(root.label(), middle.label());
        assertThat(hull.directNodes()).containsExactly(ProjectedNodeKey.of(leaf.key()));
        assertThat(hull.directEnclosures()).isEmpty();
        assertThat(hull.parentHull()).isEmpty();
        assertThat(hull.mapRoot()).isTrue();
    }

    @Test
    public void keepBranchingAndEmptyEnclosuresAsSeparateHulls() {
        NodeSnapshot excludedLeaf = node(MAP_ONE, "excluded", "Excluded", true, false, true);
        NodeSnapshot empty = node(MAP_ONE, "empty", "Visible parent", false, false, false, excludedLeaf);
        NodeSnapshot sibling = node(MAP_ONE, "sibling", "Sibling", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, empty, sibling);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(2);
        ProjectedEnclosure rootHull = projection.enclosures().get(0);
        ProjectedEnclosure emptyHull = projection.enclosures().get(1);
        assertThat(rootHull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootHull.directNodes()).containsExactly(ProjectedNodeKey.of(sibling.key()));
        assertThat(rootHull.directEnclosures()).containsExactly(emptyHull.hullKey());
        assertThat(emptyHull.endpointKeys()).containsExactly(EnclosureKey.of(empty.key()));
        assertThat(emptyHull.directNodes()).isEmpty();
        assertThat(emptyHull.directEnclosures()).isEmpty();
        assertThat(emptyHull.parentHull()).contains(rootHull.hullKey());
    }

    @Test
    public void retainAnEmptyVisibleParentBelowAUnaryMapRoot() {
        NodeSnapshot hiddenChild = node(MAP_ONE, "hidden", "Hidden", true, false, true);
        NodeSnapshot visibleParent = node(MAP_ONE, "parent", "Visible parent", false, false, false, hiddenChild);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, visibleParent);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(2);
        ProjectedEnclosure rootHull = projection.enclosures().get(0);
        ProjectedEnclosure parentHull = projection.enclosures().get(1);
        assertThat(rootHull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootHull.directEnclosures()).containsExactly(parentHull.hullKey());
        assertThat(parentHull.endpointKeys()).containsExactly(EnclosureKey.of(visibleParent.key()));
        assertThat(parentHull.labels()).containsExactly(visibleParent.label());
        assertThat(parentHull.directNodes()).isEmpty();
        assertThat(parentHull.directEnclosures()).isEmpty();
    }

    @Test
    public void useDistinctPersistentKeysForClonedLabels() {
        NodeSnapshot firstClone = node(MAP_ONE, "clone-one", "Clone", true, false, false);
        NodeSnapshot secondClone = node(MAP_ONE, "clone-two", "Clone", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, firstClone, secondClone);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).extracting(ProjectedNode::label)
            .containsExactly(firstClone.label(), secondClone.label());
        assertThat(projection.nodes()).extracting(ProjectedNode::key)
            .containsExactly(ProjectedNodeKey.of(firstClone.key()), ProjectedNodeKey.of(secondClone.key()));
        assertThat(projection.nodes().get(0).key()).isNotEqualTo(projection.nodes().get(1).key());
    }

    @Test
    public void orderSelectedMapsByWorkspaceOrderIndependentlyOfInputIteration() {
        NodeSnapshot firstRoot = node(MAP_ONE, "one-root", "One", true, false, false);
        NodeSnapshot secondRoot = node(MAP_TWO, "two-root", "Two", true, false, false);
        MapSnapshot firstMap = map(MAP_ONE, 2, firstRoot);
        MapSnapshot secondMap = map(MAP_TWO, 1, secondRoot);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, true));

        GraphProjection first = project(workspace, firstMap, secondMap);
        GraphProjection second = project(workspace, secondMap, firstMap);

        assertThat(first).isEqualTo(second);
        assertThat(first.nodes()).extracting(ProjectedNode::source)
            .containsExactly(secondRoot.key(), firstRoot.key());
    }

    @Test
    public void ignoreInactiveSnapshotsAndRejectUnregisteredDuplicateOrConflictingSnapshots() {
        NodeSnapshot activeRoot = node(MAP_ONE, "active", "Active", true, false, false);
        NodeSnapshot inactiveRoot = node(MAP_TWO, "inactive", "Inactive", true, false, false);
        MapSnapshot activeMap = map(MAP_ONE, 1, activeRoot);
        MapSnapshot inactiveMap = map(MAP_TWO, 2, inactiveRoot);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, false));

        GraphProjection projection = project(workspace, activeMap, inactiveMap);

        assertThat(projection.nodes()).extracting(ProjectedNode::source).containsExactly(activeRoot.key());
        MapSnapshot unregistered = map(MAP_THREE, 3, node(MAP_THREE, "other", "Other", true, false, false));
        assertThatThrownBy(() -> project(workspace, activeMap, unregistered))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> project(workspace, activeMap, activeMap))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> project(workspace, activeMap,
            map(MAP_TWO, 1, inactiveRoot)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void copyInputCollectionsAndExposeImmutableOrderedValues() {
        List<Integer> path = new ArrayList<Integer>(Arrays.asList(0, 1));
        SourceNodeKey transientKey = SourceNodeKey.transientPath(MAP_ONE, path);
        path.clear();
        assertThat(transientKey.structuralPath()).containsExactly(0, 1);
        assertThatThrownBy(() -> transientKey.structuralPath().add(2))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> SourceNodeKey.transientPath(MAP_ONE, Arrays.asList(0, -1)))
            .isInstanceOf(IllegalArgumentException.class);

        Set<PersistedNodeId> attached = new HashSet<PersistedNodeId>(Arrays.asList(
            PersistedNodeId.of("z"), PersistedNodeId.of("a")));
        NodeSnapshot root = node(MAP_ONE, "root", "Root", true, false, false);
        MapSnapshot snapshot = MapSnapshot.of(MAP_ONE, 1, "Map", root, attached, false);
        attached.clear();
        assertThat(snapshot.attachedPersistentIds()).extracting(PersistedNodeId::value).containsExactly("a", "z");
        assertThatThrownBy(() -> snapshot.attachedPersistentIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), snapshot);
        assertThatThrownBy(() -> projection.nodes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(SafeNodeLabel.of("full", "display")).isEqualTo(SafeNodeLabel.of("full", "display"));
    }

    private static GraphProjection project(WorkspaceDocument workspace, MapSnapshot... maps) {
        return new ProjectionEngine().projectStructure(3, workspace, Arrays.asList(maps));
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
        return map(id, workspaceOrder, root, false);
    }

    private static MapSnapshot map(MapReferenceId id, int workspaceOrder, NodeSnapshot root,
            boolean hasInaccessibleBranch) {
        return MapSnapshot.of(id, workspaceOrder, "Map " + id.value(), root,
            Collections.<PersistedNodeId>emptySet(), hasInaccessibleBranch);
    }

    private static NodeSnapshot node(MapReferenceId map, String id, String label, boolean structuralLeaf,
            boolean graphGroup, boolean excluded, NodeSnapshot... children) {
        return NodeSnapshot.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))),
            SafeNodeLabel.of(label, label), structuralLeaf, graphGroup, excluded, Arrays.asList(children));
    }

    private static List<String> projectedLabels(GraphProjection projection) {
        List<String> labels = new ArrayList<String>();
        for (ProjectedNode node : projection.nodes()) {
            labels.add(node.label().fullText());
            labels.add(node.label().displayText());
        }
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            for (SafeNodeLabel label : enclosure.labels()) {
                labels.add(label.fullText());
                labels.add(label.displayText());
            }
        }
        return labels;
    }
}
