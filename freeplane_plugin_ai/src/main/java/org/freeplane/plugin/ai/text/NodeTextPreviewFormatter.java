package org.freeplane.plugin.ai.text;

import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

public final class NodeTextPreviewFormatter {
    private static final int MAXIMUM_SHORT_TEXT_CHARACTERS = 40;
    private static final int MAXIMUM_MUTATION_SUMMARY_CHARACTERS = 20;
    private static final int MAXIMUM_TOOL_CALL_PREVIEW_CHARACTERS = 20;
    private static final String CONTINUATION_MARK = " ...";
    private static final String NO_CONTINUATION_MARK = "";

    private final TextController textController;

    public NodeTextPreviewFormatter(TextController textController) {
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    public String shortText(NodeModel node) {
        return textController.getShortPlainText(node, MAXIMUM_SHORT_TEXT_CHARACTERS, CONTINUATION_MARK);
    }

    public String modifiedNodeSummaryText(NodeModel node) {
        return textController.getShortPlainText(node, MAXIMUM_MUTATION_SUMMARY_CHARACTERS, CONTINUATION_MARK);
    }

    public String toolCallPreviewText(NodeModel node) {
        return textController.getShortPlainText(node, MAXIMUM_TOOL_CALL_PREVIEW_CHARACTERS, NO_CONTINUATION_MARK);
    }
}
