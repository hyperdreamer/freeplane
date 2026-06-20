package org.freeplane.plugin.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.TagCategoryAccess;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.icon.mindmapmode.FreeplaneTagCategoryAccess;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.note.mindmapmode.MNoteController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.connectors.ConnectorEditRequest;
import org.freeplane.plugin.ai.tools.connectors.ConnectorEditResponse;
import org.freeplane.plugin.ai.tools.connectors.ConnectorEditTool;
import org.freeplane.plugin.ai.tools.content.ListResponse;
import org.freeplane.plugin.ai.tools.content.ListTool;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.freeplane.plugin.ai.tools.content.NodeContentApplier;
import org.freeplane.plugin.ai.tools.content.NodeContentFactories;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementCalculator;
import org.freeplane.plugin.ai.tools.create.CreateNodesPreferences;
import org.freeplane.plugin.ai.tools.create.CreateNodesRequest;
import org.freeplane.plugin.ai.tools.create.CreateNodesResponse;
import org.freeplane.plugin.ai.tools.create.CreateNodesTool;
import org.freeplane.plugin.ai.tools.create.NodeCreationHierarchyBuilder;
import org.freeplane.plugin.ai.tools.create.NodeInserter;
import org.freeplane.plugin.ai.tools.create.NodeModelCreator;
import org.freeplane.plugin.ai.tools.delete.DeleteNodesRequest;
import org.freeplane.plugin.ai.tools.delete.DeleteNodesResponse;
import org.freeplane.plugin.ai.tools.delete.DeleteNodesTool;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationResponse;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationTool;
import org.freeplane.plugin.ai.tools.edit.AttributesContentEditor;
import org.freeplane.plugin.ai.tools.edit.BatchEditTool;
import org.freeplane.plugin.ai.tools.edit.EditRequest;
import org.freeplane.plugin.ai.tools.edit.EditResultItem;
import org.freeplane.plugin.ai.tools.edit.EditTargetStatus;
import org.freeplane.plugin.ai.tools.edit.HyperlinkContentEditor;
import org.freeplane.plugin.ai.tools.edit.IconsContentEditor;
import org.freeplane.plugin.ai.tools.edit.NodeContentEditor;
import org.freeplane.plugin.ai.tools.edit.NodeStyleContentEditor;
import org.freeplane.plugin.ai.tools.edit.NoteContentWriteController;
import org.freeplane.plugin.ai.tools.edit.NoteContentWriteControllerAdapter;
import org.freeplane.plugin.ai.tools.edit.TagsContentEditor;
import org.freeplane.plugin.ai.tools.edit.TextContentWriteController;
import org.freeplane.plugin.ai.tools.edit.TextContentWriteControllerAdapter;
import org.freeplane.plugin.ai.tools.edit.TextualContentEditor;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdateApplyRequest;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdateApplyResponse;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdatePreviewRequest;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdatePreviewResponse;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdatePreviewStore;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdateTool;
import org.freeplane.plugin.ai.tools.move.CreateSummaryRequest;
import org.freeplane.plugin.ai.tools.move.CreateSummaryResponse;
import org.freeplane.plugin.ai.tools.move.CreateSummaryTool;
import org.freeplane.plugin.ai.tools.move.MoveNodesIntoSummaryRequest;
import org.freeplane.plugin.ai.tools.move.MoveNodesIntoSummaryResponse;
import org.freeplane.plugin.ai.tools.move.MoveNodesIntoSummaryTool;
import org.freeplane.plugin.ai.tools.move.MoveNodesRequest;
import org.freeplane.plugin.ai.tools.move.MoveNodesResponse;
import org.freeplane.plugin.ai.tools.move.MoveNodesTool;
import org.freeplane.plugin.ai.tools.move.SummaryNodeCreator;
import org.freeplane.plugin.ai.tools.read.FetchNodesForEditingRequest;
import org.freeplane.plugin.ai.tools.read.FetchNodesForEditingResponse;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsAsPlainTextResponse;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsResponse;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsTool;
import org.freeplane.plugin.ai.tools.search.SearchNodesRequest;
import org.freeplane.plugin.ai.tools.search.SearchNodesResponse;
import org.freeplane.plugin.ai.tools.search.SearchNodesTool;
import org.freeplane.plugin.ai.tools.selection.SelectSingleNodeRequest;
import org.freeplane.plugin.ai.tools.selection.SelectSingleNodeTool;
import org.freeplane.plugin.ai.tools.selection.SelectedMapAndNodeIdentifiersTool;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersRequest;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.freeplane.plugin.ai.tools.tagcategories.EditTagCategoriesTool;
import org.freeplane.plugin.ai.tools.tagcategories.GetTagCategoriesRequest;
import org.freeplane.plugin.ai.tools.tagcategories.GetTagCategoriesTool;
import org.freeplane.plugin.ai.tools.tagcategories.TagCategoryInstructionRequestPayload;
import org.freeplane.plugin.ai.tools.tagcategories.TagCategoryStatePayload;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AIToolSet {
    private final MessageBuilder messageBuilder;
    private final ReadNodesWithDescendantsTool readNodesWithDescendantsTool;
    private final SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool;
    private final SelectSingleNodeTool selectSingleNodeTool;
    private final SearchNodesTool searchNodesTool;
    private final CreateNodesTool createNodesTool;
    private final MoveNodesTool moveNodesTool;
    private final DeleteNodesTool deleteNodesTool;
    private final CreateSummaryTool createSummaryTool;
    private final MoveNodesIntoSummaryTool moveNodesIntoSummaryTool;
    private final ListTool listTool;
    private final ConnectorEditTool connectorEditTool;
    private final BatchEditTool batchEditTool;
    private final FormulaUpdateTool formulaUpdateTool;
    private final GetApiDocumentationTool getApiDocumentationTool;
    private final GetTagCategoriesTool getTagCategoriesTool;
    private final EditTagCategoriesTool editTagCategoriesTool;
    private final MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer;
    private final ToolCallSummaryHandler toolCallSummaryHandler;
    private final ToolCaller toolCaller;

    AIToolSet(ToolCallSummaryHandler toolCallSummaryHandler, AvailableMaps availableMaps,
              AvailableMaps.MapAccessListener mapAccessListener, TextController textController,
              NodeContentFactories nodeContentFactories, MMapController mapController,
              AiCodeHostService codeHostService,
              GetApiDocumentationTool getApiDocumentationTool,
              MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer,
              java.util.function.Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
              java.util.function.Supplier<Boolean> formulaEditingEnabledSupplier,
              ToolCaller toolCaller) {
        Objects.requireNonNull(mapController, "mapController");
        Objects.requireNonNull(availableMaps, "availableMaps");
        NodeModelCreator nodeModelCreator = new NodeModelCreator();
        AnchorPlacementCalculator anchorPlacementCalculator = new AnchorPlacementCalculator();
        NodeInserter nodeInserter = new NodeInserter(mapController, anchorPlacementCalculator);
        SummaryNodeCreator summaryNodeCreator = new SummaryNodeCreator(mapController);
        ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder = new ModifiedNodeSummaryBuilder(textController);
        TextContentWriteController textContentWriteController = new TextContentWriteControllerAdapter(
            MTextController.getController());
        NoteContentWriteController noteContentWriteController = new NoteContentWriteControllerAdapter(
            MNoteController.getController());
        MAttributeController attributeController = MAttributeController.getController();
        MIconController iconController = (MIconController) IconController.getController();
        MLinkController linkController = requireLinkController();
        TextualContentEditor textualContentEditor = new TextualContentEditor(
            textContentWriteController, noteContentWriteController, textController);
        AttributesContentEditor attributesContentEditor = new AttributesContentEditor(attributeController, textController);
        TagsContentEditor tagsContentEditor = new TagsContentEditor(iconController);
        TagCategoryAccess tagCategoryAccess = new FreeplaneTagCategoryAccess(iconController);
        List<NamedIcon> iconCandidates = new ArrayList<>(IconStoreFactory.ICON_STORE.getMindIcons());
        iconCandidates.addAll(IconStoreFactory.ICON_STORE.getUserIcons());
        IconsContentEditor iconsContentEditor = new IconsContentEditor(
            nodeContentFactories.iconDescriptionResolver, iconCandidates, iconController);
        NodeStyleContentEditor nodeStyleContentEditor = new NodeStyleContentEditor();
        HyperlinkContentEditor hyperlinkContentEditor = new HyperlinkContentEditor(linkController);
        NodeContentApplier nodeContentApplier = new NodeContentApplier(textualContentEditor, attributesContentEditor,
            tagsContentEditor, iconsContentEditor, hyperlinkContentEditor);
        NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder = new NodeCreationHierarchyBuilder(
            nodeModelCreator, nodeContentApplier, nodeStyleContentEditor);
        NodeContentEditor nodeContentEditor = new NodeContentEditor(textController, nodeContentFactories.nodeContentItemReader,
            textualContentEditor, attributesContentEditor, tagsContentEditor, iconsContentEditor,
            nodeStyleContentEditor, hyperlinkContentEditor);
        BatchEditTool batchEditTool = new BatchEditTool(availableMaps, mapAccessListener, nodeContentEditor);
        FormulaUpdateTool formulaUpdateTool = new FormulaUpdateTool(
            availableMaps,
            mapAccessListener,
            nodeContentFactories.nodeContentItemReader,
            textualContentEditor,
            attributesContentEditor,
            Objects.requireNonNull(codeHostService, "codeHostService"),
            FormulaUpdatePreviewStore.shared(),
            toolAvailabilitySupplier,
            formulaEditingEnabledSupplier);
        MessageBuilder messageBuilder = new MessageBuilder();
        ReadNodesWithDescendantsTool readNodesWithDescendantsTool = new ReadNodesWithDescendantsTool(
            availableMaps, mapAccessListener, nodeContentFactories.nodeContentItemReader, textController);
        SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool = new SelectedMapAndNodeIdentifiersTool(
            availableMaps, mapAccessListener, textController);
        SelectSingleNodeTool selectSingleNodeTool = new SelectSingleNodeTool(
            availableMaps, mapAccessListener, mapController, selectedMapAndNodeIdentifiersTool);
        SearchNodesTool searchNodesTool = new SearchNodesTool(availableMaps, mapAccessListener,
            nodeContentFactories.nodeContentItemReader, textController);
        CreateNodesPreferences createNodesPreferences = new CreateNodesPreferences();
        CreateNodesTool createNodesTool = new CreateNodesTool(availableMaps, mapAccessListener,
            nodeCreationHierarchyBuilder, nodeInserter, modifiedNodeSummaryBuilder, mapController,
            createNodesPreferences);
        MoveNodesTool moveNodesTool = new MoveNodesTool(availableMaps, mapAccessListener, mapController,
            anchorPlacementCalculator);
        DeleteNodesTool deleteNodesTool = new DeleteNodesTool(availableMaps, mapAccessListener, mapController,
            modifiedNodeSummaryBuilder);
        CreateSummaryTool createSummaryTool = new CreateSummaryTool(availableMaps, mapAccessListener,
            nodeCreationHierarchyBuilder, nodeInserter, summaryNodeCreator, modifiedNodeSummaryBuilder);
        MoveNodesIntoSummaryTool moveNodesIntoSummaryTool = new MoveNodesIntoSummaryTool(availableMaps,
            mapAccessListener, mapController, summaryNodeCreator);
        ListTool listTool = new ListTool(
            nodeContentFactories.iconDescriptionResolver, availableMaps, mapAccessListener);
        ConnectorEditTool connectorEditTool = new ConnectorEditTool(availableMaps, mapAccessListener, linkController);
        GetTagCategoriesTool getTagCategoriesTool = new GetTagCategoriesTool(
            availableMaps, mapAccessListener, tagCategoryAccess);
        EditTagCategoriesTool editTagCategoriesTool = new EditTagCategoriesTool(
            availableMaps, mapAccessListener, tagCategoryAccess);
        this.messageBuilder = Objects.requireNonNull(messageBuilder, "messageBuilder");
        this.readNodesWithDescendantsTool = Objects.requireNonNull(readNodesWithDescendantsTool,
            "readNodesWithDescendantsTool");
        this.selectedMapAndNodeIdentifiersTool = Objects.requireNonNull(
            selectedMapAndNodeIdentifiersTool, "selectedMapAndNodeIdentifiersTool");
        this.selectSingleNodeTool = Objects.requireNonNull(selectSingleNodeTool, "selectSingleNodeTool");
        this.searchNodesTool = Objects.requireNonNull(searchNodesTool, "searchNodesTool");
        this.createNodesTool = Objects.requireNonNull(createNodesTool, "createNodesTool");
        this.moveNodesTool = Objects.requireNonNull(moveNodesTool, "moveNodesTool");
        this.deleteNodesTool = Objects.requireNonNull(deleteNodesTool, "deleteNodesTool");
        this.createSummaryTool = Objects.requireNonNull(createSummaryTool, "createSummaryTool");
        this.moveNodesIntoSummaryTool = Objects.requireNonNull(moveNodesIntoSummaryTool, "moveNodesIntoSummaryTool");
        this.listTool = Objects.requireNonNull(listTool, "listTool");
        this.connectorEditTool = Objects.requireNonNull(connectorEditTool, "connectorEditTool");
        this.batchEditTool = Objects.requireNonNull(batchEditTool, "batchEditTool");
        this.formulaUpdateTool = Objects.requireNonNull(formulaUpdateTool, "formulaUpdateTool");
        this.getApiDocumentationTool = Objects.requireNonNull(getApiDocumentationTool, "getApiDocumentationTool");
        this.getTagCategoriesTool = Objects.requireNonNull(getTagCategoriesTool, "getTagCategoriesTool");
        this.editTagCategoriesTool = Objects.requireNonNull(editTagCategoriesTool, "editTagCategoriesTool");
        this.mapTargetToolCallAuthorizer = Objects.requireNonNull(
            mapTargetToolCallAuthorizer,
            "mapTargetToolCallAuthorizer");
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        this.toolCaller = toolCaller == null
            ? ToolCaller.CHAT
            : toolCaller;
    }

    AIToolSet(MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer,
              CreateNodesTool createNodesTool,
              FormulaUpdateTool formulaUpdateTool) {
        this.messageBuilder = new MessageBuilder();
        this.readNodesWithDescendantsTool = null;
        this.selectedMapAndNodeIdentifiersTool = null;
        this.selectSingleNodeTool = null;
        this.searchNodesTool = null;
        this.createNodesTool = Objects.requireNonNull(createNodesTool, "createNodesTool");
        this.moveNodesTool = null;
        this.deleteNodesTool = null;
        this.createSummaryTool = null;
        this.moveNodesIntoSummaryTool = null;
        this.listTool = null;
        this.connectorEditTool = null;
        this.batchEditTool = null;
        this.formulaUpdateTool = Objects.requireNonNull(formulaUpdateTool, "formulaUpdateTool");
        this.getApiDocumentationTool = null;
        this.getTagCategoriesTool = null;
        this.editTagCategoriesTool = null;
        this.mapTargetToolCallAuthorizer = Objects.requireNonNull(
            mapTargetToolCallAuthorizer,
            "mapTargetToolCallAuthorizer");
        this.toolCallSummaryHandler = null;
        this.toolCaller = ToolCaller.CHAT;
    }

    public String systemMessageForChat(@SuppressWarnings("unused") Object input) {
        return messageBuilder.buildForChat();
    }

    public String systemMessageForChat(@SuppressWarnings("unused") Object input,
                                       ToolAvailabilityLevel toolAvailability) {
        return messageBuilder.buildForChat(toolAvailability);
    }

    public String systemMessageForChat(@SuppressWarnings("unused") Object input,
                                       ToolAvailabilityLevel toolAvailability,
                                       String systemMessage) {
        return messageBuilder.buildForChat(systemMessage, toolAvailability);
    }

    @Tool("Read nodes with descendants.")
    public ReadNodesWithDescendantsResponse readNodesWithDescendants(ReadNodesWithDescendantsRequest request) {
        try {
            ReadNodesWithDescendantsResponse response = readNodesWithDescendantsTool.readNodesWithDescendants(request);
            publishToolCallSummary(readNodesWithDescendantsTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(readNodesWithDescendantsTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Read nodes with descendants as compact plain text for context gathering. Uses the same request fields as "
        + "readNodesWithDescendants but returns indentation-preserving plain text instead of per-node JSON.")
    public ReadNodesWithDescendantsAsPlainTextResponse readNodesWithDescendantsAsPlainText(
        ReadNodesWithDescendantsRequest request) {
        try {
            ReadNodesWithDescendantsAsPlainTextResponse response =
                readNodesWithDescendantsTool.readNodesWithDescendantsAsPlainText(request);
            publishToolCallSummary(readNodesWithDescendantsTool.buildPlainTextToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(readNodesWithDescendantsTool.buildPlainTextToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Fetch nodes for editing. editableContentFields (required): TEXT, DETAILS, NOTE, ATTRIBUTES, TAGS, ICONS. "
        + "Returns editable text/details/note/attributes/tags/icons. Text, details, and note include base contentType "
        + "plus isFormula. Attributes include isFormula. Use contentType for edit.originalContentType on non-formula "
        + "TEXT/DETAILS/NOTE edits. Formula state changes are not done through edit(...); use previewFormulaUpdates "
        + "and applyFormulaUpdates instead. Style is in content.mainStyle. Read hyperlinks via readNodesWithDescendants "
        + "with ContextSection.HYPERLINK.")
    public FetchNodesForEditingResponse fetchNodesForEditing(FetchNodesForEditingRequest request) {
        try {
            FetchNodesForEditingResponse response = readNodesWithDescendantsTool.fetchNodesForEditing(request);
            publishToolCallSummary(readNodesWithDescendantsTool.buildFetchToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(readNodesWithDescendantsTool.buildFetchToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Preview ordered formula updates without persisting them. "
        + "Requires editing availability and AI formula editing enabled. "
        + "editedElement: TEXT, DETAILS, NOTE, ATTRIBUTES. "
        + "TEXT/DETAILS/NOTE support REPLACE only; ATTRIBUTES support ADD or REPLACE. "
        + "For TEXT/DETAILS/NOTE, pass originalContentType and originalIsFormula from fetchNodesForEditing; for ATTRIBUTES REPLACE, originalIsFormula is optional. "
        + "targetIsFormula is required. nodeIdentifiers expand in request order; validation runs in that order, and later items can see earlier candidate values. "
        + "Formula targets compile and evaluate before persistence. Candidate formulas must stay value-computing and must not use UI-related or state-changing Freeplane API calls. "
        + "If one target fails, later targets are BLOCKED_BY_PREVIOUS_FAILURE and no previewId is returned. "
        + "Review candidateValue and evaluationResult for plausibility before applyFormulaUpdates.")
    public FormulaUpdatePreviewResponse previewFormulaUpdates(FormulaUpdatePreviewRequest request) {
        try {
            assertMapTargetToolAuthorized("previewFormulaUpdates", request == null ? null : request.getMapIdentifier());
            FormulaUpdatePreviewResponse response = formulaUpdateTool.previewFormulaUpdates(request);
            publishToolCallSummary(new ToolCallSummary(
                "previewFormulaUpdates",
                "previewFormulaUpdates: items=" + (response.getItems() == null ? 0 : response.getItems().size())
                    + ", previewId=" + (response.getPreviewId() == null ? "none" : response.getPreviewId()),
                false));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(new ToolCallSummary(
                "previewFormulaUpdates",
                "previewFormulaUpdates error: " + ToolCallSummaryFormatter.sanitizeValue(error.getMessage()),
                true));
            throw error;
        }
    }

    @Tool("Apply a previously validated previewFormulaUpdates result by previewId. "
        + "Use this only when general AI tool availability includes editing, AI formula editing is enabled, and plausibility review is complete. "
        + "Candidate values are persisted in the same order used during preview. Candidate formulas must stay value-computing and must not use UI-related or state-changing Freeplane API calls. "
        + "If a target node no longer exists or an attribute REPLACE target can no longer be resolved, the tool returns FAILED instead of crashing.")
    public FormulaUpdateApplyResponse applyFormulaUpdates(FormulaUpdateApplyRequest request) {
        try {
            assertMapTargetToolAuthorized("applyFormulaUpdates", request == null ? null : request.getMapIdentifier());
            FormulaUpdateApplyResponse response = formulaUpdateTool.applyFormulaUpdates(request);
            publishToolCallSummary(new ToolCallSummary(
                "applyFormulaUpdates",
                "applyFormulaUpdates: items=" + (response.getItems() == null ? 0 : response.getItems().size()),
                false));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(new ToolCallSummary(
                "applyFormulaUpdates",
                "applyFormulaUpdates error: " + ToolCallSummaryFormatter.sanitizeValue(error.getMessage()),
                true));
            throw error;
        }
    }

    @Tool("Get identifiers for the currently selected map and node, with configurable selection collection mode.")
    public SelectionIdentifiersResponse getSelectedMapAndNodeIdentifiers(SelectionIdentifiersRequest request) {
        try {
            SelectionIdentifiersResponse response = selectedMapAndNodeIdentifiersTool.getSelectedMapAndNodeIdentifiers(request);
            publishToolCallSummary(selectedMapAndNodeIdentifiersTool.buildToolCallSummary(response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(selectedMapAndNodeIdentifiersTool.buildToolCallErrorSummary(error));
            throw error;
        }
    }

    @Tool("Select a single node by identifier and make it visible in the current view. This updates the node shown "
        + "to the user in the UI and should be used only for user communication; unexpected use can disrupt user "
        + "workflow and reduce AI acceptance.")
    public SelectionIdentifiersResponse selectSingleNode(SelectSingleNodeRequest request) {
        try {
            SelectionIdentifiersResponse response = selectSingleNodeTool.selectSingleNode(request);
            publishToolCallSummary(selectSingleNodeTool.buildToolCallSummary(response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(selectSingleNodeTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Search nodes by content.")
    public SearchNodesResponse searchNodes(SearchNodesRequest request) {
        try {
            SearchNodesResponse response = searchNodesTool.searchNodes(request);
            publishToolCallSummary(searchNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(searchNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Access Freeplane scripting API documentation.")
    public String getApiDocumentation() {
        try {
            GetApiDocumentationResponse response = getApiDocumentationTool.getApiDocumentation();
            publishToolCallSummary(getApiDocumentationTool.buildToolCallSummary(response));
            return getApiDocumentationTool.formatToolResponse(response);
        } catch (RuntimeException error) {
            publishToolCallSummary(getApiDocumentationTool.buildToolCallErrorSummary(error));
            throw error;
        }
    }

    public GetApiDocumentationTool getApiDocumentationTool() {
        return getApiDocumentationTool;
    }

    @Tool("Read the map's tag categories.")
    public TagCategoryStatePayload getTagCategories(GetTagCategoriesRequest request) {
        try {
            TagCategoryStatePayload response = getTagCategoriesTool.getTagCategories(request);
            publishToolCallSummary(getTagCategoriesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(getTagCategoriesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Edit the map's tag structure and return the updated categorized and uncategorized tags. For ADD_TAG and "
        + "MOVE_TAG, use targetLocation to choose CATEGORIZED or UNCATEGORIZED placement. For CATEGORIZED ADD_TAG, "
        + "path is the full target path; for CATEGORIZED MOVE_TAG, newParentPath is the target parent path. Missing "
        + "parent categorized tags are created automatically. For UNCATEGORIZED MOVE_TAG, omit newParentPath.")
    public TagCategoryStatePayload editTagCategories(TagCategoryInstructionRequestPayload request) {
        try {
            assertMapTargetToolAuthorized("editTagCategories", request == null ? null : request.getMapIdentifier());
            TagCategoryStatePayload response = editTagCategoriesTool.editTagCategories(request);
            publishToolCallSummary(editTagCategoriesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(editTagCategoriesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Delete nodes by identifier.")
    public DeleteNodesResponse deleteNodes(DeleteNodesRequest request) {
        try {
            assertMapTargetToolAuthorized("deleteNodes", request == null ? null : request.getMapIdentifier());
            DeleteNodesResponse response = deleteNodesTool.deleteNodes(request);
            publishToolCallSummary(deleteNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(deleteNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Lists application-wide icons (not map-specific). Emoji icons are referenced by the emoji character itself "
        + "and are not listed here.")
    public ListResponse listAvailableIcons() {
        try {
            ListResponse response = listTool.listAvailableIcons();
            publishToolCallSummary(listTool.buildToolCallSummary("listAvailableIcons", response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(listTool.buildToolCallErrorSummary("listAvailableIcons", error));
            throw error;
        }
    }

    @Tool("Lists styles defined in the target map.")
    public ListResponse listMapStyles(String mapIdentifier) {
        try {
            ListResponse response = listTool.listMapStyles(mapIdentifier);
            publishToolCallSummary(listTool.buildToolCallSummary("listMapStyles", response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(listTool.buildToolCallErrorSummary("listMapStyles", error));
            throw error;
        }
    }

    @Tool("Edit connectors by source and target node identifier.\n"
        + "For REPLACE/DELETE, matchSourceLabel/matchMiddleLabel/matchTargetLabel select the connector; "
        + "sourceLabel/middleLabel/targetLabel are replacement values.\n"
        + "For ADD, match* fields are ignored.\n"
        + "In match* fields, null is wildcard and empty string matches an empty label.")
    public ConnectorEditResponse editConnectors(ConnectorEditRequest request) {
        try {
            assertMapTargetToolAuthorized("editConnectors", request == null ? null : request.getMapIdentifier());
            ConnectorEditResponse response = connectorEditTool.editConnectors(request);
            publishToolCallSummary(connectorEditTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(connectorEditTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Edit node content through undo-aware controllers. "
        + "Each item targets nodeIdentifiers. Before TEXT/DETAILS/NOTE edits, call fetchNodesForEditing and pass originalContentType. "
        + "TEXT/DETAILS/NOTE treat values starting with <html> as HTML; otherwise plain text. "
        + "TEXT supports REPLACE only; clear it with an empty value. DETAILS/NOTE support REPLACE or DELETE. STYLE/HYPERLINK support REPLACE or DELETE. "
        + "ATTRIBUTES/TAGS/ICONS REPLACE/DELETE prefer index, then targetKey. ATTRIBUTES ADD uses targetKey=name and value=value. TAGS ADD uses value and optional index. ICONS ADD uses an icon from listAvailableIcons or an emoji. "
        + "Formula-backed or formula-targeting changes are not supported here; use previewFormulaUpdates/applyFormulaUpdates. "
        + "compatibilityPolicy: SKIP_INCOMPATIBLE_FIELDS (default) applies compatible edits and reports APPLIED/SKIPPED/FAILED. "
        + "REJECT_ON_ANY_INCOMPATIBLE dry-runs the request and either returns REJECTED without writes or proceeds and may still report write-time FAILED results.")
    public List<EditResultItem> edit(EditRequest request) {
        try {
            assertMapTargetToolAuthorized("edit", request == null ? null : request.getMapIdentifier());
            List<EditResultItem> response = editNodes(request);
            publishToolCallSummary(buildEditToolSummary(request, response, false, null));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(buildEditToolSummary(request, null, true, error.getMessage()));
            throw error;
        }
    }

    private void assertMapTargetToolAuthorized(String toolName, String mapIdentifier) {
        mapTargetToolCallAuthorizer.assertAuthorized(toolName, mapIdentifier);
    }

    private void publishToolCallSummary(ToolCallSummary summary) {
        if (summary == null) {
            return;
        }
        LogUtils.info(summary.getSummaryText());
        if (toolCallSummaryHandler != null) {
            toolCallSummaryHandler.handleToolCallSummary(
                summary.withToolCaller(toolCaller));
        }
    }

    private List<EditResultItem> editNodes(EditRequest request) {
        return batchEditTool.edit(request);
    }

    private ToolCallSummary buildEditToolSummary(EditRequest request, List<EditResultItem> response,
                                                 boolean hasError, String errorMessage) {
        if (request == null) {
            return null;
        }
        int itemCount = request.getItems() == null ? 0 : request.getItems().size();
        int resultCount = response == null ? 0 : response.size();
        int appliedCount = countEditResults(response, EditTargetStatus.APPLIED);
        int skippedCount = countEditResults(response, EditTargetStatus.SKIPPED);
        int rejectedCount = countEditResults(response, EditTargetStatus.REJECTED);
        int failedCount = countEditResults(response, EditTargetStatus.FAILED);
        String summaryText = "edit: results=" + resultCount + ", items=" + itemCount
            + ", applied=" + appliedCount + ", skipped=" + skippedCount
            + ", rejected=" + rejectedCount + ", failed=" + failedCount;
        if (request.getUserSummary() != null && !request.getUserSummary().isEmpty()) {
            summaryText = summaryText + ", userSummary=\"" + request.getUserSummary() + "\"";
        }
        if (hasError) {
            String safeMessage = ToolCallSummaryFormatter.sanitizeValue(errorMessage);
            if (!safeMessage.isEmpty()) {
                summaryText = summaryText + ", error=\"" + safeMessage + "\"";
            } else {
                summaryText = summaryText + ", error=true";
            }
        }
        return new ToolCallSummary("edit", summaryText, hasError);
    }

    private int countEditResults(List<EditResultItem> response, EditTargetStatus status) {
        if (response == null || status == null) {
            return 0;
        }
        int count = 0;
        for (EditResultItem item : response) {
            if (item != null && status == item.getStatus()) {
                count++;
            }
        }
        return count;
    }

    private MLinkController requireLinkController() {
        ModeController modeController = Controller.getCurrentModeController();
        if (modeController == null) {
            throw new IllegalStateException("Current mode controller is not available.");
        }
        LinkController linkController = LinkController.getController(modeController);
        if (!(linkController instanceof MLinkController)) {
            throw new IllegalStateException("Link controller is not available.");
        }
        return (MLinkController) linkController;
    }

    @Tool("Create nodes and subtrees relative to an anchor node. "
        + "Optional fields override defaults; omit them otherwise. Do not send empty strings, empty arrays, or null. "
        + "For content.text/details/note, only values starting with <html> are treated as HTML; all others are plain text. "
        + "textContentType/detailsContentType/noteContentType affect conversion and validation only; HTML still requires the <html> prefix. "
        + "Formula values are rejected for text, details, note, and attributes in createNodes. Create the nodes first, then use previewFormulaUpdates and applyFormulaUpdates for formulas. "
        + "Omit empty optional text fields such as details and note.")
    public CreateNodesResponse createNodes(CreateNodesRequest request) {
        try {
            assertMapTargetToolAuthorized("createNodes", request == null ? null : request.getMapIdentifier());
            CreateNodesResponse response = createNodesTool.createNodes(request);
            publishToolCallSummary(createNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(createNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Move nodes relative to an anchor node.")
    public MoveNodesResponse moveNodes(MoveNodesRequest request) {
        try {
            assertMapTargetToolAuthorized("moveNodes", request == null ? null : request.getMapIdentifier());
            MoveNodesResponse response = moveNodesTool.moveNodes(request);
            publishToolCallSummary(moveNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(moveNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Create summary content and a summary bracket for a summarized range. "
        + "Optional fields override defaults; omit them otherwise. Summary anchor nodes must share the same parent. "
        + "Text rules match createNodes. Formula values are rejected for text, details, note, and attributes in createSummary; "
        + "create the summary content first, then use previewFormulaUpdates and applyFormulaUpdates for formulas. "
        + "Omit empty optional text fields such as details and note. To create a summary of summaries, choose summary anchor nodes already at the same summary level under the same parent.")
    public CreateSummaryResponse createSummary(CreateSummaryRequest request) {
        try {
            assertMapTargetToolAuthorized("createSummary", request == null ? null : request.getMapIdentifier());
            CreateSummaryResponse response = createSummaryTool.createSummary(request);
            publishToolCallSummary(createSummaryTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(createSummaryTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Move existing nodes to become summary content for a summarized range.")
    public MoveNodesIntoSummaryResponse moveNodesIntoSummary(MoveNodesIntoSummaryRequest request) {
        try {
            assertMapTargetToolAuthorized("moveNodesIntoSummary", request == null ? null : request.getMapIdentifier());
            MoveNodesIntoSummaryResponse response = moveNodesIntoSummaryTool.moveNodesIntoSummary(request);
            publishToolCallSummary(moveNodesIntoSummaryTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(moveNodesIntoSummaryTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }
}
