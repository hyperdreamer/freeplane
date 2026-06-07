package org.freeplane.plugin.ai.tools.edit;

import java.util.List;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;

public class NodeContentEditor {
    private final TextController textController;
    private final NodeContentItemReader nodeContentItemReader;
    private final TextualContentEditor textualContentEditor;
    private final AttributesContentEditor attributesContentEditor;
    private final TagsContentEditor tagsContentEditor;
    private final IconsContentEditor iconsContentEditor;
    private final NodeStyleContentEditor nodeStyleContentEditor;
    private final HyperlinkContentEditor hyperlinkContentEditor;
    private final AiEditsMarker aiEditsMarker;

    public NodeContentEditor(TextController textController, NodeContentItemReader nodeContentItemReader,
                             TextualContentEditor textualContentEditor,
                             AttributesContentEditor attributesContentEditor,
                             TagsContentEditor tagsContentEditor,
                             IconsContentEditor iconsContentEditor,
                             NodeStyleContentEditor nodeStyleContentEditor,
                             HyperlinkContentEditor hyperlinkContentEditor) {
        this.textController = textController;
        this.nodeContentItemReader = nodeContentItemReader;
        this.textualContentEditor = textualContentEditor;
        this.attributesContentEditor = attributesContentEditor;
        this.tagsContentEditor = tagsContentEditor;
        this.iconsContentEditor = iconsContentEditor;
        this.nodeStyleContentEditor = nodeStyleContentEditor;
        this.hyperlinkContentEditor = hyperlinkContentEditor;
        this.aiEditsMarker = new AiEditsMarker();
    }

    public NodeContentItem edit(NodeModel nodeModel, List<NodeContentEditItem> items) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (items == null || items.isEmpty()) {
            return nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL);
        }
        for (NodeContentEditItem edit : items) {
            processEdit(nodeModel, edit, false);
        }
        aiEditsMarker.addAiEditsMarkerWithUndo(nodeModel);
        return nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true);
    }

    public void validate(NodeModel nodeModel, List<NodeContentEditItem> items) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        for (NodeContentEditItem edit : items) {
            processEdit(nodeModel, edit, true);
        }
    }

    private void processEdit(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (edit == null) {
            return;
        }
        EditedElement editedElement = edit.getEditedElement();
        if (editedElement == null) {
            throw new IllegalArgumentException("Missing edited element.");
        }
        switch (editedElement) {
            case TEXT:
            case DETAILS:
            case NOTE:
                processTextualContent(nodeModel, edit, dryRun);
                break;
            case ATTRIBUTES:
                processAttributes(nodeModel, edit, dryRun);
                break;
            case TAGS:
                processTags(nodeModel, edit, dryRun);
                break;
            case ICONS:
                processIcons(nodeModel, edit, dryRun);
                break;
            case STYLE:
                processStyle(nodeModel, edit, dryRun);
                break;
            case HYPERLINK:
                processHyperlink(nodeModel, edit, dryRun);
                break;
            default:
                throw new IllegalArgumentException("Unknown edited element: " + editedElement);
        }
    }

    private void processTextualContent(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (edit.getOriginalContentType() == null) {
            throw new IllegalArgumentException("Missing originalContentType for textual content edits.");
        }
        String value = resolveTextualValue(edit);
        if (dryRun) {
            textualContentEditor.validateExistingTextualContent(
                nodeModel,
                edit.getEditedElement(),
                edit.getOriginalContentType(),
                value,
                textController);
        } else {
            textualContentEditor.editExistingTextualContent(
                nodeModel,
                edit.getEditedElement(),
                edit.getOriginalContentType(),
                value,
                textController);
        }
    }

    private void processAttributes(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (dryRun) {
            attributesContentEditor.validateExistingAttributesContent(
                nodeModel,
                edit.getOperation(),
                edit.getTargetKey(),
                edit.getIndex(),
                edit.getValue());
        } else {
            attributesContentEditor.editExistingAttributesContent(
                nodeModel,
                edit.getOperation(),
                edit.getTargetKey(),
                edit.getIndex(),
                edit.getValue());
        }
    }

    private void processTags(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (dryRun) {
            tagsContentEditor.validateExistingTagsContent(
                nodeModel,
                edit.getOperation(),
                edit.getTargetKey(),
                edit.getIndex(),
                edit.getValue());
        } else {
            tagsContentEditor.editExistingTagsContent(
                nodeModel,
                edit.getOperation(),
                edit.getTargetKey(),
                edit.getIndex(),
                edit.getValue());
        }
    }

    private void processIcons(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (dryRun) {
            iconsContentEditor.validateExistingIconsContent(
                nodeModel,
                edit.getOperation(),
                edit.getTargetKey(),
                edit.getIndex(),
                edit.getValue());
        } else {
            iconsContentEditor.editExistingIconsContent(
                nodeModel,
                edit.getOperation(),
                edit.getTargetKey(),
                edit.getIndex(),
                edit.getValue());
        }
    }

    private void processStyle(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (dryRun) {
            nodeStyleContentEditor.validateMainStyle(nodeModel, edit.getOperation(), edit.getValue());
        } else {
            nodeStyleContentEditor.editMainStyle(nodeModel, edit.getOperation(), edit.getValue());
        }
    }

    private void processHyperlink(NodeModel nodeModel, NodeContentEditItem edit, boolean dryRun) {
        if (dryRun) {
            hyperlinkContentEditor.validateHyperlink(nodeModel, edit.getOperation(), edit.getValue());
        } else {
            hyperlinkContentEditor.editHyperlink(nodeModel, edit.getOperation(), edit.getValue());
        }
    }

    private String resolveTextualValue(NodeContentEditItem edit) {
        EditOperation operation = edit.getOperation();
        EditedElement editedElement = edit.getEditedElement();
        if (operation == EditOperation.DELETE
            && (editedElement == EditedElement.DETAILS || editedElement == EditedElement.NOTE)) {
            return "";
        }
        if (operation != EditOperation.REPLACE) {
            throw new IllegalArgumentException("Only REPLACE operations are supported for this element.");
        }
        return edit.getValue();
    }
}
