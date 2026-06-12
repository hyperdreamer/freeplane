package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompileCodeResponse {
    private final ScriptHost host;
    private final String contentType;
    private final CodeState codeState;
    private final CodeStateToken stateToken;
    private final List<CodeStateDiagnostic> diagnostics;
    private final String errorMessage;

    public CompileCodeResponse(ScriptHost host,
                               String contentType,
                               CodeState codeState,
                               CodeStateToken stateToken,
                               List<CodeStateDiagnostic> diagnostics,
                               String errorMessage) {
        this.host = host;
        this.contentType = contentType;
        this.codeState = codeState;
        this.stateToken = stateToken;
        this.diagnostics = diagnostics == null
            ? null
            : Collections.unmodifiableList(new ArrayList<CodeStateDiagnostic>(diagnostics));
        this.errorMessage = errorMessage;
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

    public CodeStateToken getStateToken() {
        return stateToken;
    }

    public List<CodeStateDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
