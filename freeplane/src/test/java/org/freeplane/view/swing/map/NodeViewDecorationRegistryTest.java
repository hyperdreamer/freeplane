package org.freeplane.view.swing.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IMouseListener;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.ui.IMapViewManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class NodeViewDecorationRegistryTest {
    private ModeController modeController;
    private final Map<Class<?>, IExtension> extensions = new HashMap<Class<?>, IExtension>();
    private MockedStatic<ResourceController> resourceControllers;
    private Controller previousController;
    private boolean controllerOverridden;

    @Before
    public void setUp() {
        modeController = mock(ModeController.class);
        doAnswer(invocation -> {
            extensions.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(modeController).addExtension(any(Class.class), any(IExtension.class));
        when(modeController.getExtension(any(Class.class))).thenAnswer(invocation ->
            extensions.get(invocation.getArgument(0)));
    }

    @After
    public void tearDown() {
        if (resourceControllers != null) {
            resourceControllers.close();
            resourceControllers = null;
        }
        if (controllerOverridden) {
            Controller.setCurrentController(previousController);
            previousController = null;
            controllerOverridden = false;
        }
        extensions.clear();
    }

    @Test
    public void lazilyInstallsAndReusesOneRegistry() {
        NodeViewDecorationRegistry first = NodeViewDecorationRegistry.of(modeController);
        NodeViewDecorationRegistry second = NodeViewDecorationRegistry.of(modeController);

        assertThat(first).isSameAs(second);
        assertThat(extensions.get(NodeViewDecorationRegistry.class)).isSameAs(first);
        verify(modeController, times(1)).addExtension(eq(NodeViewDecorationRegistry.class), eq(first));
    }

    @Test
    public void keepsRegistrationOrderAndUsesExactInstanceRemoval() {
        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        List<String> calls = new ArrayList<String>();
        NodeViewDecorationPainter first = (nodeView, graphics) -> calls.add("first");
        NodeViewDecorationPainter second = (nodeView, graphics) -> calls.add("second");
        NodeViewDecorationPainter equalButDifferent = (nodeView, graphics) -> calls.add("other");

        registry.add(first);
        registry.add(second);
        registry.add(first);
        registry.remove(equalButDifferent);

        paint(registry);
        assertThat(calls).containsExactly("first", "second");

        registry.remove(first);
        calls.clear();
        paint(registry);
        assertThat(calls).containsExactly("second");
        assertThat(registry.isEmpty()).isFalse();

        registry.remove(first);
        registry.remove(second);
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    public void rejectsNullControllersAndPainters() {
        assertThatThrownBy(() -> NodeViewDecorationRegistry.of(null))
            .isInstanceOf(NullPointerException.class);

        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        assertThatThrownBy(() -> registry.add(null))
            .isInstanceOf(NullPointerException.class);
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    public void isolatesPainterGraphicsAndDisposesEveryCopy() {
        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        Graphics2D caller = mock(Graphics2D.class);
        Graphics2D firstCopy = mock(Graphics2D.class);
        Graphics2D secondCopy = mock(Graphics2D.class);
        when(caller.create()).thenReturn(firstCopy, secondCopy);
        List<Graphics2D> received = new ArrayList<Graphics2D>();
        registry.add((nodeView, graphics) -> {
            received.add(graphics);
            graphics.translate(7, 11);
            graphics.setColor(Color.RED);
        });
        registry.add((nodeView, graphics) -> received.add(graphics));

        registry.paint(null, caller);

        assertThat(received).containsExactly(firstCopy, secondCopy);
        verify(caller, never()).dispose();
        verify(caller, never()).translate(7, 11);
        verify(firstCopy, times(1)).dispose();
        verify(secondCopy, times(1)).dispose();
    }

    @Test
    public void disposesACopyWhenItsPainterThrowsAndLeavesTheCallerAlive() {
        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        Graphics2D caller = mock(Graphics2D.class);
        Graphics2D copy = mock(Graphics2D.class);
        when(caller.create()).thenReturn(copy);
        RuntimeException failure = new RuntimeException("paint failure");
        registry.add((nodeView, graphics) -> {
            throw failure;
        });

        assertThatThrownBy(() -> registry.paint(null, caller)).isSameAs(failure);

        verify(copy, times(1)).dispose();
        verify(caller, never()).dispose();
    }

    @Test
    public void usesAStablePainterSnapshotDuringCallbacks() {
        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        List<String> calls = new ArrayList<String>();
        NodeViewDecorationPainter[] added = new NodeViewDecorationPainter[1];
        NodeViewDecorationPainter[] firstHolder = new NodeViewDecorationPainter[1];

        NodeViewDecorationPainter first = (nodeView, graphics) -> {
            calls.add("first");
            registry.add(added[0]);
            registry.remove(firstHolder[0]);
        };
        firstHolder[0] = first;
        NodeViewDecorationPainter second = (nodeView, graphics) -> calls.add("second");
        added[0] = (nodeView, graphics) -> calls.add("added");
        registry.add(first);
        registry.add(second);

        paint(registry);
        assertThat(calls).containsExactly("first", "second");
        calls.clear();
        paint(registry);
        assertThat(calls).containsExactly("second", "added");
    }

    @Test
    public void invokesDecorationsOncePerVisibleNodeInTheCloudCoordinateFrame() {
        setUpNodeViewEnvironment();
        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        List<NodeView> painted = new ArrayList<NodeView>();
        List<AffineTransform> transforms = new ArrayList<AffineTransform>();
        registry.add((nodeView, graphics) -> {
            painted.add(nodeView);
            transforms.add(graphics.getTransform());
        });

        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel rootNode = new NodeModel("root", mapModel);
        NodeModel childNode = new NodeModel("child", mapModel);
        mapModel.setRoot(rootNode);
        rootNode.insert(childNode);
        TestMapView mapView = new TestMapView(mapModel, modeController);
        TestNodeView root = new TestNodeView(rootNode, mapView, true);
        TestNodeView child = new TestNodeView(childNode, mapView, false);
        child.setTestLocation(23, 31);
        root.add(child);

        Graphics2D graphics = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            mapView.setTestPaintingMode(PaintingMode.CLOUDS);
            root.paintComponent(graphics);
        }
        finally {
            graphics.dispose();
        }

        assertThat(painted).containsExactly(root, child);
        assertThat(transforms.get(0)).isEqualTo(new AffineTransform());
        assertThat(transforms.get(1).getTranslateX()).isEqualTo(23.0);
        assertThat(transforms.get(1).getTranslateY()).isEqualTo(31.0);
        assertThat(graphics.getTransform()).isEqualTo(new AffineTransform());
    }

    @Test
    public void leavesCloudPaintingUnchangedWhenRegistryIsAbsentOrEmptyAndSkipsOtherPasses() {
        setUpNodeViewEnvironment();
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel rootNode = new NodeModel("root", mapModel);
        mapModel.setRoot(rootNode);
        TestMapView mapView = new TestMapView(mapModel, modeController);
        TestNodeView root = new TestNodeView(rootNode, mapView, true);

        mapView.setTestPaintingMode(PaintingMode.CLOUDS);
        assertCloudsPassIsANoOp(root);

        NodeViewDecorationRegistry registry = NodeViewDecorationRegistry.of(modeController);
        final int[] callbacks = new int[1];
        NodeViewDecorationPainter removedPainter = (nodeView, graphics) -> callbacks[0]++;
        registry.add(removedPainter);
        registry.remove(removedPainter);
        assertThat(registry.isEmpty()).isTrue();

        assertCloudsPassIsANoOp(root);
        assertThat(callbacks[0]).isZero();

        mapView.setTestPaintingMode(PaintingMode.SELECTED_NODES);
        Graphics2D otherPassGraphics = mock(Graphics2D.class);
        Graphics2D otherPassChild = mock(Graphics2D.class);
        when(otherPassGraphics.create()).thenReturn(otherPassChild);
        clearInvocations(otherPassGraphics, otherPassChild);
        root.paintComponent(otherPassGraphics);
        verifyNoInteractions(otherPassGraphics, otherPassChild);
    }

    private static void assertCloudsPassIsANoOp(TestNodeView root) {
        BufferedImage image = new BufferedImage(96, 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(7, 9);
        graphics.setColor(Color.MAGENTA);
        graphics.setStroke(new BasicStroke(3.0f));
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        graphics.setClip(2, 3, 70, 50);
        AffineTransform transform = graphics.getTransform();
        Color color = graphics.getColor();
        Stroke stroke = graphics.getStroke();
        Composite composite = graphics.getComposite();
        Shape clip = graphics.getClip();
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        try {
            root.paintComponent(graphics);

            assertThat(graphics.getTransform()).isEqualTo(transform);
            assertThat(graphics.getColor()).isEqualTo(color);
            assertThat(graphics.getStroke()).isEqualTo(stroke);
            assertThat(graphics.getComposite()).isEqualTo(composite);
            assertThat(graphics.getClip()).isEqualTo(clip);
            assertThat(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                .containsExactly(pixels);
        }
        finally {
            graphics.dispose();
        }

        Graphics2D recording = mock(Graphics2D.class);
        Graphics2D child = mock(Graphics2D.class);
        when(recording.create()).thenReturn(child);
        clearInvocations(recording, child);
        root.paintComponent(recording);
        verifyNoInteractions(recording, child);
    }

    private void paint(NodeViewDecorationRegistry registry) {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            registry.paint(null, graphics);
        }
        finally {
            graphics.dispose();
        }
    }

    private void setUpNodeViewEnvironment() {
        previousController = Controller.getCurrentController();
        Controller controller = mock(Controller.class);
        ResourceController resources = mock(ResourceController.class);
        IMapViewManager mapViewManager = mock(IMapViewManager.class);
        MapController mapController = mock(MapController.class);
        TextController textController = mock(TextController.class);
        IUserInputListenerFactory inputFactory = mock(IUserInputListenerFactory.class);
        IMouseListener mapMouseListener = mock(IMouseListener.class);

        when(controller.getResourceController()).thenReturn(resources);
        when(controller.getMapViewManager()).thenReturn(mapViewManager);
        when(controller.getModeController()).thenReturn(modeController);
        when(modeController.getMapController()).thenReturn(mapController);
        when(modeController.getUserInputListenerFactory()).thenReturn(inputFactory);
        when(modeController.getExtension(TextController.class)).thenReturn(textController);
        when(modeController.canEdit(any(NodeModel.class))).thenReturn(false);
        when(mapController.isFolded(any(NodeModel.class))).thenReturn(false);
        when(inputFactory.getMapMouseListener()).thenReturn(mapMouseListener);
        when(inputFactory.getMapMouseWheelListener()).thenReturn(new java.awt.event.MouseAdapter() {
        });
        when(resources.getProperty(anyString())).thenReturn("");
        when(resources.getBooleanProperty(anyString())).thenReturn(false);
        when(resources.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(resources.getColorProperty(anyString())).thenReturn(Color.WHITE);
        when(resources.getLengthProperty(anyString())).thenReturn(0);
        resourceControllers = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceControllers.when(ResourceController::getResourceController).thenReturn(resources);
        Controller.setCurrentController(controller);
        controllerOverridden = true;
    }

    private static class TestMapView extends MapView {
        private PaintingMode testPaintingMode;

        private TestMapView(MapModel map, ModeController modeController) {
            super(map, modeController);
        }

        @Override
        public void setMap(MapModel viewedMap) {
        }

        @Override
        protected PaintingMode getPaintingMode() {
            return testPaintingMode;
        }

        private void setTestPaintingMode(PaintingMode paintingMode) {
            testPaintingMode = paintingMode;
        }
    }

    private static class TestNodeView extends NodeView {
        private final boolean root;
        private int testX;
        private int testY;

        private TestNodeView(NodeModel node, MapView map, boolean root) {
            super(node, map);
            this.root = root;
            setMainView(new MainView());
        }

        @Override
        public boolean isRoot() {
            return root;
        }

        @Override
        boolean isSubtreeVisible() {
            return true;
        }

        @Override
        public boolean isContentVisible() {
            return true;
        }

        @Override
        public int getX() {
            return testX;
        }

        @Override
        public int getY() {
            return testY;
        }

        private void setTestLocation(int x, int y) {
            testX = x;
            testY = y;
        }

        @Override
        void updateIcons() {
        }

        @Override
        void fireFoldingChanged() {
        }

        @Override
        void resetLayoutPropertiesRecursively() {
        }
    }
}
