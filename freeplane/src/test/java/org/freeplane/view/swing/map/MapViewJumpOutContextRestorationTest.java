package org.freeplane.view.swing.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class MapViewJumpOutContextRestorationTest {
    private Controller previousController;
    private Controller controller;
    private ModeController modeController;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        controller = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        IMapViewManager mapViewManager = mock(IMapViewManager.class);
        modeController = mock(ModeController.class);
        MapController mapController = mock(MapController.class);
        IUserInputListenerFactory userInputListenerFactory = mock(IUserInputListenerFactory.class);
        IMouseListener mapMouseListener = mock(IMouseListener.class);
        TextController textController = mock(TextController.class);

        when(controller.getResourceController()).thenReturn(resourceController);
        when(controller.getMapViewManager()).thenReturn(mapViewManager);
        when(controller.getModeController()).thenReturn(modeController);
        when(mapViewManager.getMapViewComponent()).thenReturn(null);
        when(modeController.getMapController()).thenReturn(mapController);
        when(modeController.getUserInputListenerFactory()).thenReturn(userInputListenerFactory);
        when(modeController.getExtension(TextController.class)).thenReturn(textController);
        when(modeController.canEdit(any(NodeModel.class))).thenReturn(false);
        when(mapController.isFolded(any(NodeModel.class))).thenReturn(false);
        when(userInputListenerFactory.getMapMouseListener()).thenReturn(mapMouseListener);
        when(userInputListenerFactory.getMapMouseWheelListener()).thenReturn(new MouseAdapter() {
        });
        when(resourceController.getProperty(anyString())).thenReturn("");
        when(resourceController.getBooleanProperty(anyString())).thenReturn(false);
        when(resourceController.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(resourceController.getColorProperty(anyString())).thenReturn(Color.BLACK);
        when(resourceController.getLengthProperty(anyString())).thenReturn(10);
        when(textController.getNodeNumbering(any(NodeModel.class))).thenReturn(false);

        Controller.setCurrentController(controller);
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void usePreviousViewRootRevealsOldRootPathOnMapRootFallback() throws Exception {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel mapRootNode = new NodeModel("root", map);
        map.setRoot(mapRootNode);
        NodeModel oldRootNode = new NodeModel("old-root", map);
        mapRootNode.insert(oldRootNode);

        JumpOutTestMapView mapView = new JumpOutTestMapView(map, modeController);
        setField(mapView, "siblingMaxLevel", -1);

        TestNodeView mapRootView = new TestNodeView(mapRootNode, mapView, true);
        TestNodeView detachedOldRootView = new TestNodeView(oldRootNode, mapView, true);
        TestNodeView restoredOldRootView = new TestNodeView(oldRootNode, mapView, true);

        mapRootNode.addViewer(mapRootView);
        oldRootNode.addViewer(detachedOldRootView);

        setField(mapView, "mapRootView", mapRootView);
        setField(mapView, "currentRootView", detachedOldRootView);
        setField(mapView, "currentRootParentView", null);
        mapView.add(detachedOldRootView);
        mapView.selectAsTheOnlyOneSelected(detachedOldRootView, false);
        mapView.registerReveal(oldRootNode, restoredOldRootView);

        mapView.usePreviousViewRoot();

        assertSame(mapRootView, mapView.getRoot());
        assertSame(restoredOldRootView, mapView.getSelected());
        assertSame(mapRootView, restoredOldRootView.getParent());
        assertEquals(Collections.singletonList(oldRootNode), mapView.revealedNodes());
    }

    @Test
    public void usePreviousViewRootRestoresPreviousRootFromModelHistory() throws Exception {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel mapRootNode = new NodeModel("root", map);
        map.setRoot(mapRootNode);
        NodeModel previousRootNode = new NodeModel("previous-root", map);
        mapRootNode.insert(previousRootNode);
        NodeModel oldRootNode = new NodeModel("old-root", map);
        previousRootNode.insert(oldRootNode);

        JumpOutTestMapView mapView = new JumpOutTestMapView(map, modeController);
        setField(mapView, "siblingMaxLevel", -1);

        TestNodeView mapRootView = new TestNodeView(mapRootNode, mapView, true);
        TestNodeView detachedOldRootView = new TestNodeView(oldRootNode, mapView, true);
        TestNodeView restoredPreviousRootView = new TestNodeView(previousRootNode, mapView, true);
        TestNodeView restoredOldRootView = new TestNodeView(oldRootNode, mapView, true);

        mapRootNode.addViewer(mapRootView);
        oldRootNode.addViewer(detachedOldRootView);

        setField(mapView, "mapRootView", mapRootView);
        setField(mapView, "currentRootView", detachedOldRootView);
        setField(mapView, "currentRootParentView", null);
        setField(mapView, "rootsHistory", new ArrayList<>(Collections.singletonList(previousRootNode)));
        mapView.add(detachedOldRootView);
        mapView.selectAsTheOnlyOneSelected(detachedOldRootView, false);
        mapView.registerCreatedRootView(previousRootNode, restoredPreviousRootView);
        mapView.registerReveal(oldRootNode, restoredOldRootView);

        mapView.usePreviousViewRoot();

        assertSame(previousRootNode, mapView.getRoot().getNode());
        assertSame(restoredOldRootView, mapView.getSelected());
        assertSame(mapView.getRoot(), restoredOldRootView.getParent());
        assertEquals(Collections.singletonList(oldRootNode), mapView.revealedNodes());
        assertTrue(rootsHistoryOf(mapView).isEmpty());
    }

    @Test
    public void usePreviousViewRootSelectsOldRootWhenDeeperSelectionRemainsHidden() throws Exception {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel mapRootNode = new NodeModel("root", map);
        map.setRoot(mapRootNode);
        NodeModel oldRootNode = new NodeModel("old-root", map);
        mapRootNode.insert(oldRootNode);
        NodeModel hiddenSelectedNode = new NodeModel("hidden-selected", map);
        oldRootNode.insert(hiddenSelectedNode);

        JumpOutTestMapView mapView = new JumpOutTestMapView(map, modeController);
        setField(mapView, "siblingMaxLevel", -1);

        TestNodeView mapRootView = new TestNodeView(mapRootNode, mapView, true);
        TestNodeView detachedOldRootView = new TestNodeView(oldRootNode, mapView, true);
        TestNodeView detachedHiddenSelectedView = new TestNodeView(hiddenSelectedNode, mapView, true);
        TestNodeView restoredOldRootView = new TestNodeView(oldRootNode, mapView, true);

        detachedOldRootView.add(detachedHiddenSelectedView);
        mapRootNode.addViewer(mapRootView);
        oldRootNode.addViewer(detachedOldRootView);
        hiddenSelectedNode.addViewer(detachedHiddenSelectedView);

        setField(mapView, "mapRootView", mapRootView);
        setField(mapView, "currentRootView", detachedOldRootView);
        setField(mapView, "currentRootParentView", null);
        mapView.add(detachedOldRootView);
        mapView.selectAsTheOnlyOneSelected(detachedHiddenSelectedView, false);
        mapView.registerReveal(oldRootNode, restoredOldRootView);

        mapView.usePreviousViewRoot();

        assertSame(restoredOldRootView, mapView.getSelected());
        assertEquals(Collections.singletonList(oldRootNode), mapView.revealedNodes());
    }

    @SuppressWarnings("unchecked")
    private static List<NodeModel> rootsHistoryOf(MapView mapView) throws Exception {
        return (List<NodeModel>) getField(mapView, "rootsHistory");
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = MapView.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = MapView.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class JumpOutTestMapView extends MapView {
        private final Map<NodeModel, TestNodeView> createdRootViews = new HashMap<>();
        private final Map<NodeModel, TestNodeView> revealedViews = new HashMap<>();
        private final List<NodeModel> revealedNodes = new ArrayList<>();

        private JumpOutTestMapView(MapModel viewedMap, ModeController modeController) {
            super(viewedMap, modeController);
        }

        @Override
        public void setMap(final MapModel viewedMap) {
        }

        @Override
        NodeView newRootView(NodeModel node) {
            TestNodeView createdRootView = createdRootViews.get(node);
            return createdRootView != null ? createdRootView : super.newRootView(node);
        }

        @Override
        public void display(final NodeModel node) {
            revealedNodes.add(node);
            TestNodeView revealedView = revealedViews.get(node);
            if (revealedView != null && revealedView.getParent() == null) {
                getRoot().add(revealedView);
                if (!node.getViewers().contains(revealedView)) {
                    node.addViewer(revealedView);
                }
            }
        }

        private void registerCreatedRootView(NodeModel node, TestNodeView createdRootView) {
            createdRootViews.put(node, createdRootView);
        }

        private void registerReveal(NodeModel node, TestNodeView revealedView) {
            revealedViews.put(node, revealedView);
        }

        private List<NodeModel> revealedNodes() {
            return revealedNodes;
        }
    }

    private static class TestNodeView extends NodeView {
        private final boolean contentVisible;

        private TestNodeView(NodeModel viewedNode, MapView map, boolean contentVisible) {
            super(viewedNode, map);
            this.contentVisible = contentVisible;
            setMainView(new MainView());
        }

        @Override
        public boolean isContentVisible() {
            return contentVisible;
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
