package org.freeplane.plugin.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

import java.util.List;

import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.code.AttachedEditorProvider;
import org.freeplane.plugin.ai.code.AttachedEditorToolSet;
import org.freeplane.plugin.ai.code.OverwriteAttachedEditorContentResponse;
import org.freeplane.plugin.ai.code.ReadAttachedEditorLatestIssueResponse;
import org.freeplane.plugin.ai.code.ReadAttachedEditorResponse;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;
import org.mockito.MockedConstruction;

public class AIToolSetBuilderTest {

    @Test
    public void buildToolObjectsIncludesAttachedEditorToolSetAndProvider() {
        AttachedEditorProvider attachedEditorProvider = new AttachedEditorProvider() {
            @Override
            public ReadAttachedEditorResponse readAttachedEditor() {
                return new ReadAttachedEditorResponse(true, "text/plain", "hello", "fingerprint", false, false);
            }

            @Override
            public OverwriteAttachedEditorContentResponse overwriteAttachedEditorContent(String text) {
                return new OverwriteAttachedEditorContentResponse("fingerprint");
            }

            @Override
            public org.freeplane.features.ai.code.AiChatCodeOperationResult compileAttachedEditorContent() {
                throw new IllegalStateException("not needed");
            }

            @Override
            public ReadAttachedEditorLatestIssueResponse getAttachedEditorLatestIssue() {
                return ReadAttachedEditorLatestIssueResponse.noIssue();
            }

            @Override
            public boolean hasAttachedEditor() {
                return true;
            }

            @Override
            public String attachedContentType() {
                return "text/plain";
            }
        };

        List<Object> toolObjects;
        try (MockedConstruction<AIToolSet> ignored = mockConstruction(AIToolSet.class)) {
            toolObjects = new AIToolSetBuilder()
                .availableMaps(mock(AvailableMaps.class))
                .textController(mock(TextController.class))
                .attributeController(mock(AttributeController.class))
                .iconController(mock(IconController.class))
                .mapController(mock(MMapController.class))
                .attachedEditorProvider(attachedEditorProvider)
                .buildToolObjects();
        }

        assertThat(toolObjects).hasSize(2);
        assertThat(toolObjects.get(0)).isInstanceOf(AIToolSet.class);
        assertThat(toolObjects.get(1)).isInstanceOf(AttachedEditorToolSet.class);
        AttachedEditorToolSet attachedEditorToolSet = (AttachedEditorToolSet) toolObjects.get(1);
        assertThat(attachedEditorToolSet.readAttachedEditor().isAttached()).isTrue();
        assertThat(attachedEditorToolSet.readAttachedEditor().getText()).isEqualTo("hello");
    }
}
