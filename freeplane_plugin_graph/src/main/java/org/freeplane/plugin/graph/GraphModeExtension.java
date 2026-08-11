package org.freeplane.plugin.graph;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.freeplane.plugin.graph.group.GraphGroupController;

public final class GraphModeExtension implements IModeControllerExtensionProvider, AutoCloseable {
    private GraphGroupController graphGroupController;
    private ModeController modeController;

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
    }

    @Override
    public synchronized void close() {
        if (modeController == null) {
            return;
        }
        try {
            graphGroupController.close();
        }
        finally {
            modeController.removeExtension(GraphGroupController.class);
            graphGroupController = null;
            modeController = null;
        }
    }
}
