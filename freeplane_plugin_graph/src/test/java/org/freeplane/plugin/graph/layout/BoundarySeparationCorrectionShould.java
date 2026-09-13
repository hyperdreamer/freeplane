package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Dimension2D;
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
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
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
    public void stageTimingsAttributeHullMeasurementOutsideThePlanStage() {
        final SleepingMetrics metrics = new SleepingMetrics(METRICS, 10L);
        final EnclosureHullKey firstHull = hull("timing-first");
        final EnclosureHullKey secondHull = hull("timing-second");
        final EnclosureHullKey thirdHull = hull("timing-third");
        final GraphProjection projection = projection(Collections.<ProjectedNodeKey>emptyList(),
            Arrays.asList(root(firstHull, "first", Collections.<ProjectedNodeKey>emptyList()),
                root(secondHull, "second", Collections.<ProjectedNodeKey>emptyList()),
                root(thirdHull, "third", Collections.<ProjectedNodeKey>emptyList())));
        final LayoutPositions positions = positions(
            Collections.<Map.Entry<ProjectedNodeKey, LayoutPoint>>emptyList(),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 200.0, 0.0),
                anchorEntry(thirdHull, 400.0, 0.0)));

        final long start = System.nanoTime();
        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            metrics, Collections.<PinProjection>emptyList());
        final long elapsed = System.nanoTime() - start;
        final BoundarySeparationTimings timings = result.timings();

        // Three empty root enclosures measure one label each, so the hull stage must contain every
        // sleep and the plan stage must contain none of them (R13: hull and plan are disjoint).
        assertThat(metrics.measuredNanos()).isGreaterThan(15_000_000L);
        assertThat(timings.hullNanos()).isGreaterThanOrEqualTo(metrics.measuredNanos());
        assertThat(timings.planNanos()).isLessThan(metrics.measuredNanos());
        assertThat(timings.separationNanos()).isLessThanOrEqualTo(elapsed);
        assertThat(timings.hullNanos()).isLessThanOrEqualTo(elapsed);
        assertThat(timings.planNanos()).isLessThanOrEqualTo(elapsed);
        assertThat(timings.applyNanos()).isLessThanOrEqualTo(elapsed);
        assertThat(timings.separationNanos() + timings.hullNanos() + timings.planNanos() + timings.applyNanos())
            .isLessThanOrEqualTo(elapsed);
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

    private static final class SleepingMetrics implements GeometryTextMetrics {
        private final GeometryTextMetrics delegate;
        private final long sleepMillis;
        private long measuredNanos;

        private SleepingMetrics(final GeometryTextMetrics delegate, final long sleepMillis) {
            this.delegate = delegate;
            this.sleepMillis = sleepMillis;
        }

        @Override
        public Dimension2D measure(final String displayText, final BoundaryTier tier) {
            final long start = System.nanoTime();
            try {
                Thread.sleep(sleepMillis);
            }
            catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while measuring a hull label", exception);
            }
            measuredNanos += System.nanoTime() - start;
            return delegate.measure(displayText, tier);
        }

        private long measuredNanos() {
            return measuredNanos;
        }
    }

    @Test
    public void translatesBothFreeMapsByHalfTheMinimumTranslation() {
        final CrossMapFixture fixture = crossMapFixture(false, false);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(43.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.firstHull)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.secondHull)).isEqualTo(LayoutPoint.of(43.0, 0.0));
    }

    @Test
    public void movesOnlyTheOtherMapWhenTheFirstMapHasAnActivePin() {
        final CrossMapFixture fixture = crossMapFixture(true, false);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(48.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.firstHull)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.secondHull)).isEqualTo(LayoutPoint.of(48.0, 0.0));
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
    }

    @Test
    public void movesOnlyTheFirstMapWhenTheSecondMapHasAnActivePin() {
        final CrossMapFixture fixture = crossMapFixture(false, true);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(-10.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(38.0, 0.0));
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
    }

    @Test
    public void bothRigidMapsReportOneImmovableConflictWithBothPins() {
        final CrossMapFixture fixture = crossMapFixture(true, true);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions()).isEqualTo(fixture.positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(result.diagnostics().conflicts().get(0).blockingPins()).hasSize(2);
    }

    @Test
    public void dormantPinsDoNotCreateRigidity() {
        final CrossMapFixture fixture = crossMapFixture(false, false);
        final PinProjection dormant = PinProjection.dormant(PinRecord.of(
            fixture.firstNode.source().persistedReference().get(), 0.0, 0.0, Collections.<UnknownXml>emptyList()));
        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, Arrays.asList(dormant));

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(43.0, 0.0));
    }

    @Test
    public void translatesTheWholeFreeMapForANonRootCrossMapPair() {
        final MapReferenceId mapOne = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final MapReferenceId mapTwo = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        final ProjectedNodeKey a = key(mapOne, "a-node");
        final ProjectedNodeKey c = key(mapOne, "c-node");
        final ProjectedNodeKey b = key(mapTwo, "b-node");
        final ProjectedNodeKey d = key(mapTwo, "d-node");
        final EnclosureHullKey rootOne = hull(mapOne, "root");
        final EnclosureHullKey aHull = hull(mapOne, "a");
        final EnclosureHullKey cHull = hull(mapOne, "c");
        final EnclosureHullKey rootTwo = hull(mapTwo, "root");
        final EnclosureHullKey bHull = hull(mapTwo, "b");
        final EnclosureHullKey dHull = hull(mapTwo, "d");
        final GraphProjection projection = projection(Arrays.asList(a, c, b, d), Arrays.asList(
            suppressedRoot(rootOne, Arrays.asList(aHull, cHull)),
            child(aHull, "a", rootOne, Collections.singletonList(a)),
            child(cHull, "c", rootOne, Collections.singletonList(c)),
            suppressedRoot(rootTwo, Arrays.asList(bHull, dHull)),
            child(bHull, "b", rootTwo, Collections.singletonList(b)),
            child(dHull, "d", rootTwo, Collections.singletonList(d))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(a, 0.0, 0.0), nodeEntry(c, 500.0, 0.0), nodeEntry(b, 38.0, 0.0),
                nodeEntry(d, 500.0, 500.0)),
            Arrays.asList(anchorEntry(rootOne, 0.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(cHull, 500.0, 0.0), anchorEntry(rootTwo, 38.0, 0.0), anchorEntry(bHull, 38.0, 0.0),
                anchorEntry(dHull, 500.0, 500.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.positions().nodes().get(a)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(c)).isEqualTo(LayoutPoint.of(495.0, 0.0));
        assertThat(result.positions().nodes().get(b)).isEqualTo(LayoutPoint.of(43.0, 0.0));
        assertThat(result.positions().nodes().get(d)).isEqualTo(LayoutPoint.of(505.0, 500.0));
        assertThat(result.positions().anchors().get(cHull)).isEqualTo(LayoutPoint.of(495.0, 0.0));
        assertThat(result.positions().anchors().get(dHull)).isEqualTo(LayoutPoint.of(505.0, 500.0));
    }

    @Test
    public void accumulatesAllPairDeltasFromTheRoundSnapshot() {
        final List<MapReferenceId> maps = Arrays.asList(
            MapReferenceId.of("00000000-0000-0000-0000-000000000001"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000002"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000003"));
        final double side = 38.0;
        final double height = side * Math.sqrt(3.0) / 2.0;
        final double[][] points = {{0.0, 0.0}, {side, 0.0}, {side / 2.0, height}};
        final List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodeEntries =
            new ArrayList<Map.Entry<ProjectedNodeKey, LayoutPoint>>();
        final List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchorEntries =
            new ArrayList<Map.Entry<EnclosureHullKey, LayoutPoint>>();
        for (int index = 0; index < maps.size(); index++) {
            final ProjectedNodeKey node = key(maps.get(index), "n");
            final EnclosureHullKey root = hull(maps.get(index), "root");
            nodes.add(node);
            enclosures.add(root(root, "root", Collections.singletonList(node)));
            nodeEntries.add(nodeEntry(node, points[index][0], points[index][1]));
            anchorEntries.add(anchorEntry(root, points[index][0], points[index][1]));
        }

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(
            projection(nodes, enclosures), positions(nodeEntries, anchorEntries), METRICS,
            Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(3);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.positions().nodes().get(nodes.get(0)))
            .isEqualTo(LayoutPoint.of(-8.993321412524974, -3.993321412524973));
        assertThat(result.positions().nodes().get(nodes.get(1)))
            .isEqualTo(LayoutPoint.of(46.99332141252498, -3.993321412524976));
        assertThat(result.positions().nodes().get(nodes.get(2)))
            .isEqualTo(LayoutPoint.of(19.0, 40.895608168858615));
        for (int index = 0; index < maps.size(); index++) {
            final ProjectedNodeKey node = nodes.get(index);
            final EnclosureHullKey root = enclosures.get(index).hullKey();
            assertThat(difference(result.positions().nodes().get(node),
                fixtureNodePoint(points[index][0], points[index][1])))
                    .as("map %s translates rigidly", maps.get(index))
                    .isEqualTo(difference(result.positions().anchors().get(root),
                        fixtureAnchorPoint(points[index][0], points[index][1])));
        }
    }

    @Test
    public void reportsOneConflictPerRigidMapPairInViolationOrder() {
        final List<MapReferenceId> maps = Arrays.asList(
            MapReferenceId.of("00000000-0000-0000-0000-000000000001"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000002"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000003"));
        final double side = 38.0;
        final double height = side * Math.sqrt(3.0) / 2.0;
        final double[][] points = {{0.0, 0.0}, {side, 0.0}, {side / 2.0, height}};
        final List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        final List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodeEntries =
            new ArrayList<Map.Entry<ProjectedNodeKey, LayoutPoint>>();
        final List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchorEntries =
            new ArrayList<Map.Entry<EnclosureHullKey, LayoutPoint>>();
        for (int index = 0; index < maps.size(); index++) {
            final ProjectedNodeKey node = key(maps.get(index), "n");
            final EnclosureHullKey root = hull(maps.get(index), "root");
            nodes.add(node);
            enclosures.add(root(root, "root", Collections.singletonList(node)));
            pins.add(pin(node, points[index][0], points[index][1]));
            nodeEntries.add(nodeEntry(node, points[index][0], points[index][1]));
            anchorEntries.add(anchorEntry(root, points[index][0], points[index][1]));
        }
        final LayoutPositions positions = positions(nodeEntries, anchorEntries);
        final GraphProjection projection = projection(nodes, enclosures);

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, pins);

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(3);
        assertThat(result.diagnostics().conflicts()).hasSize(3);
        assertThat(result.diagnostics().conflicts().get(0).pairKey()).isEqualTo(
            CanonicalLayoutKeys.pair(hull(maps.get(1), "root"), hull(maps.get(2), "root")));
        assertThat(result.diagnostics().conflicts().get(1).pairKey()).isEqualTo(
            CanonicalLayoutKeys.pair(hull(maps.get(0), "root"), hull(maps.get(2), "root")));
        assertThat(result.diagnostics().conflicts().get(2).pairKey()).isEqualTo(
            CanonicalLayoutKeys.pair(hull(maps.get(0), "root"), hull(maps.get(1), "root")));
        for (final BoundaryConflict conflict : result.diagnostics().conflicts()) {
            assertThat(conflict.reason()).isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
            assertThat(conflict.blockingPins()).hasSize(2);
        }
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
    }

    private static BoundarySeparationResult apply(CrossMapFixture fixture) {
        return new BoundarySeparationCorrection().apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
    }

    private static CrossMapFixture crossMapFixture(boolean firstPinned, boolean secondPinned) {
        final MapReferenceId mapOne = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final MapReferenceId mapTwo = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        final ProjectedNodeKey firstNode = key(mapOne, "n");
        final ProjectedNodeKey secondNode = key(mapTwo, "n");
        final EnclosureHullKey firstHull = hull(mapOne, "root");
        final EnclosureHullKey secondHull = hull(mapTwo, "root");
        final GraphProjection projection = projection(Arrays.asList(firstNode, secondNode),
            Arrays.asList(root(firstHull, "root", Collections.singletonList(firstNode)),
                root(secondHull, "root", Collections.singletonList(secondNode))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(firstNode, 0.0, 0.0), nodeEntry(secondNode, 38.0, 0.0)),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 38.0, 0.0)));
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        if (firstPinned) {
            pins.add(pin(firstNode, 0.0, 0.0));
        }
        if (secondPinned) {
            pins.add(pin(secondNode, 38.0, 0.0));
        }
        return new CrossMapFixture(projection, positions, pins, firstNode, secondNode, firstHull, secondHull);
    }

    private static ProjectedEnclosure suppressedRoot(EnclosureHullKey hull, List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("root", "root")), "m",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.SUPPRESSED);
    }

    private static ProjectedEnclosure child(EnclosureHullKey hull, String label, EnclosureHullKey parent,
            List<ProjectedNodeKey> nodes) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m", Optional.of(parent), nodes,
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
    }

    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }

    private static LayoutPoint fixtureNodePoint(double x, double y) {
        return LayoutPoint.of(x, y);
    }

    private static LayoutPoint fixtureAnchorPoint(double x, double y) {
        return LayoutPoint.of(x, y);
    }

    private static LayoutPoint difference(LayoutPoint after, LayoutPoint before) {
        return LayoutPoint.of(after.x() - before.x(), after.y() - before.y());
    }

    private static final class CrossMapFixture {
        final GraphProjection projection;
        final LayoutPositions positions;
        final List<PinProjection> pins;
        final ProjectedNodeKey firstNode;
        final ProjectedNodeKey secondNode;
        final EnclosureHullKey firstHull;
        final EnclosureHullKey secondHull;

        CrossMapFixture(GraphProjection projection, LayoutPositions positions, List<PinProjection> pins,
                ProjectedNodeKey firstNode, ProjectedNodeKey secondNode, EnclosureHullKey firstHull,
                EnclosureHullKey secondHull) {
            this.projection = projection;
            this.positions = positions;
            this.pins = pins;
            this.firstNode = firstNode;
            this.secondNode = secondNode;
            this.firstHull = firstHull;
            this.secondHull = secondHull;
        }
    }

    @Test
    public void resolvesTheAttemptFiveBothSidesHalfCase() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -6.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 44.0, 30.0)),
            Arrays.asList(pin(aPin, -6.0, 30.0), pin(bPin, 44.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aFree)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(5.0, 0.0));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(-6.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(44.0, 30.0));
        assertThat(result.positions().nodes().get(aFree)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(bFree)).isEqualTo(LayoutPoint.of(43.0, 0.0));
    }

    @Test
    public void resolvesAPinnedMaximumContributorWithTheSecondSideFullCandidate() {
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final SiblingFixture fixture = siblingFixture(Collections.singletonList(aPin),
            Collections.singletonList(bFree),
            Arrays.asList(nodeEntry(aPin, 0.0, 0.0), nodeEntry(bFree, 38.0, 0.0)),
            Collections.singletonList(pin(aPin, 0.0, 0.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(10.0, 0.0));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(bFree)).isEqualTo(LayoutPoint.of(48.0, 0.0));
        assertThat(result.appliedDisplacements().keySet())
            .doesNotContain(CanonicalLayoutKeys.nodeField(aPin));
    }

    @Test
    public void recursesIntoNestedChildHullsWhenMoving() {
        final ProjectedNodeKey a1Free = key("a1-free");
        final ProjectedNodeKey bFree = key("b-free");
        final GraphProjection projection = nestedProjection(Arrays.asList(a1Free, bFree),
            Collections.singletonList(a1Free), Collections.singletonList(bFree));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(a1Free, 0.0, 0.0), nodeEntry(bFree, 54.0, 0.0)),
            Arrays.asList(anchorEntry(hull("root"), 0.0, 0.0), anchorEntry(hull("a"), 0.0, 0.0),
                anchorEntry(hull("a1"), 0.0, 0.0), anchorEntry(hull("b"), 54.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(a1Free)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(5.0, 0.0));
    }

    @Test
    public void movesAnEmptyEnclosureByItsAnchor() {
        final ProjectedNodeKey aFree = key("a-free");
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Collections.singletonList(aFree),
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                child(aHull, "a", rootHull, Collections.singletonList(aFree)),
                child(bHull, "B", rootHull, Collections.<ProjectedNodeKey>emptyList())));
        final LayoutPositions positions = positions(Collections.singletonList(nodeEntry(aFree, 0.0, 0.0)),
            Arrays.asList(anchorEntry(rootHull, 10.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(bHull, 20.0, 0.0)));
        final HullGeometry rawFirst = new org.freeplane.plugin.graph.geometry.GraphGeometryEngine()
            .computeHulls(projection, positions, METRICS).hulls().get(aHull);
        final HullGeometry rawSecond = new org.freeplane.plugin.graph.geometry.GraphGeometryEngine()
            .computeHulls(projection, positions, METRICS).hulls().get(bHull);
        final LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(rawFirst, rawSecond);

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.anchorField(bHull)))
            .isEqualTo(LayoutPoint.of(translation.x() * 0.5, translation.y() * 0.5));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aFree)))
            .isEqualTo(LayoutPoint.of(-translation.x() * 0.5, -translation.y() * 0.5));
        assertThat(result.positions().anchors().get(bHull)).isEqualTo(LayoutPoint.of(
            20.0 + translation.x() * 0.5, translation.y() * 0.5));
    }

    @Test
    public void movesEveryContributorInTheCapSetByTheSameVector() {
        final ProjectedNodeKey aLow = key("a-low");
        final ProjectedNodeKey aHigh = key("a-high");
        final ProjectedNodeKey bFree = key("b-free");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aLow, aHigh),
            Collections.singletonList(bFree),
            Arrays.asList(nodeEntry(aLow, 0.0, 0.0), nodeEntry(aHigh, 0.0, 10.0), nodeEntry(bFree, 38.0, 0.0)),
            Collections.<PinProjection>emptyList());

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aLow)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aHigh)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
    }

    private static BoundarySeparationResult apply(SiblingFixture fixture) {
        return new BoundarySeparationCorrection().apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
    }

    private static SiblingFixture siblingFixture(List<ProjectedNodeKey> aNodes, List<ProjectedNodeKey> bNodes,
            List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodeEntries, List<PinProjection> pins) {
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final List<ProjectedNodeKey> allNodes = new ArrayList<ProjectedNodeKey>(aNodes);
        allNodes.addAll(bNodes);
        final List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchorEntries =
            new ArrayList<Map.Entry<EnclosureHullKey, LayoutPoint>>();
        anchorEntries.add(anchorEntry(rootHull, 0.0, 0.0));
        anchorEntries.add(anchorEntry(aHull, 0.0, 0.0));
        anchorEntries.add(anchorEntry(bHull, 38.0, 0.0));
        final GraphProjection projection = projection(allNodes,
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                child(aHull, "a", rootHull, aNodes), child(bHull, "b", rootHull, bNodes)));
        return new SiblingFixture(projection, positions(nodeEntries, anchorEntries), pins, aHull, bHull);
    }

    private static GraphProjection nestedProjection(List<ProjectedNodeKey> allNodes,
            List<ProjectedNodeKey> a1Nodes, List<ProjectedNodeKey> bNodes) {
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey a1Hull = hull("a1");
        final EnclosureHullKey bHull = hull("b");
        return projection(allNodes,
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                nestedChild(aHull, "a", rootHull, Collections.singletonList(a1Hull)),
                child(a1Hull, "a1", aHull, a1Nodes), child(bHull, "b", rootHull, bNodes)));
    }

    private static ProjectedEnclosure nestedChild(EnclosureHullKey hull, String label, EnclosureHullKey parent,
            List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m", Optional.of(parent),
            Collections.<ProjectedNodeKey>emptyList(), children, false, BoundaryTier.SUBTLE);
    }

    private static ProjectedEnclosure parent(EnclosureHullKey hull, String label,
            List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.EMPHATIC);
    }

    private static final class SiblingFixture {
        final GraphProjection projection;
        final LayoutPositions positions;
        final List<PinProjection> pins;
        final EnclosureHullKey hullA;
        final EnclosureHullKey hullB;

        SiblingFixture(GraphProjection projection, LayoutPositions positions, List<PinProjection> pins,
                EnclosureHullKey hullA, EnclosureHullKey hullB) {
            this.projection = projection;
            this.positions = positions;
            this.pins = pins;
            this.hullA = hullA;
            this.hullB = hullB;
        }
    }

    @Test
    public void resolvesTheMixedComplementarySplitWithATieMove() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aFree)))
            .isEqualTo(LayoutPoint.of(-3.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(7.0, 0.0));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(-3.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(47.0, 30.0));
        assertThat(result.positions().nodes().get(aFree)).isEqualTo(LayoutPoint.of(-3.0, 0.0));
        assertThat(result.positions().nodes().get(bFree)).isEqualTo(LayoutPoint.of(45.0, 0.0));
    }

    @Test
    public void aTieAtTheAppliedMagnitudeIsValidAndTheTiedPinIsNotDisplaced() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.appliedDisplacements()).containsOnlyKeys(
            CanonicalLayoutKeys.nodeField(aFree), CanonicalLayoutKeys.nodeField(bFree),
            CanonicalLayoutKeys.anchorField(fixture.hullA), CanonicalLayoutKeys.anchorField(fixture.hullB),
            CanonicalLayoutKeys.anchorField(hull("root")));
        assertThat(result.appliedDisplacements().keySet())
            .doesNotContain(CanonicalLayoutKeys.nodeField(aPin))
            .doesNotContain(CanonicalLayoutKeys.nodeField(bPin));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(-3.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(47.0, 30.0));
    }

    @Test
    public void pinnedMaximumContributorsOnBothSidesReportImmovableSides() {
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Collections.singletonList(aPin),
            Collections.singletonList(bPin),
            Arrays.asList(nodeEntry(aPin, 0.0, 30.0), nodeEntry(bPin, 38.0, 30.0)),
            Arrays.asList(pin(aPin, 0.0, 30.0), pin(bPin, 38.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions()).isEqualTo(fixture.positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().boundaryVerified()).isFalse();
        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(result.diagnostics().conflicts().get(0).blockingPins()).hasSize(2);
        assertThat(result.diagnostics().residualHullPairs()).containsExactly(
            result.diagnostics().conflicts().get(0).pairKey());
    }

    @Test
    public void recursiveCapabilityFindsTheNestedPinDepth() {
        final ProjectedNodeKey a1Free = key("a1-free");
        final ProjectedNodeKey a1Pin = key("a1-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey a1Hull = hull("a1");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a1Free, a1Pin, bFree, bPin),
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                nestedChild(aHull, "a", rootHull, Collections.singletonList(a1Hull)),
                child(a1Hull, "a1", aHull, Arrays.asList(a1Free, a1Pin)),
                child(bHull, "b", rootHull, Arrays.asList(bFree, bPin))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(a1Free, 0.0, 0.0), nodeEntry(a1Pin, -3.0, 30.0),
                nodeEntry(bFree, 54.0, 0.0), nodeEntry(bPin, 63.0, 30.0)),
            Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(a1Hull, 0.0, 0.0), anchorEntry(bHull, 54.0, 0.0)));
        final List<PinProjection> pins = Arrays.asList(pin(a1Pin, -3.0, 30.0), pin(bPin, 63.0, 30.0));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, pins);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(a1Free)))
            .isEqualTo(LayoutPoint.of(-3.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(7.0, 0.0));
        assertThat(result.positions().nodes().get(a1Pin)).isEqualTo(LayoutPoint.of(-3.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(63.0, 30.0));
    }

    @Test
    public void repeatedRunsAreDeterministic() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));

        final BoundarySeparationResult first = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);
        final BoundarySeparationResult second = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);

        assertThat(second.positions()).isEqualTo(first.positions());
        assertThat(second.diagnostics().rounds()).isEqualTo(first.diagnostics().rounds());
        assertThat(second.diagnostics().hullViolationsDetected())
            .isEqualTo(first.diagnostics().hullViolationsDetected());
        assertThat(second.diagnostics().hullResidualViolations())
            .isEqualTo(first.diagnostics().hullResidualViolations());
        assertThat(second.diagnostics().appliedDisplacements())
            .isEqualTo(first.diagnostics().appliedDisplacements());
        assertThat(second.diagnostics().conflicts().size()).isEqualTo(first.diagnostics().conflicts().size());
        assertThat(second.diagnostics().residualHullPairs())
            .isEqualTo(first.diagnostics().residualHullPairs());
    }

    @Test
    public void roundLimitCoverageRecomputesReasonsOnTheFinalResidualSet() {
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final EnclosureHullKey cHull = hull("c");
        final ProjectedNodeKey aNode = key("a-node");
        final ProjectedNodeKey bNode = key("b-node");
        final ProjectedNodeKey cNode = key("c-node");
        final GraphProjection projection = projection(Arrays.asList(aNode, bNode, cNode),
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull, cHull)),
                child(aHull, "a", rootHull, Collections.singletonList(aNode)),
                child(bHull, "b", rootHull, Collections.singletonList(bNode)),
                child(cHull, "c", rootHull, Collections.singletonList(cNode))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(aNode, 0.0, 0.0), nodeEntry(bNode, 38.0, 0.0), nodeEntry(cNode, 76.0, 0.0)),
            Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(bHull, 38.0, 0.0), anchorEntry(cHull, 76.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection(1).apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(2);
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(2);
        assertThat(result.diagnostics().boundaryVerified()).isFalse();
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().conflicts()).hasSize(2);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.ROUND_LIMIT);
        assertThat(result.diagnostics().conflicts().get(1).reason())
            .isEqualTo(BoundaryConflict.Reason.ROUND_LIMIT);
        assertThat(result.diagnostics().residualHullPairs()).containsExactly(
            result.diagnostics().conflicts().get(0).pairKey(),
            result.diagnostics().conflicts().get(1).pairKey());
        assertThat(result.positions().nodes().get(aNode)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(bNode)).isEqualTo(LayoutPoint.of(38.0, 0.0));
        assertThat(result.positions().nodes().get(cNode)).isEqualTo(LayoutPoint.of(81.0, 0.0));
    }

    private static final MapReferenceId FROZEN_MAP =
        MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final String FROZEN_REGULARITY = "ID_1133378501";
    private static final String FROZEN_REPLACEMENT = "ID_822182441";
    private static final String FROZEN_CHOICE = "ID_130337169";
    private static final String FROZEN_PINNED_THEOREM = "ID_1901523076";
    private static final String FROZEN_FREE_THEOREM = "ID_1387156674";
    private static final String FROZEN_SUPPRESSED_ROOT = "ID_435635462";
    private static final String FROZEN_ZFC = "ID_1675547143";
    private static final String FROZEN_AXIOMS = "ID_1912952190";
    private static final String FROZEN_DEFINITIONS = "ID_978732953";
    private static final double FROZEN_MST_X = -13.548086052416210;
    private static final double FROZEN_REGULARITY_DELTA_X = 13.54808605241621;

    @Test
    public void frozenRealCaseViolatesBeforeAndSettlesInOneRoundAfterCorrection() {
        final FrozenFixture fixture = frozenFixture();

        assertThat(fixture.projection.prominence().get(fixture.regularity).visibleOutgoingTargets())
            .isEqualTo(2);
        assertThat(fixture.projection.prominence().get(fixture.replacement).visibleOutgoingTargets())
            .isEqualTo(0);
        final GraphGeometryEngine engine = new GraphGeometryEngine();
        final HullGeometry rawAxioms = engine.computeHulls(fixture.projection, fixture.positions, METRICS)
            .hulls().get(fixture.axiomsHull);
        final HullGeometry rawDefinitions = engine.computeHulls(fixture.projection, fixture.positions, METRICS)
            .hulls().get(fixture.definitionsHull);
        assertThat(HullIntersection.siblingOverlap(rawAxioms, rawDefinitions)).isTrue();
        assertThat(HullIntersection.minimumSeparatingTranslation(rawAxioms, rawDefinitions))
            .isEqualTo(LayoutPoint.of(FROZEN_MST_X, 0.0));
        assertThat(containsAll(rawAxioms, rawDefinitions)).isFalse();
        assertThat(containsAll(rawDefinitions, rawAxioms)).isFalse();

        final GraphGeometry rawGeometry = engine.computeHulls(fixture.projection, fixture.positions, METRICS);
        assertThat(containsAll(rawGeometry.hulls().get(fixture.zfcHull), rawAxioms)).isTrue();
        assertThat(containsAll(rawGeometry.hulls().get(fixture.zfcHull), rawDefinitions)).isTrue();

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().hullViolationsDetected()).isGreaterThanOrEqualTo(1);
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().conflicts()).isEmpty();
        assertThat(result.diagnostics().residualHullPairs()).isEmpty();
        assertThat(result.positions().nodes().get(fixture.choice))
            .isEqualTo(LayoutPoint.of(-24.832420395427746, -34.920469854404410));
        assertThat(result.positions().nodes().get(fixture.pinnedTheorem))
            .isEqualTo(LayoutPoint.of(-209.31397564145126, 9.820904009249132));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(fixture.regularity)))
            .isEqualTo(LayoutPoint.of(FROZEN_REGULARITY_DELTA_X, 0.0));
        assertThat(result.appliedDisplacements().keySet())
            .doesNotContain(CanonicalLayoutKeys.nodeField(fixture.choice))
            .doesNotContain(CanonicalLayoutKeys.nodeField(fixture.pinnedTheorem));

        final GraphGeometry correctedGeometry = engine.computeHulls(fixture.projection, result.positions(),
            METRICS);
        assertThat(HullIntersection.siblingOverlap(correctedGeometry.hulls().get(fixture.axiomsHull),
            correctedGeometry.hulls().get(fixture.definitionsHull))).isFalse();
        assertThat(HullIntersection.minimumSeparatingTranslation(
            correctedGeometry.hulls().get(fixture.axiomsHull),
            correctedGeometry.hulls().get(fixture.definitionsHull))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(containsAll(correctedGeometry.hulls().get(fixture.zfcHull),
            correctedGeometry.hulls().get(fixture.axiomsHull))).isTrue();
        assertThat(containsAll(correctedGeometry.hulls().get(fixture.zfcHull),
            correctedGeometry.hulls().get(fixture.definitionsHull))).isTrue();

        final LayoutPoint regularityDelta = LayoutPoint.of(FROZEN_REGULARITY_DELTA_X, 0.0);
        assertThat(result.positions().anchors().get(fixture.axiomsHull)).isEqualTo(regularityDelta);
        assertThat(result.positions().anchors().get(fixture.zfcHull)).isEqualTo(regularityDelta);
        assertThat(result.positions().anchors().get(fixture.rootHull)).isEqualTo(regularityDelta);
        assertThat(result.positions().anchors().get(fixture.definitionsHull))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));

        final BoundarySeparationResult second = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);
        assertThat(second.positions()).isEqualTo(result.positions());
        assertThat(second.diagnostics().rounds()).isEqualTo(result.diagnostics().rounds());
        assertThat(second.diagnostics().hullResidualViolations())
            .isEqualTo(result.diagnostics().hullResidualViolations());
        assertThat(second.diagnostics().conflicts()).hasSize(result.diagnostics().conflicts().size());
    }

    @Test
    public void canonicalKeysUseTheCapturedRealHullStrings() {
        assertThat(CanonicalLayoutKeys.hull(hull(FROZEN_MAP, FROZEN_AXIOMS)))
            .isEqualTo("m:" + FROZEN_MAP.value() + "|p:" + FROZEN_AXIOMS);
        assertThat(CanonicalLayoutKeys.pair(hull(FROZEN_MAP, FROZEN_AXIOMS),
            hull(FROZEN_MAP, FROZEN_DEFINITIONS)))
                .isEqualTo("m:" + FROZEN_MAP.value() + "|p:" + FROZEN_AXIOMS + "|m:" + FROZEN_MAP.value()
                    + "|p:" + FROZEN_DEFINITIONS);
    }

    private static boolean containsAll(HullGeometry outer, HullGeometry inner) {
        for (final LayoutPoint vertex : inner.exactPolygon()) {
            if (!outer.contains(vertex)) {
                return false;
            }
        }
        return true;
    }

    private static FrozenFixture frozenFixture() {
        final ProjectedNodeKey regularity = frozenKey(FROZEN_REGULARITY);
        final ProjectedNodeKey replacement = frozenKey(FROZEN_REPLACEMENT);
        final ProjectedNodeKey choice = frozenKey(FROZEN_CHOICE);
        final ProjectedNodeKey pinnedTheorem = frozenKey(FROZEN_PINNED_THEOREM);
        final ProjectedNodeKey freeTheorem = frozenKey(FROZEN_FREE_THEOREM);
        final EnclosureHullKey rootHull = hull(FROZEN_MAP, FROZEN_SUPPRESSED_ROOT);
        final EnclosureHullKey zfcHull = hull(FROZEN_MAP, FROZEN_ZFC);
        final EnclosureHullKey axiomsHull = hull(FROZEN_MAP, FROZEN_AXIOMS);
        final EnclosureHullKey definitionsHull = hull(FROZEN_MAP, FROZEN_DEFINITIONS);
        final List<ProjectedNode> nodes = Arrays.asList(
            frozenNode(regularity, "Fundation / Regularity"), frozenNode(replacement, "Replacement Scheme"),
            frozenNode(choice, "Axiom of Choice"), frozenNode(pinnedTheorem, "Theorem"),
            frozenNode(freeTheorem, "Theorem"));
        final List<ProjectedEnclosure> enclosures = Arrays.asList(
            frozenEnclosure(rootHull, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(zfcHull), true,
                BoundaryTier.SUPPRESSED),
            frozenEnclosure(zfcHull, "ZFC", Optional.of(rootHull), Collections.<ProjectedNodeKey>emptyList(),
                Arrays.asList(axiomsHull, definitionsHull), false, BoundaryTier.EMPHATIC),
            frozenEnclosure(axiomsHull, "Axioms", Optional.of(zfcHull),
                Arrays.asList(regularity, replacement, choice), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE),
            frozenEnclosure(definitionsHull, "Basic Definitions and Theorems", Optional.of(zfcHull),
                Arrays.asList(pinnedTheorem, freeTheorem), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
        final List<ProjectedEdge> edges = Arrays.asList(frozenEdge(regularity, freeTheorem, 0),
            frozenEdge(regularity, pinnedTheorem, 1));
        final PinProjection choicePin = pin(choice, -24.832420395427746, -34.920469854404410);
        final PinProjection theoremPin = pin(pinnedTheorem, -209.31397564145126, 9.820904009249132);
        final List<PinProjection> pins = Arrays.asList(choicePin, theoremPin);
        final GraphProjection projection = GraphProjection.projected(1L, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), pins);
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(regularity, -173.26206169386748, 1.8301628933959680),
                nodeEntry(replacement, 248.60834750232004, -59.339657536064465),
                nodeEntry(choice, -24.832420395427746, -34.920469854404410),
                nodeEntry(pinnedTheorem, -209.31397564145126, 9.820904009249132),
                nodeEntry(freeTheorem, -241.79904871734790, 14.381230674273278)),
            Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(zfcHull, 0.0, 0.0),
                anchorEntry(axiomsHull, 0.0, 0.0), anchorEntry(definitionsHull, 0.0, 0.0)));
        return new FrozenFixture(projection, positions, pins, regularity, replacement, choice, pinnedTheorem,
            freeTheorem, rootHull, zfcHull, axiomsHull, definitionsHull);
    }

    private static ProjectedNode frozenNode(ProjectedNodeKey key, String label) {
        return ProjectedNode.of(key, SafeNodeLabel.of(label, label), "map", false);
    }

    private static ProjectedEnclosure frozenEnclosure(EnclosureHullKey hull, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> nodes,
            List<EnclosureHullKey> children, boolean mapRoot, BoundaryTier tier) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "map", parent, nodes, children, mapRoot,
            tier);
    }

    private static ProjectedEdge frozenEdge(ProjectedNodeKey source, ProjectedNodeKey target, int occurrence) {
        final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(source);
        final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(target);
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(source.source(),
            target.source().persistedReference().get(), false, true, "", "", "");
        final EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(occurrence, descriptor), first, second);
        return ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(contributor));
    }

    private static ProjectedNodeKey frozenKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(FROZEN_MAP, PersistedNodeId.of(id))));
    }

    private static final class FrozenFixture {
        final GraphProjection projection;
        final LayoutPositions positions;
        final List<PinProjection> pins;
        final ProjectedNodeKey regularity;
        final ProjectedNodeKey replacement;
        final ProjectedNodeKey choice;
        final ProjectedNodeKey pinnedTheorem;
        final ProjectedNodeKey freeTheorem;
        final EnclosureHullKey rootHull;
        final EnclosureHullKey zfcHull;
        final EnclosureHullKey axiomsHull;
        final EnclosureHullKey definitionsHull;

        FrozenFixture(GraphProjection projection, LayoutPositions positions, List<PinProjection> pins,
                ProjectedNodeKey regularity, ProjectedNodeKey replacement, ProjectedNodeKey choice,
                ProjectedNodeKey pinnedTheorem, ProjectedNodeKey freeTheorem, EnclosureHullKey rootHull,
                EnclosureHullKey zfcHull, EnclosureHullKey axiomsHull, EnclosureHullKey definitionsHull) {
            this.projection = projection;
            this.positions = positions;
            this.pins = pins;
            this.regularity = regularity;
            this.replacement = replacement;
            this.choice = choice;
            this.pinnedTheorem = pinnedTheorem;
            this.freeTheorem = freeTheorem;
            this.rootHull = rootHull;
            this.zfcHull = zfcHull;
            this.axiomsHull = axiomsHull;
            this.definitionsHull = definitionsHull;
        }
    }

    @Test
    public void ancestorContainmentIsMemoizedAcrossIdenticalFrames() {
        final FrozenFixture fixture = frozenFixture();
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        final BoundarySeparationResult first = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
        final long checksAfterFirstFrame = correction.ancestorExactContainmentChecks();

        final BoundarySeparationResult second = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);

        assertThat(checksAfterFirstFrame).isGreaterThan(0);
        assertThat(correction.ancestorExactContainmentChecks()).isEqualTo(checksAfterFirstFrame);
        assertThat(correction.ancestorContainmentMemoHits()).isGreaterThan(0);
        assertThat(second.positions()).isEqualTo(first.positions());
        assertThat(second.diagnostics().rounds()).isEqualTo(first.diagnostics().rounds());
        assertThat(second.diagnostics().hullResidualViolations())
            .isEqualTo(first.diagnostics().hullResidualViolations());
        assertThat(second.diagnostics().conflicts().size()).isEqualTo(first.diagnostics().conflicts().size());
        assertThat(second.diagnostics().residualHullPairs())
            .isEqualTo(first.diagnostics().residualHullPairs());
    }

    @Test
    public void ancestorContainmentMemoRechecksAHullWhoseGeometryChanged() {
        final ProjectedNodeKey childNode = key("memo-child-node");
        final EnclosureHullKey rootHull = hull("memo-root");
        final EnclosureHullKey childHull = hull("memo-child");
        final GraphProjection projection = projection(Collections.singletonList(childNode),
            Arrays.asList(parent(rootHull, "root", Collections.singletonList(childHull)),
                child(childHull, "child", rootHull, Collections.singletonList(childNode))));
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        correction.apply(projection,
            positions(Collections.singletonList(nodeEntry(childNode, 0.0, 0.0)),
                Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(childHull, 0.0, 0.0))),
            METRICS, Collections.<PinProjection>emptyList());
        final long checksAfterFirstFrame = correction.ancestorExactContainmentChecks();

        correction.apply(projection,
            positions(Collections.singletonList(nodeEntry(childNode, 5.0, 0.0)),
                Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(childHull, 0.0, 0.0))),
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(checksAfterFirstFrame).isEqualTo(1);
        assertThat(correction.ancestorExactContainmentChecks()).isEqualTo(2);
        assertThat(correction.ancestorContainmentMemoHits()).isZero();
    }

    @Test
    public void capSetsAreReusedWithinAFrame() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        final BoundarySeparationResult result = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
        final BoundarySeparationResult fresh = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(correction.capCacheHits()).isGreaterThan(0);
        assertThat(result.positions()).isEqualTo(fresh.positions());
        assertThat(result.appliedDisplacements()).isEqualTo(fresh.appliedDisplacements());
    }

    @Test
    public void candidateSelectionStopsWhenNoSideHasAMovableContributor() {
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Collections.singletonList(aPin),
            Collections.singletonList(bPin),
            Arrays.asList(nodeEntry(aPin, 0.0, 30.0), nodeEntry(bPin, 38.0, 30.0)),
            Arrays.asList(pin(aPin, 0.0, 30.0), pin(bPin, 38.0, 30.0)));
        final BoundarySeparationCorrection correction = new BoundarySeparationCorrection();

        final BoundarySeparationResult result = correction.apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);

        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(correction.capTraversals()).isPositive();
        // Two plan passes (in-loop and terminal) traverse at most one cap per side. Assert the bound
        // and the mechanism rather than the absolute count so the test does not couple to the number
        // of plan invocations.
        assertThat(correction.capTraversals()).isLessThanOrEqualTo(4L);
        assertThat(correction.capCacheHits()).isZero();
    }
}
