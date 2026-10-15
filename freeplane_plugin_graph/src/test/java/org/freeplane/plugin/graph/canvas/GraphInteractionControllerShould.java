package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.InputEvent;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.PinProjection;
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

import org.junit.Test;

public class GraphInteractionControllerShould {
    @Test
    public void viewportSettledCallbackRunsImmediatelyOnEdt() throws Exception {
        final AtomicReference<GraphCanvas> canvas = new AtomicReference<GraphCanvas>();
        final List<String> calls = new ArrayList<String>();
        SwingUtilities.invokeAndWait(() -> {
            canvas.set(new GraphCanvas());
            canvas.get().runWhenViewportSettled(() -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                calls.add("edt");
            });
            assertThat(calls).containsExactly("edt");
        });
        final AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();
        final Thread worker = new Thread(() -> {
            try {
                canvas.get().runWhenViewportSettled(() -> {
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                    calls.add("worker");
                });
            }
            catch (Throwable failure) {
                workerFailure.set(failure);
            }
        }, "viewport-settled-registration");
        worker.start();
        worker.join(5000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(workerFailure.get()).isNull();
        SwingUtilities.invokeAndWait(() -> assertThat(calls).containsExactly("edt", "worker"));
    }

    @Test
    public void viewportSettledCallbackWaitsForPositioningAndPendingClamps() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final CanvasCompletionProbe probe = CanvasCompletionProbe.settled();
        SwingUtilities.invokeAndWait(() -> {
            probe.begin();
            probe.begin();
            probe.scheduleClamp();
            probe.scheduleClamp();
            probe.canvas.runWhenViewportSettled(() -> {
                probe.assertSettled();
                calls.add("settled");
            });
            probe.end();
            assertThat(probe.counter("viewportPositioningDepth")).isEqualTo(1);
            assertThat(calls).isEmpty();
            probe.end();
            assertThat(probe.counter("pendingViewportClamps")).isEqualTo(2);
            assertThat(calls).isEmpty();
        });
        SwingUtilities.invokeAndWait(() -> {
            probe.assertSettled();
            assertThat(calls).containsExactly("settled");
            assertThat(probe.viewportEvents.get()).isZero();
        });
    }

    @Test
    public void viewportSettledRegistrationReplacesEarlierCallback() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final CanvasCompletionProbe probe = CanvasCompletionProbe.settled();
        SwingUtilities.invokeAndWait(() -> {
            probe.begin();
            probe.scheduleClamp();
            probe.canvas.runWhenViewportSettled(() -> calls.add("first"));
            probe.canvas.runWhenViewportSettled(() -> {
                probe.assertSettled();
                calls.add("second");
            });
            assertThat(probe.counter("viewportPositioningDepth")).isEqualTo(1);
            assertThat(probe.counter("pendingViewportClamps")).isEqualTo(1);
            probe.end();
            assertThat(calls).isEmpty();
        });
        SwingUtilities.invokeAndWait(() -> {
            probe.assertSettled();
            assertThat(calls).containsExactly("second");
            assertThat(probe.viewportEvents.get()).isZero();
        });
    }

    @Test
    public void viewportSettledCallbackExceptionDoesNotEscapeCompletion() throws Exception {
        for (final Throwable failure : Arrays.asList(new RuntimeException("callback runtime"),
                new AssertionError("callback error"))) {
            SwingUtilities.invokeAndWait(() -> {
                final CanvasCompletionProbe probe = new CanvasCompletionProbe();
                final AtomicInteger calls = new AtomicInteger();
                probe.begin();
                probe.canvas.runWhenViewportSettled(() -> {
                    calls.incrementAndGet();
                    if (failure instanceof Error) {
                        throw (Error) failure;
                    }
                    throw (RuntimeException) failure;
                });
                assertThatCode(probe::end).doesNotThrowAnyException();
                probe.assertSettled();
                assertThat(calls.get()).isEqualTo(1);
            });
        }
        SwingUtilities.invokeAndWait(() -> {
            final CanvasCompletionProbe probe = new CanvasCompletionProbe();
            final AtomicInteger logs = new AtomicInteger();
            final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(
                GraphCanvas.class.getName());
            final java.util.logging.Level oldLevel = logger.getLevel();
            final java.util.logging.Handler handler = new java.util.logging.Handler() {
                @Override
                public void publish(final java.util.logging.LogRecord record) {
                    logs.incrementAndGet();
                    throw new AssertionError("logger error");
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            logger.addHandler(handler);
            logger.setLevel(java.util.logging.Level.ALL);
            try {
                probe.begin();
                probe.canvas.runWhenViewportSettled(() -> {
                    throw new AssertionError("callback before logger error");
                });
                assertThatCode(probe::end).doesNotThrowAnyException();
                probe.assertSettled();
                assertThat(logs.get()).isEqualTo(1);
            }
            finally {
                logger.removeHandler(handler);
                logger.setLevel(oldLevel);
            }
        });
    }

    @Test
    public void viewportPositioningAccessorExcludesPendingClamps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final CanvasCompletionProbe probe = new CanvasCompletionProbe();
            probe.scheduleClamp();
            assertThat(probe.counter("viewportPositioningDepth")).isZero();
            assertThat(probe.counter("pendingViewportClamps")).isEqualTo(1);
            assertThat(probe.canvas.isViewportPositioningActive()).isFalse();
            assertThat(probe.canvas.isProgrammaticViewportChange()).isTrue();
            probe.begin();
            assertThat(probe.canvas.isViewportPositioningActive()).isTrue();
            probe.end();
            assertThat(probe.canvas.isViewportPositioningActive()).isFalse();
            assertThat(probe.canvas.isProgrammaticViewportChange()).isTrue();
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    public void viewportSettledCallbackRejectsNull() {
        assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
        final GraphCanvas canvas = new GraphCanvas();
        assertThatThrownBy(() -> canvas.runWhenViewportSettled(null))
            .isInstanceOf(NullPointerException.class).hasMessage("callback");
    }

    @Test
    public void viewportSettledCallbackCanRegisterAReplacementReentrantly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final CanvasCompletionProbe probe = new CanvasCompletionProbe();
            final List<String> calls = new ArrayList<String>();
            probe.begin();
            probe.canvas.runWhenViewportSettled(() -> {
                calls.add("first");
                probe.canvas.runWhenViewportSettled(() -> calls.add("second"));
            });
            probe.end();
            probe.begin();
            probe.end();
            assertThat(calls).containsExactly("first", "second");
        });
    }

    @Test
    public void hitNodeEndpointsFirstAndPreserveHullHitOrdering() {
        final Fixture fixture = Fixture.create();
        final GraphHitIndex index = GraphHitIndex.from(fixture.state);

        assertThat(index.endpointAt(LayoutPoint.of(-25.0, 0.0)))
            .contains(fixture.firstEndpoint);
        assertThat(index.endpointAt(LayoutPoint.of(-65.0, 0.0)))
            .contains(fixture.firstHullEndpoint);
        assertThat(index.edgeAt(LayoutPoint.of(0.0, 3.0), 4.0))
            .contains(fixture.edgeKey);
        assertThat(index.edgeAt(LayoutPoint.of(0.0, 10.0), 4.0)).isEmpty();
    }

    @Test
    public void hitAnEnclosureEdgeAtItsLayoutAnchoredPaintedSegment() {
        final AnchorFixture fixture = AnchorFixture.create();

        assertThat(GraphHitIndex.from(fixture.state).edgeAt(fixture.paintedSegmentPoint, 0.1))
            .contains(fixture.edgeKey);
    }

    @Test
    public void resolveFiniteExtremeSpanEdgesWithoutAcceptingAnOffSegmentPoint() {
        final ExtremeSpanFixture fixture = ExtremeSpanFixture.create();
        final GraphHitIndex index = GraphHitIndex.from(fixture.state);

        assertThat(index.edgeAt(LayoutPoint.of(0.0, 0.0), 1.0))
            .contains(fixture.edgeKey);
        assertThat(index.edgeAt(LayoutPoint.of(0.0, 100.0), 1.0)).isEmpty();
    }

    @Test
    public void hitAnOnSegmentQueryWithMixedMagnitudeCoordinates() {
        final CoordinateSegmentFixture fixture = CoordinateSegmentFixture.create(
            10.0, 1.0e308, 1.0e100, 1.0e308,
            "00000000-0000-0000-0000-000000000031",
            "00000000-0000-0000-0000-000000000032",
            "00000000-0000-0000-0000-000000000033");

        assertThat(GraphHitIndex.from(fixture.state).edgeAt(
            LayoutPoint.of(5.0e99, 1.0e308), 1.0)).contains(fixture.edgeKey);
    }

    @Test
    public void rejectATinyOffsetFromAnExtremeSpanEdge() {
        final CoordinateSegmentFixture fixture = CoordinateSegmentFixture.create(
            -8.0e307, 0.0, 8.0e307, 0.0,
            "00000000-0000-0000-0000-000000000034",
            "00000000-0000-0000-0000-000000000035",
            "00000000-0000-0000-0000-000000000036");

        assertThat(GraphHitIndex.from(fixture.state).edgeAt(
            LayoutPoint.of(0.0, 1.0e-20), 1.0e-100)).isEmpty();
    }

    @Test
    public void keepAFiniteInteriorProjectionNearTheLargeEndpoint() {
        final CoordinateSegmentFixture fixture = CoordinateSegmentFixture.create(
            -6.501096137849319e289, -5.744732131082845e-185,
            9.80045536779535e-123, -2.363358943315723e-104,
            "00000000-0000-0000-0000-000000000040",
            "00000000-0000-0000-0000-000000000041",
            "00000000-0000-0000-0000-000000000042", Double.MIN_VALUE);

        assertThat(GraphHitIndex.from(fixture.state).edgeAt(
            LayoutPoint.of(-1.5791917055093233e129, 1.335444456746397e-106),
            1.0e100)).contains(fixture.edgeKey);
    }

    @Test
    public void rejectPositiveSubnormalDistanceAtZeroTolerance() {
        final CoordinateSegmentFixture fixture = CoordinateSegmentFixture.create(
            0.0, 0.0, 1.0e308, 1.0e308,
            "00000000-0000-0000-0000-000000000037",
            "00000000-0000-0000-0000-000000000038",
            "00000000-0000-0000-0000-000000000039", Double.MIN_VALUE);

        assertThat(GraphHitIndex.from(fixture.state).edgeAt(
            LayoutPoint.of(0.0, Double.MIN_VALUE), 0.0)).isEmpty();
    }

    @Test
    public void rejectFiniteDistanceWhenRoundedCrossProductsCancel() {
        final CoordinateSegmentFixture fixture = CoordinateSegmentFixture.create(
            0.0, 0.0, 0x1.3bd3b7940e82fp500, 0x1.06a910103d249p500,
            "00000000-0000-0000-0000-000000000043",
            "00000000-0000-0000-0000-000000000044",
            "00000000-0000-0000-0000-000000000045", Double.MIN_VALUE);
        final GraphHitIndex index = GraphHitIndex.from(fixture.state);
        final LayoutPoint query = LayoutPoint.of(
            0x1.e6e58d07c40a9p499, 0x1.94ee99ea998b4p499);

        assertThat(index.edgeAt(query, 0.0)).isEmpty();
        assertThat(index.edgeAt(query, 1.0e133)).isEmpty();
        assertThat(index.edgeAt(query, 1.0e134)).contains(fixture.edgeKey);
    }

    @Test
    public void openAHitEndpointOnDoubleClickAndRejectSecondInstallation() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        assertThatThrownBy(() -> controller.install(fixture.canvas()))
            .isInstanceOf(IllegalStateException.class);
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 2,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).containsExactly(new GraphIntent.OpenSourceNode(
            fixture.firstEndpoint));
        controller.uninstall();
    }

    @Test
    public void panWhenDraggingEmptyCanvas() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        final double beforeCenterX = canvas.viewport().centerX();
        dispatch(canvas, press(canvas, 150.0, 80.0));
        dispatch(canvas, drag(canvas, 180.0, 80.0));
        dispatch(canvas, release(canvas, 180.0, 80.0));
        assertThat(canvas.viewport().centerX()).isNotEqualTo(beforeCenterX);
        assertThat(listener.intents).isEmpty();
        controller.uninstall();
    }

    private static ScrolledFixture scrolledFixture(final CanvasState state, final double zoom,
            final double centerX, final double centerY, final int scrollWidth, final int scrollHeight) {
        final GraphCanvas canvas = new GraphCanvas();
        final JScrollPane scrollPane = new JScrollPane(canvas);
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        scrollPane.setSize(scrollWidth, scrollHeight);
        scrollPane.doLayout();
        scrollPane.getViewport().doLayout();
        canvas.setCanvasState(state);
        canvas.setViewport(GraphViewport.of(centerX, centerY, zoom));
        scrollPane.doLayout();
        scrollPane.getViewport().doLayout();
        return new ScrolledFixture(canvas, scrollPane, controller, listener);
    }

    private static final class ScrolledFixture {
        private final GraphCanvas canvas;
        private final JScrollPane scrollPane;
        private final GraphInteractionController controller;
        private final RecordingListener listener;

        private ScrolledFixture(final GraphCanvas canvas, final JScrollPane scrollPane,
                final GraphInteractionController controller, final RecordingListener listener) {
            this.canvas = canvas;
            this.scrollPane = scrollPane;
            this.controller = controller;
            this.listener = listener;
        }
    }

    private static final String PAN_MAP_ID = "00000000-0000-0000-0000-0000000000ff";
    private static final double PAN_NODE_RADIUS = 20.0;
    private static final double[][] PAN_NODE_CENTERS = {
        { -300.0, -80.0 }, { -150.0, 60.0 }, { 0.0, -60.0 },
        { 120.0, 80.0 }, { 300.0, 0.0 }, { 60.0, 140.0 }
    };

    private static CanvasState panFixtureState() {
        final MapReferenceId map = MapReferenceId.of(UUID.fromString(PAN_MAP_ID));
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final Map<ProjectedNodeKey, NodeGeometry> geometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        final Map<ProjectedNodeKey, LayoutPoint> positions =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < PAN_NODE_CENTERS.length; index++) {
            final NodeReference reference = NodeReference.of(map,
                PersistedNodeId.of("node-" + index));
            final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(reference));
            final LayoutPoint point =
                LayoutPoint.of(PAN_NODE_CENTERS[index][0], PAN_NODE_CENTERS[index][1]);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node " + index, "Node " + index),
                "Map", false));
            geometry.put(key, NodeGeometry.of(point, PAN_NODE_RADIUS));
            positions.put(key, point);
        }
        final GraphProjection projection = GraphProjection.projected(1L, nodes,
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.emptyList(), Collections.<PinProjection>emptyList());
        final GraphGeometry graphGeometry = GraphGeometry.of(geometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final LayoutFrame frame = LayoutFrame.of(1L, LayoutPositions.of(positions,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false, 0);
        return CanvasState.of(1L, projection, frame, graphGeometry, OperationalStatus.IDLE);
    }

    private static ScrolledFixture scrolledPanFixture(final double zoom) {
        return scrolledFixture(panFixtureState(), zoom, 0.0, 0.0, 900, 700);
    }

    @Test
    public void applyTheViewPositionWhenConvertingComponentPointToViewportCoordinates() {
        final ScrolledFixture fixture = scrolledPanFixture(1.5);
        try {
            fixture.scrollPane.getViewport().setViewPosition(new Point(60, 20));
            assertThat(fixture.scrollPane.getViewport().getViewPosition())
                .isEqualTo(new Point(60, 20));
            assertThat(fixture.canvas.viewportPoint(new Point(200, 150)))
                .isEqualTo(new Point(140, 130));
        }
        finally {
            fixture.controller.uninstall();
        }
    }

    @Test
    public void returnTheSamePointWhenConvertingWithoutAViewportAncestor() {
        final GraphCanvas canvas = new GraphCanvas();
        final Point point = new Point(7, 9);
        assertThat(canvas.viewportPoint(point)).isSameAs(point);
    }

    @Test
    public void rejectANullComponentPoint() {
        final GraphCanvas canvas = new GraphCanvas();
        assertThatThrownBy(() -> canvas.viewportPoint(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("componentPoint");
    }

    private static MouseEvent gestureEvent(final GraphCanvas canvas, final JScrollPane scrollPane,
            final int id, final double viewportX, final double viewportY) {
        final Point viewPosition = scrollPane.getViewport().getViewPosition();
        final int componentX = (int) Math.round(viewportX + viewPosition.x);
        final int componentY = (int) Math.round(viewportY + viewPosition.y);
        return new MouseEvent(canvas, id, System.currentTimeMillis(), 0, componentX, componentY, 1,
            false, MouseEvent.BUTTON1);
    }

    private static double contentX(final GraphCanvas canvas, final JScrollPane scrollPane) {
        return canvas.getWidth() / 2.0
            + (0.0 - canvas.viewport().centerX()) * canvas.viewport().zoom()
            - scrollPane.getViewport().getViewPosition().x;
    }

    private static double contentY(final GraphCanvas canvas, final JScrollPane scrollPane) {
        return canvas.getHeight() / 2.0
            + (0.0 - canvas.viewport().centerY()) * canvas.viewport().zoom()
            - scrollPane.getViewport().getViewPosition().y;
    }

    private static final double PAN_THRESHOLD_DEAD_ZONE_PIXELS = 3.0;
    private static final double PAN_MAX_CONTENT_STEP_PIXELS = 1.5 + 1e-6;

    private static GestureResult dragGesture(final ScrolledFixture fixture, final double directionX,
            final double directionY, final int events, final double fingerX0, final double fingerY0) {
        final GraphCanvas canvas = fixture.canvas;
        final JScrollPane scrollPane = fixture.scrollPane;
        dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_PRESSED, fingerX0, fingerY0));
        final double startX = contentX(canvas, scrollPane);
        final double startY = contentY(canvas, scrollPane);
        double previousX = startX;
        double previousY = startY;
        double maxStepX = 0.0;
        double maxStepY = 0.0;
        final List<Double> centersX = new ArrayList<Double>();
        final List<Double> centersY = new ArrayList<Double>();
        double fingerX = fingerX0;
        double fingerY = fingerY0;
        for (int index = 0; index < events; index++) {
            fingerX += directionX;
            fingerY += directionY;
            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_DRAGGED, fingerX, fingerY));
            final double currentX = contentX(canvas, scrollPane);
            final double currentY = contentY(canvas, scrollPane);
            maxStepX = Math.max(maxStepX, Math.abs(currentX - previousX));
            maxStepY = Math.max(maxStepY, Math.abs(currentY - previousY));
            previousX = currentX;
            previousY = currentY;
            centersX.add(canvas.viewport().centerX());
            centersY.add(canvas.viewport().centerY());
        }
        dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_RELEASED, fingerX, fingerY));
        return new GestureResult(previousX - startX, previousY - startY, maxStepX, maxStepY,
            centersX, centersY);
    }

    private static final class GestureResult {
        private final double contentTravelX;
        private final double contentTravelY;
        private final double maxStepX;
        private final double maxStepY;
        private final List<Double> centersX;
        private final List<Double> centersY;

        private GestureResult(final double contentTravelX, final double contentTravelY,
                final double maxStepX, final double maxStepY, final List<Double> centersX,
                final List<Double> centersY) {
            this.contentTravelX = contentTravelX;
            this.contentTravelY = contentTravelY;
            this.maxStepX = maxStepX;
            this.maxStepY = maxStepY;
            this.centersX = centersX;
            this.centersY = centersY;
        }
    }

    private static void assertTracksCursor(final GestureResult result, final double directionX,
            final double directionY, final int events, final String description) {
        assertAxisTracks(result.contentTravelX, directionX, events, description + " x");
        assertAxisTracks(result.contentTravelY, directionY, events, description + " y");
        assertThat(result.maxStepX).as(description + " max x step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertThat(result.maxStepY).as(description + " max y step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertMonotonicCenter(result.centersX, directionX, description + " x");
        assertMonotonicCenter(result.centersY, directionY, description + " y");
    }

    private static void assertAxisTracks(final double travel, final double direction,
            final int events, final String description) {
        if (direction == 0.0) {
            assertThat(travel).as(description).isEqualTo(0.0);
            return;
        }
        assertThat(travel * direction).as(description)
            .isBetween(events - PAN_THRESHOLD_DEAD_ZONE_PIXELS, (double) events);
    }

    private static void assertMonotonicCenter(final List<Double> centers, final double direction,
            final String description) {
        for (int index = 1; index < centers.size(); index++) {
            if (direction > 0.0) {
                assertThat(centers.get(index)).as(description)
                    .isLessThanOrEqualTo(centers.get(index - 1));
            }
            else if (direction < 0.0) {
                assertThat(centers.get(index)).as(description)
                    .isGreaterThanOrEqualTo(centers.get(index - 1));
            }
            else {
                assertThat(centers.get(index)).as(description).isEqualTo(centers.get(index - 1));
            }
        }
        if (direction != 0.0) {
            assertThat(centers.get(centers.size() - 1)).as(description)
                .isNotEqualTo(centers.get(0));
        }
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasRightAtZoomOnePointFive() {
        assertRightDragTracksAtZoom(1.5);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasRightAtZoomOne() {
        assertRightDragTracksAtZoom(1.0);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasRightAtZoomFive() {
        assertRightDragTracksAtZoom(5.0);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasLeftAtZoomOne() {
        assertLeftDragTracksAtZoom(1.0);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasLeftAtZoomOnePointFive() {
        assertLeftDragTracksAtZoom(1.5);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasLeftAtZoomFive() {
        assertLeftDragTracksAtZoom(5.0);
    }

    private static void assertLeftDragTracksAtZoom(final double zoom) {
        final ScrolledFixture fixture = scrolledPanFixture(zoom);
        try {
            final GestureResult result = dragGesture(fixture, -1.0, 0.0, 60, 600.0, 650.0);
            assertTracksCursor(result, -1.0, 0.0, 60, "left drag at zoom " + zoom);
        }
        finally {
            fixture.controller.uninstall();
        }
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasDiagonallyAtZoomOnePointFive() {
        final ScrolledFixture fixture = scrolledPanFixture(1.5);
        try {
            final GestureResult result = dragGesture(fixture, 1.0, 1.0, 60, 600.0, 650.0);
            assertDiagonalTracksAtZoomOnePointFive(result);
        }
        finally {
            fixture.controller.uninstall();
        }
    }

    private static void assertDiagonalTracksAtZoomOnePointFive(final GestureResult result) {
        assertThat(result.contentTravelX).as("diagonal drag at zoom 1.5 x")
            .isBetween(57.0, 60.0);
        assertThat(result.contentTravelY).as("diagonal drag at zoom 1.5 y")
            .isBetween(57.0, 69.0);
        assertThat(result.maxStepX).as("diagonal drag at zoom 1.5 max x step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertThat(result.maxStepY).as("diagonal drag at zoom 1.5 max y step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertMonotonicCenter(result.centersX, 1.0, "diagonal drag at zoom 1.5 x");
        assertMonotonicCenter(result.centersY, 1.0, "diagonal drag at zoom 1.5 y");
    }

    private static void assertRightDragTracksAtZoom(final double zoom) {
        final ScrolledFixture fixture = scrolledPanFixture(zoom);
        try {
            final GestureResult result = dragGesture(fixture, 1.0, 0.0, 60, 600.0, 650.0);
            assertTracksCursor(result, 1.0, 0.0, 60, "right drag at zoom " + zoom);
        }
        finally {
            fixture.controller.uninstall();
        }
    }

    @Test
    public void doNotPanWhenAProgrammaticScrollMovesTheViewportUnderAStationaryPointer() {
        final ScrolledFixture fixture = scrolledPanFixture(1.5);
        try {
            final GraphCanvas canvas = fixture.canvas;
            final JScrollPane scrollPane = fixture.scrollPane;
            final double beforeDragCenterX = canvas.viewport().centerX();
            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_PRESSED, 600.0, 650.0));
            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_DRAGGED, 610.0, 650.0));
            final double centerX = canvas.viewport().centerX();
            final double centerY = canvas.viewport().centerY();
            assertThat(centerX).isNotEqualTo(beforeDragCenterX);

            final Point viewPosition = scrollPane.getViewport().getViewPosition();
            final Dimension extent = scrollPane.getViewport().getExtentSize();
            final int maxX = Math.max(0, canvas.getWidth() - extent.width);
            final int maxY = Math.max(0, canvas.getHeight() - extent.height);
            final int deltaX = Math.min(40, maxX - viewPosition.x);
            final int deltaY = Math.min(20, maxY - viewPosition.y);
            assertThat(deltaX).isGreaterThan(0);
            assertThat(deltaY).isGreaterThan(0);
            scrollPane.getViewport().setViewPosition(
                new Point(viewPosition.x + deltaX, viewPosition.y + deltaY));
            assertThat(scrollPane.getViewport().getViewPosition())
                .isEqualTo(new Point(viewPosition.x + deltaX, viewPosition.y + deltaY));

            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_DRAGGED, 610.0, 650.0));

            assertThat(canvas.viewport().centerX()).isEqualTo(centerX);
            assertThat(canvas.viewport().centerY()).isEqualTo(centerY);
        }
        finally {
            fixture.controller.uninstall();
        }
    }

    @Test
    public void hitTheEndpointUnderThePointerWhenTheViewportIsScrolled() {
        final Fixture fixture = Fixture.create();
        final ScrolledFixture scrolled = scrolledFixture(fixture.state, 1.0, 0.0, 0.0, 300, 200);
        try {
            final GraphCanvas canvas = scrolled.canvas;
            final JScrollPane scrollPane = scrolled.scrollPane;
            scrollPane.getViewport().setViewPosition(new Point(100, 80));
            assertThat(scrollPane.getViewport().getViewPosition()).isEqualTo(new Point(100, 80));

            final Dimension size = new Dimension(canvas.getWidth(), canvas.getHeight());
            final Point2D node = canvas.viewport().toScreen(-40.0, 0.0, size);
            final int x = (int) Math.round(node.getX());
            final int y = (int) Math.round(node.getY());
            dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, x, y, 1, MouseEvent.BUTTON1));
            assertThat(scrolled.listener.intents).containsExactly(
                new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.RevealSourceNode(fixture.firstEndpoint));

            dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, x, y, 2, MouseEvent.BUTTON1));
            assertThat(scrolled.listener.intents).containsExactly(
                new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.RevealSourceNode(fixture.firstEndpoint),
                new GraphIntent.OpenSourceNode(fixture.firstEndpoint));

            final LayoutPoint before = canvas.viewport().toWorld((double) x, (double) y, size);
            dispatch(canvas, wheel(canvas, x, y, -1));
            final LayoutPoint after = canvas.viewport().toWorld((double) x, (double) y, size);
            assertThat(canvas.viewport().zoom()).isGreaterThan(1.0);
            assertThat(after.x()).isCloseTo(before.x(), within(1e-9));
            assertThat(after.y()).isCloseTo(before.y(), within(1e-9));
        }
        finally {
            scrolled.controller.uninstall();
        }
    }

    @Test
    public void cancelSelfConnectWithoutEmittingAConnectIntent() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        controller.setTool(InteractionTool.CONNECT);

        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, -40.0, 0.0));
        assertThat(canvas.paintState().connectionPreview()).isPresent();
        dispatch(canvas, release(canvas, -40.0, 0.0));
        assertThat(canvas.paintState().connectionPreview()).isEmpty();
        assertThat(listener.intents).isEmpty();
        controller.uninstall();
    }

    @Test
    public void emitTheConfiguredRelationshipDirectionForConnections() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        controller.setTool(InteractionTool.CONNECT);
        controller.setRelationshipDirection(RelationshipDirection.BIDIRECTIONAL);

        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, 40.0, 0.0));
        dispatch(canvas, release(canvas, 40.0, 0.0));
        assertThat(listener.intents).containsExactly(new GraphIntent.Connect(fixture.firstEndpoint,
            fixture.secondEndpoint, RelationshipDirection.BIDIRECTIONAL));
        controller.uninstall();
    }

    @Test
    public void useStableSharedHullEndpointOrderAndExcludeSuppressedHulls() {
        final Fixture fixture = Fixture.create();
        final GraphHitIndex index = GraphHitIndex.from(fixture.state);

        assertThat(fixture.firstHullEndpoint.compareTo(fixture.secondHullEndpoint)).isLessThan(0);
        assertThat(index.endpointAt(LayoutPoint.of(-65.0, 0.0)))
            .contains(fixture.firstHullEndpoint);
        assertThat(index.endpointAt(LayoutPoint.of(95.0, 0.0))).isEmpty();
    }

    @Test
    public void cancelConnectionPreviewWhenANodeIsRemovedFromCurrentCanvasState() {
        final Fixture fixture = Fixture.create();

        assertConnectionPreviewCancelledAfterStateReplacement(fixture, fixture.firstEndpoint, -40.0, 0.0,
            stateWithoutFirstNode(fixture));
    }

    @Test
    public void cancelConnectionPreviewWhenANodeSourceLosesCurrentGeometry() {
        final Fixture fixture = Fixture.create();

        assertConnectionPreviewCancelledAfterStateReplacement(fixture, fixture.firstEndpoint, -40.0, 0.0,
            stateWithoutFirstNodeGeometry(fixture));
    }

    @Test
    public void keepNodeConnectionPreviewWhenOnlyABoundaryIsSuppressed() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        controller.setTool(InteractionTool.CONNECT);

        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, -35.0, 2.0));
        assertThat(canvas.paintState().connectionPreview()).isPresent();

        canvas.setCanvasState(stateWithSuppressedFirstEnclosure(fixture));

        assertThat(canvas.paintState().connectionPreview()).isPresent();
        dispatch(canvas, release(canvas, 40.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Connect(fixture.firstEndpoint,
            fixture.secondEndpoint, RelationshipDirection.FORWARD));
        controller.uninstall();
    }

    @Test
    public void boundariesStaySelectableButNeverBecomeConnectOrPinEndpoints() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        // A hull-only spot inside the first boundary but outside the node disc.
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -65.0, 20.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).containsExactly(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstHullEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstHullEndpoint));

        controller.setTool(InteractionTool.CONNECT);
        dispatch(canvas, press(canvas, -65.0, 20.0));
        dispatch(canvas, drag(canvas, 40.0, 0.0));
        assertThat(canvas.paintState().connectionPreview()).isEmpty();
        dispatch(canvas, release(canvas, 40.0, 0.0));
        assertThat(listener.intents).hasSize(2);

        controller.setTool(InteractionTool.SELECT);
        dispatch(canvas, press(canvas, -65.0, 20.0));
        dispatch(canvas, drag(canvas, -50.0, 25.0));
        dispatch(canvas, release(canvas, -50.0, 25.0));
        assertThat(listener.intents).hasSize(2);
        controller.uninstall();
    }

    @Test
    public void rejectConnectCompletionUnlessBothEndpointsAreNodes() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        controller.setTool(InteractionTool.CONNECT);

        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, -65.0, 20.0));
        dispatch(canvas, release(canvas, -65.0, 20.0));
        assertThat(listener.intents).isEmpty();
        assertThat(canvas.paintState().connectionPreview()).isEmpty();

        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, 40.0, 0.0));
        dispatch(canvas, release(canvas, 40.0, 0.0));
        assertThat(listener.intents).containsExactly(new GraphIntent.Connect(fixture.firstEndpoint,
            fixture.secondEndpoint, RelationshipDirection.FORWARD));
        controller.uninstall();
    }

    @Test
    public void omitSuppressedEdgesFromHitTestingWhileRetainingVisibleEnclosureEdges() {
        final Fixture fixture = Fixture.create();
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(fixture.firstNodeKey.source(),
            NodeReference.of(fixture.firstNodeKey.mapReferenceId(), PersistedNodeId.of("suppressed-edge")),
            false, true, "source", "middle", "target");
        final ProjectedEdge suppressedEdge = ProjectedEdge.of(
            ProjectedEdgeKey.of(fixture.firstEndpoint, fixture.suppressedEndpoint),
            Collections.singletonList(EdgeContributor.nativeConnector(
                ConnectorSnapshot.of(0, descriptor), fixture.firstEndpoint, fixture.suppressedEndpoint)));
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>(fixture.state.projection().edges());
        edges.add(suppressedEdge);
        final GraphProjection projection = GraphProjection.projected(1L,
            fixture.state.projection().nodes(), fixture.state.projection().enclosures(), edges,
            fixture.state.projection().relationshipResolutions(), fixture.state.projection().pins());
        final CanvasState state = CanvasState.of(1L, projection, fixture.state.layout(),
            fixture.state.geometry(), fixture.state.status());
        final LayoutPoint sourceCenter = fixture.state.geometry().nodes().get(fixture.firstNodeKey).center();
        final LayoutPoint targetAnchor = LayoutPoint.of(95.0, 0.0);
        final LayoutPoint first = fixture.state.geometry().edgeAttachment(fixture.firstEndpoint, targetAnchor);
        final LayoutPoint second = fixture.state.geometry().edgeAttachment(fixture.suppressedEndpoint,
            sourceCenter);
        final LayoutPoint query = LayoutPoint.of(first.x() + (second.x() - first.x()) * 0.8,
            first.y() + (second.y() - first.y()) * 0.8);

        final GraphHitIndex index = GraphHitIndex.from(state);

        assertThat(index.edgeAt(query, 0.1)).isEmpty();
        final AnchorFixture control = AnchorFixture.create();
        assertThat(GraphHitIndex.from(control.state).edgeAt(control.paintedSegmentPoint, 0.1))
            .contains(control.edgeKey);
    }

    @Test
    public void translateHoverPinAndContextActions() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, move(canvas, -40.0, 0.0));
        assertThat(canvas.paintState().hover()).contains(fixture.firstEndpoint);
        assertThat(canvas.paintState().dimUnrelated()).isTrue();
        assertThat(canvas.getToolTipText()).contains("First full label").contains("Map");
        dispatch(canvas, exit(canvas));
        assertThat(canvas.paintState().hover()).isEmpty();
        assertThat(canvas.paintState().dimUnrelated()).isFalse();
        assertThat(canvas.getToolTipText()).isNull();

        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, -15.0, 15.0));
        dispatch(canvas, release(canvas, -15.0, 15.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Pin(fixture.firstNodeKey,
            -15.0, 15.0));

        dispatch(canvas, context(canvas, 40.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Unpin(fixture.secondNodeKey));
        dispatch(canvas, context(canvas, 0.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.InspectEdge(fixture.edgeKey));

        controller.unpinAll();
        assertThat(listener.last()).isEqualTo(new GraphIntent.UnpinAll());
        controller.deleteContributor(fixture.contributorKey);
        assertThat(listener.last()).isEqualTo(new GraphIntent.DeleteContributor(
            fixture.contributorKey));
        controller.deleteAllContributors(fixture.edgeKey,
            Collections.singletonList(fixture.contributorKey));
        assertThat(listener.last()).isEqualTo(new GraphIntent.DeleteAllContributors(fixture.edgeKey,
            Collections.singletonList(fixture.contributorKey)));
        controller.uninstall();
        assertThat(canvas.getMouseListeners()).isEmpty();
        assertThat(canvas.getMouseMotionListeners()).isEmpty();
        assertThat(canvas.getMouseWheelListeners()).isEmpty();
        assertThat(canvas.getKeyListeners()).isEmpty();
    }

    @Test
    public void togglesPinOnTheNodeUnderTheContextClickAtItsCentre() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, context(canvas, -30.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Pin(fixture.firstNodeKey, -40.0, 0.0));
        for (GraphIntent intent : listener.intents) {
            assertThat(intent).isNotInstanceOf(GraphIntent.ChangeSelection.class);
        }

        dispatch(canvas, context(canvas, 35.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Unpin(fixture.secondNodeKey));

        dispatch(canvas, context(canvas, 0.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.InspectEdge(fixture.edgeKey));
        controller.uninstall();
    }

    @Test
    public void dispatchesAtMostOneContextActionPerGesture() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, click(canvas, MouseEvent.MOUSE_PRESSED, -30.0, 0.0, 1, MouseEvent.BUTTON3));
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_RELEASED, -30.0, 0.0, 1, MouseEvent.BUTTON3));
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -30.0, 0.0, 1, MouseEvent.BUTTON3));

        assertThat(listener.intents).hasSize(1);
        assertThat(listener.last()).isEqualTo(new GraphIntent.Pin(fixture.firstNodeKey, -40.0, 0.0));
        controller.uninstall();
    }

    @Test
    public void escapeFallbackRunsWhenPressStartsWithNoPreviewAndNoSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final EscapeFixture fixture = new EscapeFixture();
            try {
                assertThat(fixture.canvas.paintState().connectionPreview()).isEmpty();
                assertThat(fixture.canvas.paintState().selection()).isEmpty();
                final KeyEvent event = key(fixture.canvas, KeyEvent.VK_ESCAPE, 0);
                fixture.controller.setEscapeFallback(() -> {
                    assertThat(event.isConsumed()).isTrue();
                    fixture.assertSelectionClearedIntent();
                    fixture.fallbackCalls.incrementAndGet();
                });
                dispatchKey(fixture.canvas, event);
                assertThat(fixture.fallbackCalls.get()).isEqualTo(1);
                fixture.assertSelectionClearedIntent();
                assertThat(event.isConsumed()).isTrue();

                fixture.controller.setEscapeFallback(null);
                fixture.listener.intents.clear();
                final KeyEvent withoutFallback = fixture.escape();
                assertThat(withoutFallback.isConsumed()).isTrue();
                assertThat(fixture.fallbackCalls.get()).isEqualTo(1);
                fixture.assertSelectionClearedIntent();
            }
            finally {
                fixture.controller.uninstall();
            }
        });
    }

    @Test
    public void escapeFallbackDoesNotRunWhenPressStartsWithPreview() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final EscapeFixture fixture = new EscapeFixture();
            try {
                fixture.preview();
                assertThat(fixture.canvas.paintState().connectionPreview()).isPresent();
                assertThat(fixture.canvas.paintState().selection()).isEmpty();
                fixture.listener.intents.clear();
                final KeyEvent event = fixture.escape();
                assertThat(fixture.fallbackCalls.get()).isZero();
                assertThat(event.isConsumed()).isTrue();
                assertThat(fixture.canvas.paintState().connectionPreview()).isEmpty();
                assertThat(fixture.listener.intents).isEmpty();
            }
            finally {
                fixture.controller.uninstall();
            }
        });
    }

    @Test
    public void escapeFallbackDoesNotRunWhenPressStartsWithSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final EscapeFixture fixture = new EscapeFixture();
            try {
                fixture.select();
                assertThat(fixture.canvas.paintState().selection()).contains(fixture.source.firstEndpoint);
                assertThat(fixture.canvas.paintState().connectionPreview()).isEmpty();
                fixture.listener.intents.clear();
                final KeyEvent event = fixture.escape();
                assertThat(fixture.fallbackCalls.get()).isZero();
                assertThat(event.isConsumed()).isTrue();
                assertThat(fixture.canvas.paintState().selection()).isEmpty();
                fixture.assertSelectionClearedIntent();
            }
            finally {
                fixture.controller.uninstall();
            }
        });
    }

    @Test
    public void escapeIsConsumedForPreviewSelectionAndFallbackCases() throws Exception {
        for (int startingState = 0; startingState < 3; startingState++) {
            final int selectedState = startingState;
            SwingUtilities.invokeAndWait(() -> {
                final EscapeFixture fixture = new EscapeFixture();
                try {
                    if (selectedState == 0) {
                        fixture.preview();
                    }
                    else if (selectedState == 1) {
                        fixture.select();
                    }
                    fixture.listener.intents.clear();
                    final KeyEvent event = fixture.escape();
                    assertThat(event.isConsumed()).isTrue();
                    assertThat(fixture.fallbackCalls.get()).isEqualTo(selectedState == 2 ? 1 : 0);
                    if (selectedState == 0) {
                        assertThat(fixture.listener.intents).isEmpty();
                    }
                    else {
                        fixture.assertSelectionClearedIntent();
                    }
                }
                finally {
                    fixture.controller.uninstall();
                }
            });
        }
    }

    @Test
    public void cancelConnectionPreviewBeforeClearingSelection() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        controller.setTool(InteractionTool.CONNECT);
        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, 0.0, 20.0));
        assertThat(canvas.paintState().connectionPreview()).isPresent();
        final int intentsBeforeEscape = listener.intents.size();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_ESCAPE, 0));
        assertThat(canvas.paintState().connectionPreview()).isEmpty();
        assertThat(canvas.paintState().selection()).contains(fixture.firstEndpoint);
        assertThat(listener.intents).hasSize(intentsBeforeEscape);
        dispatchKey(canvas, key(canvas, KeyEvent.VK_ESCAPE, 0));
        assertThat(canvas.paintState().selection()).isEmpty();
        assertThat(listener.last()).isEqualTo(new GraphIntent.ChangeSelection(
            Optional.<ProjectedEndpointKey>empty()));
        controller.uninstall();
    }

    @Test
    public void translateSelectionConnectionZoomPanAndUninstallGestures() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).contains(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstEndpoint));
        assertThat(canvas.paintState().selection()).contains(fixture.firstEndpoint);

        final double beforeZoom = canvas.viewport().zoom();
        final LayoutPoint pointerWorld = canvas.viewport().toWorld(260.0, 150.0,
            new Dimension(canvas.getWidth(), canvas.getHeight()));
        dispatch(canvas, wheel(canvas, 260, 150, -1));
        assertThat(canvas.viewport().zoom()).isGreaterThan(beforeZoom);
        assertThat(canvas.viewport().toWorld(260.0, 150.0,
            new Dimension(canvas.getWidth(), canvas.getHeight())).x())
            .isEqualTo(pointerWorld.x());

        final double beforePan = canvas.viewport().centerX();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, 0));
        assertThat(canvas.viewport().centerX()).isEqualTo(beforePan);
        dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, 390, 290, 1,
            MouseEvent.BUTTON1));
        final double beforeNoSelectionPan = canvas.viewport().centerX();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, 0));
        assertThat(canvas.viewport().centerX()).isNotEqualTo(beforeNoSelectionPan);
        final double beforeShift = canvas.viewport().centerX();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK));
        assertThat(canvas.viewport().centerX()).isNotEqualTo(beforeShift);

        controller.setTool(InteractionTool.CONNECT);
        dispatch(canvas, press(canvas, -40.0, 0.0));
        dispatch(canvas, drag(canvas, 40.0, 0.0));
        assertThat(canvas.paintState().connectionPreview()).isPresent();
        dispatch(canvas, release(canvas, 40.0, 0.0));
        assertThat(listener.last()).isInstanceOf(GraphIntent.Connect.class);
        final GraphIntent.Connect connect = (GraphIntent.Connect) listener.last();
        assertThat(connect.source()).isEqualTo(fixture.firstEndpoint);
        assertThat(connect.target()).isEqualTo(fixture.secondEndpoint);
        assertThat(connect.direction()).isEqualTo(RelationshipDirection.FORWARD);
        assertThat(canvas.paintState().connectionPreview()).isEmpty();

        final int intentCount = listener.intents.size();
        controller.uninstall();
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).hasSize(intentCount);
        assertThat(canvas.getMouseListeners()).isEmpty();
        assertThat(canvas.getMouseMotionListeners()).isEmpty();
        assertThat(canvas.getMouseWheelListeners()).isEmpty();
        assertThat(canvas.getKeyListeners()).isEmpty();
    }

    @Test
    public void revealHitEndpointSourceOnSingleClickWhileEmptySpaceOnlyClearsSelection() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).containsExactly(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstEndpoint));

        dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, 390, 290, 1,
            MouseEvent.BUTTON1));
        assertThat(listener.intents).containsExactly(
            new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
            new GraphIntent.RevealSourceNode(fixture.firstEndpoint),
            new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        controller.uninstall();
    }

    private static void assertConnectionPreviewCancelledAfterStateReplacement(final Fixture fixture,
            final ProjectedEndpointKey source, final double sourceX, final double sourceY,
            final CanvasState replacement) {
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        controller.setTool(InteractionTool.CONNECT);

        dispatch(canvas, press(canvas, sourceX, sourceY));
        dispatch(canvas, drag(canvas, sourceX + 5.0, sourceY));
        assertThat(canvas.paintState().connectionPreview()).isPresent();

        canvas.setCanvasState(replacement);

        assertThat(canvas.paintState().connectionPreview()).isEmpty();
        assertThat(canvas.paintState().selection()).isEmpty();
        dispatch(canvas, release(canvas, 40.0, 0.0));
        assertThat(listener.intents).isEmpty();
        assertThat(canvas.paintState().selection()).isEmpty();
        controller.uninstall();
    }

    private static CanvasState stateWithoutFirstNode(final Fixture fixture) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNode node : fixture.state.projection().nodes()) {
            if (!fixture.firstNodeKey.equals(node.key())) {
                nodes.add(node);
            }
        }
        return replacementState(fixture.state, nodes, fixture.state.projection().enclosures(),
            fixture.state.geometry());
    }

    private static CanvasState stateWithoutFirstNodeGeometry(final Fixture fixture) {
        final Map<ProjectedNodeKey, NodeGeometry> nodes =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>(fixture.state.geometry().nodes());
        nodes.remove(fixture.firstNodeKey);
        return replacementState(fixture.state, fixture.state.projection().nodes(),
            fixture.state.projection().enclosures(), GraphGeometry.of(nodes, fixture.state.geometry().hulls()));
    }

    private static CanvasState stateWithSuppressedFirstEnclosure(final Fixture fixture) {
        final EnclosureKey source = fixture.firstHullEndpoint.enclosure().get();
        final List<org.freeplane.plugin.graph.projection.ProjectedEnclosure> enclosures =
            new ArrayList<org.freeplane.plugin.graph.projection.ProjectedEnclosure>();
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure
                : fixture.state.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(source)) {
                enclosures.add(org.freeplane.plugin.graph.projection.ProjectedEnclosure.of(enclosure.hullKey(),
                    enclosure.endpointKeys(), enclosure.labels(), enclosure.mapName(), enclosure.parentHull(),
                    enclosure.directNodes(), enclosure.directEnclosures(), enclosure.mapRoot(),
                    BoundaryTier.SUPPRESSED));
            }
            else {
                enclosures.add(enclosure);
            }
        }
        return replacementState(fixture.state, fixture.state.projection().nodes(), enclosures,
            fixture.state.geometry());
    }

    private static CanvasState replacementState(final CanvasState base, final List<ProjectedNode> nodes,
            final List<org.freeplane.plugin.graph.projection.ProjectedEnclosure> enclosures,
            final GraphGeometry geometry) {
        final GraphProjection projection = GraphProjection.projected(base.generation(), nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), base.projection().relationshipResolutions(),
            Collections.<PinProjection>emptyList());
        return CanvasState.of(base.generation(), projection, base.layout(), geometry, base.status());
    }

    private static void dispatch(GraphCanvas canvas, AWTEvent event) {
        canvas.dispatchEvent(event);
    }

    private static void dispatchKey(GraphCanvas canvas, KeyEvent event) {
        for (KeyListener listener : canvas.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    private static MouseEvent click(GraphCanvas canvas, int id, double worldX, double worldY,
            int count, int button) {
        final Point2D screen = canvas.viewport().toScreen(worldX, worldY,
            new Dimension(canvas.getWidth(), canvas.getHeight()));
        return clickAt(canvas, id, (int) Math.round(screen.getX()),
            (int) Math.round(screen.getY()), count, button);
    }

    private static MouseEvent clickAt(GraphCanvas canvas, int id, int x, int y, int count,
            int button) {
        return new MouseEvent(canvas, id, System.currentTimeMillis(), 0, x, y, count, false,
            button);
    }

    private static MouseEvent move(GraphCanvas canvas, double worldX, double worldY) {
        return click(canvas, MouseEvent.MOUSE_MOVED, worldX, worldY, 0, MouseEvent.NOBUTTON);
    }

    private static MouseEvent exit(GraphCanvas canvas) {
        return clickAt(canvas, MouseEvent.MOUSE_EXITED, 0, 0, 0, MouseEvent.NOBUTTON);
    }

    private static MouseEvent context(GraphCanvas canvas, double worldX, double worldY) {
        return click(canvas, MouseEvent.MOUSE_PRESSED, worldX, worldY, 1, MouseEvent.BUTTON3);
    }

    private static MouseEvent press(GraphCanvas canvas, double worldX, double worldY) {
        return click(canvas, MouseEvent.MOUSE_PRESSED, worldX, worldY, 1, MouseEvent.BUTTON1);
    }

    private static MouseEvent drag(GraphCanvas canvas, double worldX, double worldY) {
        return click(canvas, MouseEvent.MOUSE_DRAGGED, worldX, worldY, 1, MouseEvent.BUTTON1);
    }

    private static MouseEvent release(GraphCanvas canvas, double worldX, double worldY) {
        return click(canvas, MouseEvent.MOUSE_RELEASED, worldX, worldY, 1, MouseEvent.BUTTON1);
    }

    private static MouseWheelEvent wheel(GraphCanvas canvas, int x, int y, int rotation) {
        return new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
            x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    private static KeyEvent key(GraphCanvas canvas, int code, int modifiers) {
        return new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
            code, KeyEvent.CHAR_UNDEFINED);
    }

    private static final class EscapeFixture {
        private final Fixture source = Fixture.create();
        private final GraphCanvas canvas = source.canvas();
        private final RecordingListener listener = new RecordingListener();
        private final GraphInteractionController controller = new GraphInteractionController(listener);
        private final AtomicInteger fallbackCalls = new AtomicInteger();

        private EscapeFixture() {
            controller.install(canvas);
            controller.setEscapeFallback(fallbackCalls::incrementAndGet);
        }

        private void preview() {
            controller.setTool(InteractionTool.CONNECT);
            dispatch(canvas, press(canvas, -40.0, 0.0));
            dispatch(canvas, drag(canvas, 0.0, 20.0));
        }

        private void select() {
            dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -40.0, 0.0, 1,
                MouseEvent.BUTTON1));
        }

        private KeyEvent escape() {
            final KeyEvent event = key(canvas, KeyEvent.VK_ESCAPE, 0);
            dispatchKey(canvas, event);
            return event;
        }

        private void assertSelectionClearedIntent() {
            assertThat(listener.intents).containsExactly(new GraphIntent.ChangeSelection(
                Optional.<ProjectedEndpointKey>empty()));
        }
    }

    private static final class CanvasCompletionProbe {
        private final GraphCanvas canvas = new GraphCanvas();
        private final JViewport viewport = new JViewport();
        private final AtomicInteger viewportEvents = new AtomicInteger();

        private CanvasCompletionProbe() {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            viewport.setView(canvas);
            canvas.setSize(400, 300);
            viewport.setExtentSize(new Dimension(80, 80));
            viewport.setViewPosition(new Point(20, 20));
            viewport.addChangeListener(event -> viewportEvents.incrementAndGet());
        }

        private static CanvasCompletionProbe settled() throws Exception {
            final AtomicReference<CanvasCompletionProbe> probe = new AtomicReference<CanvasCompletionProbe>();
            SwingUtilities.invokeAndWait(() -> probe.set(new CanvasCompletionProbe()));
            // Exclude the deferred initialization resize before observing clamp events.
            SwingUtilities.invokeAndWait(() -> probe.get().viewportEvents.set(0));
            return probe.get();
        }

        private void begin() {
            invoke("beginViewportPositioning", new Class<?>[0]);
        }

        private void end() {
            invoke("endViewportPositioning", new Class<?>[0]);
        }

        private void scheduleClamp() {
            invoke("scheduleViewportClamp", new Class<?>[] { JViewport.class }, viewport);
        }

        private void assertSettled() {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            assertThat(counter("viewportPositioningDepth")).isZero();
            assertThat(counter("pendingViewportClamps")).isZero();
        }

        private int counter(final String name) {
            try {
                final Field field = GraphCanvas.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(canvas);
            }
            catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        private void invoke(final String name, final Class<?>[] parameterTypes,
                final Object... arguments) {
            try {
                final Method method = GraphCanvas.class.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                method.invoke(canvas, arguments);
            }
            catch (InvocationTargetException failure) {
                final Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new AssertionError(cause);
            }
            catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class RecordingListener implements GraphInteractionListener {
        private final List<GraphIntent> intents = new ArrayList<GraphIntent>();

        @Override
        public void onGraphIntent(GraphIntent intent) {
            intents.add(intent);
        }

        private GraphIntent last() {
            return intents.get(intents.size() - 1);
        }
    }

    private static final class AnchorFixture {
        private final CanvasState state;
        private final ProjectedEdgeKey edgeKey;
        private final LayoutPoint paintedSegmentPoint;

        private AnchorFixture(CanvasState state, ProjectedEdgeKey edgeKey,
                LayoutPoint paintedSegmentPoint) {
            this.state = state;
            this.edgeKey = edgeKey;
            this.paintedSegmentPoint = paintedSegmentPoint;
        }

        private static AnchorFixture create() {
            final MapReferenceId nodeMap = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000014"));
            final MapReferenceId enclosureMap = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000016"));
            final NodeReference nodeReference = NodeReference.of(nodeMap, PersistedNodeId.of("node"));
            final NodeReference enclosureReference = NodeReference.of(enclosureMap,
                PersistedNodeId.of("enclosure"));
            final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(
                SourceNodeKey.persisted(nodeReference));
            final EnclosureKey enclosureKey = EnclosureKey.of(
                SourceNodeKey.persisted(enclosureReference));
            final EnclosureHullKey hullKey = EnclosureHullKey.of(
                Collections.singletonList(enclosureKey));
            final ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
            final ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(
                enclosureKey);
            final ProjectedNode node = ProjectedNode.of(nodeKey,
                SafeNodeLabel.of("Node label", "Node"), "Map", false);
            final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure =
                org.freeplane.plugin.graph.projection.ProjectedEnclosure.of(hullKey,
                    Collections.singletonList(enclosureKey), Collections.singletonList(
                        SafeNodeLabel.of("Enclosure label", "Enclosure")), "Map",
                    Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(UUID.fromString("00000000-0000-0000-0000-000000000015")),
                1L, nodeReference, enclosureReference, RelationshipDirection.FORWARD,
                Collections.emptyList());
            final ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(nodeEndpoint, enclosureEndpoint);
            final EdgeContributor contributor = EdgeContributor.graphRelationship(relationship,
                nodeEndpoint, enclosureEndpoint);
            final ProjectedEdge edge = ProjectedEdge.of(edgeKey,
                Collections.singletonList(contributor));
            final GraphProjection projection = GraphProjection.projected(1L,
                Collections.singletonList(node), Collections.singletonList(enclosure),
                Collections.singletonList(edge), Collections.emptyList(), Collections.emptyList());

            final LayoutPoint nodeCenter = LayoutPoint.of(100.0, 0.0);
            final LayoutPoint layoutAnchor = LayoutPoint.of(0.0, 60.0);
            final Map<ProjectedNodeKey, NodeGeometry> nodes =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            nodes.put(nodeKey, NodeGeometry.of(nodeCenter, 10.0));
            final Map<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry> hulls =
                new LinkedHashMap<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry>();
            hulls.put(hullKey, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(-10.0, -10.0), LayoutPoint.of(10.0, -10.0),
                    LayoutPoint.of(10.0, 10.0), LayoutPoint.of(-10.0, 10.0)),
                LayoutPoint.of(0.0, 0.0)));
            final GraphGeometry geometry = GraphGeometry.of(nodes, hulls);
            final Map<ProjectedNodeKey, LayoutPoint> nodePositions =
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            nodePositions.put(nodeKey, nodeCenter);
            final Map<EnclosureHullKey, LayoutPoint> anchors =
                new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
            anchors.put(hullKey, layoutAnchor);
            final LayoutFrame frame = LayoutFrame.of(1L, LayoutPositions.of(nodePositions, anchors),
                false, 0);
            final CanvasState state = CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE);
            final LayoutPoint firstAttachment = geometry.edgeAttachment(nodeEndpoint, layoutAnchor);
            final LayoutPoint secondAttachment = geometry.edgeAttachment(enclosureEndpoint,
                nodeCenter);
            return new AnchorFixture(state, edgeKey, LayoutPoint.of(
                (firstAttachment.x() + secondAttachment.x()) * 0.5,
                (firstAttachment.y() + secondAttachment.y()) * 0.5));
        }
    }

    private static final class CoordinateSegmentFixture {
        private final CanvasState state;
        private final ProjectedEdgeKey edgeKey;

        private CoordinateSegmentFixture(final CanvasState state, final ProjectedEdgeKey edgeKey) {
            this.state = state;
            this.edgeKey = edgeKey;
        }

        private static CoordinateSegmentFixture create(final double firstX, final double firstY,
                final double secondX, final double secondY, final String firstMapId,
                final String secondMapId, final String relationshipId) {
            return create(firstX, firstY, secondX, secondY, firstMapId, secondMapId,
                relationshipId, 10.0);
        }

        private static CoordinateSegmentFixture create(final double firstX, final double firstY,
                final double secondX, final double secondY, final String firstMapId,
                final String secondMapId, final String relationshipId, final double radius) {
            final MapReferenceId firstMap = MapReferenceId.of(UUID.fromString(firstMapId));
            final MapReferenceId secondMap = MapReferenceId.of(UUID.fromString(secondMapId));
            final NodeReference firstReference = NodeReference.of(firstMap,
                PersistedNodeId.of("coordinate-first"));
            final NodeReference secondReference = NodeReference.of(secondMap,
                PersistedNodeId.of("coordinate-second"));
            final ProjectedNodeKey first = ProjectedNodeKey.of(
                SourceNodeKey.persisted(firstReference));
            final ProjectedNodeKey second = ProjectedNodeKey.of(
                SourceNodeKey.persisted(secondReference));
            final ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofNode(first);
            final ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofNode(second);
            final ProjectedNode firstNode = ProjectedNode.of(first,
                SafeNodeLabel.of("Coordinate first", "Coordinate first"), "Map", false);
            final ProjectedNode secondNode = ProjectedNode.of(second,
                SafeNodeLabel.of("Coordinate second", "Coordinate second"), "Map", false);
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(UUID.fromString(relationshipId)), 1L, firstReference,
                secondReference, RelationshipDirection.FORWARD, Collections.emptyList());
            final EdgeContributor contributor = EdgeContributor.graphRelationship(relationship,
                firstEndpoint, secondEndpoint);
            final ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(firstEndpoint, secondEndpoint);
            final ProjectedEdge edge = ProjectedEdge.of(edgeKey,
                Collections.singletonList(contributor));
            final GraphProjection projection = GraphProjection.projected(1L,
                Arrays.asList(firstNode, secondNode), Collections.emptyList(),
                Collections.singletonList(edge), Collections.emptyList(), Collections.emptyList());

            final Map<ProjectedNodeKey, NodeGeometry> nodes =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            nodes.put(first, NodeGeometry.of(LayoutPoint.of(firstX, firstY), radius));
            nodes.put(second, NodeGeometry.of(LayoutPoint.of(secondX, secondY), radius));
            final GraphGeometry geometry = GraphGeometry.of(nodes,
                Collections.<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry>emptyMap());
            final Map<ProjectedNodeKey, LayoutPoint> positions =
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            positions.put(first, LayoutPoint.of(firstX, firstY));
            positions.put(second, LayoutPoint.of(secondX, secondY));
            final LayoutFrame frame = LayoutFrame.of(1L,
                LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false, 0);
            return new CoordinateSegmentFixture(CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE), edgeKey);
        }
    }

    private static final class ExtremeSpanFixture {
        private final CanvasState state;
        private final ProjectedEdgeKey edgeKey;

        private ExtremeSpanFixture(CanvasState state, ProjectedEdgeKey edgeKey) {
            this.state = state;
            this.edgeKey = edgeKey;
        }

        private static ExtremeSpanFixture create() {
            final MapReferenceId firstMap = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000021"));
            final MapReferenceId secondMap = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000022"));
            final NodeReference firstReference = NodeReference.of(firstMap,
                PersistedNodeId.of("extreme-first"));
            final NodeReference secondReference = NodeReference.of(secondMap,
                PersistedNodeId.of("extreme-second"));
            final ProjectedNodeKey first = ProjectedNodeKey.of(
                SourceNodeKey.persisted(firstReference));
            final ProjectedNodeKey second = ProjectedNodeKey.of(
                SourceNodeKey.persisted(secondReference));
            final ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofNode(first);
            final ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofNode(second);
            final ProjectedNode firstNode = ProjectedNode.of(first,
                SafeNodeLabel.of("Extreme first", "Extreme first"), "Map", false);
            final ProjectedNode secondNode = ProjectedNode.of(second,
                SafeNodeLabel.of("Extreme second", "Extreme second"), "Map", false);
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(UUID.fromString("00000000-0000-0000-0000-000000000023")),
                1L, firstReference, secondReference, RelationshipDirection.FORWARD,
                Collections.emptyList());
            final EdgeContributor contributor = EdgeContributor.graphRelationship(relationship,
                firstEndpoint, secondEndpoint);
            final ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(firstEndpoint, secondEndpoint);
            final ProjectedEdge edge = ProjectedEdge.of(edgeKey,
                Collections.singletonList(contributor));
            final GraphProjection projection = GraphProjection.projected(1L,
                Arrays.asList(firstNode, secondNode), Collections.emptyList(),
                Collections.singletonList(edge), Collections.emptyList(), Collections.emptyList());

            final Map<ProjectedNodeKey, NodeGeometry> nodes =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            nodes.put(first, NodeGeometry.of(LayoutPoint.of(-8.0e307, 0.0), 10.0));
            nodes.put(second, NodeGeometry.of(LayoutPoint.of(8.0e307, 0.0), 10.0));
            final GraphGeometry geometry = GraphGeometry.of(nodes,
                Collections.<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry>emptyMap());
            final Map<ProjectedNodeKey, LayoutPoint> positions =
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            positions.put(first, LayoutPoint.of(-8.0e307, 0.0));
            positions.put(second, LayoutPoint.of(8.0e307, 0.0));
            final LayoutFrame frame = LayoutFrame.of(1L,
                LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false, 0);
            return new ExtremeSpanFixture(CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE), edgeKey);
        }
    }

    private static final class Fixture {
        private final CanvasState state;
        private final ProjectedNodeKey firstNodeKey;
        private final ProjectedEndpointKey firstEndpoint;
        private final ProjectedNodeKey secondNodeKey;
        private final ProjectedEndpointKey secondEndpoint;
        private final EnclosureHullKey firstHullKey;
        private final EnclosureHullKey secondHullKey;
        private final ProjectedEndpointKey firstHullEndpoint;
        private final ProjectedEndpointKey secondHullEndpoint;
        private final ProjectedEndpointKey suppressedEndpoint;
        private final ProjectedEdgeKey edgeKey;
        private final ContributorKey contributorKey;

        private Fixture(CanvasState state, ProjectedNodeKey firstNodeKey,
                ProjectedNodeKey secondNodeKey, ProjectedEndpointKey firstEndpoint,
                ProjectedEndpointKey secondEndpoint, EnclosureHullKey firstHullKey,
                EnclosureHullKey secondHullKey, ProjectedEndpointKey firstHullEndpoint,
                ProjectedEndpointKey secondHullEndpoint, ProjectedEndpointKey suppressedEndpoint,
                ProjectedEdgeKey edgeKey,
                ContributorKey contributorKey) {
            this.state = state;
            this.firstNodeKey = firstNodeKey;
            this.secondNodeKey = secondNodeKey;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
            this.firstHullKey = firstHullKey;
            this.secondHullKey = secondHullKey;
            this.firstHullEndpoint = firstHullEndpoint;
            this.secondHullEndpoint = secondHullEndpoint;
            this.suppressedEndpoint = suppressedEndpoint;
            this.edgeKey = edgeKey;
            this.contributorKey = contributorKey;
        }

        private GraphCanvas canvas() {
            final GraphCanvas canvas = new GraphCanvas();
            canvas.setSize(400, 300);
            canvas.setCanvasState(state);
            return canvas;
        }

        private static Fixture create() {
            final MapReferenceId firstMap = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000011"));
            final MapReferenceId secondMap = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000013"));
            final NodeReference firstReference = NodeReference.of(firstMap,
                PersistedNodeId.of("first"));
            final NodeReference secondReference = NodeReference.of(secondMap,
                PersistedNodeId.of("second"));
            final ProjectedNodeKey first = ProjectedNodeKey.of(
                SourceNodeKey.persisted(firstReference));
            final ProjectedNodeKey second = ProjectedNodeKey.of(
                SourceNodeKey.persisted(secondReference));
            final ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofNode(first);
            final ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofNode(second);
            final ProjectedNode firstNode = ProjectedNode.of(first,
                SafeNodeLabel.of("First full label", "First"), "Map", false);
            final ProjectedNode secondNode = ProjectedNode.of(second,
                SafeNodeLabel.of("Second full label", "Second"), "Map", false);
            final EnclosureKey enclosureKey = EnclosureKey.of(
                SourceNodeKey.persisted(firstReference));
            final EnclosureKey secondEnclosureKey = EnclosureKey.of(
                SourceNodeKey.persisted(secondReference));
            final EnclosureHullKey hullKey = EnclosureHullKey.of(
                Collections.singletonList(enclosureKey));
            final EnclosureHullKey secondHullKey = EnclosureHullKey.of(
                Collections.singletonList(secondEnclosureKey));
            final EnclosureKey suppressedKey = EnclosureKey.of(
                SourceNodeKey.transientPath(firstMap, Arrays.asList(11)));
            final EnclosureHullKey suppressedHullKey = EnclosureHullKey.of(
                Collections.singletonList(suppressedKey));
            final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure =
                org.freeplane.plugin.graph.projection.ProjectedEnclosure.of(hullKey,
                    Collections.singletonList(enclosureKey), Collections.singletonList(
                        SafeNodeLabel.of("First full label", "First")), "Map",
                    Optional.<EnclosureHullKey>empty(),
                    Collections.<ProjectedNodeKey>emptyList(),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
            final org.freeplane.plugin.graph.projection.ProjectedEnclosure secondEnclosure =
                org.freeplane.plugin.graph.projection.ProjectedEnclosure.of(secondHullKey,
                    Collections.singletonList(secondEnclosureKey), Collections.singletonList(
                        SafeNodeLabel.of("Second full label", "Second")), "Map",
                    Optional.<EnclosureHullKey>empty(),
                    Collections.<ProjectedNodeKey>emptyList(),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
            final org.freeplane.plugin.graph.projection.ProjectedEnclosure suppressed =
                org.freeplane.plugin.graph.projection.ProjectedEnclosure.of(suppressedHullKey,
                    Collections.singletonList(suppressedKey), Collections.singletonList(
                        SafeNodeLabel.of("Suppressed hull label", "Suppressed hull")), "Map",
                    Optional.<EnclosureHullKey>empty(),
                    Collections.<ProjectedNodeKey>emptyList(),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUPPRESSED);
            final ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(firstEndpoint, secondEndpoint);
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(UUID.fromString("00000000-0000-0000-0000-000000000012")), 1L,
                firstReference, secondReference, RelationshipDirection.FORWARD,
                Collections.emptyList());
            final EdgeContributor contributor = EdgeContributor.graphRelationship(relationship,
                firstEndpoint, secondEndpoint);
            final ProjectedEdge edge = ProjectedEdge.of(edgeKey,
                Collections.singletonList(contributor));
            final PinRecord pinRecord = PinRecord.of(secondReference, 40.0, 0.0,
                Collections.emptyList());
            final GraphProjection projection = GraphProjection.projected(1L,
                Arrays.asList(firstNode, secondNode), Arrays.asList(enclosure, secondEnclosure, suppressed),
                Collections.singletonList(edge), Collections.emptyList(),
                Collections.singletonList(PinProjection.active(pinRecord, second)));

            final Map<ProjectedNodeKey, NodeGeometry> nodes =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            nodes.put(first, NodeGeometry.of(LayoutPoint.of(-40.0, 0.0), 20.0));
            nodes.put(second, NodeGeometry.of(LayoutPoint.of(40.0, 0.0), 8.0));
            final Map<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry> hulls =
                new LinkedHashMap<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry>();
            hulls.put(hullKey, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(-70.0, -30.0), LayoutPoint.of(-10.0, -30.0),
                    LayoutPoint.of(-10.0, 30.0), LayoutPoint.of(-70.0, 30.0)),
                LayoutPoint.of(-40.0, 0.0)));
            hulls.put(secondHullKey, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(30.0, -20.0), LayoutPoint.of(50.0, -20.0),
                    LayoutPoint.of(50.0, 20.0), LayoutPoint.of(30.0, 20.0)),
                LayoutPoint.of(40.0, 0.0)));
            hulls.put(suppressedHullKey, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(80.0, -20.0), LayoutPoint.of(110.0, -20.0),
                    LayoutPoint.of(110.0, 20.0), LayoutPoint.of(80.0, 20.0)),
                LayoutPoint.of(95.0, 0.0)));
            final GraphGeometry geometry = GraphGeometry.of(nodes, hulls);
            final Map<EnclosureHullKey, LayoutPoint> anchors =
                new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
            anchors.put(hullKey, LayoutPoint.of(-40.0, 0.0));
            anchors.put(secondHullKey, LayoutPoint.of(40.0, 0.0));
            anchors.put(suppressedHullKey, LayoutPoint.of(95.0, 0.0));
            final LayoutPositions positions = LayoutPositions.of(nodePoints(nodes), anchors);
            final LayoutFrame frame = LayoutFrame.of(1L, positions, false, 0);
            final CanvasState state = CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE);
            return new Fixture(state, first, second, firstEndpoint, secondEndpoint,
                hullKey, secondHullKey, ProjectedEndpointKey.ofEnclosure(enclosureKey),
                ProjectedEndpointKey.ofEnclosure(secondEnclosureKey),
                ProjectedEndpointKey.ofEnclosure(suppressedKey), edgeKey, contributor.key());
        }

        private static Map<ProjectedNodeKey, LayoutPoint> nodePoints(
                Map<ProjectedNodeKey, NodeGeometry> nodes) {
            final Map<ProjectedNodeKey, LayoutPoint> points =
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (Map.Entry<ProjectedNodeKey, NodeGeometry> entry : nodes.entrySet()) {
                points.put(entry.getKey(), entry.getValue().center());
            }
            return points;
        }
    }
}
