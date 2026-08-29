package org.freeplane.features.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.filter.condition.ASelectableCondition;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

public class QuickFindAllActionUnfoldMatchingBranchesModeTest {
    private Controller previousController;
    private Controller controller;
    private MapController mapController;
    private IMapSelection selection;
    private FilterConditionEditor filterEditor;
    private FilterController filterController;
    private ASelectableCondition condition;
    private NodeModel selected;
    private NodeModel searchRoot;
    private Filter activeFilter;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        controller = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        when(controller.getResourceController()).thenReturn(resourceController);
        textUtils = mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        Controller.setCurrentController(controller);
        ModeController modeController = mock(ModeController.class);
        mapController = mock(MapController.class);
        selection = mock(IMapSelection.class);
        filterEditor = mock(FilterConditionEditor.class);
        filterController = mock(FilterController.class);
        condition = mock(ASelectableCondition.class);
        selected = mock(NodeModel.class);
        searchRoot = mock(NodeModel.class);
        activeFilter = mock(Filter.class);

        when(controller.getSelection()).thenReturn(selection);
        when(controller.getModeController()).thenReturn(modeController);
        when(modeController.getMapController()).thenReturn(mapController);
        when(selection.getSelected()).thenReturn(selected);
        when(selection.getEffectiveSearchRoot()).thenReturn(searchRoot);
        when(selection.getFilter()).thenReturn(activeFilter);
        when(filterEditor.getCondition()).thenReturn(condition);
        when(condition.checkNode(any(NodeModel.class))).thenReturn(false);
        when(filterController.createQuickSelectionFilter(same(condition), same(selection))).thenReturn(null);
        when(selected.isDescendantOf(searchRoot)).thenReturn(false);
        when(filterController.findNextMatching(any(NodeModel.class), same(searchRoot),
                eq(MapController.Direction.FORWARD_VISIBLE), anyNodePredicate(), same(activeFilter)))
                .thenReturn(null);
    }

    @After
    public void tearDown() {
        textUtils.close();
        Controller.setCurrentController(previousController);
    }

    @Test
    public void requestsSharedUnfoldingBeforeVisibleSelectionTraversalWhenHelperFilterIsAvailable() {
        Filter quickSelectionFilter = mock(Filter.class);
        when(filterController.createQuickSelectionFilter(same(condition), same(selection)))
                .thenReturn(quickSelectionFilter);
        when(quickSelectionFilter.getFilterInfo(searchRoot)).thenReturn(new FilterInfo());
        QuickFindAllAction action = new QuickFindAllAction(filterController, filterEditor);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "select"));

        InOrder order = inOrder(filterController, quickSelectionFilter);
        order.verify(filterController).createQuickSelectionFilter(condition, selection);
        order.verify(quickSelectionFilter).calculateFilterResults(searchRoot);
        order.verify(filterController).unfoldMatchingBranchesForQuickSelection(quickSelectionFilter, selection);
        order.verify(filterController).findNextMatching(same(searchRoot), same(searchRoot),
                eq(MapController.Direction.FORWARD_VISIBLE), anyNodePredicate(), same(activeFilter));
    }

    @Test
    public void selectsEveryMatchReturnedByVisibleTraversal() {
        NodeModel firstMatch = mock(NodeModel.class);
        NodeModel secondMatch = mock(NodeModel.class);
        when(filterController.findNextMatching(any(NodeModel.class), same(searchRoot),
                eq(MapController.Direction.FORWARD_VISIBLE), anyNodePredicate(), same(activeFilter)))
                .thenReturn(firstMatch, secondMatch, null);
        QuickFindAllAction action = new QuickFindAllAction(filterController, filterEditor);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "select"));

        verify(selection).selectAsTheOnlyOneSelected(firstMatch);
        verify(selection).toggleSelected(secondMatch);
    }

    @Test
    public void usesRawQuickSelectionConditionForLazySelectionWhenNoHelperFilterIsAvailable() {
        NodeModel firstMatch = mock(NodeModel.class);
        AtomicInteger invocationCount = new AtomicInteger();
        when(condition.checkNode(searchRoot)).thenReturn(false);
        when(condition.checkNode(firstMatch)).thenReturn(true);
        when(filterController.findNextMatching(any(NodeModel.class), same(searchRoot),
                eq(MapController.Direction.FORWARD_VISIBLE), anyNodePredicate(), same(activeFilter)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Predicate<NodeModel> matches = invocation.getArgument(3);
                    return invocationCount.getAndIncrement() == 0 && matches.test(firstMatch)
                            ? firstMatch : null;
                });
        QuickFindAllAction action = new QuickFindAllAction(filterController, filterEditor);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "select"));

        verify(selection).selectAsTheOnlyOneSelected(firstMatch);
    }

    @Test
    public void usesCalculatedQuickSelectionFilterResultsForSelectionWhenHelperFilterIsAvailable() {
        Filter quickSelectionFilter = mock(Filter.class);
        NodeModel firstMatch = mock(NodeModel.class);
        FilterInfo matchedInfo = new FilterInfo();
        matchedInfo.add(FilterInfo.SHOW_AS_MATCHED);
        AtomicInteger invocationCount = new AtomicInteger();
        when(filterController.createQuickSelectionFilter(same(condition), same(selection)))
                .thenReturn(quickSelectionFilter);
        when(quickSelectionFilter.getFilterInfo(searchRoot)).thenReturn(new FilterInfo());
        when(quickSelectionFilter.getFilterInfo(firstMatch)).thenReturn(matchedInfo);
        when(filterController.findNextMatching(any(NodeModel.class), same(searchRoot),
                eq(MapController.Direction.FORWARD_VISIBLE), anyNodePredicate(), same(activeFilter)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Predicate<NodeModel> matches = invocation.getArgument(3);
                    return invocationCount.getAndIncrement() == 0 && matches.test(firstMatch)
                            ? firstMatch : null;
                });
        QuickFindAllAction action = new QuickFindAllAction(filterController, filterEditor);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "select"));

        verify(selection).selectAsTheOnlyOneSelected(firstMatch);
    }

    @SuppressWarnings("unchecked")
    private static Predicate<NodeModel> anyNodePredicate() {
        return any(Predicate.class);
    }
}
