package org.freeplane.plugin.graph.command;

import java.util.Objects;

import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class ViewMaterializationTracker {
    private final MapController mapController;
    private final IMapViewManager viewManager;

    public ViewMaterializationTracker(final ModeController modeController) {
        final ModeController controller = Objects.requireNonNull(modeController, "modeController");
        mapController = Objects.requireNonNull(controller.getMapController(), "modeController.mapController");
        final Controller applicationController = Objects.requireNonNull(controller.getController(),
            "modeController.controller");
        viewManager = Objects.requireNonNull(applicationController.getMapViewManager(), "mapViewManager");
    }

    public boolean containsView(final MapModel map) {
        Objects.requireNonNull(map, "map");
        return viewManager.containsView(map);
    }

    public boolean materialize(final MapReferenceId mapReferenceId, final MapModel map) {
        Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        final MapModel model = Objects.requireNonNull(map, "map");
        if (viewManager.containsView(model)) {
            return false;
        }
        mapController.createMapView(model);
        return true;
    }
}
