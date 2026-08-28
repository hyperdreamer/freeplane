package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
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
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
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

public class AccessibleGraphCanvasShould {
    @Test
    public void resolveAccessibleParentAndIndexFromTheLiveSwingHierarchy() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final JPanel panel = new JPanel();
        final AccessibleContext root = canvas.getAccessibleContext();
        assertThat(root.getAccessibleParent()).isNull();
        assertThat(root.getAccessibleIndexInParent()).isEqualTo(-1);

        panel.add(new JButton("Before graph"));
        panel.add(canvas);

        assertThat(root.getAccessibleParent()).isSameAs(panel);
        final AccessibleContext panelContext = panel.getAccessibleContext();
        int canvasIndex = -1;
        for (int index = 0; index < panelContext.getAccessibleChildrenCount(); index++) {
            final Accessible child = panelContext.getAccessibleChild(index);
            if (child == canvas || child != null && child.getAccessibleContext() == root) {
                canvasIndex = index;
                break;
            }
        }
        assertThat(canvasIndex).isNotEqualTo(-1);
        assertThat(root.getAccessibleIndexInParent()).isEqualTo(canvasIndex);

        panel.remove(canvas);
        assertThat(root.getAccessibleParent()).isNull();
        assertThat(root.getAccessibleIndexInParent()).isEqualTo(-1);
    }

    @Test
    public void panNormallyAndClearStaleVisualSelectionForUnmodifiedArrows() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        try {
            canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.sourceEndpoint));
            canvas.setCanvasState(fixture.emptyState());
            assertThatUnmodifiedRightArrowPansAndClears(canvas, listener);

            listener.clear();
            canvas.setCanvasState(fixture.state);
            canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.suppressedEndpoint));
            assertThatUnmodifiedRightArrowPansAndClears(canvas, listener);

            listener.clear();
            canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.geometrylessEndpoint));
            assertThatUnmodifiedRightArrowPansAndClears(canvas, listener);
        }
        finally {
            controller.uninstall();
        }
    }

    @Test
    public void makeRetainedVirtualChildUnavailableAfterCurrentProjectionRemovesEndpoint() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final AccessibleContext root = canvas.getAccessibleContext();
        final Accessible retained = childFor(root, fixture.enclosureEndpoint);
        final AccessibleContext retainedContext = retained.getAccessibleContext();
        final AccessibleAction actions = retainedContext.getAccessibleAction();
        final AccessibleComponent component = retainedContext.getAccessibleComponent();

        assertThat(retainedContext.getAccessibleName())
            .contains("Visible enclosure label");
        assertThat(retainedContext.getAccessibleDescription())
            .contains("Map Enclosure");
        assertThat(actions.getAccessibleActionCount()).isEqualTo(2);
        assertThat(component.isVisible()).isTrue();
        assertThat(component.getBounds()).isNotEqualTo(new Rectangle());

        final CanvasState replacement = fixture.emptyState();
        final CanvasState transitioning = mock(CanvasState.class);
        when(transitioning.geometry()).thenReturn(fixture.state.geometry());
        // Publish removal between visibility and detail reads to expose stale endpoint resolution.
        final int[] projectionCalls = new int[1];
        when(transitioning.projection()).thenAnswer(invocation -> {
            if (projectionCalls[0]++ == 1) {
                canvas.setCanvasState(replacement);
                return replacement.projection();
            }
            return fixture.state.projection();
        });
        canvas.setCanvasState(transitioning);

        assertUnavailableAccessibleSurface(retainedContext, actions, component);
        assertThat(canvas.canvasState()).isSameAs(replacement);
    }

    @Test
    public void keepRetainedVirtualChildUnavailableWhenCurrentGeometryDisappears() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final AccessibleContext root = canvas.getAccessibleContext();
        final Accessible retained = childFor(root, fixture.enclosureEndpoint);
        final AccessibleContext retainedContext = retained.getAccessibleContext();
        final AccessibleAction actions = retainedContext.getAccessibleAction();
        final AccessibleComponent component = retainedContext.getAccessibleComponent();

        assertThat(retainedContext.getAccessibleName())
            .contains("Visible enclosure label");
        assertThat(actions.getAccessibleActionCount()).isEqualTo(2);
        assertThat(component.getBounds()).isNotEqualTo(new Rectangle());

        canvas.setCanvasState(fixture.geometrylessState());
        assertUnavailableAccessibleSurface(retainedContext, actions, component);
    }

    private static void assertThatUnmodifiedRightArrowPansAndClears(final GraphCanvas canvas,
            final RecordingListener listener) {
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        final double beforePan = canvas.viewport().centerX();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, 0));
        assertThat(canvas.viewport().centerX()).isNotEqualTo(beforePan);
        assertThat(canvas.paintState().selection()).isEmpty();
        assertThat(listener.intents()).isEmpty();
    }

    private static void assertUnavailableAccessibleSurface(final AccessibleContext context,
            final AccessibleAction actions, final AccessibleComponent component) {
        assertThat(context.getAccessibleName()).isEqualTo("Unavailable graph endpoint");
        assertThat(context.getAccessibleDescription()).isEqualTo("Unavailable graph endpoint");
        assertThat(actions.getAccessibleActionCount()).isZero();
        assertThat(actions.getAccessibleActionDescription(0)).isNull();
        assertThat(actions.doAccessibleAction(0)).isFalse();
        assertThat(component.isEnabled()).isFalse();
        assertThat(component.isVisible()).isFalse();
        assertThat(component.getBounds()).isEqualTo(new Rectangle());
        assertThat(context.getAccessibleStateSet().contains(AccessibleState.ENABLED)).isFalse();
        assertThat(context.getAccessibleStateSet().contains(AccessibleState.VISIBLE)).isFalse();
    }

    @Test
    public void orderVisibleGeometryAndChooseStrictNearestTieByEndpointKey() {
        final Fixture fixture = Fixture.create();

        assertThat(GraphTraversalOrder.tabOrder(fixture.state))
            .containsExactlyElementsOf(fixture.visibleEndpointsSorted());
        assertThat(GraphTraversalOrder.tabOrder(fixture.state))
            .doesNotContain(fixture.suppressedEndpoint, fixture.geometrylessEndpoint);
        assertThat(GraphTraversalOrder.nearest(fixture.state, fixture.sourceEndpoint,
            TraversalDirection.RIGHT)).contains(fixture.rightTieWinner());
        assertThat(GraphTraversalOrder.nearest(fixture.state, fixture.sourceEndpoint,
            TraversalDirection.LEFT)).contains(fixture.leftEndpoint);
        assertThat(GraphTraversalOrder.nearest(fixture.state, fixture.sourceEndpoint,
            TraversalDirection.UP)).contains(fixture.upEndpoint);
        assertThat(GraphTraversalOrder.nearest(fixture.state, fixture.sourceEndpoint,
            TraversalDirection.DOWN)).contains(fixture.enclosureEndpoint);
        assertThat(GraphTraversalOrder.nearest(fixture.state, fixture.sourceEndpoint,
            TraversalDirection.RIGHT)).isNotEqualTo(Optional.of(fixture.sourceEndpoint));
        assertThat(GraphTraversalOrder.nearest(fixture.state, fixture.geometrylessEndpoint,
            TraversalDirection.RIGHT)).isEmpty();
    }

    @Test
    public void receiveTabThroughSwingDispatchAndRestoreFocusTraversalConfiguration() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final Set<AWTKeyStroke> forwardKeys = new LinkedHashSet<AWTKeyStroke>(Arrays.asList(
            AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, 0),
            AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_F1, 0)));
        final Set<AWTKeyStroke> backwardKeys = new LinkedHashSet<AWTKeyStroke>(Arrays.asList(
            AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
            AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_F2, InputEvent.SHIFT_DOWN_MASK)));
        canvas.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);
        canvas.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backwardKeys);
        final Set<AWTKeyStroke> previousForwardKeys = canvas.getFocusTraversalKeys(
            KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
        final Set<AWTKeyStroke> previousBackwardKeys = canvas.getFocusTraversalKeys(
            KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        try {
            withFocusedCanvas(canvas, new Runnable() {
                @Override
                public void run() {
                    canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.sourceEndpoint));
                    final KeyEvent tab = dispatchKeyEvent(canvas, KeyEvent.VK_TAB, 0);
                    assertThat(tab.isConsumed()).isTrue();
                    assertThat(canvas.paintState().selection()).contains(fixture.nextTabEndpoint());
                    final KeyEvent reverseTab = dispatchKeyEvent(canvas, KeyEvent.VK_TAB,
                        InputEvent.SHIFT_DOWN_MASK);
                    assertThat(reverseTab.isConsumed()).isTrue();
                    assertThat(canvas.paintState().selection()).contains(fixture.sourceEndpoint);
                    assertThat(canvas.getFocusTraversalKeysEnabled()).isFalse();
                    assertThat(canvas.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS))
                        .isEqualTo(previousForwardKeys);
                    assertThat(canvas.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS))
                        .isEqualTo(previousBackwardKeys);

                    canvas.setCanvasState(fixture.emptyState());
                    final KeyEvent tabWithoutVisibleEndpoint = dispatchKeyEvent(canvas,
                        KeyEvent.VK_TAB, 0);
                    assertThat(tabWithoutVisibleEndpoint.isConsumed()).isFalse();
                }
            });
        }
        finally {
            controller.uninstall();
        }
        assertThat(canvas.getFocusTraversalKeysEnabled()).isTrue();
        assertThat(canvas.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS))
            .isEqualTo(previousForwardKeys);
        assertThat(canvas.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS))
            .isEqualTo(previousBackwardKeys);
    }

    @Test
    public void traverseSelectionAndPreservePanAndEscapeKeyboardSemantics() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.sourceEndpoint));
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, 0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.ChangeSelection(
            Optional.of(fixture.rightTieWinner())));
        assertThat(canvas.paintState().selection()).contains(fixture.rightTieWinner());

        final double beforePan = canvas.viewport().centerX();
        canvas.setPaintState(canvas.paintState().withoutSelection());
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, 0));
        assertThat(canvas.viewport().centerX()).isNotEqualTo(beforePan);
        final double beforeFastPan = canvas.viewport().centerX();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK));
        assertThat(canvas.viewport().centerX()).isNotEqualTo(beforeFastPan);

        canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.sourceEndpoint));
        dispatchKey(canvas, key(canvas, KeyEvent.VK_TAB, 0));
        assertThat(canvas.paintState().selection()).contains(fixture.nextTabEndpoint());
        dispatchKey(canvas, key(canvas, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
        assertThat(canvas.paintState().selection()).contains(fixture.sourceEndpoint);
        dispatchKey(canvas, key(canvas, KeyEvent.VK_ENTER, 0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.OpenSourceNode(fixture.sourceEndpoint));

        controller.setTool(InteractionTool.CONNECT);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        dispatchMousePressed(canvas, 200, 150);
        assertThat(canvas.paintState().connectionPreview()).isPresent();
        dispatchKey(canvas, key(canvas, KeyEvent.VK_ESCAPE, 0));
        assertThat(canvas.paintState().connectionPreview()).isEmpty();
        assertThat(canvas.paintState().selection()).contains(fixture.sourceEndpoint);
        controller.uninstall();
    }

    @Test
    public void ignoreEnterForSelectionsAbsentFromCurrentVisibleOrderAfterStateReplacement() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        try {
            withFocusedCanvas(canvas, new Runnable() {
                @Override
                public void run() {
                    canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.sourceEndpoint));
                    canvas.setCanvasState(fixture.emptyState());
                    dispatchKeyEvent(canvas, KeyEvent.VK_ENTER, 0);
                    assertThat(listener.intents()).isEmpty();

                    listener.clear();
                    canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.suppressedEndpoint));
                    canvas.setCanvasState(fixture.state);
                    dispatchKeyEvent(canvas, KeyEvent.VK_ENTER, 0);
                    assertThat(listener.intents()).isEmpty();

                    listener.clear();
                    canvas.setCanvasState(fixture.emptyState());
                    canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.geometrylessEndpoint));
                    canvas.setCanvasState(fixture.state);
                    dispatchKeyEvent(canvas, KeyEvent.VK_ENTER, 0);
                    assertThat(listener.intents()).isEmpty();
                }
            });
        }
        finally {
            controller.uninstall();
        }
    }

    @Test
    public void tolerateStateReplacementBetweenAccessibleOrderAndChildLookup() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final AccessibleContext root = canvas.getAccessibleContext();
        final CanvasState replacement = fixture.emptyState();
        final CanvasState transitioning = mock(CanvasState.class);
        when(transitioning.geometry()).thenReturn(fixture.state.geometry());
        when(transitioning.projection()).thenAnswer(invocation -> {
            canvas.setCanvasState(replacement);
            return fixture.state.projection();
        });
        canvas.setCanvasState(transitioning);

        assertThat(root.getAccessibleComponent().getAccessibleAt(new Point(200, 150))).isNull();
        assertThat(root.getAccessibleChildrenCount()).isZero();
    }

    @Test
    public void exposeCurrentVirtualChildrenWithSafeTextBoundsAndActions() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        final AccessibleContext root = canvas.getAccessibleContext();
        assertThat(root.getAccessibleRole()).isEqualTo(AccessibleRole.CANVAS);
        assertThat(root.getAccessibleChildrenCount()).isEqualTo(fixture.visibleEndpointsSorted().size());
        for (int index = 0; index < root.getAccessibleChildrenCount(); index++) {
            final Accessible currentChild = root.getAccessibleChild(index);
            assertThat(currentChild.getAccessibleContext().getAccessibleParent()).isSameAs(canvas);
            assertThat(currentChild.getAccessibleContext().getAccessibleIndexInParent())
                .isEqualTo(index);
        }
        final Accessible child = childFor(root, fixture.prominentEndpoint);
        assertThat(child).isNotInstanceOf(JComponent.class);
        final AccessibleContext childContext = child.getAccessibleContext();
        assertThat(childContext.getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
        assertThat(childContext.getAccessibleName()).contains("Prominent safe label")
            .contains("Map Prominent");
        assertThat(childContext.getAccessibleDescription()).contains("Map Prominent")
            .doesNotContain("outgoing").doesNotContain("1.2")
            .doesNotContain("#").doesNotContain("raw source");
        final AccessibleComponent component = childContext.getAccessibleComponent();
        final Rectangle bounds = component.getBounds();
        assertThat(bounds.width).isGreaterThan(0);
        assertThat(bounds.height).isGreaterThan(0);
        assertThat(component.getLocation()).isEqualTo(new Point(bounds.x, bounds.y));
        final AccessibleAction actions = childContext.getAccessibleAction();
        assertThat(actions.getAccessibleActionCount()).isEqualTo(2);
        assertThat(actions.doAccessibleAction(0)).isTrue();
        assertThat(canvas.paintState().selection()).contains(fixture.prominentEndpoint);
        assertThat(actions.doAccessibleAction(1)).isTrue();
        assertThat(listener.last()).isEqualTo(new GraphIntent.OpenSourceNode(fixture.prominentEndpoint));

        final Accessible suppressed = childForOrNull(root, fixture.suppressedEndpoint);
        assertThat(suppressed).isNull();
        final Accessible enclosure = childFor(root, fixture.enclosureEndpoint);
        assertThat(enclosure.getAccessibleContext().getAccessibleName())
            .contains("Visible enclosure label").contains("Map Enclosure");
        assertThat(enclosure.getAccessibleContext().getAccessibleDescription())
            .doesNotContain("outgoing").doesNotContain("scale");

        canvas.setPaintState(canvas.paintState().withoutSelection());
        assertThat(child.getAccessibleContext().getAccessibleDescription()).doesNotContain("Selected");
        canvas.setPaintState(canvas.paintState().withSelection(fixture.prominentEndpoint));
        assertThat(child.getAccessibleContext().getAccessibleDescription()).contains("Selected");
        controller.uninstall();
    }

    private static Accessible childFor(final AccessibleContext root, final ProjectedEndpointKey endpoint) {
        for (int index = 0; index < root.getAccessibleChildrenCount(); index++) {
            final Accessible child = root.getAccessibleChild(index);
            if (endpoint.equals(((AccessibleGraphCanvas.EndpointAccessible) child).endpoint())) {
                return child;
            }
        }
        throw new AssertionError("Missing accessible endpoint " + endpoint);
    }

    private static Accessible childForOrNull(final AccessibleContext root,
            final ProjectedEndpointKey endpoint) {
        for (int index = 0; index < root.getAccessibleChildrenCount(); index++) {
            final Accessible child = root.getAccessibleChild(index);
            if (child instanceof AccessibleGraphCanvas.EndpointAccessible
                    && endpoint.equals(((AccessibleGraphCanvas.EndpointAccessible) child).endpoint())) {
                return child;
            }
        }
        return null;
    }

    private static void dispatchKey(final GraphCanvas canvas, final KeyEvent event) {
        for (KeyListener listener : canvas.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    private static void withFocusedCanvas(final GraphCanvas canvas, final Runnable action) {
        final JFrame[] frame = new JFrame[1];
        try {
            invokeOnEdt(new Runnable() {
                @Override
                public void run() {
                    final JFrame value = new JFrame();
                    value.setLayout(new BorderLayout());
                    value.add(canvas, BorderLayout.CENTER);
                    value.add(new JButton("Next"), BorderLayout.SOUTH);
                    value.setSize(500, 400);
                    value.setVisible(true);
                    canvas.requestFocusInWindow();
                    frame[0] = value;
                }
            });
            waitForFocus(canvas);
            invokeOnEdt(action);
        }
        finally {
            if (frame[0] != null) {
                invokeOnEdt(new Runnable() {
                    @Override
                    public void run() {
                        frame[0].dispose();
                    }
                });
            }
        }
    }

    private static void waitForFocus(final GraphCanvas canvas) {
        for (int attempt = 0; attempt < 100; attempt++) {
            final boolean[] focused = new boolean[1];
            invokeOnEdt(new Runnable() {
                @Override
                public void run() {
                    canvas.requestFocusInWindow();
                    focused[0] = canvas.isFocusOwner();
                }
            });
            if (focused[0]) {
                return;
            }
            try {
                Thread.sleep(10L);
            }
            catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("Graph canvas did not receive focus");
    }

    private static void invokeOnEdt(final Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        }
        catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        catch (final InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new AssertionError(cause);
        }
    }

    private static KeyEvent dispatchKeyEvent(final GraphCanvas canvas, final int code,
            final int modifiers) {
        final KeyEvent[] result = new KeyEvent[1];
        final Runnable dispatch = new Runnable() {
            @Override
            public void run() {
                result[0] = key(canvas, code, modifiers);
                canvas.dispatchEvent(result[0]);
            }
        };
        invokeOnEdt(dispatch);
        return result[0];
    }

    private static void dispatchMousePressed(final GraphCanvas canvas, final int x, final int y) {
        final MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    private static KeyEvent key(final GraphCanvas canvas, final int code, final int modifiers) {
        return new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
            code, KeyEvent.CHAR_UNDEFINED);
    }

    private static final class RecordingListener implements GraphInteractionListener {
        private final List<GraphIntent> intents = new ArrayList<GraphIntent>();

        @Override
        public void onGraphIntent(final GraphIntent intent) {
            intents.add(intent);
        }

        private GraphIntent last() {
            return intents.get(intents.size() - 1);
        }

        private void clear() {
            intents.clear();
        }

        private List<GraphIntent> intents() {
            return intents;
        }
    }

    private static final class Fixture {
        private final CanvasState state;
        private final ProjectedEndpointKey sourceEndpoint;
        private final ProjectedEndpointKey prominentEndpoint;
        private final ProjectedEndpointKey rightTieEndpoint;
        private final ProjectedEndpointKey leftEndpoint;
        private final ProjectedEndpointKey upEndpoint;
        private final ProjectedEndpointKey enclosureEndpoint;
        private final ProjectedEndpointKey suppressedEndpoint;
        private final ProjectedEndpointKey geometrylessEndpoint;

        private Fixture(final CanvasState state, final ProjectedEndpointKey sourceEndpoint,
                final ProjectedEndpointKey prominentEndpoint, final ProjectedEndpointKey rightTieEndpoint,
                final ProjectedEndpointKey leftEndpoint, final ProjectedEndpointKey upEndpoint,
                final ProjectedEndpointKey enclosureEndpoint, final ProjectedEndpointKey suppressedEndpoint,
                final ProjectedEndpointKey geometrylessEndpoint) {
            this.state = state;
            this.sourceEndpoint = sourceEndpoint;
            this.prominentEndpoint = prominentEndpoint;
            this.rightTieEndpoint = rightTieEndpoint;
            this.leftEndpoint = leftEndpoint;
            this.upEndpoint = upEndpoint;
            this.enclosureEndpoint = enclosureEndpoint;
            this.suppressedEndpoint = suppressedEndpoint;
            this.geometrylessEndpoint = geometrylessEndpoint;
        }

        private GraphCanvas canvas() {
            final GraphCanvas canvas = new GraphCanvas();
            canvas.setSize(400, 300);
            canvas.setCanvasState(state);
            return canvas;
        }

        private CanvasState emptyState() {
            final GraphProjection projection = GraphProjection.projected(state.generation() + 1L,
                Collections.<ProjectedNode>emptyList(),
                Collections.<ProjectedEnclosure>emptyList(),
                Collections.<ProjectedEdge>emptyList(),
                Collections.emptyList(),
                Collections.<PinProjection>emptyList());
            final GraphGeometry geometry = GraphGeometry.of(
                Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
                Collections.<EnclosureHullKey, HullGeometry>emptyMap());
            final LayoutFrame frame = LayoutFrame.of(state.generation() + 1L,
                LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                    Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false);
            return CanvasState.of(state.generation() + 1L, projection, frame, geometry,
                OperationalStatus.IDLE);
        }

        private CanvasState geometrylessState() {
            final GraphGeometry geometry = GraphGeometry.of(state.geometry().nodes(),
                Collections.<EnclosureHullKey, HullGeometry>emptyMap());
            return CanvasState.of(state.generation() + 1L, state.projection(), state.layout(),
                geometry, OperationalStatus.IDLE);
        }

        private List<ProjectedEndpointKey> visibleEndpointsSorted() {
            final List<ProjectedEndpointKey> result = new ArrayList<ProjectedEndpointKey>(Arrays.asList(
                sourceEndpoint, prominentEndpoint, rightTieEndpoint, leftEndpoint, upEndpoint,
                enclosureEndpoint));
            Collections.sort(result);
            return result;
        }

        private ProjectedEndpointKey rightTieWinner() {
            return prominentEndpoint.compareTo(rightTieEndpoint) < 0
                ? prominentEndpoint : rightTieEndpoint;
        }

        private ProjectedEndpointKey nextTabEndpoint() {
            final List<ProjectedEndpointKey> order = visibleEndpointsSorted();
            final int index = order.indexOf(sourceEndpoint);
            return order.get((index + 1) % order.size());
        }

        private static Fixture create() {
            final MapReferenceId mapSource = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000101"));
            final MapReferenceId mapProminent = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000102"));
            final MapReferenceId mapTie = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000103"));
            final MapReferenceId mapLeft = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000104"));
            final MapReferenceId mapUp = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000105"));
            final NodeReference sourceReference = reference(mapSource, "source");
            final NodeReference prominentReference = reference(mapProminent, "prominent");
            final NodeReference tieReference = reference(mapTie, "tie");
            final NodeReference leftReference = reference(mapLeft, "left");
            final NodeReference upReference = reference(mapUp, "up");
            final ProjectedNodeKey source = nodeKey(sourceReference);
            final ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofEnclosure(
                EnclosureKey.of(SourceNodeKey.persisted(sourceReference)));
            final ProjectedEndpointKey prominentEndpoint = ProjectedEndpointKey.ofEnclosure(
                EnclosureKey.of(SourceNodeKey.persisted(prominentReference)));
            final ProjectedEndpointKey tieEndpoint = ProjectedEndpointKey.ofEnclosure(
                EnclosureKey.of(SourceNodeKey.persisted(tieReference)));
            final ProjectedEndpointKey leftEndpoint = ProjectedEndpointKey.ofEnclosure(
                EnclosureKey.of(SourceNodeKey.persisted(leftReference)));
            final ProjectedEndpointKey upEndpoint = ProjectedEndpointKey.ofEnclosure(
                EnclosureKey.of(SourceNodeKey.persisted(upReference)));
            final EnclosureKey enclosure = EnclosureKey.of(SourceNodeKey.transientPath(mapSource,
                Collections.singletonList(Integer.valueOf(20))));
            final EnclosureKey suppressed = EnclosureKey.of(SourceNodeKey.transientPath(mapSource,
                Collections.singletonList(Integer.valueOf(21))));
            final EnclosureKey geometryless = EnclosureKey.of(SourceNodeKey.transientPath(mapSource,
                Collections.singletonList(Integer.valueOf(22))));
            final EnclosureHullKey enclosureHull = EnclosureHullKey.of(Collections.singletonList(enclosure));
            final EnclosureHullKey suppressedHull = EnclosureHullKey.of(Collections.singletonList(suppressed));
            final EnclosureHullKey geometrylessHull = EnclosureHullKey.of(Collections.singletonList(geometryless));
            final ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(enclosure);
            final ProjectedEndpointKey suppressedEndpoint = ProjectedEndpointKey.ofEnclosure(suppressed);
            final ProjectedEndpointKey geometrylessEndpoint = ProjectedEndpointKey.ofEnclosure(geometryless);
            final List<ProjectedEnclosure> boundaries = Arrays.asList(
                boundary(sourceReference, "Source safe label", "Source", "Map Source"),
                boundary(prominentReference, "Prominent safe label", "Prominent", "Map Prominent"),
                boundary(tieReference, "Tie safe label", "Tie", "Map Tie"),
                boundary(leftReference, "Left safe label", "Left", "Map Left"),
                boundary(upReference, "Up safe label", "Up", "Map Up"));
            final ProjectedEnclosure visibleEnclosure = ProjectedEnclosure.of(enclosureHull,
                Collections.singletonList(enclosure),
                Collections.singletonList(SafeNodeLabel.of("Visible enclosure label", "Visible")),
                "Map Enclosure", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(),
                false, BoundaryTier.SUBTLE);
            final ProjectedEnclosure hiddenEnclosure = ProjectedEnclosure.of(suppressedHull,
                Collections.singletonList(suppressed),
                Collections.singletonList(SafeNodeLabel.of("Suppressed excluded label", "Suppressed")),
                "Map Suppressed", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(),
                false, BoundaryTier.SUPPRESSED);
            final ProjectedEnclosure missingGeometryEnclosure = ProjectedEnclosure.of(geometrylessHull,
                Collections.singletonList(geometryless),
                Collections.singletonList(SafeNodeLabel.of("Missing geometry label", "Missing")),
                "Map Missing", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(),
                false, BoundaryTier.SUBTLE);
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(UUID.fromString("00000000-0000-0000-0000-000000000106")), 1L,
                prominentReference, sourceReference, RelationshipDirection.FORWARD,
                Collections.emptyList());
            final ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(sourceEndpoint, prominentEndpoint);
            final EdgeContributor contributor = EdgeContributor.graphRelationship(relationship,
                prominentEndpoint, sourceEndpoint);
            final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>(boundaries);
            enclosures.add(visibleEnclosure);
            enclosures.add(hiddenEnclosure);
            enclosures.add(missingGeometryEnclosure);
            final GraphProjection projection = GraphProjection.projected(1L,
                Collections.<ProjectedNode>emptyList(), enclosures,
                Collections.singletonList(ProjectedEdge.of(edgeKey, Collections.singletonList(contributor))),
                Collections.emptyList(), Collections.singletonList(PinProjection.active(
                    PinRecord.of(sourceReference, 0.0, 0.0, Collections.emptyList()), source)));
            final Map<EnclosureHullKey, HullGeometry> hullGeometry =
                new LinkedHashMap<EnclosureHullKey, HullGeometry>();
            hullGeometry.put(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(sourceReference)))),
                rectangle(-12.0, -12.0, 12.0, 12.0, 0.0, 0.0));
            hullGeometry.put(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(prominentReference)))),
                rectangle(82.0, 12.0, 118.0, 48.0, 100.0, 30.0));
            hullGeometry.put(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(tieReference)))),
                rectangle(92.0, -38.0, 108.0, -22.0, 100.0, -30.0));
            hullGeometry.put(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(leftReference)))),
                rectangle(-109.0, 1.0, -91.0, 19.0, -100.0, 10.0));
            hullGeometry.put(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(upReference)))),
                rectangle(-10.0, -110.0, 10.0, -90.0, 0.0, -100.0));
            hullGeometry.put(enclosureHull, rectangle(-20.0, 80.0, 20.0, 120.0, 0.0, 100.0));
            hullGeometry.put(suppressedHull, rectangle(80.0, 160.0, 110.0, 190.0, 95.0, 175.0));
            final GraphGeometry geometry = GraphGeometry.of(
                Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hullGeometry);
            final Map<EnclosureHullKey, LayoutPoint> anchors =
                new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
            for (Map.Entry<EnclosureHullKey, HullGeometry> entry : hullGeometry.entrySet()) {
                anchors.put(entry.getKey(), entry.getValue().labelAnchor());
            }
            final LayoutFrame frame = LayoutFrame.of(1L,
                LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), anchors), false);
            final CanvasState state = CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE);
            return new Fixture(state, sourceEndpoint, prominentEndpoint, tieEndpoint, leftEndpoint,
                upEndpoint, enclosureEndpoint, suppressedEndpoint, geometrylessEndpoint);
        }

        private static ProjectedEnclosure boundary(final NodeReference reference, final String fullLabel,
                final String displayLabel, final String mapName) {
            return ProjectedEnclosure.of(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(reference)))),
                Collections.singletonList(EnclosureKey.of(SourceNodeKey.persisted(reference))),
                Collections.singletonList(SafeNodeLabel.of(fullLabel, displayLabel)), mapName,
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        }

        private static HullGeometry rectangle(final double minX, final double minY,
                final double maxX, final double maxY, final double anchorX, final double anchorY) {
            return HullGeometry.of(Arrays.asList(LayoutPoint.of(minX, minY),
                LayoutPoint.of(maxX, minY), LayoutPoint.of(maxX, maxY), LayoutPoint.of(minX, maxY)),
                LayoutPoint.of(anchorX, anchorY));
        }

        private static NodeReference reference(final MapReferenceId map, final String id) {
            return NodeReference.of(map, PersistedNodeId.of(id));
        }

        private static ProjectedNodeKey nodeKey(final NodeReference reference) {
            return ProjectedNodeKey.of(SourceNodeKey.persisted(reference));
        }
    }
}
