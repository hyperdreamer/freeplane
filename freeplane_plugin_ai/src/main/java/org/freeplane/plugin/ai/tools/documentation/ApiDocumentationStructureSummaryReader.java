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
        NodeModel howToUseNode = findRequiredTopLevelSection(mapModel, mapFile, HOW_TO_USE_LABEL);
        StringBuilder summary = new StringBuilder();
        appendNode(summary, howToUseNode, 0);
        return summary.toString();
    }

    public NodeModel findRequiredTopLevelSection(MapModel mapModel, File mapFile, String sectionLabel) {
        Objects.requireNonNull(mapModel, "mapModel");
        Objects.requireNonNull(mapFile, "mapFile");
        Objects.requireNonNull(sectionLabel, "sectionLabel");
        NodeModel sectionNode = findTopLevelSection(mapModel.getRootNode(), sectionLabel);
        if (sectionNode != null) {
            return sectionNode;
        }
        throw new IllegalStateException(buildMissingTopLevelSectionMessage(mapFile, sectionLabel));
    }

    private NodeModel findTopLevelSection(NodeModel rootNode, String sectionLabel) {
        if (rootNode == null) {
            return null;
        }
        List<NodeModel> children = rootNode.getChildren();
        if (children == null) {
            return null;
        }
        for (NodeModel child : children) {
            if (sectionLabel.equals(nodeText(child))) {
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

    private String buildMissingTopLevelSectionMessage(File mapFile, String sectionLabel) {
        return "API documentation map is invalid at " + mapFile.getAbsolutePath()
            + ": missing top-level '" + sectionLabel + "' section. Remedy: regenerate freeplane-api.mm from the current build.";
    }
}
