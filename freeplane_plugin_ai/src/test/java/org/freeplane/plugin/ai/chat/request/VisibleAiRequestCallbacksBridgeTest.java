package org.freeplane.plugin.ai.chat.request;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiRequestStatus;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VisibleAiRequestCallbacksBridgeTest {

    @Test
    public void responseAppendedCompletesHandleWithSucceededResponse() {
        AtomicReference<org.freeplane.api.ai.AiRequestResult> seenResult =
            new AtomicReference<org.freeplane.api.ai.AiRequestResult>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, seenResult::set);
        VisibleAiRequestCallbacksBridge uut = new VisibleAiRequestCallbacksBridge(handle);

        uut.onResponseAppended("response");

        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(seenResult.get().getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(seenResult.get().getResponse()).isEqualTo("response");
    }

    @Test
    public void failedResponseMapsProviderFailureStatus() {
        AtomicReference<org.freeplane.api.ai.AiRequestResult> seenResult =
            new AtomicReference<org.freeplane.api.ai.AiRequestResult>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, seenResult::set);
        VisibleAiRequestCallbacksBridge uut = new VisibleAiRequestCallbacksBridge(handle);

        uut.onFailed("Prompt", "HTTP 401 unauthorized");

        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.AUTHENTICATION_ERROR);
        assertThat(seenResult.get().getDetail()).isEqualTo("HTTP 401 unauthorized");
    }

    @Test
    public void cancelledCompletesExactlyOnce() {
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
            callbackCount.incrementAndGet();
            seenStatus.set(result.getStatus());
        });
        VisibleAiRequestCallbacksBridge uut = new VisibleAiRequestCallbacksBridge(handle);

        uut.onCancelled();
        uut.onCancelled();

        assertThat(callbackCount.get()).isEqualTo(1);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.CANCELLED);
    }

    @Test
    public void cancelledAfterTimeoutReportsTimedOut() {
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result ->
            seenStatus.set(result.getStatus()));
        handle.markTimedOut();
        VisibleAiRequestCallbacksBridge uut = new VisibleAiRequestCallbacksBridge(handle);

        uut.onCancelled();

        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.TIMED_OUT);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.TIMED_OUT);
    }
}
