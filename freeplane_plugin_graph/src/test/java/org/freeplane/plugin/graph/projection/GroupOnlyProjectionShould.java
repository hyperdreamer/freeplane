package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class GroupOnlyProjectionShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    @Test
    public void projectOnlyGroupMarkedNodesAsBoundaries() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot plainBranch = node(MAP_ONE, "branch", "Branch", false, false, false, leaf);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false,
            node(MAP_ONE, "inner", "Inner", true, false, false));
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, plainBranch, group);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(0).endpointKeys())
            .containsExactly(EnclosureKey.of(root.key()));
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.enclosures().get(0).directNodes()).isEmpty();
        assertThat(projection.enclosures().get(0).directEnclosures())
            .containsExactly(EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(group.key()))));
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(group.key()));
        assertThat(projection.enclosures().get(1).directEnclosures()).isEmpty();
        assertThat(projectedLabelTexts(projection)).containsExactly("Root", "Group");
    }

    @Test
    public void hoistGroupMarkedDescendantsThroughPlainContainers() {
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false);
        NodeSnapshot plainBranch = node(MAP_ONE, "branch", "Branch", false, false, false, group);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, plainBranch);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(0).directEnclosures())
            .containsExactly(EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(group.key()))));
        assertThat(projection.enclosures().get(1).parentHull().get())
            .isEqualTo(projection.enclosures().get(0).hullKey());
    }

    @Test
    public void nestGroupMarkedDescendantsInsideTheirGroupBoundary() {
        NodeSnapshot innerGroup = node(MAP_ONE, "inner", "Inner", false, true, false);
        NodeSnapshot outerGroup = node(MAP_ONE, "outer", "Outer", false, true, false,
            node(MAP_ONE, "plain", "Plain", true, false, false), innerGroup);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, outerGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(3);
        assertThat(projection.enclosures().get(2).endpointKeys())
            .containsExactly(EnclosureKey.of(innerGroup.key()));
        assertThat(projection.enclosures().get(2).parentHull().get()).isEqualTo(
            EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(outerGroup.key()))));
    }

    @Test
    public void collapseUnaryGroupChainsIntoOneBoundary() {
        NodeSnapshot leafGroup = node(MAP_ONE, "leaf-group", "Leaf group", false, true, false);
        NodeSnapshot middleGroup = node(MAP_ONE, "middle", "Middle", false, true, false, leafGroup);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, middleGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(middleGroup.key()), EnclosureKey.of(leafGroup.key()));
    }

    @Test
    public void mapWithoutGroupsProjectsOnlyItsFrame() {
        NodeSnapshot leaf = node(MAP_ONE, "leaf", "Leaf", true, false, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, leaf);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
        assertThat(projection.enclosures().get(0).directEnclosures()).isEmpty();
    }

    @Test
    public void excludedGroupsStayHidden() {
        NodeSnapshot hiddenGroup = node(MAP_ONE, "hidden-group", "SECRET_GROUP", false, true, true);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, hiddenGroup);

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1, true)),
            map(MAP_ONE, 1, root));

        assertThat(projection.enclosures()).hasSize(1);
        assertThat(projectedLabelTexts(projection)).doesNotContain("SECRET_GROUP");
    }

    @Test
    public void togglingTheMarkerAddsAndRemovesTheBoundary() {
        NodeSnapshot inner = node(MAP_ONE, "inner", "Inner", true, false, false);
        NodeSnapshot marked = node(MAP_ONE, "topic", "Topic", false, true, false, inner);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, marked);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1, true));

        GraphProjection markedProjection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(markedProjection.enclosures()).hasSize(2);

        NodeSnapshot unmarked = node(MAP_ONE, "topic", "Topic", false, false, false, inner);
        NodeSnapshot unmarkedRoot = node(MAP_ONE, "root", "Root", false, false, false, unmarked);
        GraphProjection unmarkedProjection = project(workspace, map(MAP_ONE, 1, unmarkedRoot));

        assertThat(unmarkedProjection.enclosures()).hasSize(1);
        assertThat(unmarkedProjection.enclosures().get(0).mapRoot()).isTrue();
    }

    @Test
    public void pinsActivateOnlyForRootsAndGroupMarkedNodes() {
        NodeSnapshot leaf = node(MAP_ONE, "a-leaf", "Leaf", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, leaf, group);
        WorkspaceDocument workspace = workspace(Collections.singletonList(registration(MAP_ONE, 1, true)),
            Arrays.asList(pin(key(MAP_ONE, "a-leaf"), 1.0, 2.0), pin(key(MAP_ONE, "group"), 3.0, 4.0),
                pin(key(MAP_ONE, "root"), 5.0, 6.0)));

        GraphProjection projection = project(workspace, map(MAP_ONE, 1, root));

        assertThat(projection.pins()).hasSize(3);
        assertThat(projection.pins().get(0).active()).isFalse();
        assertThat(projection.pins().get(1).active()).isTrue();
        assertThat(projection.pins().get(2).active()).isTrue();
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

    private static WorkspaceDocument workspace(List<MapReference> registrations, List<PinRecord> pins) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000010"))
            .toBuilder()
            .maps(registrations)
            .pins(pins)
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

    private static NodeSnapshot node(MapReferenceId map, String id, String label, boolean structuralLeaf,
            boolean graphGroup, boolean excluded, NodeSnapshot... children) {
        return NodeSnapshot.of(key(map, id), SafeNodeLabel.of(label, label), structuralLeaf, graphGroup,
            excluded, Arrays.asList(children));
    }

    private static SourceNodeKey key(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    private static PinRecord pin(SourceNodeKey key, double x, double y) {
        return PinRecord.of(key.persistedReference().get(), x, y, Collections.<UnknownXml>emptyList());
    }

    private static List<String> projectedLabelTexts(GraphProjection projection) {
        List<String> labels = new ArrayList<String>();
        for (ProjectedNode node : projection.nodes()) {
            labels.add(node.label().displayText());
        }
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            for (SafeNodeLabel label : enclosure.labels()) {
                labels.add(label.displayText());
            }
        }
        return labels;
    }
}
