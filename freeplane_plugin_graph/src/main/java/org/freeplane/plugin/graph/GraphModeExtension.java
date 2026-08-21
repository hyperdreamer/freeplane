package org.freeplane.plugin.graph;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.freeplane.plugin.graph.group.GraphGroupController;
import org.freeplane.plugin.graph.group.GraphGroupMarkerPainter;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.window.OpenGraphWorkspaceAction;
import org.freeplane.plugin.graph.window.SwingGraphWorkspaceViewFactory;
import org.freeplane.view.swing.map.NodeViewDecorationRegistry;

public final class GraphModeExtension implements IModeControllerExtensionProvider, AutoCloseable {
    private GraphGroupController graphGroupController;
    private GraphGroupMarkerPainter graphGroupMarkerPainter;
    private OpenGraphWorkspaceAction openGraphWorkspaceAction;
    private DefaultGraphWorkspaceController graphWorkspaceController;
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
        final ForwardingGraphWorkspaceController viewController = new ForwardingGraphWorkspaceController();
        final DefaultGraphWorkspaceController completedController = new DefaultGraphWorkspaceController(modeController,
            new SwingGraphWorkspaceViewFactory(viewController));
        viewController.bind(completedController);
        graphWorkspaceController = completedController;
        openGraphWorkspaceAction = new OpenGraphWorkspaceAction(viewController);
        modeController.addAction(openGraphWorkspaceAction);
        nodeViewDecorationRegistry = NodeViewDecorationRegistry.of(modeController);
        graphGroupMarkerPainter = new GraphGroupMarkerPainter();
        nodeViewDecorationRegistry.add(graphGroupMarkerPainter);
    }

    @Override
    public synchronized void close() {
        if (modeController == null && graphWorkspaceController == null && graphGroupController == null
                && graphGroupMarkerPainter == null && openGraphWorkspaceAction == null
                && nodeViewDecorationRegistry == null) {
            return;
        }
        final ModeController installedModeController = modeController;
        try {
            try {
                if (graphWorkspaceController != null) {
                    graphWorkspaceController.shutdown();
                }
            }
            finally {
                try {
                    if (nodeViewDecorationRegistry != null && graphGroupMarkerPainter != null) {
                        nodeViewDecorationRegistry.remove(graphGroupMarkerPainter);
                    }
                }
                finally {
                    try {
                        if (installedModeController != null) {
                            installedModeController.removeAction(OpenGraphWorkspaceAction.KEY);
                        }
                    }
                    finally {
                        if (graphGroupController != null) {
                            graphGroupController.close();
                        }
                    }
                }
            }
        }
        finally {
            try {
                if (installedModeController != null) {
                    installedModeController.removeExtension(GraphGroupController.class);
                }
            }
            finally {
                graphGroupController = null;
                graphGroupMarkerPainter = null;
                openGraphWorkspaceAction = null;
                graphWorkspaceController = null;
                modeController = null;
                nodeViewDecorationRegistry = null;
            }
        }
    }

    private static final class ForwardingGraphWorkspaceController implements GraphWorkspaceController {
        private GraphWorkspaceController delegate;

        private void bind(final GraphWorkspaceController value) {
            delegate = value;
        }

        @Override
        public org.freeplane.plugin.graph.control.GraphWorkspaceHandle open(final java.nio.file.Path path) {
            if (delegate == null) {
                throw new IllegalStateException("Graph workspace controller is not initialized");
            }
            return delegate.open(path);
        }
    }
}
