package org.freeplane.plugin.graph.layout;

public final class EdgeConflictException extends RuntimeException {
    public EdgeConflictException(final String message) {
        super(message);
    }

    public EdgeConflictException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public EdgeConflictException(final Throwable cause) {
        super(cause);
    }
}
