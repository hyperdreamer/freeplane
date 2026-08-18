package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.InputEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
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
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.PinProjection;
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
    public void hitProminenceGeometryBeforeHullAndResolveEdgesByTolerance() {
        final Fixture fixture = Fixture.create();
        final GraphHitIndex index = GraphHitIndex.from(fixture.state);

        assertThat(index.endpointAt(LayoutPoint.of(-25.0, 0.0)))
            .contains(fixture.firstEndpoint);
        assertThat(index.endpointAt(LayoutPoint.of(-40.0, 0.0)))
            .contains(fixture.firstEndpoint);
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
        assertThat(listener.last()).isInstanceOf(GraphIntent.ChangeSelection.class);
        assertThat(((GraphIntent.ChangeSelection) listener.last()).selection())
            .contains(fixture.firstEndpoint);
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
                false);
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
                LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false);
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
                LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false);
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
        private final ProjectedEndpointKey firstHullEndpoint;
        private final ProjectedEndpointKey secondHullEndpoint;
        private final ProjectedEdgeKey edgeKey;
        private final ContributorKey contributorKey;

        private Fixture(CanvasState state, ProjectedNodeKey firstNodeKey,
                ProjectedNodeKey secondNodeKey, ProjectedEndpointKey firstEndpoint,
                ProjectedEndpointKey secondEndpoint, ProjectedEndpointKey firstHullEndpoint,
                ProjectedEndpointKey secondHullEndpoint, ProjectedEdgeKey edgeKey,
                ContributorKey contributorKey) {
            this.state = state;
            this.firstNodeKey = firstNodeKey;
            this.secondNodeKey = secondNodeKey;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
            this.firstHullEndpoint = firstHullEndpoint;
            this.secondHullEndpoint = secondHullEndpoint;
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
                SourceNodeKey.transientPath(firstMap, Arrays.asList(9)));
            final EnclosureKey secondEnclosureKey = EnclosureKey.of(
                SourceNodeKey.transientPath(firstMap, Arrays.asList(10)));
            final EnclosureHullKey hullKey = EnclosureHullKey.of(
                Arrays.asList(secondEnclosureKey, enclosureKey));
            final EnclosureKey suppressedKey = EnclosureKey.of(
                SourceNodeKey.transientPath(firstMap, Arrays.asList(11)));
            final EnclosureHullKey suppressedHullKey = EnclosureHullKey.of(
                Collections.singletonList(suppressedKey));
            final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure =
                org.freeplane.plugin.graph.projection.ProjectedEnclosure.of(hullKey,
                    Arrays.asList(secondEnclosureKey, enclosureKey), Arrays.asList(
                        SafeNodeLabel.of("Second hull label", "Second hull"),
                        SafeNodeLabel.of("Hull label", "Hull")), "Map",
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
                Arrays.asList(firstNode, secondNode), Arrays.asList(enclosure, suppressed),
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
            hulls.put(suppressedHullKey, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(80.0, -20.0), LayoutPoint.of(110.0, -20.0),
                    LayoutPoint.of(110.0, 20.0), LayoutPoint.of(80.0, 20.0)),
                LayoutPoint.of(95.0, 0.0)));
            final GraphGeometry geometry = GraphGeometry.of(nodes, hulls);
            final Map<EnclosureHullKey, LayoutPoint> anchors =
                new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
            anchors.put(hullKey, LayoutPoint.of(-40.0, 0.0));
            anchors.put(suppressedHullKey, LayoutPoint.of(95.0, 0.0));
            final LayoutPositions positions = LayoutPositions.of(nodePoints(nodes), anchors);
            final LayoutFrame frame = LayoutFrame.of(1L, positions, false);
            final CanvasState state = CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE);
            return new Fixture(state, first, second, firstEndpoint, secondEndpoint,
                ProjectedEndpointKey.ofEnclosure(enclosureKey),
                ProjectedEndpointKey.ofEnclosure(secondEnclosureKey), edgeKey, contributor.key());
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
