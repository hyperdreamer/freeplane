package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.Collections;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.Test;

public class GraphViewportShould {
    @Test
    public void roundTripWorldAndScreenCoordinates() {
        GraphViewport viewport = GraphViewport.of(20.0, -10.0, 2.0);
        Dimension size = new Dimension(200, 100);
        LayoutPoint world = LayoutPoint.of(23.25, -4.5);

        Point2D screen = viewport.toScreen(world, size);
        LayoutPoint roundTrip = viewport.toWorld(screen, size);

        assertThat(roundTrip.x()).isCloseTo(world.x(), within(1e-9));
        assertThat(roundTrip.y()).isCloseTo(world.y(), within(1e-9));
    }

    @Test
    public void rejectInvalidViewportValues() {
        assertThatThrownBy(() -> GraphViewport.of(Double.NaN, 0.0, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphViewport.of(0.0, Double.POSITIVE_INFINITY, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphViewport.of(0.0, 0.0, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphViewport.of(0.0, 0.0, -1.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphViewport.of(0.0, 0.0, Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphViewport.of(0.0, 0.0, Double.NEGATIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void preservePersistedValuesAndEmptyUnknownXml() {
        GraphViewport viewport = GraphViewport.from(Viewport.of(20.0, -10.0, 2.0,
            Collections.emptyList()));

        assertThat(viewport.centerX()).isEqualTo(20.0);
        assertThat(viewport.centerY()).isEqualTo(-10.0);
        assertThat(viewport.zoom()).isEqualTo(2.0);
        assertThat(viewport.toPersisted().unknownXml()).isEmpty();
    }

    @Test
    public void rejectMalformedPersistedValuesBeforeConversion() {
        assertThatThrownBy(() -> Viewport.of(Double.NaN, 0.0, 1.0, Collections.emptyList()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Viewport.of(0.0, Double.POSITIVE_INFINITY, 1.0, Collections.emptyList()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Viewport.of(0.0, 0.0, 0.0, Collections.emptyList()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void reportWorldBoundsThatIntersectTheVisibleRectangle() {
        GraphViewport viewport = GraphViewport.of(0.0, 0.0, 1.0);
        Dimension size = new Dimension(200, 100);

        assertThat(viewport.overlaps(-20.0, -10.0, 20.0, 10.0, size)).isTrue();
        assertThat(viewport.overlaps(101.0, 101.0, 120.0, 120.0, size)).isFalse();
    }

    @Test
    public void rejectMalformedWorldBounds() {
        GraphViewport viewport = GraphViewport.of(0.0, 0.0, 1.0);

        assertThatThrownBy(() -> viewport.overlaps(Double.NaN, 0.0, 1.0, 1.0, new Dimension(10, 10)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> viewport.overlaps(2.0, 0.0, 1.0, 1.0, new Dimension(10, 10)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void panInScreenPixelsAndZoomAroundThePointer() {
        GraphViewport viewport = GraphViewport.of(20.0, -10.0, 2.0);
        Dimension size = new Dimension(200, 100);
        Point2D pointer = new Point2D.Double(150.0, 25.0);
        LayoutPoint before = viewport.toWorld(pointer, size);

        GraphViewport panned = viewport.panPixels(20.0, -10.0);
        assertThat(panned.centerX()).isEqualTo(10.0);
        assertThat(panned.centerY()).isEqualTo(-5.0);

        GraphViewport zoomed = viewport.zoomAround(pointer, 2.0, size);
        assertThat(zoomed.toWorld(pointer, size).x()).isCloseTo(before.x(), within(1e-9));
        assertThat(zoomed.toWorld(pointer, size).y()).isCloseTo(before.y(), within(1e-9));
        assertThat(zoomed.zoom()).isEqualTo(4.0);
    }
}
