package org.freeplane.plugin.graph.layout;

import java.util.Objects;

public final class BoundarySeparationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BoundarySeparationException(final String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public BoundarySeparationException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }

    public BoundarySeparationException(final Throwable cause) {
        super(Objects.requireNonNull(cause, "cause"));
    }
}
