package org.freeplane.plugin.ai.tools;

import java.util.List;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CodeStateContent;
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
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

public class AIToolSetBuilderTest {

    @Test
    public void buildToolObjectsIncludesAiCodeToolSetAndCodeHostService() {
        AiCodeHostService codeHostService = new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return new ReadCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    "text/plain",
                    CodeLifecycleStatus.READY,
                    null,
                    new CodeStateToken("code", "input", "fingerprint"),
                    new CodeStateContent("hello", null),
                    null,
                    null,
                    null,
                    null);
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                return new WriteCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    "text/plain",
                    CodeLifecycleStatus.READY,
                    new CodeStateToken("code", "input", "fingerprint"));
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                return new CompileCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    "text/plain",
                    CodeLifecycleStatus.READY,
                    new CodeStateToken("code", "input", "fingerprint"),
                    null,
                    null);
            }

            @Override
            public RunCodeResponse runCode(RunCodeRequest request) {
                return new RunCodeResponse(
                    ScriptHost.ATTACHED_EDITOR,
                    "text/plain",
                    CodeLifecycleStatus.SUCCEEDED,
                    ScriptRunInitiator.AI,
                    new CodeStateToken("code", "input", "fingerprint"),
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

        List<Object> toolObjects;
        try (MockedConstruction<AIToolSet> ignored = mockConstruction(AIToolSet.class)) {
            toolObjects = new AIToolSetBuilder()
                .availableMaps(mock(AvailableMaps.class))
                .textController(mock(TextController.class))
                .attributeController(mock(AttributeController.class))
                .iconController(mock(IconController.class))
                .mapController(mock(MMapController.class))
                .codeHostService(codeHostService)
                .buildToolObjects();
        }

        assertThat(toolObjects).hasSize(2);
        assertThat(toolObjects.get(0)).isInstanceOf(AIToolSet.class);
        assertThat(toolObjects.get(1)).isInstanceOf(AiCodeToolSet.class);
        AiCodeToolSet aiCodeToolSet = (AiCodeToolSet) toolObjects.get(1);
        assertThat(aiCodeToolSet.readCode(new org.freeplane.plugin.ai.tools.code.ReadCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            null)).getStatus())
            .isEqualTo(CodeLifecycleStatus.READY);
        assertThat(aiCodeToolSet.readCode(new org.freeplane.plugin.ai.tools.code.ReadCodeToolRequest(
            ScriptHost.ATTACHED_EDITOR,
            null)).getContent().getSourceText())
            .isEqualTo("hello");
    }
}
