package org.freeplane.plugin.ai.tools.code;

import java.util.Collections;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnostics;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.code.AttachedEditorChatMode;
import org.freeplane.plugin.ai.code.AttachedEditorChatModeSettings;
import org.freeplane.plugin.ai.code.SingleEditorAttachmentService;
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

        ReadCodeResponse response = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.NO_CODE);
    }

    @Test
    public void readCodeReturnsLiveTextContentTypeAndFailureStateWhenAttached() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("script");
        service.attachEditor(editor, "text/x-freeplane-script-groovy");
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/x-freeplane-script-groovy",
            CodeLifecycleStatus.FAILED,
            token("failure"),
            CodeStateDiagnostics.sourceDiagnostics(Collections.singletonList("Broken"), 1),
            "Broken"));
        service.compileCode(new CompileCodeRequest(
            ScriptHost.ATTACHED_EDITOR,
            service.readCode(new ReadCodeRequest(ScriptHost.ATTACHED_EDITOR, null)).getStateToken()));
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        ReadCodeResponse response = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(response.getContentType()).isEqualTo("text/x-freeplane-script-groovy");
        assertThat(response.getContent().getSourceText()).isEqualTo("script");
        assertThat(response.getDiagnostics()).extracting(CodeStateDiagnostic::getMessage).containsExactly("Broken");
    }

    @Test
    public void formulaSystemMessageExplainsAvailabilityAndUiRestrictions() {
        AiCodeHostService codeHostService = new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return new ReadCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    "text/x-freeplane-formula-groovy",
                    CodeLifecycleStatus.READY,
                    null,
                    token("fingerprint"),
                    new CodeStateContent("=1+1", null),
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
            public RunCodeResponse runCode(RunCodeRequest request) {
                throw new IllegalStateException("not needed");
            }

            @Override
            public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
                throw new UnsupportedOperationException();
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
        assertThat(message).contains("inputText stays blank or null for formulas");
        assertThat(message).contains("Keep it value-computing.");
    }

    @Test
    public void writeCodeUpdatesFakeEditorText() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("before");
        service.attachEditor(editor, "text/plain");
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        WriteCodeResponse response = uut.writeCode(new WriteCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            new CodeStateContent("after", null),
            uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null)).getStateToken()));

        assertThat(editor.getCodeStateContent().getSourceText()).isEqualTo("after");
        assertThat(response.getStateToken().getStateFingerprint()).isNotBlank();
    }

    @Test
    public void compileCodeStoresFailureStateAndClearsItOnSuccess() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("text");
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.FAILED,
            token("failure"),
            CodeStateDiagnostics.sourceDiagnostics(Collections.singletonList("Broken"), 1),
            "Broken"));
        service.attachEditor(editor, "text/plain");
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        ReadCodeResponse initialState = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null));
        CompileCodeResponse first = uut.compileCode(new CompileCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            initialState.getStateToken()));
        ReadCodeResponse firstState = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null));
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.READY,
            token("success"),
            Collections.<CodeStateDiagnostic>emptyList(),
            null));
        CompileCodeResponse second = uut.compileCode(new CompileCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            firstState.getStateToken()));
        ReadCodeResponse secondState = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null));

        assertThat(first.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(firstState.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(secondState.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
    }

    @Test
    public void codeToolsExceptReadFailWhenNoEditorIsAttached() {
        AiCodeToolSet uut = new AiCodeToolSet(newDetachedCodeHostService(), null, null, ToolCaller.CHAT);

        assertThatThrownBy(() -> uut.writeCode(new WriteCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            new CodeStateContent("x", null),
            null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No editor is attached.");
        assertThatThrownBy(() -> uut.compileCode(new CompileCodeToolRequest(ScriptHost.ATTACHED_EDITOR, null)))
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
                    ScriptHost.ATTACHED_EDITOR,
                    null,
                    CodeLifecycleStatus.NO_CODE,
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
            public RunCodeResponse runCode(RunCodeRequest request) {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void addRunListener(AiCodeRunListener listener) {
            }

            @Override
            public void removeRunListener(AiCodeRunListener listener) {
            }
        };
    }

    private static CodeStateToken token(String stateFingerprint) {
        return new CodeStateToken("code", "input", stateFingerprint);
    }

    private static class FakeCodeEditor implements AiCodeEditor {
        private CodeStateContent content;
        private CompileCodeResponse compileResponse = new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.READY,
            token("initial"),
            Collections.<CodeStateDiagnostic>emptyList(),
            null);
        private RunCodeResponse runResponse = new RunCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.SUCCEEDED,
            ScriptRunInitiator.AI,
            token("initial"),
            null,
            null,
            null,
            null);

        private FakeCodeEditor(String text) {
            this.content = new CodeStateContent(text, null);
        }

        @Override
        public CodeStateContent getCodeStateContent() {
            return content;
        }

        @Override
        public void replaceCodeStateContent(CodeStateContent content) {
            this.content = content == null ? new CodeStateContent("", null) : content;
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            return compileResponse;
        }

        @Override
        public RunCodeResponse runCode(RunCodeRequest request) {
            return runResponse;
        }

        private void setCompileResponse(CompileCodeResponse compileResponse) {
            this.compileResponse = compileResponse;
        }
    }
}
