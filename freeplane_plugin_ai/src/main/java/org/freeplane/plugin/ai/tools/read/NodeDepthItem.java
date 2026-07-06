package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.CloneMetadata;
import org.freeplane.plugin.ai.tools.content.ConnectorItem;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeDepthItem {
    private final String nodeIdentifier;
    private final int depth;
    @Description("Structured node content. String values are plain text, not HTML.")
    private final ReadNodeContent content;
    @Description("Qualifiers when requested: summary_node, first_group_node.")
    private final List<String> qualifiers;
    private final String hyperlink;
    private final List<ConnectorItem> outgoingConnectors;
    private final List<ConnectorItem> incomingConnectors;
    private final CloneMetadata cloneMetadata;

    @JsonCreator
    public NodeDepthItem(@JsonProperty("nodeIdentifier") String nodeIdentifier,
                         @JsonProperty("depth") int depth,
                         @JsonProperty("content") ReadNodeContent content,
                         @JsonProperty("qualifiers") List<String> qualifiers,
                         @JsonProperty("hyperlink") String hyperlink,
                         @JsonProperty("outgoingConnectors") List<ConnectorItem> outgoingConnectors,
                         @JsonProperty("incomingConnectors") List<ConnectorItem> incomingConnectors,
                         @JsonProperty("cloneMetadata") CloneMetadata cloneMetadata) {
        this.nodeIdentifier = nodeIdentifier;
        this.depth = depth;
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

    public int getDepth() {
        return depth;
    }

    public ReadNodeContent getContent() {
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
