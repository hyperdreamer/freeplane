package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
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
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0)).isInstanceOf(GeneralSystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX
                + TranscriptHiddenSystemMessage.DEFAULT_TEXT);
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(2)).singleText())
            .isEqualTo("hello");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(3)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile profile.");
        assertThat(messages.get(4)).isInstanceOf(InstructionAckMessage.class);
        assertThat(((AiMessage) messages.get(4)).text()).isEqualTo("ok");
    }

    @Test
    public void providerSystemMessageUpdatesComposedTextWithoutReplacingCapturedBaseText() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new GeneralSystemMessage("base", "old composed"));

        uut.add(SystemMessage.from("new composed"));

        assertThat(uut.capturedSystemMessage()).isEqualTo("base");
        assertThat(uut.composedSystemMessage()).isEqualTo("new composed");
    }

    @Test
    public void plainUserMessageWithAutomaticCodeStatusPrefixIsStoredAsDedicatedMessageType() {
        AssistantProfileChatMemory uut = createMemory(500);

        uut.add(UserMessage.from("Automatic app-authored code-status message:\ncodeState=RUN_FAILED"));

        assertThat(uut.activeConversationRenderEntries())
            .extracting(ChatMemoryRenderEntry::chatMessage)
            .first()
            .isInstanceOf(AutomaticCodeStatusMessage.class);
        assertThat(uut.transcriptEntriesForPersistence())
            .extracting(ChatTranscriptEntry::getRole)
            .containsExactly(org.freeplane.plugin.ai.chat.history.ChatTranscriptRole.AUTOMATIC_CODE_STATUS);
    }

    @Test
    public void storedGeneralSystemMessageIsProjectorInputNotSelectedChatContent() {
        AssistantProfileChatMemory uut = createMemory(500);

        uut.add(new GeneralSystemMessage("general"));
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));

        assertThat(uut.activeConversationRenderEntries())
            .extracting(entry -> entry.chatMessage() instanceof GeneralSystemMessage
                ? ((GeneralSystemMessage) entry.chatMessage()).text()
                : entry.chatMessage() instanceof UserMessage
                    ? ((UserMessage) entry.chatMessage()).singleText()
                    : entry.chatMessage() instanceof AiMessage
                        ? ((AiMessage) entry.chatMessage()).text()
                        : null)
            .containsExactly("general", "u1", "a1");
        assertThat(uut.messages())
            .extracting(message -> message instanceof GeneralSystemMessage
                ? ((GeneralSystemMessage) message).text()
                : message instanceof UserMessage
                    ? ((UserMessage) message).singleText()
                    : message instanceof AiMessage
                        ? ((AiMessage) message).text()
                        : null)
            .containsExactly("general", "u1", "a1");
        assertThat(uut.transcriptEntriesForPersistence())
            .extracting(ChatTranscriptEntry::getText)
            .containsExactly("general", "u1", "a1");
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
    public void summaryOnlyMcpSequenceRemainsVisibleInPanelAndExcludedFromModelAndTranscript() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.addToolCallSummary("MCP1", ToolCaller.MCP);
        uut.addToolCallSummary("MCP2", ToolCaller.MCP);
        uut.addToolCallSummary("MCP3", ToolCaller.MCP);

        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .containsExactly("MCP1", "MCP2", "MCP3");
        assertThat(uut.messages()).isEmpty();
        assertThat(uut.transcriptEntriesForPersistence()).isEmpty();
    }

    @Test
    public void panelProjectionHidesOnlySummarizedChatOwnedToolBlock() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from(List.of(toolRequest("tool-1", "searchHistorical"))));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchHistorical", "history result"));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from(List.of(toolRequest("tool-2", "searchCurrent"))));
        uut.add(ToolExecutionResultMessage.from("tool-2", "searchCurrent", "fresh result"));
        uut.add(AiMessage.from("a2"));

        List<ChatMemoryRenderEntry> entries = uut.panelConversationRenderEntries();

        assertThat(entries)
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("summary-1");
        assertThat(entries)
            .noneMatch(entry -> entry.chatMessage() instanceof ToolExecutionResultMessage
                && "searchHistorical".equals(((ToolExecutionResultMessage) entry.chatMessage()).toolName()));
        assertThat(entries)
            .noneMatch(entry -> entry.chatMessage() instanceof AiMessage
                && ((AiMessage) entry.chatMessage()).hasToolExecutionRequests()
                && "searchHistorical".equals(((AiMessage) entry.chatMessage()).toolExecutionRequests().get(0).name()));
        assertThat(entries)
            .anyMatch(entry -> entry.chatMessage() instanceof ToolExecutionResultMessage
                && "searchCurrent".equals(((ToolExecutionResultMessage) entry.chatMessage()).toolName())
                && "fresh result".equals(((ToolExecutionResultMessage) entry.chatMessage()).text()));
        assertThat(entries)
            .anyMatch(entry -> entry.chatMessage() instanceof AiMessage
                && ((AiMessage) entry.chatMessage()).hasToolExecutionRequests()
                && "searchCurrent".equals(((AiMessage) entry.chatMessage()).toolExecutionRequests().get(0).name()));
    }

    @Test
    public void panelProjectionKeepsRawChatOwnedToolDetailWhenOnlyMcpSummaryExists() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.addToolCallSummary("MCP1", ToolCaller.MCP);
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from(List.of(toolRequest("tool-1", "searchNodes"))));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchNodes", "Root"));
        uut.add(AiMessage.from("a1"));

        List<ChatMemoryRenderEntry> entries = uut.panelConversationRenderEntries();

        assertThat(entries)
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("MCP1");
        assertThat(entries)
            .anyMatch(entry -> entry.chatMessage() instanceof ToolExecutionResultMessage
                && "searchNodes".equals(((ToolExecutionResultMessage) entry.chatMessage()).toolName())
                && "Root".equals(((ToolExecutionResultMessage) entry.chatMessage()).text()));
        assertThat(entries)
            .anyMatch(entry -> entry.chatMessage() instanceof AiMessage
                && ((AiMessage) entry.chatMessage()).hasToolExecutionRequests()
                && "searchNodes".equals(((AiMessage) entry.chatMessage()).toolExecutionRequests().get(0).name()));
    }

    @Test
    public void messagesIncludeOnlyLatestProfileSwitchControlMessagePair() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("alpha", "Alpha"));
        uut.add(new AssistantProfileSwitchMessage("beta", "Beta"));
        uut.add(UserMessage.from("hello"));

        List<ChatMessage> messages = uut.messages();

        assertThat(messages)
            .extracting(message -> message instanceof UserMessage
                ? ((UserMessage) message).singleText()
                : message instanceof AiMessage
                    ? ((AiMessage) message).text()
                    : null)
            .containsExactly(
                MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile Beta.",
                "ok",
                "hello");
    }

    @Test
    public void profileSwitchControlMessagePairPreservesConversationOrderForLatestMarker() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("default", "Default"));
        uut.add(UserMessage.from("u1"));
        uut.add(new AssistantProfileSwitchMessage("a", "A"));
        uut.add(UserMessage.from("u2"));
        uut.add(new AssistantProfileSwitchMessage("default", "Default"));
        uut.add(UserMessage.from("u3"));

        List<String> projectedTexts = uut.messages().stream()
            .map(message -> message instanceof UserMessage
                ? ((UserMessage) message).singleText()
                : message instanceof AiMessage
                    ? ((AiMessage) message).text()
                    : null)
            .collect(Collectors.toList());
        String latestDefaultMarker = MessageBuilder.CONTROL_INSTRUCTION_PREFIX
            + "Now you have the profile Default.";
        assertThat(projectedTexts).containsExactly("u1", "u2", latestDefaultMarker, "ok", "u3");
        assertThat(Collections.frequency(projectedTexts, latestDefaultMarker)).isEqualTo(1);
        assertThat(projectedTexts).doesNotContain(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile A.");
    }

    @Test
    public void modelProjectionPrependsLatestProfileControlMessagePairWhenLatestProfileSwitchFallsBeforeWindow() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("p1", "Profile"));
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        assertThat(uut.evictOldestTurn()).isTrue();

        List<ChatMessage> messages = uut.messages();
        assertThat(messages)
            .extracting(message -> message instanceof UserMessage
                ? ((UserMessage) message).singleText()
                : message instanceof AiMessage
                    ? ((AiMessage) message).text()
                    : null)
            .containsExactly(
                MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile Profile.",
                "ok",
                MessageBuilder.CONTROL_INSTRUCTION_PREFIX + RemovedForSpaceSystemMessage.DEFAULT_TEXT,
                "u2",
                "a2");
    }

    @Test
    public void modelProjectionReplacesSelectedProfileSwitchWithDerivedLatestProfileControlMessagePair() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("p1", "Profile"));
        uut.add(UserMessage.from("u1"));
        uut.add(AiMessage.from("a1"));

        assertThat(uut.activeConversationRenderEntries())
            .extracting(entry -> entry.chatMessage() instanceof UserMessage
                ? ((UserMessage) entry.chatMessage()).singleText()
                : entry.chatMessage() instanceof AiMessage
                    ? ((AiMessage) entry.chatMessage()).text()
                    : null)
            .containsExactly(MessageBuilder.buildAssistantProfileMarker("Profile"), "u1", "a1");
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage
                ? ((UserMessage) message).singleText()
                : message instanceof AiMessage
                    ? ((AiMessage) message).text()
                    : null)
            .containsExactly(
                MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile Profile.",
                "ok",
                "u1",
                "a1");
        assertThat(uut.transcriptEntriesForPersistence()).hasSize(3);
        assertThat(uut.transcriptEntriesForPersistence().get(0))
            .isInstanceOf(AssistantProfileTranscriptEntry.class);
        assertThat(uut.transcriptEntriesForPersistence())
            .extracting(ChatTranscriptEntry::getText)
            .contains(null, "u1", "a1");
    }

    @Test
    public void modelProjectionPlacesSyntheticAckBeforeSubsequentRealUserMessage() {
        AssistantProfileChatMemory uut = createMemory(500);
        uut.add(new AssistantProfileSwitchMessage("p1", "Profile"));
        uut.add(UserMessage.from("which node is currently selected?"));

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage
                ? ((UserMessage) message).singleText()
                : message instanceof AiMessage
                    ? ((AiMessage) message).text()
                    : null)
            .containsExactly(
                MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile Profile.",
                "ok",
                "which node is currently selected?");
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
    public void mcpSummaryRemainsVisibleWhenHistoricalChatToolActivityGroupIsHidden() {
        ToolExecutionRequest historicalRequest = toolRequest("tool-1");
        String largeToolResult = repeatedWords("history", 600);
        int visibleDialogTokens = estimateTokens(
            UserMessage.from("u1"),
            AiMessage.from("a1"),
            UserMessage.from("u2"),
            AiMessage.from("a2"));
        int maxTokens = visibleDialogTokens * 4;
        AssistantProfileChatMemory uut = createMemory(maxTokens);

        uut.add(UserMessage.from("u1"));
        uut.addToolCallSummary("MCP1", ToolCaller.MCP);
        uut.add(AiMessage.from(List.of(historicalRequest)));
        uut.add(ToolExecutionResultMessage.from("tool-1", "searchNodes", largeToolResult));
        uut.addToolCallSummary("summary-1", ToolCaller.CHAT);
        uut.add(AiMessage.from("a1"));
        uut.add(UserMessage.from("u2"));
        uut.add(AiMessage.from("a2"));

        uut.onResponseTokenUsage(new TokenUsage(1, 1));

        assertThat(uut.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("MCP1")
            .doesNotContain("summary-1");
        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains("u1", "u2")
            .doesNotContain("MCP1");
        assertThat(uut.transcriptEntriesForPersistence())
            .extracting(ChatTranscriptEntry::getText)
            .contains("u1", "a1", "u2", "a2")
            .doesNotContain("MCP1", "summary-1");
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
    public void refreshCompactionForCurrentMaxTokensRebalancesVisibleTurns() {
        String firstQuestion = repeatedWords("first", 24);
        String firstAnswer = repeatedWords("answer1", 24);
        String secondQuestion = repeatedWords("second", 24);
        String secondAnswer = repeatedWords("answer2", 24);
        String thirdQuestion = repeatedWords("third", 24);
        String thirdAnswer = repeatedWords("answer3", 24);
        int allTurnTokens = estimateTokens(
            UserMessage.from(firstQuestion),
            AiMessage.from(firstAnswer),
            UserMessage.from(secondQuestion),
            AiMessage.from(secondAnswer),
            UserMessage.from(thirdQuestion),
            AiMessage.from(thirdAnswer));
        int visibleAfterReductionTokens = estimateTokens(
            UserMessage.from(secondQuestion),
            AiMessage.from(secondAnswer),
            UserMessage.from(thirdQuestion),
            AiMessage.from(thirdAnswer));
        AtomicInteger maxTokens = new AtomicInteger(allTurnTokens);
        AssistantProfileChatMemory uut = AssistantProfileChatMemory.builder()
            .dynamicMaxTokens(ignored -> maxTokens.get())
            .tokenEstimatorModelNameProvider(() -> "gpt-4o-mini")
            .build();
        uut.add(UserMessage.from(firstQuestion));
        uut.add(AiMessage.from(firstAnswer));
        uut.add(UserMessage.from(secondQuestion));
        uut.add(AiMessage.from(secondAnswer));
        uut.add(UserMessage.from(thirdQuestion));
        uut.add(AiMessage.from(thirdAnswer));

        maxTokens.set(visibleAfterReductionTokens);
        uut.refreshCompactionForCurrentMaxTokens();

        assertThat(uut.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains(secondQuestion, thirdQuestion)
            .doesNotContain(firstQuestion);
        assertThat(uut.messages())
            .extracting(message -> message instanceof AiMessage ? ((AiMessage) message).text() : null)
            .contains(secondAnswer, thirdAnswer)
            .doesNotContain(firstAnswer);
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
        return toolRequest(id, "searchNodes");
    }

    private ToolExecutionRequest toolRequest(String id, String name) {
        return ToolExecutionRequest.builder()
            .id(id)
            .name(name)
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
