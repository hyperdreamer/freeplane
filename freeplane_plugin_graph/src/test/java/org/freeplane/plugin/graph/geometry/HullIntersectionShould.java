package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class HullIntersectionShould {
    @Test
    public void returnsZeroForDisjointOrMerelyTouchingHulls() {
        HullGeometry first = square(0, 0);
        assertPositiveZero(HullIntersection.minimumSeparatingTranslation(first, square(12, 0)));
        assertPositiveZero(HullIntersection.minimumSeparatingTranslation(first, square(10, 0)));
        assertPositiveZero(HullIntersection.minimumSeparatingTranslation(first, square(10, 10)));
        assertPositiveZero(HullIntersection.minimumSeparatingTranslation(first, translate(square(0, 0),
            LayoutPoint.of(12.0, 0.0))));
    }

    @Test
    public void returnsTheExactMinimumTranslationAppliedToTheSecondHull() {
        assertThat(HullIntersection.minimumSeparatingTranslation(square(0, 0), square(8, 0)))
            .isEqualTo(LayoutPoint.of(2.0, 0.0));
        HullGeometry diamond = hull(Arrays.asList(point(12, 0), point(17, 5), point(12, 10), point(7, 5)));
        assertThat(HullIntersection.minimumSeparatingTranslation(square(0, 0), diamond))
            .isEqualTo(LayoutPoint.of(3.0, 0.0));
    }

    @Test
    public void usesAxesFromBothConvexPolygons() {
        HullGeometry diamond = hull(Arrays.asList(point(0, -30), point(10, 0), point(0, 30), point(-10, 0)));
        HullGeometry triangle = hull(Arrays.asList(point(-25, 8), point(25, 8), point(25, 12)));
        LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(diamond, triangle);
        assertThat(translation.x()).isCloseTo(0.0, within(1e-9));
        assertThat(translation.y()).isCloseTo(22.0, within(1e-9));
        assertThat(HullIntersection.minimumSeparatingTranslation(diamond, translate(triangle, translation)))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));
    }

    @Test
    public void resolvesCoincidentAndEqualOverlapTiesDeterministically() {
        HullGeometry first = square(0, 0);
        LayoutPoint coincident = HullIntersection.minimumSeparatingTranslation(first, square(0, 0));
        assertThat(coincident).isEqualTo(LayoutPoint.of(10.0, 0.0));
        assertThat(HullIntersection.minimumSeparatingTranslation(first, square(0, 0))).isEqualTo(coincident);
        HullGeometry reversedSquare = hull(Arrays.asList(point(0, 10), point(10, 10), point(10, 0), point(0, 0)));
        assertThat(HullIntersection.minimumSeparatingTranslation(first, reversedSquare)).isEqualTo(coincident);
        assertThat(HullIntersection.minimumSeparatingTranslation(first, square(8, 8)))
            .isEqualTo(LayoutPoint.of(2.0, 0.0));
    }

    @Test
    public void separatesHugeFiniteOverlappingHulls() {
        HullGeometry first = hull(Arrays.asList(
            point(0.0, 0.0), point(1e160, 0.0), point(1e160, 1e160), point(0.0, 1e160)));
        HullGeometry second = hull(Arrays.asList(
            point(5e159, 0.0), point(1.5e160, 0.0), point(1.5e160, 1e160), point(5e159, 1e160)));

        LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(first, second);

        assertThat(translation.x()).isCloseTo(5e159, within(1e145));
        assertThat(translation.y()).isEqualTo(0.0);
        assertPositiveZero(HullIntersection.minimumSeparatingTranslation(first, translate(second, translation)));
    }

    @Test
    public void translatedHullHasNoPositiveAreaIntersectionAndReverseOrderNegatesTheVector() {
        HullGeometry first = square(0, 0);
        HullGeometry second = square(8, 0);
        LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(first, second);
        assertThat(translation).isEqualTo(LayoutPoint.of(2.0, 0.0));
        assertThat(HullIntersection.minimumSeparatingTranslation(first, translate(second, translation)))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));
        LayoutPoint reversed = HullIntersection.minimumSeparatingTranslation(second, first);
        assertThat(reversed.x()).isCloseTo(-2.0, within(1e-9));
        assertThat(reversed.y()).isCloseTo(0.0, within(1e-9));

        HullGeometry diamond = hull(Arrays.asList(point(0, -30), point(10, 0), point(0, 30), point(-10, 0)));
        HullGeometry triangle = hull(Arrays.asList(point(-25, 8), point(25, 8), point(25, 12)));
        LayoutPoint diagonal = HullIntersection.minimumSeparatingTranslation(diamond, triangle);
        assertThat(diagonal.x()).isCloseTo(0.0, within(1e-9));
        assertThat(diagonal.y()).isCloseTo(22.0, within(1e-9));
        assertThat(HullIntersection.minimumSeparatingTranslation(diamond, translate(triangle, diagonal)))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));
        LayoutPoint diagonalReversed = HullIntersection.minimumSeparatingTranslation(triangle, diamond);
        assertThat(diagonalReversed.x()).isCloseTo(0.0, within(1e-9));
        assertThat(diagonalReversed.y()).isCloseTo(-22.0, within(1e-9));
    }

    private static HullGeometry square(final double x, final double y) {
        return hull(Arrays.asList(point(x, y), point(x + 10.0, y), point(x + 10.0, y + 10.0), point(x, y + 10.0)));
    }

    private static HullGeometry hull(final List<LayoutPoint> points) {
        return HullGeometry.of(points, LayoutPoint.of(0.0, 0.0));
    }

    private static HullGeometry translate(final HullGeometry hull, final LayoutPoint vector) {
        List<LayoutPoint> moved = new ArrayList<LayoutPoint>();
        for (LayoutPoint vertex : hull.exactPolygon()) {
            moved.add(LayoutPoint.of(vertex.x() + vector.x(), vertex.y() + vector.y()));
        }
        return HullGeometry.of(moved, LayoutPoint.of(hull.labelAnchor().x() + vector.x(),
            hull.labelAnchor().y() + vector.y()));
    }

    private static void assertPositiveZero(final LayoutPoint translation) {
        assertThat(Double.doubleToLongBits(translation.x())).isEqualTo(Double.doubleToLongBits(0.0));
        assertThat(Double.doubleToLongBits(translation.y())).isEqualTo(Double.doubleToLongBits(0.0));
    }

    private static LayoutPoint point(final double x, final double y) {
        return LayoutPoint.of(x, y);
    }
}
