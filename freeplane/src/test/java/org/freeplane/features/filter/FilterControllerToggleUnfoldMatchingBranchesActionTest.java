package org.freeplane.features.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IMouseListener;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.features.filter.condition.ASelectableCondition;
import org.freeplane.features.filter.condition.NoFilteringCondition;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.core.util.TextUtils;
import org.freeplane.view.swing.map.MapView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class FilterControllerToggleUnfoldMatchingBranchesActionTest {
    private Controller previousController;
    private Controller controller;
    private MockedStatic<TextUtils> textUtils;
    private IMapViewManager mapViewManager;
    private final Map<String, AFreeplaneAction> actions = new HashMap<>();

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        controller = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        mapViewManager = mock(IMapViewManager.class);
        ModeController modeController = mock(ModeController.class);
        MapController mapController = mock(MapController.class);
        IUserInputListenerFactory userInputListenerFactory = mock(IUserInputListenerFactory.class);
        IMouseListener mapMouseListener = mock(IMouseListener.class);
        TextController textController = mock(TextController.class);

        when(controller.getResourceController()).thenReturn(resourceController);
        when(controller.getMapViewManager()).thenReturn(mapViewManager);
        when(controller.getModeController()).thenReturn(modeController);
        doAnswer(invocation -> {
            AFreeplaneAction action = invocation.getArgument(0);
            actions.put(action.getKey(), action);
            return null;
        }).when(controller).addAction(any(AFreeplaneAction.class));
        when(controller.getAction(anyString())).thenAnswer(invocation -> actions.get(invocation.getArgument(0)));
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
        when(resourceController.getEnumProperty(anyString(), anyFilteredElement()))
                .thenAnswer(invocation -> invocation.getArgument(1, Filter.FilteredElement.class));
        when(resourceController.getFreeplaneUserDirectory()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(textController.getNodeNumbering(any(NodeModel.class))).thenReturn(false);

        textUtils = mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getRawText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(anyString(), nullable(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(1) != null ? invocation.getArgument(1) : invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(anyString(), nullable(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(1) != null ? invocation.getArgument(1) : invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getOptionalText(anyString(), nullable(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        textUtils.when(() -> TextUtils.capitalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        Controller.setCurrentController(controller);
    }

    @After
    public void tearDown() {
        textUtils.close();
        Controller.setCurrentController(previousController);
    }

    @Test
    public void quickSelectionFilterIsCreatedOnlyWhenModeIsActive() {
        FilterController filterController = new FilterController();
        IMapSelection selection = mock(IMapSelection.class);
        Filter activeFilter = new Filter(null, false, false, false, false,
                Filter.FilteredElement.NODE, null);
        ASelectableCondition condition = NoFilteringCondition.createCondition();
        when(selection.getFilter()).thenReturn(activeFilter);

        assertThat(filterController.createQuickSelectionFilter(condition, selection)).isNull();

        filterController.getUnfoldMatchingBranches().setSelected(true);
        assertThat(filterController.createQuickSelectionFilter(condition, selection)).isNotNull();

        when(selection.getFilter()).thenReturn(new Filter(null, false, false, false, false,
                Filter.FilteredElement.CONNECTOR, null));
        assertThat(filterController.createQuickSelectionFilter(condition, selection)).isNull();
    }

    @Test
    public void quickSelectionUnfoldingDelegatesToMapView() {
        FilterController filterController = new FilterController();
        IMapSelection selection = mock(IMapSelection.class);
        NodeModel searchRoot = mock(NodeModel.class);
        Filter quickSelectionFilter = new Filter(null, false, false, false, false,
                Filter.FilteredElement.NODE, null);
        MapView mapView = mock(MapView.class);
        when(mapViewManager.getMapViewComponent()).thenReturn(mapView);
        when(selection.getEffectiveSearchRoot()).thenReturn(searchRoot);

        filterController.unfoldMatchingBranchesForQuickSelection(quickSelectionFilter, selection);

        verify(mapView).unfoldMatchingBranches(same(quickSelectionFilter), same(searchRoot));
        verifyNoMoreInteractions(mapView);
    }

    @Test
    public void restoredFilterAvailabilityDoesNotOverwriteModeSelection() throws Exception {
        FilterController filterController = new FilterController();
        filterController.getFilterConditionsModel();
        filterController.getUnfoldMatchingBranches().setSelected(true);
        AFreeplaneAction action = actions.get("ToggleUnfoldMatchingBranchesAction");
        assertThat(action).isNotNull();

        invokeUpdateSettingsFromFilter(filterController,
                new Filter(node -> true, false, false, false, false, Filter.FilteredElement.CONNECTOR, null));
        assertThat(filterController.getUnfoldMatchingBranches().isSelected()).isTrue();
        assertThat(action.isEnabled()).isFalse();

        invokeUpdateSettingsFromFilter(filterController,
                new Filter(node -> true, false, false, false, false, Filter.FilteredElement.NODE, null));
        assertThat(filterController.getUnfoldMatchingBranches().isSelected()).isTrue();
        assertThat(action.isEnabled()).isTrue();
    }

    private static Filter.FilteredElement anyFilteredElement() {
        return any(Filter.FilteredElement.class);
    }

    private static void invokeUpdateSettingsFromFilter(FilterController filterController, Filter filter) throws Exception {
        Method method = FilterController.class.getDeclaredMethod("updateSettingsFromFilter", Filter.class);
        method.setAccessible(true);
        method.invoke(filterController, filter);
    }
}
