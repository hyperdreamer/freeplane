package org.freeplane.api.ai;

/**
 * User-interface visibility for an AI request.
 *
 * @since 1.13.3
 */
public enum AiRequestMode {
    /**
     * Start a new visible chat tab/session for the request.
     */
    SHOW_IN_NEW_CHAT,

    /**
     * Add the request to the selected chat when compatible, otherwise start a new
     * visible script chat.
     */
    ADD_TO_CHAT,

    /**
     * Run hidden from chat history and show a progress dialog with cancellation.
     */
    HIDDEN_WITH_CANCEL_DIALOG,

    /**
     * Run hidden from chat history without showing a progress dialog.
     */
    HIDDEN
}
