package org.freeplane.plugin.ai.tools.formula;

import java.util.Collections;
import java.util.UUID;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptRequest;
import org.freeplane.features.ai.code.RunScriptResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.content.ContentType;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.edit.AttributesContentEditor;
import org.freeplane.plugin.ai.tools.edit.EditOperation;
import org.freeplane.plugin.ai.tools.edit.NoteContentWriteController;
import org.freeplane.plugin.ai.tools.edit.TextContentWriteController;
import org.freeplane.plugin.ai.tools.edit.TextualContentEditor;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FormulaUpdateToolTest {
    @Test
    public void previewAndApplyFormulaTextUpdate() {
        MapModel backingMap = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("before", backingMap);
        MapModel mapModel = mock(MapModel.class);
        when(mapModel.getNodeForID(nodeModel.createID())).thenReturn(nodeModel);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        String mapIdentifier = UUID.randomUUID().toString();
        when(availableMaps.findMapModel(eq(UUID.fromString(mapIdentifier)), any())).thenReturn(mapModel);
        TextController textController = mock(TextController.class);
        when(textController.getNodeFormat(any(NodeModel.class))).thenReturn(null);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value instanceof String && ((String) value).startsWith("=");
        });
        TextualContentEditor textualContentEditor = new TextualContentEditor(
            new TestTextContentWriteController(),
            new TestNoteContentWriteController(),
            textController);
        AttributesContentEditor attributesContentEditor = new AttributesContentEditor(
            mock(MAttributeController.class),
            textController);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        when(nodeContentItemReader.readNodeContentItem(any(NodeModel.class), eq(NodeContentPreset.FULL), eq(true), eq(true), eq(true)))
            .thenAnswer(invocation -> new NodeContentItem(
                ((NodeModel) invocation.getArgument(0)).createID(),
                null,
                null,
                null,
                null,
                null,
                null));
        FormulaUpdateTool uut = new FormulaUpdateTool(
            availableMaps,
            null,
            nodeContentItemReader,
            textualContentEditor,
            attributesContentEditor,
            successfulFormulaEvaluationCodeHostService("2"),
            FormulaUpdatePreviewStore.shared(),
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION);

        FormulaUpdatePreviewResponse previewResponse = uut.previewFormulaUpdates(new FormulaUpdatePreviewRequest(
            mapIdentifier,
            "preview formula",
            Collections.singletonList(new FormulaUpdateItem(
                Collections.singletonList(nodeModel.createID()),
                org.freeplane.plugin.ai.tools.edit.EditedElement.TEXT,
                ContentType.PLAIN_TEXT,
                Boolean.FALSE,
                Boolean.TRUE,
                "=1+1",
                null,
                EditOperation.REPLACE,
                null))));

        assertThat(previewResponse.getPreviewId()).isNotBlank();
        assertThat(previewResponse.getItems()).hasSize(1);
        assertThat(previewResponse.getItems().get(0).getStatus()).isEqualTo(FormulaUpdatePreviewStatus.VALIDATED);
        assertThat(previewResponse.getItems().get(0).getCandidateValue()).isEqualTo("=1+1");
        assertThat(previewResponse.getItems().get(0).getEvaluationResult()).isEqualTo("2");
        assertThat(nodeModel.getText()).isEqualTo("before");

        FormulaUpdateApplyResponse applyResponse = uut.applyFormulaUpdates(new FormulaUpdateApplyRequest(
            mapIdentifier,
            previewResponse.getPreviewId()));

        assertThat(applyResponse.getItems()).hasSize(1);
        assertThat(applyResponse.getItems().get(0).getStatus()).isEqualTo(FormulaUpdateApplyStatus.APPLIED);
        assertThat(nodeModel.getText()).isEqualTo("=1+1");
    }

    @Test
    public void applyUsesStoredPreviewEvenIfTargetTextChangedAfterPreview() {
        MapModel backingMap = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("before", backingMap);
        MapModel mapModel = mock(MapModel.class);
        when(mapModel.getNodeForID(nodeModel.createID())).thenReturn(nodeModel);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        String mapIdentifier = UUID.randomUUID().toString();
        when(availableMaps.findMapModel(eq(UUID.fromString(mapIdentifier)), any())).thenReturn(mapModel);
        TextController textController = mock(TextController.class);
        when(textController.getNodeFormat(any(NodeModel.class))).thenReturn(null);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value instanceof String && ((String) value).startsWith("=");
        });
        FormulaUpdateTool uut = new FormulaUpdateTool(
            availableMaps,
            null,
            mock(NodeContentItemReader.class),
            new TextualContentEditor(new TestTextContentWriteController(), new TestNoteContentWriteController(), textController),
            new AttributesContentEditor(mock(MAttributeController.class), textController),
            successfulFormulaEvaluationCodeHostService("2"),
            FormulaUpdatePreviewStore.shared(),
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION);

        FormulaUpdatePreviewResponse previewResponse = uut.previewFormulaUpdates(new FormulaUpdatePreviewRequest(
            mapIdentifier,
            "preview formula",
            Collections.singletonList(new FormulaUpdateItem(
                Collections.singletonList(nodeModel.createID()),
                org.freeplane.plugin.ai.tools.edit.EditedElement.TEXT,
                ContentType.PLAIN_TEXT,
                Boolean.FALSE,
                Boolean.TRUE,
                "=1+1",
                null,
                EditOperation.REPLACE,
                null))));
        nodeModel.setText("changed");

        FormulaUpdateApplyResponse applyResponse = uut.applyFormulaUpdates(new FormulaUpdateApplyRequest(
            mapIdentifier,
            previewResponse.getPreviewId()));

        assertThat(applyResponse.getItems()).hasSize(1);
        assertThat(applyResponse.getItems().get(0).getStatus())
            .isEqualTo(FormulaUpdateApplyStatus.APPLIED);
        assertThat(nodeModel.getText()).isEqualTo("=1+1");
    }

    @Test
    public void applyFailsGracefullyIfTargetNodeIsMissing() {
        MapModel backingMap = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("before", backingMap);
        final NodeModel[] currentNode = new NodeModel[] { nodeModel };
        MapModel mapModel = mock(MapModel.class);
        when(mapModel.getNodeForID(nodeModel.createID())).thenAnswer(invocation -> currentNode[0]);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        String mapIdentifier = UUID.randomUUID().toString();
        when(availableMaps.findMapModel(eq(UUID.fromString(mapIdentifier)), any())).thenReturn(mapModel);
        TextController textController = mock(TextController.class);
        when(textController.getNodeFormat(any(NodeModel.class))).thenReturn(null);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value instanceof String && ((String) value).startsWith("=");
        });
        FormulaUpdateTool uut = new FormulaUpdateTool(
            availableMaps,
            null,
            mock(NodeContentItemReader.class),
            new TextualContentEditor(new TestTextContentWriteController(), new TestNoteContentWriteController(), textController),
            new AttributesContentEditor(mock(MAttributeController.class), textController),
            successfulFormulaEvaluationCodeHostService("2"),
            FormulaUpdatePreviewStore.shared(),
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION);

        FormulaUpdatePreviewResponse previewResponse = uut.previewFormulaUpdates(new FormulaUpdatePreviewRequest(
            mapIdentifier,
            "preview formula",
            Collections.singletonList(new FormulaUpdateItem(
                Collections.singletonList(nodeModel.createID()),
                org.freeplane.plugin.ai.tools.edit.EditedElement.TEXT,
                ContentType.PLAIN_TEXT,
                Boolean.FALSE,
                Boolean.TRUE,
                "=1+1",
                null,
                EditOperation.REPLACE,
                null))));
        currentNode[0] = null;

        FormulaUpdateApplyResponse applyResponse = uut.applyFormulaUpdates(new FormulaUpdateApplyRequest(
            mapIdentifier,
            previewResponse.getPreviewId()));

        assertThat(applyResponse.getItems()).hasSize(1);
        assertThat(applyResponse.getItems().get(0).getStatus()).isEqualTo(FormulaUpdateApplyStatus.FAILED);
        assertThat(applyResponse.getItems().get(0).getErrorMessage()).contains("Unknown node identifier");
    }

    @Test
    public void applyFailsGracefullyIfAttributeReplaceTargetIsMissing() {
        MapModel backingMap = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("before", backingMap);
        NodeAttributeTableModel attributes = new NodeAttributeTableModel();
        attributes.getAttributes().add(new Attribute("cost", "=1"));
        nodeModel.addExtension(attributes);
        MapModel mapModel = mock(MapModel.class);
        when(mapModel.getNodeForID(nodeModel.createID())).thenReturn(nodeModel);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        String mapIdentifier = UUID.randomUUID().toString();
        when(availableMaps.findMapModel(eq(UUID.fromString(mapIdentifier)), any())).thenReturn(mapModel);
        TextController textController = mock(TextController.class);
        when(textController.getNodeFormat(any(NodeModel.class))).thenReturn(null);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value instanceof String && ((String) value).startsWith("=");
        });
        FormulaUpdateTool uut = new FormulaUpdateTool(
            availableMaps,
            null,
            mock(NodeContentItemReader.class),
            new TextualContentEditor(new TestTextContentWriteController(), new TestNoteContentWriteController(), textController),
            new AttributesContentEditor(mock(MAttributeController.class), textController),
            successfulFormulaEvaluationCodeHostService("2"),
            FormulaUpdatePreviewStore.shared(),
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION);

        FormulaUpdatePreviewResponse previewResponse = uut.previewFormulaUpdates(new FormulaUpdatePreviewRequest(
            mapIdentifier,
            "preview attribute formula",
            Collections.singletonList(new FormulaUpdateItem(
                Collections.singletonList(nodeModel.createID()),
                org.freeplane.plugin.ai.tools.edit.EditedElement.ATTRIBUTES,
                null,
                Boolean.TRUE,
                Boolean.TRUE,
                "=1+1",
                Integer.valueOf(0),
                EditOperation.REPLACE,
                null))));
        attributes.getAttributes().clear();

        FormulaUpdateApplyResponse applyResponse = uut.applyFormulaUpdates(new FormulaUpdateApplyRequest(
            mapIdentifier,
            previewResponse.getPreviewId()));

        assertThat(applyResponse.getItems()).hasSize(1);
        assertThat(applyResponse.getItems().get(0).getStatus()).isEqualTo(FormulaUpdateApplyStatus.FAILED);
        assertThat(applyResponse.getItems().get(0).getErrorMessage()).contains("Missing attribute index or name for replace.");
    }

    private static AiCodeHostService successfulFormulaEvaluationCodeHostService(final String result) {
        return new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return null;
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                return null;
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                return null;
            }

            @Override
            public RunScriptResponse runScript(RunScriptRequest request) {
                return null;
            }

            @Override
            public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
                return new AiChatCodeOperationResult(
                    true,
                    Collections.<String>emptyList(),
                    null,
                    result,
                    null,
                    null,
                    null,
                    null);
            }

            @Override
            public void addRunListener(org.freeplane.features.ai.code.AiCodeRunListener listener) {
            }

            @Override
            public void removeRunListener(org.freeplane.features.ai.code.AiCodeRunListener listener) {
            }
        };
    }

    private static class TestTextContentWriteController implements TextContentWriteController {
        @Override
        public void setNodeText(NodeModel nodeModel, String value) {
            nodeModel.setText(value);
        }

        @Override
        public void setDetails(NodeModel nodeModel, String value) {
            DetailModel.createDetailText(nodeModel).setText(value);
        }
    }

    private static class TestNoteContentWriteController implements NoteContentWriteController {
        @Override
        public void setNoteText(NodeModel nodeModel, String value) {
            NoteModel.createNote(nodeModel).setText(value);
        }
    }
}
