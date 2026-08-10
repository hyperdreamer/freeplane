package org.freeplane.plugin.graph;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;

public final class GraphModeExtension implements IModeControllerExtensionProvider, AutoCloseable {
    @Override
    public void installExtension(final ModeController modeController, final CommandLineOptions options) {
        final ApplicationResourceController resourceController =
            (ApplicationResourceController) ResourceController.getResourceController();
        resourceController.registerResourceLoader(getClass().getClassLoader());
    }

    @Override
    public void close() {
    }
}
