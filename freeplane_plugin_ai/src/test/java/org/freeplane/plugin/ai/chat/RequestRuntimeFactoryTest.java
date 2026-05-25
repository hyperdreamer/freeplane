package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.memory.ChatMemory;
import java.util.concurrent.atomic.AtomicInteger;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.prompt.HiddenAiRequestObserverFactory;
import org.freeplane.plugin.ai.prompt.HiddenPromptRequestRunner;
import org.freeplane.plugin.ai.prompt.HiddenPromptRequestRunnerFactory;
import org.freeplane.features.text.TextController;
import org.junit.Test;

public class RequestRuntimeFactoryTest {

    @Test
    public void chatPromptRunnerFactoryCreatesFreshShownAndHiddenRunners() {
        AtomicInteger hiddenRunnerCreateCount = new AtomicInteger();
        HiddenPromptRequestRunnerFactory hiddenRunnerFactory = new HiddenPromptRequestRunnerFactory() {
            @Override
            public HiddenPromptRequestRunner create(HiddenPromptRequestRunner.Callbacks callbacks) {
                hiddenRunnerCreateCount.incrementAndGet();
                return super.create(callbacks);
            }
        };
        ChatPromptRunnerFactory factory = new ChatPromptRunnerFactory(
            null,
            null,
            null,
            mock(AvailableMaps.class),
            new AiPromptRequestComposer(mock(AvailableMaps.class), mock(TextController.class)),
            (sessionId, service, preparedMessage, requestFlow, requestTokenUsageTracker, requestCallbacks) -> {
            },
            hiddenRunnerFactory,
            new AiPromptProgressDialogFactory());

        ChatPromptRunner shownOne = factory.createShown(
            mock(ChatMemory.class),
            (mapIdentifier, mapModel) -> {
            },
            mock(ChatRequestFlow.class),
            new ChatTokenUsageTracker(totals -> {
            }),
            LiveChatSessionId.create());
        ChatPromptRunner shownTwo = factory.createShown(
            mock(ChatMemory.class),
            (mapIdentifier, mapModel) -> {
            },
            mock(ChatRequestFlow.class),
            new ChatTokenUsageTracker(totals -> {
            }),
            LiveChatSessionId.create());
        ChatPromptRunner hiddenOne = factory.createHidden();
        ChatPromptRunner hiddenTwo = factory.createHidden();

        assertThat(shownOne).isNotSameAs(shownTwo);
        assertThat(hiddenOne).isNotSameAs(hiddenTwo);
        assertThat(hiddenOne.hiddenPromptRequestRunner()).isNotSameAs(hiddenTwo.hiddenPromptRequestRunner());
        assertThat(hiddenRunnerCreateCount.get()).isEqualTo(4);
    }

    @Test
    public void visibleAndHiddenBridgeFactoriesCreateFreshInstancesPerRequest() {
        AiRequestHandleImpl firstHandle = new AiRequestHandleImpl(Runnable::run, result -> {
        });
        AiRequestHandleImpl secondHandle = new AiRequestHandleImpl(Runnable::run, result -> {
        });

        VisibleAiRequestCallbacksFactory visibleFactory = new VisibleAiRequestCallbacksFactory();
        HiddenAiRequestObserverFactory hiddenFactory = new HiddenAiRequestObserverFactory();

        assertThat(visibleFactory.create(firstHandle)).isNotSameAs(visibleFactory.create(secondHandle));
        assertThat(hiddenFactory.create(firstHandle)).isNotSameAs(hiddenFactory.create(secondHandle));
    }

    @Test
    public void chatRequestFlowAndAddToChatDispatchJobFactoriesCreateFreshInstances() {
        ChatRequestFlowFactory flowFactory = new ChatRequestFlowFactory();
        ChatTokenUsageTracker tracker = new ChatTokenUsageTracker(totals -> {
        });
        ChatRequestFlow firstFlow = flowFactory.create(new NoOpRequestCallbacks(), tracker);
        ChatRequestFlow secondFlow = flowFactory.create(new NoOpRequestCallbacks(), tracker);

        assertThat(firstFlow).isNotSameAs(secondFlow);

        AIChatPanel panel = mock(AIChatPanel.class);
        AddToChatDispatchJobFactory jobFactory = new AddToChatDispatchJobFactory(panel);
        org.freeplane.api.ai.AiRequest request = new org.freeplane.api.ai.AiRequest(
            "Prompt",
            org.freeplane.api.ai.AiModelSelection.current(),
            org.freeplane.api.ai.AiToolAvailability.CURRENT,
            org.freeplane.api.ai.AiRequestMode.ADD_TO_CHAT,
            java.time.Duration.ofSeconds(10));
        AddToChatDispatchJob firstJob = jobFactory.create(
            request,
            new AiRequestHandleImpl(Runnable::run, result -> {
            }),
            mock(AiRequestTimeoutController.class));
        AddToChatDispatchJob secondJob = jobFactory.create(
            request,
            new AiRequestHandleImpl(Runnable::run, result -> {
            }),
            mock(AiRequestTimeoutController.class));

        assertThat(firstJob).isNotSameAs(secondJob);
    }

    private static class NoOpRequestCallbacks implements ChatRequestFlow.RequestCallbacks {
        @Override public void onRequestStarted() {}
        @Override public void onRequestFinished() {}
        @Override public void onUserTextRestored(String userText) {}
        @Override public void onRequestFailed(String userText, String errorMessage) {}
        @Override public void onRequestCancelled() {}
        @Override public void onAssistantResponse(String text) {}
        @Override public void onAssistantError(String text) {}
        @Override public void synchronizeTranscriptWithMemory() {}
        @Override public void rebuildHistoryFromTranscript() {}
        @Override public void onPostResponseEviction() {}
        @Override public void refreshTokenCounters() {}
        @Override public boolean isToolCallHistoryVisible() { return true; }
        @Override public void onToolSummaryAppended(ChatMemoryRenderEntry entry) {}
    }
}
