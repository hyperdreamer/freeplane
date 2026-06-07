package org.freeplane.plugin.ai.chat.request;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestStatus;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiRequestHandleImplTest {

    @Test
    public void cancelBeforeCancelActionRunsActionOnceWhenActionIsLaterAttached() {
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AtomicInteger cancelCalls = new AtomicInteger();

        handle.cancel();
        handle.setCancelAction(cancelCalls::incrementAndGet);

        assertThat(handle.isCancelled()).isTrue();
        assertThat(cancelCalls.get()).isEqualTo(1);
    }

    @Test
    public void completeDispatchesCallbackOnlyOnce() {
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
            callbackCalls.incrementAndGet();
            seenStatus.set(result.getStatus());
        });

        handle.complete(new AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));
        handle.complete(new AiRequestResult(AiRequestStatus.FAILED, null, "failure"));

        assertThat(callbackCalls.get()).isEqualTo(1);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
    }

    @Test
    public void completionCleanupRunsBeforeCallback() {
        AtomicInteger ordering = new AtomicInteger();
        AtomicInteger cleanupStep = new AtomicInteger();
        AtomicInteger callbackStep = new AtomicInteger();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result ->
            callbackStep.set(ordering.incrementAndGet()));
        handle.setCompletionCleanup(() -> cleanupStep.set(ordering.incrementAndGet()));

        handle.complete(new AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));

        assertThat(cleanupStep.get()).isEqualTo(1);
        assertThat(callbackStep.get()).isEqualTo(2);
    }
}
