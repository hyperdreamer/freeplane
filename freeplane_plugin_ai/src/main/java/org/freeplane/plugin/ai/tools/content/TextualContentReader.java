package org.freeplane.plugin.ai.tools.content;

import java.util.Objects;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.features.text.TextController;

public class TextualContentReader {
    private final TextController textController;

    public TextualContentReader(TextController textController) {
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    public String readBriefText(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        return textController.getShortPlainText(nodeModel);
    }

    public TextualContent readTextualContent(NodeModel nodeModel, NodeContentPreset preset) {
        if (nodeModel == null) {
            return null;
        }
        if (preset == NodeContentPreset.BRIEF) {
            return null;
        }
        String text = readFullText(nodeModel);
        String details = readFullDetails(nodeModel);
        String note = readFullNote(nodeModel);
        if (text == null && details == null && note == null) {
            return null;
        }
        return new TextualContent(text, details, note);
    }

    public TextualContent readTextualContent(NodeModel nodeModel, TextualContentRequest request) {
        if (nodeModel == null || request == null) {
            return null;
        }
        String text = request.includesText() ? readFullText(nodeModel) : null;
        String details = request.includesDetails() ? readFullDetails(nodeModel) : null;
        String note = request.includesNote() ? readFullNote(nodeModel) : null;
        if (text == null && details == null && note == null) {
            return null;
        }
        return new TextualContent(text, details, note);
    }

    public boolean matches(NodeModel nodeModel, TextualContentRequest request, NodeContentValueMatcher valueMatcher) {
        if (nodeModel == null || request == null || valueMatcher == null) {
            return false;
        }
        if (request.includesText() && valueMatcher.matchesValue(readFullText(nodeModel))) {
            return true;
        }
        if (request.includesDetails() && valueMatcher.matchesValue(readFullDetails(nodeModel))) {
            return true;
        }
        return request.includesNote() && valueMatcher.matchesValue(readFullNote(nodeModel));
    }

    private String readFullText(NodeModel nodeModel) {
        Object data = nodeModel.getUserObject();
        if (data == null) {
            return null;
        }
        String transformedText = textController.getTransformedTextForClipboard(nodeModel, nodeModel, data);
        return convertToPlainText(transformedText);
    }

    private String readFullDetails(NodeModel nodeModel) {
        DetailModel detailModel = DetailModel.getDetail(nodeModel);
        if (detailModel == null) {
            return null;
        }
        String detailsText = detailModel.getText();
        if (detailsText == null) {
            return null;
        }
        String transformedText = textController.getTransformedTextForClipboard(nodeModel, detailModel, detailsText);
        return convertToPlainText(transformedText);
    }

    private String readFullNote(NodeModel nodeModel) {
        NoteModel noteModel = NoteModel.getNote(nodeModel);
        if (noteModel == null) {
            return null;
        }
        String noteText = noteModel.getText();
        if (noteText == null) {
            return null;
        }
        String transformedText = textController.getTransformedTextForClipboard(nodeModel, noteModel, noteText);
        return convertToPlainText(transformedText);
    }

    private String convertToPlainText(String text) {
        if (text == null) {
            return null;
        }
        return HtmlUtils.htmlToPlain(text);
    }
}
