package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AssistantProfileChatMemoryTest {

    @Test
    public void messages_ordersSystemMessagesBySlot() {
        AssistantProfileChatMemory uut = createMemory(500);

        uut.add(UserMessage.from("hello"));
        uut.add(new TranscriptHiddenSystemMessage("hidden"));
        uut.add(new AssistantProfileSwitchMessage("profile", "profile"));
        uut.add(new GeneralSystemMessage("general"));

        List<ChatMessage> messages = uut.messages();
        assertThat(messages).hasSize(6);
        assertThat(messages.get(0)).isInstanceOf(GeneralSystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText())
            .isEqualTo("hello");
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(2)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "hidden");
        assertThat(messages.get(3)).isInstanceOf(InstructionAckMessage.class);
        assertThat(((AiMessage) messages.get(3)).text()).isEqualTo("ok");
        assertThat(messages.get(4)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(4)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile profile.");
        assertThat(messages.get(5)).isInstanceOf(InstructionAckMessage.class);
        assertThat(((AiMessage) messages.get(5)).text()).isEqualTo("ok");
    }

    @Test
    public void capacity_excludesTranscriptHiddenAndRemovedForSpace() {
        int maxTokens = estimateTokens(
            UserMessage.from("second"),
            AiMessage.from("second answer"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(new GeneralSystemMessage("general"));
        uut.add(new TranscriptHiddenSystemMessage("hidden"));
        uut.add(UserMessage.from("first"));
        uut.add(AiMessage.from("first answer"));
        uut.add(UserMessage.from("second"));
        uut.add(AiMessage.from("second answer"));
        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        List<ChatMessage> messages = uut.messages();
        assertThat(messages.get(0)).isInstanceOf(GeneralSystemMessage.class);
        assertThat(messages)
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .doesNotContain(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "hidden")
            .doesNotContain("first")
            .contains("second");
        assertThat(messages)
            .extracting(message -> message instanceof AiMessage ? ((AiMessage) message).text() : null)
            .contains("second answer");
    }

    @Test
    public void assistantProfileMessagesDropWhenNoConversationMessagesRemain() {
        int maxTokens = Math.max(1, estimateTokens(
            UserMessage.from("first"),
            AiMessage.from("answer")) - 1);
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(new AssistantProfileSwitchMessage("profile", "profile"));
        uut.add(UserMessage.from("first"));
        uut.add(AiMessage.from("answer"));
        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        List<ChatMessage> messages = uut.messages();
        assertThat(messages)
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("first");
    }

    @Test
    public void contextBoundaryMarkerInsertedOnceWhenWindowMoves() {
        int maxTokens = estimateTokens(
            UserMessage.from("next"),
            AiMessage.from("third"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(UserMessage.from("first first first first first first first first first first "
            + "first first first first first first first first first first first first first first first first"));
        uut.add(AiMessage.from("second"));
        uut.add(UserMessage.from("next"));
        uut.add(AiMessage.from("third"));
        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        List<ChatMemoryRenderEntry> entries = uut.activeConversationRenderEntries();
        long markerCount = entries.stream()
            .filter(ChatMemoryRenderEntry::isToolSummary)
            .count();
        long boundaryCount = entries.stream()
            .filter(entry -> entry.chatMessage() instanceof RemovedForSpaceSystemMessage)
            .count();
        assertThat(markerCount).isZero();
        assertThat(boundaryCount).isEqualTo(1);
    }

    @Test
    public void contextBoundaryDoesNotShowToolSummaryFromRemovedTurn() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.addToolCallSummary("summary-2", ToolCaller.CHAT);
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.addToolCallSummary("summary-3", ToolCaller.CHAT);
        uut.add(AiMessage.from("a3"));

        assertThat(uut.evictOldestTurn()).isTrue();

        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("summary-2", "summary-3")
            .doesNotContain("summary-1");
    }

    @Test
    public void contextBoundaryKeepsUserMessageBeforeVisibleToolSummary() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.addToolCallSummary("summary-2", ToolCaller.CHAT);
        uut.add(AiMessage.from("a2"));

        assertThat(uut.evictOldestTurn()).isTrue();

        List<ChatMemoryRenderEntry> entries = uut.activeConversationRenderEntries();
        int markerIndex = indexOfMessage(entries, RemovedForSpaceSystemMessage.class);
        int userIndex = indexOfUserText(entries, "u2");
        int summaryIndex = indexOfSummary(entries, "summary-2");
        int assistantIndex = indexOfAiText(entries, "a2");

        assertThat(markerIndex).isGreaterThanOrEqualTo(0);
        assertThat(userIndex).isGreaterThan(markerIndex);
        assertThat(summaryIndex).isGreaterThan(userIndex);
        assertThat(assistantIndex).isGreaterThan(summaryIndex);
    }

    @Test
    public void panelConversationRenderEntriesHideMessagesBeforeContextBoundary() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        assertThat(uut.evictOldestTurn()).isTrue();

        assertThat(uut.panelConversationRenderEntries())
            .extracting(entry -> entry.chatMessage() instanceof UserMessage
                ? ((UserMessage) entry.chatMessage()).singleText()
                : null)
            .contains("u2")
            .doesNotContain("u1");
    }

    @Test
    public void panelConversationRenderEntriesPlaceBoundaryBeforeActiveTurn() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        assertThat(uut.evictOldestTurn()).isTrue();

        List<ChatMemoryRenderEntry> entries = uut.panelConversationRenderEntries();
        int markerIndex = indexOfMessage(entries, RemovedForSpaceSystemMessage.class);
        int activeUserIndex = indexOfUserText(entries, "u2");

        assertThat(markerIndex).isLessThan(activeUserIndex);
        assertThat(indexOfUserText(entries, "u1")).isLessThan(0);
    }

    @Test
    public void messagesIncludeOnlyLatestProfileSwitchInstruction() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("alpha", "Alpha"));
        uut.add(new AssistantProfileSwitchMessage("beta", "Beta"));
        uut.add(UserMessage.from("hello"));

        List<ChatMessage> messages = uut.messages();

        assertThat(messages)
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile Beta.", "hello")
            .doesNotContain(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile Alpha.");
    }

    @Test
    public void profileSwitchInstructionPreservesConversationOrderForLatestMarker() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("default", "Default"));
        uut.add(UserMessage.from("u1"));
        uut.add(new AssistantProfileSwitchMessage("a", "A"));
        uut.add(UserMessage.from("u2"));
        uut.add(new AssistantProfileSwitchMessage("default", "Default"));
        uut.add(UserMessage.from("u3"));

        List<ChatMessage> messages = uut.messages();

        List<String> userTexts = messages.stream()
            .filter(UserMessage.class::isInstance)
            .map(UserMessage.class::cast)
            .map(UserMessage::singleText)
            .collect(Collectors.toList());
        String latestDefaultMarker = MessageBuilder.CONTROL_INSTRUCTION_PREFIX
            + "Now you have the profile Default.";
        assertThat(userTexts).contains("u1", "u2", "u3", latestDefaultMarker);
        assertThat(userTexts).doesNotContain(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile A.");
        assertThat(Collections.frequency(userTexts, latestDefaultMarker)).isEqualTo(1);
        assertThat(userTexts.indexOf(latestDefaultMarker)).isLessThan(userTexts.indexOf("u3"));
    }

    @Test
    public void estimateTokenUsageExcludesControlMessages() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("p1", "Profile"));
        uut.add(new TranscriptHiddenSystemMessage("hidden"));
        uut.add(UserMessage.from("hello"));
        uut.add(AiMessage.from("response"));

        ChatUsageTotals totals = uut.estimateTokenUsageForActiveWindow();

        int expected = estimateTokens(
            UserMessage.from("hello"),
            AiMessage.from("response"));
        assertThat(totals.getInputTokenCount() + totals.getOutputTokenCount())
            .isEqualTo(expected);
    }

    @Test
    public void evictionKeepsLastUserMessageEvenWhenOverLimit() {
        AssistantProfileChatMemory uut = createMemory(1);
        uut.add(UserMessage.from("first"));
        uut.add(AiMessage.from("answer"));

        boolean evicted = uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(evicted).isFalse();
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("first");
    }

    @Test
    public void evictingToolRequestAlsoEvictsToolResults() {
        AssistantProfileChatMemory uut = createMemory(500);
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
            .id("tool-1")
            .name("test")
            .arguments("{}")
            .build();

        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from(List.of(toolRequest)));
        uut.add(ToolExecutionResultMessage.from("tool-1", "test", "result"));
        uut.add(AiMessage.from("done"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("answer"));

        assertThat(uut.evictOldestTurn()).isTrue();

        List<ChatMessage> messages = uut.messages();
        assertThat(messages).noneMatch(message -> message instanceof ToolExecutionResultMessage);
        assertThat(messages).noneMatch(message -> message instanceof AiMessage
            && ((AiMessage) message).hasToolExecutionRequests());
        assertThat(messages)
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u2")
            .doesNotContain("u1");
    }

    @Test
    public void recordTokenUsageHidesHistoricalToolCycleBeforeDroppingHistoricalDialog() {
        ToolExecutionRequest historicalRequest = toolRequest("tool-1");
        String largeToolResult = repeatedWords("history", 600);
        int visibleDialogTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"),
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        int maxTokens = visibleDialogTokens * 4;
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from(List.of(historicalRequest)));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));

        assertThat(estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from(List.of(historicalRequest)),
            ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"),
            UserMessage.from("u3"),
            AiMessage.from("a3"))).isGreaterThan(maxTokens);

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        List<ChatMessage> messages = uut.messages();
        assertThat(messages)
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2", "u3");
        assertThat(messages).noneMatch(message -> message instanceof ToolExecutionResultMessage);
        assertThat(messages).noneMatch(this::isToolRequestAiMessage);
        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .doesNotContain("summary-1");
        assertThat(uut.activeConversationRenderEntries())
            .noneMatch(entry -> entry.chatMessage() instanceof RemovedForSpaceSystemMessage);
    }

    @Test
    public void recordTokenUsageKeepsNewestProtectedToolTurnComplete() {
        ToolExecutionRequest historicalRequest = toolRequest("tool-1");
        ToolExecutionRequest latestRequest = toolRequest("tool-2");
        String largeToolResult = repeatedWords("history", 600);
        ToolExecutionResultMessage latestResult = ToolExecutionResultMessage.from("tool-2", "searchNodes", "fresh");
        int visibleAfterTrimTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from(List.of(latestRequest)),
            latestResult,
            AiMessage.from("a2"));
        int maxTokens = visibleAfterTrimTokens * 4;
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from(List.of(historicalRequest)));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from(List.of(latestRequest)));
        uut.add(latestResult);
        uut.addToolCallSummary("summary-2", ToolCaller.CHAT);
        uut.add(AiMessage.from("a2"));

        assertThat(estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from(List.of(historicalRequest)),
            ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from(List.of(latestRequest)),
            latestResult,
            AiMessage.from("a2"))).isGreaterThan(maxTokens);

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.messages()).anyMatch(this::isToolRequestAiMessage);
        assertThat(uut.messages())
            .anyMatch(message -> message instanceof ToolExecutionResultMessage
                && "tool-2".equals(((ToolExecutionResultMessage) message).id())
                && "fresh".equals(((ToolExecutionResultMessage) message).text()));
        assertThat(uut.messages())
            .noneMatch(message -> message instanceof ToolExecutionResultMessage
                && "tool-1".equals(((ToolExecutionResultMessage) message).id()));
        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("summary-2")
            .doesNotContain("summary-1");
    }

    @Test
    public void undoRestoresHistoricalToolCycleHiddenByPostResponseCompaction() {
        ToolExecutionRequest historicalRequest = toolRequest("tool-1");
        String largeToolResult = repeatedWords("history", 600);
        String latestUser = repeatedWords("u3", 40);
        String latestAssistant = repeatedWords("a3", 40);
        int maxTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from(List.of(historicalRequest)),
            ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2")) + 20;
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from(List.of(historicalRequest)));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from(latestUser));
        uut.add(AiMessage.from(latestAssistant));

        assertThat(estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from(List.of(historicalRequest)),
            ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"),
            UserMessage.from(latestUser),
            AiMessage.from(latestAssistant))).isGreaterThan(maxTokens);

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.messages())
            .noneMatch(message -> message instanceof ToolExecutionResultMessage
                && "tool-1".equals(((ToolExecutionResultMessage) message).id()));

        String restoredUserInput = uut.undo();

        assertThat(restoredUserInput).isEqualTo(latestUser);
        assertThat(uut.messages()).anyMatch(this::isToolRequestAiMessage);
        assertThat(uut.messages())
            .anyMatch(message -> message instanceof ToolExecutionResultMessage
                && "tool-1".equals(((ToolExecutionResultMessage) message).id())
                && largeToolResult.equals(((ToolExecutionResultMessage) message).text()));
        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("summary-1");
    }

    @Test
    public void undoAndRedoTrackLastCompletedTurns() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        assertThat(uut.canUndo()).isTrue();
        assertThat(uut.canRedo()).isFalse();
        assertThat(uut.undo()).isEqualTo("u2");
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1")
            .doesNotContain("u2");
        assertThat(uut.canRedo()).isTrue();

        uut.redo();

        assertThat(uut.canRedo()).isFalse();
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2");
    }

    @Test
    public void newMessageAfterUndoClearsRedoBranch() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        assertThat(uut.undo()).isEqualTo("u2");
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));

        assertThat(uut.canRedo()).isFalse();
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u3")
            .doesNotContain("u2");
    }


    @Test
    public void noEvictionOccursWithoutResponseUsage() {
        AssistantProfileChatMemory uut = createMemory(10);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2");
        assertThat(uut.activeConversationRenderEntries())
            .noneMatch(entry -> entry.chatMessage() instanceof RemovedForSpaceSystemMessage);
    }

    @Test
    public void evictingAdvancesUntilWithinLimit() {
        int maxTokens = estimateTokens(
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u3")
            .doesNotContain("u1", "u2");
    }

    @Test
    public void evictOldestTurnRemovesFirstTurn() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        boolean evicted = uut.evictOldestTurn();

        assertThat(evicted).isTrue();
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u2")
            .doesNotContain("u1");
    }

    @Test
    public void truncateConversationMessagesPreservesAssistantProfileMessageType() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("profile", "Profile"));
        int sizeAfterProfileInjection = uut.conversationMessageCount();
        uut.add(UserMessage.from("u1"));

        uut.truncateConversationMessagesTo(sizeAfterProfileInjection);

        assertThat(uut.transcriptEntriesForPersistence())
            .anyMatch(entry -> entry instanceof AssistantProfileTranscriptEntry);
    }

    @Test
    public void undoIgnoresToolSummaryMessagesWhenRestoringUserInput() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("user question"));
        uut.addToolCallSummary("searchNodes: query=\"x\"", ToolCaller.CHAT);
        uut.add(AiMessage.from("assistant answer"));

        String restoredUserInput = uut.undo();

        assertThat(restoredUserInput).isEqualTo("user question");
    }

    @Test
    public void undoSingleTurnLeavesRenderEntriesEmpty() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("profile", "Profile"));
        uut.add(UserMessage.from("hello"));
        uut.add(AiMessage.from("answer"));

        uut.undo();

        assertThat(uut.activeConversationRenderEntries()).isEmpty();
    }

    @Test
    public void undoTwoTurnsOutOfThreeKeepsFirstTurnVisible() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));

        uut.undo();
        uut.undo();

        assertThat(uut.activeConversationRenderEntries())
            .extracting(entry -> entry.chatMessage() instanceof UserMessage
                ? ((UserMessage) entry.chatMessage()).singleText()
                : null)
            .contains("u1")
            .doesNotContain("u2", "u3");
    }

    @Test
    public void undoTreatsToolRequestResultAndFinalAssistantAsSingleTurn() {
        AssistantProfileChatMemory uut = createMemory(500);
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
            .id("tool-1")
            .name("searchNodes")
            .arguments("{\"request\":{\"query\":\"root\"}}")
            .build();
        uut.add(UserMessage.from("what is root"));
        uut.add(AiMessage.from(List.of(toolRequest)));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchNodes", "Root"));
        uut.add(AiMessage.from("Root is Spec-driven development"));

        String restoredUserInput = uut.undo();

        assertThat(restoredUserInput).isEqualTo("what is root");
        assertThat(uut.activeConversationRenderEntries()).isEmpty();
    }

    @Test
    public void undoKeepsPreviousToolSummaryAndHidesUndoneSummary() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.addToolCallSummary("summary-2", ToolCaller.CHAT);
        uut.add(AiMessage.from("a2"));

        uut.undo();

        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("summary-1")
            .doesNotContain("summary-2");
    }

    @Test
    public void redoRestoresToolSummaryForRedoneTurn() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.addToolCallSummary("summary-2", ToolCaller.CHAT);
        uut.add(AiMessage.from("a2"));

        uut.undo();
        uut.redo();

        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("summary-1", "summary-2");
    }

    @Test
    public void undoRemovesProfileInstructionWhenItBelongsToOnlyTurn() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("p1", "A sayer"));
        uut.add(UserMessage.from("hi"));
        uut.add(AiMessage.from("hello"));

        uut.undo();

        assertThat(uut.activeConversationRenderEntries()).isEmpty();
    }

    @Test
    public void recordTokenUsageEvictsOldestTurnAfterResponse() {
        int maxTokens = estimateTokens(
            UserMessage.from("u2"),
            AiMessage.from("a2"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .doesNotContain("u1")
            .contains("u2");
        assertThat(uut.activeConversationRenderEntries())
            .anyMatch(entry -> entry.chatMessage() instanceof RemovedForSpaceSystemMessage);
    }

    @Test
    public void recordTokenUsageKeepsWindowWhenWithinLimit() {
        int maxTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2")) + 10;
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2");
        assertThat(uut.activeConversationRenderEntries())
            .noneMatch(entry -> entry.chatMessage() instanceof RemovedForSpaceSystemMessage);
    }

    @Test
    public void recordTokenUsageEvictsWhenTokenCountReachesHardLimit() {
        int maxTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"),
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));

        boolean evicted = uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(evicted).isTrue();
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u2", "u3")
            .doesNotContain("u1");
    }

    @Test
    public void recordTokenUsageKeepsTwoTurnBlocksWhenTheyFitHardLimit() {
        int maxTokens = estimateTokens(
            UserMessage.from("u2"),
            AiMessage.from("a2"),
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u2", "u3")
            .doesNotContain("u1");
    }

    @Test
    public void truncateConversationMessagesAdjustsTokenTotalByDelta() {
        AssistantProfileChatMemory uut = createMemory(3);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));

        uut.truncateConversationMessagesTo(2);
        uut.add(AiMessage.from("a2"));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1")
            .doesNotContain("u2");
    }

    @Test
    public void messagesExcludeEvictedOldestTurnContent() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("first question"));
        uut.add(AiMessage.from("first answer"));
        uut.add(UserMessage.from("second question"));
        uut.add(AiMessage.from("second answer"));
        assertThat(uut.evictOldestTurn()).isTrue();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .doesNotContain("first question")
            .contains("second question");
    }

    @Test
    public void evictedOldestTurnCanReturnAfterUndoWhenRangeShrinks() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("first question"));
        uut.add(AiMessage.from("first answer"));
        uut.add(UserMessage.from("second question"));
        uut.add(AiMessage.from("second answer"));
        assertThat(uut.evictOldestTurn()).isTrue();

        assertThat(uut.canUndo()).isTrue();
        uut.undo();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("first question")
            .doesNotContain("second question");
    }

    @Test
    public void undoRebalancesWindowToIncludeEarlierTurnsWhenTheyFit() {
        int maxTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));
        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        uut.undo();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2")
            .doesNotContain("u3");
    }

    @Test
    public void redoRebalancesWindowForwardWhenExpandedRangeExceedsLimit() {
        int maxTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"));
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));
        uut.onResponseTokenUsage(new TokenUsage(1, 1));
        uut.undo();

        uut.redo();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u2", "u3")
            .doesNotContain("u1");
    }

    @Test
    public void transcriptRestoreExpansionMovesBoundaryBackwardWhenUnderMax() {
        int turn2And3Tokens = estimateTokens(
            UserMessage.from("u2"),
            AiMessage.from("a2"),
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        int turn3Tokens = estimateTokens(
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        int maxTokens = Math.max(turn2And3Tokens + 10, turn3Tokens * 5);
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.markContextWindowStart();
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));
        uut.initializeUndoRedoFromMessages();

        uut.expandWindowAfterTranscriptRestoreIfUnderutilized();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2", "u3");
    }

    @Test
    public void transcriptRestoreExpansionKeepsBoundaryWhenWindowAlreadyAtMax() {
        int turn3Tokens = estimateTokens(
            UserMessage.from("u3"),
            AiMessage.from("a3"));
        int maxTokens = turn3Tokens;
        AssistantProfileChatMemory uut = createMemory(maxTokens);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));
        uut.markContextWindowStart();
        uut.add(UserMessage.from("u3"));
        uut.add(AiMessage.from("a3"));
        uut.initializeUndoRedoFromMessages();

        uut.expandWindowAfterTranscriptRestoreIfUnderutilized();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u3")
            .doesNotContain("u1", "u2");
    }

    @Test
    public void evictOldestTurnKeepsSingleTurnBlock() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("first question"));
        uut.add(AiMessage.from("first answer"));

        boolean evicted = uut.evictOldestTurn();

        assertThat(evicted).isFalse();
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("first question");
    }

    @Test
    public void transcriptEntriesExcludeEvictedOldestTurnContent() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("first question"));
        uut.add(AiMessage.from("first answer"));
        uut.add(UserMessage.from("second question"));
        uut.add(AiMessage.from("second answer"));
        assertThat(uut.evictOldestTurn()).isTrue();

        assertThat(uut.transcriptEntriesForPersistence())
            .extracting(entry -> entry.getRole().name() + ":" + entry.getText())
            .anyMatch(value -> value.contains("REMOVED_FOR_SPACE_SYSTEM:" + RemovedForSpaceSystemMessage.DEFAULT_TEXT))
            .anyMatch(value -> value.contains("second question"))
            .anyMatch(value -> value.contains("second answer"))
            .noneMatch(value -> value.contains("first question"))
            .noneMatch(value -> value.contains("first answer"));
    }

    private AssistantProfileChatMemory createMemory(int maxTokens) {
        return AssistantProfileChatMemory.builder()
            .maxTokens(maxTokens)
            .tokenEstimatorModelNameProvider(() -> "gpt-4o-mini")
            .build();
    }

    private int estimateTokens(ChatMessage... messages) {
        OpenAiTokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o-mini");
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimator.estimateTokenCountInMessage(message);
        }
        return total;
    }

    private ToolExecutionRequest toolRequest(String id) {
        return ToolExecutionRequest.builder()
            .id(id)
            .name("searchNodes")
            .arguments("{\"request\":{\"query\":\"root\"}}")
            .build();
    }

    private boolean isToolRequestAiMessage(ChatMessage message) {
        return message instanceof AiMessage && ((AiMessage) message).hasToolExecutionRequests();
    }

    private String repeatedWords(String word, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(word);
        }
        return builder.toString();
    }

    private int indexOfMessage(List<ChatMemoryRenderEntry> entries, Class<? extends ChatMessage> messageClass) {
        for (int index = 0; index < entries.size(); index++) {
            ChatMessage message = entries.get(index).chatMessage();
            if (message != null && messageClass.isInstance(message)) {
                return index;
            }
        }
        return -1;
    }

    private int indexOfUserText(List<ChatMemoryRenderEntry> entries, String text) {
        for (int index = 0; index < entries.size(); index++) {
            ChatMessage message = entries.get(index).chatMessage();
            if (message instanceof UserMessage && text.equals(((UserMessage) message).singleText())) {
                return index;
            }
        }
        return -1;
    }

    private int indexOfSummary(List<ChatMemoryRenderEntry> entries, String summaryText) {
        for (int index = 0; index < entries.size(); index++) {
            ChatMemoryRenderEntry entry = entries.get(index);
            if (entry.isToolSummary() && summaryText.equals(entry.toolSummaryText())) {
                return index;
            }
        }
        return -1;
    }

    private int indexOfAiText(List<ChatMemoryRenderEntry> entries, String text) {
        for (int index = 0; index < entries.size(); index++) {
            ChatMessage message = entries.get(index).chatMessage();
            if (message instanceof AiMessage && text.equals(((AiMessage) message).text())) {
                return index;
            }
        }
        return -1;
    }
}
