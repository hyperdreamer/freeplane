/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2026 Dimitry Polivaev
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

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.SelectableAction;

@SelectableAction
public class ToggleUnfoldMatchingBranchesAction extends AFreeplaneAction {
    private static final long serialVersionUID = 1L;
    private final FilterController filterController;

    ToggleUnfoldMatchingBranchesAction(final FilterController filterController) {
        super("ToggleUnfoldMatchingBranchesAction");
        this.filterController = filterController;
        filterController.getUnfoldMatchingBranches().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(final ChangeEvent e) {
                setSelected(isModelSelected());
            }
        });
        setSelected(isModelSelected());
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final boolean isSelected = !isModelSelected();
        filterController.getUnfoldMatchingBranches().setSelected(isSelected);
        setSelected(isSelected);
        filterController.applyUnfoldMatchingBranchesMode();
    }

    private boolean isModelSelected() {
        return filterController.getUnfoldMatchingBranches().isSelected();
    }
}
