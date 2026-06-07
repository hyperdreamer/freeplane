package org.freeplane.api.ai;

import java.util.Objects;

/** Runtime exception for same-thread pre-acceptance AI request rejection.
 * @since 1.13.3 */
public class AiRequestRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final AiRequestStatus status;

    public AiRequestRejectedException(AiRequestStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
    }

    public AiRequestStatus getStatus() {
        return status;
    }
}
