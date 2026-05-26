package org.freeplane.api.ai;

import java.time.Duration;

/** Cross-plugin OSGi service for asynchronous AI requests.
 * @since 1.13.3 */
public interface AiRequestService {
    AiRequestHandle askAi(String prompt, AiRequestOptions options, AiRequestCallback callback);
    AiRequestHandle runAiPrompt(String promptName, Duration timeout, AiRequestCallback callback);
    AiRequestHandle runAiPrompt(String promptName, AiRequestOptions options, AiRequestCallback callback);
}
