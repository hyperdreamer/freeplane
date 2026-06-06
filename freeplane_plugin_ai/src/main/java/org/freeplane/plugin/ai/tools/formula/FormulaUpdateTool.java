package org.freeplane.plugin.ai.tools.formula;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.content.ContentType;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.edit.AiEditsMarker;
import org.freeplane.plugin.ai.tools.edit.AttributesContentEditor;
import org.freeplane.plugin.ai.tools.edit.EditOperation;
import org.freeplane.plugin.ai.tools.edit.EditedElement;
import org.freeplane.plugin.ai.tools.edit.TextualContentEditor;

public class FormulaUpdateTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeContentItemReader nodeContentItemReader;
    private final TextualContentEditor textualContentEditor;
    private final AttributesContentEditor attributesContentEditor;
    private final AiCodeHostService codeHostService;
    private final FormulaUpdatePreviewStore previewStore;
    private final Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier;
    private final AiEditsMarker aiEditsMarker;

    public FormulaUpdateTool(AvailableMaps availableMaps,
                             AvailableMaps.MapAccessListener mapAccessListener,
                             NodeContentItemReader nodeContentItemReader,
                             TextualContentEditor textualContentEditor,
                             AttributesContentEditor attributesContentEditor,
                             AiCodeHostService codeHostService,
                             FormulaUpdatePreviewStore previewStore,
                             Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeContentItemReader = Objects.requireNonNull(nodeContentItemReader, "nodeContentItemReader");
        this.textualContentEditor = Objects.requireNonNull(textualContentEditor, "textualContentEditor");
        this.attributesContentEditor = Objects.requireNonNull(attributesContentEditor, "attributesContentEditor");
        this.codeHostService = Objects.requireNonNull(codeHostService, "codeHostService");
        this.previewStore = Objects.requireNonNull(previewStore, "previewStore");
        this.toolAvailabilitySupplier = toolAvailabilitySupplier == null
            ? () -> ToolAvailabilityLevel.EDITING
            : toolAvailabilitySupplier;
        this.aiEditsMarker = new AiEditsMarker();
    }

    public FormulaUpdatePreviewResponse previewFormulaUpdates(FormulaUpdatePreviewRequest request) {
        assertScriptExecutionAvailable();
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        MapModel mapModel = requireMapModel(mapIdentifierValue);
        List<ResolvedFormulaUpdateTarget> targets = resolveTargets(request.getItems());
        List<FormulaUpdatePreviewResultItem> results = new ArrayList<FormulaUpdatePreviewResultItem>(targets.size());
        LinkedHashMap<String, PreviewSourceState> sourceStatesByKey = new LinkedHashMap<String, PreviewSourceState>();
        List<StoredFormulaUpdateTarget> storedTargets = new ArrayList<StoredFormulaUpdateTarget>(targets.size());
        boolean failed = false;
        try {
            for (ResolvedFormulaUpdateTarget target : targets) {
                if (failed) {
                    results.add(blockedResult(target));
                    continue;
                }
                NodeModel nodeModel = mapModel.getNodeForID(target.nodeIdentifier);
                if (nodeModel == null) {
                    results.add(failedValidationResult(target, null, "Unknown node identifier: " + target.nodeIdentifier, null));
                    failed = true;
                    continue;
                }
                PreviewSourceState sourceState = sourceStatesByKey.get(target.sourceStateKey());
                if (sourceState == null) {
                    sourceState = captureSourceState(target, nodeModel);
                    sourceStatesByKey.put(target.sourceStateKey(), sourceState);
                }
                try {
                    String candidateValue = prepareCandidate(nodeModel, target);
                    AiChatCodeOperationResult validationResult = null;
                    if (target.targetIsFormula) {
                        validationResult = codeHostService.evaluateFormula(
                            new EvaluateFormulaRequest(nodeModel, candidateValue));
                        if (!validationResult.isSuccessful()) {
                            results.add(failedValidationResult(target, candidateValue,
                                validationResult.getErrorMessage(), validationResult));
                            failed = true;
                            continue;
                        }
                    }
                    results.add(validatedResult(target, candidateValue, validationResult));
                    storedTargets.add(new StoredFormulaUpdateTarget(target, candidateValue));
                    applyTemporaryCandidate(nodeModel, target, candidateValue);
                } catch (RuntimeException error) {
                    results.add(failedValidationResult(target, null, safeMessage(error), null));
                    failed = true;
                }
            }
        } finally {
            restorePreviewState(mapModel, sourceStatesByKey);
        }
        if (failed) {
            return new FormulaUpdatePreviewResponse(mapIdentifierValue, null, results);
        }
        String previewId = UUID.randomUUID().toString();
        StoredFormulaUpdatePreview storedPreview = new StoredFormulaUpdatePreview(
            mapIdentifierValue,
            previewId,
            results,
            storedTargets);
        previewStore.save(storedPreview);
        return new FormulaUpdatePreviewResponse(mapIdentifierValue, previewId, results);
    }

    public FormulaUpdateApplyResponse applyFormulaUpdates(FormulaUpdateApplyRequest request) {
        assertScriptExecutionAvailable();
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        String previewId = requireValue(request.getPreviewId(), "previewId");
        FormulaUpdatePreviewResponse loadedPreview = previewStore.load(previewId);
        if (!(loadedPreview instanceof StoredFormulaUpdatePreview)) {
            throw new IllegalArgumentException("Unknown previewId: " + previewId);
        }
        StoredFormulaUpdatePreview storedPreview = (StoredFormulaUpdatePreview) loadedPreview;
        if (!mapIdentifierValue.equals(storedPreview.getMapIdentifier())) {
            throw new IllegalArgumentException("previewId does not belong to mapIdentifier: " + mapIdentifierValue);
        }
        MapModel mapModel = requireMapModel(mapIdentifierValue);
        try {
            List<FormulaUpdateApplyResultItem> results = new ArrayList<FormulaUpdateApplyResultItem>(
                storedPreview.getStoredTargets().size());
            boolean applyFailed = false;
            String applyFailureMessage = null;
            for (StoredFormulaUpdateTarget target : storedPreview.getStoredTargets()) {
                if (applyFailed) {
                    results.add(failedApplyResult(target, applyFailureMessage, null));
                    continue;
                }
                NodeModel nodeModel = mapModel.getNodeForID(target.nodeIdentifier);
                if (nodeModel == null) {
                    applyFailed = true;
                    applyFailureMessage = "Unknown node identifier: " + target.nodeIdentifier;
                    results.add(failedApplyResult(target, applyFailureMessage, null));
                    continue;
                }
                try {
                    applyValidatedCandidate(nodeModel, target);
                    aiEditsMarker.addAiEditsMarkerWithUndo(nodeModel);
                    NodeContentItem updatedContent = nodeContentItemReader.readNodeContentItem(
                        nodeModel,
                        NodeContentPreset.FULL,
                        true,
                        true,
                        true);
                    results.add(new FormulaUpdateApplyResultItem(
                        Integer.valueOf(target.itemIndex),
                        target.nodeIdentifier,
                        target.editedElement,
                        FormulaUpdateApplyStatus.APPLIED,
                        null,
                        updatedContent));
                } catch (RuntimeException error) {
                    applyFailed = true;
                    applyFailureMessage = safeMessage(error);
                    results.add(failedApplyResult(target, applyFailureMessage, null));
                }
            }
            return new FormulaUpdateApplyResponse(mapIdentifierValue, results);
        } finally {
            previewStore.remove(previewId);
        }
    }

    private void restorePreviewState(MapModel mapModel,
                                     LinkedHashMap<String, PreviewSourceState> sourceStatesByKey) {
        List<PreviewSourceState> sourceStates = new ArrayList<PreviewSourceState>(sourceStatesByKey.values());
        Collections.reverse(sourceStates);
        for (PreviewSourceState sourceState : sourceStates) {
            sourceState.restore(mapModel);
        }
    }

    private PreviewSourceState captureSourceState(ResolvedFormulaUpdateTarget target, NodeModel nodeModel) {
        switch (target.editedElement) {
            case TEXT:
                return new TextualPreviewSourceState(
                    target.nodeIdentifier,
                    target.editedElement,
                    textualContentEditor.currentRawValue(nodeModel, target.editedElement),
                    false,
                    null);
            case DETAILS:
                return new TextualPreviewSourceState(
                    target.nodeIdentifier,
                    target.editedElement,
                    textualContentEditor.currentRawValue(nodeModel, target.editedElement),
                    DetailModel.getDetail(nodeModel) != null,
                    DetailModel.getDetailContentType(nodeModel));
            case NOTE:
                return new TextualPreviewSourceState(
                    target.nodeIdentifier,
                    target.editedElement,
                    textualContentEditor.currentRawValue(nodeModel, target.editedElement),
                    NoteModel.getNote(nodeModel) != null,
                    NoteModel.getNoteContentType(nodeModel));
            case ATTRIBUTES:
                return new AttributeListPreviewSourceState(
                    target.nodeIdentifier,
                    snapshotAttributes(nodeModel),
                    nodeModel.getExtension(NodeAttributeTableModel.class) != null);
            default:
                throw new IllegalArgumentException("Unsupported editedElement for formula updates: "
                    + target.editedElement);
        }
    }

    private void applyValidatedCandidate(NodeModel nodeModel, StoredFormulaUpdateTarget target) {
        switch (target.editedElement) {
            case TEXT:
            case DETAILS:
            case NOTE:
                textualContentEditor.applyFormulaValue(nodeModel, target.editedElement, target.candidateValue);
                return;
            case ATTRIBUTES:
                attributesContentEditor.applyFormulaValue(
                    nodeModel,
                    target.operation,
                    target.targetKey,
                    target.index,
                    target.candidateValue);
                return;
            default:
                throw new IllegalArgumentException("Unsupported editedElement for formula updates: "
                    + target.editedElement);
        }
    }

    private void applyTemporaryCandidate(NodeModel nodeModel, ResolvedFormulaUpdateTarget target, String candidateValue) {
        switch (target.editedElement) {
            case TEXT:
                nodeModel.setText(candidateValue);
                return;
            case DETAILS:
                DetailModel.createDetailText(nodeModel).setText(candidateValue);
                return;
            case NOTE:
                NoteModel.createNote(nodeModel).setText(candidateValue);
                return;
            case ATTRIBUTES:
                applyTemporaryAttributeCandidate(nodeModel, target, candidateValue);
                return;
            default:
                throw new IllegalArgumentException("Unsupported editedElement for formula updates: "
                    + target.editedElement);
        }
    }

    private void applyTemporaryAttributeCandidate(NodeModel nodeModel, ResolvedFormulaUpdateTarget target,
                                                  String candidateValue) {
        NodeAttributeTableModel model = ensurePreviewAttributeModel(nodeModel);
        EditOperation operation = target.operation == null ? EditOperation.REPLACE : target.operation;
        switch (operation) {
            case ADD:
                String name = requireAttributeName(target.targetKey);
                int insertIndex = target.index == null ? model.getRowCount()
                    : Math.max(0, Math.min(target.index.intValue(), model.getRowCount()));
                model.getAttributes().add(insertIndex, new Attribute(name, candidateValue == null ? "" : candidateValue));
                return;
            case REPLACE:
                int targetIndex = findAttributeIndex(model, target.targetKey, target.index);
                if (targetIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for replace.");
                }
                Attribute existing = model.getAttribute(targetIndex);
                String attributeName = target.targetKey == null && existing != null
                    ? existing.getName()
                    : target.targetKey;
                model.getAttributes().set(targetIndex,
                    new Attribute(attributeName, candidateValue == null ? "" : candidateValue));
                return;
            default:
                throw new IllegalArgumentException("Formula updates support only ADD and REPLACE for attributes.");
        }
    }

    private String prepareCandidate(NodeModel nodeModel, ResolvedFormulaUpdateTarget target) {
        if (target.targetIsFormula == null) {
            throw new IllegalArgumentException("Missing targetIsFormula.");
        }
        if (target.value == null) {
            throw new IllegalArgumentException("Missing value.");
        }
        switch (target.editedElement) {
            case TEXT:
            case DETAILS:
            case NOTE:
                requireReplaceOperation(target.operation, target.editedElement);
                return textualContentEditor.prepareFormulaCandidate(
                    nodeModel,
                    target.editedElement,
                    target.originalContentType,
                    target.originalIsFormula,
                    target.targetIsFormula.booleanValue(),
                    target.value);
            case ATTRIBUTES:
                return attributesContentEditor.prepareFormulaCandidate(
                    nodeModel,
                    target.operation,
                    target.targetKey,
                    target.index,
                    target.value,
                    target.originalIsFormula,
                    target.targetIsFormula.booleanValue());
            default:
                throw new IllegalArgumentException("Unsupported editedElement for formula updates: "
                    + target.editedElement);
        }
    }

    private void requireReplaceOperation(EditOperation operation, EditedElement editedElement) {
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        if (resolvedOperation != EditOperation.REPLACE) {
            throw new IllegalArgumentException(editedElement + " supports REPLACE only in formula updates.");
        }
    }

    private List<ResolvedFormulaUpdateTarget> resolveTargets(List<FormulaUpdateItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Missing formula update items");
        }
        List<ResolvedFormulaUpdateTarget> targets = new ArrayList<ResolvedFormulaUpdateTarget>();
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            FormulaUpdateItem item = items.get(itemIndex);
            if (item == null) {
                throw new IllegalArgumentException("Missing formula update item at index " + itemIndex + ".");
            }
            validateEditedElement(item.getEditedElement());
            List<String> nodeIdentifiers = item.getNodeIdentifiers();
            if (nodeIdentifiers == null || nodeIdentifiers.isEmpty()) {
                throw new IllegalArgumentException("Missing nodeIdentifiers for item at index " + itemIndex + ".");
            }
            for (int nodeIndex = 0; nodeIndex < nodeIdentifiers.size(); nodeIndex++) {
                String nodeIdentifier = requireValue(nodeIdentifiers.get(nodeIndex),
                    "nodeIdentifiers[" + nodeIndex + "] for item at index " + itemIndex);
                targets.add(new ResolvedFormulaUpdateTarget(itemIndex, nodeIdentifier, item));
            }
        }
        return targets;
    }

    private void validateEditedElement(EditedElement editedElement) {
        if (editedElement == null) {
            throw new IllegalArgumentException("Missing editedElement.");
        }
        if (editedElement != EditedElement.TEXT
            && editedElement != EditedElement.DETAILS
            && editedElement != EditedElement.NOTE
            && editedElement != EditedElement.ATTRIBUTES) {
            throw new IllegalArgumentException("Formula updates support only TEXT, DETAILS, NOTE, and ATTRIBUTES.");
        }
    }

    private void assertScriptExecutionAvailable() {
        if (!currentToolAvailability().includesScriptExecution()) {
            throw new IllegalStateException("Formula authoring requires script execution availability.");
        }
    }

    private ToolAvailabilityLevel currentToolAvailability() {
        ToolAvailabilityLevel toolAvailability = toolAvailabilitySupplier.get();
        return toolAvailability == null ? ToolAvailabilityLevel.EDITING : toolAvailability;
    }

    private MapModel requireMapModel(String mapIdentifierValue) {
        MapModel mapModel = availableMaps.findMapModel(parseMapIdentifier(mapIdentifierValue), mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        return mapModel;
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier);
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName + ".");
        }
        return value;
    }

    private FormulaUpdatePreviewResultItem validatedResult(ResolvedFormulaUpdateTarget target,
                                                           String candidateValue,
                                                           AiChatCodeOperationResult validationResult) {
        return new FormulaUpdatePreviewResultItem(
            Integer.valueOf(target.itemIndex),
            target.nodeIdentifier,
            target.editedElement,
            FormulaUpdatePreviewStatus.VALIDATED,
            candidateValue,
            validationResult == null ? null : validationResult.getResult(),
            validationResult == null ? Collections.<String>emptyList() : validationResult.getCompilerDiagnostics(),
            null,
            validationResult == null ? null : validationResult.getLineNumber());
    }

    private FormulaUpdatePreviewResultItem failedValidationResult(ResolvedFormulaUpdateTarget target,
                                                                  String candidateValue,
                                                                  String errorMessage,
                                                                  AiChatCodeOperationResult validationResult) {
        return new FormulaUpdatePreviewResultItem(
            Integer.valueOf(target.itemIndex),
            target.nodeIdentifier,
            target.editedElement,
            FormulaUpdatePreviewStatus.FAILED_VALIDATION,
            candidateValue,
            null,
            validationResult == null ? Collections.<String>emptyList() : validationResult.getCompilerDiagnostics(),
            errorMessage,
            validationResult == null ? null : validationResult.getLineNumber());
    }

    private FormulaUpdatePreviewResultItem blockedResult(ResolvedFormulaUpdateTarget target) {
        return new FormulaUpdatePreviewResultItem(
            Integer.valueOf(target.itemIndex),
            target.nodeIdentifier,
            target.editedElement,
            FormulaUpdatePreviewStatus.BLOCKED_BY_PREVIOUS_FAILURE,
            null,
            null,
            Collections.<String>emptyList(),
            "Blocked by previous validation failure.",
            null);
    }

    private FormulaUpdateApplyResultItem failedApplyResult(StoredFormulaUpdateTarget target,
                                                           String errorMessage,
                                                           NodeContentItem updatedContent) {
        return new FormulaUpdateApplyResultItem(
            Integer.valueOf(target.itemIndex),
            target.nodeIdentifier,
            target.editedElement,
            FormulaUpdateApplyStatus.FAILED,
            errorMessage,
            updatedContent);
    }

    private String safeMessage(RuntimeException error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return RuntimeException.class.getSimpleName();
        }
        return error.getMessage().trim();
    }

    private List<AttributeValueSnapshot> snapshotAttributes(NodeModel nodeModel) {
        NodeAttributeTableModel attributeTableModel = nodeModel.getExtension(NodeAttributeTableModel.class);
        if (attributeTableModel == null) {
            return Collections.emptyList();
        }
        List<AttributeValueSnapshot> snapshots = new ArrayList<AttributeValueSnapshot>(attributeTableModel.getRowCount());
        for (int row = 0; row < attributeTableModel.getRowCount(); row++) {
            Attribute attribute = attributeTableModel.getAttribute(row);
            if (attribute != null) {
                snapshots.add(new AttributeValueSnapshot(attribute.getName(), Objects.toString(attribute.getValue(), null)));
            }
        }
        return snapshots;
    }

    private NodeAttributeTableModel ensurePreviewAttributeModel(NodeModel nodeModel) {
        NodeAttributeTableModel attributeTableModel = nodeModel.getExtension(NodeAttributeTableModel.class);
        if (attributeTableModel != null) {
            return attributeTableModel;
        }
        NodeAttributeTableModel created = new NodeAttributeTableModel();
        nodeModel.addExtension(created);
        return created;
    }

    private int findAttributeIndex(NodeAttributeTableModel model, String targetKey, Integer index) {
        if (index != null && index.intValue() >= 0 && index.intValue() < model.getRowCount()) {
            return index.intValue();
        }
        if (targetKey != null) {
            for (int row = 0; row < model.getRowCount(); row++) {
                Attribute attribute = model.getAttribute(row);
                if (attribute != null && targetKey.equals(attribute.getName())) {
                    return row;
                }
            }
        }
        return -1;
    }

    private String requireAttributeName(String targetKey) {
        if (targetKey == null || targetKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing attribute name.");
        }
        return targetKey;
    }

    private final class ResolvedFormulaUpdateTarget {
        private final int itemIndex;
        private final String nodeIdentifier;
        private final EditedElement editedElement;
        private final ContentType originalContentType;
        private final Boolean originalIsFormula;
        private final Boolean targetIsFormula;
        private final String value;
        private final Integer index;
        private final EditOperation operation;
        private final String targetKey;

        private ResolvedFormulaUpdateTarget(int itemIndex, String nodeIdentifier, FormulaUpdateItem item) {
            this.itemIndex = itemIndex;
            this.nodeIdentifier = nodeIdentifier;
            this.editedElement = item.getEditedElement();
            this.originalContentType = item.getOriginalContentType();
            this.originalIsFormula = item.getOriginalIsFormula();
            this.targetIsFormula = item.getTargetIsFormula();
            this.value = item.getValue();
            this.index = item.getIndex();
            this.operation = item.getOperation();
            this.targetKey = item.getTargetKey();
        }

        private String sourceStateKey() {
            if (editedElement == EditedElement.ATTRIBUTES) {
                return nodeIdentifier + "|ATTRIBUTES";
            }
            return nodeIdentifier + "|" + editedElement.name();
        }
    }

    private abstract class PreviewSourceState {
        protected final String nodeIdentifier;

        private PreviewSourceState(String nodeIdentifier) {
            this.nodeIdentifier = nodeIdentifier;
        }

        abstract void restore(MapModel mapModel);
    }

    private final class TextualPreviewSourceState extends PreviewSourceState {
        private final EditedElement editedElement;
        private final String rawValue;
        private final boolean hadModel;
        private final String freeplaneContentType;

        private TextualPreviewSourceState(String nodeIdentifier, EditedElement editedElement, String rawValue,
                                          boolean hadModel, String freeplaneContentType) {
            super(nodeIdentifier);
            this.editedElement = editedElement;
            this.rawValue = rawValue;
            this.hadModel = hadModel;
            this.freeplaneContentType = freeplaneContentType;
        }

        @Override
        void restore(MapModel mapModel) {
            if (mapModel == null) {
                return;
            }
            NodeModel nodeModel = mapModel.getNodeForID(nodeIdentifier);
            if (nodeModel == null) {
                return;
            }
            switch (editedElement) {
                case TEXT:
                    nodeModel.setText(rawValue);
                    return;
                case DETAILS:
                    if (!hadModel) {
                        nodeModel.removeExtension(DetailModel.class);
                        return;
                    }
                    DetailModel detailModel = DetailModel.createDetailText(nodeModel);
                    detailModel.setText(rawValue);
                    detailModel.setContentType(freeplaneContentType);
                    return;
                case NOTE:
                    if (!hadModel) {
                        nodeModel.removeExtension(NoteModel.class);
                        return;
                    }
                    NoteModel noteModel = NoteModel.createNote(nodeModel);
                    noteModel.setText(rawValue);
                    noteModel.setContentType(freeplaneContentType);
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported editedElement for restore: " + editedElement);
            }
        }
    }

    private final class AttributeListPreviewSourceState extends PreviewSourceState {
        private final List<AttributeValueSnapshot> attributes;
        private final boolean hadExtension;

        private AttributeListPreviewSourceState(String nodeIdentifier, List<AttributeValueSnapshot> attributes,
                                                boolean hadExtension) {
            super(nodeIdentifier);
            this.attributes = attributes;
            this.hadExtension = hadExtension;
        }

        @Override
        void restore(MapModel mapModel) {
            if (mapModel == null) {
                return;
            }
            NodeModel nodeModel = mapModel.getNodeForID(nodeIdentifier);
            if (nodeModel == null) {
                return;
            }
            if (!hadExtension) {
                nodeModel.removeExtension(NodeAttributeTableModel.class);
                return;
            }
            NodeAttributeTableModel attributeTableModel = ensurePreviewAttributeModel(nodeModel);
            attributeTableModel.getAttributes().clear();
            for (AttributeValueSnapshot attribute : attributes) {
                attributeTableModel.getAttributes().add(new Attribute(attribute.name, attribute.value));
            }
        }
    }

    private static final class AttributeValueSnapshot {
        private final String name;
        private final String value;

        private AttributeValueSnapshot(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AttributeValueSnapshot)) {
                return false;
            }
            AttributeValueSnapshot that = (AttributeValueSnapshot) other;
            return Objects.equals(name, that.name) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    private static final class StoredFormulaUpdatePreview extends FormulaUpdatePreviewResponse {
        private final List<StoredFormulaUpdateTarget> storedTargets;

        private StoredFormulaUpdatePreview(String mapIdentifier,
                                           String previewId,
                                           List<FormulaUpdatePreviewResultItem> items,
                                           List<StoredFormulaUpdateTarget> storedTargets) {
            super(mapIdentifier, previewId, items);
            this.storedTargets = storedTargets;
        }

        private List<StoredFormulaUpdateTarget> getStoredTargets() {
            return storedTargets;
        }
    }

    private static final class StoredFormulaUpdateTarget {
        private final int itemIndex;
        private final String nodeIdentifier;
        private final EditedElement editedElement;
        private final EditOperation operation;
        private final String targetKey;
        private final Integer index;
        private final String candidateValue;

        private StoredFormulaUpdateTarget(ResolvedFormulaUpdateTarget target, String candidateValue) {
            this.itemIndex = target.itemIndex;
            this.nodeIdentifier = target.nodeIdentifier;
            this.editedElement = target.editedElement;
            this.operation = target.operation;
            this.targetKey = target.targetKey;
            this.index = target.index;
            this.candidateValue = candidateValue;
        }
    }
}
