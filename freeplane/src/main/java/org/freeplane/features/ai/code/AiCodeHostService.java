package org.freeplane.features.ai.code;

public interface AiCodeHostService {
    ReadCodeResponse readCode(ReadCodeRequest request);

    WriteCodeResponse writeCode(WriteCodeRequest request);

    CompileCodeResponse compileCode(CompileCodeRequest request);

    RunScriptResponse runScript(RunScriptRequest request);

    void addRunListener(AiCodeRunListener listener);

    void removeRunListener(AiCodeRunListener listener);
}
