package org.freeplane.plugin.ai.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.freeplane.features.ai.code.AiChatCodeEditor;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.plugin.ai.chat.AIChatPanel;
import org.freeplane.plugin.ai.chat.LiveChatSessionId;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;

public class AttachedEditorToolSetTest {

    @Test
    public void readAttachedEditorReturnsDetachedStateWhenNoEditorIsAttached() {
        AttachedEditorToolSet uut = new AttachedEditorToolSet(newDetachedProvider(), null, ToolCaller.CHAT);

        ReadAttachedEditorResponse response = uut.readAttachedEditor();

        assertThat(response.isAttached()).isFalse();
    }

    @Test
    public void readAttachedEditorReturnsLiveTextCapabilitiesAndIssueStateWhenAttached() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("script");
        service.attachEditor(editor, "text/x-freeplane-script-groovy");
        editor.setCompileResult(new AiChatCodeOperationResult(
            false,
            Collections.singletonList("Broken"),
            null,
            null,
            "compile",
            "Broken",
            1,
            "failure"));
        service.compileAttachedEditorContent();
        AttachedEditorToolSet uut = new AttachedEditorToolSet(service, null, ToolCaller.CHAT);

        ReadAttachedEditorResponse response = uut.readAttachedEditor();

        assertThat(response.isAttached()).isTrue();
        assertThat(response.getContentType()).isEqualTo("text/x-freeplane-script-groovy");
        assertThat(response.getText()).isEqualTo("script");
        assertThat(response.isSupportsCompilation()).isTrue();
        assertThat(response.isHasIssue()).isTrue();
    }

    @Test
    public void overwriteAttachedEditorContentUpdatesFakeEditorText() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("before");
        service.attachEditor(editor, "text/plain");
        AttachedEditorToolSet uut = new AttachedEditorToolSet(service, null, ToolCaller.CHAT);

        OverwriteAttachedEditorContentResponse response = uut.overwriteAttachedEditorContent(
            new OverwriteAttachedEditorContentRequest("after"));

        assertThat(editor.getText()).isEqualTo("after");
        assertThat(response.getSourceFingerprint()).isNotBlank();
    }

    @Test
    public void compileAttachedEditorContentStoresLatestIssueOnlyOnFailureAndClearsItOnSuccess() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("text");
        AiChatCodeOperationResult failure = new AiChatCodeOperationResult(
            false,
            Collections.singletonList("Broken"),
            null,
            null,
            "compile",
            "Broken",
            1,
            "failure");
        AiChatCodeOperationResult success = new AiChatCodeOperationResult(
            true,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            "success");
        editor.setCompileResult(failure);
        service.attachEditor(editor, "text/plain");
        AttachedEditorToolSet uut = new AttachedEditorToolSet(service, null, ToolCaller.CHAT);

        AiChatCodeOperationResult first = uut.compileAttachedEditorContent();
        ReadAttachedEditorLatestIssueResponse firstIssue = uut.getAttachedEditorLatestIssue();
        editor.setCompileResult(success);
        AiChatCodeOperationResult second = uut.compileAttachedEditorContent();
        ReadAttachedEditorLatestIssueResponse secondIssue = uut.getAttachedEditorLatestIssue();

        assertThat(first).isSameAs(failure);
        assertThat(firstIssue.isHasIssue()).isTrue();
        assertThat(firstIssue.getIssue()).isSameAs(failure);
        assertThat(second).isSameAs(success);
        assertThat(secondIssue.isHasIssue()).isFalse();
    }

    @Test
    public void attachedEditorToolsExceptReadFailWhenNoEditorIsAttached() {
        AttachedEditorToolSet uut = new AttachedEditorToolSet(newDetachedProvider(), null, ToolCaller.CHAT);

        assertThatThrownBy(() -> uut.overwriteAttachedEditorContent(new OverwriteAttachedEditorContentRequest("x")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No editor is attached.");
        assertThatThrownBy(uut::compileAttachedEditorContent)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No editor is attached.");
        assertThatThrownBy(uut::getAttachedEditorLatestIssue)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No editor is attached.");
    }

    private SingleEditorAttachmentService newAttachedService() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        return new SingleEditorAttachmentService(aiChatPanel, settings);
    }

    private AttachedEditorProvider newDetachedProvider() {
        return new AttachedEditorProvider() {
            @Override
            public ReadAttachedEditorResponse readAttachedEditor() {
                return ReadAttachedEditorResponse.detached();
            }

            @Override
            public OverwriteAttachedEditorContentResponse overwriteAttachedEditorContent(String text) {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public AiChatCodeOperationResult compileAttachedEditorContent() {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public ReadAttachedEditorLatestIssueResponse getAttachedEditorLatestIssue() {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public boolean hasAttachedEditor() {
                return false;
            }

            @Override
            public String attachedContentType() {
                return null;
            }
        };
    }

    private static class FakeCodeEditor implements AiChatCodeEditor {
        private String text;
        private AiChatCodeOperationResult compileResult = new AiChatCodeOperationResult(
            true,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            "initial");

        private FakeCodeEditor(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void replaceText(String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public AiChatCodeOperationResult compileForAi() {
            return compileResult;
        }

        private void setCompileResult(AiChatCodeOperationResult compileResult) {
            this.compileResult = compileResult;
        }
    }
}
