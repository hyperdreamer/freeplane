/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2011 dimitry
 *
 *  This file author is dimitry
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
package org.freeplane.view.swing.map.attribute;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.freeplane.core.ui.components.JComboBoxFactory;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.format.FormatController;
import org.freeplane.features.format.FormattedFormula;
import org.freeplane.features.format.FormattedObject;
import org.freeplane.features.format.IFormattedObject;
import org.freeplane.features.format.PatternFormat;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.INodeSelectionListener;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.view.swing.map.MainView;
import org.freeplane.view.swing.map.NodeTooltipManager;
import org.freeplane.view.swing.map.NodeView;

/**
 * @author Dimitry Polivaev
 * Jan 9, 2011
 */
public class AttributePanelManager{
    final private JPanel tablePanel;
    private ModeController modeController;
    private int axis = BoxLayout.Y_AXIS;
    private class TableCreator implements INodeSelectionListener, INodeChangeListener, HierarchyListener {
        private AttributeView attributeView;
        private JComboBox formatChooser;

        public TableCreator() {
            tablePanel.addHierarchyListener(this);
        }

        @Override
        public void hierarchyChanged(HierarchyEvent e) {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (tablePanel.isShowing()) {
                    IMapSelection selection = Controller.getCurrentController().getSelection();
                    if(selection != null)
                        onSelect(selection.getSelected());
                } else
                    removeOldView();
            }
        }

        @Override
        public void onDeselect(NodeModel node) {
            if (tablePanel.isShowing())
                removeOldView();
        }

        private void removeOldView() {
            if(attributeView != null){
                tablePanel.removeAll();
                tablePanel.revalidate();
                tablePanel.repaint();
                attributeView.viewRemoved();
                attributeView = null;
            }
        }

        @Override
        public void onSelect(NodeModel node) {
            if (!tablePanel.isShowing()) {
                return;
            }
            removeOldView();

            final MainView mainView = getSelectedMainView();
            if (mainView == null) {
                return;
            }

            final NodeView nodeView = getNodeViewSafely(mainView, node);
            if (nodeView == null) {
                return;
            }

            createNewAttributeView(nodeView);
            createButtonPanel();
            finalizeTablePanelLayout();
        }

        private MainView getSelectedMainView() {
            return (MainView) Controller.getCurrentController()
                    .getMapViewManager().getSelectedComponent();
        }

        private NodeView getNodeViewSafely(MainView mainView, NodeModel node) {
            AttributeController.getController(modeController).createAttributeTableModel(node);
            final NodeView nodeView = mainView.getNodeView();
            if (nodeView == null) {
                LogUtils.severe(new RuntimeException("Node view null for mainView" + String.valueOf(mainView) + " and node " + String.valueOf(node)));
                return null;
            }
            return nodeView;
        }

        private void createNewAttributeView(NodeView nodeView) {
            attributeView = new AttributeView(nodeView, false);
        }

        private void createButtonPanel() {
            Box buttonBox = new Box(axis);
            buttonBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            tablePanel.add(buttonBox);

            Dimension btnSize = new Dimension();
            addNewAttributeButton(buttonBox, btnSize);
            addOptimalWidthButton(buttonBox, btnSize);
            addFormatChooser(buttonBox, btnSize);

            normalizeButtonSizes(buttonBox, btnSize);
            setupFormatChooserListeners();
        }

