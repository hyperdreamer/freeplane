package org.freeplane.api.ai;

/** Tool availability requested for an AI call.
 * @since 1.13.3 */
public enum AiToolAvailability {
    CURRENT,
    DISABLED,
    READING,
    EDITING,
    SCRIPT_EXECUTION
}
