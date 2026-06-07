package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.freeplane.core.util.LogUtils;

public class ChatTokenUsageTracker {
    private final Consumer<ChatUsageTotals> totalsConsumer;
    private final List<Long> outputByTurn = new ArrayList<>();
    private final List<Long> inputByTurn = new ArrayList<>();
    private int currentTurnCount;
    private ChatTokenCounterMode counterMode = ChatTokenCounterMode.HIDDEN;
    private String counterModeLabel;

    public ChatTokenUsageTracker(Consumer<ChatUsageTotals> totalsConsumer) {
        this.totalsConsumer = Objects.requireNonNull(totalsConsumer, "totalsConsumer");
    }

    public synchronized void setCounterMode(ChatTokenCounterMode counterMode) {
        setCounterMode(counterMode, null);
    }

    public synchronized void setCounterMode(ChatTokenCounterMode counterMode, String counterModeLabel) {
        this.counterMode = counterMode == null ? ChatTokenCounterMode.HIDDEN : counterMode;
        this.counterModeLabel = counterModeLabel;
    }

    public synchronized void recordProviderUsage(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return;
        }
        Integer inputCount = tokenUsage.inputTokenCount();
        Integer outputCount = tokenUsage.outputTokenCount();
        if (inputCount == null && outputCount == null) {
            return;
        }
        truncateHistoryAfterCurrentTurn();
        outputByTurn.add(outputCount == null ? null : outputCount.longValue());
        inputByTurn.add(inputCount == null ? null : inputCount.longValue());
        currentTurnCount = outputByTurn.size();
    }

    public void logToolExecuted(ToolExecutedEvent event) {
        ToolExecutionRequest request = event.request();
        LogUtils.info(buildToolCallLogMessage(request));
    }

    public synchronized void resetTotals() {
        outputByTurn.clear();
        inputByTurn.clear();
        currentTurnCount = 0;
        publishTotals(calculateTotals(null));
    }

    public synchronized ChatTokenUsageState snapshotState() {
        return new ChatTokenUsageState(outputByTurn, inputByTurn, currentTurnCount);
    }

    public synchronized void restoreState(ChatTokenUsageState state) {
        if (state == null) {
            resetTotals();
            return;
        }
        outputByTurn.clear();
        outputByTurn.addAll(state.getOutputByTurn());
        inputByTurn.clear();
        inputByTurn.addAll(state.getInputByTurn());
        int maxSize = Math.min(outputByTurn.size(), inputByTurn.size());
        if (outputByTurn.size() > maxSize) {
            outputByTurn.subList(maxSize, outputByTurn.size()).clear();
        }
        if (inputByTurn.size() > maxSize) {
            inputByTurn.subList(maxSize, inputByTurn.size()).clear();
        }
        currentTurnCount = Math.max(0, Math.min(state.getCurrentTurnCount(), maxSize));
    }

    public synchronized void refreshTotals(AssistantProfileChatMemory memory, String inputLabel, String outputLabel) {
        ChatUsageTotals totals = calculateTotals(memory)
            .withLabel(counterModeLabel)
            .withInputOutputLabels(inputLabel, outputLabel);
        publishTotals(totals);
    }

    public synchronized void undoLastResponse() {
        if (currentTurnCount <= 0) {
            return;
        }
        currentTurnCount--;
    }

    public synchronized void redoLastResponse() {
        if (currentTurnCount >= outputByTurn.size()) {
            return;
        }
        currentTurnCount++;
    }

    private void publishTotals(ChatUsageTotals totals) {
        totalsConsumer.accept(totals);
    }

    private void truncateHistoryAfterCurrentTurn() {
        if (currentTurnCount >= outputByTurn.size()) {
            return;
        }
        outputByTurn.subList(currentTurnCount, outputByTurn.size()).clear();
        inputByTurn.subList(currentTurnCount, inputByTurn.size()).clear();
    }

    private static String buildToolCallLogMessage(ToolExecutionRequest request) {
        return "Tool call: " + request.name();
    }

    private ChatUsageTotals calculateTotals(AssistantProfileChatMemory memory) {
        if (counterMode == ChatTokenCounterMode.HIDDEN) {
            return ChatUsageTotals.hidden();
        }
        if (counterMode == ChatTokenCounterMode.MODEL_RESPONSE) {
            return totalsFromProviderUsage().withLabel(counterModeLabel);
        }
        if (memory == null) {
            return new ChatUsageTotals(0L, 0L).withLabel(counterModeLabel);
        }
        if (counterMode == ChatTokenCounterMode.CONTEXT_WINDOW) {
            return memory.estimateTokenUsageForActiveWindow().withLabel(counterModeLabel);
        }
        return memory.estimateTokenUsageForFullConversation().withLabel(counterModeLabel);
    }

    private ChatUsageTotals totalsFromProviderUsage() {
        if (currentTurnCount <= 0) {
            return new ChatUsageTotals(0L, 0L);
        }
        int index = Math.min(currentTurnCount - 1, outputByTurn.size() - 1);
        Long output = outputByTurn.get(index);
        Long input = inputByTurn.get(index);
        long outputCount = output == null ? 0L : output;
        long inputCount = input == null ? 0L : input;
        return new ChatUsageTotals(inputCount, outputCount);
    }
}
