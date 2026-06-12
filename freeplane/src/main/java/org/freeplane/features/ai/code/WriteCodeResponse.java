package org.freeplane.features.ai.code;

public class WriteCodeResponse {
    private final ScriptHost host;
    private final String contentType;
    private final CodeState codeState;
    private final CodeStateToken stateToken;

    public WriteCodeResponse(ScriptHost host,
                             String contentType,
                             CodeState codeState,
                             CodeStateToken stateToken) {
        this.host = host;
        this.contentType = contentType;
        this.codeState = codeState;
        this.stateToken = stateToken;
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
}
