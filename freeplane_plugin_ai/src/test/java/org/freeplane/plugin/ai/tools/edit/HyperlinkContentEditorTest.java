package org.freeplane.plugin.ai.tools.edit;

import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HyperlinkContentEditorTest {
    @Test
    public void setInitialHyperlink_ignoresEmptyValues() {
        MLinkController linkController = mock(MLinkController.class);
        HyperlinkContentEditor uut = new HyperlinkContentEditor(linkController);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.setInitialHyperlink(nodeModel, null);
        uut.setInitialHyperlink(nodeModel, " ");

        verifyNoInteractions(linkController);
    }

    @Test
    public void setInitialHyperlink_setsLinkWithControllerType() {
        MLinkController linkController = mock(MLinkController.class);
        when(linkController.linkType()).thenReturn(LinkController.LINK_RELATIVE_TO_MINDMAP);
        HyperlinkContentEditor uut = new HyperlinkContentEditor(linkController);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.setInitialHyperlink(nodeModel, "https://example.com");

        verify(linkController).setLink(nodeModel, "https://example.com", LinkController.LINK_RELATIVE_TO_MINDMAP);
    }

    @Test
    public void editHyperlink_clearsOnDelete() {
        MLinkController linkController = mock(MLinkController.class);
        HyperlinkContentEditor uut = new HyperlinkContentEditor(linkController);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.editHyperlink(nodeModel, EditOperation.DELETE, null);

        verify(linkController).setLink(nodeModel, (String) null, LinkController.LINK_ABSOLUTE);
    }

    @Test
    public void editHyperlink_setsLinkOnReplace() {
        MLinkController linkController = mock(MLinkController.class);
        when(linkController.linkType()).thenReturn(LinkController.LINK_ABSOLUTE);
        HyperlinkContentEditor uut = new HyperlinkContentEditor(linkController);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.editHyperlink(nodeModel, EditOperation.REPLACE, "https://example.com");

        verify(linkController).setLink(nodeModel, "https://example.com", LinkController.LINK_ABSOLUTE);
    }

    @Test
    public void editHyperlink_rejectsUnsupportedOperation() {
        MLinkController linkController = mock(MLinkController.class);
        HyperlinkContentEditor uut = new HyperlinkContentEditor(linkController);
        NodeModel nodeModel = mock(NodeModel.class);

        assertThatThrownBy(() -> uut.editHyperlink(nodeModel, EditOperation.ADD, "https://example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported hyperlink edit operation: ADD");
    }
}
