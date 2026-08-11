package org.freeplane.plugin.graph.group;

import java.awt.event.ActionEvent;
import java.util.List;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.SelectableAction;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;

@SelectableAction(checkOnNodeChange = true)
final class GraphGroupAction extends AFreeplaneAction {
    static final String KEY = "GraphGroupAction";
    private static final long serialVersionUID = 1L;

    private final GraphGroupController graphGroupController;
    private final ModeController modeController;

    GraphGroupAction(final ModeController modeController, final GraphGroupController graphGroupController) {
        super(KEY);
        this.modeController = modeController;
        this.graphGroupController = graphGroupController;
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        final MapController mapController = modeController.getMapController();
        if (mapController == null) {
            return;
        }
        final NodeModel primary = mapController.getSelectedNode();
        final List<NodeModel> selection = mapController.getSelectedNodes();
        if (primary == null || selection == null || selection.isEmpty()) {
            return;
        }
        graphGroupController.setMarked(selection, !graphGroupController.isMarked(primary));
    }

    @Override
    public void setSelected() {
        final MapController mapController = modeController.getMapController();
        final NodeModel primary = mapController == null ? null : mapController.getSelectedNode();
        setSelected(primary != null && graphGroupController.isMarked(primary));
    }
}
