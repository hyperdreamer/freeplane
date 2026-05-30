package org.freeplane.plugin.ai.code;

public class OverwriteAttachedEditorContentResponse {
    private final String sourceFingerprint;

    public OverwriteAttachedEditorContentResponse(String sourceFingerprint) {
        this.sourceFingerprint = sourceFingerprint;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }
}
