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
        // The reported fixture's four nodes and two crossing chords: the scaffold returns
        // two STRAIGHT paths and reports the crossing pair as a residual.
        final EdgeRouterFixture fixture = EdgeRouterFixture.reportedConvexQuad();
        final EdgePaths paths = new EdgeRouter().route(fixture.projection, fixture.positions,
            fixture.geometry, fixture.visibleEndpoints);

        assertThat(paths.byEdge()).hasSize(2);
        final List<EdgePath> values = new java.util.ArrayList<EdgePath>(paths.byEdge().values());
        assertThat(values.get(0).shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(values.get(1).shape()).isEqualTo(EdgePath.Shape.STRAIGHT);
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(2);
        assertThat(paths.diagnostics().edgesBow()).isZero();
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
        assertThat(paths.diagnostics().edgesFallback()).isZero();
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
}
