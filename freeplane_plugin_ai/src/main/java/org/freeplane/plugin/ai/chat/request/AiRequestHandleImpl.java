package org.freeplane.plugin.ai.chat.request;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestStatus;

public class AiRequestHandleImpl implements AiRequestHandle {
    private final CallbackDispatcher callbackDispatcher;
    private final AiRequestCallback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private volatile Runnable cancelAction;
    private volatile Runnable completionCleanup;
    private volatile boolean cancelled;
    private volatile boolean timedOut;
    private volatile AiRequestStatus status;

    public AiRequestHandleImpl(CallbackDispatcher callbackDispatcher, AiRequestCallback callback) {
        this.callbackDispatcher = Objects.requireNonNull(callbackDispatcher, "callbackDispatcher");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    @Override
    public void cancel() {
        if (isDone()) {
            return;
        }
        cancelled = true;
        Runnable currentCancelAction = cancelAction;
        if (currentCancelAction != null) {
            currentCancelAction.run();
        }
    }

    @Override
    public boolean isDone() {
        return completed.get();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public AiRequestStatus getStatus() {
        return status;
    }

    public void setCancelAction(Runnable cancelAction) {
        this.cancelAction = cancelAction;
        if (cancelled && cancelAction != null && !isDone()) {
            cancelAction.run();
        }
    }

    public void markTimedOut() {
        timedOut = true;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void setCompletionCleanup(Runnable completionCleanup) {
        this.completionCleanup = completionCleanup;
    }

    public void complete(AiRequestResult result) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        status = result == null ? null : result.getStatus();
        Runnable cleanup = completionCleanup;
        if (cleanup != null) {
            cleanup.run();
        }
        callbackDispatcher.dispatch(() -> callback.accept(result));
    }

    public interface CallbackDispatcher {
        void dispatch(Runnable runnable);
    }
}
