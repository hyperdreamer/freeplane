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
    private boolean closing;

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

    void markClosing() {
        synchronized (monitor) {
            if (!closed) {
                closing = true;
            }
        }
    }

    void reopenAfterCloseFailure() {
        synchronized (monitor) {
            if (!closed) {
                closing = false;
            }
        }
    }

    void markClosed() {
        synchronized (monitor) {
            closed = true;
            closing = false;
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
            if (closed || closing) {
                throw new IllegalStateException("Graph workspace handle is not open");
            }
            return router.execute(Objects.requireNonNull(command, "command"));
        }
    }

    @Override
    public ListenerRegistration addProjectionListener(final GraphProjectionListener listener) {
        synchronized (monitor) {
            if (closed || closing) {
                throw new IllegalStateException("Graph workspace handle is not open");
            }
            return updates.addProjectionListener(Objects.requireNonNull(listener, "listener"));
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed || closing) {
                return;
            }
            closing = true;
        }
        if (!closeController.saveAndClose()) {
            synchronized (monitor) {
                if (!closed) {
                    closing = false;
                }
            }
            throw new IllegalStateException("Unable to save graph workspace while closing");
        }
    }
}
