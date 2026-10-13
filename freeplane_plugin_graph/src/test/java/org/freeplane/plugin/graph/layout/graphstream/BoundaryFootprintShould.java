package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class BoundaryFootprintShould {
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    private static ProjectedNodeKey nodeKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }

    private static ProjectedNode node(String id, String label) {
        return ProjectedNode.of(nodeKey(id), SafeNodeLabel.of(label, label), "M", true);
    }

    private static ProjectedEnclosure enclosure(String hullId, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> directNodes,
            List<EnclosureHullKey> directEnclosures, boolean mapRoot, BoundaryTier tier) {
        return ProjectedEnclosure.of(hull(hullId), Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(hullId))))),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "M", parent, directNodes,
            directEnclosures, mapRoot, tier);
    }

    private static GraphProjection projection(List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures) {
        return GraphProjection.projected(1L, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    @Test
    public void leafFootprintIsSquareAroundTheAnchor() {
        EnclosureHullKey leaf = hull("leaf");
        GraphProjection projection = projection(Arrays.asList(node("p1", "First"), node("p2", "Second")),
            Collections.singletonList(enclosure("leaf", "Leaf", Optional.<EnclosureHullKey>empty(),
                Arrays.asList(nodeKey("p1"), nodeKey("p2")), Collections.<EnclosureHullKey>emptyList(),
                true, BoundaryTier.EMPHATIC)));
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(projection);
        assertThat(sizes.discRadius(nodeKey("p1"))).isEqualTo(8.0);
        assertThat(sizes.discRadius(nodeKey("p2"))).isEqualTo(8.0);
        assertThat(sizes.contentRingRadius(leaf)).isEqualTo(11.0);
        GraphStreamLayoutEngine.BoundarySizes.Footprint footprint = sizes.footprint(leaf);
        double expected = 2.0 * (sizes.contentRingRadius(leaf) + 8.0
            + GraphStreamLayoutEngine.BoundarySizes.NODE_SETTLE_MARGIN
            + GraphStreamLayoutEngine.BoundarySizes.FRAME_CLEARANCE);
        assertThat(footprint.width).isEqualTo(expected);
        assertThat(footprint.height).isEqualTo(expected);
        assertThat(footprint.width).isEqualTo(86.0);
        assertThat(footprint.height).isEqualTo(86.0);
        assertThat(footprint.anchorX).isEqualTo(0.0);
        assertThat(footprint.anchorY).isEqualTo(0.0);
    }

    @Test
    public void singleDirectNodeFootprint() {
        EnclosureHullKey leaf = hull("leaf");
        GraphProjection projection = projection(Collections.singletonList(node("p1", "First")),
            Collections.singletonList(enclosure("leaf", "Leaf", Optional.<EnclosureHullKey>empty(),
                Collections.singletonList(nodeKey("p1")), Collections.<EnclosureHullKey>emptyList(),
                true, BoundaryTier.EMPHATIC)));
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(projection);
        assertThat(sizes.contentRingRadius(leaf)).isEqualTo(0.0);
        GraphStreamLayoutEngine.BoundarySizes.Footprint footprint = sizes.footprint(leaf);
        assertThat(footprint.width).isEqualTo(2.0 * (8.0 + 8.0 + 16.0));
        assertThat(footprint.height).isEqualTo(2.0 * (8.0 + 8.0 + 16.0));
        assertThat(footprint.width).isEqualTo(64.0);
        assertThat(footprint.height).isEqualTo(64.0);
        assertThat(footprint.anchorX).isEqualTo(0.0);
        assertThat(footprint.anchorY).isEqualTo(0.0);
    }

    @Test
    public void nestedFootprintIsThePackedChildBoxPlusFrameClearance() {
        EnclosureHullKey parent = hull("parent");
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure("parent", "Parent", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(hull("wide"), hull("narrow")), true,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure("wide", "aaaaaaaa", Optional.of(parent),
            Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(enclosure("narrow", "bbbb", Optional.of(parent),
            Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(
            projection(Collections.<ProjectedNode>emptyList(), enclosures));
        assertThat(sizes.footprint(hull("wide")).width).isEqualTo(144.0);
        assertThat(sizes.footprint(hull("wide")).height).isEqualTo(40.0);
        assertThat(sizes.footprint(hull("narrow")).width).isEqualTo(80.0);
        assertThat(sizes.footprint(hull("narrow")).height).isEqualTo(40.0);

        List<ClusterPacking.ClusterPlacement> placements = sizes.placements(parent);
        assertThat(placements).hasSize(2);
        ClusterPacking.ClusterPlacement wide = sizes.placementOf(parent, hull("wide"));
        ClusterPacking.ClusterPlacement narrow = sizes.placementOf(parent, hull("narrow"));
        assertThat(wide).isNotNull();
        assertThat(narrow).isNotNull();
        assertThat(wide.x).isEqualTo(0.0);
        assertThat(wide.y).isEqualTo(0.0);
        assertThat(narrow.x).isEqualTo(-120.0);
        assertThat(narrow.y).isEqualTo(0.0);

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (ClusterPacking.ClusterPlacement placement : placements) {
            minX = Math.min(minX, placement.x - placement.item.width * 0.5);
            maxX = Math.max(maxX, placement.x + placement.item.width * 0.5);
            minY = Math.min(minY, placement.y - placement.item.height * 0.5);
            maxY = Math.max(maxY, placement.y + placement.item.height * 0.5);
        }
        GraphStreamLayoutEngine.BoundarySizes.Footprint footprint = sizes.footprint(parent);
        assertThat(footprint.width).isEqualTo(264.0);
        assertThat(footprint.height).isEqualTo(72.0);
        assertThat(footprint.anchorX).isEqualTo(44.0);
        assertThat(footprint.anchorY).isEqualTo(0.0);
        assertThat(footprint.width).isEqualTo(maxX - minX + 2.0
            * GraphStreamLayoutEngine.BoundarySizes.FRAME_CLEARANCE);
        assertThat(footprint.height).isEqualTo(maxY - minY + 2.0
            * GraphStreamLayoutEngine.BoundarySizes.FRAME_CLEARANCE);
        assertThat(footprint.anchorX).isEqualTo(-(minX + maxX) * 0.5);
        assertThat(footprint.anchorY).isEqualTo(-(minY + maxY) * 0.5);
    }

    @Test
    public void emptyEnclosureUsesTheLabelSizedBox() {
        EnclosureHullKey empty = hull("empty");
        GraphProjection projection = projection(Collections.<ProjectedNode>emptyList(),
            Collections.singletonList(enclosure("empty", "wide", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(),
                true, BoundaryTier.SUBTLE)));
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(projection);
        GraphStreamLayoutEngine.BoundarySizes.Footprint footprint = sizes.footprint(empty);
        assertThat(footprint.width).isEqualTo(80.0);
        assertThat(footprint.height).isEqualTo(40.0);
        assertThat(footprint.anchorX).isEqualTo(0.0);
        assertThat(footprint.anchorY).isEqualTo(0.0);
        assertThat(sizes.placements(empty)).isEmpty();
    }

    @Test
    public void singleChildIsPlacedAtTheAnchorOrigin() {
        EnclosureHullKey parent = hull("parent");
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure("parent", "Parent", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(hull("child")), true,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure("child", "child", Optional.of(parent),
            Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(
            projection(Collections.<ProjectedNode>emptyList(), enclosures));
        assertThat(sizes.footprint(hull("child")).width).isEqualTo(96.0);
        assertThat(sizes.footprint(hull("child")).height).isEqualTo(40.0);
        List<ClusterPacking.ClusterPlacement> placements = sizes.placements(parent);
        assertThat(placements).hasSize(1);
        assertThat(placements.get(0).x).isEqualTo(0.0);
        assertThat(placements.get(0).y).isEqualTo(0.0);
        GraphStreamLayoutEngine.BoundarySizes.Footprint footprint = sizes.footprint(parent);
        assertThat(footprint.width).isEqualTo(128.0);
        assertThat(footprint.height).isEqualTo(72.0);
        assertThat(footprint.anchorX).isEqualTo(0.0);
        assertThat(footprint.anchorY).isEqualTo(0.0);
    }

    @Test
    public void topLevelMembershipMatchesSeedsPredicate() {
        EnclosureHullKey root = hull("root");
        EnclosureHullKey nested = hull("nested");
        EnclosureHullKey parentless = hull("parentless");
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure("root", "Root", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(nested), true,
            BoundaryTier.SUPPRESSED));
        enclosures.add(enclosure("nested", "Nested", Optional.of(root),
            Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(enclosure("parentless", "Other", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        GraphStreamLayoutEngine.BoundarySizes sizes =
            new GraphStreamLayoutEngine.BoundarySizes(projection(
                Collections.<ProjectedNode>emptyList(), enclosures));

        List<ClusterPacking.ClusterPlacement> topLevel = sizes.topLevelPlacements();
        List<EnclosureHullKey> anchors = new ArrayList<EnclosureHullKey>();
        for (ClusterPacking.ClusterPlacement placement : topLevel) {
            anchors.add(placement.item.anchorKey);
        }
        assertThat(anchors).containsExactlyInAnyOrder(root, parentless);
        assertThat(sizes.topLevelPlacementOf(nested)).isNull();
        assertThat(sizes.topLevelPlacementOf(root)).isNotNull();
        assertThat(sizes.topLevelPlacementOf(parentless)).isNotNull();
    }

    @Test
    public void footprintsAreMemoisedAndRecursionTerminates() {
        EnclosureHullKey root = hull("root");
        EnclosureHullKey first = hull("first");
        EnclosureHullKey second = hull("second");
        EnclosureHullKey grandFirst = hull("grandFirst");
        EnclosureHullKey grandSecond = hull("grandSecond");
        EnclosureHullKey grandThird = hull("grandThird");
        EnclosureHullKey grandFourth = hull("grandFourth");
        List<ProjectedNode> nodes = Arrays.asList(node("n1", "One"), node("n2", "Two"),
            node("n3", "Three"), node("n4", "Four"));
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure("root", "Root", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(first, second), true,
            BoundaryTier.SUPPRESSED));
        enclosures.add(enclosure("first", "First", Optional.of(root),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(grandFirst, grandSecond), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure("second", "Second", Optional.of(root),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(grandThird, grandFourth), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure("grandFirst", "Grand First", Optional.of(first),
            Collections.singletonList(nodeKey("n1")), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(enclosure("grandSecond", "Grand Second", Optional.of(first),
            Collections.singletonList(nodeKey("n2")), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(enclosure("grandThird", "Grand Third", Optional.of(second),
            Collections.singletonList(nodeKey("n3")), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(enclosure("grandFourth", "Grand Fourth", Optional.of(second),
            Collections.singletonList(nodeKey("n4")), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        GraphStreamLayoutEngine.BoundarySizes sizes =
            new GraphStreamLayoutEngine.BoundarySizes(projection(nodes, enclosures));

        List<EnclosureHullKey> all = Arrays.asList(root, first, second, grandFirst, grandSecond,
            grandThird, grandFourth);
        for (EnclosureHullKey key : all) {
            assertThat(sizes.footprint(key)).isSameAs(sizes.footprint(key));
        }
        assertThat(sizes.placements(root)).isSameAs(sizes.placements(root));
    }
}
