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
import java.util.Set;
import java.util.TreeSet;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
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

public class BoundaryInvariantsShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(
        new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

    @Test
    public void detectMatchesTheBoundaryStageResidualPairSet() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey b = nodeKey("b");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a, b), Arrays.asList(
            enclosure(aHull, Collections.singletonList(a), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(b), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(a, 0.0, 0.0, b, 38.0, 0.0),
            anchorMap(aHull, 0.0, 0.0, bHull, 38.0, 0.0));
        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection,
            positions, METRICS, Collections.<PinProjection>emptyList());
        final BoundaryInvariants invariants = new BoundaryInvariants();

        final List<BoundaryInvariants.Violation> detected = invariants.detect(projection,
            new GraphGeometryEngine().computeHulls(projection, result.positions(), METRICS));

        final List<String> detectedKeys = new ArrayList<String>();
        for (final BoundaryInvariants.Violation violation : detected) {
            detectedKeys.add(violation.pairKey());
        }
        assertThat(detectedKeys).isEqualTo(result.diagnostics().residualHullPairs());
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
    }

    @Test
    public void ancestorMemoAndCountersPersistAcrossDetectCalls() {
        final ProjectedNodeKey childNode = nodeKey("child");
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey childHull = hull("child");
        final GraphProjection projection = projection(Collections.singletonList(childNode),
            Arrays.asList(parent(rootHull, "root", Collections.singletonList(childHull)),
                ProjectedEnclosure.of(childHull, childHull.endpointKeys(),
                    Collections.singletonList(SafeNodeLabel.of("e", "e")), "map",
                    Optional.of(rootHull), Collections.singletonList(childNode),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(childNode, 0.0, 0.0),
            anchorMap(rootHull, 0.0, 0.0, childHull, 0.0, 0.0));
        final BoundaryInvariants invariants = new BoundaryInvariants();
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        invariants.detect(projection, geometry);
        final long checksAfterFirst = invariants.ancestorExactContainmentChecks();
        invariants.detect(projection, geometry);

        assertThat(checksAfterFirst).isGreaterThan(0);
        assertThat(invariants.ancestorExactContainmentChecks()).isEqualTo(checksAfterFirst);
        assertThat(invariants.ancestorContainmentMemoHits()).isGreaterThan(0);
    }

    @Test
    public void terminalClassificationAccumulatesTheFrameCapCounters() {
        final ProjectedNodeKey aPin = nodeKey("a-pin");
        final ProjectedNodeKey bPin = nodeKey("b-pin");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(aPin, bPin), Arrays.asList(
            enclosure(aHull, Collections.singletonList(aPin), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(bPin), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(aPin, 0.0, 30.0, bPin, 38.0, 30.0),
            anchorMap(aHull, 0.0, 30.0, bHull, 38.0, 30.0));
        final BoundaryInvariants invariants = new BoundaryInvariants();
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        final List<BoundaryConflict> conflicts = invariants.terminalConflicts(projection, positions,
            METRICS, Collections.<PinProjection>emptyList(), geometry,
            invariants.detect(projection, geometry), false);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).reason()).isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(invariants.capTraversals()).isPositive();
        // One classification pass re-queries cap keys (the pin-depth rule re-reads the full magnitude),
        // so the frame cap cache reports reuse within the pass as well.
        assertThat(invariants.capCacheHits()).isPositive();
    }

    @Test
    public void describePreservesStageFieldsAndRecomputesResiduals() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey b = nodeKey("b");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a, b), Arrays.asList(
            enclosure(aHull, Collections.singletonList(a), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(b), BoundaryTier.EMPHATIC)));
        // The overlapping 38.0 fixture rebuilds a non-empty terminal pair set, so a regression that
        // re-emits the stage baseline instead of the rebuilt record cannot pass by accident.
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(a, 0.0, 0.0, b, 38.0, 0.0),
            anchorMap(aHull, 0.0, 0.0, bHull, 38.0, 0.0));
        final BoundaryInvariants invariants = new BoundaryInvariants();
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        final List<String> rebuiltPairKeys = new ArrayList<String>(
            new TreeSet<String>(invariants.detectPairKeys(projection, geometry)));
        final int rebuiltViolations = invariants.detect(projection, geometry).size();
        assertThat(rebuiltPairKeys).isNotEmpty();
        assertThat(rebuiltViolations).isPositive();
        // The boundary baseline is a superset of the rebuilt record: it still carries one stale pair
        // that the rebuilt terminal geometry no longer detects.
        final List<String> boundaryBaselinePairs = new ArrayList<String>(rebuiltPairKeys);
        boundaryBaselinePairs.add(CanonicalKeys.hullPair(hull("stale-left"), hull("stale-right")));
        final BoundarySeparationDiagnostics stageBaseline = new BoundarySeparationDiagnostics(
            Collections.<BoundaryConflict>emptyList(), boundaryBaselinePairs, 7, 9, 3,
            1.5, 2.5, Collections.<String, LayoutPoint>emptyMap(), 0.5, 0.75);

        final BoundarySeparationDiagnostics rebuilt = invariants.describe(projection,
            positions, METRICS, Collections.<PinProjection>emptyList(), geometry,
            boundaryBaselinePairs, stageBaseline);

        assertThat(rebuilt.hullViolationsDetected()).isEqualTo(7);
        assertThat(rebuilt.hullResidualViolations()).isEqualTo(rebuiltViolations);
        assertThat(rebuilt.rounds()).isEqualTo(3);
        assertThat(rebuilt.displacementRms()).isEqualTo(1.5);
        assertThat(rebuilt.displacementMax()).isEqualTo(2.5);
        assertThat(rebuilt.deltaRms()).isEqualTo(0.5);
        assertThat(rebuilt.deltaMax()).isEqualTo(0.75);
        assertThat(rebuilt.appliedDisplacements()).isEmpty();
        assertThat(rebuilt.residualHullPairs()).isEqualTo(rebuiltPairKeys);
        assertThat(rebuilt.residualHullPairs()).isSorted();
    }

    @Test
    public void checksTheRebuildAllowanceBeforeEveryHullComparison() {
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final EnclosureHullKey cHull = hull("c");
        final GraphProjection projection = projection(Collections.<ProjectedNodeKey>emptyList(),
            Arrays.asList(enclosure(aHull, Collections.<ProjectedNodeKey>emptyList(), BoundaryTier.EMPHATIC),
                enclosure(bHull, Collections.<ProjectedNodeKey>emptyList(), BoundaryTier.EMPHATIC),
                enclosure(cHull, Collections.<ProjectedNodeKey>emptyList(), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            anchorMap(aHull, 0.0, 0.0, bHull, 0.0, 500.0, cHull, 0.0, 1000.0));
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        final BoundarySeparationDiagnostics baseline = BoundarySeparationDiagnostics.empty();
        for (int allowance = 0; allowance < 3; allowance++) {
            final BoundaryInvariants.CountedRebuild rebuilt = new BoundaryInvariants().describeCounted(
                projection, positions, METRICS, Collections.<PinProjection>emptyList(), geometry,
                Collections.<String>emptyList(), baseline, allowance);

            assertThat(rebuilt.truncated()).isTrue();
            assertThat(rebuilt.hullPairWork()).isEqualTo(allowance);
            assertThat(rebuilt.diagnostics()).isSameAs(baseline);
        }
        final BoundaryInvariants.CountedRebuild complete = new BoundaryInvariants().describeCounted(
            projection, positions, METRICS, Collections.<PinProjection>emptyList(), geometry,
            Collections.<String>emptyList(), baseline, 3L);
        assertThat(complete.truncated()).isFalse();
        assertThat(complete.hullPairWork()).isEqualTo(3L);
        assertThat(complete.diagnostics()).isNotSameAs(baseline);
    }

    @Test
    public void describeRejectsAPairAbsentFromTheBaseline() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey b = nodeKey("b");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a, b), Arrays.asList(
            enclosure(aHull, Collections.singletonList(a), BoundaryTier.EMPHATIC),
            enclosure(bHull, Collections.singletonList(b), BoundaryTier.EMPHATIC)));
        final LayoutPositions positions = LayoutPositions.of(
            pointMap(a, 0.0, 0.0, b, 38.0, 0.0),
            anchorMap(aHull, 0.0, 0.0, bHull, 38.0, 0.0));
        final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positions, METRICS);

        assertThatThrownBy(() -> new BoundaryInvariants().describe(projection, positions, METRICS,
            Collections.<PinProjection>emptyList(), geometry, Collections.<String>emptyList(),
            BoundarySeparationDiagnostics.empty()))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("baseline");
    }

    @Test
    public void detectsAncestorEscapeInTheCandidatePairSet() {
        // Enforced HR/HC: HR is the square [-10,10]x[-10,10], HC starts as [-1,1]x[-1,1] and the
        // candidate HC is [19,21]x[-1,1], leaving HR unchanged. These are explicit supplied hulls
        // that test the detector directly; they are not claimed to be production-generated parent
        // geometry, because a consistent production parent would expand around its child.
        final EnclosureKey parentKey = EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of("HR"))));
        final EnclosureKey childKey = EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of("HC"))));
        final EnclosureHullKey parentHull = EnclosureHullKey.of(Collections.singletonList(parentKey));
        final EnclosureHullKey childHull = EnclosureHullKey.of(Collections.singletonList(childKey));
        final ProjectedEnclosure parent = ProjectedEnclosure.of(parentHull, parentHull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("HR", "HR")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.singletonList(childHull), true, BoundaryTier.EMPHATIC);
        final ProjectedEnclosure child = ProjectedEnclosure.of(childHull, childHull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("HC", "HC")), "map",
            Optional.of(parentHull), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        final GraphProjection projection = projection(Collections.<ProjectedNodeKey>emptyList(),
            Arrays.asList(parent, child));
        final BoundaryInvariants invariants = new BoundaryInvariants();

        final Map<EnclosureHullKey, HullGeometry> contained =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        contained.put(parentHull, square(0.0, 0.0, 10.0));
        contained.put(childHull, square(0.0, 0.0, 1.0));
        final GraphGeometry baseline = GraphGeometry.of(
            Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), contained);

        final Map<EnclosureHullKey, HullGeometry> escaped =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        escaped.put(parentHull, square(0.0, 0.0, 10.0));
        escaped.put(childHull, square(20.0, 0.0, 1.0));
        final GraphGeometry candidate = GraphGeometry.of(
            Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), escaped);

        assertThat(invariants.detectPairKeys(projection, baseline)).isEmpty();
        assertThat(invariants.detect(projection, baseline)).isEmpty();
        final Set<String> candidatePairs = invariants.detectPairKeys(projection, candidate);
        assertThat(candidatePairs).containsExactly(CanonicalKeys.pair(parentHull, childHull));
        final List<BoundaryInvariants.Violation> violations = invariants.detect(projection, candidate);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).kind()).isEqualTo(BoundaryConflict.Kind.ANCESTOR_ESCAPE);
        assertThat(violations.get(0).pairKey()).isEqualTo(CanonicalKeys.pair(parentHull, childHull));
        // The endpoints are the canonical-sorted hull pair of the escape, in either order.
        assertThat(Arrays.asList(violations.get(0).first(), violations.get(0).second()))
            .containsExactlyInAnyOrder(parentHull, childHull);
        // The identity is not a sibling pair, so no sibling classification is reported for it.
        assertThat(violations.get(0).kind()).isNotEqualTo(BoundaryConflict.Kind.SIBLING_CROSSING);
        assertThat(violations.get(0).kind()).isNotEqualTo(BoundaryConflict.Kind.SIBLING_CONTAINMENT);
    }

    private static HullGeometry square(final double centerX, final double centerY,
            final double halfExtent) {
        return HullGeometry.of(Arrays.asList(
            LayoutPoint.of(centerX - halfExtent, centerY - halfExtent),
            LayoutPoint.of(centerX + halfExtent, centerY - halfExtent),
            LayoutPoint.of(centerX + halfExtent, centerY + halfExtent),
            LayoutPoint.of(centerX - halfExtent, centerY + halfExtent)),
            LayoutPoint.of(centerX, centerY));
    }

    private static GraphProjection projection(final List<ProjectedNodeKey> nodeKeys,
            final List<ProjectedEnclosure> enclosures) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : nodeKeys) {
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(key.source().toString(), key.source().toString()),
                "map", false));
        }
        return GraphProjection.projected(1L, nodes, enclosures, Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hull,
            final List<ProjectedNodeKey> directNodes, final BoundaryTier tier) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("e", "e")), "map", Optional.<EnclosureHullKey>empty(),
            directNodes, Collections.<EnclosureHullKey>emptyList(), false, tier);
    }

    private static ProjectedEnclosure parent(final EnclosureHullKey hull, final String label,
            final List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.EMPHATIC);
    }

    private static Map<ProjectedNodeKey, LayoutPoint> pointMap(final Object... values) {
        final Map<ProjectedNodeKey, LayoutPoint> result = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((ProjectedNodeKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }

    private static Map<EnclosureHullKey, LayoutPoint> anchorMap(final Object... values) {
        final Map<EnclosureHullKey, LayoutPoint> result = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((EnclosureHullKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(final String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }
}
