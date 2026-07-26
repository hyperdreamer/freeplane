package org.freeplane.view.swing.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

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

public class NodeViewNumberingRefreshTest {
    private Controller previousController;
    private Controller controller;
    private ModeController modeController;
    private MapController mapController;
    private TextController textController;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        controller = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        IMapViewManager mapViewManager = mock(IMapViewManager.class);
        modeController = mock(ModeController.class);
        mapController = mock(MapController.class);
        IUserInputListenerFactory userInputListenerFactory = mock(IUserInputListenerFactory.class);
        IMouseListener mapMouseListener = mock(IMouseListener.class);
        textController = mock(TextController.class);

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
        when(resourceController.getLengthProperty(anyString())).thenReturn(0);

        Controller.setCurrentController(controller);
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void skipUnnumberedSiblingTextRefreshDuringNumberChanged() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel changedNode = environment.addChild(environment.rootNode, "changed");
        NodeModel unnumberedSiblingNode = environment.addChild(environment.rootNode, "unnumbered");
        NodeModel numberedDescendantNode = environment.addChild(unnumberedSiblingNode, "numbered-descendant");
        NodeModel numberedSiblingNode = environment.addChild(environment.rootNode, "numbered");
        environment.enableNumbering(numberedSiblingNode, numberedDescendantNode);

        TestNodeView parentView = environment.newNodeView(environment.rootNode);
        TestNodeView changedView = environment.newNodeView(changedNode);
        TestNodeView unnumberedSiblingView = environment.newNodeView(unnumberedSiblingNode);
        TestNodeView numberedDescendantView = environment.newNodeView(numberedDescendantNode);
        TestNodeView numberedSiblingView = environment.newNodeView(numberedSiblingNode);

        parentView.addChildView(changedView, 0);
        parentView.addChildView(unnumberedSiblingView, 1);
        parentView.addChildView(numberedSiblingView, 2);
        unnumberedSiblingView.addChildView(numberedDescendantView, 0);

        invokeNumberChanged(parentView, 1);

        assertThat(unnumberedSiblingView.mainView().updateCount).isZero();
        assertThat(numberedDescendantView.mainView().updateCount).isZero();
        assertThat(numberedSiblingView.mainView().updateCount).isEqualTo(1);
    }

    @Test
    public void refreshNumberedDescendantsOfNumberedSibling() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel changedNode = environment.addChild(environment.rootNode, "changed");
        NodeModel numberedSiblingNode = environment.addChild(environment.rootNode, "numbered");
        NodeModel numberedDescendantNode = environment.addChild(numberedSiblingNode, "numbered-descendant");
        environment.enableNumbering(numberedSiblingNode, numberedDescendantNode);

        TestNodeView parentView = environment.newNodeView(environment.rootNode);
        TestNodeView changedView = environment.newNodeView(changedNode);
        TestNodeView numberedSiblingView = environment.newNodeView(numberedSiblingNode);
        TestNodeView numberedDescendantView = environment.newNodeView(numberedDescendantNode);

        parentView.addChildView(changedView, 0);
        parentView.addChildView(numberedSiblingView, 1);
        numberedSiblingView.addChildView(numberedDescendantView, 0);

        invokeNumberChanged(parentView, 1);

        assertThat(numberedSiblingView.mainView().updateCount).isEqualTo(1);
        assertThat(numberedDescendantView.mainView().updateCount).isEqualTo(1);
    }

    private static void invokeNumberChanged(NodeView view, int firstChangedIndex) throws Exception {
        Method method = NodeView.class.getDeclaredMethod("numberChanged", int.class);
        method.setAccessible(true);
        method.invoke(view, firstChangedIndex);
    }

    private class TestEnvironment {
        private final Set<NodeModel> numberedNodes = new HashSet<>();
        private final MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        private final NodeModel rootNode = new NodeModel("root", mapModel);
        private final MapView mapView = new TestMapView(mapModel, modeController);

        private TestEnvironment() {
            mapModel.setRoot(rootNode);
            when(textController.getNodeNumbering(any(NodeModel.class))).thenAnswer(invocation ->
                numberedNodes.contains(invocation.getArgument(0)));
        }

        private NodeModel addChild(NodeModel parent, String text) {
            NodeModel child = new NodeModel(text, mapModel);
            parent.insert(child);
            return child;
        }

        private void enableNumbering(NodeModel... nodes) {
            for (NodeModel node : nodes) {
                numberedNodes.add(node);
            }
        }

        private TestNodeView newNodeView(NodeModel node) {
            return new TestNodeView(node, mapView);
        }
    }

    private static class TestMapView extends MapView {
        private TestMapView(MapModel viewedMap, ModeController modeController) {
            super(viewedMap, modeController);
        }

        @Override
        public void setMap(final MapModel viewedMap) {
        }
    }

    private static class TestNodeView extends NodeView {
        private final CountingMainView mainView;

        private TestNodeView(NodeModel viewedNode, MapView map) {
            super(viewedNode, map);
            this.mainView = new CountingMainView();
            setMainView(mainView);
        }

        private void addChildView(TestNodeView child, int index) {
            add(child, index);
        }

        private CountingMainView mainView() {
            return mainView;
        }

        @Override
        public boolean isContentVisible() {
            return true;
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

    private static class CountingMainView extends MainView {
        private int updateCount;

        @Override
        public void updateText(NodeModel nodeModel) {
            updateCount++;
        }
    }
}
