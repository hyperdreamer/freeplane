package org.freeplane.plugin.graph.command;

import org.freeplane.plugin.graph.workspace.GraphCommandResult;

public interface PurgeCommandHandler {
    GraphCommandResult purge(GraphCommands.Purge command);
}
