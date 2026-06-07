package org.freeplane.plugin.ai.maps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;

public class ControllerMapModelProvider implements MapModelProvider {
    @Override
    public MapModel getCurrentMapModel() {
        Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return null;
        }
        return controller.getMap();
    }

    @Override
    public List<MapModel> getOpenMapModels() {
        Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return Collections.emptyList();
        }
        IMapViewManager mapViewManager = controller.getMapViewManager();
        if (mapViewManager == null) {
            return Collections.emptyList();
        }
        Collection<MapModel> mapModels = mapViewManager.getMaps().values();
        if (mapModels == null || mapModels.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<MapModel> uniqueMapModels = new LinkedHashSet<>(mapModels);
        return new ArrayList<>(uniqueMapModels);
    }

    @Override
    public NodeModel getCurrentSelectedNodeModel() {
        Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return null;
        }
        IMapSelection selection = controller.getSelection();
        if (selection == null) {
            return null;
        }
        return selection.getSelected();
    }
}
