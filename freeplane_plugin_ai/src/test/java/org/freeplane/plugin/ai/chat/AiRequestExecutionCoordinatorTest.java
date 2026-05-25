package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequest;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiToolAvailability;
import org.junit.Test;

public class AiRequestExecutionCoordinatorTest {

    @Test
    public void shownRequestsDoNotRejectBusySolelyBecauseRoutingIsRepeated() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AiRequestTimeoutControllerFactory timeoutFactory = mock(AiRequestTimeoutControllerFactory.class);
        AiRequestTimeoutController firstTimeout = mock(AiRequestTimeoutController.class);
        AiRequestTimeoutController secondTimeout = mock(AiRequestTimeoutController.class);
        when(timeoutFactory.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(firstTimeout, secondTimeout);
        AiRequestExecutionCoordinator uut = new AiRequestExecutionCoordinator(
            aiChatPanel,
            new AddToChatDispatchJobFactory(aiChatPanel),
            timeoutFactory,
            new RecordingExecutor());
        AiRequest firstRequest = request(AiRequestMode.SHOW_IN_CHAT);
        AiRequest secondRequest = request(AiRequestMode.SHOW_IN_CHAT);
        AiRequestHandleImpl firstHandle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AiRequestHandleImpl secondHandle = new AiRequestHandleImpl(Runnable::run, result -> {
        });

        uut.askAi(firstRequest, firstHandle);
        uut.askAi(secondRequest, secondHandle);

        verify(aiChatPanel).startShownAiRequest(firstRequest, firstHandle, firstTimeout);
        verify(aiChatPanel).startShownAiRequest(secondRequest, secondHandle, secondTimeout);
        assertThat(firstHandle.isDone()).isFalse();
        assertThat(secondHandle.isDone()).isFalse();
    }

    @Test
    public void hiddenModesRouteToExpectedDialogVisibility() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AiRequestTimeoutControllerFactory timeoutFactory = mock(AiRequestTimeoutControllerFactory.class);
        AiRequestTimeoutController firstTimeout = mock(AiRequestTimeoutController.class);
        AiRequestTimeoutController secondTimeout = mock(AiRequestTimeoutController.class);
        when(timeoutFactory.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(firstTimeout, secondTimeout);
        AiRequestExecutionCoordinator uut = new AiRequestExecutionCoordinator(
            aiChatPanel,
            new AddToChatDispatchJobFactory(aiChatPanel),
            timeoutFactory,
            new RecordingExecutor());
        AiRequestHandleImpl hiddenHandle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AiRequestHandleImpl dialogHandle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AiRequest hiddenRequest = request(AiRequestMode.HIDDEN);
        AiRequest dialogRequest = request(AiRequestMode.HIDDEN_WITH_CANCEL_DIALOG);

        uut.askAi(hiddenRequest, hiddenHandle);
        uut.askAi(dialogRequest, dialogHandle);

        verify(aiChatPanel).startHiddenAiRequest(hiddenRequest, hiddenHandle, false, firstTimeout);
        verify(aiChatPanel).startHiddenAiRequest(dialogRequest, dialogHandle, true, secondTimeout);
    }

    @Test
    public void addToChatRequestsCreateDistinctJobsAndSubmitThemInOrder() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        RecordingExecutor executor = new RecordingExecutor();
        AiRequestTimeoutControllerFactory timeoutFactory = mock(AiRequestTimeoutControllerFactory.class);
        when(timeoutFactory.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(AiRequestTimeoutController.class), mock(AiRequestTimeoutController.class));
        AiRequestExecutionCoordinator uut = new AiRequestExecutionCoordinator(
            aiChatPanel,
            new AddToChatDispatchJobFactory(aiChatPanel),
            timeoutFactory,
            executor);

        uut.askAi(request(AiRequestMode.ADD_TO_CHAT), new AiRequestHandleImpl(Runnable::run, result -> {
        }));
        uut.askAi(request(AiRequestMode.ADD_TO_CHAT), new AiRequestHandleImpl(Runnable::run, result -> {
        }));

        assertThat(executor.submitted).hasSize(2);
        assertThat(executor.submitted.get(0)).isInstanceOf(AddToChatDispatchJob.class);
        assertThat(executor.submitted.get(1)).isInstanceOf(AddToChatDispatchJob.class);
        assertThat(executor.submitted.get(0)).isNotSameAs(executor.submitted.get(1));
    }

    private AiRequest request(AiRequestMode mode) {
        return new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            mode,
            Duration.ofSeconds(10));
    }

    private static class RecordingExecutor extends AbstractExecutorService {
        private final List<Runnable> submitted = new ArrayList<Runnable>();

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return new ArrayList<Runnable>(submitted);
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            submitted.add(command);
        }

        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            submitted.add(task);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
