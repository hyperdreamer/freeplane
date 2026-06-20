package org.freeplane.plugin.ai.chat.ui;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLEditorKit;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.GeneralSystemMessage;
import org.freeplane.plugin.ai.chat.memory.InstructionAckMessage;
import org.freeplane.plugin.ai.chat.memory.RemovedForSpaceSystemMessage;
import org.freeplane.plugin.ai.chat.memory.TranscriptHiddenSystemMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMemoryHistoryRendererTest {

    @Test
    public void rebuildFromMessages_rendersUserAssistantAndProfileMessages() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new GeneralSystemMessage("system hidden")),
            ChatMemoryRenderEntry.forMessage(UserMessage.from("u1")),
            ChatMemoryRenderEntry.forMessage(new AssistantProfileSwitchMessage("profile", "Profile A")),
            ChatMemoryRenderEntry.forMessage(new InstructionAckMessage()),
            ChatMemoryRenderEntry.forMessage(AiMessage.from("a1")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-user");
        assertThat(html).contains("message-assistant");
        assertThat(html).contains("message-profile");
        assertThat(html).contains("Profile Profile A");
        assertThat(html).doesNotContain("ok");
        assertThat(html).doesNotContain("system hidden");
    }

    @Test
    public void fullInstructionModeShowsSystemAndProfileSnapshots() {
        RenderFixture fixture = new RenderFixture();
        fixture.uut.setInstructionHistoryRenderingMode(InstructionHistoryRenderingMode.FULL);
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new GeneralSystemMessage("system snapshot")),
            ChatMemoryRenderEntry.forMessage(new AssistantProfileSwitchMessage(
                "profile",
                "Profile A",
                "profile snapshot")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).doesNotContain("message-system");
        assertThat(html).contains("system snapshot");
        assertThat(html).contains("message-profile");
        assertThat(html).contains("profile snapshot");
        assertThat(html).doesNotContain("Profile Profile A");
    }

    @Test
    public void rebuildFromMessages_rendersToolRequestAndToolResultMessages() {
        RenderFixture fixture = new RenderFixture();
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
            .id("tool-1")
            .name("search")
            .arguments("{\"q\":\"test\"}")
            .build();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(AiMessage.from(List.of(toolRequest))),
            ChatMemoryRenderEntry.forMessage(ToolExecutionResultMessage.from("tool-1", "search", "done")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-tool");
        assertThat(html).contains("Tool call [search]");
        assertThat(html).contains("Tool result [search]: done");
    }

    @Test
    public void rebuildFromMessages_usesStoredToolSummaryText() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forToolSummary("searchNodes: query=\"abc\", results=3", ToolCaller.CHAT));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-tool");
        assertThat(html).contains("searchNodes: query=&quot;abc&quot;, results=3");
    }

    @Test
    public void appendEntry_rendersMcpSummaryWithMcpStyle() {
        RenderFixture fixture = new RenderFixture();

        fixture.uut.appendEntry(ChatMemoryRenderEntry.forToolSummary("mcp summary", ToolCaller.MCP));

        String html = fixture.html();
        assertThat(html).contains("mcp summary");
        assertThat(html).contains("message-mcp-call");
    }

    @Test
    public void appendEntryAndRebuildUseEquivalentSummaryRendering() {
        RenderFixture appendFixture = new RenderFixture();
        RenderFixture rebuildFixture = new RenderFixture();
        ChatMemoryRenderEntry entry = ChatMemoryRenderEntry.forToolSummary("searchNodes summary", ToolCaller.CHAT);

        appendFixture.uut.appendEntry(entry);
        rebuildFixture.uut.rebuildFromMessages(Collections.singletonList(entry));

        assertThat(appendFixture.html()).contains("searchNodes summary");
        assertThat(rebuildFixture.html()).contains("searchNodes summary");
        assertThat(appendFixture.html()).contains("message-tool");
        assertThat(rebuildFixture.html()).contains("message-tool");
    }

    @Test
    public void rebuildFromMessages_keepsRawToolEntriesWhenChatSummaryExistsElsewhere() {
        RenderFixture fixture = new RenderFixture();
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
            .id("tool-1")
            .name("search")
            .arguments("{\"q\":\"test\"}")
            .build();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(AiMessage.from(List.of(toolRequest))),
            ChatMemoryRenderEntry.forMessage(ToolExecutionResultMessage.from("tool-1", "search", "done")),
            ChatMemoryRenderEntry.forToolSummary("searchNodes: query=\"test\", results=1", ToolCaller.CHAT));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("searchNodes: query=&quot;test&quot;, results=1");
        assertThat(html).contains("Tool result [search]: done");
        assertThat(html).contains("Tool call [search]");
    }

    @Test
    public void rebuildFromMessages_rendersControlInstructionAsSystemMessage() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(MessageBuilder.buildSystemInstructionUserMessage("hidden instruction")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-system");
        assertThat(html).contains("hidden instruction");
    }

    @Test
    public void rebuildFromMessages_hidesTranscriptHiddenSystemMessageAndAck() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new TranscriptHiddenSystemMessage("hidden transcript")),
            ChatMemoryRenderEntry.forMessage(new InstructionAckMessage()),
            ChatMemoryRenderEntry.forMessage(UserMessage.from("visible user")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("visible user");
        assertThat(html).doesNotContain("hidden transcript");
        assertThat(html).doesNotContain("ok");
    }

    @Test
    public void rebuildFromMessages_handlesEmptyList() {
        RenderFixture fixture = new RenderFixture();

        fixture.uut.rebuildFromMessages(Collections.emptyList());

        assertThat(fixture.history.size()).isZero();
    }

    @Test
    public void rebuildFromMessages_rendersContextBoundaryMarker() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new RemovedForSpaceSystemMessage()),
            ChatMemoryRenderEntry.forMessage(UserMessage.from("recent")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-context-boundary");
        assertThat(html).contains("removed for space");
        assertThat(html).contains("continuing a previous thought");
        assertThat(html).contains("recent");
    }

    @Test
    public void rebuildFromMessages_rendersToolAndMcpSummariesWithSeparateStyles() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(UserMessage.from("u1")),
            ChatMemoryRenderEntry.forToolSummary("searchNodes summary", ToolCaller.CHAT),
            ChatMemoryRenderEntry.forToolSummary("mcp summary", ToolCaller.MCP),
            ChatMemoryRenderEntry.forMessage(AiMessage.from("a1")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("searchNodes summary");
        assertThat(html).contains("mcp summary");
        assertThat(html).contains("message-tool");
        assertThat(html).contains("message-mcp-call");
    }

    @Test
    public void rebuildFromMessages_keepsProfileControlToolSummaryOrder() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new AssistantProfileSwitchMessage("p1", "Profile A")),
            ChatMemoryRenderEntry.forMessage(new InstructionAckMessage()),
            ChatMemoryRenderEntry.forMessage(UserMessage.from("question")),
            ChatMemoryRenderEntry.forToolSummary("tool summary", ToolCaller.CHAT),
            ChatMemoryRenderEntry.forMessage(AiMessage.from("answer")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("Profile Profile A");
        assertThat(html).contains("question");
        assertThat(html).contains("tool summary");
        assertThat(html).contains("answer");
        assertThat(html.indexOf("Profile Profile A")).isLessThan(html.indexOf("question"));
        assertThat(html.indexOf("question")).isLessThan(html.indexOf("tool summary"));
        assertThat(html.indexOf("tool summary")).isLessThan(html.indexOf("answer"));
        assertThat(html).doesNotContain("ok");
    }

    @Test
    public void rebuildFromMessages_keepsRawToolMessagesWhenMcpSummaryExists() {
        RenderFixture fixture = new RenderFixture();
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
            .id("tool-1")
            .name("search")
            .arguments("{\"q\":\"test\"}")
            .build();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(AiMessage.from(List.of(toolRequest))),
            ChatMemoryRenderEntry.forMessage(ToolExecutionResultMessage.from("tool-1", "search", "done")),
            ChatMemoryRenderEntry.forToolSummary("mcp summary", ToolCaller.MCP));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("mcp summary");
        assertThat(html).contains("Tool result [search]: done");
        assertThat(html).contains("Tool call [search]");
    }

    @Test
    public void rebuildFromMessages_rendersContextBoundaryTogetherWithToolSummaries() {
        RenderFixture fixture = new RenderFixture();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new RemovedForSpaceSystemMessage()),
            ChatMemoryRenderEntry.forToolSummary("tool summary", ToolCaller.CHAT),
            ChatMemoryRenderEntry.forToolSummary("mcp summary", ToolCaller.MCP),
            ChatMemoryRenderEntry.forMessage(UserMessage.from("recent user")),
            ChatMemoryRenderEntry.forMessage(AiMessage.from("recent assistant")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-context-boundary");
        assertThat(html).contains("tool summary");
        assertThat(html).contains("mcp summary");
        assertThat(html).contains("recent user");
        assertThat(html).contains("recent assistant");
        assertThat(html.indexOf("removed for space")).isLessThan(html.indexOf("tool summary"));
    }

    @Test
    public void rebuildFromMessages_keepsContextBoundaryWhenRawToolMessagesAppearAlongsideSummary() {
        RenderFixture fixture = new RenderFixture();
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
            .id("tool-1")
            .name("search")
            .arguments("{\"q\":\"test\"}")
            .build();
        List<ChatMemoryRenderEntry> messages = Arrays.asList(
            ChatMemoryRenderEntry.forMessage(new RemovedForSpaceSystemMessage()),
            ChatMemoryRenderEntry.forMessage(AiMessage.from(List.of(toolRequest))),
            ChatMemoryRenderEntry.forMessage(ToolExecutionResultMessage.from("tool-1", "search", "done")),
            ChatMemoryRenderEntry.forToolSummary("tool summary", ToolCaller.CHAT),
            ChatMemoryRenderEntry.forMessage(UserMessage.from("after boundary")));

        fixture.uut.rebuildFromMessages(messages);

        String html = fixture.html();
        assertThat(html).contains("message-context-boundary");
        assertThat(html).contains("tool summary");
        assertThat(html).contains("after boundary");
        assertThat(html).contains("Tool result [search]: done");
        assertThat(html).contains("Tool call [search]");
    }

    private static class RenderFixture {
        private final JEditorPane pane;
        private final ChatMessageHistory history;
        private final ChatMemoryHistoryRenderer uut;

        private RenderFixture() {
            pane = new JEditorPane();
            pane.setContentType("text/html");
            HTMLEditorKit editorKit = (HTMLEditorKit) pane.getEditorKit();
            history = new ChatMessageHistory(pane, editorKit);
            ChatMessageRenderer messageRenderer = new ChatMessageRenderer();
            uut = new ChatMemoryHistoryRenderer(
                history,
                messageRenderer,
                profileName -> "Profile " + profileName);
        }

        private String html() {
            return pane.getText();
        }
    }
}
