package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequest;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiToolAvailability;
import org.junit.Test;

public class ScriptAiRequestServiceTest {

    @Test
    public void askAi_delegatesToRequestStarterOnUiDispatcher() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<AiRequest> seenRequest = new AtomicReference<AiRequest>();
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
                seenRequest.set(request);
                started.countDown();
            },
            Runnable::run);
        AiRequest request = request(Duration.ofSeconds(1));

        uut.askAi(request, result -> {
        });

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seenRequest.get()).isSameAs(request);
    }

    @Test
    public void askAi_cancelsThroughReturnedHandle() throws Exception {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> handle.setCancelAction(() -> handle.complete(
                new org.freeplane.api.ai.AiRequestResult(AiRequestStatus.CANCELLED, null, null))),
            Runnable::run);

        org.freeplane.api.ai.AiRequestHandle handle = uut.askAi(request(Duration.ofSeconds(1)), result -> {
            seenStatus.set(result.getStatus());
            callbackLatch.countDown();
        });
        handle.cancel();

        assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(handle.isDone()).isTrue();
        assertThat(handle.isCancelled()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.CANCELLED);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.CANCELLED);
    }

    private AiRequest request(Duration timeout) {
        return new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            AiRequestMode.HIDDEN,
            timeout);
    }
}
