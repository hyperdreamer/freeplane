package org.freeplane.plugin.ai.prompt;

import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import javax.swing.SwingWorker;
import org.freeplane.plugin.ai.chat.AIChatService;
import org.freeplane.plugin.ai.chat.ChatRequestCancellation;

public class HiddenPromptRequestRunner {

    public interface Callbacks {
        void onRequestStarted(String promptName);
        void onRequestFinished(String promptName);
        void onRequestSucceeded(String promptName, String response);
        void onRequestCancelled(String promptName);
        void onRequestFailed(String promptName, String errorMessage);
    }

    private final Callbacks callbacks;
    private final ChatRequestCancellation requestCancellation = new ChatRequestCancellation();
    private SwingWorker<String, Void> activeWorker;
    private String activePromptName;
    private boolean requestActive;

    public HiddenPromptRequestRunner(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public boolean isRequestActive() {
        return requestActive;
    }

    public Supplier<Boolean> cancellationSupplier() {
        return requestCancellation::isCancelled;
    }

    public void cancelActiveRequest() {
        if (!requestActive) {
            return;
        }
        requestCancellation.cancel();
        String promptName = activePromptName;
        SwingWorker<String, Void> worker = activeWorker;
        clearActiveState();
        if (worker != null) {
            worker.cancel(true);
        }
        if (callbacks != null) {
            callbacks.onRequestCancelled(promptName);
            callbacks.onRequestFinished(promptName);
        }
    }

    public void submit(String promptName, AIChatService chatService, String userMessage) {
        if (requestActive) {
            throw new IllegalStateException("A hidden prompt request is already active.");
        }
        requestCancellation.reset();
        requestActive = true;
        activePromptName = promptName;
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return chatService.chat(userMessage);
            }

            @Override
            protected void done() {
                if (this != activeWorker) {
                    return;
                }
                try {
                    String response = get();
                    if (callbacks != null) {
                        callbacks.onRequestSucceeded(promptName, response);
                    }
                } catch (CancellationException cancelled) {
                    if (callbacks != null) {
                        callbacks.onRequestCancelled(promptName);
                    }
                    return;
                } catch (Exception error) {
                    if (!requestCancellation.isCancelled() && callbacks != null) {
                        callbacks.onRequestFailed(promptName, normalizeErrorMessage(error));
                    }
                } finally {
                    if (this == activeWorker) {
                        clearActiveState();
                        if (callbacks != null) {
                            callbacks.onRequestFinished(promptName);
                        }
                    }
                }
            }
        };
        activeWorker = worker;
        if (callbacks != null) {
            callbacks.onRequestStarted(promptName);
        }
        if (requestCancellation.isCancelled()) {
            return;
        }
        worker.execute();
    }

    private void clearActiveState() {
        activeWorker = null;
        activePromptName = null;
        requestActive = false;
    }

    private String normalizeErrorMessage(Exception error) {
        Throwable rootCause = rootCause(error);
        String message = rootCause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return rootCause.getClass().getSimpleName();
        }
        return message;
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
