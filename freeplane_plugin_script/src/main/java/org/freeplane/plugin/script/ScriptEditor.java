/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file author is Christian Foltin
 *  It is modified by Dimitry Polivaev in 2008.
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
package org.freeplane.plugin.script;

import java.awt.event.ActionEvent;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.script.ScriptEditorPanel.IScriptModel;
import org.freeplane.plugin.script.ScriptEditorPanel.ScriptHolder;

/**
 * @author foltin
 */
class ScriptEditor extends AFreeplaneAction {
    final private class AttributeHolder {
        Attribute mScriptAttribute;
        int mScriptPosition;
        Attribute mInputAttribute;
        Integer mInputPosition;

        private AttributeHolder(Attribute scriptAttribute, int scriptPosition) {
            mScriptAttribute = scriptAttribute;
            mScriptPosition = scriptPosition;
        }

        private String scriptName() {
            return mScriptAttribute.getName();
        }

        private String argumentsJsonText() {
            return mInputAttribute == null ? null : String.valueOf(mInputAttribute.getValue());
        }
    }

    final private class NodeScriptModel implements IScriptModel {
        private boolean isDirty = false;
        final private NodeModel mNode;
        final private ArrayList<AttributeHolder> mScripts;
        final private ArrayList<Integer> mOrphanInputPositions;

        private NodeScriptModel(final ArrayList<AttributeHolder> pScripts,
                                final ArrayList<Integer> orphanInputPositions,
                                final NodeModel node) {
            mScripts = pScripts;
            mOrphanInputPositions = orphanInputPositions;
            mNode = node;
        }

        public int addNewScript() {
            final int index = mScripts.size();
            final int attributeIndex = NodeAttributeTableModel.getModel(mNode).getAttributeTableLength();
            final String scriptName = ScriptingEngine.SCRIPT_PREFIX;
            int scriptNameSuffix = 1;
            boolean found;
            do {
                found = false;
                for (final AttributeHolder holder : mScripts) {
                    if ((scriptName + scriptNameSuffix).equals(holder.mScriptAttribute.getName())) {
                        found = true;
                        scriptNameSuffix++;
                        break;
                    }
                }
            } while (found);
            mScripts.add(new AttributeHolder(new Attribute(scriptName + scriptNameSuffix, ""), attributeIndex));
            isDirty = true;
            return index;
        }

        public ScriptEditorWindowConfigurationStorage decorateDialog(final ScriptEditorPanel pPanel,
                                                                     final String pWindow_preference_storage_property) {
            final String marshalled = ResourceController.getResourceController().getProperty(
                pWindow_preference_storage_property);
            return ScriptEditorWindowConfigurationStorage.decorateDialog(marshalled, pPanel);
        }

        public void endDialog(final boolean pIsCanceled) {
            if (pIsCanceled) {
                return;
            }
            final MAttributeController attributeController = (MAttributeController) AttributeController.getController();
            final int attributeTableLength = NodeAttributeTableModel.getModel(mNode).getAttributeTableLength();
            for (final AttributeHolder holder : mScripts) {
                final Attribute attribute = holder.mScriptAttribute;
                final int position = holder.mScriptPosition;
                if (attributeTableLength <= position) {
                    attributeController.addAttribute(mNode, attribute);
                }
                else if (NodeAttributeTableModel.getModel(mNode).getAttribute(position).getValue() != attribute.getValue()) {
                    attributeController.setAttribute(mNode, position, attribute);
                }
                if (holder.mInputAttribute != null && holder.mInputPosition != null) {
                    attributeController.setAttribute(mNode, holder.mInputPosition.intValue(), holder.mInputAttribute);
                }
            }
            removeObsoleteInputAttributes(attributeController);
            addMissingInputAttributes(attributeController);
        }

        public Object executeScript(final int pIndex,
                                    final PrintStream pOutStream,
                                    final PrintStream pCallbackOutputStream,
                                    final IFreeplaneScriptErrorHandler pErrorHandler) {
            final ScriptHolder scriptHolder = getScript(pIndex);
            ModeController mMindMapController = Controller.getCurrentModeController();
            ScriptInputJsonSupport.ParseResult parseResult = ScriptInputJsonSupport.parseInputText(scriptHolder.getArgumentsJsonText());
            if (!parseResult.isSuccessful()) {
                throw ScriptInputJsonSupport.toExecuteScriptException(parseResult.getDiagnostic());
            }
            ScriptContext scriptContext = new ScriptContext(null)
                .withBoundVariables(ScriptInputJsonSupport.boundVariables(parseResult.getArgsValue()))
                .withCallbackOutputStream(pCallbackOutputStream);
            return ScriptingEngine.executeScript(
                mMindMapController.getMapController().getSelectedNode(),
                scriptHolder.getScript(),
                pErrorHandler,
                pOutStream,
                scriptContext,
                ScriptingPermissions.getPermissiveScriptingPermissions());
        }

        public int getAmountOfScripts() {
            return mScripts.size();
        }

