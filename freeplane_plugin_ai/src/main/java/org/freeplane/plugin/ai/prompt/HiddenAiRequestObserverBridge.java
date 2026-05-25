package org.freeplane.plugin.ai.prompt;

import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.plugin.ai.chat.AiRequestHandleImpl;
import org.freeplane.plugin.ai.chat.AiRequestStatusMapper;

public class HiddenAiRequestObserverBridge {
    private final AiRequestHandleImpl handle;

    public HiddenAiRequestObserverBridge(AiRequestHandleImpl handle) {
        this.handle = handle;
    }

    public void onSucceeded(String response) {
        handle.complete(new AiRequestResult(AiRequestStatus.SUCCEEDED, response, null));
    }

    public void onFailed(String errorMessage) {
        handle.complete(new AiRequestResult(
            AiRequestStatusMapper.fromFailure(new RuntimeException(errorMessage)),
            null,
            errorMessage));
    }

    public void onCancelled() {
        AiRequestStatus status = handle.isTimedOut() ? AiRequestStatus.TIMED_OUT : AiRequestStatus.CANCELLED;
        handle.complete(new AiRequestResult(status, null, null));
    }
}
