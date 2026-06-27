package org.freeplane.plugin.ai.chat.request;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiModelConfiguration;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.junit.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class AddToChatDispatchJobTest {

    @Test
    public void cancelBeforeDispatchCompletesCancelledWithoutStartingRequest() throws Exception {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        ResolvedAiRequest request = request();
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
            seenStatus.set(result.getStatus());
            callbackLatch.countDown();
        });
        AddToChatDispatchJob job = new AddToChatDispatchJob(
            aiChatPanel,
            request,
            handle,
            mock(AiRequestTimeoutController.class));
        handle.setCancelAction(job::cancel);

        handle.cancel();
        job.run();

        assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(handle.isDone()).isTrue();
        assertThat(handle.isCancelled()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.CANCELLED);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.CANCELLED);
        verifyNoInteractions(aiChatPanel);
    }

    @Test
    public void timeoutBeforeDispatchCompletesTimedOutWithoutStartingRequest() throws Exception {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AddToChatDispatchJob job = new AddToChatDispatchJob(
            aiChatPanel,
            request(),
            handle,
            mock(AiRequestTimeoutController.class));
        handle.setCancelAction(job::cancel);

        handle.markTimedOut();
        handle.cancel();
        job.run();

        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.TIMED_OUT);
        verifyNoInteractions(aiChatPanel);
    }

    @Test
    public void armsTimeoutOnlyAfterVisibleRequestStartsAtDispatch() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        org.mockito.Mockito.when(aiChatPanel.startAddToChatAiRequestAtDispatch(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(requestFlow);
        AiRequestTimeoutController timeoutController = mock(AiRequestTimeoutController.class);
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AddToChatDispatchJob job = new AddToChatDispatchJob(aiChatPanel, request(), handle, timeoutController);

        verifyNoInteractions(aiChatPanel, timeoutController);
        job.run();

        InOrder inOrder = inOrder(aiChatPanel, timeoutController);
        inOrder.verify(aiChatPanel).startAddToChatAiRequestAtDispatch(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.same(handle));
        inOrder.verify(timeoutController).armAfterStart();
    }

    private ResolvedAiRequest request() {
        return new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            (AiSelectionOverride) null,
            null,
            false,
            null,
            null);
    }
}
