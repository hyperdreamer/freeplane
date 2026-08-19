package org.freeplane.plugin.graph.control;

import java.nio.file.Path;

public final class GraphWorkspaceOpenException extends RuntimeException {
    private final Path path;

    public GraphWorkspaceOpenException(final Path path, final Throwable cause) {
        super("Unable to open graph workspace: " + path, cause);
        this.path = path;
    }

    public Path path() {
        return path;
    }
}
