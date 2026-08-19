package org.freeplane.plugin.graph.control;

import java.util.Objects;

import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;

public final class DefaultGraphWorkspaceHandle implements GraphWorkspaceHandle {
    private final Object monitor;
    private final GraphCommandRouter router;
    private final GraphUpdateCoordinator updates;
    private final WorkspaceCloseController closeController;
    private boolean closed;

    public DefaultGraphWorkspaceHandle(final GraphCommandRouter router, final GraphUpdateCoordinator updates,
            final WorkspaceCloseController closeController) {
        this(router, updates, closeController, new Object());
    }

    DefaultGraphWorkspaceHandle(final GraphCommandRouter router, final GraphUpdateCoordinator updates,
            final WorkspaceCloseController closeController, final Object monitor) {
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.router = Objects.requireNonNull(router, "router");
        this.updates = Objects.requireNonNull(updates, "updates");
        this.closeController = Objects.requireNonNull(closeController, "closeController");
    }

    void markClosed() {
        synchronized (monitor) {
            closed = true;
        }
    }

    @Override
    public GraphProjection currentProjection() {
        synchronized (monitor) {
            return updates.currentProjection();
        }
    }

    @Override
    public GraphCommandResult execute(final GraphCommand command) {
        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("Graph workspace handle is closed");
            }
            return router.execute(Objects.requireNonNull(command, "command"));
        }
    }

    @Override
    public ListenerRegistration addProjectionListener(final GraphProjectionListener listener) {
        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("Graph workspace handle is closed");
            }
            return updates.addProjectionListener(Objects.requireNonNull(listener, "listener"));
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            if (!closeController.saveAndClose()) {
                throw new IllegalStateException("Unable to save graph workspace while closing");
            }
            closed = true;
        }
    }
}
