package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNode;

public class NodeContentItemReader {
    private final NodeContentReader nodeContentReader;

    public NodeContentItemReader(NodeContentReader nodeContentReader) {
        this.nodeContentReader = Objects.requireNonNull(nodeContentReader, "nodeContentReader");
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentPreset preset) {
        return readNodeContentItem(nodeModel, preset, true, true, false);
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentPreset preset,
                                               boolean includesNodeIdentifiers) {
        return readNodeContentItem(nodeModel, preset, includesNodeIdentifiers, true, false);
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentPreset preset,
                                               boolean includesNodeIdentifiers, boolean includesQualifiers) {
        return readNodeContentItem(nodeModel, preset, includesNodeIdentifiers, includesQualifiers, false);
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentPreset preset,
                                               boolean includesNodeIdentifiers, boolean includesQualifiers,
                                               boolean includesLinkMetadata) {
        if (nodeModel == null) {
            return null;
        }
        String nodeIdentifier = includesNodeIdentifiers ? nodeModel.createID() : null;
        NodeContentResponse content = nodeContentReader.readNodeContent(nodeModel, preset);
        List<String> qualifiers = includesQualifiers ? buildQualifiers(nodeModel) : null;
        LinkMetadata linkMetadata = includesLinkMetadata ? buildLinkMetadata(nodeModel) : null;
        return new NodeContentItem(nodeIdentifier, content, qualifiers,
            linkMetadata == null ? null : linkMetadata.hyperlink,
            linkMetadata == null ? null : linkMetadata.outgoingConnectors,
            linkMetadata == null ? null : linkMetadata.incomingConnectors,
            linkMetadata == null ? null : linkMetadata.cloneMetadata);
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentResponse content,
                                               boolean includesNodeIdentifiers) {
        return readNodeContentItem(nodeModel, content, includesNodeIdentifiers, true, false);
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentResponse content,
                                               boolean includesNodeIdentifiers, boolean includesQualifiers) {
        return readNodeContentItem(nodeModel, content, includesNodeIdentifiers, includesQualifiers, false);
    }

    public NodeContentItem readNodeContentItem(NodeModel nodeModel, NodeContentResponse content,
                                               boolean includesNodeIdentifiers, boolean includesQualifiers,
                                               boolean includesLinkMetadata) {
        if (nodeModel == null) {
            return null;
        }
        String nodeIdentifier = includesNodeIdentifiers ? nodeModel.createID() : null;
        List<String> qualifiers = includesQualifiers ? buildQualifiers(nodeModel) : null;
        LinkMetadata linkMetadata = includesLinkMetadata ? buildLinkMetadata(nodeModel) : null;
        return new NodeContentItem(nodeIdentifier, content, qualifiers,
            linkMetadata == null ? null : linkMetadata.hyperlink,
            linkMetadata == null ? null : linkMetadata.outgoingConnectors,
            linkMetadata == null ? null : linkMetadata.incomingConnectors,
            linkMetadata == null ? null : linkMetadata.cloneMetadata);
    }

    public NodeContentResponse readNodeContent(NodeModel nodeModel, NodeContentRequest request,
                                               NodeContentPreset fallbackPreset) {
        return nodeContentReader.readNodeContent(nodeModel, request, fallbackPreset);
    }

    public boolean matchesNodeContent(NodeModel nodeModel, NodeContentRequest request,
                                      NodeContentValueMatcher valueMatcher) {
        return nodeContentReader.matches(nodeModel, request, valueMatcher);
    }

    private List<String> buildQualifiers(NodeModel nodeModel) {
        List<String> qualifiers = new ArrayList<>();
        if (SummaryNode.isSummaryNode(nodeModel)) {
            qualifiers.add("summary_node");
        }
        if (SummaryNode.isFirstGroupNode(nodeModel)) {
            qualifiers.add("first_group_node");
        }
        if (qualifiers.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(qualifiers);
    }

    private LinkMetadata buildLinkMetadata(NodeModel nodeModel) {
        String hyperlink = NodeLinkMetadataReader.readHyperlink(nodeModel);
        List<ConnectorItem> outgoingConnectors = NodeLinkMetadataReader.readOutgoingConnectors(nodeModel);
        List<ConnectorItem> incomingConnectors = NodeLinkMetadataReader.readIncomingConnectors(nodeModel);
        CloneMetadata cloneMetadata = NodeLinkMetadataReader.readCloneMetadata(nodeModel);
        return new LinkMetadata(hyperlink, outgoingConnectors, incomingConnectors, cloneMetadata);
    }

    private static class LinkMetadata {
        private final String hyperlink;
        private final List<ConnectorItem> outgoingConnectors;
        private final List<ConnectorItem> incomingConnectors;
        private final CloneMetadata cloneMetadata;

        private LinkMetadata(String hyperlink, List<ConnectorItem> outgoingConnectors,
                             List<ConnectorItem> incomingConnectors, CloneMetadata cloneMetadata) {
            this.hyperlink = hyperlink;
            this.outgoingConnectors = outgoingConnectors;
            this.incomingConnectors = incomingConnectors;
            this.cloneMetadata = cloneMetadata;
        }
    }
}
