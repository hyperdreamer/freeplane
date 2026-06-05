package org.freeplane.plugin.ai.maps;

import java.util.List;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;

public interface MapModelProvider {
    MapModel getCurrentMapModel();

    List<MapModel> getOpenMapModels();

    NodeModel getCurrentSelectedNodeModel();
}
