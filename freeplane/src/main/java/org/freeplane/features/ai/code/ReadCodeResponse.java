package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadCodeResponse {
    private final ScriptHost host;
    private final String contentType;
    private final CodeState codeState;
    private final ScriptRunInitiator runInitiator;
    private final CodeStateToken stateToken;
    private final CodeStateContent content;
    private final List<CodeStateDiagnostic> diagnostics;
    private final String errorMessage;
    private final String stdout;
    private final Object structuredResult;

    public ReadCodeResponse(ScriptHost host,
                            String contentType,
                            CodeState codeState,
                            ScriptRunInitiator runInitiator,
                            CodeStateToken stateToken,
                            CodeStateContent content,
                            List<CodeStateDiagnostic> diagnostics,
                            String errorMessage,
                            String stdout,
                            Object structuredResult) {
        this.host = host;
        this.contentType = contentType;
        this.codeState = codeState;
        this.runInitiator = runInitiator;
        this.stateToken = stateToken;
        this.content = content;
        this.diagnostics = diagnostics == null
            ? null
            : Collections.unmodifiableList(new ArrayList<CodeStateDiagnostic>(diagnostics));
        this.errorMessage = errorMessage;
        this.stdout = stdout;
        this.structuredResult = structuredResult;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getContentType() {
        return contentType;
    }

    public CodeState getCodeState() {
        return codeState;
    }

    public ScriptRunInitiator getRunInitiator() {
        return runInitiator;
    }

    public CodeStateToken getStateToken() {
        return stateToken;
    }

    public CodeStateContent getContent() {
        return content;
    }

    public List<CodeStateDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStdout() {
        return stdout;
    }

    public Object getStructuredResult() {
        return structuredResult;
    }
}
