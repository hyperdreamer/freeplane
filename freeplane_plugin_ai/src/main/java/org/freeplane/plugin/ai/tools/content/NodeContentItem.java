package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeContentItem {
    private final String nodeIdentifier;
    private final NodeContentResponse content;
    @Description("Qualifiers when requested: summary_node, first_group_node.")
    private final List<String> qualifiers;
    private final String hyperlink;
    private final List<ConnectorItem> outgoingConnectors;
    private final List<ConnectorItem> incomingConnectors;
    private final CloneMetadata cloneMetadata;

    @JsonCreator
    public NodeContentItem(@JsonProperty("nodeIdentifier") String nodeIdentifier,
                           @JsonProperty("content") NodeContentResponse content,
                           @JsonProperty("qualifiers") List<String> qualifiers,
                           @JsonProperty("hyperlink") String hyperlink,
                           @JsonProperty("outgoingConnectors") List<ConnectorItem> outgoingConnectors,
                           @JsonProperty("incomingConnectors") List<ConnectorItem> incomingConnectors,
                           @JsonProperty("cloneMetadata") CloneMetadata cloneMetadata) {
        this.nodeIdentifier = nodeIdentifier;
        this.content = content;
        this.qualifiers = qualifiers;
        this.hyperlink = hyperlink;
        this.outgoingConnectors = outgoingConnectors;
        this.incomingConnectors = incomingConnectors;
        this.cloneMetadata = cloneMetadata;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public NodeContentResponse getContent() {
        return content;
    }

    public List<String> getQualifiers() {
        return qualifiers;
    }

    public String getHyperlink() {
        return hyperlink;
    }

    public List<ConnectorItem> getOutgoingConnectors() {
        return outgoingConnectors;
    }

    public List<ConnectorItem> getIncomingConnectors() {
        return incomingConnectors;
    }

    public CloneMetadata getCloneMetadata() {
        return cloneMetadata;
    }
}
