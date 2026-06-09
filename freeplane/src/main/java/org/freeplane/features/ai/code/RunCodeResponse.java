package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RunCodeResponse {
    private final ScriptHost host;
    private final String contentType;
    private final CodeLifecycleStatus status;
    private final ScriptRunInitiator runInitiator;
    private final CodeStateToken stateToken;
    private final List<CodeStateDiagnostic> diagnostics;
    private final String errorMessage;
    private final String stdout;
    private final Object structuredResult;

    public RunCodeResponse(ScriptHost host,
                           String contentType,
                           CodeLifecycleStatus status,
                           ScriptRunInitiator runInitiator,
                           CodeStateToken stateToken,
                           List<CodeStateDiagnostic> diagnostics,
                           String errorMessage,
                           String stdout,
                           Object structuredResult) {
        this.host = host;
        this.contentType = contentType;
        this.status = status;
        this.runInitiator = runInitiator;
        this.stateToken = stateToken;
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

    public CodeLifecycleStatus getStatus() {
        return status;
    }

    public ScriptRunInitiator getRunInitiator() {
        return runInitiator;
    }

    public CodeStateToken getStateToken() {
        return stateToken;
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
