package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AIChatServiceTest {

    @Test
    public void chatRebuildsAssistantWhenToolAvailabilityChangesBetweenTurns() {
        AtomicReference<ToolAvailabilityLevel> toolAvailability =
            new AtomicReference<ToolAvailabilityLevel>(ToolAvailabilityLevel.READING);
        AIChatService.AIAssistant readingAssistant = mock(AIChatService.AIAssistant.class);
        AIChatService.AIAssistant editingAssistant = mock(AIChatService.AIAssistant.class);
        when(readingAssistant.chat("first")).thenReturn("reading-response");
        when(editingAssistant.chat("second")).thenReturn("editing-response");
        when(editingAssistant.chat("third")).thenReturn("editing-response-2");
        List<ToolAvailabilityLevel> builtAvailabilities = new ArrayList<ToolAvailabilityLevel>();
        Function<ToolAvailabilityLevel, AIChatService.AIAssistant> assistantFactory = availability -> {
            builtAvailabilities.add(availability);
            return availability == ToolAvailabilityLevel.READING
                ? readingAssistant
                : editingAssistant;
        };

        AIToolSet toolSet = mock(AIToolSet.class);
        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            null,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            toolAvailability::get,
            () -> Boolean.FALSE,
            assistantFactory);

        assertThat(uut.chat("first")).isEqualTo("reading-response");
        toolAvailability.set(ToolAvailabilityLevel.EDITING);
        assertThat(uut.chat("second")).isEqualTo("editing-response");
        assertThat(uut.chat("third")).isEqualTo("editing-response-2");

        assertThat(builtAvailabilities).containsExactly(
            ToolAvailabilityLevel.READING,
            ToolAvailabilityLevel.EDITING);
        verify(readingAssistant).chat("first");
        verify(editingAssistant).chat("second");
        verify(editingAssistant).chat("third");
    }

    @Test
    public void systemMessageProviderUsesResolvedToolAvailability() {
        AIToolSet toolSet = mock(AIToolSet.class);

        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            null,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.READING,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class));

        String message = uut.systemMessageProvider(ToolAvailabilityLevel.READING).apply("request");

        assertThat(message).contains("Available Freeplane tools are limited to reading");
        assertThat(message).contains("Respond in Markdown.");
        assertThat(message).doesNotContain("Profile changes are communicated");
    }

    @Test
    public void systemMessageProviderUsesCapturedVisibleSystemMessageAsBaseText() {
        AIToolSet toolSet = mock(AIToolSet.class);

        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            null,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.READING,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class),
            " captured ",
            false);

        String message = uut.systemMessageProvider(ToolAvailabilityLevel.READING).apply("request");

        assertThat(message).startsWith("captured\n\n");
        assertThat(message).contains("Available Freeplane tools are limited to reading");
    }

    @Test
    public void hiddenRequestSystemMessageIsBaseTextAndDoesNotBypassDynamicGuidance() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AiCodeToolSet aiCodeToolSet = mock(AiCodeToolSet.class);
        when(aiCodeToolSet.systemMessageForChat("request")).thenReturn("code guidance");

        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            aiCodeToolSet,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.READING,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class),
            " hidden base ",
            true);

        String message = uut.systemMessageProvider(ToolAvailabilityLevel.READING).apply("request");

        assertThat(message).startsWith("hidden base\n\n");
        assertThat(message).contains("Available Freeplane tools are limited to reading");
        assertThat(message).contains("code guidance");
        assertThat(message).doesNotContain("Respond in Markdown.");
    }

    @Test
    public void systemMessageProviderIncludesProfileControlGuidanceWhenMemoryHasProfileMessage() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AssistantProfileChatMemory chatMemory = AssistantProfileChatMemory.withMaxTokens(500);
        chatMemory.add(new AssistantProfileSwitchMessage("profile", "Reviewer", "profile guidance"));

        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            null,
            chatMemory,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.READING,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class),
            "base",
            false);

        String message = uut.systemMessageProvider(ToolAvailabilityLevel.READING).apply("request");

        assertThat(message).contains("Profile changes are communicated");
    }

    @Test
    public void allowedToolNamesIncludeAuthorizedCodeTools() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AiCodeToolSet aiCodeToolSet = mock(AiCodeToolSet.class);
        when(aiCodeToolSet.authorizedToolNames()).thenReturn(new java.util.LinkedHashSet<String>(java.util.Arrays.asList(
            "readCode",
            "writeCode",
            "compileCode")));
        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            aiCodeToolSet,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.DISABLED,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class));

        assertThat(uut.allowedToolNames(ToolAvailabilityLevel.DISABLED)).containsExactly(
            "readCode",
            "writeCode",
            "compileCode");
    }

    @Test
    public void allowedToolNamesExposeFormulaToolsOnlyWhenFormulaEditingIsEnabled() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AIChatService disabled = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            null,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.EDITING,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class));
        AIChatService enabled = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            null,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.EDITING,
            () -> Boolean.TRUE,
            availability -> mock(AIChatService.AIAssistant.class));

        assertThat(disabled.allowedToolNames(ToolAvailabilityLevel.EDITING)).doesNotContain(
            "previewFormulaUpdates",
            "applyFormulaUpdates");
        assertThat(enabled.allowedToolNames(ToolAvailabilityLevel.EDITING)).contains(
            "previewFormulaUpdates",
            "applyFormulaUpdates");
    }

    @Test
    public void systemMessageProviderAppendsCodeGuidanceWhenPresent() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AiCodeToolSet aiCodeToolSet = mock(AiCodeToolSet.class);
        when(aiCodeToolSet.systemMessageForChat("request")).thenReturn("code guidance");

        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            aiCodeToolSet,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ToolAvailabilityLevel.READING,
            () -> Boolean.FALSE,
            availability -> mock(AIChatService.AIAssistant.class));

        String message = uut.systemMessageProvider(ToolAvailabilityLevel.READING).apply("request");

        assertThat(message).contains("Available Freeplane tools are limited to reading");
        assertThat(message).contains("code guidance");
    }
}
