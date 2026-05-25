package org.freeplane.api.ai;

import java.util.Objects;

/** Immutable terminal result delivered to {@link AiRequestCallback}.
 * @since 1.13.3 */
public class AiRequestResult {
    private final AiRequestStatus status;
    private final String response;
    private final String detail;

    public AiRequestResult(AiRequestStatus status, String response, String detail) {
        this.status = Objects.requireNonNull(status, "status");
        this.response = response;
        this.detail = detail;
    }

    public AiRequestStatus getStatus() {
        return status;
    }

    public String getResponse() {
        return response;
    }

    public String getDetail() {
        return detail;
    }
}
