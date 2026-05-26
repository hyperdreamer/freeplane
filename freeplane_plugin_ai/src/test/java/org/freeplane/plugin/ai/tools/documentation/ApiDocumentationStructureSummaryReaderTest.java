package org.freeplane.plugin.ai.tools.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

public class ApiDocumentationStructureSummaryReaderTest {
    @Test
    public void readStructureSummary_rendersIndentedPlainTextInMapOrderIncludingMultilineGuideLeaf() {
        TextController textController = mock(TextController.class);
        ApiDocumentationStructureSummaryReader uut = new ApiDocumentationStructureSummaryReader(textController);
        MapModel mapModel = mock(MapModel.class);
        NodeModel root = mock(NodeModel.class);
        NodeModel howToUse = mock(NodeModel.class);
        NodeModel guide = mock(NodeModel.class);
        NodeModel apiGroups = mock(NodeModel.class);
        when(mapModel.getRootNode()).thenReturn(root);
        when(root.getChildren()).thenReturn(Arrays.asList(howToUse, apiGroups));
        when(howToUse.getChildren()).thenReturn(Collections.singletonList(guide));
        when(guide.getChildren()).thenReturn(Collections.emptyList());
        when(apiGroups.getChildren()).thenReturn(Collections.emptyList());
        when(textController.getPlainTransformedTextWithoutNodeNumber(howToUse)).thenReturn("How to use this map");
        when(textController.getPlainTransformedTextWithoutNodeNumber(guide))
            .thenReturn("Use API groups.\nUse Packages.");
        when(textController.getPlainTransformedTextWithoutNodeNumber(apiGroups)).thenReturn("API groups");

        String summary = uut.readStructureSummary(mapModel, new File("/tmp/freeplane-api.mm"));

        assertThat(summary).isEqualTo("How to use this map\n  Use API groups.\n  Use Packages.");
    }

    @Test
    public void readStructureSummary_throwsExactInvalidMapErrorWhenHowToUseSectionIsMissing() {
        TextController textController = mock(TextController.class);
        ApiDocumentationStructureSummaryReader uut = new ApiDocumentationStructureSummaryReader(textController);
        MapModel mapModel = mock(MapModel.class);
        NodeModel root = mock(NodeModel.class);
        NodeModel proxy = mock(NodeModel.class);
        File mapFile = new File("/tmp/freeplane-api.mm");
        when(mapModel.getRootNode()).thenReturn(root);
        when(root.getChildren()).thenReturn(Collections.singletonList(proxy));
        when(textController.getPlainTransformedTextWithoutNodeNumber(proxy)).thenReturn("Proxy");

        assertThatThrownBy(() -> uut.readStructureSummary(mapModel, mapFile))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("API documentation map is invalid at " + mapFile.getAbsolutePath()
                + ": missing top-level 'How to use this map' section. Remedy: regenerate freeplane-api.mm from the current build.");
    }
}
