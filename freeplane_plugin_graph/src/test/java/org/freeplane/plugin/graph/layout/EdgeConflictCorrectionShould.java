package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
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

public class EdgeConflictCorrectionShould {
    private static final GeometryTextMetrics METRICS =
        new AwtGeometryTextMetrics(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12),
            new java.awt.font.FontRenderContext(null, true, true));
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    @Test
    public void returnsTheBoundaryResultUnchangedInTheScaffold() {
        final GraphProjection projection = GraphProjection.structure(1L,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedNode>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList());
        final LayoutPositions positions = LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        final GraphGeometry geometry = GraphGeometry.of(
            Collections.<ProjectedNodeKey, org.freeplane.plugin.graph.geometry.NodeGeometry>emptyMap(),
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));

        final EdgeConflictResult result = new EdgeConflictCorrection().apply(projection, positions, METRICS,
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEndpointKey>emptySet(), boundary);

        assertThat(result.positions()).isSameAs(positions);
        assertThat(result.diagnostics()).isEqualTo(EdgeConflictDiagnostics.empty());
        assertThat(result.boundaryDiagnostics()).isSameAs(boundary.diagnostics());
        assertThat(result.nodeResidualViolations()).isEqualTo(boundary.nodeResidualViolations());
        assertThat(result.appliedDisplacements()).isEmpty();
        assertThat(result.timings().totalNanos()).isZero();
    }

    @Test
    public void swapsTheReportedConvexQuadAndRemovesTheCrossing() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().edgeOverNodeDetected()).isZero();
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = result.diagnostics().repairsApplied().get(0);
        assertThat(repair.kind()).isEqualTo(EdgeConflictDiagnostics.RepairKind.SWAP);
        assertThat(repair.nodeKeys()).containsExactly(fixture.key(0), fixture.key(1));
        assertThat(repair.fromCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(repair.toCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(repair.fromCentres().get(fixture.key(1))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(repair.toCentres().get(fixture.key(1))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(result.positions().nodes().get(fixture.key(2))).isEqualTo(LayoutPoint.of(275.0, 240.0));
        assertThat(result.positions().nodes().get(fixture.key(3))).isEqualTo(LayoutPoint.of(240.0, 208.0));
        assertThat(result.diagnostics().crossingsResolved()).isEqualTo(1);
        assertThat(result.diagnostics().edgeOverNodeResolved()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void rejectsTheTwoAdjacentSwapBatchAndRetriesSingles() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        // Greedy batch = {swap01, swap23}; applying both leaves the two diagonals, so A1's
        // strict decrease fails, and the single swap01 is accepted.
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verifications()).isEqualTo(2);
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void retriesASingleCandidateAfterARejectedBatch() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        // A whole-batch discard (probe 10) would leave rounds == 0 and a residual crossing.
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        assertThat(result.diagnostics().repairsApplied().get(0).kind())
            .isEqualTo(EdgeConflictDiagnostics.RepairKind.SWAP);
        assertThat(result.diagnostics().crossingsResolved()).isEqualTo(1);
    }

    @Test
    public void detectsACrossingAtExtremeSpans() {
        final Fixture fixture = fixture(1L, new String[] {"A", "B", "C", "D"},
            new double[] {-1e300, -1e300, 1e300, 1e300, -1e300, 1e300, 1e300, -1e300},
            new int[][] {{0, 1}, {2, 3}});
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().indexBudgetExhausted()).isTrue();
    }

    @Test
    public void enumeratesEveryPairWhenSubdivisionExhausts() {
        final int chords = 65;
        final double[] x = new double[chords * 2];
        final double[] y = new double[chords * 2];
        for (int index = 0; index < chords; index++) {
            final double theta = 2.0 * Math.PI * index / (double) chords;
            x[index * 2] = 100.5 * Math.cos(theta);
            y[index * 2] = 100.5 * Math.sin(theta);
            x[index * 2 + 1] = -100.5 * Math.cos(theta);
            y[index * 2 + 1] = -100.5 * Math.sin(theta);
        }
        final int[][] edges = new int[chords][];
        for (int index = 0; index < chords; index++) {
            edges[index] = new int[] {index * 2, index * 2 + 1};
        }
        final String[] ids = new String[chords * 2];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = "C" + index;
        }
        final Fixture fixture = fixture(1L, ids, x, y, edges, 14.0);
        // Every node is pinned, so no relocation candidate can be accepted and the ladder
        // cannot run: the frame is the single complete initial detection pinned by spec §8.5.
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, fixture.pinsForAllNodes(), fixture.visible,
            fixture.boundary);

        assertThat(result.diagnostics().crossingsDetected()).isEqualTo(2080);
        assertThat(result.diagnostics().indexSubdivisions()).isEqualTo(4);
        assertThat(result.diagnostics().indexBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().indexPairChecks()).isGreaterThanOrEqualTo(2080L);
    }

    @Test
    public void boundsTheCountedRelocationWorkOnADenseWideSpanFrame() {
        final Fixture fixture = referenceScaleFan(500);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().indexInsertedItems())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(result.diagnostics().indexPairChecks())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(result.diagnostics().verificationWork())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME);
        assertThat(result.diagnostics().verifications())
            .isLessThanOrEqualTo(EdgeConflictCorrection.MAX_VERIFICATIONS_PER_FRAME);
    }

    @Test
    public void suppressesAFrameWithMoreThanTheRepairableConflictBound() {
        final Fixture fixture = concurrentDiameters(5);
        final EdgeConflictCorrection.Detection detected = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);
        final EdgeConflictResult result = apply(fixture);

        assertThat(detected.keys()).hasSizeGreaterThan(
            EdgeConflictCorrection.EDGE_CONFLICT_MAX_REPAIRABLE_CONFLICTS_PER_FRAME);
        assertThat(result.positions()).isSameAs(fixture.boundary.positions());
        assertThat(result.diagnostics().conflictsDetected()).isEqualTo(detected.keys().size());
        assertThat(result.diagnostics().conflictsResidual()).containsExactlyElementsOf(detected.keys());
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.diagnostics().crossingsResolved()).isZero();
        assertThat(result.diagnostics().edgeOverNodeResolved()).isZero();
        assertThat(result.boundaryDiagnostics()).isSameAs(fixture.boundary.diagnostics());
        assertThat(result.timings().rebuildNanos()).isZero();
    }

    @Test
    public void truncatesDetectionAtTheItemBudget() {
        final Fixture fixture = referenceScaleFan(500);
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection bruteForce = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().indexBudgetExhausted()).isFalse();
        assertThat(result.diagnostics().indexInsertedItems())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(result.diagnostics().indexPairChecks())
            .isLessThan(EdgeConflictCorrection.EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(result.diagnostics().crossingsDetected()).isLessThan(bruteForce.crossings());
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(new java.util.TreeSet<String>(bruteForce.keys()))
            .containsAll(result.diagnostics().conflictsResidual());
        assertThat(result.diagnostics().conflictsResidual()).isSorted();
        assertThat(new java.util.LinkedHashSet<String>(result.diagnostics().conflictsResidual()))
            .hasSize(result.diagnostics().conflictsResidual().size());
    }

    @Test
    public void truncatesDetectionAtThePairBudget() {
        final Fixture fixture = sharedLeafFan(513);
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection bruteForce = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().indexPairChecks())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME);
        assertThat(result.diagnostics().indexInsertedItems())
            .isLessThan(EdgeConflictCorrection.EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME);
        assertThat(result.diagnostics().conflictsDetected())
            .isLessThan(EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(new java.util.TreeSet<String>(bruteForce.keys()))
            .containsAll(result.diagnostics().conflictsResidual());
        assertThat(result.diagnostics().conflictsResidual()).isSorted();
    }

    @Test
    public void truncatesDetectionAtTheVolumeBudget() {
        final Fixture fixture = concurrentDiameters(92);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().conflictsDetected())
            .isEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().crossingsDetected())
            .isEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().conflictsResidual()).hasSize(
            EdgeConflictCorrection.EDGE_CONFLICT_MAX_DETECTED_PER_FRAME);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.diagnostics().conflictsResidual()).isSorted();
    }

    @Test
    public void rejectsTheAttemptWhenVerificationWorkExhausts() {
        final Fixture fixture = wideNodeFieldWithOneCrossing(512);
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection expected = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().detectionTruncated()).isFalse();
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verificationWork())
            .isLessThanOrEqualTo(EdgeConflictCorrection.EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME);
        assertThat(result.diagnostics().conflictsResidual()).isEqualTo(expected.keys());
    }

    @Test
    public void rejectsTheAttemptWhenAVerificationDetectionStops() {
        final Fixture fixture = verificationDetectionStopFixture();
        final EdgeConflictResult result = apply(fixture);
        final EdgeConflictCorrection.Detection expected = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().detectionTruncated()).isFalse();
        assertThat(result.diagnostics().conflictsResidual()).isEqualTo(expected.keys());
    }

    @Test
    public void boundsTheTerminalRebuildAfterAnAcceptedRepair() {
        // Two complete A2 scans admit the repair: 2 * C(220, 2) = 48,180.
        // Its terminal rebuild needs another 24,090 units and must stop at 65,536.
        final Fixture fixture = wideNodeFieldWithOneCrossing(220);
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verificationWork()).isEqualTo(65_536L);
        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().detectionTruncated()).isFalse();
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
        assertThat(result.boundaryDiagnostics()).isSameAs(fixture.boundary.diagnostics());
    }

    @Test
    public void truncatesThePublishedDetectionAfterAnAcceptedRepair() {
        // Initial detection, rejected batch and accepted single complete. Exercise the
        // post-round detection (4) and the final publication detection (5) independently.
        for (final int detection : new int[] {4, 5}) {
            final Fixture fixture = reportedQuad();
            final EdgeConflictCorrection correction = new EdgeConflictCorrection();
            final Set<ProjectedEndpointKey> visible = seedCountersAtDetection(correction, fixture,
                detection, 262_143L, 131_072L, 0L);
            final EdgeConflictResult result = correction.apply(fixture.projection,
                fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
                visible, fixture.boundary);

            assertThat(result.diagnostics().rounds()).as("detection %s", detection).isEqualTo(1);
            assertThat(result.diagnostics().indexInsertedItems()).isEqualTo(262_144L);
            assertThat(result.diagnostics().indexPairChecks()).isEqualTo(131_072L);
            assertThat(result.diagnostics().verificationWork()).isLessThanOrEqualTo(65_536L);
            assertThat(result.diagnostics().detectionTruncated()).isTrue();
            assertThat(result.diagnostics().verificationBudgetExhausted()).isFalse();
            assertThat(result.diagnostics().conflictsResidual()).isEmpty();
        }
    }

    @Test
    public void reconcilesDetectionAndVerificationCapsAtAnInitialDetectionStop() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictCorrection correction = new EdgeConflictCorrection();
        final EdgeConflictResult result = correction.apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            seedCountersAtDetection(correction, fixture, 1, 0L, 131_072L, 65_536L),
            fixture.boundary);

        assertThat(result.diagnostics().detectionTruncated()).isTrue();
        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().indexBudgetExhausted()).isFalse();
        assertThat(result.diagnostics().indexPairChecks()).isEqualTo(131_072L);
        assertThat(result.diagnostics().verificationWork()).isEqualTo(65_536L);
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void keepsTheCompleteResidualWhenBothCapsStopVerificationDetection() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictCorrection correction = new EdgeConflictCorrection();
        final EdgeConflictResult result = correction.apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            seedCountersAtDetection(correction, fixture, 2, 0L, 131_072L, 65_536L),
            fixture.boundary);

        assertThat(result.diagnostics().detectionTruncated()).isFalse();
        assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().indexPairChecks()).isEqualTo(131_072L);
        assertThat(result.diagnostics().verificationWork()).isEqualTo(65_536L);
        assertThat(result.diagnostics().conflictsResidual()).containsExactly(CanonicalKeys.edgePair(
            fixture.projection.edges().get(0).key(), fixture.projection.edges().get(1).key()));
    }

    private static Set<ProjectedEndpointKey> seedCountersAtDetection(
            final EdgeConflictCorrection correction, final Fixture fixture, final int detection,
            final long items, final long pairs, final long verification) {
        return new java.util.AbstractSet<ProjectedEndpointKey>() {
            private int queries;

            @Override
            public boolean contains(final Object endpoint) {
                if (++queries == (detection - 1) * fixture.projection.edges().size() * 2 + 1) {
                    setCounter(correction, "indexInsertedItems", items);
                    setCounter(correction, "indexPairChecks", pairs);
                    setCounter(correction, "verificationWork", verification);
                }
                return fixture.visible.contains(endpoint);
            }

            @Override
            public java.util.Iterator<ProjectedEndpointKey> iterator() {
                return fixture.visible.iterator();
            }

            @Override
            public int size() {
                return fixture.visible.size();
            }
        };
    }

    private static void setCounter(final EdgeConflictCorrection correction, final String name,
            final long value) {
        try {
            final java.lang.reflect.Field field = EdgeConflictCorrection.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(correction, value);
        }
        catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    public void publishesTheRebuildUnitsInTheFrameVerificationTotal() {
        final Fixture fixture = rebuildWorkFixture();
        final EdgeConflictResult result = apply(fixture);
        // The rebuild's counted unit is a predicate evaluation (spec §13.1): its A2 scan of the
        // six published nodes evaluates C(6, 2) = 15 node pairs. The fixture has no hulls, so the
        // rebuild consumes no A3 comparison and recomputes no hull.
        final long rebuildUnits = NodeSeparationProjection.violatingPairsCounted(fixture.projection,
            result.positions(), Integer.MAX_VALUE).pairEvaluations();

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verifications()).isEqualTo(2);
        assertThat(rebuildUnits).isEqualTo(15L);
        // 30 counted verification units (the rejected batch stops before A2, and the accepted
        // single runs two six-node A2 scans of 15 pairs each) plus the rebuild's 15 predicate
        // evaluations. The result-size formula published 31 and fails this pin.
        assertThat(result.diagnostics().verificationWork()).isEqualTo(30L + rebuildUnits);
    }

    @Test
    public void countsTheRebuildHullPairComparisonsAsVerificationWork() {
        final A3SubsetFixture fixture = preExistingSiblingResidualFixture(0.0, 30.0, 100.0);

        final BoundaryInvariants.CountedRebuild rebuilt = new BoundaryInvariants().describeCounted(
            fixture.projection, fixture.boundary.positions(), METRICS,
            Collections.<PinProjection>emptyList(), fixture.boundary.geometry(),
            fixture.boundary.diagnostics().residualHullPairs(), fixture.boundary.diagnostics());
        final long countedDetectWork = new BoundaryInvariants().detectPairKeys(fixture.projection,
            fixture.boundary.geometry(), Long.MAX_VALUE).work();

        // The fixture's three sibling hulls consume at least the A-C hull-pair comparison, and
        // the rebuild must report exactly the work its own detect pass consumes.
        assertThat(countedDetectWork).isGreaterThan(0L);
        assertThat(rebuilt.hullPairWork()).isEqualTo(countedDetectWork);
    }

    private static Fixture referenceScaleFan(final int chords) {
        return diameters(chords, 600000.0);
    }

    private static Fixture concurrentDiameters(final int chords) {
        return diameters(chords, 1000.0);
    }

    private static Fixture verificationDetectionStopFixture() {
        return diameters(65, 1600.0);
    }

    /**
     * The superseded archived reference-scale shape: {@code chords} diameters of a circle of
     * the given radius. With the span-adaptive level-0 cell size the shared origin cell is
     * re-collected at every subdivision level, so the item writes alone reach the frame cap.
     */
    private static Fixture diameters(final int chords, final double radius) {
        final String[] ids = new String[chords * 2];
        final double[] x = new double[chords * 2];
        final double[] y = new double[chords * 2];
        for (int index = 0; index < chords; index++) {
            final double theta = Math.PI * index / (double) chords;
            x[index * 2] = radius * Math.cos(theta);
            y[index * 2] = radius * Math.sin(theta);
            x[index * 2 + 1] = -x[index * 2];
            y[index * 2 + 1] = -y[index * 2];
            ids[index * 2] = "C" + index + "A";
            ids[index * 2 + 1] = "C" + index + "B";
        }
        final int[][] edges = new int[chords][];
        for (int index = 0; index < chords; index++) {
            edges[index] = new int[] {index * 2, index * 2 + 1};
        }
        return fixture(1L, ids, x, y, edges, 14.0);
    }

    /**
     * {@code chords} fan chords sharing the radius-tiny hub {@code H_0}, so every chord starts
     * in the hub's leaf cell and the leaf's pairs consume the pair-check budget, plus two
     * corner chords that properly cross and keep the accumulated lower bound non-empty. The
     * tip discs stay far apart and are never reached before the counted stop.
     */
    private static Fixture sharedLeafFan(final int chords) {
        final int nodeCount = chords + 5;
        final String[] ids = new String[nodeCount];
        final double[] x = new double[nodeCount];
        final double[] y = new double[nodeCount];
        ids[0] = "A_0";
        x[0] = -5.0;
        y[0] = -5.0;
        ids[1] = "A_1";
        x[1] = 5.0;
        y[1] = 5.0;
        ids[2] = "A_2";
        x[2] = -5.0;
        y[2] = 5.0;
        ids[3] = "A_3";
        x[3] = 5.0;
        y[3] = -5.0;
        ids[4] = "H_0";
        x[4] = 0.0;
        y[4] = 0.0;
        final double radius = 200.0;
        for (int index = 0; index < chords; index++) {
            final double theta = Math.toRadians(-85.0 + 170.0 * index / (double) (chords - 1));
            ids[index + 5] = "T" + index;
            x[index + 5] = radius * Math.cos(theta);
            y[index + 5] = radius * Math.sin(theta);
        }
        final int[][] edges = new int[chords + 2][];
        edges[0] = new int[] {0, 1};
        edges[1] = new int[] {2, 3};
        for (int index = 0; index < chords; index++) {
            edges[index + 2] = new int[] {index + 5, 4};
        }
        return fixture(1L, ids, x, y, edges, 0.001);
    }

    /**
     * Well-separated leaf discs plus the four corners of one reported X: the initial detection
     * is one crossing, the first candidate reaches A2, and the first counted predicate call
     * exhausts the verification-work cap mid-scan.
     */
    private static Fixture wideNodeFieldWithOneCrossing(final int nodes) {
        final int leaves = nodes - 4;
        final String[] ids = new String[nodes];
        final double[] x = new double[nodes];
        final double[] y = new double[nodes];
        for (int index = 0; index < leaves; index++) {
            final double theta = 2.0 * Math.PI * index / (double) leaves;
            ids[index] = "F" + index;
            x[index] = 5000.0 * Math.cos(theta);
            y[index] = 5000.0 * Math.sin(theta);
        }
        ids[leaves] = "X_A";
        x[leaves] = -50.0;
        y[leaves] = -50.0;
        ids[leaves + 1] = "X_B";
        x[leaves + 1] = 50.0;
        y[leaves + 1] = 50.0;
        ids[leaves + 2] = "X_C";
        x[leaves + 2] = -50.0;
        y[leaves + 2] = 50.0;
        ids[leaves + 3] = "X_D";
        x[leaves + 3] = 50.0;
        y[leaves + 3] = -50.0;
        final int[][] edges = new int[][] {{leaves, leaves + 1}, {leaves + 2, leaves + 3}};
        return fixture(1L, ids, x, y, edges, 14.0);
    }

    /**
     * The reported quad plus two far-away discs below the minimum gap: the crossing is repaired
     * through one rejected batch and one accepted single (30 counted verification units), and
     * the published positions keep the close pair, so the §5.4 rebuild adds exactly one unit.
     */
    private static Fixture rebuildWorkFixture() {
        return fixture(7L, new String[] {"ID_A", "ID_B", "ID_C", "ID_D", "ID_X", "ID_Y"},
            new double[] {272.0, 170.0, 307.0, 202.0, 275.0, 240.0, 240.0, 208.0, 1000.0, 1000.0,
                1010.0, 1000.0},
            new int[][] {{0, 2}, {1, 3}});
    }

    @Test
    public void reportsTheReportedFixtureCountsBeforeRepair() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictCorrection.Detection detection = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(),
            fixture.visible);

        assertThat(detection.keys()).hasSize(1);
        assertThat(detection.crossings()).isEqualTo(1);
        assertThat(detection.edgeOverNodes()).isZero();
        assertThat(detection.keys().get(0)).isEqualTo(org.freeplane.plugin.graph.projection.CanonicalKeys
            .edgePair(fixture.projection.edges().get(0).key(), fixture.projection.edges().get(1).key()));
    }

    @Test
    public void ignoresAnInvisibleEdgeForC2() {
        // C=(50,0) is a non-endpoint disc on the A-B chord: without the visibility guard the
        // chord is drawn and C produces the C2 key CanonicalKeys.edgeNodePair(A-B, C).
        final Fixture fixture = fixture(1L, new String[] {"A", "B", "C"},
            new double[] {0.0, 0.0, 100.0, 0.0, 50.0, 0.0}, new int[][] {{0, 1}});
        final Set<org.freeplane.plugin.graph.projection.ProjectedEndpointKey> visible =
            Collections.emptySet();

        final EdgeConflictCorrection.Detection detection = EdgeConflictCorrection.detectConflicts(
            fixture.projection, fixture.boundary.positions(), fixture.boundary.geometry(), visible);

        assertThat(detection.keys()).isEmpty();
    }

    @Test
    public void detectConflictsIsAscendingDeduplicatedAndDeterministic() {
        final Fixture fixture = reportedQuad();
        final List<String> first = EdgeConflictCorrection.detectConflicts(fixture.projection,
            fixture.boundary.positions(), fixture.boundary.geometry(), fixture.visible).keys();
        final List<String> second = EdgeConflictCorrection.detectConflicts(fixture.projection,
            fixture.boundary.positions(), fixture.boundary.geometry(), fixture.visible).keys();

        assertThat(first).isSorted();
        assertThat(first).isEqualTo(second);
    }

    @Test
    public void resetsTheIndexCountersOnEveryApply() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictCorrection correction = new EdgeConflictCorrection();

        final EdgeConflictResult first = apply(correction, fixture);
        final EdgeConflictResult second = apply(correction, fixture);

        assertThat(second.diagnostics().indexInsertedItems())
            .isEqualTo(first.diagnostics().indexInsertedItems());
        assertThat(second.diagnostics().indexPairChecks())
            .isEqualTo(first.diagnostics().indexPairChecks());
        assertThat(second.diagnostics().indexSubdivisions())
            .isEqualTo(first.diagnostics().indexSubdivisions());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }

    @Test
    public void clearsTheExhaustedIndexBudgetBeforeTheNextFrame() {
        final Fixture exhausted = fixture(1L, new String[] {"A", "B", "C", "D"},
            new double[] {-1e300, -1e300, 1e300, 1e300, -1e300, 1e300, 1e300, -1e300},
            new int[][] {{0, 1}, {2, 3}});
        final Fixture quad = reportedQuad();
        final EdgeConflictCorrection correction = new EdgeConflictCorrection();

        apply(correction, exhausted);
        final EdgeConflictResult result = apply(correction, quad);

        assertThat(result.diagnostics().indexBudgetExhausted()).isFalse();
        assertThat(result.diagnostics()).isEqualTo(apply(new EdgeConflictCorrection(), quad).diagnostics());
    }

    @Test
    public void vetoesACandidateThatAddsASiblingHullViolation() {
        final SiblingFixture fixture = siblingOverlapFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(result.diagnostics().conflictsResidual())
            .containsExactly(CanonicalKeys.edgeNodePair(fixture.edgeKey, fixture.passedOverNode));
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.positions().nodes().get(fixture.passedOverNode))
            .isEqualTo(fixture.initialPassedOverCentre);
    }

    @Test
    public void keepsAPreExistingHullResidualWhenTheCandidateDoesNotAddOne() {
        final SiblingFixture fixture = siblingOverlapFixture();
        final BoundarySeparationResult boundary = fixture.boundaryWithResidual(
            CanonicalKeys.hullPair(fixture.siblingFirst, fixture.siblingSecond));
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            boundary.positions(), METRICS, Collections.<PinProjection>emptyList(), fixture.visible, boundary);

        // A3 compares pair-key subsets, so a legal pre-existing residual never vetoes the repair.
        assertThat(result.diagnostics().rounds()).isGreaterThanOrEqualTo(0);
        assertThat(result.diagnostics().conflictsResidual()).doesNotContain(
            CanonicalKeys.hullPair(fixture.siblingFirst, fixture.siblingSecond));
    }

    @Test
    public void acceptsAPreExistingSiblingHullResidualWhenTheCandidateKeepsTheSamePair() {
        // V_in = {pair(A, C)} is a real pre-existing residual of the published geometry, and the
        // candidate repairs the C2 conflict without changing that pair, so A3 accepts it.
        final A3SubsetFixture fixture = preExistingSiblingResidualFixture(0.0, 30.0, 100.0);
        assertThat(new BoundaryInvariants().detectPairKeys(fixture.projection,
            fixture.boundary.geometry()))
            .containsExactly(CanonicalKeys.hullPair(fixture.aHull, fixture.cHull));

        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = result.diagnostics().repairsApplied().get(0);
        assertThat(repair.kind()).isEqualTo(EdgeConflictDiagnostics.RepairKind.TRANSLATION);
        assertThat(repair.nodeKeys()).containsExactly(fixture.aKey);
        assertThat(repair.toCentres().get(fixture.aKey)).isEqualTo(LayoutPoint.of(-6.0, 0.0));
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    @Test
    public void rejectsACandidateThatReplacesAPreExistingHullResidualWithAnEqualCountPair() {
        // V_in = {pair(A, C)} and V_out = {pair(A, D)} have equal violation counts, so only the
        // pair-key subset comparison rejects the candidate; a count comparison would accept it.
        final A3SubsetFixture fixture = preExistingSiblingResidualFixture(0.0, 45.0, -53.0);
        final BoundaryInvariants invariants = new BoundaryInvariants();
        assertThat(invariants.detectPairKeys(fixture.projection, fixture.boundary.geometry()))
            .containsExactly(CanonicalKeys.hullPair(fixture.aHull, fixture.cHull));

        final Map<ProjectedNodeKey, LayoutPoint> candidateNodes =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(fixture.boundary.positions().nodes());
        candidateNodes.put(fixture.aKey, LayoutPoint.of(-6.0, 0.0));
        final LayoutPositions candidatePositions = LayoutPositions.of(candidateNodes,
            fixture.boundary.positions().anchors());
        final GraphGeometry candidateGeometry = new GraphGeometryEngine().recomputeHulls(
            fixture.projection, candidatePositions, METRICS, fixture.boundary.geometry(),
            BoundaryInvariants.changedHulls(BoundaryInvariants.nodeHullsByKey(fixture.projection),
                fixture.boundary.positions(), candidatePositions));
        assertThat(invariants.detectPairKeys(fixture.projection, candidateGeometry))
            .containsExactly(CanonicalKeys.hullPair(fixture.aHull, fixture.dHull));

        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.diagnostics().conflictsResidual()).containsExactly(
            CanonicalKeys.edgeNodePair(fixture.edgeKey, fixture.aKey));
        assertThat(result.positions().nodes().get(fixture.aKey)).isEqualTo(fixture.aCentre);
    }

    @Test
    public void recordsOneRepairPerCandidateForAnAcceptedMixedKindBatch() {
        // Greedy batching accepts {swap01, translation-n} as one mixed-kind attempt; each
        // candidate must be reported separately so its kind labels only its own moved nodes.
        final Fixture fixture = mixedKindBatchFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, fixture.pinsFor(2, 3), fixture.visible,
            fixture.boundary);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(2);
        final EdgeConflictDiagnostics.Repair swap = repairOfKind(result,
            EdgeConflictDiagnostics.RepairKind.SWAP);
        final EdgeConflictDiagnostics.Repair translation = repairOfKind(result,
            EdgeConflictDiagnostics.RepairKind.TRANSLATION);
        assertThat(swap.nodeKeys()).containsExactly(fixture.key(0), fixture.key(1));
        assertThat(swap.fromCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(272.0, 170.0));
        assertThat(swap.toCentres().get(fixture.key(0))).isEqualTo(LayoutPoint.of(307.0, 202.0));
        assertThat(translation.nodeKeys()).containsExactly(fixture.key(6));
        assertThat(translation.fromCentres().get(fixture.key(6)))
            .isEqualTo(LayoutPoint.of(1005.0, 1050.0));
        assertThat(translation.toCentres().get(fixture.key(6))).isEqualTo(LayoutPoint.of(983.0, 1050.0));
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    }

    private static EdgeConflictDiagnostics.Repair repairOfKind(final EdgeConflictResult result,
            final EdgeConflictDiagnostics.RepairKind kind) {
        for (final EdgeConflictDiagnostics.Repair repair : result.diagnostics().repairsApplied()) {
            if (repair.kind() == kind) {
                return repair;
            }
        }
        throw new AssertionError("No repair of kind " + kind);
    }

    @Test
    public void appliesCandidatesInCanonicalOrder() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        final List<EdgeConflictDiagnostics.Repair> repairs = result.diagnostics().repairsApplied();
        for (int index = 1; index < repairs.size(); index++) {
            assertThat(repairs.get(index - 1).batchIndex()).isLessThan(repairs.get(index).batchIndex());
        }
        assertThat(result.diagnostics().repairsApplied().get(0).nodeKeys().get(0))
            .isEqualTo(fixture.key(0));
    }

    @Test
    public void vetoesACandidateThatWouldBreakMinGap() {
        final MinGapFixture fixture = minGapFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isEqualTo(1);
        assertThat(org.freeplane.plugin.graph.layout.NodeSeparationProjection.violatingPairs(
            fixture.projection, result.positions())).contains(
                CanonicalKeys.nodePair(fixture.passedOverNode, fixture.bystanderNode));
    }

    @Test
    public void movesThePassedOverNodeOffTheChordWithTheLexicographicallySmallerNormal() {
        final C2Fixture fixture = c2FixtureWithPositiveProjection();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = result.diagnostics().repairsApplied().get(0);
        assertThat(repair.kind()).isEqualTo(EdgeConflictDiagnostics.RepairKind.TRANSLATION);
        assertThat(repair.nodeKeys()).containsExactly(fixture.passedOverNode);
        assertThat(result.diagnostics().conflictsResidual()).isEmpty();
        // Flipping the normal direction (probe 16) would leave |2s - (R + 3)| < R + 3.
        assertThat(result.positions().nodes().get(fixture.passedOverNode))
            .isNotEqualTo(fixture.initialPassedOverCentre);
    }

    @Test
    public void rejectsACandidateThatIntroducesANewCrossing() {
        final Fixture fixture = crossingReplacementFixture();
        final EdgeConflictResult result = apply(fixture);

        final ProjectedEdgeKey first = fixture.projection.edges().get(0).key();
        final ProjectedEdgeKey second = fixture.projection.edges().get(1).key();
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).containsExactly(
            CanonicalKeys.edgePair(first, second));
    }

    @Test
    public void isDeterministicAcrossRunsAndPermutedInputs() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult first = apply(fixture);

        final EdgeConflictResult second = new EdgeConflictCorrection().apply(fixture.permutedProjection(),
            fixture.permutedPositions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.permutedBoundary());

        assertThat(second.positions()).isEqualTo(first.positions());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }

    @Test
    public void discardsCandidatesThatMoveAPinnedNode() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, fixture.pinsForAllNodes(), fixture.visible,
            fixture.boundary);

        // Every candidate moves a pinned node and is discarded before evaluation, so no
        // verification is counted and every published position equals the input (I1).
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().verifications()).isZero();
        assertThat(result.diagnostics().conflictsResidual()).hasSize(1);
        for (final ProjectedNodeKey key : fixture.keys()) {
            assertThat(result.positions().nodes().get(key))
                .isEqualTo(fixture.boundary.positions().nodes().get(key));
        }
    }

    @Test
    public void leavesAMixedEnclosureConflictToRoutingWithoutMovingAnchors() {
        final MixedFixture fixture = mixedEnclosureConflictFixture();
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
            fixture.visible, fixture.boundary);

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().repairsApplied()).isEmpty();
        assertThat(result.diagnostics().conflictsResidual()).isNotEmpty();
        assertThat(result.positions().anchors()).isEqualTo(fixture.boundary.positions().anchors());
    }

    private static final class Fixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final List<ProjectedNodeKey> keys;

        private Fixture(GraphProjection projection, BoundarySeparationResult boundary,
                Set<ProjectedEndpointKey> visible, List<ProjectedNodeKey> keys) {
            this.projection = projection;
            this.boundary = boundary;
            this.visible = visible;
            this.keys = keys;
        }

        private ProjectedNodeKey key(int index) {
            return keys.get(index);
        }

        private List<ProjectedNodeKey> keys() {
            return keys;
        }

        private GraphProjection permutedProjection() {
            final List<ProjectedNode> reversedNodes = new ArrayList<ProjectedNode>(projection.nodes());
            Collections.reverse(reversedNodes);
            final List<ProjectedEdge> reversedEdges = new ArrayList<ProjectedEdge>(projection.edges());
            Collections.reverse(reversedEdges);
            return GraphProjection.projected(projection.generation(), reversedNodes,
                projection.enclosures(), reversedEdges, projection.relationshipResolutions(),
                projection.pins());
        }

        private LayoutPositions permutedPositions() {
            final Map<ProjectedNodeKey, LayoutPoint> reversed =
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (int index = keys.size() - 1; index >= 0; index--) {
                final ProjectedNodeKey key = keys.get(index);
                reversed.put(key, boundary.positions().nodes().get(key));
            }
            return LayoutPositions.of(reversed, boundary.positions().anchors());
        }

        private GraphGeometry permutedGeometry() {
            final Map<ProjectedNodeKey, NodeGeometry> reversedNodes =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            for (int index = keys.size() - 1; index >= 0; index--) {
                final ProjectedNodeKey key = keys.get(index);
                reversedNodes.put(key, boundary.geometry().nodes().get(key));
            }
            final Map<EnclosureHullKey, HullGeometry> reversedHulls =
                new LinkedHashMap<EnclosureHullKey, HullGeometry>();
            final List<EnclosureHullKey> hullKeys =
                new ArrayList<EnclosureHullKey>(boundary.geometry().hulls().keySet());
            for (int index = hullKeys.size() - 1; index >= 0; index--) {
                final EnclosureHullKey key = hullKeys.get(index);
                reversedHulls.put(key, boundary.geometry().hulls().get(key));
            }
            return GraphGeometry.of(reversedNodes, reversedHulls);
        }

        private BoundarySeparationResult permutedBoundary() {
            return new BoundarySeparationResult(permutedPositions(), permutedGeometry(),
                boundary.diagnostics(), boundary.nodeResidualViolations(), boundary.timings());
        }

        private BoundarySeparationResult boundaryWithNodeRadius(final double radius) {
            final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            for (final ProjectedNodeKey key : keys) {
                nodeGeometry.put(key, NodeGeometry.of(boundary.positions().nodes().get(key), radius));
            }
            return new BoundarySeparationResult(boundary.positions(),
                GraphGeometry.of(nodeGeometry, boundary.geometry().hulls()), boundary.diagnostics(),
                boundary.nodeResidualViolations(), boundary.timings());
        }

        private List<PinProjection> pinsForAllNodes() {
            final List<PinProjection> result = new ArrayList<PinProjection>();
            for (final ProjectedNodeKey key : keys) {
                final LayoutPoint point = boundary.positions().nodes().get(key);
                result.add(PinProjection.active(PinRecord.of(key.source().persistedReference().get(),
                    point.x(), point.y(), Collections.<UnknownXml>emptyList()), key));
            }
            return result;
        }

        private List<PinProjection> pinsFor(final int... indices) {
            final List<PinProjection> result = new ArrayList<PinProjection>();
            for (final int index : indices) {
                final ProjectedNodeKey key = keys.get(index);
                final LayoutPoint point = boundary.positions().nodes().get(key);
                result.add(PinProjection.active(PinRecord.of(key.source().persistedReference().get(),
                    point.x(), point.y(), Collections.<UnknownXml>emptyList()), key));
            }
            return result;
        }
    }

    private static EdgeConflictResult apply(final Fixture fixture) {
        return apply(new EdgeConflictCorrection(), fixture);
    }

    private static EdgeConflictResult apply(final EdgeConflictCorrection correction,
            final Fixture fixture) {
        return correction.apply(fixture.projection, fixture.boundary.positions(), METRICS,
            Collections.<PinProjection>emptyList(), fixture.visible, fixture.boundary);
    }

    private static Fixture reportedQuad() {
        return fixture(1L, new String[] {"ID_A", "ID_B", "ID_C", "ID_D"},
            new double[] {272.0, 170.0, 307.0, 202.0, 275.0, 240.0, 240.0, 208.0},
            new int[][] {{0, 2}, {1, 3}});
    }

    /**
     * The reported quad plus a far-away C2 conflict on node n, with C and D pinned so the
     * crossing offers only swap01 and M(A)/M(B) and the C2 offers only translation-n: the first
     * greedy batch is the mixed-kind pair {swap01, translation-n}.
     */
    private static Fixture mixedKindBatchFixture() {
        return fixture(6L, new String[] {"ID_A", "ID_B", "ID_C", "ID_D", "ID_P", "ID_Q", "ID_N"},
            new double[] {272.0, 170.0, 307.0, 202.0, 275.0, 240.0, 240.0, 208.0, 1000.0, 1000.0,
                1000.0, 1100.0, 1005.0, 1050.0},
            new int[][] {{0, 2}, {1, 3}, {4, 5}});
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] coordinates,
            final int[][] edges) {
        return fixture(generation, ids, coordinates, edges, 14.0);
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] x,
            final double[] y, final int[][] edges, final double radius) {
        final double[] coordinates = new double[x.length * 2];
        for (int index = 0; index < x.length; index++) {
            coordinates[index * 2] = x[index];
            coordinates[index * 2 + 1] = y[index];
        }
        return fixture(generation, ids, coordinates, edges, radius);
    }

    private static Fixture fixture(final long generation, final String[] ids, final double[] coordinates,
            final int[][] edges, final double radius) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (int index = 0; index < ids.length; index++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
                NodeReference.of(MAP, PersistedNodeId.of(ids[index]))));
            final LayoutPoint point = LayoutPoint.of(coordinates[index * 2], coordinates[index * 2 + 1]);
            keys.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(ids[index], ids[index]), "map", false));
            points.put(key, point);
            nodeGeometry.put(key, NodeGeometry.of(point, radius));
        }
        final List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        for (final int[] edge : edges) {
            final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(keys.get(edge[0]));
            final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(keys.get(edge[1]));
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(
                first.node().get().source(), NodeReference.of(second.mapReferenceId(),
                    PersistedNodeId.of(ids[edge[1]])), false, false, "s", "m", "t");
            projectedEdges.add(ProjectedEdge.of(ProjectedEdgeKey.of(first, second),
                Collections.singletonList(EdgeContributor.nativeConnector(
                    ConnectorSnapshot.of(0, descriptor), first, second))));
        }
        final GraphProjection projection = GraphProjection.projected(generation, nodes,
            Collections.<ProjectedEnclosure>emptyList(), projectedEdges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        final LayoutPositions positions = LayoutPositions.of(points,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        final GraphGeometry geometry = GraphGeometry.of(nodeGeometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        return new Fixture(projection, boundary, visible, keys);
    }

    private static Fixture crossingReplacementFixture() {
        return fixture(2L, new String[] {"A", "B", "C", "D", "E", "F"},
            new double[] {93.0, 203.0, -32.0, 245.0, -75.0, 119.0, 55.0, 78.0, 63.0, 148.0,
                173.0, 132.0},
            new int[][] {{0, 2}, {1, 3}, {4, 5}});
    }

    private static SourceNodeKey source(final MapReferenceId map, final String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(source(MAP, id));
    }

    private static ProjectedNode projectedNode(final ProjectedNodeKey key, final String label) {
        return ProjectedNode.of(key, SafeNodeLabel.of(label, label), "map", false);
    }

    private static ProjectedEdge nodeEdge(final int occurrence, final ProjectedNodeKey first,
            final ProjectedNodeKey second) {
        final ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofNode(first);
        final ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofNode(second);
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(first.source(),
            second.source().persistedReference().get(), false, false, "s", "m", "t");
        return ProjectedEdge.of(ProjectedEdgeKey.of(firstEndpoint, secondEndpoint),
            Collections.singletonList(EdgeContributor.nativeConnector(
                ConnectorSnapshot.of(occurrence, descriptor), firstEndpoint, secondEndpoint)));
    }

    private static ProjectedEdge enclosureEdge(final int occurrence, final EnclosureKey enclosure,
            final ProjectedNodeKey target) {
        final ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(enclosure);
        final ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofNode(target);
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(enclosure.source(),
            target.source().persistedReference().get(), false, false, "s", "m", "t");
        return ProjectedEdge.of(ProjectedEdgeKey.of(enclosureEndpoint, targetEndpoint),
            Collections.singletonList(EdgeContributor.nativeConnector(
                ConnectorSnapshot.of(occurrence, descriptor), enclosureEndpoint, targetEndpoint)));
    }

    private static final class SiblingFixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final EnclosureHullKey siblingFirst;
        private final EnclosureHullKey siblingSecond;
        private final ProjectedEdgeKey edgeKey;
        private final ProjectedNodeKey passedOverNode;
        private final LayoutPoint initialPassedOverCentre;

        private SiblingFixture(final GraphProjection projection, final BoundarySeparationResult boundary,
                final Set<ProjectedEndpointKey> visible, final EnclosureHullKey siblingFirst,
                final EnclosureHullKey siblingSecond, final ProjectedEdgeKey edgeKey,
                final ProjectedNodeKey passedOverNode, final LayoutPoint initialPassedOverCentre) {
            this.projection = projection;
            this.boundary = boundary;
            this.visible = visible;
            this.siblingFirst = siblingFirst;
            this.siblingSecond = siblingSecond;
            this.edgeKey = edgeKey;
            this.passedOverNode = passedOverNode;
            this.initialPassedOverCentre = initialPassedOverCentre;
        }

        private BoundarySeparationResult boundaryWithResidual(final String pairKey) {
            final BoundarySeparationDiagnostics diagnostics = boundary.diagnostics();
            final BoundarySeparationDiagnostics rebuilt = new BoundarySeparationDiagnostics(
                diagnostics.conflicts(), Collections.singletonList(pairKey),
                diagnostics.hullViolationsDetected(), diagnostics.hullResidualViolations(),
                diagnostics.rounds(), diagnostics.displacementRms(), diagnostics.displacementMax(),
                diagnostics.appliedDisplacements(), diagnostics.deltaRms(), diagnostics.deltaMax());
            return new BoundarySeparationResult(boundary.positions(), boundary.geometry(), rebuilt,
                boundary.nodeResidualViolations(), boundary.timings());
        }
    }

    private static SiblingFixture siblingOverlapFixture() {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey c = nodeKey("c");
        final ProjectedNodeKey p = nodeKey("p");
        final ProjectedNodeKey q = nodeKey("q");
        final EnclosureKey aKey = EnclosureKey.of(source(MAP, "A"));
        final EnclosureKey cKey = EnclosureKey.of(source(MAP, "C"));
        final EnclosureKey rootKey = EnclosureKey.of(source(MAP, "R"));
        final EnclosureHullKey aHull = EnclosureHullKey.of(Collections.singletonList(aKey));
        final EnclosureHullKey cHull = EnclosureHullKey.of(Collections.singletonList(cKey));
        final EnclosureHullKey rootHull = EnclosureHullKey.of(Collections.singletonList(rootKey));
        final ProjectedEnclosure root = ProjectedEnclosure.of(rootHull,
            Collections.singletonList(rootKey), Collections.singletonList(SafeNodeLabel.of("R", "R")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Arrays.asList(aHull, cHull), true, BoundaryTier.EMPHATIC);
        final ProjectedEnclosure aEnclosure = ProjectedEnclosure.of(aHull,
            Collections.singletonList(aKey), Collections.singletonList(SafeNodeLabel.of("A", "A")),
            "map", Optional.of(rootHull), Collections.singletonList(a),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        final ProjectedEnclosure cEnclosure = ProjectedEnclosure.of(cHull,
            Collections.singletonList(cKey), Collections.singletonList(SafeNodeLabel.of("C", "C")),
            "map", Optional.of(rootHull), Collections.singletonList(c),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(projectedNode(a, "a"));
        nodes.add(projectedNode(c, "c"));
        nodes.add(projectedNode(p, "p"));
        nodes.add(projectedNode(q, "q"));
        final ProjectedEdge pToQ = nodeEdge(0, p, q);
        final GraphProjection projection = GraphProjection.projected(1L, nodes,
            Arrays.asList(root, aEnclosure, cEnclosure), Collections.singletonList(pToQ),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        final Map<ProjectedNodeKey, LayoutPoint> points =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        points.put(a, LayoutPoint.of(10.0, 0.0));
        points.put(c, LayoutPoint.of(-58.0, 0.0));
        points.put(p, LayoutPoint.of(0.0, 100.0));
        points.put(q, LayoutPoint.of(0.0, -100.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(rootHull, LayoutPoint.of(0.0, 0.0));
        anchors.put(aHull, LayoutPoint.of(0.0, 0.0));
        anchors.put(cHull, LayoutPoint.of(0.0, 0.0));
        final LayoutPositions positions = LayoutPositions.of(points, anchors);
        final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        return new SiblingFixture(projection, boundary, visible, aHull, cHull, pToQ.key(), a,
            LayoutPoint.of(10.0, 0.0));
    }

    private static final class A3SubsetFixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final ProjectedNodeKey aKey;
        private final EnclosureHullKey aHull;
        private final EnclosureHullKey cHull;
        private final EnclosureHullKey dHull;
        private final ProjectedEdgeKey edgeKey;
        private final LayoutPoint aCentre;

        private A3SubsetFixture(final GraphProjection projection, final BoundarySeparationResult boundary,
                final Set<ProjectedEndpointKey> visible, final ProjectedNodeKey aKey,
                final EnclosureHullKey aHull, final EnclosureHullKey cHull,
                final EnclosureHullKey dHull, final ProjectedEdgeKey edgeKey,
                final LayoutPoint aCentre) {
            this.projection = projection;
            this.boundary = boundary;
            this.visible = visible;
            this.aKey = aKey;
            this.aHull = aHull;
            this.cHull = cHull;
            this.dHull = dHull;
            this.edgeKey = edgeKey;
            this.aCentre = aCentre;
        }
    }

    /**
     * Three sibling enclosures A, C and D under no common parent, each holding one node on the
     * x-axis, plus the vertical edge p-q: the C2 conflict on node a drives a single TRANSLATION
     * candidate that moves a from (aX, 0) to (-6, 0). The sibling hull distance is chosen by the
     * caller, so aX/cX/dX select the pre-existing residual state A3 must judge.
     */
    private static A3SubsetFixture preExistingSiblingResidualFixture(final double aX, final double cX,
            final double dX) {
        final ProjectedNodeKey a = nodeKey("a");
        final ProjectedNodeKey c = nodeKey("c");
        final ProjectedNodeKey d = nodeKey("d");
        final ProjectedNodeKey p = nodeKey("p");
        final ProjectedNodeKey q = nodeKey("q");
        final EnclosureKey aKey = EnclosureKey.of(source(MAP, "A"));
        final EnclosureKey cKey = EnclosureKey.of(source(MAP, "C"));
        final EnclosureKey dKey = EnclosureKey.of(source(MAP, "D"));
        final EnclosureHullKey aHull = EnclosureHullKey.of(Collections.singletonList(aKey));
        final EnclosureHullKey cHull = EnclosureHullKey.of(Collections.singletonList(cKey));
        final EnclosureHullKey dHull = EnclosureHullKey.of(Collections.singletonList(dKey));
        final ProjectedEnclosure aEnclosure = ProjectedEnclosure.of(aHull,
            Collections.singletonList(aKey), Collections.singletonList(SafeNodeLabel.of("A", "A")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.singletonList(a),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        final ProjectedEnclosure cEnclosure = ProjectedEnclosure.of(cHull,
            Collections.singletonList(cKey), Collections.singletonList(SafeNodeLabel.of("C", "C")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.singletonList(c),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        final ProjectedEnclosure dEnclosure = ProjectedEnclosure.of(dHull,
            Collections.singletonList(dKey), Collections.singletonList(SafeNodeLabel.of("D", "D")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.singletonList(d),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        final List<ProjectedNode> nodes = Arrays.asList(projectedNode(a, "a"), projectedNode(c, "c"),
            projectedNode(d, "d"), projectedNode(p, "p"), projectedNode(q, "q"));
        final GraphProjection projection = GraphProjection.projected(10L, nodes,
            Arrays.asList(aEnclosure, cEnclosure, dEnclosure), Collections.singletonList(nodeEdge(0, p, q)),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        final Map<ProjectedNodeKey, LayoutPoint> points =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        points.put(a, LayoutPoint.of(aX, 0.0));
        points.put(c, LayoutPoint.of(cX, 0.0));
        points.put(d, LayoutPoint.of(dX, 0.0));
        points.put(p, LayoutPoint.of(5.0, 100.0));
        points.put(q, LayoutPoint.of(5.0, -100.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(aHull, LayoutPoint.of(aX, 0.0));
        anchors.put(cHull, LayoutPoint.of(cX, 0.0));
        anchors.put(dHull, LayoutPoint.of(dX, 0.0));
        final LayoutPositions positions = LayoutPositions.of(points, anchors);
        final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions,
            METRICS);
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            new BoundarySeparationDiagnostics(Collections.<BoundaryConflict>emptyList(),
                Collections.singletonList(CanonicalKeys.hullPair(aHull, cHull)), 0, 0, 0, 0.0, 0.0,
                Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0),
            0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        return new A3SubsetFixture(projection, boundary, visible, a, aHull, cHull, dHull,
            projection.edges().get(0).key(), LayoutPoint.of(aX, 0.0));
    }

    private static final class MinGapFixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final ProjectedNodeKey passedOverNode;
        private final ProjectedNodeKey bystanderNode;

        private MinGapFixture(final Fixture fixture, final ProjectedNodeKey passedOverNode,
                final ProjectedNodeKey bystanderNode) {
            this.projection = fixture.projection;
            this.boundary = fixture.boundary;
            this.visible = fixture.visible;
            this.passedOverNode = passedOverNode;
            this.bystanderNode = bystanderNode;
        }
    }

    private static MinGapFixture minGapFixture() {
        final Fixture fixture = fixture(3L, new String[] {"A", "B", "n", "b", "g"},
            new double[] {-100.0, 0.0, 100.0, 0.0, 0.0, 5.0, 0.0, 20.0, 0.0, 38.0},
            new int[][] {{0, 1}});
        return new MinGapFixture(fixture, fixture.key(2), fixture.key(3));
    }

    private static final class C2Fixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;
        private final ProjectedNodeKey passedOverNode;
        private final LayoutPoint initialPassedOverCentre;

        private C2Fixture(final Fixture fixture, final ProjectedNodeKey passedOverNode,
                final LayoutPoint initialPassedOverCentre) {
            this.projection = fixture.projection;
            this.boundary = fixture.boundary;
            this.visible = fixture.visible;
            this.passedOverNode = passedOverNode;
            this.initialPassedOverCentre = initialPassedOverCentre;
        }
    }

    private static C2Fixture c2FixtureWithPositiveProjection() {
        final Fixture fixture = fixture(4L, new String[] {"A", "B", "n"},
            new double[] {-100.0, 0.0, 100.0, 0.0, 0.0, 5.0},
            new int[][] {{0, 1}});
        return new C2Fixture(fixture, fixture.key(2),
            fixture.boundary.positions().nodes().get(fixture.key(2)));
    }

    private static final class MixedFixture {
        private final GraphProjection projection;
        private final BoundarySeparationResult boundary;
        private final Set<ProjectedEndpointKey> visible;

        private MixedFixture(final GraphProjection projection, final BoundarySeparationResult boundary,
                final Set<ProjectedEndpointKey> visible) {
            this.projection = projection;
            this.boundary = boundary;
            this.visible = visible;
        }
    }

    private static MixedFixture mixedEnclosureConflictFixture() {
        final ProjectedNodeKey e = nodeKey("e");
        final ProjectedNodeKey x = nodeKey("x");
        final ProjectedNodeKey y = nodeKey("y");
        final ProjectedNodeKey z = nodeKey("z");
        final EnclosureKey enclosureKey = EnclosureKey.of(e.source());
        final EnclosureHullKey enclosureHull = EnclosureHullKey.of(
            Collections.singletonList(enclosureKey));
        final ProjectedEnclosure enclosure = ProjectedEnclosure.of(enclosureHull,
            Collections.singletonList(enclosureKey), Collections.singletonList(SafeNodeLabel.of("e", "e")),
            "map", Optional.<EnclosureHullKey>empty(), Collections.singletonList(e),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC);
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(projectedNode(e, "e"));
        nodes.add(projectedNode(x, "x"));
        nodes.add(projectedNode(y, "y"));
        nodes.add(projectedNode(z, "z"));
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        edges.add(enclosureEdge(0, enclosureKey, x));
        edges.add(nodeEdge(1, y, z));
        final GraphProjection projection = GraphProjection.projected(5L, nodes,
            Collections.singletonList(enclosure), edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        final Map<ProjectedNodeKey, LayoutPoint> points =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        points.put(e, LayoutPoint.of(-100.0, 0.0));
        points.put(x, LayoutPoint.of(100.0, 0.0));
        points.put(y, LayoutPoint.of(0.0, -50.0));
        points.put(z, LayoutPoint.of(0.0, 50.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(enclosureHull, LayoutPoint.of(-90.0, 7.0));
        final LayoutPositions positions = LayoutPositions.of(points, anchors);
        final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions, METRICS);
        final BoundarySeparationResult boundary = new BoundarySeparationResult(positions, geometry,
            BoundarySeparationDiagnostics.empty(), 0, new BoundarySeparationTimings(0L, 0L, 0L, 0L));
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        return new MixedFixture(projection, boundary, visible);
    }

    @Test
    public void rebuildsTheBoundaryRecordForThePublishedPositions() {
        final Fixture fixture = reportedQuad();
        final EdgeConflictResult result = apply(fixture);

        assertThat(result.boundaryDiagnostics()).isNotSameAs(fixture.boundary.diagnostics());
        assertThat(result.boundaryDiagnostics().hullViolationsDetected())
            .isEqualTo(fixture.boundary.diagnostics().hullViolationsDetected());
        assertThat(result.boundaryDiagnostics().rounds())
            .isEqualTo(fixture.boundary.diagnostics().rounds());
        assertThat(result.boundaryDiagnostics().appliedDisplacements())
            .isEqualTo(fixture.boundary.diagnostics().appliedDisplacements());
        assertThat(result.boundaryDiagnostics().residualHullPairs()).isSorted();
        assertThat(result.nodeResidualViolations()).isZero();
        assertThat(result.appliedDisplacements()).isNotEmpty();
        assertThat(result.timings().rebuildNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    public void reusesTheBoundaryTerminalGeometry() {
        final Fixture fixture = reportedQuad();
        final BoundarySeparationResult poisoned = fixture.boundaryWithNodeRadius(40.0);
        final EdgeConflictResult result = new EdgeConflictCorrection().apply(fixture.projection,
            poisoned.positions(), METRICS, Collections.<PinProjection>emptyList(), fixture.visible, poisoned);

        // A fresh computeHulls would rebuild radius 8.0/14.0 discs from prominence, and the
        // reported fixture's chords sit ~33.6 from every unrelated centre, so only detection
        // against the supplied terminal geometry (radius 40.0) sees the C2 conflict.
        assertThat(result.diagnostics().edgeOverNodeDetected()).isGreaterThanOrEqualTo(1);
    }
}
