package org.freeplane.api.ai;

/** Terminal status for an AI request callback result.
 * @since 1.13.3 */
public enum AiRequestStatus {
    SUCCEEDED,
    REJECTED_BUSY,
    PERMISSION_DENIED,
    AI_UNAVAILABLE,
    CONFIGURATION_ERROR,
    AUTHENTICATION_ERROR,
    MODEL_UNAVAILABLE,
    PROVIDER_ERROR,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
