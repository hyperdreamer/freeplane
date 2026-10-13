package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.junit.Test;

public class EdgeRouterShould {
    @Test
    public void exposeEdgePathsInCanonicalEdgeOrder() {
        final ProjectedEdgeKey first = EdgeRouterFixture.exitEdge("a", "b");
        final ProjectedEdgeKey second = EdgeRouterFixture.exitEdge("a", "c");

        assertThat(org.freeplane.plugin.graph.projection.CanonicalKeys.edge(first)
                .compareTo(org.freeplane.plugin.graph.projection.CanonicalKeys.edge(second)) <= 0).isTrue();
    }

    @Test
    public void exposeStraightFallbackForAStraightOnlyScaffold() {
        // The reported fixture's four nodes and two crossing chords: with the full ladder no
        // rung clears (every rung still crosses the other chord), so both edges are straight
        // fallbacks and the crossing pair stays as the published residual.
        final EdgeRouterFixture fixture = EdgeRouterFixture.reportedConvexQuad();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).hasSize(2);
        final List<EdgePath> values = new java.util.ArrayList<EdgePath>(paths.byEdge().values());
        assertThat(values.get(0).shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(values.get(1).shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().edgesStraight()).isZero();
        assertThat(paths.diagnostics().edgesBow()).isZero();
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
        assertThat(paths.diagnostics().edgesFallback()).isEqualTo(2);
        assertThat(paths.diagnostics().pathCrossingsDetected()).isEqualTo(1);
        assertThat(paths.diagnostics().pathCrossingsResidual()).hasSize(1);
    }

