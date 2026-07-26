package org.freeplane.main.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.core.util.ConfigurationUtils;
import org.freeplane.features.filter.Filter;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.NodeView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class LastOpenedListTest {
    private Controller previousController;
    private Controller controller;
    private ResourceController resourceController;
    private ModeController modeController;
    private IUserInputListenerFactory userInputListenerFactory;
    private IMapViewManager mapViewManager;
    private Map<String, String> properties;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        controller = mock(Controller.class);
        resourceController = mock(ResourceController.class);
        modeController = mock(ModeController.class);
        userInputListenerFactory = mock(IUserInputListenerFactory.class);
        mapViewManager = mock(IMapViewManager.class);
        properties = new HashMap<>();

        when(controller.getResourceController()).thenReturn(resourceController);
        when(controller.getModeController()).thenReturn(modeController);
        when(controller.getMapViewManager()).thenReturn(mapViewManager);
        when(modeController.getUserInputListenerFactory()).thenReturn(userInputListenerFactory);
        when(modeController.getModeName()).thenReturn(MModeController.MODENAME);
        when(resourceController.getProperty(anyString())).thenAnswer(invocation -> properties.getOrDefault(invocation.getArgument(0), ""));
        when(resourceController.getProperty(anyString(), anyString())).thenAnswer(invocation ->
            properties.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
        when(resourceController.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(resourceController.getBooleanProperty(anyString())).thenReturn(false);
        when(resourceController.getColorProperty(anyString())).thenReturn(Color.BLACK);
        when(resourceController.getLengthProperty(anyString())).thenReturn(10);
        when(mapViewManager.getMapViewComponent()).thenReturn(null);

        org.mockito.Mockito.doAnswer(invocation -> {
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(resourceController).setProperty(anyString(), anyString());

        Controller.setCurrentController(controller);
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void savePropertiesStoresNestedRootHistoryListPerRecentFile() throws Exception {
        LastOpenedList subject = new LastOpenedList();
        List<LastOpenedList.RecentFile> recentFiles = recentFilesOf(subject);

        LastOpenedList.RecentFile first = new LastOpenedList.RecentFile("MindMap:/tmp/first.mm");
        first.lastVisitedNodeId = "ID_SELECTED_1";
        first.lastRootNodeId = "ID_ROOT_1";
        first.lastRootHistoryNodeIds = Arrays.asList("ID_A", "ID_B");
        recentFiles.add(first);

        LastOpenedList.RecentFile second = new LastOpenedList.RecentFile("MindMap:/tmp/second.mm");
        second.lastVisitedNodeId = "ID_SELECTED_2";
        second.lastRootNodeId = "ID_ROOT_2";
        second.lastRootHistoryNodeIds = Collections.emptyList();
        recentFiles.add(second);

        subject.saveProperties();

        String expected = ConfigurationUtils.encodeListValue(Arrays.asList(
            ConfigurationUtils.encodeListValue(first.lastRootHistoryNodeIds, false),
            ConfigurationUtils.encodeListValue(second.lastRootHistoryNodeIds, false)), true);
        assertEquals(expected, properties.get("lastRootsHistory"));

        LastOpenedList restored = new LastOpenedList();
        List<LastOpenedList.RecentFile> restoredRecentFiles = recentFilesOf(restored);
        assertEquals(Arrays.asList("ID_A", "ID_B"), restoredRecentFiles.get(0).lastRootHistoryNodeIds);
        assertTrue(restoredRecentFiles.get(1).lastRootHistoryNodeIds.isEmpty());
    }

    @Test
    public void selectLastVisitedNodeRestoresRootHistoryWithoutUnfoldingAncestors() throws Exception {
        LastOpenedList subject = new LastOpenedList();
        IMapSelection selection = mock(IMapSelection.class);
        MapModel map = mock(MapModel.class);
        NodeModel mapRoot = mock(NodeModel.class);
        NodeModel restoredRoot = mock(NodeModel.class);
        NodeModel visitedNode = mock(NodeModel.class);
        Filter filter = mock(Filter.class);
        MapView mapView = mock(MapView.class);

        LastOpenedList.RecentFile recentFile = new LastOpenedList.RecentFile("MindMap:/tmp/test.mm");
        recentFile.lastRootNodeId = "ID_ROOT";
        recentFile.lastVisitedNodeId = "ID_SELECTED";
        recentFile.lastRootHistoryNodeIds = Arrays.asList("ID_PARENT", "ID_CHILD");

        when(controller.getSelection()).thenReturn(selection);
        when(selection.getMap()).thenReturn(map);
        when(selection.getFilter()).thenReturn(filter);
        when(selection.isSelected(mapRoot)).thenReturn(true);
        when(map.getRootNode()).thenReturn(mapRoot);
        when(map.getNodeForID("ID_ROOT")).thenReturn(restoredRoot);
        when(map.getNodeForID("ID_SELECTED")).thenReturn(visitedNode);
        when(visitedNode.hasVisibleContent(filter)).thenReturn(true);
        when(mapViewManager.getMapViewComponent()).thenReturn(mapView);

        boolean restored = (boolean) invoke(subject, "selectLastVisitedNode", new Class<?>[] { LastOpenedList.RecentFile.class }, recentFile);

        assertTrue(restored);
        InOrder inOrder = inOrder(mapView, mapViewManager, selection);
        inOrder.verify(mapView).setRootsHistoryNodeIds(recentFile.lastRootHistoryNodeIds);
        inOrder.verify(mapViewManager).setViewRoot(restoredRoot);
        inOrder.verify(selection).selectAsTheOnlyOneSelected(visitedNode);
        verify(mapView, never()).getRootsHistoryNodeIds();
    }

    @Test
    public void afterViewChangeAndSavePropertiesPersistNavigationStateFromLastActiveSameMapView() throws Exception {
        LastOpenedList subject = new LastOpenedList();
        List<LastOpenedList.RecentFile> recentFiles = recentFilesOf(subject);

        File mapAFile = File.createTempFile("last-opened-a", ".mm");
        File mapBFile = File.createTempFile("last-opened-b", ".mm");
        mapAFile.deleteOnExit();
        mapBFile.deleteOnExit();
        String mapARestorable = "MindMap:" + mapAFile.getAbsolutePath();
        String mapBRestorable = "MindMap:" + mapBFile.getAbsolutePath();

        LastOpenedList.RecentFile recentFileA = new LastOpenedList.RecentFile(mapARestorable);
        LastOpenedList.RecentFile recentFileB = new LastOpenedList.RecentFile(mapBRestorable);
        recentFiles.add(recentFileA);
        recentFiles.add(recentFileB);

        MapModel mapA = mock(MapModel.class);
        MapModel mapB = mock(MapModel.class);
        when(mapA.containsExtension(org.freeplane.features.map.DocuMapAttribute.class)).thenReturn(false);
        when(mapB.containsExtension(org.freeplane.features.map.DocuMapAttribute.class)).thenReturn(false);
        when(mapA.getFile()).thenReturn(mapAFile);
        when(mapB.getFile()).thenReturn(mapBFile);

        MapView mapA1 = mapView("ID_SELECTED_A1", "ID_ROOT_A1", Arrays.asList("ID_H1"));
        MapView mapA2 = mapView("ID_SELECTED_A2", "ID_ROOT_A2", Arrays.asList("ID_H2"));
        MapView mapBView = mapView("ID_SELECTED_B", "ID_ROOT_B", Collections.emptyList());

        when(mapViewManager.getMap((Component) mapA1)).thenReturn(mapA);
        when(mapViewManager.getMap((Component) mapA2)).thenReturn(mapA);
        when(mapViewManager.getMap((Component) mapBView)).thenReturn(mapB);
        when(mapViewManager.getMapViewComponent()).thenReturn(mapBView);

        subject.afterViewChange(mapA2, mapBView);
        subject.saveProperties();

        assertEquals("ID_SELECTED_A2", recentFileA.lastVisitedNodeId);
        assertEquals("ID_ROOT_A2", recentFileA.lastRootNodeId);
        assertEquals(Arrays.asList("ID_H2"), recentFileA.lastRootHistoryNodeIds);
    }

    @Test
    public void afterViewCloseDoesNotOverwriteNavigationStateFromLastActiveSameMapView() throws Exception {
        LastOpenedList subject = new LastOpenedList();
        List<LastOpenedList.RecentFile> recentFiles = recentFilesOf(subject);

        File mapAFile = File.createTempFile("last-opened-a", ".mm");
        File mapBFile = File.createTempFile("last-opened-b", ".mm");
        mapAFile.deleteOnExit();
        mapBFile.deleteOnExit();
        String mapARestorable = "MindMap:" + mapAFile.getAbsolutePath();
        String mapBRestorable = "MindMap:" + mapBFile.getAbsolutePath();

        LastOpenedList.RecentFile recentFileA = new LastOpenedList.RecentFile(mapARestorable);
        LastOpenedList.RecentFile recentFileB = new LastOpenedList.RecentFile(mapBRestorable);
        recentFiles.add(recentFileA);
        recentFiles.add(recentFileB);

        MapModel mapA = mock(MapModel.class);
        MapModel mapB = mock(MapModel.class);
        when(mapA.containsExtension(org.freeplane.features.map.DocuMapAttribute.class)).thenReturn(false);
        when(mapB.containsExtension(org.freeplane.features.map.DocuMapAttribute.class)).thenReturn(false);
        when(mapA.getFile()).thenReturn(mapAFile);
        when(mapB.getFile()).thenReturn(mapBFile);

        MapView mapA1 = mapView("ID_SELECTED_A1", "ID_ROOT_A1", Arrays.asList("ID_H1"));
        MapView mapA2 = mapView("ID_SELECTED_A2", "ID_ROOT_A2", Arrays.asList("ID_H2"));
        MapView mapBView = mapView("ID_SELECTED_B", "ID_ROOT_B", Collections.emptyList());

        when(mapViewManager.getMap((Component) mapA1)).thenReturn(mapA);
        when(mapViewManager.getMap((Component) mapA2)).thenReturn(mapA);
        when(mapViewManager.getMap((Component) mapBView)).thenReturn(mapB);
        when(mapViewManager.getMapViewComponent()).thenReturn(mapBView);

        subject.afterViewChange(mapA2, mapBView);
        subject.afterViewClose(mapA1);
        subject.saveProperties();

        assertEquals("ID_SELECTED_A2", recentFileA.lastVisitedNodeId);
        assertEquals("ID_ROOT_A2", recentFileA.lastRootNodeId);
        assertEquals(Arrays.asList("ID_H2"), recentFileA.lastRootHistoryNodeIds);
    }

    @SuppressWarnings("unchecked")
    private static List<LastOpenedList.RecentFile> recentFilesOf(LastOpenedList subject) throws Exception {
        Field field = LastOpenedList.class.getDeclaredField("lastOpenedList");
        field.setAccessible(true);
        return (List<LastOpenedList.RecentFile>) field.get(subject);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = LastOpenedList.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private MapView mapView(String selectedId, String rootId, List<String> rootHistory) {
        MapView mapView = mock(MapView.class);
        ModeController viewModeController = mock(ModeController.class);
        NodeView selectedView = mock(NodeView.class);
        NodeView rootView = mock(NodeView.class);
        NodeModel selectedNode = mock(NodeModel.class);
        NodeModel rootNode = mock(NodeModel.class);

        when(mapView.getModeController()).thenReturn(viewModeController);
        when(viewModeController.getModeName()).thenReturn(MModeController.MODENAME);
        when(mapView.getSelected()).thenReturn(selectedView);
        when(mapView.getRoot()).thenReturn(rootView);
        when(mapView.getRootsHistoryNodeIds()).thenReturn(rootHistory);
        when(selectedView.getNode()).thenReturn(selectedNode);
        when(rootView.getNode()).thenReturn(rootNode);
        when(selectedNode.getID()).thenReturn(selectedId);
        when(rootNode.getID()).thenReturn(rootId);
        return mapView;
    }
}
