package org.freeplane.api.ai;

/**
 * Tool availability requested for an AI call.
 *
 * <p>{@link #CURRENT} means the request should use the current AI tool setting.
 * To inherit a saved prompt's own setting, leave
 * {@link AiRequestOptions.Builder#toolAvailability(AiToolAvailability)} unset.</p>
 *
 * @since 1.13.3
 */
public enum AiToolAvailability {
    /**
     * Use the current AI tool setting.
     */
    CURRENT,

    /**
     * Disable application tools for the request.
     */
    DISABLED,

    /**
     * Allow Freeplane read/search/selection tools only.
     */
    READING,

    /**
     * Allow Freeplane reading and map-editing tools, but not script/code execution tools.
     */
    EDITING,

    /**
     * Allow editing tools and script/code execution tools where those tools are enabled and authorized.
     */
    SCRIPT_EXECUTION
}
