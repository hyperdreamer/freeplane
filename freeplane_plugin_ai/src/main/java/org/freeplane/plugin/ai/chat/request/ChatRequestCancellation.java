package org.freeplane.plugin.ai.chat.request;

class ChatRequestCancellation {
    private volatile boolean cancelled;

    public void reset() {
        cancelled = false;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
