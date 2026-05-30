package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiChatCodeOperationResult {
    private final boolean successful;
    private final List<String> compilerDiagnostics;
    private final String standardOutput;
    private final String result;
    private final String errorCategory;
    private final String errorMessage;
    private final Integer lineNumber;
    private final String sourceFingerprint;

    public AiChatCodeOperationResult(boolean successful,
                                     List<String> compilerDiagnostics,
                                     String standardOutput,
                                     String result,
                                     String errorCategory,
                                     String errorMessage,
                                     Integer lineNumber,
                                     String sourceFingerprint) {
        this.successful = successful;
        this.compilerDiagnostics = compilerDiagnostics == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(compilerDiagnostics));
        this.standardOutput = standardOutput;
        this.result = result;
        this.errorCategory = errorCategory;
        this.errorMessage = errorMessage;
        this.lineNumber = lineNumber;
        this.sourceFingerprint = sourceFingerprint;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<String> getCompilerDiagnostics() {
        return compilerDiagnostics;
    }

    public String getStandardOutput() {
        return standardOutput;
    }

    public String getResult() {
        return result;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }
}
