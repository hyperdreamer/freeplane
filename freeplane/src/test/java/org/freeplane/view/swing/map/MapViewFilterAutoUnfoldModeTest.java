package org.freeplane.view.swing.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

import org.freeplane.api.ChildNodesLayout;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IMouseListener;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.features.filter.Filter;
import org.freeplane.features.filter.FilterController;
import org.freeplane.features.filter.ToggleUnfoldMatchingBranchesAction;
import org.freeplane.features.layout.LayoutController;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.styles.MapViewLayout;
import org.freeplane.features.text.TextController;
import org.freeplane.features.ui.IMapViewManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MapViewFilterAutoUnfoldModeTest {
    private Controller previousController;
    private Controller controller;
    private ModeController modeController;
    private boolean unfoldMatchingBranchesSelected;

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
        LayoutController layoutController = mock(LayoutController.class);
        FilterController filterController = mock(FilterController.class);

        when(controller.getResourceController()).thenReturn(resourceController);
        when(controller.getMapViewManager()).thenReturn(mapViewManager);
        when(controller.getModeController()).thenReturn(modeController);
        when(controller.getExtension(FilterController.class)).thenReturn(filterController);
        when(mapViewManager.getMapViewComponent()).thenReturn(null);
        when(modeController.getMapController()).thenReturn(mapController);
        when(modeController.getUserInputListenerFactory()).thenReturn(userInputListenerFactory);
        when(modeController.getExtension(TextController.class)).thenReturn(textController);
        when(modeController.getExtension(LayoutController.class)).thenReturn(layoutController);
        when(modeController.canEdit(any(NodeModel.class))).thenReturn(false);
        when(mapController.isFolded(any(NodeModel.class))).thenAnswer(invocation -> ((NodeModel) invocation.getArgument(0)).isFolded());
        when(userInputListenerFactory.getMapMouseListener()).thenReturn(mapMouseListener);
        when(userInputListenerFactory.getMapMouseWheelListener()).thenReturn(new MouseAdapter() {
        });
        when(resourceController.getProperty(anyString())).thenReturn("");
        when(resourceController.getBooleanProperty(anyString())).thenReturn(false);
        when(resourceController.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(resourceController.getColorProperty(anyString())).thenReturn(Color.BLACK);
        when(resourceController.getLengthProperty(anyString())).thenReturn(10);
        when(textController.getNodeNumbering(any(NodeModel.class))).thenReturn(false);
        when(layoutController.getEffectiveChildNodesLayout(any(NodeModel.class))).thenReturn(ChildNodesLayout.AUTO);
        when(filterController.isUnfoldMatchingBranchesSelected()).thenAnswer(invocation -> unfoldMatchingBranchesSelected);

        Controller.setCurrentController(controller);
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void unfoldsMatchingBranchesAndPreservesUserOpenedBranches() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel autoParent = environment.addChild(environment.rootNode, "auto-parent", true);
        environment.addChild(autoParent, "match-leaf", false);
        NodeModel userParent = environment.addChild(environment.rootNode, "user-parent", false);
        environment.addChild(userParent, "plain-leaf", false);
        environment.attach();

        TestNodeView autoParentView = environment.viewFor(autoParent);
        TestNodeView userParentView = environment.viewFor(userParent);
        assertThat(autoParentView.getChildrenViews()).isEmpty();
        assertThat(userParentView.getChildrenViews()).hasSize(1);

        unfoldMatchingBranchesSelected = true;
        environment.setFilter(filterFor(environment.mapModel, "match", false, false, Filter.FilteredElement.NODE));
        environment.updateFilterNodeFolding();

        assertThat(autoParentView.isFolded()).isFalse();
        assertThat(autoParentView.getChildrenViews()).hasSize(1);
        assertThat(userParentView.isFolded()).isFalse();

        unfoldMatchingBranchesSelected = false;
        environment.mapView.mapChanged(new MapChangeEvent(this, environment.mapModel,
                ToggleUnfoldMatchingBranchesAction.class, null, null, false));

        assertThat(autoParentView.isFolded()).isTrue();
        assertThat(autoParentView.getChildrenViews()).isEmpty();
        assertThat(userParentView.isFolded()).isFalse();
    }

    @Test
    public void recomputesTemporaryFoldingForRestoredFilterWhenFilterChanges() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel firstParent = environment.addChild(environment.rootNode, "first-parent", true);
        environment.addChild(firstParent, "match-one", false);
        NodeModel secondParent = environment.addChild(environment.rootNode, "second-parent", true);
        environment.addChild(secondParent, "match-two", false);
        environment.attach();

        TestNodeView firstParentView = environment.viewFor(firstParent);
        TestNodeView secondParentView = environment.viewFor(secondParent);

        unfoldMatchingBranchesSelected = true;
        environment.setFilter(filterFor(environment.mapModel, "match-one", false, false, Filter.FilteredElement.NODE));
        environment.updateFilterNodeFolding();
        assertThat(firstParentView.isFolded()).isFalse();
        assertThat(secondParentView.isFolded()).isTrue();

        environment.setFilter(filterFor(environment.mapModel, "match-two", false, false, Filter.FilteredElement.NODE));
        environment.updateFilterNodeFolding();

        assertThat(firstParentView.isFolded()).isTrue();
        assertThat(secondParentView.isFolded()).isFalse();
    }

    @Test
    public void descendantOnlyVisibleNodesDoNotDriveAdditionalUnfolding() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel directMatchParent = environment.addChild(environment.rootNode, "match-parent", false);
        NodeModel foldedDescendantBranch = environment.addChild(directMatchParent, "descendant-branch", true);
        environment.addChild(foldedDescendantBranch, "shown-descendant", false);
        environment.attach();

        TestNodeView foldedDescendantBranchView = environment.viewFor(foldedDescendantBranch);
        assertThat(foldedDescendantBranchView.getChildrenViews()).isEmpty();

        unfoldMatchingBranchesSelected = true;
        environment.setFilter(filterFor(environment.mapModel, "match-parent", false, true, Filter.FilteredElement.NODE));
        environment.updateFilterNodeFolding();

        assertThat(foldedDescendantBranchView.isFolded()).isTrue();
        assertThat(foldedDescendantBranchView.getChildrenViews()).isEmpty();
    }

    @Test
    public void clearsTrackedBranchesWhenConnectorFilteringIsSelected() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel autoParent = environment.addChild(environment.rootNode, "auto-parent", true);
        environment.addChild(autoParent, "match-leaf", false);
        environment.attach();

        TestNodeView autoParentView = environment.viewFor(autoParent);

        unfoldMatchingBranchesSelected = true;
        environment.setFilter(filterFor(environment.mapModel, "match", false, false, Filter.FilteredElement.NODE));
        environment.updateFilterNodeFolding();
        assertThat(autoParentView.isFolded()).isFalse();

        environment.setFilter(filterFor(environment.mapModel, "match", false, false, Filter.FilteredElement.CONNECTOR));
        environment.updateFilterNodeFolding();

        assertThat(autoParentView.isFolded()).isTrue();
        assertThat(autoParentView.getChildrenViews()).isEmpty();
    }

    @Test
    public void recomputesAgainstTheDisplayedRootSubtree() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        NodeModel branchA = environment.addChild(environment.rootNode, "branch-a", false);
        NodeModel autoParentA = environment.addChild(branchA, "auto-parent-a", true);
        environment.addChild(autoParentA, "match-a", false);
        NodeModel branchB = environment.addChild(environment.rootNode, "branch-b", false);
        NodeModel autoParentB = environment.addChild(branchB, "auto-parent-b", true);
        environment.addChild(autoParentB, "match-b", false);
        environment.attach();

        TestNodeView branchAView = environment.viewFor(branchA);
        TestNodeView autoParentAView = environment.viewFor(autoParentA);
        TestNodeView autoParentBView = environment.viewFor(autoParentB);

        unfoldMatchingBranchesSelected = true;
        environment.setFilter(filterFor(environment.mapModel, "match", false, false, Filter.FilteredElement.NODE));
        environment.updateFilterNodeFolding();
        assertThat(autoParentAView.isFolded()).isFalse();
        assertThat(autoParentBView.isFolded()).isFalse();

        environment.setCurrentRootView(branchAView);
        environment.updateFilterNodeFolding();

        assertThat(autoParentAView.isFolded()).isFalse();
        assertThat(autoParentBView.isFolded()).isTrue();
    }

    private static Filter filterFor(MapModel mapModel, String directMatchText, boolean hidesMatchingNodes,
            boolean showDescendants, Filter.FilteredElement filteredElement) {
        Filter filter = new Filter(node -> node.toString().contains(directMatchText), hidesMatchingNodes, false,
                showDescendants, false, filteredElement, null);
        filter.calculateFilterResults(mapModel);
        return filter;
    }

    private static class TestEnvironment {
        private final MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        private final NodeModel rootNode = new NodeModel("root", mapModel);
        private final TestMapView mapView = new TestMapView(mapModel, Controller.getCurrentController().getModeController());
        private final Map<NodeModel, TestNodeView> nodeViews = new IdentityHashMap<>();

        private TestEnvironment() throws Exception {
            mapModel.setRoot(rootNode);
            setField(mapView, "siblingMaxLevel", -1);
            setField(mapView, "layoutType", MapViewLayout.MAP);
        }

        private NodeModel addChild(NodeModel parent, String text, boolean folded) {
            NodeModel child = new NodeModel(text, mapModel);
            child.setFolded(folded);
            parent.insert(child);
            return child;
        }

        private TestNodeView viewFor(NodeModel node) {
            return nodeViews.computeIfAbsent(node, key -> new TestNodeView(key, mapView, this));
        }

        private void attach() throws Exception {
            TestNodeView rootView = viewFor(rootNode);
            rootNode.addViewer(rootView);
            setField(mapView, "mapRootView", rootView);
            setField(mapView, "currentRootView", rootView);
            setField(mapView, "currentRootParentView", null);
            mapView.add(rootView);
            rootView.syncDisplayedChildrenRecursively();
        }

        private void setFilter(Filter filter) throws Exception {
            setField(mapView, "filter", filter);
        }

        private void setCurrentRootView(NodeView currentRootView) throws Exception {
            setField(mapView, "currentRootView", currentRootView);
            setField(mapView, "currentRootParentView", currentRootView.getParent());
        }

        private void updateFilterNodeFolding() throws Exception {
            Method method = MapView.class.getDeclaredMethod("updateFilterNodeFolding");
            method.setAccessible(true);
            method.invoke(mapView);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = MapView.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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
        private final TestEnvironment environment;

        private TestNodeView(NodeModel viewedNode, MapView map, TestEnvironment environment) {
            super(viewedNode, map);
            this.environment = environment;
            setMainView(new MainView());
        }

        private void syncDisplayedChildrenRecursively() {
            for (NodeView childView : getChildrenViews()) {
                childView.getNode().removeViewer(childView);
                remove(childView);
            }
            if (isFolded()) {
                return;
            }
            for (NodeModel childNode : getNode().getChildren()) {
                TestNodeView childView = environment.viewFor(childNode);
                childNode.removeViewer(childView);
                add(childView);
                childNode.addViewer(childView);
                childView.syncDisplayedChildrenRecursively();
            }
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
            syncDisplayedChildrenRecursively();
        }

        @Override
        void resetLayoutPropertiesRecursively() {
        }
    }
}
