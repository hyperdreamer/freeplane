package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
            () -> null,
            sessionId -> null,
            () -> ToolAvailabilityLevel.EDITING,
            sessionId -> null,
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
        ChatPromptRunner hiddenOne = factory.createHidden(LiveChatSessionId.create());
        ChatPromptRunner hiddenTwo = factory.createHidden(LiveChatSessionId.create());

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
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            (AiSelectionOverride) null);
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
