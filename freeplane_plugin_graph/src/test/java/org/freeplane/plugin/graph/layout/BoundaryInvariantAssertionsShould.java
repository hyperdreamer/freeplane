package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
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

public class BoundaryInvariantAssertionsShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");

    @Test
    public void acceptACleanVerifiedFrame() {
        assertThatCode(() -> BoundaryInvariantAssertions.assertVerifiedFrame(projection(), frame(0.0, 60.0),
            BoundarySeparationDiagnostics.empty(), metrics(), Collections.<PinProjection>emptyList()))
                .doesNotThrowAnyException();
        assertThatCode(() -> BoundaryInvariantAssertions.assertBijection(BoundarySeparationDiagnostics.empty()))
            .doesNotThrowAnyException();
    }

    @Test
    public void rejectACrossingVerifiedFrame() {
        assertThatThrownBy(() -> BoundaryInvariantAssertions.assertVerifiedFrame(projection(), frame(0.0, 38.0),
            BoundarySeparationDiagnostics.empty(), metrics(), Collections.<PinProjection>emptyList()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void rejectNonBijectiveDiagnostics() {
        final BoundaryConflict conflict = new BoundaryConflict(hull("left"), hull("right"),
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.ROUND_LIMIT,
            Collections.<PinProjection>emptyList());
        final BoundarySeparationDiagnostics orphaned = new BoundarySeparationDiagnostics(
            Collections.singletonList(conflict), Collections.<String>emptyList(), 1, 0, 1, 0.0, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0);

        assertThatThrownBy(() -> BoundaryInvariantAssertions.assertBijection(orphaned))
            .isInstanceOf(AssertionError.class);
    }

    private static GraphProjection projection() {
        final ProjectedNodeKey left = node("left");
        final ProjectedNodeKey right = node("right");
        final EnclosureHullKey leftHull = hull("left");
        final EnclosureHullKey rightHull = hull("right");
        final ProjectedEnclosure leftEnclosure = ProjectedEnclosure.of(leftHull,
            leftHull.endpointKeys(), Collections.singletonList(SafeNodeLabel.of("left", "left")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(left),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        final ProjectedEnclosure rightEnclosure = ProjectedEnclosure.of(rightHull,
            rightHull.endpointKeys(), Collections.singletonList(SafeNodeLabel.of("right", "right")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(right),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        return GraphProjection.projected(1L,
            Arrays.asList(ProjectedNode.of(left, SafeNodeLabel.of("left", "left"), "map", false),
                ProjectedNode.of(right, SafeNodeLabel.of("right", "right"), "map", false)),
            Arrays.asList(leftEnclosure, rightEnclosure),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static LayoutFrame frame(double leftX, double rightX) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(node("left"), LayoutPoint.of(leftX, 0.0));
        nodes.put(node("right"), LayoutPoint.of(rightX, 0.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hull("left"), LayoutPoint.of(leftX, 0.0));
        anchors.put(hull("right"), LayoutPoint.of(rightX, 0.0));
        return LayoutFrame.of(0L, LayoutPositions.of(nodes, anchors), false, 0);
    }

    private static GeometryTextMetrics metrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
    }

    private static ProjectedNodeKey node(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }
}
