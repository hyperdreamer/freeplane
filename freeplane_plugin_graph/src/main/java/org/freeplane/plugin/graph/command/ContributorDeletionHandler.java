package org.freeplane.plugin.graph.command;

import org.freeplane.plugin.graph.workspace.GraphCommandResult;

public interface ContributorDeletionHandler {
    GraphCommandResult deleteOne(GraphCommands.DeleteContributor command);

    GraphCommandResult deleteAll(GraphCommands.DeleteAllContributors command);
}
