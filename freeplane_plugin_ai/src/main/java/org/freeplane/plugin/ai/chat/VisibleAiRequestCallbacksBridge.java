package org.freeplane.plugin.ai.chat;

import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestStatus;

class VisibleAiRequestCallbacksBridge implements ChatPromptRunner.VisiblePromptRequestCallbacks {
    private final AiRequestHandleImpl handle;

    VisibleAiRequestCallbacksBridge(AiRequestHandleImpl handle) {
        this.handle = handle;
    }

    @Override
    public void onResponseAppended(String response) {
        handle.complete(new AiRequestResult(AiRequestStatus.SUCCEEDED, response, null));
    }

    @Override
    public void onFailed(String userText, String errorMessage) {
        handle.complete(new AiRequestResult(
            AiRequestStatusMapper.fromFailure(new RuntimeException(errorMessage)),
            null,
            errorMessage));
    }

    @Override
    public void onCancelled() {
        AiRequestStatus status = handle.isTimedOut() ? AiRequestStatus.TIMED_OUT : AiRequestStatus.CANCELLED;
        handle.complete(new AiRequestResult(status, null, null));
    }
}
