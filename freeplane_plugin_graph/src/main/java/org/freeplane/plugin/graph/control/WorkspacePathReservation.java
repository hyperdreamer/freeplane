package org.freeplane.plugin.graph.control;

import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;

public interface WorkspacePathReservation extends AutoCloseable {
    void commit(WorkspaceIdentityChange change);

    @Override
    void close();
}
