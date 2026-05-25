package org.freeplane.api.ai;

/** Non-blocking handle for an in-flight or completed AI request.
 * @since 1.13.3 */
public interface AiRequestHandle {
    void cancel();
    boolean isDone();
    boolean isCancelled();
    AiRequestStatus getStatus();
}
