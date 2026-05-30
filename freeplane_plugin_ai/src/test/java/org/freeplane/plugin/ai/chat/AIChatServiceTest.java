package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.freeplane.plugin.ai.code.AttachedEditorToolSet;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.junit.Test;

import dev.langchain4j.model.chat.ChatModel;

public class AIChatServiceTest {

    @Test
    public void chatRebuildsAssistantWhenToolAvailabilityChangesBetweenTurns() {
        AtomicReference<ChatToolAvailability> toolAvailability =
            new AtomicReference<ChatToolAvailability>(ChatToolAvailability.READING);
        AIChatService.AIAssistant readingAssistant = mock(AIChatService.AIAssistant.class);
        AIChatService.AIAssistant editingAssistant = mock(AIChatService.AIAssistant.class);
        when(readingAssistant.chat("first")).thenReturn("reading-response");
        when(editingAssistant.chat("second")).thenReturn("editing-response");
        when(editingAssistant.chat("third")).thenReturn("editing-response-2");
        List<ChatToolAvailability> builtAvailabilities = new ArrayList<ChatToolAvailability>();
        Function<ChatToolAvailability, AIChatService.AIAssistant> assistantFactory = availability -> {
            builtAvailabilities.add(availability);
            return availability == ChatToolAvailability.READING
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
            assistantFactory);

        assertThat(uut.chat("first")).isEqualTo("reading-response");
        toolAvailability.set(ChatToolAvailability.EDITING);
        assertThat(uut.chat("second")).isEqualTo("editing-response");
        assertThat(uut.chat("third")).isEqualTo("editing-response-2");

        assertThat(builtAvailabilities).containsExactly(
            ChatToolAvailability.READING,
            ChatToolAvailability.EDITING);
        verify(readingAssistant).chat("first");
        verify(editingAssistant).chat("second");
        verify(editingAssistant).chat("third");
    }

    @Test
    public void systemMessageProviderUsesResolvedToolAvailability() {
        AIToolSet toolSet = mock(AIToolSet.class);
        when(toolSet.systemMessageForChat("request", ChatToolAvailability.READING))
            .thenReturn("reading-guidance");

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
            () -> ChatToolAvailability.READING,
            availability -> mock(AIChatService.AIAssistant.class));

        String message = uut.systemMessageProvider(ChatToolAvailability.READING).apply("request");

        assertThat(message).isEqualTo("reading-guidance");
        verify(toolSet).systemMessageForChat("request", ChatToolAvailability.READING);
    }

    @Test
    public void allowedToolNamesAlwaysIncludeAttachedEditorTools() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AttachedEditorToolSet attachedEditorToolSet = mock(AttachedEditorToolSet.class);
        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            attachedEditorToolSet,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ChatToolAvailability.DISABLED,
            availability -> mock(AIChatService.AIAssistant.class));

        assertThat(uut.allowedToolNames(ChatToolAvailability.DISABLED)).containsExactly(
            "readAttachedEditor",
            "overwriteAttachedEditorContent",
            "compileAttachedEditorContent",
            "getAttachedEditorLatestIssue");
    }

    @Test
    public void systemMessageProviderAppendsAttachedEditorGuidanceWhenPresent() {
        AIToolSet toolSet = mock(AIToolSet.class);
        AttachedEditorToolSet attachedEditorToolSet = mock(AttachedEditorToolSet.class);
        when(toolSet.systemMessageForChat("request", ChatToolAvailability.READING)).thenReturn("base guidance");
        when(attachedEditorToolSet.systemMessageForChat("request")).thenReturn("attached guidance");

        AIChatService uut = new AIChatService(
            mock(ChatModel.class),
            toolSet,
            Collections.<Object>singletonList(toolSet),
            attachedEditorToolSet,
            null,
            new ChatTokenUsageTracker(totals -> {
            }),
            null,
            null,
            null,
            () -> ChatToolAvailability.READING,
            availability -> mock(AIChatService.AIAssistant.class));

        String message = uut.systemMessageProvider(ChatToolAvailability.READING).apply("request");

        assertThat(message).isEqualTo("base guidance\n\nattached guidance");
    }
}
