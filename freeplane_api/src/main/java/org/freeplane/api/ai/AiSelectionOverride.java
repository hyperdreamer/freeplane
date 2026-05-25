package org.freeplane.api.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.freeplane.api.MindMap;

/** Overrides the selection structure injected into the first AI prompt message.
 * This affects only prompt composition and does not replace later tool results.
 * @since 1.13.3 */
public class AiSelectionOverride {
    private final MindMap mindMap;
    private final List<String> selectedNodeIds;

    public AiSelectionOverride(MindMap mindMap, List<String> selectedNodeIds) {
        this.mindMap = Objects.requireNonNull(mindMap, "mindMap");
        this.selectedNodeIds = copySelectedNodeIds(selectedNodeIds);
    }

    public MindMap getMindMap() {
        return mindMap;
    }

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
