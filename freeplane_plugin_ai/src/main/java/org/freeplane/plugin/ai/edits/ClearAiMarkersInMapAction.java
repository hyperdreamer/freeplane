package org.freeplane.plugin.ai.edits;

import java.awt.event.ActionEvent;
import java.util.Map;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

public class ClearAiMarkersInMapAction extends AFreeplaneAction {
    private static final long serialVersionUID = 1L;
    public static final String ACTION_KEY = "ClearAiMarkersInMapAction";

    public ClearAiMarkersInMapAction() {
        super(ACTION_KEY);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return;
        }
        MapModel mapModel = controller.getMap();
        if (mapModel == null) {
            return;
        }
        MapController mapController = controller.getModeController().getMapController();
        if (mapController == null) {
            return;
        }
        AiEditsMarkerRemoval markerRemoval = new AiEditsMarkerRemoval();
        Map<NodeModel, AIEdits> removedEditsByNode = markerRemoval.collectNodesWithAiEdits(mapModel.getRootNode());
        if (removedEditsByNode.isEmpty()) {
            return;
        }
        IActor actor = markerRemoval.createUndoableRemoval(removedEditsByNode, mapController, getKey());
        controller.getModeController().execute(actor, mapModel);
    }
}
