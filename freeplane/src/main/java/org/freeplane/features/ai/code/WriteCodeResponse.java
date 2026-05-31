package org.freeplane.features.ai.code;

public class WriteCodeResponse {
    private final String codeId;
    private final ScriptHost host;
    private final String contentType;
    private final CodeLifecycleStatus status;
    private final String fingerprint;

    public WriteCodeResponse(String codeId,
                             ScriptHost host,
                             String contentType,
                             CodeLifecycleStatus status,
                             String fingerprint) {
        this.codeId = codeId;
        this.host = host;
        this.contentType = contentType;
        this.status = status;
        this.fingerprint = fingerprint;
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
}
