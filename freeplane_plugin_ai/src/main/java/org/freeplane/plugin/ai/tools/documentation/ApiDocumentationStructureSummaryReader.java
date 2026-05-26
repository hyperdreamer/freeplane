package org.freeplane.plugin.ai.tools.documentation;

import java.io.File;
import java.util.List;
import java.util.Objects;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

public class ApiDocumentationStructureSummaryReader {
    private static final String HOW_TO_USE_LABEL = "How to use this map";

    private final TextController textController;

    public ApiDocumentationStructureSummaryReader(TextController textController) {
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    public String readStructureSummary(MapModel mapModel, File mapFile) {
        Objects.requireNonNull(mapModel, "mapModel");
        Objects.requireNonNull(mapFile, "mapFile");
        NodeModel rootNode = mapModel.getRootNode();
        NodeModel howToUseNode = findHowToUseNode(rootNode);
        if (howToUseNode == null) {
            throw new IllegalStateException(buildMissingHowToUseMessage(mapFile));
        }
        StringBuilder summary = new StringBuilder();
        appendNode(summary, howToUseNode, 0);
        return summary.toString();
    }

    private NodeModel findHowToUseNode(NodeModel rootNode) {
        if (rootNode == null) {
            return null;
        }
        List<NodeModel> children = rootNode.getChildren();
        if (children == null) {
            return null;
        }
        for (NodeModel child : children) {
            if (HOW_TO_USE_LABEL.equals(nodeText(child))) {
                return child;
            }
        }
        return null;
    }

    private void appendNode(StringBuilder summary, NodeModel nodeModel, int depth) {
        appendIndentedText(summary, nodeText(nodeModel), depth);
        List<NodeModel> children = nodeModel.getChildren();
        if (children == null) {
            return;
        }
        for (NodeModel child : children) {
            appendNode(summary, child, depth + 1);
        }
    }

    private void appendIndentedText(StringBuilder summary, String text, int depth) {
        String[] lines = text == null ? new String[]{""} : text.split("\\r?\\n", -1);
        for (String line : lines) {
            if (summary.length() > 0) {
                summary.append('\n');
            }
            for (int index = 0; index < depth; index += 1) {
                summary.append("  ");
            }
            summary.append(line);
        }
    }

    private String nodeText(NodeModel nodeModel) {
        String text = textController.getPlainTransformedTextWithoutNodeNumber(nodeModel);
        return text == null ? "" : text;
    }

    private String buildMissingHowToUseMessage(File mapFile) {
        return "API documentation map is invalid at " + mapFile.getAbsolutePath()
            + ": missing top-level 'How to use this map' section. Remedy: regenerate freeplane-api.mm from the current build.";
    }
}
