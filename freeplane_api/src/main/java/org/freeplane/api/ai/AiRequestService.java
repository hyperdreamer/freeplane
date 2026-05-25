package org.freeplane.api.ai;

/** Cross-plugin OSGi service for asynchronous AI requests.
 * @since 1.13.3 */
public interface AiRequestService {
    AiRequestHandle askAi(AiRequest request, AiRequestCallback callback);
}
