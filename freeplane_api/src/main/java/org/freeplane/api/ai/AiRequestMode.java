package org.freeplane.api.ai;

/** Visible or hidden execution mode for {@link AiRequestOptions}.
 * @since 1.13.3 */
public enum AiRequestMode {
    SHOW_IN_CHAT,
    ADD_TO_CHAT,
    HIDDEN_WITH_CANCEL_DIALOG,
    HIDDEN
}
