package org.freeplane.plugin.graph.workspace;

import java.nio.file.Path;
import java.util.Objects;

public final class WorkspaceSaveException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WorkspaceSaveException(Path target, Throwable cause) {
        super("Unable to save workspace at " + Objects.requireNonNull(target, "target"),
            Objects.requireNonNull(cause, "cause"));
    }
}
