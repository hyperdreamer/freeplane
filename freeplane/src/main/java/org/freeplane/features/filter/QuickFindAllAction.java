/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2009 Dimitry Polivaev
 *
 *  This file author is Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.features.filter;

import java.awt.event.ActionEvent;
import java.util.function.Predicate;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.features.filter.condition.ASelectableCondition;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapController.Direction;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * @author Dimitry Polivaev
 * Mar 30, 2009
 */
class QuickFindAllAction extends AFreeplaneAction {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	/**
	 *
	 */
	private final FilterConditionEditor filterEditor;
	private final FilterController filterController;


	/**
	 * @param filterController
	 * @param quickEditor
	 */
	QuickFindAllAction(final FilterController filterController, FilterConditionEditor quickEditor) {
		super("QuickFindAllAction");
		this.filterController = filterController;
		this.filterEditor = quickEditor;
	}

	public void actionPerformed(final ActionEvent e) {
		final ASelectableCondition condition = filterEditor.getCondition();
		if(condition == null){
			return;
		}
		final IMapSelection selection = Controller.getCurrentController().getSelection();
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		final NodeModel selected = selection.getSelected();
        final NodeModel searchRoot = selection.getEffectiveSearchRoot();
        NodeModel searchStart;
        if (selected == searchRoot || selected.isDescendantOf(searchRoot))
            searchStart = selected;
        else
            searchStart = searchRoot;
        final Filter quickSelectionFilter = filterController
                .createQuickSelectionFilter(condition, selection);
        final Predicate<NodeModel> matches;
        if (quickSelectionFilter == null) {
            matches = condition::checkNode;
        }
        else {
            quickSelectionFilter.calculateFilterResults(searchRoot);
            filterController.unfoldMatchingBranchesForQuickSelection(quickSelectionFilter, selection);
            matches = node -> quickSelectionFilter.getFilterInfo(node).isMatched();
        }
		boolean nodeFound = matches.test(searchStart);
		if(nodeFound){
			selection.selectAsTheOnlyOneSelected(searchStart);
		}
		NodeModel next = searchRoot;
		for(;;){
			next = filterController.findNextMatching(next, searchRoot, Direction.FORWARD_VISIBLE, matches, selection.getFilter());
			if(next == null){
				break;
			}
			mapController.displayNode(next);
			if(nodeFound){
				selection.toggleSelected(next);
			}
			else{
				selection.selectAsTheOnlyOneSelected(next);
				nodeFound = true;
			}
		}
		if(matches.test(searchStart))
		    selection.makeTheSelected(searchStart);
	}
}
