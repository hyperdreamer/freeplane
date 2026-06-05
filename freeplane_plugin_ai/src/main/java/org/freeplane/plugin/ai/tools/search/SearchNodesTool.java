package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.content.NodeContentRequest;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.NodeContentValueMatcher;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class SearchNodesTool {
    private static final int SUMMARY_PREVIEW_TEXT_LIMIT = 20;
    private static final int SUMMARY_PREVIEW_COUNT_LIMIT = 3;

    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeContentItemReader nodeContentItemReader;
    private final TextController textController;
    private final ObjectMapper objectMapper;

    public SearchNodesTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                           NodeContentItemReader nodeContentItemReader) {
        this(availableMaps, mapAccessListener, nodeContentItemReader, TextController.getController(),
            new ObjectMapper());
    }

    public SearchNodesTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                           NodeContentItemReader nodeContentItemReader,
                           TextController textController) {
        this(availableMaps, mapAccessListener, nodeContentItemReader, textController, new ObjectMapper());
    }

    SearchNodesTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                    NodeContentItemReader nodeContentItemReader,
                    TextController textController, ObjectMapper objectMapper) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeContentItemReader = Objects.requireNonNull(nodeContentItemReader, "nodeContentItemReader");
        this.textController = Objects.requireNonNull(textController, "textController");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public SearchNodesResponse searchNodes(SearchNodesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        String queryText = requireValue(request.getQueryText(), "queryText");
        SearchMatchingMode matchingMode = request.getMatchingMode();
        SearchCaseSensitivity caseSensitivity = request.getCaseSensitivity();
        Pattern regularExpression = matchingMode == SearchMatchingMode.REGULAR_EXPRESSION
            ? compileRegularExpression(queryText, caseSensitivity)
            : null;
        boolean includesBreadcrumbPath = request.getResultSections().contains(SearchResultSection.BREADCRUMB_PATH);
        int offset = request.getOffset();
        int limit = request.getLimit();
        int maximumTotalTextCharacters = request.getMaximumTotalTextCharacters();
        NodeContentRequest contentRequest = request.getNodeContentRequestForSearch();
        List<NodeModel> searchRoots = resolveSearchRoots(mapModel, request.getSubtreeRootNodeIdentifiers());
        NodeContentValueMatcher valueMatcher = new NodeContentValueMatcher(
            queryText, matchingMode, caseSensitivity, regularExpression);
        List<NodeModel> matches = new ArrayList<>();
        for (NodeModel root : searchRoots) {
            collectMatches(root, contentRequest, valueMatcher, matches);
        }
        int fromIndex = Math.min(offset, matches.size());
        int toIndex = Math.min(fromIndex + limit, matches.size());
        List<NodeModel> pageNodes = matches.subList(fromIndex, toIndex);
        List<SearchResultItem> results = new ArrayList<>(pageNodes.size());
        List<String> resultPreviewTexts = new ArrayList<>();
        int budgetUsed = 0;
        int omittedResultCount = 0;
        for (NodeModel nodeModel : pageNodes) {
            SearchResultItem resultItem = buildSearchResultItem(nodeModel, includesBreadcrumbPath);
            int itemSize = measureSerializedLength(resultItem);
            if (budgetUsed + itemSize > maximumTotalTextCharacters) {
                omittedResultCount += 1;
                continue;
            }
            results.add(resultItem);
            budgetUsed += itemSize;
            addPreviewText(nodeModel, resultPreviewTexts);
        }
        Omissions omissions = omittedResultCount > 0
            ? new Omissions(null, null, null, omittedResultCount, Collections.singletonList(OmissionReason.TEXT_BUDGET))
            : null;
        return new SearchNodesResponse(mapIdentifierValue, results, omissions, resultPreviewTexts);
    }

    private List<NodeModel> resolveSearchRoots(MapModel mapModel, List<String> subtreeRootNodeIdentifiers) {
        if (subtreeRootNodeIdentifiers == null || subtreeRootNodeIdentifiers.isEmpty()) {
            NodeModel rootNode = mapModel.getRootNode();
            return rootNode == null ? Collections.emptyList() : Collections.singletonList(rootNode);
        }
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String nodeIdentifier : subtreeRootNodeIdentifiers) {
            if (!seen.add(nodeIdentifier)) {
                duplicates.add(nodeIdentifier);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate subtree root node identifiers");
        }
        List<String> unknownIdentifiers = new ArrayList<>();
        List<NodeModel> roots = new ArrayList<>(subtreeRootNodeIdentifiers.size());
        for (String nodeIdentifier : subtreeRootNodeIdentifiers) {
            NodeModel node = mapModel.getNodeForID(nodeIdentifier);
            if (node == null) {
                unknownIdentifiers.add(nodeIdentifier);
            } else {
                roots.add(node);
            }
        }
        if (!unknownIdentifiers.isEmpty()) {
            throw new IllegalArgumentException("Unknown node identifiers: " + String.join(", ", unknownIdentifiers));
        }
        return roots;
    }

    private void collectMatches(NodeModel root, NodeContentRequest contentRequest,
                                NodeContentValueMatcher valueMatcher, List<NodeModel> matches) {
        if (root == null) {
            return;
        }
        Deque<NodeModel> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            NodeModel current = stack.pop();
            if (matchesNode(current, contentRequest, valueMatcher)) {
                matches.add(current);
            }
            List<NodeModel> children = current.getChildren();
            if (children == null || children.isEmpty()) {
                continue;
            }
            for (int index = children.size() - 1; index >= 0; index -= 1) {
                stack.push(children.get(index));
            }
        }
    }

    private boolean matchesNode(NodeModel nodeModel, NodeContentRequest contentRequest,
                                NodeContentValueMatcher valueMatcher) {
        return nodeContentItemReader.matchesNodeContent(nodeModel, contentRequest, valueMatcher);
    }

    private SearchResultItem buildSearchResultItem(NodeModel nodeModel, boolean includesBreadcrumbPath) {
        String nodeIdentifier = nodeModel.createID();
        NodeContentResponse briefContent = nodeContentItemReader.readNodeContent(nodeModel, null, NodeContentPreset.BRIEF);
        String briefText = briefContent == null ? null : briefContent.getBriefText();
        String breadcrumbPath = includesBreadcrumbPath ? buildBreadcrumbPath(nodeModel) : null;
        return new SearchResultItem(nodeIdentifier, briefText, breadcrumbPath);
    }

    private String buildBreadcrumbPath(NodeModel nodeModel) {
        List<String> pathSegments = new ArrayList<>();
        NodeModel current = nodeModel;
        while (current != null) {
            NodeContentResponse briefContent = nodeContentItemReader.readNodeContent(current, null, NodeContentPreset.BRIEF);
            String text = briefContent == null ? null : briefContent.getBriefText();
            if (text != null && !text.isEmpty()) {
                pathSegments.add(text);
            }
            current = current.getParentNode();
        }
        if (pathSegments.isEmpty()) {
            return null;
        }
        Collections.reverse(pathSegments);
        return String.join("/", pathSegments);
    }

    private int measureSerializedLength(Object item) {
        try {
            return objectMapper.writeValueAsBytes(item).length;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize search results.", error);
        }
    }

    private Pattern compileRegularExpression(String expression, SearchCaseSensitivity caseSensitivity) {
        try {
            int flags = caseSensitivity == SearchCaseSensitivity.CASE_INSENSITIVE
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                : 0;
            return Pattern.compile(expression, flags);
        } catch (PatternSyntaxException error) {
            throw new IllegalArgumentException("Invalid regular expression: " + error.getMessage(), error);
        }
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

    public ToolCallSummary buildToolCallSummary(SearchNodesRequest request, SearchNodesResponse response) {
        String queryText = request == null ? "" : ToolCallSummaryFormatter.sanitizeValue(request.getQueryText());
        int resultCount = response == null || response.getResults() == null ? 0 : response.getResults().size();
        String summaryText = "searchNodes: query=\"" + queryText + "\""
            + ", results=" + resultCount;
        String resultTexts = ToolCallSummaryFormatter.joinTextValues(
            response == null ? null : response.getResultPreviewTexts(), "; ");
        if (!resultTexts.isEmpty()) {
            summaryText = summaryText + ", resultTexts=\"" + resultTexts + "\"";
        }
        if (request != null && request.hasMatchingMode()) {
            summaryText = summaryText + ", matchingMode=" + request.getMatchingMode();
        }
        if (request != null && request.hasCaseSensitivity()) {
            summaryText = summaryText + ", caseSensitivity=" + request.getCaseSensitivity();
        }
        if (request != null && request.hasOffset()) {
            summaryText = summaryText + ", offset=" + request.getOffset();
        }
        if (request != null && request.hasLimit()) {
            summaryText = summaryText + ", limit=" + request.getLimit();
        }
        return new ToolCallSummary("searchNodes", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(SearchNodesRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("searchNodes", "searchNodes error: " + safeMessage, true);
    }

    private void addPreviewText(NodeModel resultNode, List<String> previews) {
        if (resultNode == null || previews == null || previews.size() >= SUMMARY_PREVIEW_COUNT_LIMIT) {
            return;
        }
        String previewText = textController.getShortPlainText(resultNode, SUMMARY_PREVIEW_TEXT_LIMIT, "");
        if (previewText != null && !previewText.isEmpty()) {
            previews.add(previewText);
        }
    }
}
