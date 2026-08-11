package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.cloud.CloudShape;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.GraphModeExtension;
import org.freeplane.view.swing.map.NodeView;
import org.freeplane.view.swing.map.NodeViewDecorationPainter;
import org.freeplane.view.swing.map.NodeViewDecorationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphGroupMarkerPainterShould {
    private static final Color CORAL = new Color(0xDF625D);

    private MockedStatic<ResourceController> resourceControllers;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class);
        when(resources.getProperty(anyString())).thenReturn("");
        resourceControllers = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceControllers.when(ResourceController::getResourceController).thenReturn(resources);
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getRawText(any(String.class))).thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceControllers.close();
    }

    @Test
    public void paintsOnlyRecognizedMarkedNodesAndHandlesEmptyCoordinates() {
        MapModel map = mapWithRoot();
        NodeModel unmarked = new NodeModel("unmarked", map);
        map.getRootNode().insert(unmarked);
        NodeView unmarkedView = nodeView(unmarked, new Point[0], null);
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();

        BufferedImage image = image();
        paint(painter, unmarkedView, image);
        assertThat(nonTransparentPixelCount(image)).isZero();

        NodeModel marked = new NodeModel("marked", map);
        map.getRootNode().insert(marked);
        marked.addExtension(new GraphGroupModel());
        NodeView emptyView = nodeView(marked, new Point[0], null);
        paint(painter, emptyView, image);
        assertThat(nonTransparentPixelCount(image)).isZero();
    }

    @Test
    public void drawsOneCoralEnvelopeAroundTheWholeVisibleSubtreeAndALeaf() {
        MapModel map = mapWithRoot();
        NodeModel marked = new NodeModel("marked", map);
        map.getRootNode().insert(marked);
        marked.addExtension(new GraphGroupModel());
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();

        BufferedImage image = image();
        paint(painter, nodeView(marked, new Point[] {
            new Point(20, 20), new Point(20, 45), new Point(115, 45), new Point(115, 20)
        }, null), image);
        Rectangle bounds = nonTransparentBounds(image);

        assertThat(bounds.x).isLessThanOrEqualTo(14);
        assertThat(bounds.y).isLessThanOrEqualTo(14);
        assertThat(bounds.x + bounds.width - 1).isGreaterThanOrEqualTo(121);
        assertThat(bounds.y + bounds.height - 1).isGreaterThanOrEqualTo(51);
        assertThat(containsOpaqueCoral(image)).isTrue();
        Color fill = new Color(image.getRGB(60, 30), true);
        assertThat(Math.abs(fill.getRed() - CORAL.getRed())).isLessThanOrEqualTo(4);
        assertThat(Math.abs(fill.getGreen() - CORAL.getGreen())).isLessThanOrEqualTo(4);
        assertThat(Math.abs(fill.getBlue() - CORAL.getBlue())).isLessThanOrEqualTo(4);
        assertThat(fill.getAlpha()).isGreaterThan(0).isLessThan(255);

        BufferedImage leafImage = image();
        paint(painter, nodeView(marked, new Point[] {new Point(80, 70)}, null), leafImage);
        assertThat(nonTransparentPixelCount(leafImage)).isGreaterThan(0);
    }

    @Test
    public void rendersNestedMarkersWithLowerAlphaAndAStableDashedStroke() {
        MapModel map = mapWithRoot();
        NodeModel parent = new NodeModel("parent", map);
        NodeModel child = new NodeModel("child", map);
        map.getRootNode().insert(parent);
        parent.insert(child);
        parent.addExtension(new GraphGroupModel());
        child.addExtension(new GraphGroupModel());
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();

        BufferedImage activeImage = image();
        paint(painter, nodeView(parent, new Point[] {
            new Point(25, 25), new Point(120, 55)
        }, null), activeImage);
        BufferedImage inactiveImage = image();
        paint(painter, nodeView(child, new Point[] {
            new Point(25, 25), new Point(120, 55)
        }, null), inactiveImage);

        assertThat(maxAlphaWithCoral(activeImage)).isEqualTo(255);
        assertThat(maxAlphaWithCoral(inactiveImage)).isLessThan(255);
        assertThat(nonTransparentPixelCount(inactiveImage)).isLessThan(nonTransparentPixelCount(activeImage));
    }

    @Test
    public void keepsMarkerStyleIndependentOfEachOrdinaryCloudShapeAndColor() {
        MapModel map = mapWithRoot();
        NodeModel marked = new NodeModel("marked", map);
        map.getRootNode().insert(marked);
        marked.addExtension(new GraphGroupModel());
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();
        Map<CloudShape, Rectangle> bounds = new EnumMap<CloudShape, Rectangle>(CloudShape.class);

        for (CloudShape shape : CloudShape.values()) {
            CloudModel cloud = mock(CloudModel.class);
            when(cloud.getShape()).thenReturn(shape);
            when(cloud.getColor()).thenReturn(Color.GREEN);
            NodeView view = nodeView(marked, new Point[] {
                new Point(30, 30), new Point(95, 55)
            }, cloud);
            BufferedImage image = image();
            paint(painter, view, image);
            bounds.put(shape, nonTransparentBounds(image));

            assertThat(containsOpaqueCoral(image)).isTrue();
            verify(cloud, times(1)).getShape();
            verify(cloud, never()).getColor();
        }

        assertThat(bounds.get(CloudShape.STAR).width).isGreaterThan(bounds.get(CloudShape.RECT).width);
        assertThat(bounds.get(CloudShape.ARC).width).isGreaterThan(bounds.get(CloudShape.RECT).width);
        assertThat(bounds.get(CloudShape.ROUND_RECT).width).isEqualTo(bounds.get(CloudShape.RECT).width);
    }

    @Test
    public void installsRemovesAndReinstallsExactlyOnePainterWithoutRemovingSharedRegistry() {
        ModeSetup setup = new ModeSetup();
        NodeViewDecorationRegistry initial = NodeViewDecorationRegistry.of(setup.modeController);
        NodeViewDecorationRegistry registry = spy(initial);
        setup.extensions.put(NodeViewDecorationRegistry.class, registry);
        clearInvocations(setup.modeController);
        setup.reinstallExtensionState();

        GraphModeExtension extension = new GraphModeExtension();
        extension.installExtension(setup.modeController, null);
        extension.installExtension(setup.modeController, null);
        ArgumentCaptor<NodeViewDecorationPainter> firstPainter =
            ArgumentCaptor.forClass(NodeViewDecorationPainter.class);
        verify(registry, times(1)).add(firstPainter.capture());
        assertThat(registry.isEmpty()).isFalse();

        extension.close();
        verify(registry, times(1)).remove(same(firstPainter.getValue()));
        assertThat(setup.extensions.get(NodeViewDecorationRegistry.class)).isSameAs(registry);
        assertThat(registry.isEmpty()).isTrue();

        extension.installExtension(setup.modeController, null);
        ArgumentCaptor<NodeViewDecorationPainter> allPainters =
            ArgumentCaptor.forClass(NodeViewDecorationPainter.class);
        verify(registry, times(2)).add(allPainters.capture());
        assertThat(allPainters.getAllValues().get(0)).isNotSameAs(allPainters.getAllValues().get(1));
        extension.close();
        verify(registry, times(1)).remove(same(allPainters.getAllValues().get(0)));
        verify(registry, times(1)).remove(same(allPainters.getAllValues().get(1)));
        verify(setup.modeController, times(2)).removeExtension(GraphGroupController.class);
    }

    private static BufferedImage image() {
        BufferedImage image = new BufferedImage(180, 140, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.dispose();
        return image;
    }

    private static void paint(GraphGroupMarkerPainter painter, NodeView view, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        try {
            painter.paint(view, graphics);
        }
        finally {
            graphics.dispose();
        }
    }

    private static NodeView nodeView(NodeModel node, Point[] points, CloudModel cloud) {
        NodeView view = mock(NodeView.class);
        when(view.getNode()).thenReturn(node);
        when(view.getZoomed(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(view.getCloudModel()).thenReturn(cloud);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LinkedList<Point> coordinates = invocation.getArgument(0);
            for (Point point : points) {
                coordinates.add(new Point(point));
            }
            return null;
        }).when(view).getCoordinates(any(LinkedList.class));
        return view;
    }

    private static MapModel mapWithRoot() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        map.setRoot(new NodeModel("root", map));
        return map;
    }

    private static int nonTransparentPixelCount(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Rectangle nonTransparentBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < 0 ? new Rectangle() : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static boolean containsOpaqueCoral(BufferedImage image) {
        return maxAlphaWithCoral(image) == 255;
    }

    private static int maxAlphaWithCoral(BufferedImage image) {
        int maxAlpha = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() == CORAL.getRed() && color.getGreen() == CORAL.getGreen()
                        && color.getBlue() == CORAL.getBlue()) {
                    maxAlpha = Math.max(maxAlpha, color.getAlpha());
                }
            }
        }
        return maxAlpha;
    }

    private static final class ModeSetup {
        private final ModeController modeController = mock(ModeController.class);
        private final MapController mapController = mock(MapController.class);
        private final ReadManager reader = new ReadManager();
        private final WriteManager writer = new WriteManager();
        private final Map<Class<?>, IExtension> extensions = new HashMap<Class<?>, IExtension>();

        private ModeSetup() {
            when(modeController.getMapController()).thenReturn(mapController);
            when(mapController.getReadManager()).thenReturn(reader);
            when(mapController.getWriteManager()).thenReturn(writer);
            doAnswer(invocation -> {
                extensions.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(modeController).addExtension(any(Class.class), any(IExtension.class));
            when(modeController.getExtension(any(Class.class))).thenAnswer(invocation ->
                extensions.get(invocation.getArgument(0)));
        }

        private void reinstallExtensionState() {
            when(modeController.getMapController()).thenReturn(mapController);
            when(mapController.getReadManager()).thenReturn(reader);
            when(mapController.getWriteManager()).thenReturn(writer);
        }
    }
}
