package org.freeplane.plugin.ai.tools.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;

public class BatchEditTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeContentEditor nodeContentEditor;

    public BatchEditTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                         NodeContentEditor nodeContentEditor) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeContentEditor = Objects.requireNonNull(nodeContentEditor, "nodeContentEditor");
    }

    public List<EditResultItem> edit(EditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<NodeContentEditItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Missing edit items");
        }
        List<ResolvedEditTarget> targets = resolveEditTargets(items);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Missing nodeIdentifiers in edit items.");
        }
        EditCompatibilityPolicy compatibilityPolicy = request.getResolvedCompatibilityPolicy();
        if (compatibilityPolicy == EditCompatibilityPolicy.REJECT_ON_ANY_INCOMPATIBLE) {
            return applyWithRejectPolicy(mapModel, targets);
        }
        return applyWithSkipPolicy(mapModel, targets);
    }

    private List<ResolvedEditTarget> resolveEditTargets(List<NodeContentEditItem> items) {
        List<ResolvedEditTarget> targets = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            NodeContentEditItem item = items.get(itemIndex);
            if (item == null) {
                throw new IllegalArgumentException("Missing edit item at index " + itemIndex + ".");
            }
            List<String> nodeIdentifiers = item.getNodeIdentifiers();
            if (nodeIdentifiers == null || nodeIdentifiers.isEmpty()) {
                throw new IllegalArgumentException("Missing nodeIdentifiers for item at index " + itemIndex + ".");
            }
            for (int nodeIndex = 0; nodeIndex < nodeIdentifiers.size(); nodeIndex++) {
                String nodeIdentifier = requireValue(nodeIdentifiers.get(nodeIndex),
                    "nodeIdentifiers[" + nodeIndex + "] for item at index " + itemIndex);
                targets.add(new ResolvedEditTarget(itemIndex, nodeIdentifier, item));
            }
        }
        return targets;
    }

    private List<EditResultItem> applyWithSkipPolicy(MapModel mapModel, List<ResolvedEditTarget> targets) {
        List<EditResultItem> results = new ArrayList<>(targets.size());
        for (ResolvedEditTarget target : targets) {
            NodeModel nodeModel = mapModel.getNodeForID(target.nodeIdentifier);
            if (nodeModel == null) {
                results.add(buildSkippedResult(target,
                    "Unknown node identifier: " + target.nodeIdentifier));
                continue;
            }
            String incompatibilityReason = validateTargetCompatibility(nodeModel, target);
            if (incompatibilityReason != null) {
                results.add(buildSkippedResult(target, incompatibilityReason));
                continue;
            }
            try {
                results.add(applySingleTarget(nodeModel, target));
            } catch (RuntimeException error) {
                results.add(buildFailedResult(target, error.getMessage()));
            }
        }
        return results;
    }

    private List<EditResultItem> applyWithRejectPolicy(MapModel mapModel, List<ResolvedEditTarget> targets) {
        List<EditResultItem> rejectedResults = new ArrayList<>();
        for (ResolvedEditTarget target : targets) {
            NodeModel nodeModel = mapModel.getNodeForID(target.nodeIdentifier);
            if (nodeModel == null) {
                rejectedResults.add(buildRejectedResult(target,
                    "Unknown node identifier: " + target.nodeIdentifier));
                continue;
            }
            String incompatibilityReason = validateTargetCompatibility(nodeModel, target);
            if (incompatibilityReason != null) {
                rejectedResults.add(buildRejectedResult(target, incompatibilityReason));
            }
        }
        if (!rejectedResults.isEmpty()) {
            return rejectedResults;
        }
        List<EditResultItem> results = new ArrayList<>(targets.size());
        for (ResolvedEditTarget target : targets) {
            NodeModel nodeModel = mapModel.getNodeForID(target.nodeIdentifier);
            if (nodeModel == null) {
                results.add(buildFailedResult(target, "Unknown node identifier: " + target.nodeIdentifier));
                continue;
            }
            try {
                results.add(applySingleTarget(nodeModel, target));
            } catch (RuntimeException error) {
                results.add(buildFailedResult(target, error.getMessage()));
            }
        }
        return results;
    }

    private String validateTargetCompatibility(NodeModel nodeModel, ResolvedEditTarget target) {
        try {
            nodeContentEditor.validate(nodeModel, Collections.singletonList(target.item));
            return null;
        } catch (IllegalArgumentException incompatibleError) {
            return incompatibleError.getMessage();
        }
    }

    private EditResultItem applySingleTarget(NodeModel nodeModel, ResolvedEditTarget target) {
        NodeContentItem content = nodeContentEditor.edit(nodeModel, Collections.singletonList(target.item));
        return buildAppliedResult(target, content);
    }

    private EditResultItem buildAppliedResult(ResolvedEditTarget target, NodeContentItem content) {
        return new EditResultItem(
            target.itemIndex,
            target.nodeIdentifier,
            target.item.getEditedElement(),
            EditTargetStatus.APPLIED,
            null,
            null,
            content);
    }

    private EditResultItem buildSkippedResult(ResolvedEditTarget target, String reason) {
        String resolvedReason = reason == null || reason.trim().isEmpty()
            ? "Incompatible edit target."
            : reason;
        return new EditResultItem(
            target.itemIndex,
            target.nodeIdentifier,
            target.item.getEditedElement(),
            EditTargetStatus.SKIPPED,
            Collections.singletonList(resolvedReason),
            null,
            null);
    }

    private EditResultItem buildFailedResult(ResolvedEditTarget target, String errorMessage) {
        String resolvedMessage = errorMessage == null || errorMessage.trim().isEmpty()
            ? "Edit failed."
            : errorMessage;
        return new EditResultItem(
            target.itemIndex,
            target.nodeIdentifier,
            target.item.getEditedElement(),
            EditTargetStatus.FAILED,
            null,
            resolvedMessage,
            null);
    }

    private EditResultItem buildRejectedResult(ResolvedEditTarget target, String reason) {
        String resolvedReason = reason == null || reason.trim().isEmpty()
            ? "Incompatible edit target."
            : reason;
        return new EditResultItem(
            target.itemIndex,
            target.nodeIdentifier,
            target.item.getEditedElement(),
            EditTargetStatus.REJECTED,
            Collections.singletonList(resolvedReason),
            null,
            null);
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName + ".");
        }
        return value;
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier);
        }
    }

    private static class ResolvedEditTarget {
        private final int itemIndex;
        private final String nodeIdentifier;
        private final NodeContentEditItem item;

        private ResolvedEditTarget(int itemIndex, String nodeIdentifier, NodeContentEditItem item) {
            this.itemIndex = itemIndex;
            this.nodeIdentifier = nodeIdentifier;
            this.item = item;
        }
    }
}
