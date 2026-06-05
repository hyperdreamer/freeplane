package org.freeplane.plugin.ai.code;

import java.util.Collections;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptRequest;
import org.freeplane.features.ai.code.RunScriptResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiCodeToolSetTest {

    @Test
    public void readCodeReturnsNoCodeStateWhenNoEditorIsAttached() {
        AiCodeToolSet uut = new AiCodeToolSet(newDetachedCodeHostService(), null, null, ToolCaller.CHAT);

        ReadCodeResponse response = uut.readCode(new ReadCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.NO_CODE);
    }

    @Test
    public void readCodeReturnsLiveTextContentTypeAndFailureStateWhenAttached() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("script");
        service.attachEditor(editor, "text/x-freeplane-script-groovy");
        editor.setCompileResponse(new CompileCodeResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/x-freeplane-script-groovy",
            CodeLifecycleStatus.FAILED,
            "failure",
            Collections.singletonList("Broken"),
            "Broken",
            1));
        service.compileCode(new CompileCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        ReadCodeResponse response = uut.readCode(new ReadCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(response.getContentType()).isEqualTo("text/x-freeplane-script-groovy");
        assertThat(response.getCodeText()).isEqualTo("script");
        assertThat(response.getCompilerDiagnostics()).containsExactly("Broken");
    }

    @Test
    public void formulaSystemMessageExplainsReadOnlyAndUiRestrictions() {
        AiCodeHostService codeHostService = new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return new ReadCodeResponse(
                    "attached-editor-1",
                    ScriptHost.ATTACHED_EDITOR,
                    "text/x-freeplane-formula-groovy",
                    CodeLifecycleStatus.READY,
                    null,
                    "fingerprint",
                    "=1+1",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                throw new IllegalStateException("not needed");
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                throw new IllegalStateException("not needed");
            }

            @Override
            public RunScriptResponse runScript(RunScriptRequest request) {
                throw new IllegalStateException("not needed");
            }

            @Override
            public void addRunListener(AiCodeRunListener listener) {
            }

            @Override
            public void removeRunListener(AiCodeRunListener listener) {
            }
        };
        AiCodeToolSet uut = new AiCodeToolSet(codeHostService, null, null, ToolCaller.CHAT);

        String message = uut.systemMessageForChat("request");

        assertThat(message).contains("Use readCode, writeCode, and compileCode.");
        assertThat(message).contains("The attached content is a formula.");
        assertThat(message).contains("Keep the formula read-only and value-computing.");
        assertThat(message).contains("Avoid state-changing Freeplane API calls");
        assertThat(message).contains("avoid obviously UI-driving calls");
        assertThat(message).contains("Use the available Freeplane API documentation for API surface and semantics");
        assertThat(message).contains("do not assume it explicitly marks which methods are UI-related");
    }

    @Test
    public void writeCodeUpdatesFakeEditorText() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("before");
        service.attachEditor(editor, "text/plain");
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        WriteCodeResponse response = uut.writeCode(new WriteCodeRequest(null, ScriptHost.ATTACHED_EDITOR, "after", null));

        assertThat(editor.getText()).isEqualTo("after");
        assertThat(response.getFingerprint()).isNotBlank();
    }

    @Test
    public void compileCodeStoresFailureStateAndClearsItOnSuccess() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("text");
        editor.setCompileResponse(new CompileCodeResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.FAILED,
            "failure",
            Collections.singletonList("Broken"),
            "Broken",
            1));
        service.attachEditor(editor, "text/plain");
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        CompileCodeResponse first = uut.compileCode(new CompileCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        ReadCodeResponse firstState = uut.readCode(new ReadCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        editor.setCompileResponse(new CompileCodeResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.READY,
            "success",
            Collections.<String>emptyList(),
            null,
            null));
        CompileCodeResponse second = uut.compileCode(new CompileCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        ReadCodeResponse secondState = uut.readCode(new ReadCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));

        assertThat(first.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(firstState.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(secondState.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
    }

    @Test
    public void codeToolsExceptReadFailWhenNoEditorIsAttached() {
        AiCodeToolSet uut = new AiCodeToolSet(newDetachedCodeHostService(), null, null, ToolCaller.CHAT);

        assertThatThrownBy(() -> uut.writeCode(new WriteCodeRequest(null, ScriptHost.ATTACHED_EDITOR, "x", null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No editor is attached.");
        assertThatThrownBy(() -> uut.compileCode(new CompileCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null)))
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

    private AiCodeHostService newDetachedCodeHostService() {
        return new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return new ReadCodeResponse(
                    null,
                    ScriptHost.ATTACHED_EDITOR,
                    null,
                    CodeLifecycleStatus.NO_CODE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public RunScriptResponse runScript(RunScriptRequest request) {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public void addRunListener(AiCodeRunListener listener) {
            }

            @Override
            public void removeRunListener(AiCodeRunListener listener) {
            }
        };
    }

    private static class FakeCodeEditor implements AiCodeEditor {
        private String text;
        private CompileCodeResponse compileResponse = new CompileCodeResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.READY,
            "initial",
            Collections.<String>emptyList(),
            null,
            null);
        private RunScriptResponse runResponse = new RunScriptResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.SUCCEEDED,
            ScriptRunInitiator.AI,
            "initial",
            null,
            null,
            null,
            null,
            null);

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
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            return compileResponse;
        }

        @Override
        public RunScriptResponse runScript(RunScriptRequest request) {
            return runResponse;
        }

        private void setCompileResponse(CompileCodeResponse compileResponse) {
            this.compileResponse = compileResponse;
        }
    }
}
