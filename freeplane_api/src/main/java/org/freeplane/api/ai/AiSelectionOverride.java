package org.freeplane.api.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.freeplane.api.MindMap;

/**
 * Overrides the selection structure injected into the first AI prompt message.
 *
 * <p>This affects only prompt composition. It does not change the Freeplane UI
 * selection and does not replace later tool results; tools still observe their own
 * current input and permissions.</p>
 *
 * @since 1.13.3
 */
public class AiSelectionOverride {
    private final MindMap mindMap;
    private final List<String> selectedNodeIds;

    /**
     * Creates a prompt-time selection override.
     *
     * <p>The node IDs are trimmed, kept in the supplied order, and must be unique.
     * They should identify nodes in {@code mindMap}.</p>
     *
     * @param mindMap map used for the injected selection
     * @param selectedNodeIds ordered selected node IDs; must not contain null,
     *        blank, or duplicate values
     */
    public AiSelectionOverride(MindMap mindMap, List<String> selectedNodeIds) {
        this.mindMap = Objects.requireNonNull(mindMap, "mindMap");
        this.selectedNodeIds = copySelectedNodeIds(selectedNodeIds);
    }

    /**
     * Returns the map used for the injected selection.
     *
     * @return mind map
     */
    public MindMap getMindMap() {
        return mindMap;
    }

    /**
     * Returns ordered selected node IDs.
     *
     * @return immutable ordered node-ID list
     */
    public List<String> getSelectedNodeIds() {
        return selectedNodeIds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mindMap, selectedNodeIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AiSelectionOverride)) {
            return false;
        }
        AiSelectionOverride other = (AiSelectionOverride) obj;
        return Objects.equals(mindMap, other.mindMap)
            && Objects.equals(selectedNodeIds, other.selectedNodeIds);
    }

    private static List<String> copySelectedNodeIds(List<String> selectedNodeIds) {
        Objects.requireNonNull(selectedNodeIds, "selectedNodeIds");
        ArrayList<String> copiedNodeIds = new ArrayList<String>(selectedNodeIds.size());
        LinkedHashSet<String> uniqueNodeIds = new LinkedHashSet<String>();
        for (String nodeId : selectedNodeIds) {
            if (nodeId == null) {
                throw new IllegalArgumentException("selectedNodeIds must not contain null");
            }
            String normalized = nodeId.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("selectedNodeIds must not contain blank values");
            }
            if (!uniqueNodeIds.add(normalized)) {
                throw new IllegalArgumentException("selectedNodeIds must not contain duplicates");
            }
            copiedNodeIds.add(normalized);
        }
        return Collections.unmodifiableList(copiedNodeIds);
    }
}
