package org.freeplane.plugin.graph.workspace;

import java.nio.file.Path;
import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class WorkspaceIdentityChange {
    private final Path oldPath;
    private final Path newPath;
    private final WorkspaceId oldId;
    private final WorkspaceId newId;

    WorkspaceIdentityChange(final Path oldPath, final Path newPath, final WorkspaceId oldId,
            final WorkspaceId newId) {
        this.oldPath = Objects.requireNonNull(oldPath, "oldPath");
        this.newPath = Objects.requireNonNull(newPath, "newPath");
        this.oldId = Objects.requireNonNull(oldId, "oldId");
        this.newId = Objects.requireNonNull(newId, "newId");
    }

    public Path oldPath() {
        return oldPath;
    }

    public Path newPath() {
        return newPath;
    }

    public WorkspaceId oldId() {
        return oldId;
    }

    public WorkspaceId newId() {
        return newId;
    }
}
