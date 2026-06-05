package org.freeplane.plugin.ai.chat.request;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AiRequestTimeoutController {
    interface TimeoutScheduler {
        ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit);
    }

    private final AiRequestHandleImpl handle;
    private final TimeoutScheduler timeoutScheduler;
    private final Duration timeout;
    private ScheduledFuture<?> timeoutFuture;

    AiRequestTimeoutController(AiRequestHandleImpl handle,
                               TimeoutScheduler timeoutScheduler,
                               Duration timeout) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.handle.setCompletionCleanup(this::cancelTimer);
    }

    public synchronized void armAfterStart() {
        if (timeoutFuture != null || handle.isDone()) {
            return;
        }
        timeoutFuture = timeoutScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (handle.isDone()) {
                    return;
                }
                handle.markTimedOut();
                handle.cancel();
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    synchronized void cancelTimer() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }
}
