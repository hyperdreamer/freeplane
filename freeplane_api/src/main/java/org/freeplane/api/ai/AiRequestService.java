package org.freeplane.api.ai;

import java.time.Duration;

/**
 * Cross-plugin OSGi service for asynchronous AI requests.
 *
 * @since 1.13.3
 */
public interface AiRequestService {
    /**
     * Starts an asynchronous AI request for raw prompt text.
     *
     * <p>The supplied options must include a positive timeout and a non-null
     * {@link AiRequestMode}. System-message handling is controlled by
     * {@link AiRequestOptions.Builder#systemMessage(String)} and
     * {@link AiRequestOptions.Builder#exactSystemMessage(String)}.</p>
     *
     * @param prompt prompt text sent as the user request
     * @param options request options; must include timeout and mode
     * @param callback callback invoked with the terminal request result
     * @return non-blocking request handle
     */
    AiRequestHandle askAi(String prompt, AiRequestOptions options, AiRequestCallback callback);

    /**
     * Starts an asynchronous AI request from a saved AI prompt name using the
     * supplied timeout and the saved prompt's stored execution defaults.
     *
     * @param promptName saved prompt name
     * @param timeout positive request timeout
     * @param callback callback invoked with the terminal request result
     * @return non-blocking request handle
     */
    AiRequestHandle runAiPrompt(String promptName, Duration timeout, AiRequestCallback callback);

    /**
     * Starts an asynchronous AI request from a saved AI prompt name using options
     * to override saved-prompt execution defaults.
     *
     * <p>Unset option fields inherit from the saved prompt where applicable.
     * Explicit values in {@link AiRequestOptions} override saved-prompt values;
     * unset fields inside {@link AiModelConfiguration} inherit independently.</p>
     *
     * @param promptName saved prompt name
     * @param options request options; must include a positive timeout
     * @param callback callback invoked with the terminal request result
     * @return non-blocking request handle
     */
    AiRequestHandle runAiPrompt(String promptName, AiRequestOptions options, AiRequestCallback callback);
}
