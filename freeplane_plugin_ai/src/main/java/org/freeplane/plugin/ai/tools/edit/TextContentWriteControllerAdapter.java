package org.freeplane.plugin.ai.tools.edit;

import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.mindmapmode.MTextController;

public class TextContentWriteControllerAdapter implements TextContentWriteController {
    private final MTextController textController;

    public TextContentWriteControllerAdapter(MTextController textController) {
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    @Override
    public void setNodeText(NodeModel nodeModel, String value) {
        textController.setNodeText(nodeModel, value);
    }

    @Override
    public void setDetails(NodeModel nodeModel, String value) {
        textController.setDetails(nodeModel, value);
    }
}
