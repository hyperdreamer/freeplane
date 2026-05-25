package org.freeplane.api.ai;

/** Callback invoked exactly once with the terminal result of an AI request.
 * @since 1.13.3 */
@FunctionalInterface
public interface AiRequestCallback {
    void accept(AiRequestResult result);
}
