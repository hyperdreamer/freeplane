package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RunScriptResponse {
    private final String codeId;
    private final ScriptHost host;
    private final String contentType;
    private final CodeLifecycleStatus status;
    private final ScriptRunInitiator runInitiator;
    private final String fingerprint;
    private final List<String> compilerDiagnostics;
    private final String errorMessage;
    private final Integer lineNumber;
    private final String stdout;
    private final Object structuredResult;

    public RunScriptResponse(String codeId,
                             ScriptHost host,
                             String contentType,
                             CodeLifecycleStatus status,
                             ScriptRunInitiator runInitiator,
                             String fingerprint,
                             List<String> compilerDiagnostics,
                             String errorMessage,
                             Integer lineNumber,
                             String stdout,
                             Object structuredResult) {
        this.codeId = codeId;
        this.host = host;
        this.contentType = contentType;
        this.status = status;
        this.runInitiator = runInitiator;
        this.fingerprint = fingerprint;
        this.compilerDiagnostics = compilerDiagnostics == null
            ? null
            : Collections.unmodifiableList(new ArrayList<String>(compilerDiagnostics));
        this.errorMessage = errorMessage;
        this.lineNumber = lineNumber;
        this.stdout = stdout;
        this.structuredResult = structuredResult;
    }

    public String getCodeId() {
        return codeId;
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

    public String getFingerprint() {
        return fingerprint;
    }

    public List<String> getCompilerDiagnostics() {
        return compilerDiagnostics;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getStdout() {
        return stdout;
    }

    public Object getStructuredResult() {
        return structuredResult;
    }
}
