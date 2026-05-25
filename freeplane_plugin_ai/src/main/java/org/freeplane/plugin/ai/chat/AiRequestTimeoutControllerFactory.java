package org.freeplane.plugin.ai.chat;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.freeplane.api.ai.AiRequest;

class AiRequestTimeoutControllerFactory {
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "freeplane-ai-request-timeout");
                thread.setDaemon(true);
                return thread;
            }
        });

    private final AiRequestTimeoutController.TimeoutScheduler timeoutScheduler;

    AiRequestTimeoutControllerFactory() {
        this(TIMEOUT_EXECUTOR::schedule);
    }

    AiRequestTimeoutControllerFactory(AiRequestTimeoutController.TimeoutScheduler timeoutScheduler) {
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
    }

    AiRequestTimeoutController create(AiRequest request, AiRequestHandleImpl handle) {
        return new AiRequestTimeoutController(handle, timeoutScheduler, request.getTimeout());
    }
}
