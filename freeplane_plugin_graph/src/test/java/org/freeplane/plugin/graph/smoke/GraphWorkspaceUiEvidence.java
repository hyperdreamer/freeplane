package org.freeplane.plugin.graph.smoke;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspacePresentation;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.group.GraphGroupMarkerPainter;
import org.freeplane.plugin.graph.group.GraphGroupModel;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.view.swing.map.NodeView;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Paints the actual graph workspace shell with deterministic state for reviewable evidence.
 */
public final class GraphWorkspaceUiEvidence {
    private static final MapReferenceId FIRST_MAP = mapId(1L);
    private static final MapReferenceId SECOND_MAP = mapId(2L);

    private GraphWorkspaceUiEvidence() {
    }

    public static void main(final String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected desktop workspace and marker image paths");
        }
        final Path desktop = Paths.get(arguments[0]).toAbsolutePath().normalize();
        final Path marker = Paths.get(arguments[1]).toAbsolutePath().normalize();
        Files.createDirectories(desktop.getParent());
        Files.createDirectories(marker.getParent());

        onEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<ResourceController> resourceController = Mockito.mockStatic(ResourceController.class);
                        MockedStatic<TextUtils> textUtils = Mockito.mockStatic(TextUtils.class, invocation -> {
                            final String method = invocation.getMethod().getName();
                            if (invocation.getArguments().length > 0
                                    && invocation.getArguments()[0] instanceof String
                                    && ("getText".equals(method) || "getRawText".equals(method)
                                        || "format".equals(method))) {
                                return compactText((String) invocation.getArguments()[0]);
                            }
                            return Answers.RETURNS_DEFAULTS.answer(invocation);
                        })) {
                    resourceController.when(ResourceController::getResourceController)
                        .thenReturn(mock(ResourceController.class));
                    final EvidenceImages images = new EvidenceImages(desktop, marker);
                    images.capture();
                }
            }
        });
        System.out.println("Graph UI evidence: EDT shell interactions, desktop workspace, and marker paints passed");
    }

    private static void onEdt(final Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    action.run();
                }
                catch (final Throwable exception) {
                    failure.set(exception);
                }
            }
        });
        final Throwable exception = failure.get();
        if (exception == null) {
            return;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        if (exception instanceof Error) {
            throw (Error) exception;
        }
        throw new IllegalStateException("EDT evidence probe failed", exception);
    }

    private static String compactText(final String key) {
        final int separator = key.lastIndexOf('.');
        final String value = separator < 0 ? key : key.substring(separator + 1);
        if ("add_map".equals(value)) {
            return "Add";
        }
        if ("remove_map".equals(value)) {
            return "Remove";
        }
        if ("retry_map".equals(value)) {
            return "Retry";
        }
        if ("locate_map".equals(value)) {
            return "Locate";
        }
        if ("open".equals(value)) {
            return "O";
        }
        if ("save".equals(value)) {
            return "S";
        }
        if ("undo_workspace".equals(value)) {
            return "U";
        }
        if ("redo_workspace".equals(value)) {
            return "R";
        }
        if ("zoom_in".equals(value)) {
            return "In";
        }
        if ("zoom_out".equals(value)) {
            return "Out";
        }
        if ("fit_graph".equals(value)) {
            return "Fit";
        }
        if ("reset_zoom".equals(value)) {
            return "Reset";
        }
        if ("settings".equals(value)) {
            return "Set";
        }
        if ("connect".equals(value)) {
            return "Link";
        }
        return value.replace('_', ' ');
    }

    private static MapReferenceId mapId(final long value) {
        return MapReferenceId.of(UUID.fromString(String.format(
            "00000000-0000-0000-0000-%012d", Long.valueOf(value))));
    }

    private static CanvasState twoMapState() {
        final SourceNodeKey firstSource = SourceNodeKey.transientPath(FIRST_MAP, Collections.<Integer>emptyList());
        final SourceNodeKey secondSource = SourceNodeKey.transientPath(SECOND_MAP,
            Collections.<Integer>emptyList());
        final ProjectedNodeKey firstKey = ProjectedNodeKey.of(firstSource);
        final ProjectedNodeKey secondKey = ProjectedNodeKey.of(secondSource);
        final ProjectedNode first = ProjectedNode.of(firstKey, SafeNodeLabel.of("Alpha", "Alpha"),
            "Alpha map", false);
        final ProjectedNode second = ProjectedNode.of(secondKey, SafeNodeLabel.of("Beta", "Beta"),
            "Beta map", false);
        final List<ProjectedNode> nodes = Arrays.asList(first, second);
        final GraphProjection projection = GraphProjection.structure(7L, nodes, Collections.emptyList());
        final LayoutPoint firstPoint = LayoutPoint.of(-110.0, -32.0);
        final LayoutPoint secondPoint = LayoutPoint.of(110.0, 32.0);
        final java.util.Map<ProjectedNodeKey, NodeGeometry> geometry =
            new java.util.LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometry.put(firstKey, NodeGeometry.of(firstPoint, 28.0));
        geometry.put(secondKey, NodeGeometry.of(secondPoint, 28.0));
        final java.util.Map<ProjectedNodeKey, LayoutPoint> positions =
            new java.util.LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(firstKey, firstPoint);
        positions.put(secondKey, secondPoint);
        return CanvasState.of(7L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(positions, Collections.emptyMap()), false),
            GraphGeometry.of(geometry, Collections.emptyMap()), OperationalStatus.IDLE);
    }

    private static GraphWorkspacePresentation presentation() {
        return GraphWorkspacePresentation.of(DisplaySettings.defaults(), Arrays.asList(
            GraphWorkspacePresentation.MapColor.of(FIRST_MAP, "#4E79A7"),
            GraphWorkspacePresentation.MapColor.of(SECOND_MAP, "#E15759")));
    }

    private static final class EvidenceImages {
        private final Path desktop;
        private final Path marker;
        private final ModelAccess modelAccess;
        private final JPanel root;
        private final GraphCanvas canvas;

        EvidenceImages(final Path desktop, final Path marker) {
            this.desktop = desktop;
            this.marker = marker;
            final CanvasState state = twoMapState();
            final GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
            when(handle.currentProjection()).thenReturn(state.projection());
            final GraphWorkspaceController applicationController = mock(GraphWorkspaceController.class);
            final WorkspaceCloseController closeController = mock(WorkspaceCloseController.class);
            final GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
            when(binding.currentCanvasState()).thenReturn(state);
            when(binding.currentViewport()).thenReturn(Viewport.of(0.0, 0.0, 1.0,
                Collections.emptyList()));
            when(binding.currentMapRows()).thenReturn(Arrays.asList(
                GraphWorkspaceViewBinding.MapRegistration.of(FIRST_MAP, "Alpha map", MapAvailability.AVAILABLE),
                GraphWorkspaceViewBinding.MapRegistration.of(SECOND_MAP, "Beta map", MapAvailability.AVAILABLE)));
            when(binding.currentPresentation()).thenReturn(presentation());
            when(binding.currentSessionStatus()).thenReturn(WorkspaceSessionStatus.empty());
            when(binding.addCanvasStateListener(any())).thenReturn(mock(ListenerRegistration.class));
            when(binding.addSessionStatusListener(any())).thenReturn(mock(ListenerRegistration.class));
            modelAccess = ModelAccess.create(handle, binding, applicationController, closeController);
            modelAccess.completeInitialLayout();
            final JMenuBar menuBar = (JMenuBar) modelAccess.invoke("menuBar");
            final JPanel content = (JPanel) modelAccess.invoke("content");
            root = new JPanel(new java.awt.BorderLayout());
            root.setName("graph-workspace-root-panel");
            root.add(menuBar, java.awt.BorderLayout.NORTH);
            root.add(content, java.awt.BorderLayout.CENTER);
            canvas = (GraphCanvas) findNamed(root, "graph-workspace-canvas");
            if (canvas == null) {
                throw new AssertionError("Full graph workspace root has no canvas");
            }
        }

        void capture() {
            root.setSize(new Dimension(1280, 800));
            layoutRecursively(root);
            dispatchInteractions();
            paintAndVerify(desktop, root);

            root.setSize(new Dimension(900, 900));
            layoutRecursively(root);
            verifyWorkspacePaint(root);
            paintMarkerEvidence(marker);
            modelAccess.close();
        }

        private void verifyWorkspacePaint(final JPanel panel) {
            final BufferedImage image = render(panel);
            assertNonBlank(image, Paths.get("narrow graph workspace"));
            assertNoOverlap(panel);
        }

        private void paintMarkerEvidence(final Path path) {
            final MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
            final NodeModel rootNode = new NodeModel("root", map);
            map.setRoot(rootNode);
            final NodeModel marked = new NodeModel("marked", map);
            rootNode.insert(marked);
            marked.addExtension(new GraphGroupModel());
            if (!GraphGroupModel.isMarked(marked)) {
                throw new AssertionError("Marker fixture did not contain a marked NodeModel");
            }

            final NodeView nodeView = mock(NodeView.class);
            when(nodeView.getNode()).thenReturn(marked);
            when(nodeView.getZoomed(anyInt())).thenAnswer(invocation ->
                Integer.valueOf(((Integer) invocation.getArgument(0)).intValue()));
            final Point[] points = new Point[] {
                new Point(300, 300), new Point(300, 420), new Point(600, 420), new Point(600, 300)
            };
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                final LinkedList<Point> coordinates = (LinkedList<Point>) invocation.getArgument(0);
                for (final Point point : points) {
                    coordinates.add(new Point(point));
                }
                return null;
            }).when(nodeView).getCoordinates(any(LinkedList.class));

            final BufferedImage image = new BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            try {
                new GraphGroupMarkerPainter().paint(nodeView, graphics);
            }
            finally {
                graphics.dispose();
            }
            final Rectangle markerBounds = opaqueCoralBounds(image);
            final int markerPixels = opaqueCoralPixelCount(image);
            if (markerPixels < 20 || markerBounds.width <= 300 || markerBounds.height <= 120
                    || markerBounds.x > 300 || markerBounds.y > 300
                    || markerBounds.x + markerBounds.width - 1 < 600
                    || markerBounds.y + markerBounds.height - 1 < 420) {
                throw new AssertionError("Marker geometry/coral assertion failed: pixels=" + markerPixels
                    + ", bounds=" + markerBounds);
            }
            writeImage(image, path);
        }

        private void dispatchInteractions() {
            final JComponent zoomIn = findNamed(root, "graph-workspace-zoom-in");
            final JComponent fitGraph = findNamed(root, "graph-workspace-fit-graph");
            final JComponent resetZoom = findNamed(root, "graph-workspace-reset-zoom");
            final JComponent select = findNamed(root, "graph-workspace-select");
            final JComponent connect = findNamed(root, "graph-workspace-connect");
            final JComponent search = findNamed(root, "graph-workspace-search");
            final JComponent direction = findNamed(root, "graph-workspace-direction");
            final JComponent arrowheads = findNamed(root, "graph-workspace-show-arrowheads");
            requireComponent(zoomIn, "zoom in");
            requireComponent(fitGraph, "fit graph");
            requireComponent(resetZoom, "reset zoom");
            requireComponent(select, "select tool");
            requireComponent(connect, "connect tool");
            requireComponent(search, "search field");
            requireComponent(direction, "direction control");
            requireComponent(arrowheads, "arrowhead setting");
            ((AbstractButton) zoomIn).doClick();
            ((AbstractButton) fitGraph).doClick();
            ((AbstractButton) resetZoom).doClick();
            ((AbstractButton) connect).doClick();
            ((AbstractButton) select).doClick();
            ((JComboBox<?>) direction).setSelectedIndex(1);
            ((JTextField) search).setText("Alpha");
            ((AbstractButton) arrowheads).doClick();

            final int firstX = canvas.getWidth() / 2 - 110;
            final int firstY = canvas.getHeight() / 2 - 32;
            final long now = System.currentTimeMillis();
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now,
                InputEvent.BUTTON1_DOWN_MASK, firstX, firstY, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now + 1L,
                0, firstX, firstY, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, now + 2L,
                0, firstX, firstY, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, now + 3L, 0,
                canvas.getWidth() / 2, canvas.getHeight() / 2, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1));
            canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now + 4L, 0,
                KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
        }

        private void paintAndVerify(final Path path, final JPanel panel) {
            final BufferedImage image = render(panel);
            assertNonBlank(image, path);
            assertNoOverlap(panel);
            writeImage(image, path);
        }

        private static BufferedImage render(final JPanel panel) {
            final BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = image.createGraphics();
            try {
                panel.paint(graphics);
            }
            finally {
                graphics.dispose();
            }
            return image;
        }

        private static void writeImage(final BufferedImage image, final Path path) {
            try {
                ImageIO.write(image, "png", path.toFile());
            }
            catch (final java.io.IOException exception) {
                throw new IllegalStateException("Unable to write UI evidence " + path, exception);
            }
        }

        private static int opaqueCoralPixelCount(final BufferedImage image) {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    final Color color = new Color(image.getRGB(x, y), true);
                    if (color.getRed() == 0xDF && color.getGreen() == 0x62
                            && color.getBlue() == 0x5D && color.getAlpha() == 255) {
                        count++;
                    }
                }
            }
            return count;
        }

        private static Rectangle opaqueCoralBounds(final BufferedImage image) {
            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    final Color color = new Color(image.getRGB(x, y), true);
                    if (color.getRed() == 0xDF && color.getGreen() == 0x62
                            && color.getBlue() == 0x5D && color.getAlpha() == 255) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            return maxX < 0 ? new Rectangle() : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }

        private static void assertNonBlank(final BufferedImage image, final Path path) {
            final int reference = image.getRGB(0, 0);
            int differing = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (image.getRGB(x, y) != reference) {
                        differing++;
                    }
                }
            }
            if (differing < 500) {
                throw new AssertionError("Evidence image is blank: " + path + " differingPixels=" + differing);
            }
        }

        private static void assertNoOverlap(final Container container) {
            final Component[] children = container.getComponents();
            for (int first = 0; first < children.length; first++) {
                final Component firstComponent = children[first];
                final Rectangle firstBounds = firstComponent.getBounds();
                if (firstBounds.width <= 0 || firstBounds.height <= 0) {
                    continue;
                }
                if (firstBounds.x < 0 || firstBounds.y < 0
                        || firstBounds.x + firstBounds.width > container.getWidth()
                        || firstBounds.y + firstBounds.height > container.getHeight()) {
                    throw new AssertionError("Component escapes its parent: " + firstComponent.getName());
                }
                for (int second = first + 1; second < children.length; second++) {
                    final Rectangle secondBounds = children[second].getBounds();
                    if (secondBounds.width > 0 && secondBounds.height > 0
                            && firstBounds.intersects(secondBounds)) {
                        throw new AssertionError("Sibling components overlap: " + firstComponent.getName()
                            + " and " + children[second].getName());
                    }
                }
            }
            if (container instanceof javax.swing.JScrollPane) {
                return;
            }
            for (final Component child : children) {
                if (child instanceof Container) {
                    assertNoOverlap((Container) child);
                }
            }
        }

        private static void layoutRecursively(final Container container) {
            container.doLayout();
            for (final Component child : container.getComponents()) {
                if (child instanceof Container) {
                    layoutRecursively((Container) child);
                }
            }
        }
    }

    private static void requireComponent(final JComponent component, final String description) {
        if (component == null) {
            throw new AssertionError("Missing " + description + " in full graph workspace root");
        }
    }

    private static JComponent findNamed(final Container container, final String name) {
        if (name.equals(container.getName())) {
            return container instanceof JComponent ? (JComponent) container : null;
        }
        for (final Component child : container.getComponents()) {
            if (child instanceof JComponent && name.equals(child.getName())) {
                return (JComponent) child;
            }
            if (child instanceof Container) {
                final JComponent result = findNamed((Container) child, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static final class ModelAccess {
        private final Object model;
        private final Class<?> type;

        private ModelAccess(final Object model, final Class<?> type) {
            this.model = model;
            this.type = type;
        }

        static ModelAccess create(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
                final GraphWorkspaceController applicationController, final WorkspaceCloseController closeController) {
            try {
                final Class<?> type = Class.forName("org.freeplane.plugin.graph.window.GraphWorkspaceWindowModel");
                final Constructor<?> constructor = type.getDeclaredConstructor(GraphWorkspaceHandle.class,
                    GraphWorkspaceViewBinding.class, GraphWorkspaceController.class, Supplier.class,
                    WorkspaceCloseController.class, Runnable.class, Runnable.class, Runnable.class, Consumer.class);
                constructor.setAccessible(true);
                final Object model = constructor.newInstance(handle, binding, applicationController,
                    (Supplier<Path>) () -> null, closeController, (Runnable) () -> { }, (Runnable) () -> { },
                    (Runnable) () -> { }, (Consumer<String>) value -> { });
                return new ModelAccess(model, type);
            }
            catch (final ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to construct the real graph workspace model", exception);
            }
        }

        Object invoke(final String name, final Object... arguments) {
            try {
                final Class<?>[] parameterTypes = new Class<?>[arguments.length];
                for (int index = 0; index < arguments.length; index++) {
                    parameterTypes[index] = arguments[index].getClass();
                }
                final Method method = findMethod(name, parameterTypes);
                method.setAccessible(true);
                return method.invoke(model, arguments);
            }
            catch (final InvocationTargetException exception) {
                final Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException("Graph workspace model operation failed: " + name, cause);
            }
            catch (final ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to invoke graph workspace model operation: " + name,
                    exception);
            }
        }

        void completeInitialLayout() {
            invoke("completeInitialLayout");
        }

        void close() {
            invoke("close");
        }

        private Method findMethod(final String name, final Class<?>[] parameterTypes) {
            try {
                return type.getDeclaredMethod(name, parameterTypes);
            }
            catch (final NoSuchMethodException exception) {
                if (parameterTypes.length == 1 && parameterTypes[0] == CanvasState.class) {
                    try {
                        return type.getDeclaredMethod(name, CanvasState.class);
                    }
                    catch (final NoSuchMethodException ignored) {
                        throw new IllegalStateException(exception);
                    }
                }
                throw new IllegalStateException(exception);
            }
        }
    }
}