        private void addNewAttributeButton(Box buttonBox, Dimension btnSize) {
            final JButton newAttributeButton = new JButton(TextUtils.getText("attributes_popup_new"));
            newAttributeButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent arg0) {
                    if (!modeController.isEditingLocked())
                        attributeView.addRow();
                }
            });
            increaseSize(btnSize, newAttributeButton);
            buttonBox.add(newAttributeButton);
        }

        private void addOptimalWidthButton(Box buttonBox, Dimension btnSize) {
            final JButton optimalWidthButton = new JButton(TextUtils.getText("attributes_popup_optimal_width"));
            optimalWidthButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent arg0) {
                    if (!modeController.isEditingLocked())
                        attributeView.setOptimalColumnWidths();
                }
            });
            increaseSize(btnSize, optimalWidthButton);
            buttonBox.add(optimalWidthButton);
        }

        private void normalizeButtonSizes(Box buttonBox, Dimension btnSize) {
            for (int i = 0; i < buttonBox.getComponentCount(); i++) {
                buttonBox.getComponent(i).setMaximumSize(btnSize);
            }
        }

        private void finalizeTablePanelLayout() {
            tablePanel.add(Box.createVerticalStrut(5));
            JComponent c = attributeView.getContainer();
            NodeTooltipManager.getSharedInstance().ignoreGlobalShowTooltipOption(attributeView.getAttributeTable());
            attributeView.update();
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            tablePanel.add(c);
            tablePanel.add(Box.createGlue());
            tablePanel.revalidate();
            tablePanel.repaint();
        }

        private void addFormatChooser(Box buttonBox, Dimension btnSize) {
            formatChooser = createFormatChooser();
            formatChooser.setEnabled(false);
            increaseSize(btnSize, formatChooser);
            buttonBox.add(formatChooser);
        }

        private JComboBox createFormatChooser() {
            final List<PatternFormat> formats = FormatController.getController().getAllFormats();
            Vector<PatternFormat> items = new Vector<PatternFormat>(formats);
            for(int i = items.size()-1; i >= 0; i--){
                PatternFormat item = items.get(i);
                if(item.getType().equals(PatternFormat.TYPE_IDENTITY) &&
                        ! item.getType().endsWith(PatternFormat.IDENTITY_PATTERN))
                    items.remove(i);
            }
            final JComboBox formatChooser = JComboBoxFactory.create(items);
            formatChooser.setEditable(true);
            formatChooser.setSelectedItem(null);
            final String NODE_FORMAT = "OptionPanel.nodeformat"; // duplicated from StyleEditorPanel
            formatChooser.setToolTipText(TextUtils.getRawText(NODE_FORMAT + ".tooltip"));
            formatChooser.setAlignmentX(Component.LEFT_ALIGNMENT);
            formatChooser.setBorder(new TitledBorder(TextUtils.getText("value_format")));
            return formatChooser;
        }

        private void increaseSize(final Dimension btnSize, final JComponent comp) {
            final Dimension preferredSize = comp.getPreferredSize();
            btnSize.width =  Math.max(btnSize.width, preferredSize.width);
            btnSize.height =  Math.max(btnSize.height, preferredSize.height);
        }

        private void setupFormatChooserListeners() {
            formatChooser.addItemListener(new ItemListener() {
                boolean handlingEvent = false;

                @Override
                public void itemStateChanged(final ItemEvent e) {
                    if (handlingEvent || !formatChooser.isEnabled() || e.getStateChange() != ItemEvent.SELECTED)
                        return;
                    handlingEvent = true;
                    final PatternFormat newFormat = toPatternFormat(e.getItem());
                    final AttributeTable table = attributeView.getAttributeTable();
                    if (table.getSelectedColumn() == 1 && table.getSelectedRow() != -1) {
                        final Object value = table.getValueAt(table.getSelectedRow(), table.getSelectedColumn());
                        try {
                            final Object newValue = formatValue(newFormat, table, value);
                            if (newValue != null)
                                table.setValueAt(newValue, table.getSelectedRow(), table.getSelectedColumn());
                        }
                        catch (Exception e2) {
                            Controller.getCurrentController().getViewController()
                                .out("Pattern is not applicable: " + e2.getMessage());
                            LogUtils.warn("pattern is not applicable", e2);
                        }
                    }
                    handlingEvent = false;
                }

                public PatternFormat toPatternFormat(Object value) {
                    if (value instanceof PatternFormat)
                        return (PatternFormat) value;
                    final PatternFormat patternFormat = PatternFormat.guessPatternFormat(value.toString());
                    return (patternFormat == null) ? PatternFormat.getIdentityPatternFormat() : patternFormat;
                }

                private Object formatValue(final PatternFormat newFormat, final AttributeTable table,
                                           final Object objectToBeFormatted) {
                    if (formatChooser.getSelectedItem() == null)
                        return null;
                    if (objectToBeFormatted instanceof IFormattedObject) {
                        final Object actualObject = ((IFormattedObject) objectToBeFormatted).getObject();
                        if(actualObject != objectToBeFormatted)
                            return formatValue(newFormat, table, actualObject);
                    }
                    if (newFormat == PatternFormat.getIdentityPatternFormat())
                        return makeFormattedObjectForIdentityFormat(objectToBeFormatted);
                    if (objectToBeFormatted instanceof String && ((String) objectToBeFormatted).startsWith("="))
                        return new FormattedFormula((String) objectToBeFormatted, newFormat.getPattern());
                    return newFormat.formatObject(objectToBeFormatted);
                }

                private FormattedObject makeFormattedObjectForIdentityFormat(final Object objectToBeFormatted) {
                    return new FormattedObject(String.valueOf(objectToBeFormatted), PatternFormat.IDENTITY_PATTERN);
                }
            });

            attributeView.addTableSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(final ListSelectionEvent event) {
                    // update format chooser
                    if (tablePanel.isShowing() && attributeView != null && !event.getValueIsAdjusting()) {
                        setSelectedFormatItem();
                    }
                }
            });
        }

        private void setSelectedFormatItem() {
            final AttributeTable table = attributeView.getAttributeTable();
            final int selectedColumn = table.getSelectedColumn();
            final int selectedRow = table.getSelectedRow();
            if (selectedColumn == 1 && selectedRow >= 0 && selectedRow < table.getRowCount()) {
                formatChooser.setEnabled(true);
                final Object value = table.getValueAt(selectedRow, selectedColumn);
                if (value instanceof IFormattedObject) {
                    final String format = ((IFormattedObject) value).getPattern();
                    formatChooser.setSelectedItem(PatternFormat.guessPatternFormat(format));
                }
                else {
                    formatChooser.setSelectedItem(null);
                }
            }
            else {
                formatChooser.setEnabled(false);
            }
        }

        @Override
        public void nodeChanged(NodeChangeEvent event) {
            if (!tablePanel.isShowing()) {
                return;
            }
            if(attributeView != null && event.getProperty().equals(NodeAttributeTableModel.class)){
                setSelectedFormatItem();
            }
        }
    }

    public AttributePanelManager(final ModeController modeController){
        this.modeController = modeController;
        tablePanel = new JPanel();
        tablePanel.setMinimumSize(new Dimension(0, 0));
        tablePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        tablePanel.setLayout(new BoxLayout(tablePanel, axis));
        final TableCreator tableCreator = new TableCreator();
        final MapController mapController = modeController.getMapController();
        mapController.addNodeSelectionListener(tableCreator);
        mapController.addUINodeChangeListener(tableCreator);
    }
    public JPanel getTablePanel() {
        return tablePanel;
    }
}
