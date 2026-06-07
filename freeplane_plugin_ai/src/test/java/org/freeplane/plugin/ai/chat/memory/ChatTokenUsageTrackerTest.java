package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatTokenUsageTrackerTest {
    @Test
    public void modelResponseModeShowsLatestUsage() {
        AtomicReference<ChatUsageTotals> totalsReference = new AtomicReference<>();
        ChatTokenUsageTracker uut = new ChatTokenUsageTracker(totalsReference::set);
        uut.setCounterMode(ChatTokenCounterMode.MODEL_RESPONSE, "Model response only");

        uut.recordProviderUsage(new TokenUsage(400, 500));
        uut.recordProviderUsage(new TokenUsage(1000, 100));
        uut.refreshTotals(null, "input", "output");

        ChatUsageTotals totals = totalsReference.get();
        assertThat(totals.getOutputTokenCount()).isEqualTo(100L);
        assertThat(totals.getInputTokenCount()).isEqualTo(1000L);
        assertThat(totals.getLabel()).isEqualTo("Model response only");
    }

    @Test
    public void resetTotals_clearsCounts() {
        AtomicReference<ChatUsageTotals> totalsReference = new AtomicReference<>();
        ChatTokenUsageTracker uut = new ChatTokenUsageTracker(totalsReference::set);
        uut.setCounterMode(ChatTokenCounterMode.MODEL_RESPONSE, "Model response only");

        uut.recordProviderUsage(new TokenUsage(3, 7));
        uut.resetTotals();
        uut.refreshTotals(null, "input", "output");

        ChatUsageTotals totals = totalsReference.get();
        assertThat(totals.getInputTokenCount()).isZero();
        assertThat(totals.getOutputTokenCount()).isZero();
    }

    @Test
    public void undoRedoUpdatesModelResponse() {
        AtomicReference<ChatUsageTotals> totalsReference = new AtomicReference<>();
        ChatTokenUsageTracker uut = new ChatTokenUsageTracker(totalsReference::set);
        uut.setCounterMode(ChatTokenCounterMode.MODEL_RESPONSE, "Model response only");

        uut.recordProviderUsage(new TokenUsage(400, 500));
        uut.recordProviderUsage(new TokenUsage(1000, 100));
        uut.undoLastResponse();
        uut.refreshTotals(null, "input", "output");

        ChatUsageTotals afterUndo = totalsReference.get();
        assertThat(afterUndo.getOutputTokenCount()).isEqualTo(500L);
        assertThat(afterUndo.getInputTokenCount()).isEqualTo(400L);

        uut.redoLastResponse();
        uut.refreshTotals(null, "input", "output");
        ChatUsageTotals afterRedo = totalsReference.get();
        assertThat(afterRedo.getOutputTokenCount()).isEqualTo(100L);
        assertThat(afterRedo.getInputTokenCount()).isEqualTo(1000L);
    }

    @Test
    public void restoreState_rehydratesTotals() {
        AtomicReference<ChatUsageTotals> totalsReference = new AtomicReference<>();
        ChatTokenUsageTracker uut = new ChatTokenUsageTracker(totalsReference::set);
        uut.setCounterMode(ChatTokenCounterMode.MODEL_RESPONSE, "Model response only");

        uut.recordProviderUsage(new TokenUsage(400, 500));
        ChatTokenUsageState state = uut.snapshotState();
        uut.resetTotals();

        uut.restoreState(state);
        uut.refreshTotals(null, "input", "output");

        ChatUsageTotals totals = totalsReference.get();
        assertThat(totals.getOutputTokenCount()).isEqualTo(500L);
        assertThat(totals.getInputTokenCount()).isEqualTo(400L);
    }

    @Test
    public void hiddenModeHidesTotals() {
        AtomicReference<ChatUsageTotals> totalsReference = new AtomicReference<>();
        ChatTokenUsageTracker uut = new ChatTokenUsageTracker(totalsReference::set);
        uut.setCounterMode(ChatTokenCounterMode.HIDDEN, "Hidden");

        uut.refreshTotals(null, "input", "output");

        ChatUsageTotals totals = totalsReference.get();
        assertThat(totals.isVisible()).isFalse();
        assertThat(totals.getLabel()).isNull();
    }

    @Test
    public void estimateModeUsesMemoryValues() {
        AtomicReference<ChatUsageTotals> totalsReference = new AtomicReference<>();
        ChatTokenUsageTracker uut = new ChatTokenUsageTracker(totalsReference::set);
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.builder()
            .maxTokens(1000)
            .tokenEstimatorModelNameProvider(() -> "gpt-4o-mini")
            .build();
        memory.add(UserMessage.from("hello"));
        memory.add(AiMessage.from("answer"));

        uut.setCounterMode(ChatTokenCounterMode.CONTEXT_WINDOW, "Context window estimates");
        ChatUsageTotals expected = memory.estimateTokenUsageForActiveWindow();
        uut.refreshTotals(memory, "input", "output");

        ChatUsageTotals totals = totalsReference.get();
        assertThat(totals.getOutputTokenCount()).isEqualTo(expected.getOutputTokenCount());
        assertThat(totals.getInputTokenCount()).isEqualTo(expected.getInputTokenCount());
        assertThat(totals.getLabel()).isEqualTo("Context window estimates");
    }
}
