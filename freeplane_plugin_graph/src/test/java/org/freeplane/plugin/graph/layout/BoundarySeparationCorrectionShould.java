package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
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
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.junit.Test;

public class BoundarySeparationCorrectionShould {
    static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
        new FontRenderContext(null, true, true));

    @Test
    public void fastPathPublishesInputPositionsWithZeroRounds() {
        final ProjectedNodeKey first = key("first");
        final ProjectedNodeKey second = key("second");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final GraphProjection projection = projection(Arrays.asList(first, second),
            Arrays.asList(root(firstHull, "first", Collections.singletonList(first)),
                root(secondHull, "second", Collections.singletonList(second))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(first, 0.0, 0.0), nodeEntry(second, 100.0, 0.0)),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 100.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().conflicts()).isEmpty();
        assertThat(result.diagnostics().residualHullPairs()).isEmpty();
    }

    @Test
    public void bareContactIsNotAViolation() {
        final ProjectedNodeKey first = key("first");
        final ProjectedNodeKey second = key("second");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final GraphProjection projection = projection(Arrays.asList(first, second),
            Arrays.asList(root(firstHull, "first", Collections.singletonList(first)),
                root(secondHull, "second", Collections.singletonList(second))));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection,
            positions(Arrays.asList(nodeEntry(first, 0.0, 0.0), nodeEntry(second, 48.0, 0.0)),
                Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 48.0, 0.0))),
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
    }

    @Test
    public void coincidentPinnedHullsAreContainmentViolationsWithOneConflict() {
        final ProjectedNodeKey first = key("first");
        final ProjectedNodeKey second = key("second");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final PinProjection firstPin = pin(first, 0.0, 0.0);
        final PinProjection secondPin = pin(second, 0.0, 0.0);
        final GraphProjection projection = projection(Arrays.asList(first, second),
            Arrays.asList(root(firstHull, "first", Collections.singletonList(first)),
                root(secondHull, "second", Collections.singletonList(second))));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection,
            positions(Arrays.asList(nodeEntry(first, 0.0, 0.0), nodeEntry(second, 0.0, 0.0)),
                Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 0.0, 0.0))),
            METRICS, Arrays.asList(firstPin, secondPin));

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isFalse();
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).kind())
            .isEqualTo(BoundaryConflict.Kind.SIBLING_CONTAINMENT);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(result.diagnostics().residualHullPairs()).containsExactly(
            result.diagnostics().conflicts().get(0).pairKey());
    }

    @Test
    public void suppressedEnclosuresAreNotEnforced() {
        final ProjectedNodeKey first = key("first");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey suppressedHull = hull("suppressed-hull");
        final ProjectedEnclosure enforced = root(firstHull, "first", Collections.singletonList(first));
        final ProjectedEnclosure suppressed = ProjectedEnclosure.of(suppressedHull, suppressedHull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("suppressed", "suppressed")), "m",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUPPRESSED);
        final GraphProjection projection = projection(Collections.singletonList(first),
            Arrays.asList(enforced, suppressed));
        final LayoutPositions positions = positions(Collections.singletonList(nodeEntry(first, 0.0, 0.0)),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(suppressedHull, 10.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().conflicts()).isEmpty();
    }

    @Test
    public void subEpsilonOverlapIsContactByTolerance() {
        final HullGeometry first = square(0.0, 1.0);
        final HullGeometry second = square(2.0 - 1.0e-12, 1.0);

        assertThat(HullIntersection.siblingOverlap(first, second)).isTrue();
        assertThat(HullIntersection.minimumSeparatingTranslation(first, second))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));
    }

    @Test
    public void subEpsilonOverlappingEnclosuresAreContactByTolerance() {
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final GraphProjection projection = projection(Collections.<ProjectedNodeKey>emptyList(),
            Arrays.asList(root(firstHull, "contact", Collections.<ProjectedNodeKey>emptyList()),
                root(secondHull, "contact", Collections.<ProjectedNodeKey>emptyList())));
        final GraphGeometry unplaced = new GraphGeometryEngine().computeHulls(projection,
            positions(Collections.<Map.Entry<ProjectedNodeKey, LayoutPoint>>emptyList(),
                Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 0.0, 0.0))),
            METRICS);
        final double subEpsilonOverlap = 1.0e-12;
        final double secondAnchorX = unplaced.hulls().get(firstHull).maxX()
            - unplaced.hulls().get(secondHull).minX() - subEpsilonOverlap;
        final LayoutPositions positions = positions(
            Collections.<Map.Entry<ProjectedNodeKey, LayoutPoint>>emptyList(),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, secondAnchorX, 0.0)));
        final GraphGeometry placed = new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        assertThat(HullIntersection.siblingOverlap(placed.hulls().get(firstHull),
            placed.hulls().get(secondHull))).isTrue();
        assertThat(HullIntersection.minimumSeparatingTranslation(placed.hulls().get(firstHull),
            placed.hulls().get(secondHull))).isEqualTo(LayoutPoint.of(0.0, 0.0));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().conflicts()).isEmpty();
        assertThat(result.diagnostics().residualHullPairs()).isEmpty();
    }

    @Test
    public void guardedMstFailureIsWrappedWithItsCause() {
        final HullGeometry first = square(0.0, 1.7e308);
        final HullGeometry second = square(0.005e308, 1.69e308);

        assertThatThrownBy(() -> BoundarySeparationCorrection.guardedMinimumSeparatingTranslation(first, second))
            .isInstanceOf(BoundarySeparationException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void ancestorEscapePairKeysReportEscapingChildren() {
        final ProjectedNodeKey child = key("child");
        final EnclosureHullKey childHull = hull("child-hull");
        final EnclosureHullKey parentHull = hull("parent-hull");
        final ProjectedEnclosure parent = root(parentHull, "parent", Collections.<ProjectedNodeKey>emptyList());
        final ProjectedEnclosure escaping = ProjectedEnclosure.of(childHull, childHull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("child", "child")), "m", Optional.of(parentHull),
            Collections.singletonList(child), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE);
        final GraphProjection projection = projection(Collections.singletonList(child),
            Arrays.asList(parent, escaping));
        final Map<EnclosureHullKey, HullGeometry> hulls =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(parentHull, square(0.0, 10.0));
        hulls.put(childHull, square(20.0, 1.0));

        assertThat(BoundarySeparationCorrection.ancestorEscapePairKeys(projection, hulls))
            .containsExactly(CanonicalLayoutKeys.pair(childHull, parentHull));
    }

    private static HullGeometry square(double centerX, double halfExtent) {
        return HullGeometry.of(Arrays.asList(LayoutPoint.of(centerX - halfExtent, -halfExtent),
            LayoutPoint.of(centerX + halfExtent, -halfExtent), LayoutPoint.of(centerX + halfExtent, halfExtent),
            LayoutPoint.of(centerX - halfExtent, halfExtent)), LayoutPoint.of(centerX, 0.0));
    }

    private static ProjectedEnclosure root(EnclosureHullKey hull, String label, List<ProjectedNodeKey> nodes) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m", Optional.<EnclosureHullKey>empty(),
            nodes, Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
    }

    private static GraphProjection projection(List<ProjectedNodeKey> nodes, List<ProjectedEnclosure> enclosures) {
        final List<ProjectedNode> projected = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : nodes) {
            projected.add(ProjectedNode.of(key, SafeNodeLabel.of("n", "n"), "m", false));
        }
        return GraphProjection.projected(1L, projected, enclosures,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static LayoutPositions positions(List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodes,
            List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchors) {
        final Map<ProjectedNodeKey, LayoutPoint> nodeMap = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : nodes) {
            nodeMap.put(entry.getKey(), entry.getValue());
        }
        final Map<EnclosureHullKey, LayoutPoint> anchorMap = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : anchors) {
            anchorMap.put(entry.getKey(), entry.getValue());
        }
        return LayoutPositions.of(nodeMap, anchorMap);
    }

    private static Map.Entry<ProjectedNodeKey, LayoutPoint> nodeEntry(ProjectedNodeKey key, double x, double y) {
        return new java.util.AbstractMap.SimpleImmutableEntry<ProjectedNodeKey, LayoutPoint>(key,
            LayoutPoint.of(x, y));
    }

    private static Map.Entry<EnclosureHullKey, LayoutPoint> anchorEntry(EnclosureHullKey key, double x,
            double y) {
        return new java.util.AbstractMap.SimpleImmutableEntry<EnclosureHullKey, LayoutPoint>(key,
            LayoutPoint.of(x, y));
    }

    private static PinProjection pin(ProjectedNodeKey key, double x, double y) {
        return PinProjection.active(PinRecord.of(key.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList()), key);
    }

    private static ProjectedNodeKey key(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }
}
