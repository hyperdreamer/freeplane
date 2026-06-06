package org.freeplane.plugin.ai.tools.edit;

import java.util.Objects;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.content.ContentType;
import org.freeplane.plugin.ai.tools.content.ContentTypeConverter;
import org.freeplane.plugin.ai.tools.content.NodeContentWriteRequest;

public class TextualContentEditor {
    private final TextContentWriteController textContentWriteController;
    private final NoteContentWriteController noteContentWriteController;
    private final TextController textController;
    private final ContentTypeConverter contentTypeConverter;

    public TextualContentEditor(TextContentWriteController textContentWriteController,
                                NoteContentWriteController noteContentWriteController) {
        this(textContentWriteController, noteContentWriteController, safeTextController());
    }

    public TextualContentEditor(TextContentWriteController textContentWriteController,
                                NoteContentWriteController noteContentWriteController,
                                TextController textController) {
        this.textContentWriteController = Objects.requireNonNull(
            textContentWriteController, "textContentWriteController");
        this.noteContentWriteController = Objects.requireNonNull(
            noteContentWriteController, "noteContentWriteController");
        this.textController = textController;
        this.contentTypeConverter = new ContentTypeConverter();
    }

    public void setInitialContent(NodeModel nodeModel, NodeContentWriteRequest content) {
        if (nodeModel == null || content == null) {
            return;
        }
        applyInitialText(nodeModel, content.getText(), content.getTextContentType());
        applyInitialDetails(nodeModel, content.getDetails(), content.getDetailsContentType());
        applyInitialNote(nodeModel, content.getNote(), content.getNoteContentType());
    }

