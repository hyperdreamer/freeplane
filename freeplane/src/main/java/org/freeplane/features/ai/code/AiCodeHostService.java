package org.freeplane.features.ai.code;

public interface AiCodeHostService {
    ReadCodeResponse readCode(ReadCodeRequest request);

    WriteCodeResponse writeCode(WriteCodeRequest request);

    CompileCodeResponse compileCode(CompileCodeRequest request);

    RunCodeResponse runCode(RunCodeRequest request);

    AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request);

    void addRunListener(AiCodeRunListener listener);

    void removeRunListener(AiCodeRunListener listener);
}