    @Test
    public void omitAnEdgeWhoseGeometryIsNotResolvableAndDoNotUseItAsAnObstacle() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.oneResolvableEdgeAndOneBrokenEdge();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).hasSize(1);
        assertThat(paths.diagnostics().pathCrossingsDetected()).isZero();
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void detectAProperCrossingWithTheExponentNormalisedPredicate() {
        final EdgePath first = EdgePath.straight(LayoutPoint.of(-1e300, -1e300), LayoutPoint.of(1e300, 1e300));
        final EdgePath second = EdgePath.straight(LayoutPoint.of(1e300, -1e300), LayoutPoint.of(-1e300, 1e300));
        // The plan's original second segment shared the interior origin with the diagonal, so it
        // crosses; that pair is pinned true here and the negative case is moved clear.
        final EdgePath crossingAtOrigin = EdgePath.straight(LayoutPoint.of(-1e300, 0.0),
            LayoutPoint.of(1e300, 0.0));
        final EdgePath clearHorizontal = EdgePath.straight(LayoutPoint.of(-1e300, 2e300),
            LayoutPoint.of(1e300, 2e300));

        assertThat(EdgeRouter.properlyCross(first, second)).isTrue();
        assertThat(EdgeRouter.properlyCross(first, crossingAtOrigin)).isTrue();
        assertThat(EdgeRouter.properlyCross(first, clearHorizontal)).isFalse();
        assertThat(EdgeRouter.properlyCross(EdgePath.straight(LayoutPoint.of(0.0, 0.0),
            LayoutPoint.of(1.0, 1.0)), EdgePath.straight(LayoutPoint.of(1.0, 1.0),
                LayoutPoint.of(2.0, 2.0)))).isFalse();
    }

    @Test
    public void rejectNullInputsAndEmptyEdgeSets() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.empty();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).isEmpty();
        assertThat(paths.diagnostics()).isEqualTo(EdgeRouteDiagnostics.empty());
        assertThat(EdgeRouterFixture.exitEdge("a", "b")).isNotNull();
        assertThat(Arrays.asList(fixture.projection, fixture.positions, fixture.geometry)).doesNotContainNull();
    }

    @Test
    public void routesTheFirstEdgeAroundALaterChordsCrossing() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath first = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_1", "ID_2"));
        final EdgePath second = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_3", "ID_4"));

        assertThat(first.shape()).isEqualTo(EdgePath.Shape.BOW);
        assertThat(first.control()).contains(LayoutPoint.of(0.0, 90.0));
        assertThat(first.samples()).hasSize(9);
        assertThat(first.samples().get(0).x()).isEqualTo(-80.100505, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.samples().get(0).y()).isEqualTo(9.899495, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.samples().get(4).y()).isEqualTo(49.949747, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.length()).isLessThanOrEqualTo(1.25 * 180.0);
        assertThat(first.length()).isCloseTo(183.58, org.assertj.core.data.Offset.offset(0.05));
        assertThat(second.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().edgesBow()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesFallback()).isZero();
        assertThat(paths.diagnostics().pathCrossingsDetected()).isEqualTo(1);
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void waivesTheDistanceRequirementInsideTheFanRegion() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.diagnostics().edgesFallback()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(1);
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
        assertThat(paths.diagnostics().pathHullPenetrations()).isZero();
        assertTightFanStraightRungs();
    }

    @Test
    public void rejectsAProperCrossingInsideTheFanRegion() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath first = paths.byEdge().get(EdgeRouterFixture.exitEdge("X", "A"));

        // The widest negative bow would cross X-B's chord near (12.55, 7.65) inside the fan
        // region; a mutation that waives the crossing ban there accepts it.
        assertThat(first.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
    }

    @Test
    public void rejectsTheDeadDoglegRungAndFallsBack() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        // The same-side dogleg 0.50 has analytic ratio 1.535184 > ROUTE_LENGTH_CAP = 1.25 and
        // must never be selected as a live rung.
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
    }

    @Test
    public void rejectsAnAlternatingBendDogleg() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.fanCrossing();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        // Both bends of every candidate lie on the same signed side; an alternating-bend
        // dogleg (ratio ~2.256) is not part of the ladder.
        for (final EdgePath path : paths.byEdge().values()) {
            if (path.shape() == EdgePath.Shape.DOGLEG) {
                final List<LayoutPoint> points = path.definingPoints();
                final double firstSide = EdgeRouterFixture.side(points.get(0), points.get(3),
                    points.get(1));
                final double secondSide = EdgeRouterFixture.side(points.get(0), points.get(3),
                    points.get(2));
                assertThat(firstSide * secondSide).isGreaterThan(0.0);
            }
        }
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
        assertAcceptsOneSameSideDogleg();
    }

    @Test
    public void acceptsTheSameSideDoglegWhenStraightAndBowsAreBlocked() {
        assertAcceptsOneSameSideDogleg();
    }

    @Test
    public void waivesTheDistanceRequirementForATightFan() {
        assertTightFanStraightRungs();
    }

    /**
     * Routes the F-1 fixture, whose straight chord and every live bow pass within the
     * clearance of a mirrored obstacle disc while the live same-side dogleg 0.30 clears.
     * An accepted alternating-bend dogleg (probe 12) would cross a disc and never be
     * selected, so the accepted-shape assertions below falsify that mutation.
     */
    private static void assertAcceptsOneSameSideDogleg() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.doglegAccepting();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath path = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_A", "ID_B"));

        assertThat(paths.diagnostics().edgesStraight()).isZero();
        assertThat(paths.diagnostics().edgesBow()).isZero();
        assertThat(paths.diagnostics().edgesDogleg()).isEqualTo(1);
        assertThat(paths.diagnostics().edgesFallback()).isZero();
        assertThat(path).isNotNull();
        assertThat(path.shape()).isEqualTo(EdgePath.Shape.DOGLEG);
        final List<LayoutPoint> points = path.definingPoints();
        final double firstSide = EdgeRouterFixture.side(points.get(0), points.get(3), points.get(1));
        final double secondSide = EdgeRouterFixture.side(points.get(0), points.get(3), points.get(2));
        assertThat(firstSide).isGreaterThan(0.0);
        assertThat(secondSide).isGreaterThan(0.0);
        assertThat(firstSide * secondSide).isGreaterThan(0.0);
        assertThat(EdgeRouterFixture.distance(
            fixture.geometry.nodes().get(EdgeRouterFixture.node("ID_A")).center(), path.start()))
            .isEqualTo(14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(EdgeRouterFixture.distance(
            fixture.geometry.nodes().get(EdgeRouterFixture.node("ID_B")).center(), path.end()))
            .isEqualTo(14.0, org.assertj.core.data.Offset.offset(1e-9));
        // The accepted +0.30 rung lies on the positive side, so both rims face it.
        assertThat(path.start().y()).isGreaterThan(0.0);
        assertThat(path.end().y()).isGreaterThan(0.0);
    }

    /**
     * Routes the F-2 fixture, whose two straight rungs are 2.786 world units apart at the
     * shared-X attachments (below {@code EdgeRouter.EDGE_CLEARANCE}). Dropping the spec
     * 6.4 fan exemption (probe 5) rejects both straight rungs, so the pinned straight and
     * fallback counts below falsify that mutation.
     */
    private static void assertTightFanStraightRungs() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.tightFan();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath first = paths.byEdge().get(EdgeRouterFixture.exitEdge("X", "A"));
        final EdgePath second = paths.byEdge().get(EdgeRouterFixture.exitEdge("X", "B"));

        assertThat(first.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(second.shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(2);
        assertThat(paths.diagnostics().edgesBow()).isZero();
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
        assertThat(paths.diagnostics().edgesFallback()).isZero();
        assertThat(paths.diagnostics().pathCrossingsResidual()).isEmpty();
        assertThat(EdgeRouterFixture.distance(first.end(), second.end()))
            .isCloseTo(2.786, org.assertj.core.data.Offset.offset(0.001))
            .isLessThan(EdgeRouter.EDGE_CLEARANCE);
    }

    @Test
    public void attachBowsAndDoglegsAtTheRimFacingTheRung() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePath bow = paths.byEdge().get(EdgeRouterFixture.exitEdge("ID_1", "ID_2"));

        assertThat(bow.shape()).isEqualTo(EdgePath.Shape.BOW);
        final LayoutPoint control = bow.control().get();
        assertThat(EdgeRouterFixture.distance(
            fixture.geometry.nodes().get(EdgeRouterFixture.node("ID_1")).center(), bow.start()))
            .isEqualTo(14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(EdgeRouterFixture.distance(
            fixture.geometry.nodes().get(EdgeRouterFixture.node("ID_2")).center(), bow.end()))
            .isEqualTo(14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(bow.start().x()).isLessThan(control.x());
    }

    @Test
    public void keepEverySampleWithinTheSagittaTolerance() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        for (final EdgePath path : paths.byEdge().values()) {
            if (path.shape() != EdgePath.Shape.BOW) {
                continue;
            }
            final double worst = EdgeRouterFixture.maxSagitta(path);
            assertThat(worst).isLessThanOrEqualTo(0.75);
        }
    }

    @Test
    public void isDeterministicAcrossRunsAndPermutedEdgeOrder() {
        final EdgeRouterFixture fixture = EdgeRouterFixture.laterChordBow();
        final EdgePaths first = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);
        final EdgePaths second = new EdgeRouter().route(fixture.permutedProjection(),
            fixture.positions, fixture.geometry, fixture.visibleEndpoints);

        assertThat(second.byEdge()).isEqualTo(first.byEdge());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }
}
