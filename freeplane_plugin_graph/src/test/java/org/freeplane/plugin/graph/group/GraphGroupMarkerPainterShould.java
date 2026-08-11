package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.cloud.CloudController;
import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.cloud.CloudShape;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.GraphModeExtension;
import org.freeplane.view.swing.map.MainView;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.NodeView;
import org.freeplane.view.swing.map.NodeViewDecorationPainter;
import org.freeplane.view.swing.map.NodeViewDecorationRegistry;
import org.freeplane.view.swing.map.cloud.CloudView;
import org.freeplane.view.swing.map.cloud.CloudViewFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphGroupMarkerPainterShould {
    private static final Color CORAL = new Color(0xDF625D);
    private static final int MINIMUM_OUTER_GAP_AT_ZOOM_ONE = 6;

    private MockedStatic<ResourceController> resourceControllers;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class);
        when(resources.getProperty(anyString())).thenReturn("");
        when(resources.getProperty(CloudController.RESOURCES_CLOUD_COLOR)).thenReturn("#ff808080");
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
    public void paintsOutsideRealOrdinaryCloudsWithoutChangingTheirState() {
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();
        Color[] cloudColors = new Color[] {Color.GREEN, Color.BLUE, Color.ORANGE, Color.CYAN};

        for (int index = 0; index < CloudShape.values().length; index++) {
            CloudShape shape = CloudShape.values()[index];
            CloudFixture fixture = cloudFixture(shape, cloudColors[index]);
            PaintOnlySnapshot beforeMarkerPaint = PaintOnlySnapshot.capture(fixture.view);

            BufferedImage ordinaryCloud = image();
            paintCloud(fixture.cloud, fixture.view, ordinaryCloud);
            Rectangle cloudBounds = nonTransparentBounds(ordinaryCloud);
            assertThat(cloudBounds.width).isGreaterThan(0);
            assertThat(cloudBounds.height).isGreaterThan(0);
            assertThat(containsOpaqueColor(ordinaryCloud, cloudColors[index])).isTrue();

            BufferedImage marker = image();
            paint(painter, fixture.view, marker);
            beforeMarkerPaint.assertUnchanged(fixture.view);
            Rectangle markerBounds = opaqueCoralBounds(marker);
            assertThat(markerBounds.width).isGreaterThan(0);
            assertThat(markerBounds.height).isGreaterThan(0);
            assertMarkerStaysOutsideCloud(markerBounds, cloudBounds, fixture.view);

            BufferedImage combined = image();
            paint(painter, fixture.view, combined);
            paintCloud(fixture.cloud, fixture.view, combined);
            assertThat(containsOpaqueColor(combined, cloudColors[index])).isTrue();
            assertThat(containsOpaqueCoral(combined)).isTrue();

            fixture.cloud.setColor(Color.MAGENTA);
            PaintOnlySnapshot beforeRecoloredMarkerPaint = PaintOnlySnapshot.capture(fixture.view);
            BufferedImage recoloredMarker = image();
            paint(painter, fixture.view, recoloredMarker);
            beforeRecoloredMarkerPaint.assertUnchanged(fixture.view);
            assertThat(opaqueCoralPoints(recoloredMarker)).isEqualTo(opaqueCoralPoints(marker));
        }
    }

    @Test
    public void usesRealNodeViewCoordinatesForVisibleSubtreesAndDoesNotMutatePaintState() {
        NodeViewFixture fixture = new NodeViewFixture();
        MapModel map = mapWithRoot();
        NodeModel marked = new NodeModel("marked", map);
        NodeModel visible = new NodeModel("visible", map);
        NodeModel folded = new NodeModel("folded", map);
        NodeModel omitted = new NodeModel("omitted", map);
        map.getRootNode().insert(marked);
        marked.insert(visible);
        marked.insert(folded);
        folded.insert(omitted);
        marked.addExtension(new GraphGroupModel());
        CloudModel cloud = cloud(CloudShape.RECT, Color.GREEN);
        marked.addExtension(cloud);

        TestNodeView markedView = fixture.create(marked, 0, 0, 180, 120, new Rectangle(20, 30, 40, 20));
        markedView.putClientProperty(CloudModel.class, cloud);
        TestNodeView visibleView = fixture.create(visible, 90, 10, 60, 40, new Rectangle(4, 5, 30, 15));
        TestNodeView foldedView = fixture.create(folded, 220, 90, 60, 40, new Rectangle(3, 4, 30, 15));
        markedView.add(visibleView);
        markedView.add(foldedView);
        foldedView.setVisible(false);
        markedView.resetPaintMutationCountsRecursively();

        LinkedList<Point> visibleCoordinates = new LinkedList<Point>();
        markedView.getCoordinates(visibleCoordinates);
        Rectangle visibleBounds = pointBounds(visibleCoordinates);
        Rectangle omittedWouldBeBounds = translatedContentBounds(foldedView);
        assertThat(visibleCoordinates).hasSize(8);
        assertThat(visibleBounds.x + visibleBounds.width - 1).isLessThan(omittedWouldBeBounds.x);

        PaintOnlySnapshot beforeMarkerPaint = PaintOnlySnapshot.capture(markedView);
        NodeState omittedState = new NodeState(omitted);
        BufferedImage image = image();
        paint(new GraphGroupMarkerPainter(), markedView, image);
        beforeMarkerPaint.assertUnchanged(markedView);
        omittedState.assertUnchanged();
        Rectangle markerBounds = opaqueCoralBounds(image);
        assertThat(markerBounds.x).isLessThanOrEqualTo(visibleBounds.x);
        assertThat(markerBounds.y).isLessThanOrEqualTo(visibleBounds.y);
        assertThat(markerBounds.x + markerBounds.width - 1)
            .isGreaterThanOrEqualTo(visibleBounds.x + visibleBounds.width - 1);
        assertThat(markerBounds.y + markerBounds.height - 1)
            .isGreaterThanOrEqualTo(visibleBounds.y + visibleBounds.height - 1);
        assertThat(markerBounds.x + markerBounds.width - 1).isLessThan(omittedWouldBeBounds.x);

        NodeModel markedLeaf = new NodeModel("marked leaf", map);
        map.getRootNode().insert(markedLeaf);
        markedLeaf.addExtension(new GraphGroupModel());
        TestNodeView leafView = fixture.create(markedLeaf, 0, 0, 100, 80, new Rectangle(45, 35, 20, 10));
        leafView.resetPaintMutationCountsRecursively();
        PaintOnlySnapshot beforeLeafPaint = PaintOnlySnapshot.capture(leafView);
        BufferedImage leafImage = image();
        paint(new GraphGroupMarkerPainter(), leafView, leafImage);
        beforeLeafPaint.assertUnchanged(leafView);
        assertThat(nonTransparentPixelCount(leafImage)).isGreaterThan(0);
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

    private static CloudFixture cloudFixture(CloudShape shape, Color color) {
        NodeViewFixture nodeViewFixture = new NodeViewFixture();
        MapModel map = mapWithRoot();
        NodeModel marked = new NodeModel("marked", map);
        map.getRootNode().insert(marked);
        marked.addExtension(new GraphGroupModel());
        CloudModel cloud = cloud(shape, color);
        marked.addExtension(cloud);
        TestNodeView view = nodeViewFixture.create(marked, 0, 0, 300, 200,
            new Rectangle(130, 100, 80, 40));
        view.putClientProperty(CloudModel.class, cloud);
        view.resetPaintMutationCountsRecursively();
        return new CloudFixture(cloud, view);
    }

    private static CloudModel cloud(CloudShape shape, Color color) {
        CloudModel cloud = new CloudModel();
        cloud.setShape(shape);
        cloud.setColor(color);
        return cloud;
    }

    private static void assertMarkerStaysOutsideCloud(Rectangle markerBounds, Rectangle cloudBounds,
                                                       TestNodeView view) {
        int outerGap = view.getZoomed(MINIMUM_OUTER_GAP_AT_ZOOM_ONE);
        int markerRight = markerBounds.x + markerBounds.width - 1;
        int markerBottom = markerBounds.y + markerBounds.height - 1;
        int cloudRight = cloudBounds.x + cloudBounds.width - 1;
        int cloudBottom = cloudBounds.y + cloudBounds.height - 1;

        assertThat(markerBounds.x).isLessThanOrEqualTo(cloudBounds.x - outerGap);
        assertThat(markerBounds.y).isLessThanOrEqualTo(cloudBounds.y - outerGap);
        assertThat(markerRight).isGreaterThanOrEqualTo(cloudRight + outerGap);
        assertThat(markerBottom).isGreaterThanOrEqualTo(cloudBottom + outerGap);
    }

    private static Rectangle opaqueCoralBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (Point point : opaqueCoralPoints(image)) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return maxX < 0 ? new Rectangle() : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Set<Point> opaqueCoralPoints(BufferedImage image) {
        Set<Point> points = new HashSet<Point>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() == CORAL.getRed() && color.getGreen() == CORAL.getGreen()
                        && color.getBlue() == CORAL.getBlue() && color.getAlpha() == 255) {
                    points.add(new Point(x, y));
                }
            }
        }
        return points;
    }

    private static boolean containsOpaqueColor(BufferedImage image, Color expected) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() == expected.getRed() && color.getGreen() == expected.getGreen()
                        && color.getBlue() == expected.getBlue() && color.getAlpha() == 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Rectangle pointBounds(LinkedList<Point> points) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Rectangle translatedContentBounds(TestNodeView view) {
        Rectangle bounds = new Rectangle(view.getContent().getBounds());
        bounds.translate(view.getX(), view.getY());
        return bounds;
    }

    private static void paintCloud(CloudModel cloud, NodeView view, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        try {
            CloudView cloudView = new CloudViewFactory().createCloudView(cloud, view);
            cloudView.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
    }

    private static BufferedImage image() {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
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

    private static final class CloudFixture {
        private final CloudModel cloud;
        private final TestNodeView view;

        private CloudFixture(CloudModel cloud, TestNodeView view) {
            this.cloud = cloud;
            this.view = view;
        }
    }

    private static final class NodeViewFixture {
        private final CloudController cloudController = mock(CloudController.class);
        private final MapController mapController = mock(MapController.class);
        private final MapView mapView = mock(MapView.class);
        private final ModeController modeController = mock(ModeController.class);
        private final TextController textController = mock(TextController.class);

        private NodeViewFixture() {
            when(mapView.getModeController()).thenReturn(modeController);
            when(mapView.getZoom()).thenReturn(1.0f);
            when(mapView.getZoomed(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
            when(mapView.isValid()).thenReturn(true);
            when(modeController.getMapController()).thenReturn(mapController);
            when(modeController.getExtension(CloudController.class)).thenReturn(cloudController);
            when(modeController.getExtension(TextController.class)).thenReturn(textController);
            when(modeController.canEdit(any(NodeModel.class))).thenReturn(false);
            when(mapController.isFolded(any(NodeModel.class))).thenReturn(false);
            when(cloudController.getWidth(any(NodeModel.class))).thenReturn(CloudController.NORMAL_WIDTH);
        }

        private TestNodeView create(NodeModel node, int x, int y, int width, int height, Rectangle contentBounds) {
            TestNodeView view = new TestNodeView(node, mapView);
            view.setTestGeometry(x, y, width, height, contentBounds);
            return view;
        }
    }

    private static final class TestNodeView extends NodeView {
        private final Rectangle testBounds = new Rectangle();
        private int boundsSetCount;
        private int locationSetCount;
        private int maximumSizeSetCount;
        private int minimumSizeSetCount;
        private int preferredSizeSetCount;
        private int revalidateCount;
        private int sizeSetCount;

        private TestNodeView(NodeModel node, MapView map) {
            super(node, map);
            installMainView(this);
        }

        private void setTestGeometry(int x, int y, int width, int height, Rectangle contentBounds) {
            testBounds.setBounds(x, y, width, height);
            getMainView().setBounds(contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);
            setPreferredSize(new Dimension(width, height));
            setMinimumSize(new Dimension(width - 1, height - 1));
            setMaximumSize(new Dimension(width + 1, height + 1));
            resetPaintMutationCountsRecursively();
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(testBounds);
        }

        @Override
        public int getHeight() {
            return testBounds.height;
        }

        @Override
        public Rectangle getInnerBounds() {
            return new Rectangle(getContent().getBounds());
        }

        @Override
        public Point getLocation() {
            return new Point(testBounds.x, testBounds.y);
        }

        @Override
        public int getWidth() {
            return testBounds.width;
        }

        @Override
        public int getX() {
            return testBounds.x;
        }

        @Override
        public int getY() {
            return testBounds.y;
        }

        @Override
        public boolean isContentVisible() {
            return true;
        }

        @Override
        public boolean usesHorizontalLayout() {
            return true;
        }

        @Override
        public int getZoomedFoldingMarkHalfSize() {
            return 0;
        }

        @Override
        public void revalidate() {
            revalidateCount++;
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            boundsSetCount++;
        }

        @Override
        public void setLocation(int x, int y) {
            locationSetCount++;
        }

        @Override
        public void setLocation(Point location) {
            locationSetCount++;
        }

        @Override
        public void setMaximumSize(Dimension maximumSize) {
            maximumSizeSetCount++;
            super.setMaximumSize(maximumSize);
        }

        @Override
        public void setMinimumSize(Dimension minimumSize) {
            minimumSizeSetCount++;
            super.setMinimumSize(minimumSize);
        }

        @Override
        public void setPreferredSize(Dimension preferredSize) {
            preferredSizeSetCount++;
            super.setPreferredSize(preferredSize);
        }

        @Override
        public void setSize(Dimension size) {
            sizeSetCount++;
        }

        @Override
        public void setSize(int width, int height) {
            sizeSetCount++;
        }

        private int paintMutationCount() {
            return boundsSetCount + locationSetCount + maximumSizeSetCount + minimumSizeSetCount
                + preferredSizeSetCount + revalidateCount + sizeSetCount;
        }

        private void resetPaintMutationCountsRecursively() {
            boundsSetCount = 0;
            locationSetCount = 0;
            maximumSizeSetCount = 0;
            minimumSizeSetCount = 0;
            preferredSizeSetCount = 0;
            revalidateCount = 0;
            sizeSetCount = 0;
            for (NodeView child : getChildrenViews()) {
                ((TestNodeView) child).resetPaintMutationCountsRecursively();
            }
        }
    }

    private static final class PaintOnlySnapshot {
        private final List<ViewState> viewStates = new ArrayList<ViewState>();

        private PaintOnlySnapshot(TestNodeView root) {
            collect(root);
        }

        private static PaintOnlySnapshot capture(TestNodeView root) {
            return new PaintOnlySnapshot(root);
        }

        private void assertUnchanged(TestNodeView root) {
            assertThat(viewStates.get(0).view).isSameAs(root);
            for (ViewState viewState : viewStates) {
                viewState.assertUnchanged();
            }
        }

        private void collect(TestNodeView view) {
            viewStates.add(new ViewState(view));
            for (NodeView child : view.getChildrenViews()) {
                collect((TestNodeView) child);
            }
        }
    }

    private static final class ViewState {
        private final Rectangle bounds;
        private final List<NodeView> children;
        private final Dimension maximumSize;
        private final Dimension minimumSize;
        private final CloudModel nodeViewCloud;
        private final NodeState nodeState;
        private final Point location;
        private final Dimension preferredSize;
        private final TestNodeView view;
        private final boolean visible;

        private ViewState(TestNodeView view) {
            this.view = view;
            bounds = view.getBounds();
            location = view.getLocation();
            preferredSize = new Dimension(view.getPreferredSize());
            minimumSize = new Dimension(view.getMinimumSize());
            maximumSize = new Dimension(view.getMaximumSize());
            visible = view.isVisible();
            children = new ArrayList<NodeView>(view.getChildrenViews());
            nodeViewCloud = view.getCloudModel();
            nodeState = new NodeState(view.getNode());
        }

        private void assertUnchanged() {
            assertThat(view.getBounds()).isEqualTo(bounds);
            assertThat(view.getLocation()).isEqualTo(location);
            assertThat(view.getPreferredSize()).isEqualTo(preferredSize);
            assertThat(view.getMinimumSize()).isEqualTo(minimumSize);
            assertThat(view.getMaximumSize()).isEqualTo(maximumSize);
            assertThat(view.isVisible()).isEqualTo(visible);
            assertThat(view.getChildrenViews()).containsExactlyElementsOf(children);
            assertThat(view.getCloudModel()).isSameAs(nodeViewCloud);
            assertThat(view.paintMutationCount()).isZero();
            nodeState.assertUnchanged();
        }
    }

    private static final class NodeState {
        private final CloudModel cloud;
        private final Color cloudColor;
        private final CloudShape cloudShape;
        private final List<IExtension> extensions;
        private final boolean hasCloud;
        private final String id;
        private final NodeModel node;
        private final String text;

        private NodeState(NodeModel node) {
            this.node = node;
            id = node.getID();
            text = node.getText();
            extensions = new ArrayList<IExtension>(node.getSharedExtensions().values());
            cloud = CloudModel.getModel(node);
            hasCloud = node.containsExtension(CloudModel.class);
            cloudShape = cloud == null ? null : cloud.getShape();
            cloudColor = cloud == null ? null : cloud.getColor();
        }

        private void assertUnchanged() {
            assertThat(node.getID()).isEqualTo(id);
            assertThat(node.getText()).isEqualTo(text);
            assertThat(node.getSharedExtensions().values()).containsExactlyElementsOf(extensions);
            assertThat(node.containsExtension(CloudModel.class)).isEqualTo(hasCloud);
            assertThat(CloudModel.getModel(node)).isSameAs(cloud);
            if (cloud != null) {
                assertThat(cloud.getShape()).isSameAs(cloudShape);
                assertThat(cloud.getColor()).isSameAs(cloudColor);
            }
        }
    }

    private static void installMainView(NodeView nodeView) {
        try {
            Constructor<MainView> mainViewConstructor = MainView.class.getDeclaredConstructor();
            mainViewConstructor.setAccessible(true);
            MainView mainView = mainViewConstructor.newInstance();
            Method setMainView = NodeView.class.getDeclaredMethod("setMainView", MainView.class);
            setMainView.setAccessible(true);
            setMainView.invoke(nodeView, mainView);
            Constructor<?> forkPainterConstructor = Class.forName("org.freeplane.view.swing.map.ForkPainter")
                .getDeclaredConstructor(MainView.class);
            forkPainterConstructor.setAccessible(true);
            Field painter = MainView.class.getDeclaredField("painter");
            painter.setAccessible(true);
            painter.set(mainView, forkPainterConstructor.newInstance(mainView));
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
