package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class TypedForcesShould {
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
    public void produceIdenticalFramesForEqualRequests() {
        GraphProjection projection = baseline(1);

        LayoutFrame first = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());
        LayoutFrame second = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());

        assertThat(first.positions()).isEqualTo(second.positions());
    }

    @Test
    public void smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin() {
        GraphProjection projection = baseline(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            assertThat(greatestDistanceBetweenDistinctPositions(frame)).isGreaterThan(1.0);
        }
    }

    @Test
    public void firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours() {
        GraphProjection projection = leafRoots(2);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame applied = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            LayoutFrame stepped = engine.step();

            assertThat(greatestMovementBetween(applied, stepped)).isLessThan(8.0);
        }
    }

    @Test
    public void aTopologyChangeDoesNotTeleportRetainedParticles() {
        GraphProjection baseline = leafRoots(3);
        GraphProjection expanded = leafRoots(2);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, baseline, baseline, Collections.<PinProjection>emptyList()));
            engine.step();
            LayoutFrame before = engine.apply(request(WORKSPACE_ONE, baseline, expanded,
                Collections.<PinProjection>emptyList()));
            LayoutFrame after = engine.step();

            assertThat(greatestMovementBetween(before, after)).isLessThan(8.0);

            LayoutFrame reAdded = engine.apply(request(WORKSPACE_ONE, expanded, baseline,
                Collections.<PinProjection>emptyList()));
            LayoutFrame afterReAdd = engine.step();

            assertThat(greatestMovementBetween(reAdded, afterReAdd)).isLessThan(8.0);
        }
    }

    @Test
    public void largerWorkspacesSeedWiderThanTheMinimumSpread() {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<EnclosureHullKey> rootChildren = new ArrayList<EnclosureHullKey>();
        EnclosureHullKey rootHull = hull(MAP_ONE, "root");
        for (int index = 0; index < 200; index++) {
            EnclosureKey key = EnclosureKey.of(source(MAP_ONE, "wide-" + index));
            EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(key));
            SafeNodeLabel label = SafeNodeLabel.of("wide-" + index, "wide-" + index);
            enclosures.add(ProjectedEnclosure.of(hull, Collections.singletonList(key),
                Collections.singletonList(label), "Map", Optional.of(rootHull),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            rootChildren.add(hull);
        }
        enclosures.add(ProjectedEnclosure.of(rootHull, Collections.singletonList(
            EnclosureKey.of(source(MAP_ONE, "root"))), Collections.singletonList(SafeNodeLabel.of("Root", "Root")),
            "Map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), rootChildren,
            true, BoundaryTier.EMPHATIC));
        GraphProjection projection = projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList());

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            assertThat(greatestDistanceBetweenDistinctPositions(frame)).isGreaterThan(150.0);
        }
    }

    @Test
    public void hierarchyAnchorsSeedOnTheGroupRing() {
        List<SafeNodeLabel> groupLabels = new ArrayList<SafeNodeLabel>();
        List<EnclosureHullKey> groupHulls = new ArrayList<EnclosureHullKey>();
        List<EnclosureHullKey> rootChildren = new ArrayList<EnclosureHullKey>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        EnclosureHullKey rootHull = hull(MAP_ONE, "root");
        for (int index = 0; index < 3; index++) {
            SafeNodeLabel label = SafeNodeLabel.of("group-" + index, "group-" + index);
            groupLabels.add(label);
            EnclosureHullKey groupHull = hull(MAP_ONE, "group-" + index);
            groupHulls.add(groupHull);
            rootChildren.add(groupHull);
            enclosures.add(ProjectedEnclosure.of(groupHull, Collections.singletonList(
                EnclosureKey.of(source(MAP_ONE, "group-" + index))), Collections.singletonList(label), "Map",
                Optional.of(rootHull), Collections.<ProjectedNodeKey>emptyList(),
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE));
        }
        enclosures.add(ProjectedEnclosure.of(rootHull, Collections.singletonList(
            EnclosureKey.of(source(MAP_ONE, "root"))), Collections.singletonList(SafeNodeLabel.of("Root", "Root")),
            "Map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), rootChildren,
            true, BoundaryTier.EMPHATIC));
        GraphProjection projection = projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList());
        double ringRadius = ringRadius(groupLabels);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            for (EnclosureHullKey groupHull : groupHulls) {
                assertThat(distance(frame.positions().anchors().get(rootHull),
                    frame.positions().anchors().get(groupHull))).isCloseTo(ringRadius, within(0.001));
            }
            for (int first = 0; first < groupHulls.size(); first++) {
                for (int second = first + 1; second < groupHulls.size(); second++) {
                    double chord = distance(frame.positions().anchors().get(groupHulls.get(first)),
                        frame.positions().anchors().get(groupHulls.get(second)));
                    assertThat(chord).isGreaterThanOrEqualTo(maximumWidth(groupLabels) + SIBLING_GAP);
                }
            }
        }
    }

    @Test
    public void hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength() {
        GraphProjection nested = hierarchyProjection(1, true);
        GraphProjection peers = hierarchyProjection(1, false);
        List<PinProjection> pins = Collections.singletonList(pin(key(MAP_ONE, "hierarchy-parent"), 0.0, 0.0));
        double nestedDistance = anchorDistanceAfterSteps(WORKSPACE_ONE, nested, pins, 300);
        double peerDistance = anchorDistanceAfterSteps(WORKSPACE_ONE, peers, pins, 300);

        assertThat(nestedDistance).isGreaterThan(100.0);
        assertThat(nestedDistance).isLessThan(peerDistance);
    }

    @Test
    public void capAggregateCrossMapFanOutDisplacementOncePerParticle() {
        GraphProjection projection = crossMapFanOut(1);
        EnclosureHullKey center = hull(MAP_ONE, "center");

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame before = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            LayoutFrame after = engine.step();

            assertThat(distance(before.positions().anchors().get(center), after.positions().anchors().get(center)))
                .isLessThanOrEqualTo(0.0050000001);
        }
    }

    @Test
    public void acceptRelationshipAndHierarchyEdgesBetweenTheSameAnchors() {
        EnclosureKey parentKey = EnclosureKey.of(source(MAP_ONE, "parallel-parent"));
        EnclosureKey childKey = EnclosureKey.of(source(MAP_ONE, "parallel-child"));
        EnclosureHullKey parentHull = EnclosureHullKey.of(Collections.singletonList(parentKey));
        EnclosureHullKey childHull = EnclosureHullKey.of(Collections.singletonList(childKey));
        ProjectedEnclosure parent = enclosure(parentHull, parentKey, "parallel-parent",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(childHull), true);
        ProjectedEnclosure child = enclosure(childHull, childKey, "parallel-child", Optional.of(parentHull),
            Collections.<EnclosureHullKey>emptyList(), false);
        GraphProjection projection = projection(1, Collections.<ProjectedNode>emptyList(),
            Arrays.asList(parent, child),
            Collections.singletonList(connectorEdge(0, ProjectedEndpointKey.ofEnclosure(parentKey),
                ProjectedEndpointKey.ofEnclosure(childKey))));

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame applied = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            LayoutFrame stepped = engine.step();

            assertThat(applied.failed()).isFalse();
            assertThat(stepped.failed()).isFalse();
            assertCoverage(stepped, projection);
        }
    }

    @Test
    public void exposeEveryEnclosureAnchor() {
        GraphProjection projection = baseline(1);

        LayoutFrame frame = frameAfterOneStep(WORKSPACE_ONE, projection, Collections.<PinProjection>emptyList());

        assertCoverage(frame, projection);
    }

    @Test
    public void retainHierarchyAnchorsAcrossSteps() {
        GraphProjection projection = baseline(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, projection, projection, Collections.<PinProjection>emptyList()));
            LayoutFrame first = engine.step();
            LayoutFrame second = engine.step();

            assertCoverage(first, projection);
            assertCoverage(second, projection);
            assertThat(second.positions().anchors()).containsKeys(rootHull(), childHull());
        }
    }

    @Test
    public void returnToBaselineCoverageAfterOneHundredAddRemoveCycles() {
        GraphProjection baseline = baseline(1);
        GraphProjection expanded = expanded(2);
        LayoutFrame finalFrame = null;

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, baseline, baseline, Collections.<PinProjection>emptyList()));
            engine.step();
            for (int cycle = 0; cycle < 100; cycle++) {
                engine.apply(request(WORKSPACE_ONE, baseline, expanded, Collections.<PinProjection>emptyList()));
                engine.step();
                engine.apply(request(WORKSPACE_ONE, expanded, baseline, Collections.<PinProjection>emptyList()));
                finalFrame = engine.step();
            }
        }

        assertThat(finalFrame).isNotNull();
        assertCoverage(finalFrame, baseline);
    }

    private static LayoutFrame frameAfterOneStep(WorkspaceId workspace, GraphProjection projection,
            List<PinProjection> pins) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(workspace, projection, projection, pins));
            return engine.step();
        }
    }

    private static double anchorDistanceAfterSteps(WorkspaceId workspace, GraphProjection projection,
            List<PinProjection> pins, int steps) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(workspace, projection, projection, pins));
            for (int step = 0; step < steps; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(request(workspace, projection, projection, pins));
            return distance(frame.positions().anchors().get(hierarchyParentHull()),
                frame.positions().anchors().get(hierarchyChildHull()));
        }
    }

    private static double greatestDistanceBetweenDistinctPositions(LayoutFrame frame) {
        List<LayoutPoint> positions = new ArrayList<LayoutPoint>(frame.positions().anchors().values());
        double greatestDistance = 0.0;
        for (int first = 0; first < positions.size(); first++) {
            for (int second = first + 1; second < positions.size(); second++) {
                greatestDistance = Math.max(greatestDistance, distance(positions.get(first), positions.get(second)));
            }
        }
        return greatestDistance;
    }

    private static double greatestMovementBetween(LayoutFrame first, LayoutFrame second) {
        double greatest = 0.0;
        for (EnclosureHullKey key : first.positions().anchors().keySet()) {
            greatest = Math.max(greatest, distance(first.positions().anchors().get(key),
                second.positions().anchors().get(key)));
        }
        return greatest;
    }

    private static LayoutRequest request(WorkspaceId workspace, GraphProjection before, GraphProjection after,
            List<PinProjection> pins) {
        return LayoutRequest.of(workspace, after, ProjectionDiff.between(before, after), pins);
    }

    private static void assertCoverage(LayoutFrame frame, GraphProjection projection) {
        List<EnclosureHullKey> anchors = new ArrayList<EnclosureHullKey>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.add(enclosure.hullKey());
        }
        assertThat(frame.positions().nodes()).isEmpty();
        assertThat(frame.positions().anchors().keySet()).containsExactlyElementsOf(anchors);
    }

    private static GraphProjection baseline(long generation) {
        ProjectedEnclosure root = enclosure(rootHull(), enclosureKey(MAP_ONE, "root"),
            "root", Optional.<EnclosureHullKey>empty(), Collections.singletonList(childHull()), true);
        ProjectedEnclosure child = enclosure(childHull(), enclosureKey(MAP_ONE, "child"), "child",
            Optional.of(rootHull()),
            Collections.<EnclosureHullKey>emptyList(), false);
        ProjectedEnclosure otherRoot = enclosure(hull(MAP_TWO, "other-root"), enclosureKey(MAP_TWO, "other-root"),
            "other-root", Optional.<EnclosureHullKey>empty(), Collections.<EnclosureHullKey>emptyList(), true);
        return projection(generation, Collections.<ProjectedNode>emptyList(), Arrays.asList(root, child, otherRoot),
            Collections.<ProjectedEdge>emptyList());
    }

    private static GraphProjection expanded(long generation) {
        ProjectedEnclosure root = enclosure(rootHull(), enclosureKey(MAP_ONE, "root"),
            "root", Optional.<EnclosureHullKey>empty(), Arrays.asList(childHull(), extraHull()), true);
        ProjectedEnclosure child = enclosure(childHull(), enclosureKey(MAP_ONE, "child"), "child",
            Optional.of(rootHull()),
            Collections.<EnclosureHullKey>emptyList(), false);
        ProjectedEnclosure extra = enclosure(extraHull(), enclosureKey(MAP_ONE, "extra"), "extra",
            Optional.of(rootHull()),
            Collections.<EnclosureHullKey>emptyList(), false);
        ProjectedEnclosure otherRoot = enclosure(hull(MAP_TWO, "other-root"), enclosureKey(MAP_TWO, "other-root"),
            "other-root", Optional.<EnclosureHullKey>empty(), Collections.<EnclosureHullKey>emptyList(), true);
        return projection(generation, Collections.<ProjectedNode>emptyList(),
            Arrays.asList(root, child, extra, otherRoot), Collections.<ProjectedEdge>emptyList());
    }

    private static GraphProjection leafRoots(int count) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        for (int index = 0; index < count; index++) {
            String id = "r" + (index + 1);
            enclosures.add(enclosure(hull(MAP_ONE, id), enclosureKey(MAP_ONE, id), id,
                Optional.<EnclosureHullKey>empty(), Collections.<EnclosureHullKey>emptyList(), true));
        }
        return projection(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList());
    }

    private static GraphProjection hierarchyProjection(long generation, boolean includeHierarchy) {
        ProjectedEnclosure parent = enclosure(hierarchyParentHull(), enclosureKey(MAP_ONE, "hierarchy-parent"),
            "hierarchy-parent", Optional.<EnclosureHullKey>empty(), Collections.<EnclosureHullKey>emptyList(),
            true);
        ProjectedEnclosure child = enclosure(hierarchyChildHull(), enclosureKey(MAP_ONE, "hierarchy-child"),
            "hierarchy-child",
            includeHierarchy ? Optional.of(hierarchyParentHull()) : Optional.<EnclosureHullKey>empty(),
            Collections.<EnclosureHullKey>emptyList(), false);
        return projection(generation, Collections.<ProjectedNode>emptyList(), Arrays.asList(parent, child),
            Collections.<ProjectedEdge>emptyList());
    }

    private static GraphProjection crossMapFanOut(long generation) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        EnclosureHullKey centerHull = hull(MAP_ONE, "center");
        EnclosureKey centerKey = enclosureKey(MAP_ONE, "center");
        enclosures.add(enclosure(centerHull, centerKey, "center", Optional.<EnclosureHullKey>empty(),
            Collections.<EnclosureHullKey>emptyList(), true));
        List<EnclosureHullKey> leafHulls = new ArrayList<EnclosureHullKey>();
        for (int index = 0; index < 8; index++) {
            EnclosureKey leafKey = enclosureKey(MAP_TWO, "leaf-" + index);
            leafHulls.add(EnclosureHullKey.of(Collections.singletonList(leafKey)));
        }
        enclosures.add(enclosure(hull(MAP_TWO, "other-root"), enclosureKey(MAP_TWO, "other-root"), "other-root",
            Optional.<EnclosureHullKey>empty(), leafHulls, true));
        for (int index = 0; index < 8; index++) {
            EnclosureKey leafKey = enclosureKey(MAP_TWO, "leaf-" + index);
            enclosures.add(enclosure(EnclosureHullKey.of(Collections.singletonList(leafKey)), leafKey,
                "leaf-" + index, Optional.of(hull(MAP_TWO, "other-root")),
                Collections.<EnclosureHullKey>emptyList(), false));
            edges.add(edge(index, centerKey, leafKey));
        }
        return projection(generation, Collections.<ProjectedNode>emptyList(), enclosures, edges);
    }

    private static ProjectedEdge connectorEdge(int occurrence, ProjectedEndpointKey source,
            ProjectedEndpointKey target) {
        SourceNodeKey sourceKey = endpointSource(source);
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(sourceKey, endpointReference(target), false, true,
            "source", "middle", "target");
        ConnectorSnapshot snapshot = ConnectorSnapshot.of(occurrence, descriptor);
        return ProjectedEdge.of(ProjectedEdgeKey.of(source, target),
            Collections.singletonList(EdgeContributor.nativeConnector(snapshot, source, target)));
    }

    private static SourceNodeKey endpointSource(ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            return endpoint.node().get().source();
        }
        return endpoint.enclosure().get().source();
    }

    private static NodeReference endpointReference(ProjectedEndpointKey endpoint) {
        return endpointSource(endpoint).persistedReference().get();
    }

    private static ProjectedEdge edge(int occurrence, EnclosureKey source, EnclosureKey target) {
        ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofEnclosure(source);
        ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofEnclosure(target);
        EdgeContributor contributor;
        if (source.mapReferenceId().equals(target.mapReferenceId())) {
            ConnectorDescriptor descriptor = ConnectorDescriptor.of(source.source(),
                target.source().persistedReference().get(), false, true, "source", "middle", "target");
            contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor),
                sourceEndpoint, targetEndpoint);
        }
        else {
            long sequence = occurrence + 1L;
            GraphRelationshipRecord relationship = GraphRelationshipRecord.of(RelationshipId.of(String.format(
                "20000000-0000-0000-0000-%012d", Long.valueOf(sequence))), sequence,
                source.source().persistedReference().get(), target.source().persistedReference().get(),
                RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
            contributor = EdgeContributor.graphRelationship(relationship, sourceEndpoint, targetEndpoint);
        }
        return ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(contributor));
    }

    private static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges) {
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedEnclosure enclosure(EnclosureHullKey hull, EnclosureKey endpoint, String id,
            Optional<EnclosureHullKey> parent, List<EnclosureHullKey> directEnclosures, boolean mapRoot) {
        return ProjectedEnclosure.of(hull, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map", parent,
            Collections.<ProjectedNodeKey>emptyList(), directEnclosures, mapRoot,
            mapRoot ? BoundaryTier.EMPHATIC : BoundaryTier.SUBTLE);
    }

    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(source(map, id));
    }

    private static EnclosureKey enclosureKey(MapReferenceId map, String id) {
        return EnclosureKey.of(source(map, id));
    }

    private static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        PinRecord record = PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<org.freeplane.plugin.graph.workspace.model.UnknownXml>emptyList());
        return PinProjection.active(record, node);
    }

    private static EnclosureHullKey hierarchyParentHull() {
        return hull(MAP_ONE, "hierarchy-parent");
    }

    private static EnclosureHullKey hierarchyChildHull() {
        return hull(MAP_ONE, "hierarchy-child");
    }

    private static EnclosureHullKey rootHull() {
        return hull(MAP_ONE, "root");
    }

    private static EnclosureHullKey childHull() {
        return hull(MAP_ONE, "child");
    }

    private static EnclosureHullKey extraHull() {
        return hull(MAP_ONE, "extra");
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
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

    private static double maximumWidth(List<SafeNodeLabel> labels) {
        double result = 0.0;
        for (SafeNodeLabel label : labels) {
            result = Math.max(result, widthOf(label));
        }
        return result;
    }

    private static double widthOf(SafeNodeLabel label) {
        return label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
    }

    private static double heightOf(SafeNodeLabel label) {
        return CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
    }

    private static double distance(LayoutPoint first, LayoutPoint second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        return Math.sqrt(x * x + y * y);
    }
}
