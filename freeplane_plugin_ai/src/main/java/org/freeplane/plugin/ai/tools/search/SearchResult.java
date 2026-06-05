package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;

public class SearchResult {
    private final String nodeIdentifier;
    private final NodeContentResponse content;

    @JsonCreator
    public SearchResult(@JsonProperty("nodeIdentifier") String nodeIdentifier,
                        @JsonProperty("content") NodeContentResponse content) {
        this.nodeIdentifier = nodeIdentifier;
        this.content = content;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public NodeContentResponse getContent() {
        return content;
    }
}
