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
    public void projectGroupMarkedLeavesAsBoundariesBeforePlainLeavesVanish() {
        NodeSnapshot group = node(MAP_ONE, "group", "Group", true, true, false);
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group, leaf);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(0).endpointKeys())
            .containsExactly(EnclosureKey.of(root.key()));
        assertThat(projection.enclosures().get(0).directNodes()).isEmpty();
        assertThat(projection.enclosures().get(0).directEnclosures())
            .containsExactly(EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(group.key()))));
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(group.key()));
        assertThat(projection.enclosures().get(1).mapRoot()).isFalse();
        assertThat(projectedLabels(projection)).doesNotContain("Leaf");
        assertThat(projection.edges()).isEmpty();
        assertThat(projection.relationshipResolutions()).isEmpty();
        assertThat(projection.pins()).isEmpty();
        assertThat(projection.projectedNodeCount()).isEqualTo(1);
        assertThat(projection.projectedEdgeCount()).isZero();
    }

    @Test
    public void countGroupMarkedBoundariesAsNodesButNeverMapRootFrames() {
        NodeSnapshot group = node(MAP_ONE, "group", "Group", true, true, false);
        NodeSnapshot plain = node(MAP_ONE, "plain", "Plain", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group, plain);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.projectedNodeCount()).isEqualTo(1);
        assertThat(projection.enclosures().get(1).mapRoot()).isFalse();

        NodeSnapshot rootOnly = node(MAP_ONE, "root", "Root", false, false, false, plain);
        GraphProjection noGroups = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, rootOnly));
        assertThat(noGroups.projectedNodeCount()).isZero();
    }

    @Test
    public void nestNestedGroupsAndReactivateThemWhenTheOuterGroupIsRemoved() {
        NodeSnapshot innerGroup = node(MAP_ONE, "inner", "INNER_SECRET", true, true, false);
        NodeSnapshot secretLeaf = node(MAP_ONE, "secret", "LEAF_SECRET", true, false, false);
        NodeSnapshot groupedOuter = node(MAP_ONE, "outer", "Outer", false, true, false, innerGroup, secretLeaf);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, groupedOuter);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));

        GraphProjection nested = project(workspace, map(MAP_ONE, 1, root));

        assertThat(nested.nodes()).isEmpty();
        assertThat(nested.enclosures()).hasSize(3);
        assertThat(nested.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(groupedOuter.key()));
        assertThat(nested.enclosures().get(2).endpointKeys())
            .containsExactly(EnclosureKey.of(innerGroup.key()));
        assertThat(nested.enclosures().get(2).parentHull().get())
            .isEqualTo(nested.enclosures().get(1).hullKey());
        assertThat(projectedLabels(nested)).contains("INNER_SECRET").doesNotContain("LEAF_SECRET");

        NodeSnapshot ordinaryOuter = node(MAP_ONE, "outer", "Outer", false, false, false, innerGroup, secretLeaf);
        NodeSnapshot ordinaryRoot = node(MAP_ONE, "root", "Root", false, false, false, ordinaryOuter);
        GraphProjection reactivated = project(workspace, map(MAP_ONE, 1, ordinaryRoot));

        assertThat(reactivated.enclosures()).hasSize(2);
        assertThat(reactivated.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(innerGroup.key()));
        assertThat(reactivated.enclosures().get(1).parentHull().get())
            .isEqualTo(reactivated.enclosures().get(0).hullKey());
        assertThat(projectedLabels(reactivated)).contains("INNER_SECRET").doesNotContain("LEAF_SECRET");
    }

    @Test
    public void excludedSubtreesNeverProjectLabels() {
        NodeSnapshot visibleLeaf = node(MAP_ONE, "visible", "Visible", true, false, false);
        NodeSnapshot excludedLeaf = node(MAP_ONE, "hidden-leaf", "SECRET_DESCENDANT", true, false, false);
        NodeSnapshot excludedBranch = node(MAP_ONE, "hidden-branch", "SECRET_ENCLOSURE", false, false, true,
            excludedLeaf);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, visibleLeaf, excludedBranch);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projection.enclosures().get(0).endpointKeys())
            .containsExactly(EnclosureKey.of(root.key()));
        assertThat(projectedLabels(projection)).doesNotContain("SECRET_ENCLOSURE", "SECRET_DESCENDANT");
    }

    @Test
    public void projectLockedLeavesAndVisibleSummaryAndFreeNodesAsBoundaries() {
        NodeSnapshot visibleSummary = node(MAP_ONE, "summary", "Visible summary", true, true, false);
        NodeSnapshot freeNode = node(MAP_ONE, "free", "Free node", true, true, false);
        NodeSnapshot lockedLeaf = node(MAP_ONE, "locked", "Locked leaf", true, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false,
            visibleSummary, freeNode, lockedLeaf);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root, true));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(4);
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(visibleSummary.key()));
        assertThat(projection.enclosures().get(2).endpointKeys())
            .containsExactly(EnclosureKey.of(freeNode.key()));
        assertThat(projection.enclosures().get(3).endpointKeys())
            .containsExactly(EnclosureKey.of(lockedLeaf.key()));
        assertThat(projection.enclosures().get(1).mapRoot()).isFalse();
        assertThat(projection.enclosures().get(2).mapRoot()).isFalse();
        assertThat(projection.enclosures().get(3).mapRoot()).isFalse();
    }

    @Test
    public void keepTheMapRootFrameAloneAndChainUnaryGroups() {
        NodeSnapshot leafGroup = node(MAP_ONE, "leaf-group", "Leaf group", true, true, false);
        NodeSnapshot middleGroup = node(MAP_ONE, "middle", "Middle", false, true, false, leafGroup);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, middleGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(2);
        ProjectedEnclosure rootHull = projection.enclosures().get(0);
        ProjectedEnclosure chainedHull = projection.enclosures().get(1);
        assertThat(rootHull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootHull.directNodes()).isEmpty();
        assertThat(rootHull.directEnclosures()).containsExactly(chainedHull.hullKey());
        assertThat(rootHull.parentHull()).isEmpty();
        assertThat(rootHull.mapRoot()).isTrue();
        assertThat(chainedHull.endpointKeys()).containsExactly(EnclosureKey.of(middleGroup.key()),
            EnclosureKey.of(leafGroup.key()));
        assertThat(chainedHull.labels()).containsExactly(middleGroup.label(), leafGroup.label());
        assertThat(chainedHull.directNodes()).isEmpty();
        assertThat(chainedHull.directEnclosures()).isEmpty();
        assertThat(chainedHull.parentHull()).contains(rootHull.hullKey());
    }

    @Test
    public void keepBranchingAndEmptyGroupsAsSeparateHulls() {
        NodeSnapshot innerOne = node(MAP_ONE, "inner-one", "Inner one", false, true, false);
        NodeSnapshot innerTwo = node(MAP_ONE, "inner-two", "Inner two", false, true, false);
        NodeSnapshot outer = node(MAP_ONE, "outer", "Outer", false, true, false, innerOne, innerTwo);
        NodeSnapshot emptyGroup = node(MAP_ONE, "empty-group", "Empty group", false, true, false,
            node(MAP_ONE, "plain", "Plain", true, false, false));
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, outer, emptyGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(5);
        ProjectedEnclosure rootHull = projection.enclosures().get(0);
        ProjectedEnclosure outerHull = projection.enclosures().get(1);
        ProjectedEnclosure innerOneHull = projection.enclosures().get(2);
        ProjectedEnclosure innerTwoHull = projection.enclosures().get(3);
        ProjectedEnclosure emptyHull = projection.enclosures().get(4);
        assertThat(rootHull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootHull.directNodes()).isEmpty();
        assertThat(rootHull.directEnclosures()).containsExactly(outerHull.hullKey(), emptyHull.hullKey());
        assertThat(outerHull.endpointKeys()).containsExactly(EnclosureKey.of(outer.key()));
        assertThat(outerHull.directNodes()).isEmpty();
        assertThat(outerHull.directEnclosures()).containsExactly(innerOneHull.hullKey(), innerTwoHull.hullKey());
        assertThat(innerOneHull.parentHull()).contains(outerHull.hullKey());
        assertThat(innerTwoHull.parentHull()).contains(outerHull.hullKey());
        assertThat(emptyHull.endpointKeys()).containsExactly(EnclosureKey.of(emptyGroup.key()));
        assertThat(emptyHull.directNodes()).isEmpty();
        assertThat(emptyHull.directEnclosures()).isEmpty();
        assertThat(emptyHull.parentHull()).contains(rootHull.hullKey());
    }

    @Test
    public void retainAnEmptyGroupBoundaryBelowTheMapRootFrame() {
        NodeSnapshot hiddenChild = node(MAP_ONE, "hidden", "Hidden", true, false, true);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false, hiddenChild);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, group);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(2);
        ProjectedEnclosure rootHull = projection.enclosures().get(0);
        ProjectedEnclosure groupHull = projection.enclosures().get(1);
        assertThat(rootHull.endpointKeys()).containsExactly(EnclosureKey.of(root.key()));
        assertThat(rootHull.directEnclosures()).containsExactly(groupHull.hullKey());
        assertThat(groupHull.endpointKeys()).containsExactly(EnclosureKey.of(group.key()));
        assertThat(groupHull.labels()).containsExactly(group.label());
        assertThat(groupHull.directNodes()).isEmpty();
        assertThat(groupHull.directEnclosures()).isEmpty();
    }

    @Test
    public void useDistinctPersistentKeysForClonedLabels() {
        NodeSnapshot firstClone = node(MAP_ONE, "clone-one", "Clone", true, true, false);
        NodeSnapshot secondClone = node(MAP_ONE, "clone-two", "Clone", true, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, firstClone, secondClone);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)), map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(3);
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(firstClone.key()));
        assertThat(projection.enclosures().get(2).endpointKeys())
            .containsExactly(EnclosureKey.of(secondClone.key()));
        assertThat(projection.enclosures().get(1).labels()).containsExactly(firstClone.label());
        assertThat(projection.enclosures().get(2).labels()).containsExactly(secondClone.label());
        assertThat(projection.enclosures().get(1).hullKey()).isNotEqualTo(projection.enclosures().get(2).hullKey());
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
        assertThat(first.enclosures()).extracting(ProjectedEnclosure::mapReferenceId)
            .containsExactly(MAP_TWO, MAP_ONE);
    }

    @Test
    public void ignoreInactiveSnapshotsAndRejectUnregisteredDuplicateOrConflictingSnapshots() {
        NodeSnapshot activeRoot = node(MAP_ONE, "active", "Active", true, false, false);
        NodeSnapshot inactiveRoot = node(MAP_TWO, "inactive", "Inactive", true, false, false);
        MapSnapshot activeMap = map(MAP_ONE, 1, activeRoot);
        MapSnapshot inactiveMap = map(MAP_TWO, 2, inactiveRoot);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true), registration(MAP_TWO, 2, false));

        GraphProjection projection = project(workspace, activeMap, inactiveMap);

        assertThat(projection.enclosures()).extracting(ProjectedEnclosure::mapReferenceId)
            .containsExactly(MAP_ONE);
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
