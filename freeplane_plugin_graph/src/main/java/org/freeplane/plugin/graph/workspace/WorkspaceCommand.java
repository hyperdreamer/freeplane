package org.freeplane.plugin.graph.workspace;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public interface WorkspaceCommand {
    WorkspaceTransition apply(WorkspaceDocument before);
}
