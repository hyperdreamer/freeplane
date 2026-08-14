package org.freeplane.plugin.ai.tools.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeState;
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
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
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

        ReadCodeResponse response = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR));

        assertThat(response.getCodeState()).isEqualTo(CodeState.NO_CODE);
    }

    @Test
    public void readCodeReturnsLiveTextContentTypeAndFailureStateWhenAttached() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("script");
        service.attachEditor(editor, "text/x-freeplane-script-groovy");
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/x-freeplane-script-groovy",
            CodeState.INVALID_SCRIPT,
            null,
            CodeStateDiagnostics.sourceDiagnostics(Collections.singletonList("Broken"), 1),
            "Broken"));
        service.compileCode(new CompileCodeRequest(
            ScriptHost.ATTACHED_EDITOR,
            service.readCode(new ReadCodeRequest(ScriptHost.ATTACHED_EDITOR)).getStateToken()));
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        ReadCodeResponse response = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR));

        assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
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
                    CodeState.RUNNABLE,
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
        assertThat(message).contains("The attached content is a content formula.");
        assertThat(message).contains("argument-free");
        assertThat(message).contains("runCode is not supported");
    }

    @Test
    public void filterConditionSystemMessageExplainsConditionSemantics() {
        AiCodeHostService codeHostService = new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return new ReadCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    "text/x-freeplane-formula-condition-groovy",
                    CodeState.EDITED,
                    null,
                    token("fingerprint"),
                    new CodeStateContent("node.text == 'x'", null),
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
                throw new IllegalStateException("not runnable");
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

        assertThat(message).contains("The attached content is a condition formula.");
        assertThat(message).contains("argument-free");
        assertThat(message).contains("Boolean or Number");
        assertThat(message).contains("runCode is not supported");
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
            uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR)).getStateToken()));

        assertThat(editor.getCodeStateContent().getSourceText()).isEqualTo("after");
        assertThat(response.getStateToken().getCodeFingerprint()).isNotBlank();
        assertThat(response.getStateToken().getArgumentsFingerprint()).isNotBlank();
    }

    @Test
    public void compileCodeStoresFailureStateAndClearsItOnSuccess() {
        SingleEditorAttachmentService service = newAttachedService();
        FakeCodeEditor editor = new FakeCodeEditor("text");
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.INVALID_SCRIPT,
            null,
            CodeStateDiagnostics.sourceDiagnostics(Collections.singletonList("Broken"), 1),
            "Broken"));
        service.attachEditor(editor, "text/plain");
        AiCodeToolSet uut = new AiCodeToolSet(service, null, null, ToolCaller.CHAT);

        ReadCodeResponse initialState = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR));
        CompileCodeResponse first = uut.compileCode(new CompileCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            initialState.getStateToken()));
        ReadCodeResponse firstState = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR));
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.RUNNABLE,
            null,
            Collections.<CodeStateDiagnostic>emptyList(),
            null));
        CompileCodeResponse second = uut.compileCode(new CompileCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            firstState.getStateToken()));
        ReadCodeResponse secondState = uut.readCode(new ReadCodeToolRequest(ScriptHost.ATTACHED_EDITOR));

        assertThat(first.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(firstState.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(second.getCodeState()).isEqualTo(CodeState.RUNNABLE);
        assertThat(secondState.getCodeState()).isEqualTo(CodeState.RUNNABLE);
    }

    @Test
    public void runCodeSuppressesMcpWaitingAiSummaryForDelayedDispatcherCompletion() {
        List<ToolCallSummary> summaries = new ArrayList<ToolCallSummary>();
        AiCodeToolSet uut = new AiCodeToolSet(
            newRunCodeHostService(CodeState.WAITING_FOR_USER_RUN, ScriptHost.AI),
            null,
            summaries::add,
            ToolCaller.MCP);

        RunCodeResponse response = uut.runCode(new RunCodeToolRequest(ScriptHost.AI, token("args")));

        assertThat(response.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(summaries).isEmpty();
    }

    @Test
    public void runCodePublishesChatWaitingAiSummaryImmediately() {
        List<ToolCallSummary> summaries = new ArrayList<ToolCallSummary>();
        AiCodeToolSet uut = new AiCodeToolSet(
            newRunCodeHostService(CodeState.WAITING_FOR_USER_RUN, ScriptHost.AI),
            null,
            summaries::add,
            ToolCaller.CHAT);

        RunCodeResponse response = uut.runCode(new RunCodeToolRequest(ScriptHost.AI, token("args")));

        assertThat(response.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(summaries).extracting(ToolCallSummary::getSummaryText)
            .containsExactly("runCode: codeState=WAITING_FOR_USER_RUN, host=AI");
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

    private AiCodeHostService newRunCodeHostService(CodeState codeState, ScriptHost host) {
        return new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RunCodeResponse runCode(RunCodeRequest request) {
                return new RunCodeResponse(
                    host,
                    "text/x-freeplane-script-groovy",
                    codeState,
                    ScriptRunInitiator.AI,
                    token("args"),
                    null,
                    null,
                    null,
                    null);
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

    private AiCodeHostService newDetachedCodeHostService() {
        return new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return new ReadCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    null,
                    CodeState.NO_CODE,
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
        return new CodeStateToken("code", stateFingerprint);
    }

    private static class FakeCodeEditor implements AiCodeEditor {
        private CodeStateContent content;
        private CompileCodeResponse compileResponse = new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.RUNNABLE,
            token("initial"),
            Collections.<CodeStateDiagnostic>emptyList(),
            null);
        private RunCodeResponse runResponse = new RunCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.RUN_SUCCEEDED,
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
