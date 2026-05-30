package org.freeplane.plugin.ai.code;

public class ReadAttachedEditorResponse {
    private final boolean attached;
    private final String contentType;
    private final String text;
    private final String sourceFingerprint;
    private final boolean supportsCompilation;
    private final boolean hasIssue;

    public ReadAttachedEditorResponse(boolean attached,
                                      String contentType,
                                      String text,
                                      String sourceFingerprint,
                                      boolean supportsCompilation,
                                      boolean hasIssue) {
        this.attached = attached;
        this.contentType = contentType;
        this.text = text;
        this.sourceFingerprint = sourceFingerprint;
        this.supportsCompilation = supportsCompilation;
        this.hasIssue = hasIssue;
    }

    public static ReadAttachedEditorResponse detached() {
        return new ReadAttachedEditorResponse(false, null, null, null, false, false);
    }

    public boolean isAttached() {
        return attached;
    }

    public String getContentType() {
        return contentType;
    }

    public String getText() {
        return text;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public boolean isSupportsCompilation() {
        return supportsCompilation;
    }

    public boolean isHasIssue() {
        return hasIssue;
    }
}
