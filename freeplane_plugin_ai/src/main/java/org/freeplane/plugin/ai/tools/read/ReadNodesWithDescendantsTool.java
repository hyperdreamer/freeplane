package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;
import org.freeplane.plugin.ai.tools.content.AttributesContentRequest;
import org.freeplane.plugin.ai.tools.content.CloneMetadata;
import org.freeplane.plugin.ai.tools.content.ConnectorItem;
import org.freeplane.plugin.ai.tools.content.EditableContentRequest;
import org.freeplane.plugin.ai.tools.content.IconsContent;
import org.freeplane.plugin.ai.tools.content.IconsContentRequest;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.content.NodeContentRequest;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.NodeLinkMetadataReader;
import org.freeplane.plugin.ai.tools.content.TagsContent;
import org.freeplane.plugin.ai.tools.content.TagsContentRequest;
import org.freeplane.plugin.ai.tools.content.TextualContent;
import org.freeplane.plugin.ai.tools.content.TextualContentRequest;
import org.freeplane.plugin.ai.tools.search.OmissionReason;
import org.freeplane.plugin.ai.tools.search.Omissions;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class ReadNodesWithDescendantsTool {
    private static final int SUMMARY_PREVIEW_TEXT_LIMIT = 20;
    private static final int SUMMARY_PREVIEW_COUNT_LIMIT = 3;
    private static final NodeContentRequest FULL_CONTENT_REQUEST = new NodeContentRequest(
        new TextualContentRequest(true, true, true),
        new AttributesContentRequest(true),
        new TagsContentRequest(true),
        new IconsContentRequest(true),
        null);
    private static final String FOCUS_BLOCK_SEPARATOR = "\n\n";
    private static final String INDENT_UNIT = "  ";

    private static final class PlainTextRenderResult {
        private final String plainText;
        private final boolean hasRenderedNode;
        private final int omittedChildCount;
        private final int omittedDescendantCount;

        private PlainTextRenderResult(String plainText, boolean hasRenderedNode,
                                      int omittedChildCount, int omittedDescendantCount) {
            this.plainText = plainText;
            this.hasRenderedNode = hasRenderedNode;
            this.omittedChildCount = omittedChildCount;
            this.omittedDescendantCount = omittedDescendantCount;
        }
    }

    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeContentItemReader nodeContentItemReader;
    private final TextController textController;
    private final ObjectMapper objectMapper;

    public ReadNodesWithDescendantsTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                                        NodeContentItemReader nodeContentItemReader) {
        this(availableMaps, mapAccessListener, nodeContentItemReader, TextController.getController(),
            new ObjectMapper());
    }

    public ReadNodesWithDescendantsTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                                        NodeContentItemReader nodeContentItemReader,
                                        TextController textController) {
        this(availableMaps, mapAccessListener, nodeContentItemReader, textController, new ObjectMapper());
    }

    ReadNodesWithDescendantsTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                                 NodeContentItemReader nodeContentItemReader,
                                 TextController textController, ObjectMapper objectMapper) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeContentItemReader = Objects.requireNonNull(nodeContentItemReader, "nodeContentItemReader");
        this.textController = Objects.requireNonNull(textController, "textController");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ReadNodesWithDescendantsResponse readNodesWithDescendants(ReadNodesWithDescendantsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<String> nodeIdentifiers = resolveNodeIdentifiers(mapModel, request.getNodeIdentifiers());
        validateDuplicateNodeIdentifiers(nodeIdentifiers);
        List<NodeModel> focusNodes = resolveFocusNodes(mapModel, nodeIdentifiers);
        List<ContextSection> sections = request.getContextSections();
        boolean includeQualifiers = sections.contains(ContextSection.QUALIFIERS);
        boolean includeHyperlink = sections.contains(ContextSection.HYPERLINK);
        boolean includeOutgoingConnectors = sections.contains(ContextSection.OUTGOING_CONNECTORS);
        boolean includeIncomingConnectors = sections.contains(ContextSection.INCOMING_CONNECTORS);
        boolean includeCloneMetadata = sections.contains(ContextSection.CLONE_METADATA);
        int fullContentDepth = request.getFullContentDepth();
        int additionalSummaryDepth = request.getAdditionalSummaryDepth();
        if (fullContentDepth < 0 || additionalSummaryDepth < 0) {
            throw new IllegalArgumentException("Depth values must be 0 or greater");
        }
        int maxCharacters = request.getMaxCharacters();
        boolean enforceBudget = focusNodes.size() > 1 || fullContentDepth > 0 || additionalSummaryDepth > 0;
        List<ReadNodesWithDescendantsItem> items = new ArrayList<>();
        List<String> focusNodePreviewTexts = new ArrayList<>();
        int budgetUsed = 0;
        int omittedFocusNodeCount = 0;
        for (NodeModel focusNode : focusNodes) {
            ReadNodesWithDescendantsItem item = buildItemForFocusNode(
                focusNode,
                request,
                includeQualifiers,
                includeHyperlink,
                includeOutgoingConnectors,
                includeIncomingConnectors,
                includeCloneMetadata,
                enforceBudget,
                budgetUsed);
            if (item == null) {
                omittedFocusNodeCount = focusNodes.size() - items.size();
                break;
            }
            int itemSize = measureSerializedLength(item);
            if (enforceBudget) {
                if (budgetUsed + itemSize > maxCharacters) {
                    omittedFocusNodeCount = focusNodes.size() - items.size();
                    break;
                }
                budgetUsed += itemSize;
            }
            items.add(item);
            addPreviewText(focusNode, focusNodePreviewTexts);
        }
        Omissions responseOmissions = buildResponseOmissions(omittedFocusNodeCount);
        return new ReadNodesWithDescendantsResponse(mapIdentifierValue, items, responseOmissions, focusNodePreviewTexts);
    }

    public ReadNodesWithDescendantsAsPlainTextResponse readNodesWithDescendantsAsPlainText(
        ReadNodesWithDescendantsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<String> nodeIdentifiers = resolveNodeIdentifiers(mapModel, request.getNodeIdentifiers());
        validateDuplicateNodeIdentifiers(nodeIdentifiers);
        List<NodeModel> focusNodes = resolveFocusNodes(mapModel, nodeIdentifiers);
        List<ContextSection> sections = request.getContextSections();
        boolean includeQualifiers = sections.contains(ContextSection.QUALIFIERS);
        boolean includeHyperlink = sections.contains(ContextSection.HYPERLINK);
        boolean includeOutgoingConnectors = sections.contains(ContextSection.OUTGOING_CONNECTORS);
        boolean includeIncomingConnectors = sections.contains(ContextSection.INCOMING_CONNECTORS);
        boolean includeCloneMetadata = sections.contains(ContextSection.CLONE_METADATA);
        int fullContentDepth = request.getFullContentDepth();
        int additionalSummaryDepth = request.getAdditionalSummaryDepth();
        if (fullContentDepth < 0 || additionalSummaryDepth < 0) {
            throw new IllegalArgumentException("Depth values must be 0 or greater");
        }
        int maxCharacters = request.getMaxCharacters();
        StringBuilder plainText = new StringBuilder();
        List<String> focusNodePreviewTexts = new ArrayList<>();
        int omittedFocusNodeCount = 0;
        int omittedChildCount = 0;
        int omittedDescendantCount = 0;
        for (int focusIndex = 0; focusIndex < focusNodes.size(); focusIndex += 1) {
            NodeModel focusNode = focusNodes.get(focusIndex);
            int remainingBudget = maxCharacters - plainText.length();
            if (remainingBudget <= 0) {
                omittedFocusNodeCount = focusNodes.size() - focusIndex;
                break;
            }
            if (plainText.length() > 0) {
                if (FOCUS_BLOCK_SEPARATOR.length() > remainingBudget) {
                    omittedFocusNodeCount = focusNodes.size() - focusIndex;
                    break;
                }
                remainingBudget -= FOCUS_BLOCK_SEPARATOR.length();
            }
            PlainTextRenderResult renderResult = renderFocusNodeAsPlainText(
                focusNode,
                request,
                includeQualifiers,
                includeHyperlink,
                includeOutgoingConnectors,
                includeIncomingConnectors,
                includeCloneMetadata,
                remainingBudget);
            if (!renderResult.hasRenderedNode) {
                omittedFocusNodeCount = focusNodes.size() - focusIndex;
                break;
            }
            if (plainText.length() > 0) {
                plainText.append(FOCUS_BLOCK_SEPARATOR);
            }
            plainText.append(renderResult.plainText);
            omittedChildCount += renderResult.omittedChildCount;
            omittedDescendantCount += renderResult.omittedDescendantCount;
            addPreviewText(focusNode, focusNodePreviewTexts);
        }
        Omissions omissions = buildResponseOmissions(
            omittedFocusNodeCount,
            omittedChildCount,
            omittedDescendantCount);
        return new ReadNodesWithDescendantsAsPlainTextResponse(
            mapIdentifierValue,
            plainText.toString(),
            omissions,
            focusNodePreviewTexts);
    }

    public FetchNodesForEditingResponse fetchNodesForEditing(FetchNodesForEditingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        if (request.getEditableContentFields() == null || request.getEditableContentFields().isEmpty()) {
            throw new IllegalArgumentException("Missing editableContentFields");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<String> nodeIdentifiers = resolveNodeIdentifiers(mapModel, request.getNodeIdentifiers());
        validateDuplicateNodeIdentifiers(nodeIdentifiers);
        List<NodeModel> focusNodes = resolveFocusNodes(mapModel, nodeIdentifiers);
        EditableContentRequest editableContentRequest = new EditableContentRequest(request.getEditableContentFields());
        NodeContentRequest contentRequest = new NodeContentRequest(null, null, null, null, editableContentRequest);
        List<NodeContentItem> items = new ArrayList<>();
        for (NodeModel focusNode : focusNodes) {
            NodeContentResponse content = nodeContentItemReader.readNodeContent(
                focusNode,
                contentRequest,
                NodeContentPreset.FULL);
            items.add(nodeContentItemReader.readNodeContentItem(focusNode, content, true, false));
        }
        return new FetchNodesForEditingResponse(mapIdentifierValue, items);
    }

    private ReadNodesWithDescendantsItem buildItemForFocusNode(NodeModel focusNode,
                                                           ReadNodesWithDescendantsRequest request,
                                                           boolean includeQualifiers,
                                                           boolean includeHyperlink,
                                                           boolean includeOutgoingConnectors,
                                                           boolean includeIncomingConnectors,
                                                           boolean includeCloneMetadata,
                                                           boolean enforceBudget,
                                                           int budgetUsed) {
        List<NodeDepthItem> allNodes = buildNodeDepthItems(
            focusNode,
            request,
            includeQualifiers,
            includeHyperlink,
            includeOutgoingConnectors,
            includeIncomingConnectors,
            includeCloneMetadata);
        if (allNodes.isEmpty()) {
            return null;
        }
        List<ContextSection> contextSections = request.getContextSections();
        NodeDepthItem parentNode = buildParentNodeItem(
            focusNode,
            contextSections,
            includeQualifiers,
            includeHyperlink,
            includeOutgoingConnectors,
            includeIncomingConnectors,
            includeCloneMetadata);
        String breadcrumbPath = contextSections.contains(ContextSection.BREADCRUMB_PATH)
            ? buildBreadcrumbPath(focusNode)
            : null;
        List<NodeDepthItem> nodes = new ArrayList<>();
        ReadNodesWithDescendantsItem baseItem = new ReadNodesWithDescendantsItem(nodes, parentNode, breadcrumbPath, null);
        int omittedChildCount = 0;
        int omittedDescendantCount = 0;
        for (int index = 0; index < allNodes.size(); index += 1) {
            NodeDepthItem nodeDepthItem = allNodes.get(index);
            nodes.add(nodeDepthItem);
            int itemSize = measureSerializedLength(baseItem);
            if (enforceBudget && budgetUsed + itemSize > request.getMaxCharacters()) {
                nodes.remove(nodes.size() - 1);
                if (nodes.isEmpty()) {
                    return null;
                }
                for (int remaining = index; remaining < allNodes.size(); remaining += 1) {
                    NodeDepthItem omittedNode = allNodes.get(remaining);
                    if (omittedNode.getDepth() == 1) {
                        omittedChildCount += 1;
                    } else if (omittedNode.getDepth() > 1) {
                        omittedDescendantCount += 1;
                    }
                }
                break;
            }
        }
        Omissions omissions = omittedChildCount > 0 || omittedDescendantCount > 0
            ? new Omissions(null, omittedChildCount, omittedDescendantCount, null,
                Collections.singletonList(OmissionReason.TEXT_BUDGET))
            : null;
        return new ReadNodesWithDescendantsItem(nodes, parentNode, breadcrumbPath, omissions);
    }

    private PlainTextRenderResult renderFocusNodeAsPlainText(NodeModel focusNode,
                                                              ReadNodesWithDescendantsRequest request,
                                                              boolean includeQualifiers,
                                                              boolean includeHyperlink,
                                                              boolean includeOutgoingConnectors,
                                                              boolean includeIncomingConnectors,
                                                              boolean includeCloneMetadata,
                                                              int maximumCharacters) {
        List<NodeDepthItem> allNodes = buildNodeDepthItems(
            focusNode,
            request,
            includeQualifiers,
            includeHyperlink,
            includeOutgoingConnectors,
            includeIncomingConnectors,
            includeCloneMetadata);
        if (allNodes.isEmpty()) {
            return new PlainTextRenderResult("", false, 0, 0);
        }
        List<ContextSection> contextSections = request.getContextSections();
        NodeDepthItem parentNode = buildParentNodeItem(
            focusNode,
            contextSections,
            includeQualifiers,
            includeHyperlink,
            includeOutgoingConnectors,
            includeIncomingConnectors,
            includeCloneMetadata);
        String breadcrumbPath = contextSections.contains(ContextSection.BREADCRUMB_PATH)
            ? buildBreadcrumbPath(focusNode)
            : null;
        StringBuilder block = new StringBuilder();
        if (breadcrumbPath != null && !breadcrumbPath.isEmpty()) {
            appendLine(block, "breadcrumbPath: " + breadcrumbPath);
        }
        if (parentNode != null) {
            appendLine(block, "parentSummary:");
            appendBlockText(block, renderNodeContribution(parentNode, 1));
        }
        int renderedNodeCount = 0;
        int omittedChildCount = 0;
        int omittedDescendantCount = 0;
        for (int index = 0; index < allNodes.size(); index += 1) {
            NodeDepthItem node = allNodes.get(index);
            String nodeContribution = renderNodeContribution(node, node.getDepth());
            int additionalLength = nodeContribution.length();
            if (block.length() > 0) {
                additionalLength += 1;
            }
            if (block.length() + additionalLength > maximumCharacters) {
                if (renderedNodeCount == 0) {
                    return new PlainTextRenderResult("", false, 0, 0);
                }
                for (int remaining = index; remaining < allNodes.size(); remaining += 1) {
                    NodeDepthItem omittedNode = allNodes.get(remaining);
                    if (omittedNode.getDepth() == 1) {
                        omittedChildCount += 1;
                    } else if (omittedNode.getDepth() > 1) {
                        omittedDescendantCount += 1;
                    }
                }
                break;
            }
            appendBlockText(block, nodeContribution);
            renderedNodeCount += 1;
        }
        return new PlainTextRenderResult(block.toString(), renderedNodeCount > 0,
            omittedChildCount, omittedDescendantCount);
    }

    private String renderNodeContribution(NodeDepthItem nodeDepthItem, int indentationLevel) {
        StringBuilder builder = new StringBuilder();
        String indent = repeatIndent(indentationLevel);
        String continuationIndent = indent + INDENT_UNIT;
        String[] contentLines = splitLines(nodeDepthItem == null ? null : nodeDepthItem.getUnformattedText());
        String firstContentLine = contentLines.length == 0 ? "" : contentLines[0];
        StringBuilder firstLine = new StringBuilder(indent).append('-');
        boolean hasQualifiers = nodeDepthItem != null
            && nodeDepthItem.getQualifiers() != null
            && !nodeDepthItem.getQualifiers().isEmpty();
        if (!firstContentLine.isEmpty() || hasQualifiers) {
            firstLine.append(' ').append(firstContentLine);
        }
        if (hasQualifiers) {
            firstLine.append(" [").append(String.join(", ", nodeDepthItem.getQualifiers())).append(']');
        }
        appendLine(builder, firstLine.toString());
        for (int index = 1; index < contentLines.length; index += 1) {
            appendLine(builder, continuationIndent + contentLines[index]);
        }
        if (nodeDepthItem != null && nodeDepthItem.getHyperlink() != null) {
            appendLine(builder, continuationIndent + "hyperlink: " + nodeDepthItem.getHyperlink());
        }
        if (nodeDepthItem != null && nodeDepthItem.getOutgoingConnectors() != null) {
            for (ConnectorItem connector : nodeDepthItem.getOutgoingConnectors()) {
                appendLine(builder, continuationIndent + buildConnectorLine("outgoingConnector", connector));
            }
        }
        if (nodeDepthItem != null && nodeDepthItem.getIncomingConnectors() != null) {
            for (ConnectorItem connector : nodeDepthItem.getIncomingConnectors()) {
                appendLine(builder, continuationIndent + buildConnectorLine("incomingConnector", connector));
            }
        }
        if (nodeDepthItem != null && nodeDepthItem.getCloneMetadata() != null) {
            CloneMetadata cloneMetadata = nodeDepthItem.getCloneMetadata();
            List<String> cloneNodeIdentifiers = cloneMetadata.getCloneNodeIdentifiers();
            boolean hasCloneNodeIdentifiers = cloneNodeIdentifiers != null && !cloneNodeIdentifiers.isEmpty();
            if (cloneMetadata.isCloneTreeRoot() || cloneMetadata.isCloneTreeNode() || hasCloneNodeIdentifiers) {
                String identifiers = hasCloneNodeIdentifiers ? String.join(", ", cloneNodeIdentifiers) : "";
                appendLine(builder,
                    continuationIndent + "cloneMetadata: cloneTreeRoot=" + cloneMetadata.isCloneTreeRoot()
                        + ", cloneTreeNode=" + cloneMetadata.isCloneTreeNode()
                        + ", cloneNodeIdentifiers=" + identifiers);
            }
        }
        return builder.toString();
    }

    private String buildConnectorLine(String label, ConnectorItem connector) {
        if (connector == null) {
            return label + ":  ->  [sourceLabel=, middleLabel=, targetLabel=]";
        }
        String sourceNodeIdentifier = connector.getSourceNodeIdentifier() == null
            ? ""
            : connector.getSourceNodeIdentifier();
        String targetNodeIdentifier = connector.getTargetNodeIdentifier() == null
            ? ""
            : connector.getTargetNodeIdentifier();
        String sourceLabel = connector.getSourceLabel() == null ? "" : connector.getSourceLabel();
        String middleLabel = connector.getMiddleLabel() == null ? "" : connector.getMiddleLabel();
        String targetLabel = connector.getTargetLabel() == null ? "" : connector.getTargetLabel();
        return label + ": " + sourceNodeIdentifier + " -> " + targetNodeIdentifier
            + " [sourceLabel=" + sourceLabel
            + ", middleLabel=" + middleLabel
            + ", targetLabel=" + targetLabel + "]";
    }

    private String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split("\\r?\\n", -1);
    }

    private void appendBlockText(StringBuilder builder, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text);
    }

    private void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line == null ? "" : line);
    }

    private String repeatIndent(int indentationLevel) {
        int normalizedLevel = Math.max(0, indentationLevel);
        StringBuilder builder = new StringBuilder(normalizedLevel * INDENT_UNIT.length());
        for (int index = 0; index < normalizedLevel; index += 1) {
            builder.append(INDENT_UNIT);
        }
        return builder.toString();
    }

    private List<NodeDepthItem> buildNodeDepthItems(NodeModel focusNode,
                                                    ReadNodesWithDescendantsRequest request,
                                                    boolean includeQualifiers,
                                                    boolean includeHyperlink,
                                                    boolean includeOutgoingConnectors,
                                                    boolean includeIncomingConnectors,
                                                    boolean includeCloneMetadata) {
        int maximumDepth = request.getFullContentDepth() + request.getAdditionalSummaryDepth();
        List<NodeDepthItem> nodes = new ArrayList<>();
        Deque<NodeModel> stack = new ArrayDeque<>();
        Deque<Integer> depthStack = new ArrayDeque<>();
        stack.push(focusNode);
        depthStack.push(0);
        while (!stack.isEmpty()) {
            NodeModel current = stack.pop();
            int depth = depthStack.pop();
            if (depth > maximumDepth) {
                continue;
            }
            NodeDepthItem nodeDepthItem = buildNodeDepthItem(
                current,
                depth,
                request,
                includeQualifiers,
                includeHyperlink,
                includeOutgoingConnectors,
                includeIncomingConnectors,
                includeCloneMetadata);
            nodes.add(nodeDepthItem);
            if (depth < maximumDepth) {
                List<NodeModel> children = current.getChildren();
                for (int index = children.size() - 1; index >= 0; index -= 1) {
                    stack.push(children.get(index));
                    depthStack.push(depth + 1);
                }
            }
        }
        return nodes;
    }

    private NodeDepthItem buildNodeDepthItem(NodeModel nodeModel, int depth, ReadNodesWithDescendantsRequest request,
                                             boolean includeQualifiers,
                                             boolean includeHyperlink,
                                             boolean includeOutgoingConnectors,
                                             boolean includeIncomingConnectors,
                                             boolean includeCloneMetadata) {
        int fullContentDepth = request.getFullContentDepth();
        String unformattedText;
        if (depth <= fullContentDepth) {
            NodeContentResponse content = nodeContentItemReader.readNodeContent(
                nodeModel,
                FULL_CONTENT_REQUEST,
                NodeContentPreset.FULL);
            unformattedText = buildUnformattedText(content);
        } else {
            unformattedText = readBriefText(nodeModel);
        }
        List<String> qualifiers = includeQualifiers ? buildQualifiers(nodeModel) : null;
        String hyperlink = includeHyperlink ? NodeLinkMetadataReader.readHyperlink(nodeModel) : null;
        List<ConnectorItem> outgoingConnectors = includeOutgoingConnectors
            ? NodeLinkMetadataReader.readOutgoingConnectors(nodeModel)
            : null;
        List<ConnectorItem> incomingConnectors = includeIncomingConnectors
            ? NodeLinkMetadataReader.readIncomingConnectors(nodeModel)
            : null;
        CloneMetadata cloneMetadata = includeCloneMetadata ? NodeLinkMetadataReader.readCloneMetadata(nodeModel) : null;
        return new NodeDepthItem(nodeModel.createID(), depth, unformattedText, qualifiers,
            hyperlink, outgoingConnectors, incomingConnectors, cloneMetadata);
    }

    private NodeDepthItem buildParentNodeItem(NodeModel focusNode, List<ContextSection> sections,
                                              boolean includeQualifiers,
                                              boolean includeHyperlink,
                                              boolean includeOutgoingConnectors,
                                              boolean includeIncomingConnectors,
                                              boolean includeCloneMetadata) {
        if (!sections.contains(ContextSection.PARENT_SUMMARY)) {
            return null;
        }
        NodeModel parentNode = focusNode.getParentNode();
        if (parentNode == null) {
            return null;
        }
        String unformattedText = readBriefText(parentNode);
        List<String> qualifiers = includeQualifiers ? buildQualifiers(parentNode) : null;
        String hyperlink = includeHyperlink ? NodeLinkMetadataReader.readHyperlink(parentNode) : null;
        List<ConnectorItem> outgoingConnectors = includeOutgoingConnectors
            ? NodeLinkMetadataReader.readOutgoingConnectors(parentNode)
            : null;
        List<ConnectorItem> incomingConnectors = includeIncomingConnectors
            ? NodeLinkMetadataReader.readIncomingConnectors(parentNode)
            : null;
        CloneMetadata cloneMetadata = includeCloneMetadata ? NodeLinkMetadataReader.readCloneMetadata(parentNode) : null;
        return new NodeDepthItem(parentNode.createID(), -1, unformattedText, qualifiers,
            hyperlink, outgoingConnectors, incomingConnectors, cloneMetadata);
    }

    private String buildBreadcrumbPath(NodeModel nodeModel) {
        List<String> pathSegments = new ArrayList<>();
        NodeModel current = nodeModel;
        while (current != null) {
            if (!SummaryNode.isHidden(current)) {
                String text = readBriefText(current);
                if (text != null && !text.isEmpty()) {
                    pathSegments.add(text);
                }
            }
            current = current.getParentNode();
        }
        if (pathSegments.isEmpty()) {
            return null;
        }
        Collections.reverse(pathSegments);
        return String.join("/", pathSegments);
    }

    private List<String> resolveNodeIdentifiers(MapModel mapModel, List<String> nodeIdentifiers) {
        if (nodeIdentifiers != null && !nodeIdentifiers.isEmpty()) {
            return nodeIdentifiers;
        }
        NodeModel rootNode = mapModel.getRootNode();
        if (rootNode == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(rootNode.getID());
    }

    private void validateDuplicateNodeIdentifiers(List<String> nodeIdentifiers) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (String nodeIdentifier : nodeIdentifiers) {
            if (!seen.add(nodeIdentifier)) {
                duplicates.add(nodeIdentifier);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate node identifiers");
        }
    }

    private List<NodeModel> resolveFocusNodes(MapModel mapModel, List<String> nodeIdentifiers) {
        List<String> unknown = new ArrayList<>();
        List<NodeModel> focusNodes = new ArrayList<>(nodeIdentifiers.size());
        for (String nodeIdentifier : nodeIdentifiers) {
            NodeModel node = mapModel.getNodeForID(nodeIdentifier);
            if (node == null) {
                unknown.add(nodeIdentifier);
            } else {
                focusNodes.add(node);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown node identifiers: " + String.join(", ", unknown));
        }
        return focusNodes;
    }

    private Omissions buildResponseOmissions(int omittedFocusNodeCount) {
        return buildResponseOmissions(omittedFocusNodeCount, 0, 0);
    }

    private Omissions buildResponseOmissions(int omittedFocusNodeCount, int omittedChildCount,
                                             int omittedDescendantCount) {
        if (omittedFocusNodeCount == 0 && omittedChildCount == 0 && omittedDescendantCount == 0) {
            return null;
        }
        return new Omissions(
            omittedFocusNodeCount == 0 ? null : omittedFocusNodeCount,
            omittedChildCount == 0 ? null : omittedChildCount,
            omittedDescendantCount == 0 ? null : omittedDescendantCount,
            null,
            Collections.singletonList(OmissionReason.TEXT_BUDGET));
    }

    private int measureSerializedLength(Object item) {
        try {
            return objectMapper.writeValueAsBytes(item).length;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize read response.", error);
        }
    }

    private void addPreviewText(NodeModel focusNode, List<String> previews) {
        if (focusNode == null || previews == null || previews.size() >= SUMMARY_PREVIEW_COUNT_LIMIT) {
            return;
        }
        String previewText = textController.getShortPlainText(focusNode, SUMMARY_PREVIEW_TEXT_LIMIT, "");
        if (previewText != null && !previewText.isEmpty()) {
            previews.add(previewText);
        }
    }

    private String readBriefText(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        return textController.getShortPlainText(nodeModel);
    }

    private String buildUnformattedText(NodeContentResponse content) {
        if (content == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        TextualContent textualContent = content.getTextualContent();
        if (textualContent != null) {
            appendLabeledLine(lines, "Text", textualContent.getText());
            appendLabeledLine(lines, "Details", textualContent.getDetails());
            appendLabeledLine(lines, "Note", textualContent.getNote());
        }
        AttributesContent attributesContent = content.getAttributesContent();
        if (attributesContent != null && attributesContent.getAttributes() != null
            && !attributesContent.getAttributes().isEmpty()) {
            List<String> entries = new ArrayList<>(attributesContent.getAttributes().size());
            for (AttributeEntry attribute : attributesContent.getAttributes()) {
                if (attribute == null) {
                    continue;
                }
                String value = HtmlUtils.htmlToPlain(attribute.getValue());
                String name = attribute.getName();
                entries.add(name + "=" + (value == null ? "" : value));
            }
            appendLabeledLine(lines, "Attributes", String.join("; ", entries));
        }
        TagsContent tagsContent = content.getTagsContent();
        if (tagsContent != null && tagsContent.getTags() != null && !tagsContent.getTags().isEmpty()) {
            appendLabeledLine(lines, "Tags", String.join(", ", tagsContent.getTags()));
        }
        IconsContent iconsContent = content.getIconsContent();
        if (iconsContent != null && iconsContent.getDescriptions() != null && !iconsContent.getDescriptions().isEmpty()) {
            appendLabeledLine(lines, "Icons", String.join(", ", iconsContent.getDescriptions()));
        }
        if (lines.isEmpty()) {
            return null;
        }
        return String.join("\n", lines);
    }

    private void appendLabeledLine(List<String> lines, String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        lines.add(label + ": " + value);
    }

    public ToolCallSummary buildPlainTextToolCallSummary(ReadNodesWithDescendantsRequest request,
                                                         ReadNodesWithDescendantsAsPlainTextResponse response) {
        int textCharacters = response == null || response.getPlainText() == null ? 0 : response.getPlainText().length();
        String summaryText = "readNodesWithDescendantsAsPlainText: textCharacters=" + textCharacters;
        String focusNodeTexts = ToolCallSummaryFormatter.joinTextValues(
            response == null ? null : response.getFocusNodePreviewTexts(), "; ");
        if (!focusNodeTexts.isEmpty()) {
            summaryText = summaryText + ", focusNodeTexts=\"" + focusNodeTexts + "\"";
        }
        if (request != null && request.hasFullContentDepth()) {
            summaryText = summaryText + ", fullContentDepth=" + request.getFullContentDepth();
        }
        if (request != null && request.hasAdditionalSummaryDepth()) {
            summaryText = summaryText + ", additionalSummaryDepth=" + request.getAdditionalSummaryDepth();
        }
        if (request != null && !request.getContextSections().isEmpty()) {
            String sectionsText = ToolCallSummaryFormatter.joinEnumValues(
                request.getContextSections());
            if (!sectionsText.isEmpty()) {
                summaryText = summaryText + ", sections=" + sectionsText;
            }
        }
        return new ToolCallSummary("readNodesWithDescendantsAsPlainText", summaryText, false);
    }

    public ToolCallSummary buildPlainTextToolCallErrorSummary(ReadNodesWithDescendantsRequest request,
                                                              RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("readNodesWithDescendantsAsPlainText",
            "readNodesWithDescendantsAsPlainText error: " + safeMessage, true);
    }

    public ToolCallSummary buildToolCallSummary(ReadNodesWithDescendantsRequest request, ReadNodesWithDescendantsResponse response) {
        int itemCount = response == null || response.getItems() == null ? 0 : response.getItems().size();
        String summaryText = "readNodesWithDescendants: items=" + itemCount;
        String focusNodeTexts = ToolCallSummaryFormatter.joinTextValues(
            response == null ? null : response.getFocusNodePreviewTexts(), "; ");
        if (!focusNodeTexts.isEmpty()) {
            summaryText = summaryText + ", focusNodeTexts=\"" + focusNodeTexts + "\"";
        }
        if (request != null && request.hasFullContentDepth()) {
            summaryText = summaryText + ", fullContentDepth=" + request.getFullContentDepth();
        }
        if (request != null && request.hasAdditionalSummaryDepth()) {
            summaryText = summaryText + ", additionalSummaryDepth=" + request.getAdditionalSummaryDepth();
        }
        if (request != null && !request.getContextSections().isEmpty()) {
            String sectionsText = ToolCallSummaryFormatter.joinEnumValues(
                request.getContextSections());
            if (!sectionsText.isEmpty()) {
                summaryText = summaryText + ", sections=" + sectionsText;
            }
        }
        return new ToolCallSummary("readNodesWithDescendants", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(ReadNodesWithDescendantsRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("readNodesWithDescendants",
            "readNodesWithDescendants error: " + safeMessage, true);
    }

    public ToolCallSummary buildFetchToolCallSummary(FetchNodesForEditingRequest request, FetchNodesForEditingResponse response) {
        int itemCount = response == null || response.getItems() == null ? 0 : response.getItems().size();
        String summaryText = "fetchNodesForEditing: items=" + itemCount;
        return new ToolCallSummary("fetchNodesForEditing", summaryText, false);
    }

    public ToolCallSummary buildFetchToolCallErrorSummary(FetchNodesForEditingRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("fetchNodesForEditing", "fetchNodesForEditing error: " + safeMessage, true);
    }

    private List<String> buildQualifiers(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        List<String> qualifiers = new ArrayList<>();
        if (SummaryNode.isSummaryNode(nodeModel)) {
            qualifiers.add("summary_node");
        }
        if (SummaryNode.isFirstGroupNode(nodeModel)) {
            qualifiers.add("first_group_node");
        }
        if (qualifiers.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableList(qualifiers);
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier, error);
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        return value;
    }

}
