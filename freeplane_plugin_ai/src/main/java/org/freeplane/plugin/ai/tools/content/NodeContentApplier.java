package org.freeplane.plugin.ai.tools.content;

import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.edit.AttributesContentEditor;
import org.freeplane.plugin.ai.tools.edit.HyperlinkContentEditor;
import org.freeplane.plugin.ai.tools.edit.IconsContentEditor;
import org.freeplane.plugin.ai.tools.edit.TagsContentEditor;
import org.freeplane.plugin.ai.tools.edit.TextualContentEditor;

public class NodeContentApplier {
    private final TextualContentEditor textualContentEditor;
    private final AttributesContentEditor attributesContentEditor;
    private final TagsContentEditor tagsContentEditor;
    private final IconsContentEditor iconsContentEditor;
    private final HyperlinkContentEditor hyperlinkContentEditor;

    public NodeContentApplier(TextualContentEditor textualContentEditor,
                              AttributesContentEditor attributesContentEditor,
                              TagsContentEditor tagsContentEditor,
                              IconsContentEditor iconsContentEditor,
                              HyperlinkContentEditor hyperlinkContentEditor) {
        this.textualContentEditor = Objects.requireNonNull(textualContentEditor, "textualContentEditor");
        this.attributesContentEditor = Objects.requireNonNull(attributesContentEditor, "attributesContentEditor");
        this.tagsContentEditor = Objects.requireNonNull(tagsContentEditor, "tagsContentEditor");
        this.iconsContentEditor = Objects.requireNonNull(iconsContentEditor, "iconsContentEditor");
        this.hyperlinkContentEditor = Objects.requireNonNull(hyperlinkContentEditor, "hyperlinkContentEditor");
    }

    public void apply(NodeModel nodeModel, NodeContentWriteRequest content) {
        if (nodeModel == null || content == null) {
            return;
        }
        guardApply(nodeModel, content);
    }

    private void guardApply(NodeModel nodeModel, NodeContentWriteRequest content) {
        if (content == null) {
            return;
        }
        textualContentEditor.setInitialContent(nodeModel, content);
        attributesContentEditor.setInitialContent(nodeModel, toAttributesContent(content));
        tagsContentEditor.setInitialContent(nodeModel, toTagsContent(content));
        iconsContentEditor.setInitialContent(nodeModel, toIconsContent(content));
        hyperlinkContentEditor.setInitialHyperlink(nodeModel, content.getHyperlink());
    }

    private AttributesContent toAttributesContent(NodeContentWriteRequest content) {
        if (content.getAttributes() == null) {
            return null;
        }
        return new AttributesContent(content.getAttributes());
    }

    private TagsContent toTagsContent(NodeContentWriteRequest content) {
        if (content.getTags() == null) {
            return null;
        }
        return new TagsContent(content.getTags());
    }

    private IconsContent toIconsContent(NodeContentWriteRequest content) {
        if (content.getIcons() == null) {
            return null;
        }
        return new IconsContent(content.getIcons());
    }
}
