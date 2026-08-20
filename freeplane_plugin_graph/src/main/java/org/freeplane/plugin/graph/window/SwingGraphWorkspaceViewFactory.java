package org.freeplane.plugin.graph.window;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewFactory;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;

public final class SwingGraphWorkspaceViewFactory implements GraphWorkspaceViewFactory {
    private final GraphWorkspaceController applicationController;
    private final Supplier<Path> pathChooser;

    public SwingGraphWorkspaceViewFactory(final GraphWorkspaceController applicationController) {
        this(applicationController, GraphWorkspaceWindow::chooseWorkspacePath);
    }

    SwingGraphWorkspaceViewFactory(final GraphWorkspaceController applicationController,
            final Supplier<Path> pathChooser) {
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
    }

    @Override
    public GraphWorkspaceView create(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController close) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(close, "close");
        final AtomicReference<GraphWorkspaceView> view = new AtomicReference<GraphWorkspaceView>();
        final Runnable construction = new Runnable() {
            @Override
            public void run() {
                if (GraphicsEnvironment.isHeadless()) {
                    view.set(new HeadlessGraphWorkspaceView(handle, binding, close, applicationController, pathChooser));
                }
                else {
                    view.set(new GraphWorkspaceWindow(handle, binding, close, applicationController, pathChooser));
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            construction.run();
        }
        else {
            try {
                SwingUtilities.invokeAndWait(construction);
            }
            catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while constructing graph workspace window", exception);
            }
            catch (final java.lang.reflect.InvocationTargetException exception) {
                final Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException("Graph workspace window construction failed", cause);
            }
        }
        return Objects.requireNonNull(view.get(), "workspace view");
    }
}
