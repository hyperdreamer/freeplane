package org.freeplane.features.ai.code;

public interface AiCodeEditor extends AiChatAttachableEditor {
    CompileCodeResponse compileCode(CompileCodeRequest request);

    RunCodeResponse runCode(RunCodeRequest request);
}
