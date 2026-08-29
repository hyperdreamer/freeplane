package org.freeplane.plugin.graph.group;

import java.awt.Color;
import java.awt.event.ActionEvent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.ColorTracker;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

final class GraphGroupColorAction extends AFreeplaneAction {
    static final String KEY = "GraphGroupColorAction";
    private static final long serialVersionUID = 1L;

    GraphGroupColorAction() {
        super(KEY);
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        final Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return;
        }
        final NodeModel selected = controller.getSelection().getSelected();
        final Color actionColor = ColorTracker.showCommonJColorChooserDialog(selected,
            TextUtils.getText("choose_graph_group_color"), GraphGroupColors.currentColor(),
            GraphGroupColors.DEFAULT_COLOR);
        if (actionColor != null) {
            ResourceController.getResourceController().setProperty(GraphGroupColors.COLOR_PROPERTY_KEY,
                ColorUtils.colorToString(actionColor));
        }
    }
}
