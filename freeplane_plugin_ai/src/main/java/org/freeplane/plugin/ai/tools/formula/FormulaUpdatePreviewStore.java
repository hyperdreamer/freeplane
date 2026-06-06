package org.freeplane.plugin.ai.tools.formula;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FormulaUpdatePreviewStore {
    private static final FormulaUpdatePreviewStore SHARED = new FormulaUpdatePreviewStore();

    public static FormulaUpdatePreviewStore shared() {
        return SHARED;
    }

    private final Map<String, FormulaUpdatePreviewResponse> previewsById = new ConcurrentHashMap<String, FormulaUpdatePreviewResponse>();

    public void save(FormulaUpdatePreviewResponse previewResponse) {
        if (previewResponse == null || previewResponse.getPreviewId() == null
            || previewResponse.getPreviewId().trim().isEmpty()) {
            throw new IllegalArgumentException("previewResponse.previewId is required.");
        }
        previewsById.put(previewResponse.getPreviewId(), previewResponse);
    }

    public FormulaUpdatePreviewResponse load(String previewId) {
        if (previewId == null || previewId.trim().isEmpty()) {
            return null;
        }
        return previewsById.get(previewId.trim());
    }

    public void remove(String previewId) {
        if (previewId == null || previewId.trim().isEmpty()) {
            return;
        }
        previewsById.remove(previewId.trim());
    }
}
