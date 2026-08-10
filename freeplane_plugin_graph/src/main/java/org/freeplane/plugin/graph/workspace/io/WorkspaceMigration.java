package org.freeplane.plugin.graph.workspace.io;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public interface WorkspaceMigration {
    int fromVersion();

    int toVersion();

    WorkspaceDocument migrate(WorkspaceDocument source);
}