        public ScriptHolder getScript(final int pIndex) {
            final AttributeHolder attributeHolder = mScripts.get(pIndex);
            final Attribute attribute = attributeHolder.mScriptAttribute;
            return new ScriptHolder(
                attribute.getName(),
                attribute.getValue().toString(),
                attributeHolder.argumentsJsonText());
        }

        public boolean isDirty() {
            return isDirty;
        }

        public void setScript(final int pIndex, final ScriptHolder pScript) {
            final AttributeHolder oldHolder = mScripts.get(pIndex);
            if (!pScript.mScriptName.equals(oldHolder.mScriptAttribute.getName())) {
                isDirty = true;
            }
            if (!pScript.mScript.equals(oldHolder.mScriptAttribute.getValue())) {
                isDirty = true;
            }
            if (!equalsNullable(pScript.getArgumentsJsonText(), oldHolder.argumentsJsonText())) {
                isDirty = true;
            }
            oldHolder.mScriptAttribute.setName(pScript.mScriptName);
            oldHolder.mScriptAttribute.setValue(pScript.mScript);
            if (ScriptInputJsonSupport.isBlankInput(pScript.getArgumentsJsonText())) {
                oldHolder.mInputAttribute = null;
            }
            else {
                oldHolder.mInputAttribute = new Attribute(
                    ScriptInputJsonSupport.companionAttributeName(pScript.mScriptName),
                    pScript.getArgumentsJsonText());
            }
        }

        public void storeDialogPositions(final ScriptEditorPanel pPanel,
                                         final ScriptEditorWindowConfigurationStorage pStorage,
                                         final String pWindow_preference_storage_property) {
            pStorage.storeDialogPositions(pPanel, pWindow_preference_storage_property);
        }

        @Override
        public String getTitle() {
            return Controller.getCurrentModeController().getExtension(TextController.class).getShortPlainText(mNode);
        }

        private void removeObsoleteInputAttributes(MAttributeController attributeController) {
            List<Integer> removalPositions = new ArrayList<Integer>(mOrphanInputPositions);
            for (AttributeHolder holder : mScripts) {
                if (holder.mInputPosition != null && holder.mInputAttribute == null) {
                    removalPositions.add(holder.mInputPosition.intValue());
                }
            }
            Collections.sort(removalPositions, Comparator.reverseOrder());
            for (Integer position : removalPositions) {
                if (position == null) {
                    continue;
                }
                if (position.intValue() < NodeAttributeTableModel.getModel(mNode).getAttributeTableLength()) {
                    attributeController.performRemoveAttribute(mNode, position.intValue());
                }
            }
        }

        private void addMissingInputAttributes(MAttributeController attributeController) {
            for (AttributeHolder holder : mScripts) {
                if (holder.mInputAttribute == null) {
                    continue;
                }
                if (holder.mInputPosition != null) {
                    continue;
                }
                holder.mInputPosition = Integer.valueOf(attributeController.addAttribute(mNode, holder.mInputAttribute));
            }
        }

        private boolean equalsNullable(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    private static final long serialVersionUID = 1L;

    public ScriptEditor() {
        super("ScriptEditor");
    }

    public void actionPerformed(final ActionEvent e) {
        final ModeController modeController = Controller.getCurrentModeController();
        final NodeModel node = modeController.getMapController().getSelectedNode();
        final ArrayList<AttributeHolder> scripts = new ArrayList<AttributeHolder>();
        final Map<String, AttributeHolder> scriptByName = new HashMap<String, AttributeHolder>();
        final ArrayList<Integer> orphanInputPositions = new ArrayList<Integer>();
        for (int position = 0; position < NodeAttributeTableModel.getModel(node).getAttributeTableLength(); position++) {
            final Attribute attribute = NodeAttributeTableModel.getModel(node).getAttribute(position);
            if (attribute.getName().startsWith(ScriptingEngine.SCRIPT_PREFIX)) {
                AttributeHolder holder = new AttributeHolder(new Attribute(attribute), position);
                scripts.add(holder);
                scriptByName.put(holder.scriptName(), holder);
            }
        }
        for (int position = 0; position < NodeAttributeTableModel.getModel(node).getAttributeTableLength(); position++) {
            final Attribute attribute = NodeAttributeTableModel.getModel(node).getAttribute(position);
            if (!ScriptInputJsonSupport.isCompanionAttributeName(attribute.getName())) {
                continue;
            }
            String scriptName = attribute.getName().substring(ScriptInputJsonSupport.SAVED_SCRIPT_INPUT_PREFIX.length());
            AttributeHolder holder = scriptByName.get(scriptName);
            if (holder == null) {
                orphanInputPositions.add(Integer.valueOf(position));
            }
            else {
                holder.mInputAttribute = new Attribute(attribute);
                holder.mInputPosition = Integer.valueOf(position);
            }
        }
        final NodeScriptModel nodeScriptModel = new NodeScriptModel(scripts, orphanInputPositions, node);
        final ScriptEditorPanel scriptEditorPanel = new ScriptEditorPanel(nodeScriptModel, true);
        scriptEditorPanel.setVisible(true);
    }
}
