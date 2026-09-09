package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class BoundarySeparationShould {
    // Test-local size formulas; the values must equal the production
    // BoundarySizes constants (reviewed by the task reviewer).
    private static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
    private static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
    private static final double BOUNDARY_PADDING = 8.0;
    private static final double SIBLING_GAP = 8.0;
    private static final double FRAME_CLEARANCE = 16.0;

    private static final WorkspaceId WORKSPACE_ONE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000111");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void siblingBoundariesSeedWithoutOverlap() {
        SiblingFixture fixture = wideSiblingFixture();

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, fixture.projection, fixture.projection,
                Collections.<PinProjection>emptyList()));

            assertNoSiblingOverlap(frame, fixture.hulls, fixture.labels);
        }
    }

    @Test
    public void settledSiblingBoundariesRemainSeparated() {
        SiblingFixture fixture = wideSiblingFixture();

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, fixture.projection, fixture.projection,
                Collections.<PinProjection>emptyList()));
            for (int step = 0; step < 1500; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, fixture.projection, fixture.projection,
                Collections.<PinProjection>emptyList()));

            assertNoSiblingOverlap(frame, fixture.hulls, fixture.labels);
        }
    }

    @Test
    public void nestedGroupRingsDoNotInterleave() {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        EnclosureHullKey rootHull = hull(MAP_ONE, "root");
        List<EnclosureHullKey> groupHulls = new ArrayList<EnclosureHullKey>();
        List<Double> groupSpans = new ArrayList<Double>();
        List<EnclosureHullKey> rootChildren = new ArrayList<EnclosureHullKey>();
        for (int groupIndex = 0; groupIndex < 2; groupIndex++) {
            EnclosureHullKey groupHull = hull(MAP_ONE, "group-" + groupIndex);
            groupHulls.add(groupHull);
            List<EnclosureHullKey> subgroups = new ArrayList<EnclosureHullKey>();
            List<SafeNodeLabel> subgroupLabels = new ArrayList<SafeNodeLabel>();
            for (int index = 0; index < 3; index++) {
                EnclosureKey key = EnclosureKey.of(source(MAP_ONE, "group-" + groupIndex + "-sub-" + index));
                EnclosureHullKey subgroupHull = EnclosureHullKey.of(Collections.singletonList(key));
                SafeNodeLabel label = SafeNodeLabel.of("sub-" + groupIndex + "-" + index, "sub-" + groupIndex
                    + "-" + index);
                enclosures.add(ProjectedEnclosure.of(subgroupHull, Collections.singletonList(key),
                    Collections.singletonList(label), "map", Optional.of(groupHull),
                    Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
                    BoundaryTier.EMPHATIC));
                subgroups.add(subgroupHull);
                subgroupLabels.add(label);
            }
            enclosures.add(ProjectedEnclosure.of(groupHull, Collections.singletonList(
                EnclosureKey.of(source(MAP_ONE, "group-" + groupIndex))), Collections.singletonList(
                    SafeNodeLabel.of("group-" + groupIndex, "group-" + groupIndex)), "map",
                Optional.of(rootHull), Collections.<ProjectedNodeKey>emptyList(), subgroups, false,
                BoundaryTier.EMPHATIC));
            groupSpans.add(Double.valueOf(ringRadius(subgroupLabels) + maximumReach(subgroupLabels)));
            rootChildren.add(groupHull);
        }
        enclosures.add(ProjectedEnclosure.of(rootHull, Collections.singletonList(
            EnclosureKey.of(source(MAP_ONE, "root"))), Collections.singletonList(SafeNodeLabel.of("Root", "Root")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), rootChildren,
            true, BoundaryTier.EMPHATIC));
        GraphProjection projection = projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            for (int first = 0; first < groupHulls.size(); first++) {
                for (int second = first + 1; second < groupHulls.size(); second++) {
                    double distance = distance(frame.positions().anchors().get(groupHulls.get(first)),
                        frame.positions().anchors().get(groupHulls.get(second)));
                    assertThat(distance).isGreaterThanOrEqualTo(groupSpans.get(first).doubleValue()
                        + groupSpans.get(second).doubleValue() + SIBLING_GAP);
                }
            }
        }
    }

    @Test
    public void rootFramesSeparateOnTheTopRing() {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<EnclosureHullKey> rootHulls = new ArrayList<EnclosureHullKey>();
        List<Double> rootWidths = new ArrayList<Double>();
        MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO};
        for (int mapIndex = 0; mapIndex < maps.length; mapIndex++) {
            MapReferenceId map = maps[mapIndex];
            EnclosureHullKey rootHull = hull(map, "root");
            EnclosureHullKey groupHull = hull(map, "group");
            EnclosureKey groupKey = EnclosureKey.of(source(map, "group"));
            SafeNodeLabel groupLabel = SafeNodeLabel.of("Group A", "Group A");
            enclosures.add(ProjectedEnclosure.of(groupHull, Collections.singletonList(groupKey),
                Collections.singletonList(groupLabel), "map", Optional.of(rootHull),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.EMPHATIC));
            rootHulls.add(rootHull);
            enclosures.add(ProjectedEnclosure.of(rootHull, Collections.singletonList(
                EnclosureKey.of(source(map, "root"))), Collections.singletonList(SafeNodeLabel.of("Root", "Root")),
                "map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(groupHull), true, BoundaryTier.EMPHATIC));
            rootWidths.add(Double.valueOf(2.0 * (reachOf(groupLabel) + FRAME_CLEARANCE)));
        }
        GraphProjection projection = projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            double distance = distance(frame.positions().anchors().get(rootHulls.get(0)),
                frame.positions().anchors().get(rootHulls.get(1)));
            assertThat(distance).isGreaterThanOrEqualTo(
                (rootWidths.get(0).doubleValue() + rootWidths.get(1).doubleValue()) * 0.5);
        }
    }

    @Test
    public void pinnedNodesKeepTheirForcedPositions() {
        ProjectedNodeKey pinnedNodeOne = key(MAP_ONE, "pinned-1");
        ProjectedNodeKey pinnedNodeTwo = key(MAP_ONE, "pinned-2");
        ProjectedNode nodeObjectOne = node(MAP_ONE, "pinned-1");
        ProjectedNode nodeObjectTwo = node(MAP_ONE, "pinned-2");

        EnclosureKey groupKey = EnclosureKey.of(source(MAP_ONE, "group"));
        EnclosureHullKey groupHull = EnclosureHullKey.of(Collections.singletonList(groupKey));
        EnclosureKey rootKey = EnclosureKey.of(source(MAP_ONE, "root"));
        EnclosureHullKey rootHull = EnclosureHullKey.of(Collections.singletonList(rootKey));

        ProjectedEnclosure group = ProjectedEnclosure.of(groupHull, Collections.singletonList(groupKey),
            Collections.singletonList(SafeNodeLabel.of("Group", "Group")), "map",
            Optional.of(rootHull), Arrays.asList(pinnedNodeOne, pinnedNodeTwo),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC);
        ProjectedEnclosure root = ProjectedEnclosure.of(rootHull, Collections.singletonList(rootKey),
            Collections.singletonList(SafeNodeLabel.of("Root", "Root")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.singletonList(groupHull), true, BoundaryTier.EMPHATIC);

        List<PinProjection> pins = Arrays.asList(
            pin(pinnedNodeOne, 40.0, 40.0),
            pin(pinnedNodeTwo, 60.0, 80.0)
        );

        GraphProjection projection = projection(1, Arrays.asList(nodeObjectOne, nodeObjectTwo),
            Arrays.asList(root, group), Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, projection, projection, pins));
            for (int step = 0; step < 300; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection, pins));

            assertThat(frame.positions().nodes().get(pinnedNodeOne)).isEqualTo(LayoutPoint.of(40.0, 40.0));
            assertThat(frame.positions().nodes().get(pinnedNodeTwo)).isEqualTo(LayoutPoint.of(60.0, 80.0));
        }
    }

    @Test
    public void repeatedSettlesAreDeterministic() {
        SiblingFixture fixture = wideSiblingFixture();

        LayoutFrame first = settle(WORKSPACE_ONE, fixture.projection);
        LayoutFrame second = settle(WORKSPACE_ONE, fixture.projection);

        assertThat(second.positions()).isEqualTo(first.positions());
    }

    @Test
    public void directNodesLieInsideTheirParentHullAfterGeometryComputation() {
        ProjectedNodeKey nodeOne = key(MAP_ONE, "direct-one");
        ProjectedNodeKey nodeTwo = key(MAP_ONE, "direct-two");
        ProjectedNode nodeObjectOne = node(MAP_ONE, "direct-one");
        ProjectedNode nodeObjectTwo = node(MAP_ONE, "direct-two");

        EnclosureKey groupKey = EnclosureKey.of(source(MAP_ONE, "enclosure"));
        EnclosureHullKey groupHull = EnclosureHullKey.of(Collections.singletonList(groupKey));
        EnclosureKey rootKey = EnclosureKey.of(source(MAP_ONE, "root"));
        EnclosureHullKey rootHull = EnclosureHullKey.of(Collections.singletonList(rootKey));

        ProjectedEnclosure enclosure = ProjectedEnclosure.of(groupHull, Collections.singletonList(groupKey),
            Collections.singletonList(SafeNodeLabel.of("Enclosure", "Enclosure")), "map",
            Optional.of(rootHull), Arrays.asList(nodeOne, nodeTwo), Collections.<EnclosureHullKey>emptyList(),
            false, BoundaryTier.SUBTLE);
        ProjectedEnclosure root = ProjectedEnclosure.of(rootHull, Collections.singletonList(rootKey),
            Collections.singletonList(SafeNodeLabel.of("Root", "Root")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.singletonList(groupHull), true, BoundaryTier.SUPPRESSED);

        GraphProjection testProjection = projection(1, Arrays.asList(nodeObjectOne, nodeObjectTwo),
            Arrays.asList(root, enclosure), Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, testProjection, testProjection, Collections.<PinProjection>emptyList()));
            for (int step = 0; step < 300; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, testProjection, testProjection,
                Collections.<PinProjection>emptyList()));

            org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
                new org.freeplane.plugin.graph.geometry.GraphGeometryEngine().computeHulls(
                    testProjection, frame.positions(),
                    new org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics(
                        new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12),
                        new java.awt.font.FontRenderContext(null, true, true)));

            org.freeplane.plugin.graph.geometry.HullGeometry hullGeometry = geometry.hulls().get(groupHull);
            assertThat(hullGeometry).isNotNull();

            LayoutPoint posOne = frame.positions().nodes().get(nodeOne);
            LayoutPoint posTwo = frame.positions().nodes().get(nodeTwo);
            assertThat(posOne).isNotNull();
            assertThat(posTwo).isNotNull();

            assertThat(hullGeometry.contains(posOne)).isTrue();
            assertThat(hullGeometry.contains(posTwo)).isTrue();
        }
    }

    private static ProjectedNode node(MapReferenceId map, String id) {
        ProjectedNodeKey key = key(map, id);
        return ProjectedNode.of(key, SafeNodeLabel.of(id, id), "Map " + map.value(), false);
    }

    private static LayoutFrame settle(WorkspaceId workspace, GraphProjection projection) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(workspace, projection, projection, Collections.<PinProjection>emptyList()));
            for (int step = 0; step < 1500; step++) {
                engine.step();
            }
            return engine.apply(request(workspace, projection, projection, Collections.<PinProjection>emptyList()));
        }
    }

    private static void assertNoSiblingOverlap(LayoutFrame frame, List<EnclosureHullKey> hulls,
            List<SafeNodeLabel> labels) {
        for (int first = 0; first < hulls.size(); first++) {
            for (int second = first + 1; second < hulls.size(); second++) {
                LayoutPoint firstAnchor = frame.positions().anchors().get(hulls.get(first));
                LayoutPoint secondAnchor = frame.positions().anchors().get(hulls.get(second));
                assertThat(boxesOverlap(firstAnchor, widthOf(labels.get(first)), heightOf(labels.get(first)),
                    secondAnchor, widthOf(labels.get(second)), heightOf(labels.get(second)))).isFalse();
            }
        }
    }

    // One very wide sibling plus three narrow siblings: the ring radius depends on
    // the widest sibling (max), so a regression to the mean width shrinks the ring
    // enough to overlap the wide sibling with its opposite narrow sibling.
    private static SiblingFixture wideSiblingFixture() {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<EnclosureHullKey> hulls = new ArrayList<EnclosureHullKey>();
        List<SafeNodeLabel> labels = new ArrayList<SafeNodeLabel>();
        List<EnclosureHullKey> rootChildren = new ArrayList<EnclosureHullKey>();
        EnclosureHullKey rootHull = hull(MAP_ONE, "root");
        String[] texts = new String[] {"A very wide boundary label number 4", "n1", "n2", "n3"};
        for (int index = 0; index < texts.length; index++) {
            EnclosureKey key = EnclosureKey.of(source(MAP_ONE, "wide-" + index));
            EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(key));
            SafeNodeLabel label = SafeNodeLabel.of(texts[index], texts[index]);
            enclosures.add(ProjectedEnclosure.of(hull, Collections.singletonList(key),
                Collections.singletonList(label), "map", Optional.of(rootHull),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.EMPHATIC));
            hulls.add(hull);
            labels.add(label);
            rootChildren.add(hull);
        }
        enclosures.add(ProjectedEnclosure.of(rootHull, Collections.singletonList(
            EnclosureKey.of(source(MAP_ONE, "root"))), Collections.singletonList(SafeNodeLabel.of("Root", "Root")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), rootChildren,
            true, BoundaryTier.EMPHATIC));
        return new SiblingFixture(projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList()), hulls, labels);
    }

    private static double ringRadius(List<SafeNodeLabel> children) {
        if (children.size() <= 1) {
            return 0.0;
        }
        double maxWidth = 0.0;
        double maxHeight = 0.0;
        for (SafeNodeLabel label : children) {
            maxWidth = Math.max(maxWidth, widthOf(label));
            maxHeight = Math.max(maxHeight, heightOf(label));
        }
        return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
            / (2.0 * Math.sin(Math.PI / children.size()));
    }

    private static double maximumReach(List<SafeNodeLabel> labels) {
        double result = 0.0;
        for (SafeNodeLabel label : labels) {
            result = Math.max(result, reachOf(label));
        }
        return result;
    }

    private static double reachOf(SafeNodeLabel label) {
        return 0.5 * Math.hypot(widthOf(label), heightOf(label));
    }

    private static double widthOf(SafeNodeLabel label) {
        return label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
    }

    private static double heightOf(SafeNodeLabel label) {
        return CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
    }

    private static boolean boxesOverlap(LayoutPoint first, double firstWidth, double firstHeight,
            LayoutPoint second, double secondWidth, double secondHeight) {
        return Math.abs(first.x() - second.x()) < (firstWidth + secondWidth) * 0.5
            && Math.abs(first.y() - second.y()) < (firstHeight + secondHeight) * 0.5;
    }

    private static LayoutRequest request(WorkspaceId workspace, GraphProjection before, GraphProjection after,
            List<PinProjection> pins) {
        return LayoutRequest.of(workspace, after, ProjectionDiff.between(before, after), pins);
    }

    private static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges) {
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<org.freeplane.plugin.graph.projection.RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(source(map, id));
    }

    private static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        PinRecord record = PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<org.freeplane.plugin.graph.workspace.model.UnknownXml>emptyList());
        return PinProjection.active(record, node);
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    private static double distance(LayoutPoint first, LayoutPoint second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        return Math.sqrt(x * x + y * y);
    }

    private static final class SiblingFixture {
        final GraphProjection projection;
        final List<EnclosureHullKey> hulls;
        final List<SafeNodeLabel> labels;

        SiblingFixture(GraphProjection projection, List<EnclosureHullKey> hulls, List<SafeNodeLabel> labels) {
            this.projection = projection;
            this.hulls = hulls;
            this.labels = labels;
        }
    }
}
