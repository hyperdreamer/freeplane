package org.freeplane.plugin.graph.control;

import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;

public interface GraphWorkspaceHandle extends AutoCloseable {
    GraphProjection currentProjection();
    GraphCommandResult execute(GraphCommand command);
    ListenerRegistration addProjectionListener(GraphProjectionListener listener);
    @Override
    void close();
}