    public String prepareFormulaCandidate(NodeModel nodeModel, EditedElement editedElement,
                                          ContentType originalContentType, Boolean originalIsFormula,
                                          boolean targetIsFormula, String value) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (editedElement == null) {
            throw new IllegalArgumentException("Missing edited element.");
        }
        if (originalContentType == null) {
            throw new IllegalArgumentException("Missing originalContentType for textual formula update.");
        }
        if (originalIsFormula == null) {
            throw new IllegalArgumentException("Missing originalIsFormula for textual formula update.");
        }
        switch (editedElement) {
            case TEXT:
                Object currentTextValue = nodeModel.getUserObject();
                validateFormulaState(currentTextValue, originalIsFormula.booleanValue());
                NodeTextContentType nodeTextContentType = resolveNodeTextContentType(nodeModel, currentTextValue);
                validateContentType(nodeTextContentType.contentType, originalContentType, true);
                String updatedTextValue = prepareTextValue(nodeTextContentType, value);
                validateTargetFormulaState(updatedTextValue, targetIsFormula);
                return updatedTextValue;
            case DETAILS:
                String currentDetails = DetailModel.getDetailText(nodeModel);
                validateFormulaState(currentDetails, originalIsFormula.booleanValue());
                ContentType currentDetailsContentType = resolveContentType(
                    currentDetails, DetailModel.getDetailContentType(nodeModel));
                validateContentType(currentDetailsContentType, originalContentType, false);
                String updatedDetailsValue = prepareRichTextValue(currentDetailsContentType, value);
                validateTargetFormulaState(updatedDetailsValue, targetIsFormula);
                return updatedDetailsValue;
            case NOTE:
                String currentNote = NoteModel.getNoteText(nodeModel);
                validateFormulaState(currentNote, originalIsFormula.booleanValue());
                ContentType currentNoteContentType = resolveContentType(
                    currentNote, NoteModel.getNoteContentType(nodeModel));
                validateContentType(currentNoteContentType, originalContentType, false);
                String updatedNoteValue = prepareRichTextValue(currentNoteContentType, value);
                validateTargetFormulaState(updatedNoteValue, targetIsFormula);
                return updatedNoteValue;
            default:
                throw new IllegalArgumentException("Unsupported edited element for textual formula update: "
                    + editedElement);
        }
    }

    public void applyFormulaValue(NodeModel nodeModel, EditedElement editedElement, String candidateValue) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (editedElement == null) {
            throw new IllegalArgumentException("Missing edited element.");
        }
        switch (editedElement) {
            case TEXT:
                textContentWriteController.setNodeText(nodeModel, candidateValue);
                return;
            case DETAILS:
                textContentWriteController.setDetails(nodeModel, candidateValue);
                return;
            case NOTE:
                noteContentWriteController.setNoteText(nodeModel, candidateValue);
                return;
            default:
                throw new IllegalArgumentException("Unsupported edited element for textual formula update: "
                    + editedElement);
        }
    }

    public String currentRawValue(NodeModel nodeModel, EditedElement editedElement) {
        if (nodeModel == null || editedElement == null) {
            return null;
        }
        switch (editedElement) {
            case TEXT:
                Object currentTextValue = nodeModel.getUserObject();
                return currentTextValue == null ? null : String.valueOf(currentTextValue);
            case DETAILS:
                return DetailModel.getDetailText(nodeModel);
            case NOTE:
                return NoteModel.getNoteText(nodeModel);
            default:
                throw new IllegalArgumentException("Unsupported edited element for textual content: " + editedElement);
        }
    }

    public ContentType currentBaseContentType(NodeModel nodeModel, EditedElement editedElement) {
        if (nodeModel == null || editedElement == null) {
            return null;
        }
        switch (editedElement) {
            case TEXT:
                return resolveNodeTextContentType(nodeModel, nodeModel.getUserObject()).contentType;
            case DETAILS:
                return resolveContentType(DetailModel.getDetailText(nodeModel), DetailModel.getDetailContentType(nodeModel));
            case NOTE:
                return resolveContentType(NoteModel.getNoteText(nodeModel), NoteModel.getNoteContentType(nodeModel));
            default:
                throw new IllegalArgumentException("Unsupported edited element for textual content: " + editedElement);
        }
    }

    public boolean currentIsFormula(NodeModel nodeModel, EditedElement editedElement) {
        return isFormula(currentRawValue(nodeModel, editedElement));
    }

    private void applyInitialText(NodeModel nodeModel, String text, ContentType contentType) {
        if (text == null) {
            return;
        }
        String updatedText = prepareInitialTextValue(contentType, text);
        ensureCreatePathValueIsNotFormula(updatedText);
        nodeModel.setText(updatedText);
        String nodeFormat = toNodeFormat(contentType);
        if (nodeFormat != null) {
            NodeStyleModel.setNodeFormat(nodeModel, nodeFormat);
        }
    }

    private void applyInitialDetails(NodeModel nodeModel, String details, ContentType contentType) {
        if (details == null || details.isEmpty()) {
            return;
        }
        DetailModel detailModel = DetailModel.createDetailText(nodeModel);
        String updatedDetails = prepareInitialRichTextValue(contentType, details);
        ensureCreatePathValueIsNotFormula(updatedDetails);
        detailModel.setText(updatedDetails);
        String freeplaneContentType = toFreeplaneContentType(contentType);
        if (freeplaneContentType != null) {
            detailModel.setContentType(freeplaneContentType);
        }
    }

    private void applyInitialNote(NodeModel nodeModel, String note, ContentType contentType) {
        if (note == null || note.isEmpty()) {
            return;
        }
        NoteModel noteModel = NoteModel.createNote(nodeModel);
        String updatedNote = prepareInitialRichTextValue(contentType, note);
        ensureCreatePathValueIsNotFormula(updatedNote);
        noteModel.setText(updatedNote);
        String freeplaneContentType = toFreeplaneContentType(contentType);
        if (freeplaneContentType != null) {
            noteModel.setContentType(freeplaneContentType);
        }
    }

    private void ensureCreatePathValueIsNotFormula(String value) {
        if (isFormula(value)) {
            throw new IllegalArgumentException(
                "Formula values are not allowed in createNodes or createSummary; create the node first, then use previewFormulaUpdates and applyFormulaUpdates.");
        }
    }

    private String prepareInitialTextValue(ContentType contentType, String value) {
        if (contentType == null) {
            return value;
        }
        switch (contentType) {
            case HTML:
                return htmlOf(value);
            case MARKDOWN:
                rejectHtml(value, "Markdown content does not allow html; use markdown syntax.");
                return value;
            case LATEX:
                rejectHtml(value, "Latex content does not allow html.");
                return contentTypeConverter.stripLatexPrefix(value);
            case PLAIN_TEXT:
                return HtmlUtils.isHtml(value) ? HtmlUtils.htmlToPlain(value) : value;
            default:
                return value;
        }
    }

    private String prepareInitialRichTextValue(ContentType contentType, String value) {
        if (contentType == null) {
            return htmlOf(value);
        }
        switch (contentType) {
            case HTML:
                return htmlOf(value);
            case MARKDOWN:
                rejectHtml(value, "Markdown content does not allow html; use markdown syntax.");
                return value;
            case LATEX:
                rejectHtml(value, "Latex content does not allow html.");
                return contentTypeConverter.stripLatexPrefix(value);
            case PLAIN_TEXT:
                return HtmlUtils.isHtml(value) ? HtmlUtils.htmlToPlain(value) : value;
            default:
                return value;
        }
    }

    private String htmlOf(String text) {
        return HtmlUtils.isHtml(text) ? text : HtmlUtils.plainToHTML(text);
    }

    private String toNodeFormat(ContentType contentType) {
        if (contentType == ContentType.MARKDOWN) {
            return "markdown";
        }
        if (contentType == ContentType.LATEX) {
            return "latex";
        }
        return null;
    }

    private String toFreeplaneContentType(ContentType contentType) {
        if (contentType == ContentType.MARKDOWN) {
            return "markdown";
        }
        if (contentType == ContentType.LATEX) {
            return "latex";
        }
        if (contentType == ContentType.HTML) {
            return TextController.CONTENT_TYPE_HTML;
        }
        return null;
    }

    public void editExistingTextualContent(NodeModel nodeModel, EditedElement editedElement,
                                           ContentType originalContentType, String value,
                                           TextController textController) {
        processExistingTextualContent(nodeModel, editedElement, originalContentType, value, textController, false);
    }

    public void validateExistingTextualContent(NodeModel nodeModel, EditedElement editedElement,
                                               ContentType originalContentType, String value,
                                               TextController textController) {
        processExistingTextualContent(nodeModel, editedElement, originalContentType, value, textController, true);
    }

    private void processExistingTextualContent(NodeModel nodeModel, EditedElement editedElement,
                                               ContentType originalContentType, String value,
                                               TextController textController, boolean dryRun) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (editedElement == null) {
            throw new IllegalArgumentException("Missing edited element.");
        }
        if (textController == null) {
            throw new IllegalArgumentException("Missing text controller.");
        }
        switch (editedElement) {
            case TEXT:
                Object currentTextValue = nodeModel.getUserObject();
                ensureFormulaIsNotUsed(currentTextValue, value, textController);
                NodeTextContentType nodeTextContentType = resolveNodeTextContentType(nodeModel, currentTextValue,
                    textController);
                validateContentType(nodeTextContentType.contentType, originalContentType, true);
                String updatedTextValue = prepareTextValue(nodeTextContentType, value);
                if (!dryRun) {
                    textContentWriteController.setNodeText(nodeModel, updatedTextValue);
                }
                break;
            case DETAILS:
                DetailModel detailModel = DetailModel.getDetail(nodeModel);
                String currentDetails = detailModel == null ? null : detailModel.getText();
                ensureFormulaIsNotUsed(currentDetails, value, textController);
                ContentType currentDetailsContentType = resolveContentType(
                    currentDetails, DetailModel.getDetailContentType(nodeModel), textController);
                validateContentType(currentDetailsContentType, originalContentType, false);
                String updatedDetailsValue = prepareRichTextValue(currentDetailsContentType, value);
                if (!dryRun) {
                    textContentWriteController.setDetails(nodeModel, updatedDetailsValue);
                }
                break;
            case NOTE:
                NoteModel noteModel = NoteModel.getNote(nodeModel);
                String currentNote = noteModel == null ? null : noteModel.getText();
                ensureFormulaIsNotUsed(currentNote, value, textController);
                ContentType currentNoteContentType = resolveContentType(
                    currentNote, NoteModel.getNoteContentType(nodeModel), textController);
                validateContentType(currentNoteContentType, originalContentType, false);
                String updatedNoteValue = prepareRichTextValue(currentNoteContentType, value);
                if (!dryRun) {
                    noteContentWriteController.setNoteText(nodeModel, updatedNoteValue);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported edited element for textual content: " + editedElement);
        }
    }

    private void validateFormulaState(Object currentValue, boolean originalIsFormula) {
        boolean currentIsFormula = isFormula(currentValue);
        if (currentIsFormula != originalIsFormula) {
            throw new IllegalArgumentException("Formula state has changed; read editable content again.");
        }
    }

    private void validateTargetFormulaState(String candidateValue, boolean targetIsFormula) {
        boolean candidateIsFormula = isFormula(candidateValue);
        if (candidateIsFormula != targetIsFormula) {
            throw new IllegalArgumentException(targetIsFormula
                ? "targetIsFormula=true requires a formula value."
                : "targetIsFormula=false requires a non-formula value.");
        }
    }

    private void ensureFormulaIsNotUsed(Object currentValue, String newValue, TextController textController) {
        if (isFormula(currentValue)) {
            throw new IllegalArgumentException("Cannot edit formula content with edit(...); use previewFormulaUpdates and applyFormulaUpdates.");
        }
        if (isFormula(newValue)) {
            throw new IllegalArgumentException("Formula content edits are not allowed in edit(...); use previewFormulaUpdates and applyFormulaUpdates.");
        }
    }

    private void validateContentType(ContentType currentContentType, ContentType originalContentType,
                                     boolean allowPlainTextHtmlSwitch) {
        if (originalContentType == null) {
            return;
        }
        if (currentContentType == originalContentType) {
            return;
        }
        if (allowPlainTextHtmlSwitch
            && isPlainTextOrHtml(currentContentType)
            && isPlainTextOrHtml(originalContentType)) {
            return;
        }
        if (currentContentType != originalContentType) {
            throw new IllegalArgumentException("Content type has changed; read editable content again.");
        }
    }

    private boolean isPlainTextOrHtml(ContentType contentType) {
        return contentType == ContentType.PLAIN_TEXT || contentType == ContentType.HTML;
    }

    private ContentType resolveContentType(Object currentValue, String freeplaneContentType) {
        return resolveContentType(currentValue, freeplaneContentType, textController);
    }

    private ContentType resolveContentType(Object currentValue, String freeplaneContentType,
                                           TextController textController) {
        return contentTypeConverter.toContentType(
            freeplaneContentType, currentValue == null ? null : String.valueOf(currentValue));
    }

    private NodeTextContentType resolveNodeTextContentType(NodeModel nodeModel, Object currentValue) {
        return resolveNodeTextContentType(nodeModel, currentValue, textController);
    }

    private NodeTextContentType resolveNodeTextContentType(NodeModel nodeModel, Object currentValue,
                                                           TextController textController) {
        String rawValue = currentValue == null ? null : String.valueOf(currentValue);
        String latexPrefix = contentTypeConverter.findLatexPrefix(rawValue);
        String nodeFormat = textController == null ? null : textController.getNodeFormat(nodeModel);
        ContentType contentType = latexPrefix == null
            ? contentTypeConverter.toTextContentTypeForNode(nodeFormat, rawValue)
            : ContentType.LATEX;
        return new NodeTextContentType(contentType, latexPrefix);
    }

    private String prepareTextValue(NodeTextContentType nodeTextContentType, String value) {
        if (nodeTextContentType.contentType == ContentType.LATEX) {
            boolean allowLatexPrefixWithoutReapply = false;
            return prepareLatexValue(value, nodeTextContentType.latexPrefix, allowLatexPrefixWithoutReapply);
        }
        if (nodeTextContentType.contentType == ContentType.MARKDOWN) {
            rejectHtml(value, "Markdown content does not allow html; use markdown syntax.");
        }
        return value;
    }

    private String prepareRichTextValue(ContentType currentContentType, String value) {
        if (currentContentType == ContentType.LATEX) {
            boolean allowLatexPrefixWithoutReapply = true;
            return prepareLatexValue(value, null, allowLatexPrefixWithoutReapply);
        }
        if (currentContentType == ContentType.MARKDOWN) {
            rejectHtml(value, "Markdown content does not allow html; use markdown syntax.");
        }
        return value;
    }

    private String prepareLatexValue(String value, String latexPrefix, boolean allowLatexPrefixWithoutReapply) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing latex content.");
        }
        rejectHtml(value, "Latex content does not allow html.");
        if (latexPrefix == null && contentTypeConverter.findLatexPrefix(value) != null
            && !allowLatexPrefixWithoutReapply) {
            throw new IllegalArgumentException("Latex prefix is not allowed for this content.");
        }
        String strippedValue = contentTypeConverter.stripLatexPrefix(value);
        if (latexPrefix == null) {
            return strippedValue;
        }
        return latexPrefix + " " + strippedValue;
    }

    private static TextController safeTextController() {
        try {
            return TextController.getController();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isFormula(Object value) {
        return textController != null && textController.isFormula(value);
    }

    private void rejectHtml(String value, String message) {
        if (value != null && HtmlUtils.isHtml(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static class NodeTextContentType {
        private final ContentType contentType;
        private final String latexPrefix;

        private NodeTextContentType(ContentType contentType, String latexPrefix) {
            this.contentType = contentType;
            this.latexPrefix = latexPrefix;
        }
    }
}
