package org.freeplane.plugin.ai.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.freeplane.api.ai.AiRequest;

class AddToChatDispatchJob implements Runnable {
    private final AIChatPanel aiChatPanel;
    private final AiRequest request;
    private final AiRequestHandleImpl handle;
    private final AiRequestTimeoutController timeoutController;
    private final AtomicBoolean dispatchStarted = new AtomicBoolean(false);
    private volatile ChatRequestFlow activeFlow;

    AddToChatDispatchJob(AIChatPanel aiChatPanel,
                         AiRequest request,
                         AiRequestHandleImpl handle,
                         AiRequestTimeoutController timeoutController) {
        this.aiChatPanel = aiChatPanel;
        this.request = request;
        this.handle = handle;
        this.timeoutController = timeoutController;
    }

    @Override
    public void run() {
        if (handle.isDone() || !dispatchStarted.compareAndSet(false, true)) {
            return;
        }
        if (handle.isDone()) {
            return;
        }
        ChatRequestFlow startedFlow = invokeOnUiThreadAndWait();
        if (startedFlow == null || handle.isDone()) {
            return;
        }
        activeFlow = startedFlow;
        if (handle.isCancelled() && !handle.isDone()) {
            startedFlow.cancelActiveRequest();
            return;
        }
        if (handle.isDone()) {
            return;
        }
        timeoutController.armAfterStart();
    }

    void cancel() {
        ChatRequestFlow flow = activeFlow;
        if (flow != null && flow.isRequestActive()) {
            flow.cancelActiveRequest();
            return;
        }
        if (!dispatchStarted.get()) {
            completeCancelledRequest();
        }
    }

    private ChatRequestFlow invokeOnUiThreadAndWait() {
        if (SwingUtilities.isEventDispatchThread()) {
            return aiChatPanel.startAddToChatAiRequestAtDispatch(request, handle);
        }
        final ChatRequestFlow[] startedFlow = new ChatRequestFlow[1];
        final RuntimeException[] runtimeFailure = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        startedFlow[0] = aiChatPanel.startAddToChatAiRequestAtDispatch(request, handle);
                    } catch (RuntimeException runtimeException) {
                        runtimeFailure[0] = runtimeException;
                    }
                }
            });
        } catch (Exception exception) {
            runtimeFailure[0] = new RuntimeException(exception);
        }
        if (runtimeFailure[0] != null) {
            handle.complete(failedResult(runtimeFailure[0]));
            return null;
        }
        return startedFlow[0];
    }

    private void completeCancelledRequest() {
        org.freeplane.api.ai.AiRequestStatus status = handle.isTimedOut()
            ? org.freeplane.api.ai.AiRequestStatus.TIMED_OUT
            : org.freeplane.api.ai.AiRequestStatus.CANCELLED;
        handle.complete(new org.freeplane.api.ai.AiRequestResult(status, null, null));
    }

    private org.freeplane.api.ai.AiRequestResult failedResult(Throwable error) {
        return new org.freeplane.api.ai.AiRequestResult(
            org.freeplane.api.ai.AiRequestStatus.FAILED,
            null,
            AiRequestStatusMapper.detailMessage(error));
    }
}
