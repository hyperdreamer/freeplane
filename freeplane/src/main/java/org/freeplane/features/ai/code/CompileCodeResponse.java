package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompileCodeResponse {
    private final String codeId;
    private final ScriptHost host;
    private final String contentType;
    private final CodeLifecycleStatus status;
    private final String fingerprint;
    private final List<String> compilerDiagnostics;
    private final String errorMessage;
    private final Integer lineNumber;

    public CompileCodeResponse(String codeId,
                               ScriptHost host,
                               String contentType,
                               CodeLifecycleStatus status,
                               String fingerprint,
                               List<String> compilerDiagnostics,
                               String errorMessage,
                               Integer lineNumber) {
        this.codeId = codeId;
        this.host = host;
        this.contentType = contentType;
        this.status = status;
        this.fingerprint = fingerprint;
        this.compilerDiagnostics = compilerDiagnostics == null
            ? null
            : Collections.unmodifiableList(new ArrayList<String>(compilerDiagnostics));
        this.errorMessage = errorMessage;
        this.lineNumber = lineNumber;
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
}
