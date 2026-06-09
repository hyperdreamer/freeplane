package org.freeplane.features.ai.code;

public class WriteCodeResponse {
    private final ScriptHost host;
    private final String contentType;
    private final CodeLifecycleStatus status;
    private final CodeStateToken stateToken;

    public WriteCodeResponse(ScriptHost host,
                             String contentType,
                             CodeLifecycleStatus status,
                             CodeStateToken stateToken) {
        this.host = host;
        this.contentType = contentType;
        this.status = status;
        this.stateToken = stateToken;
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

    public CodeStateToken getStateToken() {
        return stateToken;
    }
}
