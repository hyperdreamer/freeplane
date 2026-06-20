package org.freeplane.plugin.ai.chat.request;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.MindMap;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestOptions;
import org.freeplane.api.ai.AiRequestRejectedException;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class ScriptAiRequestServiceTest {

    @Test
    public void askAi_delegatesToRequestStarterOnUiDispatcherWithResolvedDefaults() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<ResolvedAiRequest> seenRequest = new AtomicReference<ResolvedAiRequest>();
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
                seenRequest.set(request);
                started.countDown();
            },
            promptName -> null,
            Runnable::run);
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(1))
            .mode(AiRequestMode.HIDDEN)
            .systemMessage(" system ")
            .profile(" reviewer ", " profile message ")
            .build();

        uut.askAi("Prompt", options, result -> {
        });

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seenRequest.get().getPromptText()).isEqualTo("Prompt");
        assertThat(seenRequest.get().getPromptDisplayName()).isNull();
        assertThat(seenRequest.get().getTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(seenRequest.get().getMode()).isEqualTo(AiRequestMode.HIDDEN);
        assertThat(seenRequest.get().getModelSelection()).isEqualTo(AiModelSelection.current());
        assertThat(seenRequest.get().getToolAvailability()).isEqualTo(AiToolAvailability.CURRENT);
        assertThat(seenRequest.get().getSystemMessage()).isEqualTo("system");
        assertThat(seenRequest.get().getProfileName()).isEqualTo("reviewer");
        assertThat(seenRequest.get().getProfileMessage()).isEqualTo("profile message");
    }

    @Test
    public void askAi_rejectsMissingModeSynchronously() {
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
            },
            promptName -> null,
            Runnable::run);

        assertThatThrownBy(() -> uut.askAi(
            "Prompt",
            AiRequestOptions.builder().timeout(Duration.ofSeconds(1)).build(),
            result -> {
            }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("options.mode");
    }

    @Test
    public void askAi_cancelsThroughReturnedHandle() throws Exception {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> handle.setCancelAction(() -> handle.complete(
                new org.freeplane.api.ai.AiRequestResult(AiRequestStatus.CANCELLED, null, null))),
            promptName -> null,
            Runnable::run);

        AiRequestHandle handle = uut.askAi(
            "Prompt",
            AiRequestOptions.builder().timeout(Duration.ofSeconds(1)).mode(AiRequestMode.HIDDEN).build(),
            result -> {
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

    @Test
    public void runAiPromptWithTimeoutOnly_usesSavedPromptDefaults() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<ResolvedAiRequest> seenRequest = new AtomicReference<ResolvedAiRequest>();
        AiPrompt savedPrompt = new AiPrompt(
            "Rewrite",
            "Rewrite the selection",
            false,
            "openrouter|openai/gpt-4.1-mini",
            "reading");
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
                seenRequest.set(request);
                started.countDown();
            },
            promptName -> savedPrompt.copy(),
            Runnable::run);

        uut.runAiPrompt(" Rewrite ", Duration.ofSeconds(30), result -> {
        });

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seenRequest.get().getPromptText()).isEqualTo("Rewrite the selection");
        assertThat(seenRequest.get().getPromptDisplayName()).isEqualTo("Rewrite");
        assertThat(seenRequest.get().getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(seenRequest.get().getMode()).isEqualTo(AiRequestMode.HIDDEN_WITH_CANCEL_DIALOG);
        assertThat(seenRequest.get().getModelSelection())
            .isEqualTo(AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini"));
        assertThat(seenRequest.get().getToolAvailability()).isEqualTo(AiToolAvailability.READING);
    }

    @Test
    public void runAiPromptWithOptions_preservesSavedPromptTextAndAppliesOverrides() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<ResolvedAiRequest> seenRequest = new AtomicReference<ResolvedAiRequest>();
        MindMap mindMap = mock(MindMap.class);
        AiSelectionOverride selectionOverride = new AiSelectionOverride(mindMap, Collections.singletonList("ID_1"));
        AiPrompt savedPrompt = new AiPrompt(
            "Rewrite",
            "Rewrite the selection",
            true,
            "openrouter|openai/gpt-4.1-mini",
            "disabled");
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
                seenRequest.set(request);
                started.countDown();
            },
            promptName -> savedPrompt.copy(),
            Runnable::run);
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(30))
            .mode(AiRequestMode.ADD_TO_CHAT)
            .modelSelection(AiModelSelection.current())
            .toolAvailability(AiToolAvailability.CURRENT)
            .selectionOverride(selectionOverride)
            .systemMessage("saved system")
            .profile("saved profile")
            .build();

        uut.runAiPrompt("Rewrite", options, result -> {
        });

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seenRequest.get().getPromptText()).isEqualTo("Rewrite the selection");
        assertThat(seenRequest.get().getPromptDisplayName()).isEqualTo("Rewrite");
        assertThat(seenRequest.get().getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(seenRequest.get().getMode()).isEqualTo(AiRequestMode.ADD_TO_CHAT);
        assertThat(seenRequest.get().getModelSelection()).isEqualTo(AiModelSelection.current());
        assertThat(seenRequest.get().getToolAvailability()).isEqualTo(AiToolAvailability.CURRENT);
        assertThat(seenRequest.get().getSelectionOverride()).isSameAs(selectionOverride);
        assertThat(seenRequest.get().getSystemMessage()).isEqualTo("saved system");
        assertThat(seenRequest.get().getProfileName()).isEqualTo("saved profile");
        assertThat(seenRequest.get().getProfileMessage()).isNull();
    }

    @Test
    public void runAiPrompt_rejectsMissingSavedPromptSynchronously() {
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
            },
            promptName -> null,
            Runnable::run);
        AtomicInteger callbackCount = new AtomicInteger();

        assertThatThrownBy(() -> uut.runAiPrompt("Missing", Duration.ofSeconds(30), result -> callbackCount.incrementAndGet()))
            .isInstanceOf(AiRequestRejectedException.class)
            .satisfies(error -> assertThat(((AiRequestRejectedException) error).getStatus())
                .isEqualTo(AiRequestStatus.PROMPT_NOT_FOUND));

        assertThat(callbackCount.get()).isZero();
    }

    @Test
    public void runAiPrompt_rejectsBlankPromptNameSynchronously() {
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> {
            },
            promptName -> null,
            Runnable::run);

        assertThatThrownBy(() -> uut.runAiPrompt("   ", Duration.ofSeconds(30), result -> {
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("promptName");
    }

    @Test
    public void runAiPrompt_malformedSavedExplicitModelSelectionCompletesConfigurationErrorWithoutStartingRequest()
        throws Exception {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AtomicReference<String> seenDetail = new AtomicReference<String>();
        AtomicInteger startCount = new AtomicInteger();
        AiPrompt savedPrompt = new AiPrompt(
            "Rewrite",
            "Rewrite the selection",
            true,
            "broken-selection",
            "reading");
        ScriptAiRequestService uut = new ScriptAiRequestService(
            (request, handle) -> startCount.incrementAndGet(),
            promptName -> savedPrompt.copy(),
            Runnable::run);

        AiRequestHandle handle = uut.runAiPrompt(
            "Rewrite",
            Duration.ofSeconds(30),
            result -> {
                seenStatus.set(result.getStatus());
                seenDetail.set(result.getDetail());
                callbackLatch.countDown();
            });

        assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.CONFIGURATION_ERROR);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.CONFIGURATION_ERROR);
        assertThat(seenDetail.get()).contains("Malformed saved AI prompt model selection");
        assertThat(startCount.get()).isZero();
    }
}
