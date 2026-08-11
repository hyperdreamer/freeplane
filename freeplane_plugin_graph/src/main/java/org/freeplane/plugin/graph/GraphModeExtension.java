package org.freeplane.plugin.graph;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.freeplane.plugin.graph.group.GraphGroupController;
import org.freeplane.plugin.graph.group.GraphGroupMarkerPainter;
import org.freeplane.view.swing.map.NodeViewDecorationRegistry;

public final class GraphModeExtension implements IModeControllerExtensionProvider, AutoCloseable {
    private GraphGroupController graphGroupController;
    private GraphGroupMarkerPainter graphGroupMarkerPainter;
    private ModeController modeController;
    private NodeViewDecorationRegistry nodeViewDecorationRegistry;

    @Override
    public synchronized void installExtension(final ModeController modeController, final CommandLineOptions options) {
        if (this.modeController == modeController) {
            return;
        }
        close();
        final ApplicationResourceController resourceController =
            (ApplicationResourceController) ResourceController.getResourceController();
        resourceController.registerResourceLoader(getClass().getClassLoader());
        this.modeController = modeController;
        graphGroupController = new GraphGroupController(modeController);
        modeController.addExtension(GraphGroupController.class, graphGroupController);
        nodeViewDecorationRegistry = NodeViewDecorationRegistry.of(modeController);
        graphGroupMarkerPainter = new GraphGroupMarkerPainter();
        nodeViewDecorationRegistry.add(graphGroupMarkerPainter);
    }

    @Override
    public synchronized void close() {
        if (modeController == null) {
            return;
        }
        try {
            try {
                nodeViewDecorationRegistry.remove(graphGroupMarkerPainter);
            }
            finally {
                graphGroupController.close();
            }
        }
        finally {
            modeController.removeExtension(GraphGroupController.class);
            graphGroupController = null;
            graphGroupMarkerPainter = null;
            modeController = null;
            nodeViewDecorationRegistry = null;
        }
    }
}
